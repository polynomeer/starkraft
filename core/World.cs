using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>
/// All game state except the tick and random stream, which <see cref="Simulation"/> owns.
/// Anything that can affect play must also be written by <see cref="WorldCodec"/>, or saves and hashes will miss it.
/// </summary>
public sealed class World
{
    private readonly List<RemovalEvent> _removedEvents = new();
    private readonly List<int> _aliveIds = new();

    public MapGrid Map { get; }
    public OccupancyGrid Occupancy { get; }
    public PathRequestQueue PathRequests { get; }

    public EntityStore<Position> Positions { get; } = new();
    public EntityStore<UnitTag> Tags { get; } = new();
    public EntityStore<Health> Healths { get; } = new();
    public EntityStore<WeaponRef> Weapons { get; } = new();
    public EntityStore<List<Order>> Orders { get; } = new();
    public EntityStore<Fix> VisionRanges { get; } = new();
    public EntityStore<PathFollow> PathFollows { get; } = new();
    public EntityStore<int> RepathCooldowns { get; } = new();
    public EntityStore<BuildingFootprint> Footprints { get; } = new();
    public EntityStore<ConstructionSite> ConstructionSites { get; } = new();
    public EntityStore<int> BuilderTasks { get; } = new();
    public EntityStore<RallyPoint> RallyPoints { get; } = new();
    public EntityStore<List<ProductionJob>> ProductionQueues { get; } = new();
    public EntityStore<List<ResearchJob>> ResearchQueues { get; } = new();
    public EntityStore<StuckTracker> Stucks { get; } = new();
    public EntityStore<ResourceNode> ResourceNodes { get; } = new();
    public EntityStore<Harvester> Harvesters { get; } = new();
    public EntityStore<int> AutoAttackTargets { get; } = new();

    /// <summary>Sorted containers, because these are serialised by walking them.</summary>
    public SortedDictionary<int, ResourceStockpile> Stockpiles { get; } = new();
    public SortedDictionary<int, SortedSet<string>> UnlockedTechs { get; } = new();

    public int NextEntityId { get; internal set; } = 1;
    public bool MatchEnded { get; set; }
    public int? WinnerFaction { get; set; }
    public MatchEndReason? EndReason { get; set; }

    /// <summary>Removals since the last clear, for the presentation layer. Not state: cleared every tick and never read by rules.</summary>
    public IReadOnlyList<RemovalEvent> RemovedEvents => _removedEvents;

    /// <summary>Living entities in ascending id order, as of the start of the tick. Rebuilt every tick, so not state.</summary>
    public IReadOnlyList<int> AliveIds => _aliveIds;

    public World(int mapWidth, int mapHeight, PathSettings? pathSettings = null)
    {
        Map = new MapGrid(mapWidth, mapHeight);
        Occupancy = new OccupancyGrid(mapWidth, mapHeight);
        PathRequests = new PathRequestQueue(pathSettings ?? PathSettings.Default);
    }

    public void UpdateAliveSnapshot()
    {
        _aliveIds.Clear();
        foreach (int id in Positions)
        {
            if (Healths.TryGet(id, out Health health) && health.Hp > 0)
            {
                _aliveIds.Add(id);
            }
        }
    }

    public int Spawn(Position position, UnitTag tag, Health health, WeaponRef? weapon = null, Fix? visionRange = null)
    {
        int id = NextEntityId++;
        Positions.Set(id, position);
        Tags.Set(id, tag);
        Healths.Set(id, health);
        if (weapon != null)
        {
            Weapons.Set(id, weapon);
        }

        Orders.Set(id, new List<Order>());
        if (visionRange is Fix range)
        {
            VisionRanges.Set(id, range);
        }

        RepathCooldowns.Set(id, 0);
        Stucks.Set(id, new StuckTracker(position.X, position.Y));
        return id;
    }

    public void Remove(int id, string reason = "despawn")
    {
        bool tagged = Tags.TryGet(id, out UnitTag tag);
        _removedEvents.Add(new RemovalEvent(id, tagged ? tag.Faction : 0, tagged ? tag.TypeId : null, reason));
        RemoveComponents(id);
        RemoveReferencesTo(BuilderTasks, id);
        RemoveReferencesTo(AutoAttackTargets, id);
    }

    /// <summary>
    /// Whether attacks may target this entity: alive and not a resource node. Kotlin let units shoot resource
    /// nodes, which carry an owner and health; that was not intended play and is dropped here.
    /// </summary>
    public bool IsAttackable(int id) =>
        Healths.TryGet(id, out Health health) && health.Hp > 0 && Positions.Has(id) && Tags.Has(id) && !ResourceNodes.Has(id);

    /// <summary>Attackable and owned by a faction other than <paramref name="faction"/>.</summary>
    public bool IsEnemyOf(int id, int faction) => IsAttackable(id) && Tags.Get(id).Faction != faction;

    public void ClearRemovedEvents() => _removedEvents.Clear();

    public bool UnlockTech(int faction, string techId)
    {
        if (!UnlockedTechs.TryGetValue(faction, out SortedSet<string>? techs))
        {
            techs = new SortedSet<string>(System.StringComparer.Ordinal);
            UnlockedTechs[faction] = techs;
        }

        return techs.Add(techId);
    }

    public bool HasTech(int faction, string techId) =>
        UnlockedTechs.TryGetValue(faction, out SortedSet<string>? techs) && techs.Contains(techId);

    public ResourceStockpile Stockpile(int faction)
    {
        if (!Stockpiles.TryGetValue(faction, out ResourceStockpile? stockpile))
        {
            stockpile = new ResourceStockpile();
            Stockpiles[faction] = stockpile;
        }

        return stockpile;
    }

    /// <summary>Drops a unit's path. Its queued request, if any, stays queued.</summary>
    internal void ClearPath(int id) => PathFollows.Remove(id);

    private void RemoveComponents(int id)
    {
        Positions.Remove(id);
        Tags.Remove(id);
        Healths.Remove(id);
        Weapons.Remove(id);
        Orders.Remove(id);
        VisionRanges.Remove(id);
        PathFollows.Remove(id);
        RepathCooldowns.Remove(id);
        Footprints.Remove(id);
        ConstructionSites.Remove(id);
        BuilderTasks.Remove(id);
        RallyPoints.Remove(id);
        ProductionQueues.Remove(id);
        ResearchQueues.Remove(id);
        Stucks.Remove(id);
        ResourceNodes.Remove(id);
        Harvesters.Remove(id);
        AutoAttackTargets.Remove(id);
    }

    private static void RemoveReferencesTo(EntityStore<int> store, int target)
    {
        foreach (int id in store)
        {
            if (store.Get(id) == target)
            {
                store.Remove(id);
            }
        }
    }
}
