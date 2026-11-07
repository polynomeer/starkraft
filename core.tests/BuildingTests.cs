using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Ported from BuildingPlacementTest. Its "issue a command" cases call the placement directly; commands arrive in step 6.</summary>
public class BuildingTests
{
    private static Simulation New(GameContent? content = null) =>
        Simulation.Create(new MatchSetup(16, 16, 1UL), content ?? EconomyContent.Standard);

    private static int Place(Simulation s, string type, int x, int y, int faction = 1)
    {
        PlacementResult result = s.Buildings.Place(faction, type, x, y);
        Assert.True(result.Succeeded, $"{type} at ({x},{y}): {result.Failure}");
        return result.EntityId;
    }

    private static int Worker(Simulation s, string type, string x, string y) =>
        s.World.Spawn(new Position(Fix.FromText(x), Fix.FromText(y)), new UnitTag(1, type), new Health(40, 40));

    private static void AssignBuilder(Simulation s, int worker, int site)
    {
        s.World.BuilderTasks.Set(worker, site);
        s.World.Orders.Get(worker).Add(new Order.Construct(site));
    }

    [Fact]
    public void Place_and_remove_building_updates_occupancy()
    {
        Simulation s = New();
        int id = Place(s, "Tall", 4, 5);
        Assert.Equal(1, s.World.Footprints.Count);
        Assert.True(s.World.Occupancy.IsBlocked(4, 5) && s.World.Occupancy.IsBlocked(5, 7));

        Assert.True(s.Buildings.Remove(id));
        Assert.Equal(0, s.World.Footprints.Count);
        Assert.False(s.World.Occupancy.IsBlocked(4, 5) || s.World.Occupancy.IsBlocked(5, 7));
    }

    [Fact]
    public void Building_footprint_blocks_path_tiles()
    {
        Simulation s = New();
        Place(s, "Barracks", 6, 6);
        var finder = new Pathfinder(s.World.Map, s.World.Occupancy);
        int[] output = new int[256];
        int length = finder.FindPath(5, 6, 8, 6, 500, output);
        Assert.True(length > 0);
        Assert.DoesNotContain(output[..length], node => node % 16 is 6 or 7 && node / 16 is 6 or 7);
    }

    [Fact]
    public void Placement_spends_resources_and_fails_when_unaffordable()
    {
        Simulation s = New();
        s.Resources.Set(1, 125);
        Assert.True(s.Buildings.Place(1, "Depot", 2, 2).Succeeded);
        Assert.Equal(BuildFailure.InsufficientResources, s.Buildings.Place(1, "Depot", 6, 2).Failure);
        Assert.Equal(25, s.World.Stockpile(1).Ore);
    }

    // Was "build command places footprint through issue": the price now always comes from data.
    [Fact]
    public void Placement_takes_size_and_price_from_data()
    {
        Simulation s = New();
        s.Resources.Set(1, 150);
        int id = Place(s, "Depot", 8, 8);
        Assert.Equal(new BuildingFootprint(8, 8, 2, 2, 0), s.World.Footprints.Get(id));
        Assert.True(s.World.Occupancy.IsBlocked(8, 8));
        Assert.Equal(50, s.World.Stockpile(1).Ore);
        Assert.Equal(new Position(Fix.FromInt(9), Fix.FromInt(9)).X, s.World.Positions.Get(id).X);
        Assert.Equal(1, s.World.Healths.Get(id).Armor);
    }

    [Fact]
    public void Placement_uses_build_time_from_data()
    {
        Simulation s = New();
        s.Resources.Set(1, 150);
        int id = Place(s, "SlowDepot", 8, 8);
        Assert.Equal(4, s.World.ConstructionSites.Get(id).RemainingTicks);
        Assert.Equal(1, s.World.Healths.Get(id).Hp);
    }

    [Fact]
    public void Unknown_building_type_is_rejected()
    {
        Assert.Equal(BuildFailure.UnknownType, New().Buildings.Place(1, "Castle", 2, 2).Failure);
    }

    [Fact]
    public void Clearance_prevents_adjacent_placement()
    {
        Simulation s = New();
        Place(s, "ClearDepot", 4, 4);
        Assert.Equal(BuildFailure.InvalidPlacement, s.Buildings.Place(1, "ClearDepot", 6, 4).Failure);
        Assert.True(s.Buildings.Place(1, "ClearDepot", 7, 4).Succeeded);
    }

    [Fact]
    public void Placement_rejects_missing_tech_requirements()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        Assert.Equal(BuildFailure.MissingTech, s.Buildings.Place(1, "Factory", 6, 6).Failure);
        Assert.Equal(0, s.World.Footprints.Count);
        Assert.Equal(500, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Construction_progresses_health_and_completes()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int worker = Worker(s, "Worker", "4.5", "3.5");
        int id = Place(s, "SlowDepot", 4, 4);
        AssignBuilder(s, worker, id);
        Assert.Equal(1, s.World.Healths.Get(id).Hp);

        s.Construction.Tick();
        Assert.Equal(100, s.World.Healths.Get(id).Hp);
        Assert.True(s.World.ConstructionSites.Has(id));

        s.Construction.Tick();
        s.Construction.Tick();
        s.Construction.Tick();
        Assert.Equal(400, s.World.Healths.Get(id).Hp);
        Assert.False(s.World.ConstructionSites.Has(id));
    }

    [Fact]
    public void Under_construction_buildings_do_not_satisfy_tech_requirements()
    {
        Simulation s = New(EconomyContent.Build(
            EconomyContent.Worker,
            """
            {"id":"Depot","archetype":"producer","hp":400,"buildMs":100,"footprintWidth":2,"footprintHeight":2},
            {"id":"Factory","archetype":"producer","hp":450,"footprintWidth":2,"footprintHeight":2,"requiredBuildingTypes":["Depot"]}
            """));
        int worker = Worker(s, "Worker", "2.5", "1.5");
        int depot = Place(s, "Depot", 2, 2);
        AssignBuilder(s, worker, depot);
        Assert.Equal(BuildFailure.MissingTech, s.Buildings.Place(1, "Factory", 8, 8).Failure);

        s.Construction.Tick();
        s.Construction.Tick();
        Assert.True(s.Buildings.Place(1, "Factory", 8, 8).Succeeded);
    }

    [Fact]
    public void Construction_does_not_progress_without_an_assigned_worker()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int id = Place(s, "SlowDepot", 4, 4);
        s.Construction.Tick();
        Assert.Equal(1, s.World.Healths.Get(id).Hp);
        Assert.Equal(4, s.World.ConstructionSites.Get(id).RemainingTicks);
    }

    [Fact]
    public void Non_worker_units_do_not_build()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int marine = Worker(s, "Marine", "4.5", "3.5");
        int id = Place(s, "SlowDepot", 4, 4);
        AssignBuilder(s, marine, id);
        s.Construction.Tick();
        Assert.Equal(4, s.World.ConstructionSites.Get(id).RemainingTicks);
    }

    [Fact]
    public void Two_workers_build_twice_as_fast()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int id = Place(s, "SlowDepot", 4, 4);
        AssignBuilder(s, Worker(s, "Worker", "4.5", "3.5"), id);
        AssignBuilder(s, Worker(s, "Worker", "5.5", "3.5"), id);
        s.Construction.Tick();
        Assert.Equal(2, s.World.ConstructionSites.Get(id).RemainingTicks);
    }

    [Fact]
    public void Cancel_build_refunds_and_removes_unfinished_structure()
    {
        Simulation s = New();
        s.Resources.Set(1, 150);
        int id = Place(s, "SlowDepot", 8, 8);
        Assert.Null(s.Buildings.CancelConstruction(id));
        Assert.False(s.World.Footprints.Has(id) || s.World.ConstructionSites.Has(id));
        Assert.False(s.World.Occupancy.IsBlocked(8, 8));
        Assert.Equal(150, s.World.Stockpile(1).Ore);
        Assert.Equal("constructionCanceled", s.World.RemovedEvents.Last().Reason);
    }

    [Fact]
    public void Cancel_build_rejects_completed_structures()
    {
        Simulation s = New();
        s.Resources.Set(1, 150);
        int id = Place(s, "Depot", 8, 8);
        Assert.Equal(CancelConstructionFailure.NotUnderConstruction, s.Buildings.CancelConstruction(id));
        Assert.Equal(CancelConstructionFailure.MissingBuilding, s.Buildings.CancelConstruction(999));
        Assert.True(s.World.Footprints.Has(id));
        Assert.Equal(50, s.World.Stockpile(1).Ore);
    }
}
