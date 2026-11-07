using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Ported from ProductionSystemTest. Command and label cases (train by label, rally validation) move to step 6.</summary>
public class ProductionTests
{
    private static Simulation New(GameContent? content = null) =>
        Simulation.Create(new MatchSetup(16, 16, 1UL), content ?? EconomyContent.Standard);

    private static int Depot(Simulation s, string type = "Depot")
    {
        PlacementResult result = s.Buildings.Place(1, type, 6, 6);
        Assert.True(result.Succeeded, result.Failure.ToString());
        return result.EntityId;
    }

    private static int[] Marines(Simulation s) =>
        Enumerable.Range(1, s.World.NextEntityId - 1).Where(id => s.World.Tags.TryGet(id, out UnitTag tag) && tag.TypeId == "Marine").ToArray();

    [Fact]
    public void Queue_spawns_a_unit_when_the_timer_completes_and_sends_it_to_the_rally_point()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int depot = Depot(s);
        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(350, s.World.Stockpile(1).Ore);

        s.Production.Tick();
        Assert.Empty(Marines(s));
        Assert.Equal(1, s.World.ProductionQueues.Count);

        s.Production.Tick();
        int marine = Assert.Single(Marines(s));
        Assert.Equal(0, s.World.ProductionQueues.Count);
        Assert.Equal(new Order.Move(Fix.FromInt(11), Fix.FromInt(7)), s.World.Orders.Get(marine).FirstOrDefault());
        Assert.True(s.World.Weapons.Has(marine));
        Assert.Equal(ProductionSystem.TrainedVisionRange, s.World.VisionRanges.Get(marine));
        Assert.Contains(s.Production.Events, e => e.Kind == QueueEventKind.Complete && e.SpawnedId == marine);
    }

    [Fact]
    public void Production_waits_while_the_spawn_ring_is_blocked()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int depot = Depot(s);
        for (int y = 5; y <= 8; y++)
        {
            for (int x = 5; x <= 8; x++)
            {
                if (x == 5 || x == 8 || y == 5 || y == 8)
                {
                    s.World.Occupancy.AddDynamic(x, y);
                }
            }
        }

        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        s.Production.Tick();
        s.Production.Tick();
        Assert.Empty(Marines(s));
        Assert.Equal(1, s.World.ProductionQueues.Count);

        s.World.Occupancy.ClearDynamic();
        s.Production.Tick();
        Assert.Single(Marines(s));
    }

    [Fact]
    public void Enqueue_fails_when_resources_are_insufficient()
    {
        Simulation s = New();
        s.Resources.Set(1, 120);
        int depot = Depot(s);
        Assert.Equal(TrainFailure.InsufficientResources, s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(20, s.World.Stockpile(1).Ore);
        Assert.Equal(0, s.World.ProductionQueues.Count);
    }

    [Fact]
    public void Cancel_refunds_and_clears_the_queue_tail()
    {
        Simulation s = New();
        s.Resources.Set(1, 300);
        int depot = Depot(s);
        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(150, s.World.Stockpile(1).Ore);
        Assert.True(s.Production.CancelLast(depot));
        Assert.Equal(200, s.World.Stockpile(1).Ore);
        Assert.Equal(0, s.World.ProductionQueues.Count);
        Assert.False(s.Production.CancelLast(depot));
    }

    [Fact]
    public void Queue_enforces_its_limit()
    {
        Simulation s = New();
        s.Resources.Set(1, 1000);
        int depot = Depot(s);
        for (int i = 0; i < 3; i++)
        {
            Assert.Null(s.Production.Enqueue(depot, "Marine"));
        }

        Assert.Equal(TrainFailure.QueueFull, s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(750, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Producer_under_construction_rejects_training()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int depot = Depot(s, "SlowDepot");
        Assert.Equal(TrainFailure.UnderConstruction, s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(400, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Rally_point_overrides_the_default_rally_offset()
    {
        Simulation s = New();
        s.Resources.Set(1, 300);
        int depot = Depot(s);
        s.World.RallyPoints.Set(depot, new RallyPoint(Fix.FromInt(15), Fix.FromInt(16)));
        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        s.Production.Tick();
        s.Production.Tick();
        Assert.Equal(new Order.Move(Fix.FromInt(15), Fix.FromInt(16)), s.World.Orders.Get(Marines(s)[0]).FirstOrDefault());
    }

    [Fact]
    public void Producer_without_training_support_rejects_enqueue()
    {
        Simulation s = New(EconomyContent.Build(
            EconomyContent.Marine.Replace("[\"Depot\"]", "[\"Tower\"]"),
            """{"id":"Tower","archetype":"defense","hp":300,"footprintWidth":2,"footprintHeight":2,"productionQueueLimit":0}"""));
        s.Resources.Set(1, 500);
        Assert.Equal(TrainFailure.IncompatibleProducer, s.Production.Enqueue(Depot(s, "Tower"), "Marine"));
    }

    // Was "build and train commands can resolve defaults from data": time and price always come from data now.
    [Fact]
    public void Training_takes_time_and_price_from_data()
    {
        Simulation s = New();
        s.Resources.Set(1, 300);
        int depot = Depot(s);
        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        ProductionJob job = s.World.ProductionQueues.Get(depot)[0];
        Assert.Equal((2, 50), (job.RemainingTicks, job.OreCost));
        Assert.Equal(150, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Enqueue_rejects_an_incompatible_producer_type()
    {
        Simulation s = New(EconomyContent.Build(
            EconomyContent.Marine,
            EconomyContent.Depot + "," + """{"id":"Factory","archetype":"producer","hp":500,"footprintWidth":2,"footprintHeight":2,"supportsTraining":true,"oreCost":100}"""));
        s.Resources.Set(1, 500);
        int factory = Depot(s, "Factory");
        Assert.Equal(TrainFailure.IncompatibleProducer, s.Production.Enqueue(factory, "Marine"));
        Assert.Equal(400, s.World.Stockpile(1).Ore);
        Assert.Equal(0, s.World.ProductionQueues.Count);
    }

    [Fact]
    public void Enqueue_rejects_missing_tech_requirements()
    {
        Simulation s = New(EconomyContent.Build(
            EconomyContent.Marine.Replace("\"producerTypes\":[\"Depot\"]", "\"producerTypes\":[\"Depot\"],\"requiredBuildingTypes\":[\"Academy\"]"),
            EconomyContent.Depot + "," + """{"id":"Academy","archetype":"tech","hp":250,"footprintWidth":2,"footprintHeight":2}"""));
        s.Resources.Set(1, 500);
        int depot = Depot(s);
        Assert.Equal(TrainFailure.MissingTech, s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(400, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Unknown_or_untrainable_units_are_rejected()
    {
        Simulation s = New(EconomyContent.Build(
            EconomyContent.Marine + "," + """{"id":"Statue","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1}""",
            EconomyContent.Depot));
        s.Resources.Set(1, 500);
        int depot = Depot(s);
        Assert.Equal(TrainFailure.InvalidUnit, s.Production.Enqueue(depot, "Nobody"));
        Assert.Equal(TrainFailure.InvalidUnit, s.Production.Enqueue(depot, "Statue"));
        Assert.Equal(TrainFailure.MissingBuilding, s.Production.Enqueue(999, "Marine"));
    }
}
