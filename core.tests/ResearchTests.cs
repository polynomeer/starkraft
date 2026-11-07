using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Ported from ResearchSystemTest.</summary>
public class ResearchTests
{
    private static readonly GameContent Data = EconomyContent.Build(
        EconomyContent.Marine.Replace("\"producerTypes\":[\"Depot\"]", "\"producerTypes\":[\"Depot\"],\"requiredResearchIds\":[\"AdvancedTraining\"]"),
        EconomyContent.Depot + "," +
        """
        {"id":"SlowDepot","archetype":"producer","hp":400,"buildMs":100,"footprintWidth":2,"footprintHeight":2,"supportsResearch":true,"productionQueueLimit":3},
        {"id":"Academy","archetype":"tech","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"requiredResearchIds":["AdvancedTraining"]}
        """,
        EconomyContent.AdvancedTraining);

    private static (Simulation Sim, int Depot) New(string type = "Depot")
    {
        Simulation s = Simulation.Create(new MatchSetup(16, 16, 1UL), Data);
        s.Resources.Set(1, 500);
        PlacementResult depot = s.Buildings.Place(1, type, 4, 4);
        Assert.True(depot.Succeeded);
        return (s, depot.EntityId);
    }

    [Fact]
    public void Research_completes_and_unlocks_faction_tech()
    {
        (Simulation s, int depot) = New();
        Assert.Null(s.Research.Enqueue(depot, "AdvancedTraining"));
        s.Research.Tick();
        s.Research.Tick();
        Assert.False(s.World.HasTech(1, "AdvancedTraining"));
        s.Research.Tick();
        Assert.True(s.World.HasTech(1, "AdvancedTraining"));
        Assert.False(s.World.HasTech(2, "AdvancedTraining"));
        Assert.Equal(0, s.World.ResearchQueues.Count);
    }

    [Fact]
    public void Unlock_enables_gated_training_and_building()
    {
        (Simulation s, int depot) = New();
        s.Resources.Set(1, 1000);
        Assert.Equal(TrainFailure.MissingTech, s.Production.Enqueue(depot, "Marine"));
        Assert.Equal(BuildFailure.MissingTech, s.Buildings.Place(1, "Academy", 8, 4).Failure);

        Assert.Null(s.Research.Enqueue(depot, "AdvancedTraining"));
        for (int i = 0; i < 3; i++)
        {
            s.Research.Tick();
        }

        Assert.Null(s.Production.Enqueue(depot, "Marine"));
        Assert.True(s.Buildings.Place(1, "Academy", 8, 4).Succeeded);
        Assert.Equal(ResearchFailure.AlreadyUnlocked, s.Research.Enqueue(depot, "AdvancedTraining"));
    }

    [Fact]
    public void Producer_under_construction_cannot_research()
    {
        (Simulation s, int depot) = New("SlowDepot");
        Assert.Equal(ResearchFailure.UnderConstruction, s.Research.Enqueue(depot, "AdvancedTraining"));
    }

    [Fact]
    public void Cancel_research_refunds_and_removes_the_queued_job()
    {
        (Simulation s, int depot) = New();
        Assert.Null(s.Research.Enqueue(depot, "AdvancedTraining"));
        Assert.Equal(325, s.World.Stockpile(1).Ore);
        s.Research.ClearEvents();

        Assert.Null(s.Research.CancelLast(depot));
        Assert.Equal(400, s.World.Stockpile(1).Ore);
        Assert.Equal(0, s.World.ResearchQueues.Count);
        Assert.Equal(QueueEventKind.Cancel, Assert.Single(s.Research.Events).Kind);
    }

    [Fact]
    public void Cancel_research_fails_when_the_queue_is_empty()
    {
        (Simulation s, int depot) = New();
        Assert.Equal(ResearchFailure.NothingToCancel, s.Research.CancelLast(depot));
        Assert.Equal(ResearchFailure.MissingBuilding, s.Research.CancelLast(999));
    }

    [Fact]
    public void Unknown_tech_is_rejected()
    {
        (Simulation s, int depot) = New();
        Assert.Equal(ResearchFailure.InvalidTech, s.Research.Enqueue(depot, "Teleport"));
    }
}
