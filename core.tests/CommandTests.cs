using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>The command entry point: ownership, replacing versus queueing, and each command's validation.</summary>
public class CommandTests
{
    private static Simulation New(GameContent? content = null) =>
        Simulation.Create(new MatchSetup(24, 24, 1UL), content ?? TestContent.Real);

    private static Fix F(int value) => Fix.FromInt(value);

    private static int Unit(Simulation s, int faction, int x, int y, string type = "gunner") =>
        s.World.Spawn(new Position(F(x) + Fix.Half, F(y) + Fix.Half), new UnitTag(faction, type), new Health(45, 45), new WeaponRef("matchlock"));

    private static CommandResult Issue(Simulation s, PlayerCommand command) => s.Step(new[] { command })[0];

    /// <summary>Keeps the match open: victory ends it once a faction has nothing left.</summary>
    private static void AddOpponent(Simulation s) => Unit(s, 2, 22, 22);

    [Fact]
    public void Player_can_only_order_its_own_living_units()
    {
        Simulation s = New();
        int mine = Unit(s, 1, 2, 2);
        int theirs = Unit(s, 2, 20, 20);
        int dead = Unit(s, 1, 3, 2);
        s.World.Healths.Get(dead).Hp = 0;

        CommandResult result = Issue(s, new PlayerCommand.Move(1, new[] { mine, theirs, dead, mine, 999 }, F(10), F(10)));
        Assert.True(result.Accepted);
        Assert.Equal(1, result.AppliedUnits);
        Assert.Empty(s.World.Orders.Get(theirs));
        Assert.Equal(CommandRejection.NoOwnUnits, Issue(s, new PlayerCommand.Move(1, new[] { theirs }, F(10), F(10))).Rejection);
    }

    [Fact]
    public void A_new_order_replaces_and_a_queued_order_appends()
    {
        Simulation s = New();
        AddOpponent(s);
        int unit = Unit(s, 1, 2, 2);
        s.Step(new PlayerCommand[]
        {
            new PlayerCommand.Move(1, new[] { unit }, F(10), F(2)),
            new PlayerCommand.Move(1, new[] { unit }, F(10), F(10), Queued: true),
        });
        Assert.Equal(2, s.World.Orders.Get(unit).Count);

        Issue(s, new PlayerCommand.Hold(1, new[] { unit }));
        Assert.Equal(new Order.Hold(), Assert.Single(s.World.Orders.Get(unit)));
        Assert.False(s.World.PathFollows.Has(unit));
    }

    [Fact]
    public void Commands_apply_in_the_order_given_before_the_systems_run()
    {
        Simulation s = New();
        AddOpponent(s);
        s.Resources.Set(1, 1000);
        var results = s.Step(new PlayerCommand[]
        {
            new PlayerCommand.Build(1, "hall", 4, 4),
            new PlayerCommand.Train(1, s.World.NextEntityId, "artisan"),
        });
        Assert.All(results, r => Assert.True(r.Accepted, r.Rejection + " " + r.Detail));
        Assert.Equal(new[] { 0, 1 }, results.Select(r => r.Index));
        Assert.True(s.World.ProductionQueues.Has(results[0].EntityId));
        Assert.Equal(1, s.World.ProductionQueues.Count);
    }

    [Fact]
    public void Points_outside_the_map_are_rejected()
    {
        Simulation s = New();
        AddOpponent(s);
        int unit = Unit(s, 1, 2, 2);
        Assert.Equal(CommandRejection.OutOfBounds, Issue(s, new PlayerCommand.Move(1, new[] { unit }, F(30), F(2))).Rejection);
        Assert.Equal(CommandRejection.OutOfBounds, Issue(s, new PlayerCommand.AttackMove(1, new[] { unit }, F(-1), F(2))).Rejection);
    }

    [Fact]
    public void Patrol_runs_from_where_each_unit_stands()
    {
        Simulation s = New();
        AddOpponent(s);
        int unit = Unit(s, 1, 2, 2);
        Issue(s, new PlayerCommand.Patrol(1, new[] { unit }, F(10), F(2)));
        var patrol = Assert.IsType<Order.Patrol>(s.World.Orders.Get(unit)[0]);
        Assert.Equal((F(2) + Fix.Half, F(10)), (patrol.AX, patrol.BX));
    }

    [Fact]
    public void Attack_needs_a_living_enemy_target()
    {
        Simulation s = New();
        int unit = Unit(s, 1, 2, 2);
        int friend = Unit(s, 1, 3, 2);
        int enemy = Unit(s, 2, 20, 2);
        Assert.Equal(CommandRejection.InvalidTarget, Issue(s, new PlayerCommand.Attack(1, new[] { unit }, friend)).Rejection);
        Assert.True(Issue(s, new PlayerCommand.Attack(1, new[] { unit }, enemy)).Accepted);
        Assert.Equal(new Order.Attack(enemy), s.World.Orders.Get(unit)[0]);
    }

    // Ported from ResourceHarvestSystemTest "harvest command assigns workers to a resource node".
    [Fact]
    public void Harvest_assigns_workers_and_sends_them_to_the_node()
    {
        Simulation s = New();
        AddOpponent(s);
        int worker = Unit(s, 1, 2, 2, "artisan");
        int gunner = Unit(s, 1, 3, 2);
        int node = s.World.Spawn(new Position(F(8), F(8)), new UnitTag(0, "ore"), new Health(1, 1));
        s.World.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 12, 1));

        CommandResult result = Issue(s, new PlayerCommand.Harvest(1, new[] { worker, gunner }, node));
        Assert.Equal(1, result.AppliedUnits);
        Assert.Equal(node, s.World.Harvesters.Get(worker).TargetNodeId);
        Assert.False(s.World.Harvesters.Has(gunner));
        Assert.Equal(CommandRejection.InvalidTarget, Issue(s, new PlayerCommand.Harvest(1, new[] { worker }, gunner)).Rejection);
    }

    [Fact]
    public void A_fresh_order_pulls_a_worker_off_harvesting()
    {
        Simulation s = New();
        AddOpponent(s);
        int worker = Unit(s, 1, 2, 2, "artisan");
        int node = s.World.Spawn(new Position(F(8), F(8)), new UnitTag(0, "ore"), new Health(1, 1));
        s.World.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 100, 5));
        Issue(s, new PlayerCommand.Harvest(1, new[] { worker }, node));
        Issue(s, new PlayerCommand.Move(1, new[] { worker }, F(2), F(20)));
        for (int i = 0; i < 20; i++)
        {
            s.Step();
        }

        Assert.False(s.World.Harvesters.Has(worker));
        Assert.True(s.World.Positions.Get(worker).Y > F(4));
    }

    [Fact]
    public void Build_and_construct_use_data_and_check_the_site_owner()
    {
        GameContent content = EconomyContent.Standard;
        Simulation s = New(content);
        AddOpponent(s);
        s.Resources.Set(1, 500);
        s.Resources.Set(2, 500);
        int worker = s.World.Spawn(new Position(F(2) + Fix.Half, F(2) + Fix.Half), new UnitTag(1, "Worker"), new Health(40, 40));
        CommandResult built = Issue(s, new PlayerCommand.Build(1, "SlowDepot", 6, 6));
        Assert.True(built.Accepted);
        Assert.Equal(400, s.World.Stockpile(1).Ore);

        int enemySite = Issue(s, new PlayerCommand.Build(2, "SlowDepot", 14, 14)).EntityId;
        Assert.Equal(CommandRejection.InvalidTarget, Issue(s, new PlayerCommand.Construct(1, new[] { worker }, enemySite)).Rejection);
        Assert.True(Issue(s, new PlayerCommand.Construct(1, new[] { worker }, built.EntityId)).Accepted);
        Assert.Equal(built.EntityId, s.World.BuilderTasks.Get(worker));

        CommandResult failed = Issue(s, new PlayerCommand.Build(1, "Factory", 2, 12));
        Assert.Equal((CommandRejection.Build, "MissingTech"), (failed.Rejection!.Value, failed.Detail));
    }

    // Ported from BuildingPlacementTest "cancel build refunds and removes unfinished structure", through the command.
    [Fact]
    public void Cancel_build_refunds_only_for_the_owner()
    {
        Simulation s = New(EconomyContent.Standard);
        AddOpponent(s);
        s.Resources.Set(1, 150);
        int site = Issue(s, new PlayerCommand.Build(1, "SlowDepot", 8, 8)).EntityId;
        Assert.Equal(CommandRejection.NotYourBuilding, Issue(s, new PlayerCommand.CancelBuild(2, site)).Rejection);
        Assert.True(Issue(s, new PlayerCommand.CancelBuild(1, site)).Accepted);
        Assert.Equal(150, s.World.Stockpile(1).Ore);
        Assert.False(s.World.Occupancy.IsBlocked(8, 8));
    }

    // Replaces ProductionSystemTest "train command enqueues production via label": labels belong to scripts, not players.
    [Fact]
    public void Train_and_cancel_work_only_on_own_buildings()
    {
        Simulation s = New(EconomyContent.Standard);
        AddOpponent(s);
        s.Resources.Set(1, 300);
        int depot = Issue(s, new PlayerCommand.Build(1, "Depot", 6, 6)).EntityId;
        Assert.Equal(CommandRejection.NotYourBuilding, Issue(s, new PlayerCommand.Train(2, depot, "Marine")).Rejection);
        // Marines train in two ticks, so queueing and cancelling happen within one tick.
        var results = s.Step(new PlayerCommand[]
        {
            new PlayerCommand.Train(1, depot, "Marine"),
            new PlayerCommand.Train(1, depot, "Nobody"),
            new PlayerCommand.CancelTrain(1, depot),
            new PlayerCommand.CancelTrain(1, depot),
        });
        Assert.True(results[0].Accepted);
        Assert.Equal((CommandRejection.Train, "InvalidUnit"), (results[1].Rejection!.Value, results[1].Detail));
        Assert.True(results[2].Accepted);
        Assert.Equal(CommandRejection.NothingToCancel, results[3].Rejection);
        Assert.Equal(200, s.World.Stockpile(1).Ore);
    }

    [Fact]
    public void Research_and_cancel_work_through_commands()
    {
        Simulation s = New(EconomyContent.Standard);
        AddOpponent(s);
        s.Resources.Set(1, 500);
        int depot = Issue(s, new PlayerCommand.Build(1, "Depot", 6, 6)).EntityId;
        Assert.True(Issue(s, new PlayerCommand.Research(1, depot, "AdvancedTraining")).Accepted);
        Assert.True(Issue(s, new PlayerCommand.CancelResearch(1, depot)).Accepted);
        Assert.Equal(CommandRejection.NothingToCancel, Issue(s, new PlayerCommand.CancelResearch(1, depot)).Rejection);
        Assert.Equal(400, s.World.Stockpile(1).Ore);
    }

    // Ported from ProductionSystemTest "producer without rally support ignores rally override".
    [Fact]
    public void Rally_is_rejected_on_buildings_without_rally_support()
    {
        Simulation s = New(EconomyContent.Standard);
        AddOpponent(s);
        s.Resources.Set(1, 500);
        int tower = Issue(s, new PlayerCommand.Build(1, "Tower", 6, 6)).EntityId;
        int depot = Issue(s, new PlayerCommand.Build(1, "Depot", 12, 6)).EntityId;
        Assert.Equal(CommandRejection.UnsupportedRally, Issue(s, new PlayerCommand.Rally(1, tower, F(15), F(16))).Rejection);
        Assert.False(s.World.RallyPoints.Has(tower));
        Assert.True(Issue(s, new PlayerCommand.Rally(1, depot, F(15), F(16))).Accepted);
        Assert.Equal(new RallyPoint(F(15), F(16)), s.World.RallyPoints.Get(depot));
    }

    // Ported from AppTest "issue emits command ack records" (rally on a building that does not exist).
    [Fact]
    public void Rally_is_rejected_for_a_building_that_does_not_exist()
    {
        Simulation s = New(EconomyContent.Standard);
        AddOpponent(s);

        Assert.Equal(CommandRejection.NotYourBuilding, Issue(s, new PlayerCommand.Rally(1, 999, F(15), F(16))).Rejection);
        Assert.False(s.World.RallyPoints.Has(999));
    }

    // Ported from SurrenderCommandTest.
    [Fact]
    public void Surrender_ends_the_match_with_the_opponent_as_winner()
    {
        Simulation s = New();
        Unit(s, 1, 2, 2);
        Unit(s, 2, 10, 10);
        Assert.True(Issue(s, new PlayerCommand.Surrender(1)).Accepted);
        Assert.True(s.World.MatchEnded);
        Assert.Equal(2, s.World.WinnerFaction);
        Assert.Equal(MatchEndReason.Surrender, s.World.EndReason);
    }

    // Ported from SurrenderCommandTest.
    [Fact]
    public void Surrender_by_a_faction_not_in_the_match_is_rejected()
    {
        Simulation s = New();
        Unit(s, 1, 2, 2);
        Unit(s, 2, 10, 10);
        Assert.Equal(CommandRejection.NotInMatch, Issue(s, new PlayerCommand.Surrender(9)).Rejection);
        Assert.False(s.World.MatchEnded);
        Assert.Null(s.World.WinnerFaction);
    }

    [Fact]
    public void Commands_after_the_match_ended_are_rejected()
    {
        Simulation s = New();
        int unit = Unit(s, 1, 2, 2);
        Unit(s, 2, 10, 10);
        Issue(s, new PlayerCommand.Surrender(2));
        Assert.Equal(CommandRejection.MatchEnded, Issue(s, new PlayerCommand.Move(1, new[] { unit }, F(5), F(5))).Rejection);
    }
}
