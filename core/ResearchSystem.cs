using System.Collections.Generic;

namespace Samjae.Core;

public enum ResearchFailure : byte
{
    MissingBuilding = 1,
    UnderConstruction,
    InvalidTech,
    MissingTech,
    IncompatibleProducer,
    InsufficientResources,
    AlreadyUnlocked,
    QueueFull,
    NothingToCancel,
}

public readonly record struct ResearchEvent(QueueEventKind Kind, int BuildingId, string TechId, int RemainingTicks);

/// <summary>Research queues on buildings. Finishing a job unlocks the tech for the building's faction.</summary>
public sealed class ResearchSystem : ITickSystem
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly Resources _resources;
    private readonly List<ResearchEvent> _events = new();

    public ResearchSystem(World world, GameContent content, Resources resources)
    {
        _world = world;
        _content = content;
        _resources = resources;
    }

    public IReadOnlyList<ResearchEvent> Events => _events;

    public void ClearEvents() => _events.Clear();

    public ResearchFailure? Enqueue(int buildingId, string techId)
    {
        if (!_world.Footprints.Has(buildingId) || !_world.Tags.TryGet(buildingId, out UnitTag tag))
        {
            return ResearchFailure.MissingBuilding;
        }

        if (_world.ConstructionSites.Has(buildingId))
        {
            return ResearchFailure.UnderConstruction;
        }

        if (_world.HasTech(tag.Faction, techId))
        {
            return ResearchFailure.AlreadyUnlocked;
        }

        if (_content.TechOrNull(techId) is not TechDef tech || tech.BuildTicks <= 0)
        {
            return ResearchFailure.InvalidTech;
        }

        if (_content.BuildingOrNull(tag.TypeId) is not BuildingDef producer)
        {
            return ResearchFailure.MissingBuilding;
        }

        ResearchFailure? failure = CheckProducerAndTech(tag, producer, tech) ?? Reserve(buildingId, tag.Faction, producer, tech);
        if (failure == null)
        {
            _events.Add(new ResearchEvent(QueueEventKind.Enqueue, buildingId, techId, tech.BuildTicks));
        }

        return failure;
    }

    public ResearchFailure? CancelLast(int buildingId)
    {
        if (!_world.Footprints.Has(buildingId) || !_world.Tags.TryGet(buildingId, out UnitTag tag))
        {
            return ResearchFailure.MissingBuilding;
        }

        if (!_world.ResearchQueues.TryGet(buildingId, out List<ResearchJob>? queue) || queue.Count == 0)
        {
            return ResearchFailure.NothingToCancel;
        }

        ResearchJob job = queue[^1];
        queue.RemoveAt(queue.Count - 1);
        _resources.Refund(tag.Faction, job.OreCost, job.GasCost);
        _events.Add(new ResearchEvent(QueueEventKind.Cancel, buildingId, job.TechId, job.RemainingTicks));
        if (queue.Count == 0)
        {
            _world.ResearchQueues.Remove(buildingId);
        }

        return null;
    }

    public void Tick()
    {
        foreach (int id in _world.ResearchQueues)
        {
            List<ResearchJob> queue = _world.ResearchQueues.Get(id);
            if (queue.Count == 0 || !_world.Tags.TryGet(id, out UnitTag tag))
            {
                continue;
            }

            ResearchJob job = queue[0];
            job.RemainingTicks--;
            if (job.RemainingTicks > 0)
            {
                _events.Add(new ResearchEvent(QueueEventKind.Progress, id, job.TechId, job.RemainingTicks));
                continue;
            }

            _world.UnlockTech(tag.Faction, job.TechId);
            _events.Add(new ResearchEvent(QueueEventKind.Complete, id, job.TechId, 0));
            queue.RemoveAt(0);
            if (queue.Count == 0)
            {
                _world.ResearchQueues.Remove(id);
            }
        }
    }

    private ResearchFailure? CheckProducerAndTech(UnitTag building, BuildingDef producer, TechDef tech)
    {
        bool producerFits = producer.SupportsResearch
            && (tech.ProducerTypes.Count == 0 || System.Linq.Enumerable.Contains(tech.ProducerTypes, building.TypeId));
        if (!producerFits)
        {
            return ResearchFailure.IncompatibleProducer;
        }

        bool hasTech = Requirements.HasBuildings(_world, building.Faction, tech.RequiredBuildingTypes)
            && Requirements.HasResearch(_world, building.Faction, tech.RequiredResearchIds);
        return hasTech ? null : ResearchFailure.MissingTech;
    }

    private ResearchFailure? Reserve(int buildingId, int faction, BuildingDef producer, TechDef tech)
    {
        _world.ResearchQueues.TryGet(buildingId, out List<ResearchJob>? queue);
        if ((queue?.Count ?? 0) >= producer.ProductionQueueLimit)
        {
            return ResearchFailure.QueueFull;
        }

        if (!_resources.Spend(faction, tech.OreCost, tech.GasCost))
        {
            return ResearchFailure.InsufficientResources;
        }

        if (queue == null)
        {
            queue = new List<ResearchJob>();
            _world.ResearchQueues.Set(buildingId, queue);
        }

        queue.Add(new ResearchJob(tech.Id, tech.BuildTicks, tech.OreCost, tech.GasCost));
        return null;
    }
}
