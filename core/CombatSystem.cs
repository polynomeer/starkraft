using System;
using System.Collections.Generic;

namespace Samjae.Core;

public readonly record struct AttackEvent(int Attacker, int Target, int Damage, int TargetHp, bool Killed);

/// <summary>
/// Chooses targets for every ready weapon first, then applies all hits, so the order units are visited in cannot
/// let one unit's hit change another's choice within the tick. Planned damage is reserved so automatic targeting
/// avoids wasting shots on a unit that is already going to die.
/// </summary>
public sealed class CombatSystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly Reach _reach;
    private readonly Dictionary<int, int> _reservedDamage = new();
    private readonly List<PlannedAttack> _planned = new();
    private readonly List<AttackEvent> _events = new();

    public CombatSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
        _reach = new Reach(world, content);
    }

    /// <summary>Hits applied this tick, for presentation. Not state.</summary>
    public IReadOnlyList<AttackEvent> LastTickEvents => _events;
    public int LastTickKills { get; private set; }

    public void Tick()
    {
        _events.Clear();
        _planned.Clear();
        _reservedDamage.Clear();
        LastTickKills = 0;
        foreach (int id in _world.AliveIds)
        {
            PlanAttack(id);
        }

        foreach (PlannedAttack attack in _planned)
        {
            ApplyAttack(attack);
        }
    }

    private void PlanAttack(int id)
    {
        if (!_world.Weapons.TryGet(id, out WeaponRef weapon) || !IsReady(weapon)
            || _content.WeaponOrNull(weapon.Id) is not WeaponDef def
            || !_world.Tags.TryGet(id, out UnitTag tag) || !_world.Positions.TryGet(id, out Position position))
        {
            return;
        }

        var shooter = new Shooter(id, tag.Faction, position, def);
        (int target, bool ordered) = OrderedTarget(shooter);
        if (target == 0)
        {
            target = StickyTarget(shooter);
        }

        if (target == 0)
        {
            target = BestAutomaticTarget(shooter);
        }

        if (target == 0)
        {
            _world.AutoAttackTargets.Remove(id);
            return;
        }

        if (!ordered)
        {
            _world.AutoAttackTargets.Set(id, target);
        }

        int damage = DamageAgainst(def, target);
        _reservedDamage[target] = ReservedOn(target) + damage;
        _planned.Add(new PlannedAttack(id, target, damage, ordered));
    }

    /// <summary>The cooldown counts down every tick, and a weapon fires on the tick it reaches zero, so the interval is exactly its cooldown.</summary>
    private static bool IsReady(WeaponRef weapon)
    {
        if (weapon.CooldownTicks > 0)
        {
            weapon.CooldownTicks--;
        }

        return weapon.CooldownTicks == 0;
    }

    private (int Target, bool Ordered) OrderedTarget(in Shooter shooter)
    {
        List<Order> orders = _world.Orders.Get(shooter.Id);
        if (orders.Count == 0 || orders[0] is not Order.Attack attack)
        {
            return (0, false);
        }

        if (!_world.IsEnemyOf(attack.Target, shooter.Faction))
        {
            orders.RemoveAt(0);
            _world.ClearPath(shooter.Id);
            return (0, false);
        }

        return InRange(shooter, attack.Target) ? (attack.Target, true) : (0, false);
    }

    private int StickyTarget(in Shooter shooter)
    {
        if (!_world.AutoAttackTargets.TryGet(shooter.Id, out int sticky))
        {
            return 0;
        }

        if (_world.IsEnemyOf(sticky, shooter.Faction) && InRange(shooter, sticky))
        {
            return sticky;
        }

        _world.AutoAttackTargets.Remove(shooter.Id);
        return 0;
    }

    /// <summary>Prefers a target that survives the hit, then the nearest edge. Ties go to the lowest id.</summary>
    private int BestAutomaticTarget(in Shooter shooter)
    {
        int best = 0;
        Fix bestGap = Fix.MaxValue;
        bool bestSurvives = false;
        foreach (int other in _world.AliveIds)
        {
            if (other == shooter.Id || !_world.IsEnemyOf(other, shooter.Faction) || !InRange(shooter, other, out Fix gap))
            {
                continue;
            }

            int effectiveHp = _world.Healths.Get(other).Hp - ReservedOn(other);
            if (effectiveHp <= 0)
            {
                continue;
            }

            bool survives = effectiveHp > DamageAgainst(shooter.Weapon, other);
            if (best == 0 || (survives && !bestSurvives) || (survives == bestSurvives && gap < bestGap))
            {
                best = other;
                bestGap = gap;
                bestSurvives = survives;
            }
        }

        return best;
    }

    /// <summary>
    /// Shots planned this tick land even if their shooter was killed earlier in the same loop, so two units that
    /// fire at each other trade blows whatever their ids. Kotlin dropped the second shot, favouring the lower id.
    /// </summary>
    private void ApplyAttack(PlannedAttack attack)
    {
        if (_world.Weapons.TryGet(attack.Attacker, out WeaponRef weapon) && _content.WeaponOrNull(weapon.Id) is WeaponDef def)
        {
            weapon.CooldownTicks = def.CooldownTicks;
        }

        if (!_world.Healths.TryGet(attack.Target, out Health health) || health.Hp <= 0)
        {
            return;
        }

        health.Hp -= attack.Damage;
        bool killed = health.Hp <= 0;
        _events.Add(new AttackEvent(attack.Attacker, attack.Target, attack.Damage, health.Hp, killed));
        if (!killed)
        {
            return;
        }

        LastTickKills++;
        _world.Remove(attack.Target, "death");
        if (attack.Ordered && _world.Orders.TryGet(attack.Attacker, out List<Order>? orders) && orders.Count > 0 && orders[0] is Order.Attack ordered && ordered.Target == attack.Target)
        {
            orders.RemoveAt(0);
        }
    }

    private bool InRange(in Shooter shooter, int target) => InRange(shooter, target, out _);

    private bool InRange(in Shooter shooter, int target, out Fix gap)
    {
        gap = _reach.Gap(shooter.Id, shooter.Position, target);
        return gap <= shooter.Weapon.Range;
    }

    private int DamageAgainst(WeaponDef weapon, int target) => Math.Max(0, weapon.Damage - _world.Healths.Get(target).Armor);

    private int ReservedOn(int target) => _reservedDamage.GetValueOrDefault(target);

    private readonly record struct Shooter(int Id, int Faction, Position Position, WeaponDef Weapon);

    private readonly record struct PlannedAttack(int Attacker, int Target, int Damage, bool Ordered);
}
