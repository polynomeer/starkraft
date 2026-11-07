using System.Collections.Generic;

namespace Samjae.Core;

public enum ResourceChangeKind : byte
{
    Spend = 1,
    Refund = 2,
}

public readonly record struct ResourceChange(ResourceChangeKind Kind, int Faction, int Ore, int Gas);

/// <summary>Faction stockpiles. Changes are recorded for presentation and cleared at the start of each tick.</summary>
public sealed class Resources
{
    private readonly World _world;
    private readonly List<ResourceChange> _changes = new();

    public Resources(World world) => _world = world;

    public IReadOnlyList<ResourceChange> Changes => _changes;

    public void ClearChanges() => _changes.Clear();

    public void Set(int faction, int ore, int gas = 0)
    {
        ResourceStockpile stockpile = _world.Stockpile(faction);
        stockpile.Ore = ore;
        stockpile.Gas = gas;
    }

    public bool CanAfford(int faction, int ore, int gas = 0)
    {
        ResourceStockpile stockpile = _world.Stockpile(faction);
        return stockpile.Ore >= ore && stockpile.Gas >= gas;
    }

    public bool Spend(int faction, int ore, int gas = 0)
    {
        if (!CanAfford(faction, ore, gas))
        {
            return false;
        }

        ResourceStockpile stockpile = _world.Stockpile(faction);
        stockpile.Ore -= ore;
        stockpile.Gas -= gas;
        _changes.Add(new ResourceChange(ResourceChangeKind.Spend, faction, ore, gas));
        return true;
    }

    public void Refund(int faction, int ore, int gas = 0)
    {
        ResourceStockpile stockpile = _world.Stockpile(faction);
        stockpile.Ore += ore;
        stockpile.Gas += gas;
        _changes.Add(new ResourceChange(ResourceChangeKind.Refund, faction, ore, gas));
    }
}

/// <summary>Tech gates shared by building, training and research.</summary>
public static class Requirements
{
    /// <summary>Finished buildings only: a construction site does not count.</summary>
    public static bool HasBuildings(World world, int faction, IReadOnlyList<string> requiredTypes)
    {
        foreach (string type in requiredTypes)
        {
            if (!HasFinishedBuilding(world, faction, type))
            {
                return false;
            }
        }

        return true;
    }

    public static bool HasResearch(World world, int faction, IReadOnlyList<string> requiredIds)
    {
        foreach (string tech in requiredIds)
        {
            if (!world.HasTech(faction, tech))
            {
                return false;
            }
        }

        return true;
    }

    private static bool HasFinishedBuilding(World world, int faction, string type)
    {
        foreach (int id in world.Footprints)
        {
            if (!world.ConstructionSites.Has(id) && world.Tags.TryGet(id, out UnitTag tag)
                && tag.Faction == faction && tag.TypeId == type)
            {
                return true;
            }
        }

        return false;
    }
}
