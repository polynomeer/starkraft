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
    Summon = 4,
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

/// <summary>
/// What a summon creates: a named unit type, or a copy of the trigger's other party (a fallen enemy rising for the
/// summoner). A lifetime of zero means the unit stays until killed.
/// </summary>
public sealed record SummonDef(string? UnitId, bool CopyOther, int LifetimeTicks);

/// <summary>
/// <see cref="Amount"/> is hit points for damage and heal, stacks for a status, and units for a summon. A summon's
/// target shape is where it appears: at the owner or at the other party.
/// </summary>
public sealed record EffectDef(EffectKind Kind, EffectTarget Target, int Amount, string? StatusId, SummonDef? Summon = null);

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

/// <summary>
/// Removes summoned units whose time is up, right after statuses count down. A lifetime of N lasts the N ticks after
/// the one the unit appeared in, like a status.
/// </summary>
public sealed class LifetimeSystem : ITickSystem
{
    private readonly World _world;
    private readonly List<int> _expired = new();

    public LifetimeSystem(World world) => _world = world;

    public void Tick()
    {
        _expired.Clear();
        foreach (int id in _world.Lifetimes)
        {
            int remaining = _world.Lifetimes.Get(id);
            if (remaining == 0)
            {
                _expired.Add(id);
            }
            else
            {
                _world.Lifetimes.Set(id, remaining - 1);
            }
        }

        foreach (int id in _expired)
        {
            _world.Remove(id, "expired");
        }
    }
}

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
    public const int SummonSearchRadius = 3;

    private readonly World _world;
    private readonly GameContent _content;
    private readonly Reach _reach;
    private readonly Queue<Firing> _queue = new();
    private readonly List<int> _targets = new();
    private readonly HashSet<int> _standingTiles = new();
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

    /// <summary>
    /// Applies a cast's effects at once, then resolves the triggers they set off. <c>other</c> is the target unit, or
    /// the point for where a summon appears; a circle is centred on the target, the point, or the caster.
    /// </summary>
    public void Cast(int caster, AbilityDef ability, int target, Fix x, Fix y)
    {
        UnitTag tag = _world.Tags.Get(caster);
        Position at = _world.Positions.Get(caster);
        Firing firing = ability.TargetKind switch
        {
            AbilityTargetKind.Unit => CastFiring(caster, tag, at, target, _world.Tags.Get(target).TypeId, _world.Positions.Get(target)),
            AbilityTargetKind.Point => CastFiring(caster, tag, at, 0, null, new Position(x, y)),
            _ => new Firing(caster, tag.TypeId, tag.Faction, at.X, at.Y, 0, 0, 0, null, Fix.Zero, Fix.Zero, false, at.X, at.Y),
        };
        foreach (EffectDef effect in ability.Effects)
        {
            Apply(firing, effect);
        }

        Resolve();
    }

    private static Firing CastFiring(int caster, UnitTag tag, Position at, int other, string? otherType, Position center) =>
        new(caster, tag.TypeId, tag.Faction, at.X, at.Y, 0, other, 0, otherType, center.X, center.Y, true, center.X, center.Y);

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

        // The other party may be removed before this firing resolves, so where it stood and what it was are kept now.
        bool otherKnown = _world.Tags.TryGet(other, out UnitTag otherTag) & _world.Positions.TryGet(other, out Position otherAt);
        _queue.Enqueue(new Firing(
            owner, tag.TypeId, tag.Faction, position.X, position.Y, on, other, depth,
            otherKnown ? otherTag.TypeId : null, otherKnown ? otherAt.X : Fix.Zero, otherKnown ? otherAt.Y : Fix.Zero,
            otherKnown, position.X, position.Y));
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
        if (effect.Kind == EffectKind.Summon)
        {
            Summon(firing, effect);
            return;
        }

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
                var center = new Position(firing.CenterX, firing.CenterY);
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

    /// <summary>
    /// Summoned units belong to the owner's faction and take free tiles nearest the anchor, searching rings out to
    /// <see cref="SummonSearchRadius"/> in row order. Units that do not fit are not created. They first act next tick.
    /// </summary>
    private void Summon(in Firing firing, EffectDef effect)
    {
        SummonDef summon = effect.Summon!;
        bool atOther = effect.Target.Shape == TargetShape.Other;
        string? typeId = summon.CopyOther ? firing.OtherType : summon.UnitId;
        if ((atOther && !firing.OtherAnchored) || typeId == null || _content.UnitOrNull(typeId) is not UnitDef def)
        {
            return;
        }

        Fix anchorX = atOther ? firing.OtherX : firing.X;
        Fix anchorY = atOther ? firing.OtherY : firing.Y;
        CollectStandingTiles();
        for (int i = 0; i < effect.Amount; i++)
        {
            if (FreeTileNear(anchorX.FloorToInt(), anchorY.FloorToInt()) is not (int x, int y))
            {
                return;
            }

            _standingTiles.Add(y * _world.Map.Width + x);
            WeaponRef? weapon = def.WeaponId == null ? null : new WeaponRef(def.WeaponId);
            var position = new Position(Fix.FromInt(x) + Fix.Half, Fix.FromInt(y) + Fix.Half);
            int id = _world.Spawn(position, new UnitTag(firing.OwnerFaction, def.Id), new Health(def.Hp, def.Hp, def.Armor), weapon, ProductionSystem.TrainedVisionRange);
            if (summon.LifetimeTicks > 0)
            {
                _world.Lifetimes.Set(id, summon.LifetimeTicks);
            }

            _events.Add(new EffectEvent(firing.Owner, id, EffectKind.Summon, 1, null));
        }
    }

    /// <summary>Tiles under living units now. The occupancy grid is from the start of the tick and still holds the dead.</summary>
    private void CollectStandingTiles()
    {
        _standingTiles.Clear();
        foreach (int id in _world.Positions)
        {
            if (_world.IsAttackable(id) && !_world.Footprints.Has(id))
            {
                Position p = _world.Positions.Get(id);
                _standingTiles.Add(p.Y.FloorToInt() * _world.Map.Width + p.X.FloorToInt());
            }
        }
    }

    private (int X, int Y)? FreeTileNear(int centerX, int centerY)
    {
        for (int radius = 0; radius <= SummonSearchRadius; radius++)
        {
            for (int y = centerY - radius; y <= centerY + radius; y++)
            {
                for (int x = centerX - radius; x <= centerX + radius; x++)
                {
                    bool onRing = Math.Abs(x - centerX) == radius || Math.Abs(y - centerY) == radius;
                    if (onRing && _world.Map.IsPassable(x, y) && !_world.Occupancy.IsStaticBlocked(x, y)
                        && !_standingTiles.Contains(y * _world.Map.Width + x))
                    {
                        return (x, y);
                    }
                }
            }
        }

        return null;
    }

    /// <summary>
    /// <see cref="On"/> is 0 for a cast. <see cref="OtherAnchored"/> says <see cref="OtherX"/> and <see cref="OtherY"/>
    /// are a real place: the other party, or a cast's point. A circle is centred on <see cref="CenterX"/>, <see cref="CenterY"/>.
    /// </summary>
    private readonly record struct Firing(
        int Owner,
        string OwnerType,
        int OwnerFaction,
        Fix X,
        Fix Y,
        TriggerEvent On,
        int Other,
        int Depth,
        string? OtherType,
        Fix OtherX,
        Fix OtherY,
        bool OtherAnchored,
        Fix CenterX,
        Fix CenterY);
}
