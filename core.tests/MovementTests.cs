using System.Collections.Generic;
using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class MovementTests
{
    private static Simulation New(int width = 16, int height = 16, PathSettings? paths = null) =>
        Simulation.Create(new MatchSetup(width, height, 1UL, paths), TestContent.Real);

    private static Position At(int x, int y) => new(Fix.FromInt(x), Fix.FromInt(y));
    private static Fix F(int value) => Fix.FromInt(value);

    private static int Spawn(Simulation s, int x, int y, string type = "Test", int faction = 1, WeaponRef? weapon = null) =>
        s.World.Spawn(At(x, y), new UnitTag(faction, type), new Health(10, 10), weapon);

    private static void Run(Simulation s, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            s.Step();
        }
    }

    private static (int X, int Y) Tile(Simulation s, int id) =>
        (s.World.Positions.Get(id).X.FloorToInt(), s.World.Positions.Get(id).Y.FloorToInt());

    [Fact]
    public void System_order_is_pinned()
    {
        Assert.Equal(
            new[] { "AliveSystem", "OccupancySystem", "PathfindingSystem", "MovementSystem", "CombatSystem" },
            New().SystemOrder.ToArray());
    }

    // Ported from CornerCutTest. Ticks are scaled from 50 to 20 per second.
    [Fact]
    public void Avoids_diagonal_corner_cut()
    {
        Simulation s = New(6, 6);
        s.World.Map.SetBlocked(2, 1, true);
        s.World.Map.SetBlocked(1, 2, true);
        int id = Spawn(s, 1, 1);
        s.World.Orders.Get(id).Add(new Order.Move(F(3), F(3)));

        for (int tick = 0; tick < 48; tick++)
        {
            s.Step();
            if (s.World.PathFollows.TryGet(id, out PathFollow path) && path.Index < path.Length)
            {
                int node = path.Nodes[path.Index];
                bool aimsAcrossCorner = node % 6 == 2 && node / 6 == 2 && Tile(s, id) == (1, 1);
                Assert.False(aimsAcrossCorner, "Path attempted to cut blocked diagonal corner");
            }
        }
    }

    // Ported from RepathTest.
    [Fact]
    public void Replans_when_blocked_mid_path()
    {
        Simulation s = New(10, 10);
        for (int y = 0; y < 10; y++)
        {
            if (y != 2 && y != 7)
            {
                s.World.Map.SetBlocked(5, y, true);
            }
        }

        int id = Spawn(s, 2, 2);
        s.World.Orders.Get(id).Add(new Order.Move(F(8), F(2)));
        bool safePathObserved = false;
        for (int tick = 0; tick < 160; tick++)
        {
            if (tick == 8)
            {
                s.World.Occupancy.AddStatic(5, 2);
            }

            s.Step();
            if (tick > 16 && s.World.PathFollows.TryGet(id, out PathFollow path))
            {
                safePathObserved = !path.Nodes[path.Index..path.Length].Contains(2 * 10 + 5);
            }
        }

        Assert.Equal((8, 2), Tile(s, id));
        Assert.True(safePathObserved, "Replan did not avoid blocked gap");
        Assert.True(s.World.Orders.Get(id).Count == 0);
    }

    // Regression for a Kotlin bug: Pathfinder.open() wiped the parent link, so paths skipped the detour and units walked through walls.
    [Fact]
    public void Unit_walks_around_a_wall_rather_than_through_it()
    {
        Simulation s = New(8, 8);
        for (int y = 0; y <= 5; y++)
        {
            s.World.Map.SetBlocked(3, y, true);
        }

        int id = Spawn(s, 1, 1);
        s.World.Orders.Get(id).Add(new Order.Move(F(5), F(1)));
        for (int tick = 0; tick < 200; tick++)
        {
            s.Step();
            (int x, int y) = Tile(s, id);
            Assert.False(s.World.Map.IsBlocked(x, y), $"inside the wall at tick {tick}");
        }

        Assert.Equal((5, 1), Tile(s, id));
    }

    [Fact]
    public void Units_never_stand_on_blocked_tiles()
    {
        var scenario = new MovementScenario(seed: 11UL);
        for (int tick = 0; tick < 800; tick++)
        {
            scenario.StepOnce();
            foreach (int id in scenario.Ids)
            {
                (int x, int y) = Tile(scenario.Simulation, id);
                Assert.False(scenario.Simulation.World.Map.IsBlocked(x, y), $"unit {id} on ({x},{y}) at tick {tick}");
            }
        }
    }

    [Fact]
    public void Far_waypoint_occupied_by_a_unit_does_not_interrupt_walking()
    {
        Simulation s = New();
        int walker = Spawn(s, 1, 3);
        s.World.Orders.Get(walker).Add(new Order.Move(F(9), F(3)));
        s.Step();
        s.Step();
        s.World.Occupancy.AddStatic(9, 9); // Unrelated.
        Spawn(s, 9, 3); // Stands on the waypoint after the path was assigned.
        int replans = 0;
        for (int tick = 0; tick < 10; tick++)
        {
            s.Step();
            replans += s.Movement.LastTickReplansBlocked;
        }

        Assert.Equal(0, replans);
        Assert.True(Tile(s, walker).X >= 2);
    }

    // Ported from OccupancyStressTest.
    [Fact]
    public void Many_units_move_without_stalling()
    {
        var scenario = new MovementScenario(seed: 42UL);
        var start = scenario.Ids.Select(id => Tile(scenario.Simulation, id)).ToArray();
        for (int tick = 0; tick < 240; tick++)
        {
            scenario.StepOnce();
        }

        int moved = scenario.Ids.Where((id, i) => Tile(scenario.Simulation, id) != start[i]).Count();
        Assert.True(moved >= MovementScenario.UnitCount / 2, $"only {moved} units moved");
    }

    // Replaces PathfindingSystemMetricsTest, which only asserted the counters were not negative.
    [Fact]
    public void Node_budget_limits_a_tick_and_carries_requests_over()
    {
        Simulation s = New(paths: new PathSettings(8, 16, 16));
        int[] ids = Enumerable.Range(0, 6).Select(i => Spawn(s, 1 + i, 1)).ToArray();
        foreach (int id in ids)
        {
            s.World.Orders.Get(id).Add(new Order.Move(F(14), F(14)));
        }

        s.Step();
        Assert.Equal(6, s.World.PathRequests.Count);

        s.Step();
        Assert.Equal(6, s.Pathing.LastTickRequests);
        Assert.True(s.Pathing.LastTickNodesUsed <= 8, $"used {s.Pathing.LastTickNodesUsed}");
        Assert.True(s.Pathing.LastTickCarryOver > 0);
        Assert.True(s.Pathing.LastTickBudgetExhausted > 0);
        Assert.Equal(0, s.Pathing.LastTickSolved);
    }

    [Fact]
    public void Unit_stops_on_the_centre_of_the_goal_tile_and_finishes_the_order()
    {
        Simulation s = New();
        int id = Spawn(s, 1, 1);
        s.World.Orders.Get(id).Add(new Order.Move(F(5), F(5)));
        Run(s, 100);
        Position position = s.World.Positions.Get(id);
        Assert.Empty(s.World.Orders.Get(id));
        Assert.False(s.World.PathFollows.Has(id));
        Assert.True(Fix.IsWithin(position.X - (F(5) + Fix.Half), position.Y - (F(5) + Fix.Half), Fix.FromRatio(1, 10)));
    }

    [Fact]
    public void Speed_comes_from_the_unit_definition()
    {
        Simulation s = New();
        int artisan = Spawn(s, 1, 1, "artisan");
        int brute = Spawn(s, 1, 5, "brute", weapon: null);
        s.World.Orders.Get(artisan).Add(new Order.Move(F(14), F(1)));
        s.World.Orders.Get(brute).Add(new Order.Move(F(14), F(5)));
        Run(s, 12);
        Assert.True(s.World.Positions.Get(brute).X > s.World.Positions.Get(artisan).X);
    }

    [Fact]
    public void Patrol_keeps_walking_between_its_two_points()
    {
        Simulation s = New();
        int id = Spawn(s, 2, 2);
        s.World.Orders.Get(id).Add(new Order.Patrol(F(2), F(2), F(8), F(2)));
        var visited = new HashSet<int>();
        for (int tick = 0; tick < 300; tick++)
        {
            s.Step();
            visited.Add(Tile(s, id).X);
        }

        Assert.Contains(2, visited);
        Assert.Contains(8, visited);
        Assert.Single(s.World.Orders.Get(id));
    }

    [Fact]
    public void Attack_move_halts_for_an_enemy_in_range()
    {
        Simulation s = New();
        int unit = Spawn(s, 1, 1, "gunner", 1, new WeaponRef("matchlock"));
        Spawn(s, 9, 1, "gunner", 2);
        s.World.Orders.Get(unit).Add(new Order.AttackMove(F(14), F(1)));
        Run(s, 200);
        int x = Tile(s, unit).X;
        Assert.InRange(x, 2, 5); // 4.5 tiles plus both radii short of the enemy at x = 9, within the waypoint step.
        Assert.Empty(s.World.Orders.Get(unit)); // Beyond the 3-tile leash, so the order is dropped.
    }

    [Fact]
    public void Attack_move_keeps_its_order_inside_the_leash()
    {
        Simulation s = New();
        int unit = Spawn(s, 6, 1, "gunner", 1, new WeaponRef("matchlock"));
        int enemy = Spawn(s, 8, 1, "gunner", 2);
        s.World.Healths.Set(enemy, new Health(100_000, 100_000));
        s.World.Orders.Get(unit).Add(new Order.AttackMove(F(7), F(1)));
        Run(s, 40);
        Assert.Single(s.World.Orders.Get(unit));
    }

    [Fact]
    public void Attack_order_chases_until_in_range_then_stops_walking()
    {
        Simulation s = New();
        int unit = Spawn(s, 1, 1, "gunner", 1, new WeaponRef("matchlock"));
        int enemy = Spawn(s, 12, 1, "gunner", 2);
        s.World.Healths.Set(enemy, new Health(100_000, 100_000)); // Survives, so only the chase is measured.
        s.World.Orders.Get(unit).Add(new Order.Attack(enemy));
        Run(s, 200);
        var reach = new Reach(s.World, s.Content);
        Assert.True(reach.IsInReach(unit, s.World.Positions.Get(unit), enemy, Fix.FromRatio(9, 2)));
        // Stopped close to the edge of reach rather than walking up to the target.
        Assert.True(reach.Gap(unit, s.World.Positions.Get(unit), enemy) > Fix.FromInt(3));
        Assert.Single(s.World.Orders.Get(unit));
        Assert.False(s.World.PathFollows.Has(unit));
    }

    [Theory]
    [InlineData("dead")]
    [InlineData("removed")]
    [InlineData("friendly")]
    public void Attack_order_is_dropped_for_an_invalid_target(string kind)
    {
        Simulation s = New();
        int unit = Spawn(s, 1, 1, "gunner", 1, new WeaponRef("matchlock"));
        int target = Spawn(s, 12, 1, "gunner", kind == "friendly" ? 1 : 2);
        if (kind == "dead")
        {
            s.World.Healths.Get(target).Hp = 0;
        }
        else if (kind == "removed")
        {
            s.World.Remove(target);
        }

        s.World.Orders.Get(unit).Add(new Order.Attack(target));
        s.Step();
        Assert.Empty(s.World.Orders.Get(unit));
    }

    [Fact]
    public void Construct_order_walks_into_reach_and_stays_assigned()
    {
        Simulation s = New();
        int builder = Spawn(s, 1, 1, "artisan");
        int site = Spawn(s, 10, 10, "hall");
        s.World.Footprints.Set(site, new BuildingFootprint(10, 10, 2, 2));
        s.World.ConstructionSites.Set(site, new ConstructionSite(100, 100, 400));
        s.World.BuilderTasks.Set(builder, site);
        s.World.Orders.Get(builder).Add(new Order.Construct(site));
        Run(s, 100);

        Assert.InRange(Tile(s, builder).X, 8, 13);
        Assert.Single(s.World.Orders.Get(builder));
        Assert.False(s.World.PathFollows.Has(builder));

        s.World.ConstructionSites.Remove(site);
        s.Step();
        Assert.Empty(s.World.Orders.Get(builder));
        Assert.False(s.World.BuilderTasks.Has(builder));
    }

    [Fact]
    public void Stuck_unit_requests_a_new_path()
    {
        Simulation s = New();
        int id = Spawn(s, 1, 1);
        s.World.Orders.Get(id).Add(new Order.Move(F(9), F(1)));
        s.Step();
        s.Step();
        Assert.True(s.World.PathFollows.Has(id));
        Position position = s.World.Positions.Get(id);
        StuckTracker stuck = s.World.Stucks.Get(id);
        stuck.LastX = position.X;
        stuck.LastY = position.Y;
        stuck.Ticks = 100;
        s.World.RepathCooldowns.Set(id, 0);
        s.Step();
        Assert.Equal(1, s.Movement.LastTickReplansStuck);
        Assert.False(s.World.PathFollows.Has(id));
        Assert.Equal(1, s.World.PathRequests.Count);
    }

    [Fact]
    public void Dead_units_neither_move_nor_occupy_tiles()
    {
        Simulation s = New();
        int id = Spawn(s, 3, 3);
        s.World.Orders.Get(id).Add(new Order.Move(F(9), F(9)));
        s.World.Healths.Get(id).Hp = 0;
        Run(s, 20);
        Assert.Equal((3, 3), Tile(s, id));
        Assert.False(s.World.Occupancy.IsBlocked(3, 3));
    }

    [Fact]
    public void Hold_order_stays_put()
    {
        Simulation s = New();
        int id = Spawn(s, 3, 3);
        s.World.Orders.Get(id).Add(new Order.Hold());
        Run(s, 20);
        Assert.Equal((3, 3), Tile(s, id));
        Assert.Equal(0, s.World.PathRequests.Count);
    }

    [Fact]
    public void Save_and_load_mid_movement_continues_identically_with_carried_requests()
    {
        var scenario = new MovementScenario(seed: 7UL);
        for (int tick = 0; tick < 60; tick++)
        {
            scenario.StepOnce();
        }

        Simulation restored = Simulation.LoadState(scenario.Simulation.SaveState(), TestContent.Real);
        for (int tick = 0; tick < 200; tick++)
        {
            scenario.Simulation.Step();
            restored.Step();
            Assert.Equal(scenario.Simulation.ComputeHash(), restored.ComputeHash());
        }
    }

    [Fact]
    public void Same_scenario_gives_the_same_hash()
    {
        var a = new MovementScenario(seed: 3UL);
        var b = new MovementScenario(seed: 3UL);
        for (int tick = 0; tick < 300; tick++)
        {
            a.StepOnce();
            b.StepOnce();
        }

        Assert.Equal(a.Simulation.ComputeHash(), b.Simulation.ComputeHash());
    }
}
