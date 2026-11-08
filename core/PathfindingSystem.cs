using System;

namespace Samjae.Core;

/// <summary>
/// Solves queued path requests within the tick's node budget. Requests that run out of budget carry over.
/// A goal that is blocked or taken by another unit is replaced by the nearest free tile, so groups sent to one spot
/// spread around it and units sent to a building or resource stop beside it. Kotlin gave such requests no path.
/// A tile already chosen as another unit's goal counts as taken, so two units do not keep chasing the same spot.
/// A unit already near a blocked goal that cannot reach any free tile stays where it is, which ends its move.
/// </summary>
public sealed class PathfindingSystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly Pathfinder _pathfinder;
    private readonly int[] _output;
    private readonly System.Collections.Generic.Dictionary<int, int> _claimedGoals = new();

    public int LastTickRequests { get; private set; }
    public int LastTickSolved { get; private set; }
    public int LastTickNodesUsed { get; private set; }
    public int LastTickBudgetExhausted { get; private set; }
    public int LastTickCarryOver { get; private set; }

    public PathfindingSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
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
        CollectClaimedGoals();

        for (int i = 0; i < queue.Count; i++)
        {
            int id = queue.IdAt(i);
            if (!_world.Orders.TryGet(id, out var orders) || orders.Count == 0)
            {
                continue;
            }

            if (Layers.IsAir(_world, _content, id))
            {
                FlyStraight(id, orders[0]);
                continue;
            }

            if (remainingBudget <= 0)
            {
                queue.SetId(carried++, id);
                continue;
            }

            if (!_world.Positions.TryGet(id, out Position position)
                || GoalFor(orders[0], position) is not (int rawGoalX, int rawGoalY))
            {
                continue;
            }

            (int goalX, int goalY) = AbilityRules.ChasesUnit(orders[0])
                ? (rawGoalX, rawGoalY)
                : FreeGoalNear(id, rawGoalX, rawGoalY, position.X.FloorToInt(), position.Y.FloorToInt());

            bool substituted = (goalX, goalY) != (rawGoalX, rawGoalY);
            if (Solve(id, position, orders[0], goalX, goalY, ref remainingBudget, substituted ? (rawGoalX, rawGoalY) : null))
            {
                queue.SetId(carried++, id);
            }
        }

        queue.Reset(carried);
        LastTickCarryOver = carried;
    }

    /// <summary>
    /// Air units fly straight to the goal tile: a one-node path that costs no budget, claims no goal and is never
    /// moved off a taken tile, because nothing blocks the air.
    /// </summary>
    private void FlyStraight(int id, Order order)
    {
        if (!_world.Positions.TryGet(id, out Position position) || GoalFor(order, position) is not (int x, int y)
            || !_world.Map.InBounds(x, y))
        {
            return;
        }

        _world.PathFollows.Set(id, new PathFollow(new[] { y * _world.Map.Width + x }, 1, 0, x, y));
        LastTickSolved++;
    }

    /// <summary>Returns true when the request ran out of budget and must be retried next tick.</summary>
    private bool Solve(int id, Position position, Order order, int goalX, int goalY, ref int remainingBudget, (int X, int Y)? blockedGoal)
    {
        int fromX = position.X.FloorToInt();
        int fromY = position.Y.FloorToInt();
        int length = _pathfinder.FindPath(fromX, fromY, goalX, goalY, remainingBudget, _output, allowOccupiedGoal: AbilityRules.ChasesUnit(order));
        if (length == 0 && !_pathfinder.LastBudgetExhausted && blockedGoal is (int X, int Y) original
            && System.Math.Max(System.Math.Abs(fromX - original.X), System.Math.Abs(fromY - original.Y)) <= GoalSearchRadius)
        {
            (goalX, goalY) = (fromX, fromY);
            length = _pathfinder.FindPath(fromX, fromY, goalX, goalY, remainingBudget, _output);
        }

        remainingBudget -= _pathfinder.LastNodesUsed;
        LastTickNodesUsed += _pathfinder.LastNodesUsed;
        if (length > 0)
        {
            _world.PathFollows.Set(id, new PathFollow(_output[..length], length, 0, goalX, goalY));
            if (!AbilityRules.ChasesUnit(order))
            {
                _claimedGoals[goalY * _world.Map.Width + goalX] = id;
            }

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
            case Order.Cast { Target: > 0 } cast:
                return _world.Positions.TryGet(cast.Target, out Position castTarget)
                    ? (castTarget.X.FloorToInt(), castTarget.Y.FloorToInt())
                    : null;
            case Order.Cast point:
                // Casts without a target never ask for a path, so the rest walk to a point.
                return (point.X.FloorToInt(), point.Y.FloorToInt());
            default:
                return null;
        }
    }

    private const int GoalSearchRadius = 8;

    /// <summary>The goal itself if free, else the free tile closest to it within a few rings. The unit's own tile counts as free.</summary>
    private (int X, int Y) FreeGoalNear(int id, int goalX, int goalY, int fromX, int fromY)
    {
        if (IsFreeFor(id, goalX, goalY, fromX, fromY))
        {
            return (goalX, goalY);
        }

        for (int radius = 1; radius <= GoalSearchRadius; radius++)
        {
            (int X, int Y)? best = null;
            int bestDistance = int.MaxValue;
            for (int y = goalY - radius; y <= goalY + radius; y++)
            {
                for (int x = goalX - radius; x <= goalX + radius; x++)
                {
                    bool onRing = System.Math.Max(System.Math.Abs(x - goalX), System.Math.Abs(y - goalY)) == radius;
                    int distance = (x - goalX) * (x - goalX) + (y - goalY) * (y - goalY);
                    if (onRing && distance < bestDistance && IsFreeFor(id, x, y, fromX, fromY))
                    {
                        best = (x, y);
                        bestDistance = distance;
                    }
                }
            }

            if (best is (int X, int Y) found)
            {
                return found;
            }
        }

        return (goalX, goalY);
    }

    private bool IsFreeFor(int id, int x, int y, int fromX, int fromY)
    {
        if (!_world.Map.IsPassable(x, y))
        {
            return false;
        }

        bool claimedByOther = _claimedGoals.TryGetValue(y * _world.Map.Width + x, out int owner) && owner != id;
        return !claimedByOther && ((x == fromX && y == fromY) || !_world.Occupancy.IsBlocked(x, y));
    }

    /// <summary>Goals of units still walking a path, rebuilt every tick from state, so it never needs saving.</summary>
    private void CollectClaimedGoals()
    {
        _claimedGoals.Clear();
        foreach (int id in _world.PathFollows)
        {
            if (_world.Orders.TryGet(id, out var orders) && orders.Count > 0 && !AbilityRules.ChasesUnit(orders[0]))
            {
                PathFollow path = _world.PathFollows.Get(id);
                _claimedGoals[path.GoalY * _world.Map.Width + path.GoalX] = id;
            }
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
