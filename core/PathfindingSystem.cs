using System;

namespace Samjae.Core;

/// <summary>Solves queued path requests within the tick's node budget. Requests that run out of budget carry over.</summary>
public sealed class PathfindingSystem : ITickSystem
{
    private readonly World _world;
    private readonly Pathfinder _pathfinder;
    private readonly int[] _output;

    public int LastTickRequests { get; private set; }
    public int LastTickSolved { get; private set; }
    public int LastTickNodesUsed { get; private set; }
    public int LastTickBudgetExhausted { get; private set; }
    public int LastTickCarryOver { get; private set; }

    public PathfindingSystem(World world)
    {
        _world = world;
        _pathfinder = new Pathfinder(world.Map, world.Occupancy);
        _output = new int[world.Map.Width * world.Map.Height];
    }

    public void Tick()
    {
        PathRequestQueue queue = _world.PathRequests;
        int remainingBudget = queue.Settings.NodeBudgetPerTick;
        int carried = 0;
        LastTickSolved = 0;
        LastTickNodesUsed = 0;
        LastTickBudgetExhausted = 0;
        LastTickRequests = queue.Count;

        for (int i = 0; i < queue.Count; i++)
        {
            int id = queue.IdAt(i);
            if (!_world.Orders.TryGet(id, out var orders) || orders.Count == 0)
            {
                continue;
            }

            if (remainingBudget <= 0)
            {
                queue.SetId(carried++, id);
                continue;
            }

            if (!_world.Positions.TryGet(id, out Position position)
                || GoalFor(orders[0], position) is not (int goalX, int goalY))
            {
                continue;
            }

            if (Solve(id, position, orders[0], goalX, goalY, ref remainingBudget))
            {
                queue.SetId(carried++, id);
            }
        }

        queue.Reset(carried);
        LastTickCarryOver = carried;
    }

    /// <summary>Returns true when the request ran out of budget and must be retried next tick.</summary>
    private bool Solve(int id, Position position, Order order, int goalX, int goalY, ref int remainingBudget)
    {
        int length = _pathfinder.FindPath(
            position.X.FloorToInt(), position.Y.FloorToInt(), goalX, goalY, remainingBudget, _output,
            allowOccupiedGoal: order is Order.Attack);
        remainingBudget -= _pathfinder.LastNodesUsed;
        LastTickNodesUsed += _pathfinder.LastNodesUsed;
        if (length > 0)
        {
            _world.PathFollows.Set(id, new PathFollow(_output[..length], length, 0, goalX, goalY));
            LastTickSolved++;
            return false;
        }

        if (_pathfinder.LastBudgetExhausted)
        {
            LastTickBudgetExhausted++;
            return true;
        }

        return false;
    }

    private (int X, int Y)? GoalFor(Order order, Position position)
    {
        switch (order)
        {
            case Order.Move move:
                return (move.X.FloorToInt(), move.Y.FloorToInt());
            case Order.AttackMove attackMove:
                return (attackMove.X.FloorToInt(), attackMove.Y.FloorToInt());
            case Order.Patrol patrol:
                Fix x = patrol.TowardB ? patrol.BX : patrol.AX;
                Fix y = patrol.TowardB ? patrol.BY : patrol.AY;
                return (x.FloorToInt(), y.FloorToInt());
            case Order.Construct construct:
                return _world.Footprints.TryGet(construct.Target, out BuildingFootprint footprint)
                    ? NearestApproachTile(position.X.FloorToInt(), position.Y.FloorToInt(), footprint)
                    : null;
            case Order.Attack attack:
                return _world.Positions.TryGet(attack.Target, out Position target)
                    ? (target.X.FloorToInt(), target.Y.FloorToInt())
                    : null;
            default:
                return null;
        }
    }

    private static (int X, int Y) NearestApproachTile(int fromX, int fromY, BuildingFootprint footprint)
    {
        int minX = footprint.TileX - 1;
        int minY = footprint.TileY - 1;
        int maxX = footprint.TileX + footprint.Width;
        int maxY = footprint.TileY + footprint.Height;
        (int X, int Y) best = (minX, footprint.TileY);
        int bestDistance = int.MaxValue;
        for (int y = minY; y <= maxY; y++)
        {
            for (int x = minX; x <= maxX; x++)
            {
                bool onBorder = x == minX || x == maxX || y == minY || y == maxY;
                int distance = Math.Abs(fromX - x) + Math.Abs(fromY - y);
                if (onBorder && distance < bestDistance)
                {
                    bestDistance = distance;
                    best = (x, y);
                }
            }
        }

        return best;
    }
}
