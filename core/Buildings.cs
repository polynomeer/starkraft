namespace Samjae.Core;

public enum BuildFailure : byte
{
    UnknownType = 1,
    MissingTech,
    InvalidPlacement,
    InsufficientResources,
}

public enum CancelConstructionFailure : byte
{
    MissingBuilding = 1,
    NotUnderConstruction,
}

public readonly record struct PlacementResult(int EntityId, BuildFailure? Failure)
{
    public bool Succeeded => Failure == null;
}

/// <summary>
/// Places and removes buildings. Size, health, time and cost always come from the building definition, never from the
/// caller. Kotlin took them from the command, which let a client choose its own price.
/// </summary>
public sealed class BuildingPlacement
{
    private readonly World _world;
    private readonly GameContent _content;
    private readonly Resources _resources;

    public BuildingPlacement(World world, GameContent content, Resources resources)
    {
        _world = world;
        _content = content;
        _resources = resources;
    }

    public bool CanPlace(int tileX, int tileY, int width, int height, int clearance = 0)
    {
        for (int y = tileY - clearance; y < tileY + height + clearance; y++)
        {
            for (int x = tileX - clearance; x < tileX + width + clearance; x++)
            {
                if (!_world.Map.IsPassable(x, y) || _world.Occupancy.IsBlocked(x, y))
                {
                    return false;
                }
            }
        }

        return true;
    }

    public PlacementResult Place(int faction, string typeId, int tileX, int tileY)
    {
        if (_content.BuildingOrNull(typeId) is not BuildingDef def)
        {
            return new PlacementResult(0, BuildFailure.UnknownType);
        }

        if (!Requirements.HasBuildings(_world, faction, def.RequiredBuildingTypes)
            || !Requirements.HasResearch(_world, faction, def.RequiredResearchIds))
        {
            return new PlacementResult(0, BuildFailure.MissingTech);
        }

        if (!CanPlace(tileX, tileY, def.FootprintWidth, def.FootprintHeight, def.PlacementClearance))
        {
            return new PlacementResult(0, BuildFailure.InvalidPlacement);
        }

        if (!_resources.Spend(faction, def.OreCost, def.GasCost))
        {
            return new PlacementResult(0, BuildFailure.InsufficientResources);
        }

        return new PlacementResult(Spawn(faction, def, tileX, tileY), null);
    }

    public CancelConstructionFailure? CancelConstruction(int id, string reason = "constructionCanceled")
    {
        if (!_world.ConstructionSites.TryGet(id, out ConstructionSite site))
        {
            return _world.Footprints.Has(id) ? CancelConstructionFailure.NotUnderConstruction : CancelConstructionFailure.MissingBuilding;
        }

        if (!_world.Tags.TryGet(id, out UnitTag tag))
        {
            return CancelConstructionFailure.MissingBuilding;
        }

        _resources.Refund(tag.Faction, site.OreCost, site.GasCost);
        Remove(id, reason);
        return null;
    }

    public bool Remove(int id, string reason = "buildingRemoved")
    {
        if (!_world.Footprints.TryGet(id, out BuildingFootprint footprint))
        {
            return false;
        }

        ForEachTile(footprint, (x, y) => _world.Occupancy.RemoveStatic(x, y));
        _world.Remove(id, reason);
        return true;
    }

    private int Spawn(int faction, BuildingDef def, int tileX, int tileY)
    {
        var footprint = new BuildingFootprint(tileX, tileY, def.FootprintWidth, def.FootprintHeight, def.PlacementClearance);
        ForEachTile(footprint, (x, y) => _world.Occupancy.AddStatic(x, y));
        var centre = new Position(
            Fix.FromInt(tileX) + Fix.FromRatio(def.FootprintWidth, 2),
            Fix.FromInt(tileY) + Fix.FromRatio(def.FootprintHeight, 2));
        bool needsConstruction = def.BuildTicks > 0;
        int id = _world.Spawn(centre, new UnitTag(faction, def.Id), new Health(needsConstruction ? 1 : def.Hp, def.Hp, def.Armor));
        _world.Footprints.Set(id, footprint);
        if (needsConstruction)
        {
            _world.ConstructionSites.Set(id, new ConstructionSite(def.BuildTicks, def.BuildTicks, def.Hp, def.OreCost, def.GasCost));
        }

        return id;
    }

    private static void ForEachTile(BuildingFootprint footprint, System.Action<int, int> visit)
    {
        for (int y = footprint.TileY; y < footprint.TileY + footprint.Height; y++)
        {
            for (int x = footprint.TileX; x < footprint.TileX + footprint.Width; x++)
            {
                visit(x, y);
            }
        }
    }
}

/// <summary>Advances construction sites by one tick per worker standing near the site with a construct order for it.</summary>
public sealed class ConstructionSystem : ITickSystem
{
    public const string WorkerArchetype = "worker";

    private static readonly Fix WorkerReach = Fix.FromRatio(3, 2);

    private readonly World _world;
    private readonly GameContent _content;

    public ConstructionSystem(World world, GameContent content)
    {
        _world = world;
        _content = content;
    }

    public void Tick()
    {
        foreach (int id in _world.ConstructionSites)
        {
            if (!_world.Healths.TryGet(id, out Health health))
            {
                continue;
            }

            ConstructionSite site = _world.ConstructionSites.Get(id);
            int builders = AssignedBuilderCount(id);
            if (site.RemainingTicks > 0 && builders > 0)
            {
                site.RemainingTicks = System.Math.Max(0, site.RemainingTicks - builders);
            }

            int progressed = site.TotalTicks - site.RemainingTicks;
            int targetHp = System.Math.Max(1, (site.MaxHp * progressed + site.TotalTicks - 1) / site.TotalTicks);
            health.Hp = System.Math.Max(health.Hp, targetHp);
            health.MaxHp = site.MaxHp;
            if (site.RemainingTicks <= 0)
            {
                health.Hp = site.MaxHp;
                _world.ConstructionSites.Remove(id);
            }
        }
    }

    private int AssignedBuilderCount(int buildingId)
    {
        if (!_world.Tags.TryGet(buildingId, out UnitTag buildingTag) || !_world.Footprints.TryGet(buildingId, out BuildingFootprint footprint))
        {
            return 0;
        }

        int count = 0;
        foreach (int id in _world.BuilderTasks)
        {
            if (_world.BuilderTasks.Get(id) == buildingId && IsWorkingOn(id, buildingId, buildingTag.Faction, footprint))
            {
                count++;
            }
        }

        return count;
    }

    private bool IsWorkingOn(int id, int buildingId, int faction, BuildingFootprint footprint)
    {
        if (!_world.Positions.TryGet(id, out Position position) || !_world.Tags.TryGet(id, out UnitTag tag)
            || tag.Faction != faction || !_world.Healths.TryGet(id, out Health health) || health.Hp <= 0
            || _content.UnitOrNull(tag.TypeId)?.Archetype != WorkerArchetype)
        {
            return false;
        }

        if (!_world.Orders.TryGet(id, out var orders) || orders.Count == 0
            || orders[0] is not Order.Construct construct || construct.Target != buildingId)
        {
            return false;
        }

        return position.X >= Fix.FromInt(footprint.TileX) - WorkerReach
            && position.X <= Fix.FromInt(footprint.TileX + footprint.Width) + WorkerReach
            && position.Y >= Fix.FromInt(footprint.TileY) - WorkerReach
            && position.Y <= Fix.FromInt(footprint.TileY + footprint.Height) + WorkerReach;
    }
}
