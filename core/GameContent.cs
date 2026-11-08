using System;
using System.Collections.Generic;
using System.IO;

namespace Samjae.Core;

public enum ResourceKind : byte
{
    Ore = 0,
    Gas = 1,
}

public sealed record UnitDef(
    string Id,
    string Archetype,
    int Hp,
    int Armor,
    Fix Radius,
    Fix SpeedPerTick,
    string? WeaponId,
    int OreCost,
    int GasCost,
    int BuildTicks,
    int FootprintWidth,
    int FootprintHeight,
    IReadOnlyList<string> ProducerTypes,
    IReadOnlyList<string> RequiredBuildingTypes,
    IReadOnlyList<string> RequiredResearchIds,
    IReadOnlyList<TriggerDef> Triggers,
    IReadOnlyList<string> Abilities,
    MoveLayer Layer)
{
    public bool IsTrainable => BuildTicks > 0;
}

public sealed record WeaponDef(string Id, int Damage, Fix Range, int CooldownTicks, LayerMask Targets);

public sealed record BuildingDef(
    string Id,
    string Archetype,
    int Hp,
    int BuildTicks,
    int Armor,
    int FootprintWidth,
    int FootprintHeight,
    int PlacementClearance,
    bool SupportsTraining,
    bool SupportsResearch,
    bool SupportsRally,
    bool SupportsDropoff,
    IReadOnlyList<ResourceKind> DropoffResourceKinds,
    int ProductionQueueLimit,
    Fix RallyOffsetX,
    Fix RallyOffsetY,
    int OreCost,
    int GasCost,
    IReadOnlyList<string> RequiredBuildingTypes,
    IReadOnlyList<string> RequiredResearchIds);

public sealed record TechDef(
    string Id,
    int BuildTicks,
    int OreCost,
    int GasCost,
    IReadOnlyList<string> ProducerTypes,
    IReadOnlyList<string> RequiredBuildingTypes,
    IReadOnlyList<string> RequiredResearchIds);

/// <summary>Validated, tick-converted game data. Immutable, and identified by <see cref="Hash"/> so saves cannot load against other data.</summary>
public sealed class GameContent
{
    private readonly Dictionary<string, UnitDef> _units = new();
    private readonly Dictionary<string, WeaponDef> _weapons = new();
    private readonly Dictionary<string, BuildingDef> _buildings = new();
    private readonly Dictionary<string, TechDef> _techs = new();
    private readonly Dictionary<string, StatusDef> _statuses = new();
    private readonly Dictionary<string, AbilityDef> _abilities = new();

    public IReadOnlyList<UnitDef> Units { get; }
    public IReadOnlyList<WeaponDef> Weapons { get; }
    public IReadOnlyList<BuildingDef> Buildings { get; }
    public IReadOnlyList<TechDef> Techs { get; }
    public IReadOnlyList<StatusDef> Statuses { get; }
    public IReadOnlyList<AbilityDef> Abilities { get; }
    public ulong Hash { get; }

    public GameContent(
        IReadOnlyList<UnitDef> units,
        IReadOnlyList<WeaponDef> weapons,
        IReadOnlyList<BuildingDef> buildings,
        IReadOnlyList<TechDef> techs,
        IReadOnlyList<StatusDef>? statuses = null,
        IReadOnlyList<AbilityDef>? abilities = null)
    {
        Units = units;
        Weapons = weapons;
        Buildings = buildings;
        Techs = techs;
        Statuses = statuses ?? Array.Empty<StatusDef>();
        Abilities = abilities ?? Array.Empty<AbilityDef>();
        Index(units, _units, def => def.Id, "unit");
        Index(weapons, _weapons, def => def.Id, "weapon");
        Index(buildings, _buildings, def => def.Id, "building");
        Index(techs, _techs, def => def.Id, "tech");
        Index(Statuses, _statuses, def => def.Id, "status");
        Index(Abilities, _abilities, def => def.Id, "ability");
        ValidateReferences();
        Hash = ComputeHash();
    }

    /// <summary>Body radius for entity types with no unit definition, such as test dummies.</summary>
    public static readonly Fix DefaultRadius = Fix.FromRatio(2, 5);

    public UnitDef? UnitOrNull(string id) => _units.GetValueOrDefault(id);

    public Fix RadiusOf(string typeId) => UnitOrNull(typeId)?.Radius ?? DefaultRadius;
    public WeaponDef? WeaponOrNull(string id) => _weapons.GetValueOrDefault(id);
    public BuildingDef? BuildingOrNull(string id) => _buildings.GetValueOrDefault(id);
    public TechDef? TechOrNull(string id) => _techs.GetValueOrDefault(id);
    public StatusDef? StatusOrNull(string id) => _statuses.GetValueOrDefault(id);
    public AbilityDef? AbilityOrNull(string id) => _abilities.GetValueOrDefault(id);

    /// <summary>Rounds up, so a timer is never shorter than the data asks for at any tick rate.</summary>
    public static int MillisecondsToTicks(int milliseconds) =>
        checked((int)(((long)milliseconds * Simulation.TicksPerSecond + 999) / 1000));

    private static void Index<T>(IReadOnlyList<T> defs, Dictionary<string, T> index, Func<T, string> key, string kind)
    {
        foreach (T def in defs)
        {
            if (key(def).Length == 0)
            {
                throw new InvalidDataException($"A {kind} has an empty id.");
            }

            if (!index.TryAdd(key(def), def))
            {
                throw new InvalidDataException($"Duplicate {kind} id '{key(def)}'.");
            }
        }
    }

    private void ValidateReferences()
    {
        foreach (UnitDef unit in Units)
        {
            string where = $"unit '{unit.Id}'";
            if (unit.WeaponId != null)
            {
                RequireKnown(_weapons, unit.WeaponId, where, "weapon");
            }

            RequireAllKnown(_buildings, unit.ProducerTypes, where, "producer");
            RequireAllKnown(_buildings, unit.RequiredBuildingTypes, where, "required building");
            RequireAllKnown(_techs, unit.RequiredResearchIds, where, "required research");
            ValidateTriggers(unit, where);
            ValidateAbilityList(unit, where);
        }

        foreach (AbilityDef ability in Abilities)
        {
            ValidateEffects(ability.Effects, $"ability '{ability.Id}'");
        }

        foreach (BuildingDef building in Buildings)
        {
            string where = $"building '{building.Id}'";
            RequireAllKnown(_buildings, building.RequiredBuildingTypes, where, "required building");
            RequireAllKnown(_techs, building.RequiredResearchIds, where, "required research");
        }

        foreach (TechDef tech in Techs)
        {
            string where = $"tech '{tech.Id}'";
            RequireAllKnown(_buildings, tech.ProducerTypes, where, "producer");
            RequireAllKnown(_buildings, tech.RequiredBuildingTypes, where, "required building");
            RequireAllKnown(_techs, tech.RequiredResearchIds, where, "required research");
        }
    }

    private void ValidateTriggers(UnitDef unit, string where)
    {
        var seen = new HashSet<TriggerEvent>();
        foreach (TriggerDef trigger in unit.Triggers)
        {
            if (!seen.Add(trigger.On))
            {
                throw new InvalidDataException($"{where} has more than one '{trigger.On}' trigger.");
            }

            ValidateEffects(trigger.Effects, where);
        }
    }

    private void ValidateAbilityList(UnitDef unit, string where)
    {
        var seen = new HashSet<string>();
        foreach (string id in unit.Abilities)
        {
            RequireKnown(_abilities, id, where, "ability");
            if (!seen.Add(id))
            {
                throw new InvalidDataException($"{where} lists ability '{id}' more than once.");
            }
        }
    }

    private void ValidateEffects(IReadOnlyList<EffectDef> effects, string where)
    {
        foreach (EffectDef effect in effects)
        {
            if (effect.Kind == EffectKind.ApplyStatus)
            {
                RequireKnown(_statuses, effect.StatusId!, where, "status");
            }

            if (effect.Summon?.UnitId is string summoned)
            {
                RequireKnown(_units, summoned, where, "summoned unit");
            }
        }
    }

    private static void RequireAllKnown<T>(Dictionary<string, T> index, IReadOnlyList<string> ids, string where, string role)
    {
        foreach (string id in ids)
        {
            RequireKnown(index, id, where, role);
        }
    }

    private static void RequireKnown<T>(Dictionary<string, T> index, string id, string where, string role)
    {
        if (!index.ContainsKey(id))
        {
            throw new InvalidDataException($"{where} names unknown {role} '{id}'.");
        }
    }

    private ulong ComputeHash()
    {
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);
        foreach (UnitDef u in Units)
        {
            writer.Write(u.Id);
            writer.Write(u.Archetype);
            WriteInts(writer, u.Hp, u.Armor, u.Radius.Raw, u.SpeedPerTick.Raw, u.OreCost, u.GasCost, u.BuildTicks, u.FootprintWidth, u.FootprintHeight);
            writer.Write(u.WeaponId ?? "");
            WriteLists(writer, u.ProducerTypes, u.RequiredBuildingTypes, u.RequiredResearchIds);
            WriteTriggers(writer, u.Triggers);
            WriteLists(writer, u.Abilities);
            writer.Write((byte)u.Layer);
        }

        foreach (WeaponDef w in Weapons)
        {
            writer.Write(w.Id);
            WriteInts(writer, w.Damage, w.Range.Raw, w.CooldownTicks);
            writer.Write((byte)w.Targets);
        }

        foreach (BuildingDef b in Buildings)
        {
            WriteBuilding(writer, b);
        }

        foreach (TechDef t in Techs)
        {
            writer.Write(t.Id);
            WriteInts(writer, t.BuildTicks, t.OreCost, t.GasCost);
            WriteLists(writer, t.ProducerTypes, t.RequiredBuildingTypes, t.RequiredResearchIds);
        }

        foreach (StatusDef s in Statuses)
        {
            writer.Write(s.Id);
            WriteInts(writer, s.DurationTicks, s.MaxStacks, s.DamagePerStack, s.ArmorPerStack, s.SpeedPercentPerStack);
        }

        foreach (AbilityDef a in Abilities)
        {
            writer.Write(a.Id);
            writer.Write((byte)a.TargetKind);
            writer.Write((byte)a.TargetFaction);
            WriteInts(writer, a.Range.Raw, a.CooldownTicks);
            WriteEffects(writer, a.Effects);
        }

        writer.Flush();
        return StateHash.Compute(stream.ToArray());
    }

    private static void WriteBuilding(BinaryWriter writer, BuildingDef b)
    {
        writer.Write(b.Id);
        writer.Write(b.Archetype);
        WriteInts(writer, b.Hp, b.BuildTicks, b.Armor, b.FootprintWidth, b.FootprintHeight, b.PlacementClearance);
        writer.Write(b.SupportsTraining);
        writer.Write(b.SupportsResearch);
        writer.Write(b.SupportsRally);
        writer.Write(b.SupportsDropoff);
        writer.Write(b.DropoffResourceKinds.Count);
        foreach (ResourceKind kind in b.DropoffResourceKinds)
        {
            writer.Write((byte)kind);
        }

        WriteInts(writer, b.ProductionQueueLimit, b.RallyOffsetX.Raw, b.RallyOffsetY.Raw, b.OreCost, b.GasCost);
        WriteLists(writer, b.RequiredBuildingTypes, b.RequiredResearchIds);
    }

    private static void WriteTriggers(BinaryWriter writer, IReadOnlyList<TriggerDef> triggers)
    {
        writer.Write(triggers.Count);
        foreach (TriggerDef trigger in triggers)
        {
            writer.Write((byte)trigger.On);
            WriteEffects(writer, trigger.Effects);
        }
    }

    private static void WriteEffects(BinaryWriter writer, IReadOnlyList<EffectDef> effects)
    {
        writer.Write(effects.Count);
        foreach (EffectDef e in effects)
        {
            writer.Write((byte)e.Kind);
            writer.Write((byte)e.Target.Shape);
            writer.Write((byte)e.Target.Faction);
            WriteInts(writer, e.Target.Radius.Raw, e.Amount);
            writer.Write(e.StatusId ?? "");
            writer.Write(e.Summon?.UnitId ?? "");
            writer.Write(e.Summon?.CopyOther ?? false);
            writer.Write(e.Summon?.LifetimeTicks ?? 0);
        }
    }

    private static void WriteInts(BinaryWriter writer, params int[] values)
    {
        foreach (int value in values)
        {
            writer.Write(value);
        }
    }

    private static void WriteLists(BinaryWriter writer, params IReadOnlyList<string>[] lists)
    {
        foreach (IReadOnlyList<string> list in lists)
        {
            writer.Write(list.Count);
            foreach (string item in list)
            {
                writer.Write(item);
            }
        }
    }
}
