using System.Collections.Generic;

namespace Samjae.Core;

public enum TrainFailure : byte
{
    MissingBuilding = 1,
    UnderConstruction,
    MissingTech,
    InvalidUnit,
    IncompatibleProducer,
    InsufficientResources,
    QueueFull,
}

public enum QueueEventKind : byte
{
    Enqueue = 1,
    Progress = 2,
    Complete = 3,
    Cancel = 4,
}

public readonly record struct ProductionEvent(QueueEventKind Kind, int BuildingId, string TypeId, int RemainingTicks, int SpawnedId);

/// <summary>Training queues on buildings. A finished unit waits in the queue until a tile around its building is free.</summary>
public sealed class ProductionSystem : ITickSystem
{
    /// <summary>Sight range given to trained units, as in Kotlin.</summary>
    public static readonly Fix TrainedVisionRange = Fix.FromInt(6);

    private readonly World _world;
    private readonly GameContent _content;
    private readonly Resources _resources;
    private readonly List<ProductionEvent> _events = new();

    public ProductionSystem(World world, GameContent content, Resources resources)
    {
        _world = world;
        _content = content;
        _resources = resources;
    }

    public IReadOnlyList<ProductionEvent> Events => _events;

    public void ClearEvents() => _events.Clear();

    public TrainFailure? Enqueue(int buildingId, string typeId)
    {
        if (!_world.Footprints.Has(buildingId) || !_world.Tags.TryGet(buildingId, out UnitTag tag))
        {
            return TrainFailure.MissingBuilding;
        }

        if (_world.ConstructionSites.Has(buildingId))
        {
            return TrainFailure.UnderConstruction;
        }

        if (_content.UnitOrNull(typeId) is not UnitDef unit || !unit.IsTrainable)
        {
            return TrainFailure.InvalidUnit;
        }

        if (_content.BuildingOrNull(tag.TypeId) is not BuildingDef producer)
        {
            return TrainFailure.MissingBuilding;
        }

        TrainFailure? failure = CheckProducerAndTech(tag, producer, unit) ?? Reserve(buildingId, tag.Faction, producer, unit);
        if (failure != null)
        {
            return failure;
        }

        _events.Add(new ProductionEvent(QueueEventKind.Enqueue, buildingId, typeId, unit.BuildTicks, 0));
        return null;
    }

    public bool CancelLast(int buildingId)
    {
        if (!_world.ProductionQueues.TryGet(buildingId, out List<ProductionJob>? queue) || queue.Count == 0
            || !_world.Tags.TryGet(buildingId, out UnitTag tag))
        {
            return false;
        }

        ProductionJob job = queue[^1];
        queue.RemoveAt(queue.Count - 1);
        _resources.Refund(tag.Faction, job.OreCost, job.GasCost);
        _events.Add(new ProductionEvent(QueueEventKind.Cancel, buildingId, job.TypeId, job.RemainingTicks, 0));
        if (queue.Count == 0)
        {
            _world.ProductionQueues.Remove(buildingId);
        }

        return true;
    }

    public void Tick()
    {
        foreach (int id in _world.ProductionQueues)
        {
            List<ProductionJob> queue = _world.ProductionQueues.Get(id);
            if (queue.Count == 0)
            {
                continue;
            }

            ProductionJob job = queue[0];
            job.RemainingTicks = System.Math.Max(0, job.RemainingTicks - 1);
            if (job.RemainingTicks > 0 || !TrySpawn(id, job, out int spawned))
            {
                _events.Add(new ProductionEvent(QueueEventKind.Progress, id, job.TypeId, job.RemainingTicks, 0));
                continue;
            }

            _events.Add(new ProductionEvent(QueueEventKind.Complete, id, job.TypeId, 0, spawned));
            queue.RemoveAt(0);
            if (queue.Count == 0)
            {
                _world.ProductionQueues.Remove(id);
            }
        }
    }

    private TrainFailure? CheckProducerAndTech(UnitTag building, BuildingDef producer, UnitDef unit)
    {
        if (!producer.SupportsTraining || (unit.ProducerTypes.Count > 0 && !Contains(unit.ProducerTypes, building.TypeId)))
        {
            return TrainFailure.IncompatibleProducer;
        }

        bool hasTech = Requirements.HasBuildings(_world, building.Faction, unit.RequiredBuildingTypes)
            && Requirements.HasResearch(_world, building.Faction, unit.RequiredResearchIds);
        return hasTech ? null : TrainFailure.MissingTech;
    }

    private TrainFailure? Reserve(int buildingId, int faction, BuildingDef producer, UnitDef unit)
    {
        _world.ProductionQueues.TryGet(buildingId, out List<ProductionJob>? queue);
        if ((queue?.Count ?? 0) >= producer.ProductionQueueLimit)
        {
            return TrainFailure.QueueFull;
        }

        if (!_resources.Spend(faction, unit.OreCost, unit.GasCost))
        {
            return TrainFailure.InsufficientResources;
        }

        if (queue == null)
        {
            queue = new List<ProductionJob>();
            _world.ProductionQueues.Set(buildingId, queue);
        }

        queue.Add(new ProductionJob(unit.Id, unit.BuildTicks, unit.OreCost, unit.GasCost));
        return null;
    }

    private bool TrySpawn(int buildingId, ProductionJob job, out int spawned)
    {
        spawned = 0;
        if (!_world.Footprints.TryGet(buildingId, out BuildingFootprint footprint) || !_world.Tags.TryGet(buildingId, out UnitTag tag)
            || _content.UnitOrNull(job.TypeId) is not UnitDef def || FindSpawnTile(footprint) is not (int x, int y))
        {
            return false;
        }

        WeaponRef? weapon = def.WeaponId == null ? null : new WeaponRef(def.WeaponId);
        var position = new Position(Fix.FromInt(x) + Fix.Half, Fix.FromInt(y) + Fix.Half);
        spawned = _world.Spawn(position, new UnitTag(tag.Faction, def.Id), new Health(def.Hp, def.Hp, def.Armor), weapon, TrainedVisionRange);
        AddRallyOrder(buildingId, spawned);
        return true;
    }

    private (int X, int Y)? FindSpawnTile(BuildingFootprint footprint)
    {
        int minX = footprint.TileX - 1;
        int minY = footprint.TileY - 1;
        int maxX = footprint.TileX + footprint.Width;
        int maxY = footprint.TileY + footprint.Height;
        for (int y = minY; y <= maxY; y++)
        {
            for (int x = minX; x <= maxX; x++)
            {
                bool onBorder = x == minX || x == maxX || y == minY || y == maxY;
                if (onBorder && _world.Map.IsPassable(x, y) && !_world.Occupancy.IsBlocked(x, y))
                {
                    return (x, y);
                }
            }
        }

        return null;
    }

    private void AddRallyOrder(int buildingId, int unitId)
    {
        List<Order> orders = _world.Orders.Get(unitId);
        if (_world.RallyPoints.TryGet(buildingId, out RallyPoint rally))
        {
            orders.Add(new Order.Move(rally.X, rally.Y));
            return;
        }

        if (!_world.Tags.TryGet(buildingId, out UnitTag tag) || _content.BuildingOrNull(tag.TypeId) is not BuildingDef def
            || !def.SupportsRally || (def.RallyOffsetX == Fix.Zero && def.RallyOffsetY == Fix.Zero))
        {
            return;
        }

        Position building = _world.Positions.Get(buildingId);
        orders.Add(new Order.Move(building.X + def.RallyOffsetX, building.Y + def.RallyOffsetY));
    }

    private static bool Contains(IReadOnlyList<string> list, string value)
    {
        foreach (string item in list)
        {
            if (item == value)
            {
                return true;
            }
        }

        return false;
    }
}
