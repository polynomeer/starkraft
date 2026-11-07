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
    IReadOnlyList<string> RequiredResearchIds)
{
    public bool IsTrainable => BuildTicks > 0;
}

public sealed record WeaponDef(string Id, int Damage, Fix Range, int CooldownTicks);

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

    public IReadOnlyList<UnitDef> Units { get; }
    public IReadOnlyList<WeaponDef> Weapons { get; }
    public IReadOnlyList<BuildingDef> Buildings { get; }
    public IReadOnlyList<TechDef> Techs { get; }
    public ulong Hash { get; }

    public GameContent(
        IReadOnlyList<UnitDef> units,
        IReadOnlyList<WeaponDef> weapons,
        IReadOnlyList<BuildingDef> buildings,
        IReadOnlyList<TechDef> techs)
    {
        Units = units;
        Weapons = weapons;
        Buildings = buildings;
        Techs = techs;
        Index(units, _units, def => def.Id, "unit");
        Index(weapons, _weapons, def => def.Id, "weapon");
        Index(buildings, _buildings, def => def.Id, "building");
        Index(techs, _techs, def => def.Id, "tech");
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

    /// <summary>Rounds up, so a timer is never shorter than the data asks for at any tick rate.</summary>
    public static int MillisecondsToTicks(int milliseconds) =>
        checked((int)(((long)milliseconds * Simulation.TicksPerSecond + 999) / 1000));

    private static void Index<T>(IReadOnlyList<T> defs, Dictionary<string, T> index, Func<T, string> key, string kind)
    {
        foreach (T def in defs)
        {
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
        }

        foreach (WeaponDef w in Weapons)
        {
            writer.Write(w.Id);
            WriteInts(writer, w.Damage, w.Range.Raw, w.CooldownTicks);
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
