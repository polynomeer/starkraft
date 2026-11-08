using System;
using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>A timed, stackable change to a unit's numbers. Every bonus is per stack and may be negative.</summary>
public sealed record StatusDef(
    string Id,
    int DurationTicks,
    int MaxStacks,
    int DamagePerStack,
    int ArmorPerStack,
    int SpeedPercentPerStack);

public enum EffectKind : byte
{
    ApplyStatus = 1,
    Damage = 2,
    Heal = 3,
}

/// <summary><see cref="Other"/> is the other party of the trigger: the victim, the killer or the attacker.</summary>
public enum TargetShape : byte
{
    Self = 1,
    Other = 2,
    Circle = 3,
}

/// <summary>Relative to the faction of the unit that owns the trigger.</summary>
public enum TargetFaction : byte
{
    Any = 1,
    Ally = 2,
    Enemy = 3,
}

public sealed record EffectTarget(TargetShape Shape, TargetFaction Faction, Fix Radius);

/// <summary><see cref="Amount"/> is hit points for damage and heal, and stacks for a status.</summary>
public sealed record EffectDef(EffectKind Kind, EffectTarget Target, int Amount, string? StatusId);

public enum TriggerEvent : byte
{
    /// <summary>The owner killed something.</summary>
    Kill = 1,

    /// <summary>The owner died. Fires from where it stood.</summary>
    Death = 2,

    /// <summary>A weapon hit the owner.</summary>
    Damaged = 3,

    /// <summary>The owner's weapon hit something.</summary>
    Hit = 4,
}

public sealed record TriggerDef(TriggerEvent On, IReadOnlyList<EffectDef> Effects);

public sealed class StatusInstance
{
    public readonly string StatusId;
    public int Stacks;
    public int RemainingTicks;

    public StatusInstance(string statusId, int stacks, int remainingTicks)
    {
        StatusId = statusId;
        Stacks = stacks;
        RemainingTicks = remainingTicks;
    }
}

public readonly record struct EffectEvent(int Source, int Target, EffectKind Kind, int Amount, string? StatusId);

/// <summary>Sums of status bonuses, read by movement and combat.</summary>
public static class StatusRules
{
    public static int DamageBonus(World world, GameContent content, int id) => Sum(world, content, id, def => def.DamagePerStack);

    public static int ArmorBonus(World world, GameContent content, int id) => Sum(world, content, id, def => def.ArmorPerStack);

    /// <summary>Never below zero, so a slow can stop a unit but not move it backwards.</summary>
    public static Fix ScaleSpeed(World world, GameContent content, int id, Fix speed)
    {
        int percent = 100 + Sum(world, content, id, def => def.SpeedPercentPerStack);
        if (percent == 100)
        {
            return speed;
        }

        return Fix.FromRaw(checked((int)((long)speed.Raw * Math.Max(0, percent) / 100)));
    }

    private static int Sum(World world, GameContent content, int id, Func<StatusDef, int> perStack)
    {
        if (!world.Statuses.TryGet(id, out List<StatusInstance>? statuses))
        {
            return 0;
        }

        int total = 0;
        foreach (StatusInstance status in statuses)
        {
            if (content.StatusOrNull(status.StatusId) is StatusDef def)
            {
                total = checked(total + perStack(def) * status.Stacks);
            }
        }

        return total;
    }
}

/// <summary>
/// Counts statuses down at the start of the tick. A status applied with a duration of N is in force for the N ticks
/// after the one it was applied in, and is gone at the start of the tick after that.
/// </summary>
public sealed class StatusSystem : ITickSystem
{
    private readonly World _world;
    private readonly List<int> _emptied = new();

    public StatusSystem(World world) => _world = world;

    public void Tick()
    {
        _emptied.Clear();
        foreach (int id in _world.Statuses)
        {
            List<StatusInstance> statuses = _world.Statuses.Get(id);
            statuses.RemoveAll(Expire);
            if (statuses.Count == 0)
            {
                _emptied.Add(id);
            }
        }

        foreach (int id in _emptied)
        {
            _world.Statuses.Remove(id);
        }
    }

    private static bool Expire(StatusInstance status)
    {
        if (status.RemainingTicks == 0)
        {
            return true;
        }

        status.RemainingTicks--;
        return false;
    }
}

/// <summary>
/// Runs unit triggers. Combat queues firings while it applies hits and resolves them once every hit has landed, so
/// one unit's trigger cannot change another unit's hit in the same tick. Firings resolve first in, first out.
/// Kills by effect damage fire kill and death triggers again, up to <see cref="MaxChainDepth"/>; damaged and hit
/// triggers fire only for weapon hits, so two units that hurt whoever hurts them cannot loop.
/// </summary>
public sealed class EffectSystem
{
    public const int MaxChainDepth = 4;

    private readonly World _world;
    private readonly GameContent _content;
    private readonly Reach _reach;
    private readonly Queue<Firing> _queue = new();
    private readonly List<int> _targets = new();
    private readonly List<EffectEvent> _events = new();

    public EffectSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
        _reach = new Reach(world, content);
    }

    /// <summary>Effects applied this tick, for presentation. Not state.</summary>
    public IReadOnlyList<EffectEvent> LastTickEvents => _events;

    /// <summary>Kills by effect damage this tick.</summary>
    public int LastTickKills { get; private set; }

    public void ClearEvents()
    {
        _events.Clear();
        LastTickKills = 0;
    }

    /// <summary>Records a weapon hit. Call before the target is removed, so its death trigger knows where it stood.</summary>
    public void QueueHit(int attacker, int target, bool killed) => QueueHit(attacker, target, killed, depth: 0, weapon: true);

    public void Resolve()
    {
        while (_queue.Count > 0)
        {
            Firing firing = _queue.Dequeue();
            if (firing.On != TriggerEvent.Death && !_world.IsAttackable(firing.Owner))
            {
                continue;
            }

            foreach (EffectDef effect in TriggersOf(firing.OwnerType, firing.On))
            {
                Apply(firing, effect);
            }
        }
    }

    private void QueueHit(int attacker, int target, bool killed, int depth, bool weapon)
    {
        if (weapon)
        {
            Queue(attacker, TriggerEvent.Hit, target, depth);
            Queue(target, TriggerEvent.Damaged, attacker, depth);
        }

        if (killed)
        {
            Queue(target, TriggerEvent.Death, attacker, depth);
            Queue(attacker, TriggerEvent.Kill, target, depth);
        }
    }

    private void Queue(int owner, TriggerEvent on, int other, int depth)
    {
        if (!_world.Tags.TryGet(owner, out UnitTag tag) || !_world.Positions.TryGet(owner, out Position position)
            || TriggersOf(tag.TypeId, on).Count == 0)
        {
            return;
        }

        _queue.Enqueue(new Firing(owner, tag.TypeId, tag.Faction, position.X, position.Y, on, other, depth));
    }

    private IReadOnlyList<EffectDef> TriggersOf(string typeId, TriggerEvent on)
    {
        if (_content.UnitOrNull(typeId) is not UnitDef unit)
        {
            return Array.Empty<EffectDef>();
        }

        foreach (TriggerDef trigger in unit.Triggers)
        {
            if (trigger.On == on)
            {
                return trigger.Effects;
            }
        }

        return Array.Empty<EffectDef>();
    }

    private void Apply(in Firing firing, EffectDef effect)
    {
        CollectTargets(firing, effect.Target);
        foreach (int target in _targets)
        {
            if (!_world.IsAttackable(target))
            {
                continue;
            }

            switch (effect.Kind)
            {
                case EffectKind.ApplyStatus:
                    ApplyStatus(target, _content.StatusOrNull(effect.StatusId!)!, effect.Amount);
                    break;
                case EffectKind.Heal:
                    Heal(target, effect.Amount);
                    break;
                case EffectKind.Damage:
                    Damage(firing, target, effect.Amount);
                    break;
            }

            _events.Add(new EffectEvent(firing.Owner, target, effect.Kind, effect.Amount, effect.StatusId));
        }
    }

    /// <summary>Targets are fixed before any is changed, in ascending id order for circles.</summary>
    private void CollectTargets(in Firing firing, EffectTarget target)
    {
        _targets.Clear();
        switch (target.Shape)
        {
            case TargetShape.Self:
                AddIfFactionMatches(firing, firing.Owner, target.Faction);
                break;
            case TargetShape.Other:
                AddIfFactionMatches(firing, firing.Other, target.Faction);
                break;
            case TargetShape.Circle:
                var center = new Position(firing.X, firing.Y);
                foreach (int id in _world.Positions)
                {
                    if (_world.IsAttackable(id) && _reach.GapFromPoint(center, id) <= target.Radius)
                    {
                        AddIfFactionMatches(firing, id, target.Faction);
                    }
                }

                break;
        }
    }

    private void AddIfFactionMatches(in Firing firing, int id, TargetFaction faction)
    {
        if (!_world.Tags.TryGet(id, out UnitTag tag))
        {
            return;
        }

        bool ally = tag.Faction == firing.OwnerFaction;
        if (faction == TargetFaction.Any || (faction == TargetFaction.Ally) == ally)
        {
            _targets.Add(id);
        }
    }

    /// <summary>Applying a status again adds stacks up to its limit and restarts its timer.</summary>
    private void ApplyStatus(int target, StatusDef def, int stacks)
    {
        if (!_world.Statuses.TryGet(target, out List<StatusInstance>? statuses))
        {
            statuses = new List<StatusInstance>();
            _world.Statuses.Set(target, statuses);
        }

        foreach (StatusInstance status in statuses)
        {
            if (status.StatusId == def.Id)
            {
                status.Stacks = Math.Min(def.MaxStacks, status.Stacks + stacks);
                status.RemainingTicks = def.DurationTicks;
                return;
            }
        }

        statuses.Add(new StatusInstance(def.Id, Math.Min(def.MaxStacks, stacks), def.DurationTicks));
    }

    /// <summary>Buildings still under construction are skipped: their hit points follow construction progress.</summary>
    private void Heal(int target, int amount)
    {
        if (_world.ConstructionSites.Has(target))
        {
            return;
        }

        Health health = _world.Healths.Get(target);
        health.Hp = Math.Min(health.MaxHp, health.Hp + amount);
    }

    /// <summary>Effect damage ignores armor; armor is a defence against weapons.</summary>
    private void Damage(in Firing firing, int target, int amount)
    {
        Health health = _world.Healths.Get(target);
        health.Hp -= amount;
        if (health.Hp > 0)
        {
            return;
        }

        LastTickKills++;
        if (firing.Depth + 1 <= MaxChainDepth)
        {
            QueueHit(firing.Owner, target, killed: true, firing.Depth + 1, weapon: false);
        }

        _world.Remove(target, "death");
    }

    private readonly record struct Firing(
        int Owner,
        string OwnerType,
        int OwnerFaction,
        Fix X,
        Fix Y,
        TriggerEvent On,
        int Other,
        int Depth);
}
