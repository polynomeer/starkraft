using System.Collections.Generic;

namespace Samjae.Core;

public sealed class Position
{
    public Fix X;
    public Fix Y;

    public Position(Fix x, Fix y)
    {
        X = x;
        Y = y;
    }
}

public sealed record UnitTag(int Faction, string TypeId);

public sealed class Health
{
    public int Hp;
    public int MaxHp;
    public int Armor;

    public Health(int hp, int maxHp, int armor = 0)
    {
        Hp = hp;
        MaxHp = maxHp;
        Armor = armor;
    }
}

public sealed class WeaponRef
{
    public readonly string Id;
    public int CooldownTicks;

    public WeaponRef(string id, int cooldownTicks = 0)
    {
        Id = id;
        CooldownTicks = cooldownTicks;
    }
}

public abstract record Order
{
    public sealed record Move(Fix X, Fix Y) : Order;
    public sealed record AttackMove(Fix X, Fix Y) : Order;
    public sealed record Patrol(Fix AX, Fix AY, Fix BX, Fix BY, bool TowardB = true) : Order;
    public sealed record Hold : Order;
    public sealed record Construct(int Target) : Order;
    public sealed record Attack(int Target) : Order;

    /// <summary>Walk into range and cast. <see cref="Target"/> is 0 unless the ability targets a unit; X and Y are used only for a point.</summary>
    public sealed record Cast(string AbilityId, int Target, Fix X, Fix Y) : Order;
}

public sealed class PathFollow
{
    public int[] Nodes;
    public int Length;
    public int Index;
    public int GoalX;
    public int GoalY;

    public PathFollow(int[] nodes, int length, int index = 0, int goalX = -1, int goalY = -1)
    {
        Nodes = nodes;
        Length = length;
        Index = index;
        GoalX = goalX;
        GoalY = goalY;
    }
}

public sealed record BuildingFootprint(int TileX, int TileY, int Width, int Height, int Clearance = 0);

public sealed class ConstructionSite
{
    public int RemainingTicks;
    public readonly int TotalTicks;
    public readonly int MaxHp;
    public readonly int OreCost;
    public readonly int GasCost;

    public ConstructionSite(int remainingTicks, int totalTicks, int maxHp, int oreCost = 0, int gasCost = 0)
    {
        RemainingTicks = remainingTicks;
        TotalTicks = totalTicks;
        MaxHp = maxHp;
        OreCost = oreCost;
        GasCost = gasCost;
    }
}

public sealed record RallyPoint(Fix X, Fix Y);

public sealed class ResourceStockpile
{
    public int Ore;
    public int Gas;
}

public sealed class ResourceNode
{
    public readonly ResourceKind Kind;
    public int Remaining;
    public readonly int YieldPerTick;

    public ResourceNode(ResourceKind kind, int remaining, int yieldPerTick = 0)
    {
        Kind = kind;
        Remaining = remaining;
        YieldPerTick = yieldPerTick;
    }
}

public sealed class Harvester
{
    public static readonly Fix DefaultRange = Fix.FromRatio(5, 4);
    public static readonly Fix DefaultDropoffRange = Fix.FromRatio(3, 2);

    public int TargetNodeId;
    public readonly int HarvestPerTick;
    public readonly Fix Range;
    public readonly int CargoCapacity;
    public readonly Fix DropoffRange;
    public ResourceKind? CargoKind;
    public int CargoAmount;
    public int ReturnTargetId = -1;

    public Harvester(int targetNodeId, int harvestPerTick, Fix range, int cargoCapacity, Fix dropoffRange)
    {
        TargetNodeId = targetNodeId;
        HarvestPerTick = harvestPerTick;
        Range = range;
        CargoCapacity = cargoCapacity;
        DropoffRange = dropoffRange;
    }

    public Harvester(int targetNodeId)
        : this(targetNodeId, 1, DefaultRange, int.MaxValue, DefaultDropoffRange)
    {
    }
}

public sealed class ProductionJob
{
    public readonly string TypeId;
    public int RemainingTicks;
    public readonly int OreCost;
    public readonly int GasCost;

    public ProductionJob(string typeId, int remainingTicks, int oreCost = 0, int gasCost = 0)
    {
        TypeId = typeId;
        RemainingTicks = remainingTicks;
        OreCost = oreCost;
        GasCost = gasCost;
    }
}

public sealed class ResearchJob
{
    public readonly string TechId;
    public int RemainingTicks;
    public readonly int OreCost;
    public readonly int GasCost;

    public ResearchJob(string techId, int remainingTicks, int oreCost = 0, int gasCost = 0)
    {
        TechId = techId;
        RemainingTicks = remainingTicks;
        OreCost = oreCost;
        GasCost = gasCost;
    }
}

public sealed class StuckTracker
{
    public Fix LastX;
    public Fix LastY;
    public int Ticks;

    public StuckTracker(Fix lastX, Fix lastY, int ticks = 0)
    {
        LastX = lastX;
        LastY = lastY;
        Ticks = ticks;
    }
}

public enum MatchEndReason : byte
{
    Elimination = 1,
    Surrender = 2,
    Timeout = 3,
    Draw = 4,
}

public readonly record struct RemovalEvent(int EntityId, int Faction, string? TypeId, string Reason);
