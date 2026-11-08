using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>Walks units along their paths, requests new paths when blocked or stuck, and finishes movement orders.</summary>
public sealed class MovementSystem : ITickSystem
{
    /// <summary>Speed for entity types with no unit definition.</summary>
    public static readonly Fix DefaultSpeedPerTick = Fix.FromRatio(17, 4) / Fix.FromInt(Simulation.TicksPerSecond);

    private static readonly Fix ArrivalDistance = Fix.FromRatio(1, 20);
    private static readonly Fix AttackMoveLeash = Fix.FromInt(3);
    private static readonly Fix ConstructReach = Fix.FromRatio(3, 2);
    private static readonly int RepathCooldownTicks = GameContent.MillisecondsToTicks(200);
    private static readonly int StuckThresholdTicks = GameContent.MillisecondsToTicks(400);

    /// <summary>(0.01 tiles)² with 32 fraction bits.</summary>
    private const long StuckDistanceSquaredRaw = 429497;

    private readonly World _world;
    private readonly GameContent _content;
    private readonly Reach _reach;

    public int LastTickReplans { get; private set; }
    public int LastTickReplansBlocked { get; private set; }
    public int LastTickReplansStuck { get; private set; }

    public MovementSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
        _reach = new Reach(world, content);
    }

    public void Tick()
    {
        _world.PathRequests.BeginTick();
        LastTickReplans = 0;
        LastTickReplansBlocked = 0;
        LastTickReplansStuck = 0;
        foreach (int id in _world.AliveIds)
        {
            if (!_world.Positions.TryGet(id, out Position position))
            {
                continue;
            }

            UpdateTimers(id, position);
            if (!_world.Orders.TryGet(id, out List<Order>? orders) || orders.Count == 0)
            {
                continue;
            }

            if (orders[0] is Order.Attack attack)
            {
                TickAttackChase(id, position, orders, attack);
            }
            else if (orders[0] is not Order.Hold)
            {
                TickRoute(id, position, orders, orders[0]);
            }
        }
    }

    private void UpdateTimers(int id, Position position)
    {
        if (_world.RepathCooldowns.Has(id) && _world.RepathCooldowns.Get(id) > 0)
        {
            _world.RepathCooldowns.Get(id)--;
        }

        if (_world.Stucks.TryGet(id, out StuckTracker stuck))
        {
            if (Fix.LengthSquaredRaw(position.X - stuck.LastX, position.Y - stuck.LastY) < StuckDistanceSquaredRaw)
            {
                stuck.Ticks++;
            }
            else
            {
                stuck.Ticks = 0;
                stuck.LastX = position.X;
                stuck.LastY = position.Y;
            }
        }
    }

    private void TickRoute(int id, Position position, List<Order> orders, Order order)
    {
        if (order is Order.Construct construct && HandleConstructStop(id, position, orders, construct))
        {
            return;
        }

        if (!_world.PathFollows.TryGet(id, out PathFollow path))
        {
            TryRequestMissingPath(id);
            return;
        }

        if (path.Index >= path.Length)
        {
            FinishRoute(id, orders, order);
            return;
        }

        (int nodeX, int nodeY) = NodeTile(path);
        if (TryReplan(id, position, order, path, nodeX, nodeY))
        {
            return;
        }

        if (order is Order.AttackMove attackMove && HasEnemyInRange(id, position))
        {
            if (!Fix.IsWithin(attackMove.X - position.X, attackMove.Y - position.Y, AttackMoveLeash))
            {
                _world.ClearPath(id);
                orders.RemoveAt(0);
            }

            return;
        }

        if (AdvanceTowardNode(id, position, path, nodeX, nodeY) && path.Index >= path.Length)
        {
            FinishRoute(id, orders, order);
        }
    }

    /// <summary>Returns true when the order needs no walking this tick.</summary>
    private bool HandleConstructStop(int id, Position position, List<Order> orders, Order.Construct construct)
    {
        if (!_world.ConstructionSites.Has(construct.Target))
        {
            _world.BuilderTasks.Remove(id);
            _world.ClearPath(id);
            orders.RemoveAt(0);
            return true;
        }

        if (IsConstructTargetInRange(position, construct.Target))
        {
            _world.ClearPath(id);
            return true;
        }

        return false;
    }

    private void FinishRoute(int id, List<Order> orders, Order order)
    {
        _world.ClearPath(id);
        if (order is Order.Patrol patrol)
        {
            orders[0] = patrol with { TowardB = !patrol.TowardB };
        }
        else if (order is not Order.Construct)
        {
            orders.RemoveAt(0);
        }
    }

    private void TickAttackChase(int id, Position position, List<Order> orders, Order.Attack attack)
    {
        if (!IsValidAttackTarget(id, attack.Target, out Position? target) || target == null)
        {
            _world.ClearPath(id);
            orders.RemoveAt(0);
            return;
        }

        if (IsAttackTargetInRange(id, position, attack.Target))
        {
            _world.ClearPath(id);
            return;
        }

        if (RefreshChasePath(id, target))
        {
            return;
        }

        if (!_world.PathFollows.TryGet(id, out PathFollow path))
        {
            return;
        }

        if (path.Index >= path.Length)
        {
            _world.ClearPath(id);
            if (IsCooldownReady(id) && _world.PathRequests.Enqueue(id))
            {
                StartCooldown(id);
                LastTickReplans++;
            }

            return;
        }

        (int nodeX, int nodeY) = NodeTile(path);
        if (!TryReplan(id, position, attack, path, nodeX, nodeY))
        {
            AdvanceTowardNode(id, position, path, nodeX, nodeY);
        }
    }

    private bool IsValidAttackTarget(int id, int targetId, out Position? target)
    {
        target = null;
        return _world.Tags.TryGet(id, out UnitTag tag)
            && _world.IsEnemyOf(targetId, tag.Faction)
            && _world.Positions.TryGet(targetId, out target);
    }

    /// <summary>Returns true when the tick is over because a new path was requested for a missing or stale goal.</summary>
    private bool RefreshChasePath(int id, Position target)
    {
        bool hasPath = _world.PathFollows.TryGet(id, out PathFollow path);
        bool goalMoved = hasPath && (path.GoalX != target.X.FloorToInt() || path.GoalY != target.Y.FloorToInt());
        if ((hasPath && !goalMoved) || !IsCooldownReady(id))
        {
            return false;
        }

        _world.ClearPath(id);
        if (_world.PathRequests.Enqueue(id))
        {
            StartCooldown(id);
            LastTickReplans++;
        }

        return true;
    }

    /// <summary>
    /// Returns true when the unit should not walk this tick because it is waiting on a replan. Waypoints can be far
    /// apart, so only the next tile on the way is checked. An attack target standing on the goal does not block its attacker.
    /// </summary>
    private bool TryReplan(int id, Position position, Order order, PathFollow path, int nodeX, int nodeY)
    {
        int tileX = position.X.FloorToInt();
        int tileY = position.Y.FloorToInt();
        int stepX = tileX + System.Math.Sign(nodeX - tileX);
        int stepY = tileY + System.Math.Sign(nodeY - tileY);
        bool isAttackGoal = order is Order.Attack && stepX == path.GoalX && stepY == path.GoalY;
        bool blocked = !_world.Map.IsPassable(stepX, stepY)
            || (!isAttackGoal && _world.Occupancy.IsBlockedAllowing(stepX, stepY, tileX, tileY))
            || IsDiagonalCornerBlocked(tileX, tileY, stepX, stepY);
        bool stuck = _world.Stucks.TryGet(id, out StuckTracker tracker) && tracker.Ticks >= StuckThresholdTicks;
        if ((!blocked && !stuck) || !IsCooldownReady(id))
        {
            return false;
        }

        if (_world.PathRequests.Enqueue(id))
        {
            StartCooldown(id);
            _world.ClearPath(id);
            LastTickReplans++;
            LastTickReplansBlocked += blocked ? 1 : 0;
            LastTickReplansStuck += stuck ? 1 : 0;
        }

        return true;
    }

    /// <summary>Moves one step, or moves on to the next node when close enough. Returns true when a node was reached.</summary>
    private bool AdvanceTowardNode(int id, Position position, PathFollow path, int nodeX, int nodeY)
    {
        Fix dx = Fix.FromInt(nodeX) + Fix.Half - position.X;
        Fix dy = Fix.FromInt(nodeY) + Fix.Half - position.Y;
        Fix distance = Fix.Length(dx, dy);
        if (distance <= ArrivalDistance)
        {
            path.Index++;
            return true;
        }

        Fix step = Fix.Min(SpeedFor(id), distance);
        position.X += dx / distance * step;
        position.Y += dy / distance * step;
        return false;
    }

    private (int X, int Y) NodeTile(PathFollow path)
    {
        int node = path.Nodes[path.Index];
        return (node % _world.Map.Width, node / _world.Map.Width);
    }

    private void TryRequestMissingPath(int id)
    {
        if (_world.RepathCooldowns.Has(id) && _world.RepathCooldowns.Get(id) > 0)
        {
            return;
        }

        if (_world.PathRequests.Enqueue(id))
        {
            StartCooldown(id);
            LastTickReplans++;
        }
    }

    private bool IsCooldownReady(int id) => _world.RepathCooldowns.Has(id) && _world.RepathCooldowns.Get(id) == 0;

    private void StartCooldown(int id)
    {
        if (_world.RepathCooldowns.Has(id))
        {
            _world.RepathCooldowns.Set(id, RepathCooldownTicks);
        }
    }

    private Fix SpeedFor(int id)
    {
        Fix speed = _world.Tags.TryGet(id, out UnitTag tag) && _content.UnitOrNull(tag.TypeId) is UnitDef unit
            ? unit.SpeedPerTick
            : DefaultSpeedPerTick;
        return StatusRules.ScaleSpeed(_world, _content, id, speed);
    }

    private bool IsDiagonalCornerBlocked(int tileX, int tileY, int stepX, int stepY)
    {
        if (tileX == stepX || tileY == stepY)
        {
            return false;
        }

        return IsClosedOff(stepX, tileY) || IsClosedOff(tileX, stepY);
    }

    private bool IsClosedOff(int x, int y) => !_world.Map.IsPassable(x, y) || _world.Occupancy.IsBlocked(x, y);

    private bool IsAttackTargetInRange(int id, Position source, int target)
    {
        return WeaponRange(id) is Fix range && _reach.IsInReach(id, source, target, range);
    }

    private Fix? WeaponRange(int id)
    {
        return _world.Weapons.TryGet(id, out WeaponRef weapon) && _content.WeaponOrNull(weapon.Id) is WeaponDef def
            ? def.Range
            : null;
    }

    private bool IsConstructTargetInRange(Position source, int targetId)
    {
        if (!_world.Footprints.TryGet(targetId, out BuildingFootprint footprint))
        {
            return false;
        }

        Fix minX = Fix.FromInt(footprint.TileX) - ConstructReach;
        Fix minY = Fix.FromInt(footprint.TileY) - ConstructReach;
        Fix maxX = Fix.FromInt(footprint.TileX + footprint.Width) + ConstructReach;
        Fix maxY = Fix.FromInt(footprint.TileY + footprint.Height) + ConstructReach;
        return source.X >= minX && source.X <= maxX && source.Y >= minY && source.Y <= maxY;
    }

    private bool HasEnemyInRange(int id, Position source)
    {
        if (WeaponRange(id) is not Fix range || !_world.Tags.TryGet(id, out UnitTag tag))
        {
            return false;
        }

        foreach (int other in _world.AliveIds)
        {
            if (other != id && _world.IsEnemyOf(other, tag.Faction) && _reach.IsInReach(id, source, other, range))
            {
                return true;
            }
        }

        return false;
    }
}
