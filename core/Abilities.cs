using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>What a cast command points at.</summary>
public enum AbilityTargetKind : byte
{
    /// <summary>Cast where the caster stands, at once, without walking.</summary>
    None = 1,
    Unit = 2,
    Point = 3,
}

/// <summary>
/// An ability a unit casts on command. <see cref="Range"/> is measured from the caster's edge, like a weapon, to the
/// target's edge or to the point. In the effects, <c>self</c> is the caster, <c>other</c> is the target unit (or the
/// point, for where a summon appears) and a circle is centred on the target, the point or the caster.
/// <see cref="TargetFaction"/> limits which units a unit-target ability may be cast on, relative to the caster.
/// </summary>
public sealed record AbilityDef(
    string Id,
    AbilityTargetKind TargetKind,
    TargetFaction TargetFaction,
    Fix Range,
    int CooldownTicks,
    IReadOnlyList<EffectDef> Effects);

public sealed class AbilityCooldown
{
    public readonly string AbilityId;
    public int RemainingTicks;

    public AbilityCooldown(string abilityId, int remainingTicks)
    {
        AbilityId = abilityId;
        RemainingTicks = remainingTicks;
    }
}

public enum CastFailure : byte
{
    UnknownAbility = 1,
    InvalidTarget,
    OutOfBounds,
}

/// <summary>A cast that happened this tick, for presentation. <see cref="Target"/> is 0 for a point or no target.</summary>
public readonly record struct CastEvent(int Caster, string AbilityId, int Target, Fix X, Fix Y);

/// <summary>Checks shared by the command processor, movement and the ability system.</summary>
public static class AbilityRules
{
    /// <summary>The ability if the caster's type has it.</summary>
    public static AbilityDef? AbilityOf(World world, GameContent content, int caster, string abilityId)
    {
        if (!world.Tags.TryGet(caster, out UnitTag tag) || content.UnitOrNull(tag.TypeId) is not UnitDef unit)
        {
            return null;
        }

        foreach (string id in unit.Abilities)
        {
            if (id == abilityId)
            {
                return content.AbilityOrNull(id);
            }
        }

        return null;
    }

    /// <summary>Whether the cast still has something to land on: a living unit of the right side, or a point on the map.</summary>
    public static CastFailure? CheckTarget(World world, AbilityDef ability, int caster, int target, Fix x, Fix y)
    {
        switch (ability.TargetKind)
        {
            case AbilityTargetKind.Unit:
                if (!world.IsAttackable(target) || !world.Tags.TryGet(caster, out UnitTag casterTag))
                {
                    return CastFailure.InvalidTarget;
                }

                bool ally = world.Tags.Get(target).Faction == casterTag.Faction;
                bool allowed = ability.TargetFaction == TargetFaction.Any || (ability.TargetFaction == TargetFaction.Ally) == ally;
                return allowed ? null : CastFailure.InvalidTarget;
            case AbilityTargetKind.Point:
                return world.Map.InBounds(x.FloorToInt(), y.FloorToInt()) ? null : CastFailure.OutOfBounds;
            default:
                return null;
        }
    }

    public static bool InRange(World world, Reach reach, AbilityDef ability, int caster, Order.Cast cast)
    {
        Position from = world.Positions.Get(caster);
        return ability.TargetKind switch
        {
            AbilityTargetKind.Unit => reach.Gap(caster, from, cast.Target) <= ability.Range,
            AbilityTargetKind.Point => reach.GapFromPoint(new Position(cast.X, cast.Y), caster) <= ability.Range,
            _ => true,
        };
    }

    public static bool IsReady(World world, int caster, string abilityId)
    {
        if (!world.Cooldowns.TryGet(caster, out List<AbilityCooldown>? cooldowns))
        {
            return true;
        }

        foreach (AbilityCooldown cooldown in cooldowns)
        {
            if (cooldown.AbilityId == abilityId)
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>Whether movement should chase a unit rather than walk to a fixed tile.</summary>
    public static bool ChasesUnit(Order order) => order is Order.Attack or Order.Cast { Target: > 0 };
}

/// <summary>
/// Casts abilities whose caster has reached range, after movement and before combat, so a cast can change this tick's
/// attacks. Cooldowns count down first: an ability with a cooldown of N can be cast again N ticks after it was cast,
/// the same interval rule as weapons. A caster in range whose ability is still cooling down waits there with its order.
/// Casters go in ascending id order, and each cast's triggers resolve before the next caster.
/// </summary>
public sealed class AbilitySystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly EffectSystem _effects;
    private readonly Reach _reach;
    private readonly List<int> _cooled = new();
    private readonly List<CastEvent> _events = new();

    public AbilitySystem(World world, GameContent content, EffectSystem effects)
    {
        _world = world;
        _content = content;
        _effects = effects;
        _reach = new Reach(world, content);
    }

    public IReadOnlyList<CastEvent> LastTickCasts => _events;

    public void Tick()
    {
        _events.Clear();
        CountDown();
        foreach (int id in _world.AliveIds)
        {
            if (_world.IsAttackable(id) && _world.Orders.TryGet(id, out List<Order>? orders) && orders.Count > 0
                && orders[0] is Order.Cast cast)
            {
                TryCast(id, orders, cast);
            }
        }
    }

    private void CountDown()
    {
        _cooled.Clear();
        foreach (int id in _world.Cooldowns)
        {
            List<AbilityCooldown> cooldowns = _world.Cooldowns.Get(id);
            cooldowns.RemoveAll(c => --c.RemainingTicks <= 0);
            if (cooldowns.Count == 0)
            {
                _cooled.Add(id);
            }
        }

        foreach (int id in _cooled)
        {
            _world.Cooldowns.Remove(id);
        }
    }

    private void TryCast(int id, List<Order> orders, Order.Cast cast)
    {
        if (AbilityRules.AbilityOf(_world, _content, id, cast.AbilityId) is not AbilityDef ability
            || AbilityRules.CheckTarget(_world, ability, id, cast.Target, cast.X, cast.Y) != null)
        {
            orders.RemoveAt(0);
            _world.ClearPath(id);
            return;
        }

        if (!AbilityRules.InRange(_world, _reach, ability, id, cast) || !AbilityRules.IsReady(_world, id, ability.Id))
        {
            return;
        }

        orders.RemoveAt(0);
        _world.ClearPath(id);
        StartCooldown(id, ability);
        _events.Add(new CastEvent(id, ability.Id, cast.Target, cast.X, cast.Y));
        _effects.Cast(id, ability, cast.Target, cast.X, cast.Y);
    }

    private void StartCooldown(int id, AbilityDef ability)
    {
        if (ability.CooldownTicks == 0)
        {
            return;
        }

        if (!_world.Cooldowns.TryGet(id, out List<AbilityCooldown>? cooldowns))
        {
            cooldowns = new List<AbilityCooldown>();
            _world.Cooldowns.Set(id, cooldowns);
        }

        cooldowns.Add(new AbilityCooldown(ability.Id, ability.CooldownTicks));
    }
}
