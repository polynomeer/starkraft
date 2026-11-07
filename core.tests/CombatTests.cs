using System.Collections.Generic;
using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Ported from CombatBehaviorTest. Its test data is kept: an unknown "Zergling" type, and a "Marine" at 3 tiles/s
/// (0.06 per tick at 50 ticks/s) with 8 damage and a 4-tile range. Tick counts are scaled from 50 to 20 per second.
/// </summary>
public class CombatTests
{
    private static readonly GameContent Data = ContentParser.Parse(
        """{"list":[{"id":"Marine","archetype":"infantry","hp":45,"radius":0.4,"speedTilesPerSecond":3.0}]}""",
        """{"list":[{"id":"Gauss","damage":8,"range":4.0,"cooldownMs":50}]}""",
        """{"list":[]}""",
        """{"list":[]}""");

    private static Simulation New(int size = 24) => Simulation.Create(new MatchSetup(size, size, 1UL), Data);

    private static Fix F(int value) => Fix.FromInt(value);

    private static int Marine(Simulation s, Fix x, Fix y, int faction = 1) =>
        s.World.Spawn(new Position(x, y), new UnitTag(faction, "Marine"), new Health(45, 45), new WeaponRef("Gauss"));

    private static int Target(Simulation s, Fix x, Fix y, int hp, int faction = 2) =>
        s.World.Spawn(new Position(x, y), new UnitTag(faction, "Zergling"), new Health(hp, hp));

    private static void Run(Simulation s, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            s.Step();
        }
    }

    [Fact]
    public void Combat_runs_after_movement_and_before_victory()
    {
        var order = New().SystemOrder.ToList();
        Assert.True(order.IndexOf("MovementSystem") < order.IndexOf("CombatSystem"));
        Assert.Equal(order.IndexOf("CombatSystem") + 1, order.IndexOf("VictorySystem"));
    }

    [Fact]
    public void Unit_type_missing_from_data_moves_at_the_default_speed()
    {
        Simulation s = New();
        int unit = Target(s, F(2), F(2), 10, faction: 1);
        s.World.Orders.Get(unit).Add(new Order.Move(F(12), F(2)));
        Run(s, 16);
        Assert.True(s.World.Positions.Get(unit).X > F(2));
    }

    [Fact]
    public void Attack_order_chases_targets_that_start_out_of_range()
    {
        Simulation s = New();
        int attacker = Marine(s, F(2), F(2));
        // Kotlin used 35 hp. Its chase stalled on cut-short paths, so the target outlived the test; here it would not.
        int target = Target(s, F(12), F(2), 1000);
        s.World.Orders.Get(attacker).Add(new Order.Attack(target));
        Run(s, 56);
        Assert.True(s.World.Positions.Get(attacker).X > Fix.FromRatio(5, 2));
        Assert.Equal(new Order.Attack(target), s.World.Orders.Get(attacker).FirstOrDefault());
    }

    [Fact]
    public void Attack_order_clears_after_target_death()
    {
        Simulation s = New(16);
        int attacker = Marine(s, F(2), F(2));
        int target = Target(s, F(4), F(2), 6);
        s.World.Orders.Get(attacker).Add(new Order.Attack(target));
        Run(s, 3);
        Assert.False(s.World.Healths.Has(target));
        Assert.Empty(s.World.Orders.Get(attacker));
        Assert.False(s.World.PathFollows.Has(attacker));
    }

    [Fact]
    public void Attack_move_pauses_to_fight_and_then_continues()
    {
        Simulation s = New();
        int attacker = Marine(s, F(2), F(2));
        int blocker = Target(s, F(6), F(2), 8);
        s.World.Orders.Get(attacker).Add(new Order.AttackMove(F(12), F(2)));
        Run(s, 64);
        Assert.False(s.World.Healths.Has(blocker));
        Assert.True(s.World.Positions.Get(attacker).X > F(9));
        Assert.Equal(new Order.AttackMove(F(12), F(2)), s.World.Orders.Get(attacker).FirstOrDefault());
    }

    [Fact]
    public void Attack_move_clears_once_combat_pulls_unit_beyond_leash()
    {
        Simulation s = New(32);
        int attacker = Marine(s, F(2), F(2));
        Target(s, F(8), F(2), 35);
        s.World.Orders.Get(attacker).Add(new Order.AttackMove(F(3), F(2)));
        Run(s, 64);
        Assert.Empty(s.World.Orders.Get(attacker));
    }

    [Fact]
    public void Auto_targeting_avoids_wasting_lethal_follow_up_shots()
    {
        Simulation s = New();
        Marine(s, F(2), F(2));
        Marine(s, F(2), Fix.FromRatio(5, 2));
        int near = Target(s, F(5), F(2), 12);
        int far = Target(s, F(5), F(4), 35);
        s.Step();
        Assert.Equal(4, s.World.Healths.Get(near).Hp);
        Assert.Equal(27, s.World.Healths.Get(far).Hp);
        Assert.Equal(2, s.Combat.LastTickEvents.Count);
    }

    [Fact]
    public void Hold_order_fires_in_range_but_does_not_chase()
    {
        Simulation s = New();
        int attacker = Marine(s, F(2), F(2));
        int near = Target(s, F(5), F(2), 6);
        Target(s, F(10), F(2), 35);
        s.World.Orders.Get(attacker).Add(new Order.Hold());
        Run(s, 16);
        Assert.False(s.World.Healths.Has(near));
        Assert.True(s.World.Positions.Get(attacker).X < Fix.FromRatio(11, 5));
        Assert.Equal(new Order.Hold(), s.World.Orders.Get(attacker).FirstOrDefault());
        Assert.False(s.World.PathFollows.Has(attacker));
    }

    [Fact]
    public void Auto_targeting_stays_sticky_while_target_remains_in_range()
    {
        Simulation s = New();
        int attacker = Marine(s, F(2), F(2));
        int near = Target(s, F(5), F(2), 35);
        Target(s, F(5), Fix.FromRatio(13, 5), 35);
        s.Step();
        Assert.Equal(near, s.World.AutoAttackTargets.Get(attacker));
        s.Step();
        Assert.Equal(near, s.World.AutoAttackTargets.Get(attacker));
    }

    [Fact]
    public void Patrol_order_reverses_after_reaching_its_endpoint()
    {
        Simulation s = New();
        int scout = Marine(s, F(2), F(2));
        s.World.Orders.Get(scout).Add(new Order.Patrol(F(2), F(2), F(6), F(2)));
        int ticks = 0;
        while (s.World.Orders.Get(scout)[0] is Order.Patrol { TowardB: true } && ticks++ < 200)
        {
            s.Step();
        }

        Assert.True(ticks < 200, "never reached the far end");
        Fix returnStart = s.World.Positions.Get(scout).X;
        Run(s, 12);
        Assert.True(s.World.Positions.Get(scout).X < returnStart);
    }

    [Fact]
    public void Weapon_fires_exactly_once_per_cooldown()
    {
        Simulation s = Simulation.Create(new MatchSetup(16, 16, 1UL), TestContent.Real);
        int gunner = s.World.Spawn(new Position(F(2), F(2)), new UnitTag(1, "gunner"), new Health(45, 45), new WeaponRef("matchlock"));
        s.World.Spawn(new Position(F(4), F(2)), new UnitTag(2, "hall"), new Health(1000, 1000));
        var shotTicks = new List<int>();
        for (int tick = 0; tick < 20; tick++)
        {
            s.Step();
            if (s.Combat.LastTickEvents.Any(e => e.Attacker == gunner))
            {
                shotTicks.Add(tick);
            }
        }

        // matchlock: 240 ms rounds up to 5 ticks of 50 ms.
        Assert.Equal(new[] { 0, 5, 10, 15 }, shotTicks);
    }

    [Fact]
    public void Armor_reduces_damage_but_never_below_zero()
    {
        Simulation s = New();
        Marine(s, F(2), F(2));
        int armored = s.World.Spawn(new Position(F(4), F(2)), new UnitTag(2, "Zergling"), new Health(50, 50, armor: 3));
        int fortress = s.World.Spawn(new Position(F(4), F(3)), new UnitTag(2, "Zergling"), new Health(50, 50, armor: 20));
        s.Step();
        Assert.Equal(45, s.World.Healths.Get(armored).Hp);
        Assert.Equal(50, s.World.Healths.Get(fortress).Hp);
    }

    [Fact]
    public void Melee_reaches_an_enemy_on_the_next_tile_including_diagonally()
    {
        Simulation s = Simulation.Create(new MatchSetup(16, 16, 1UL), TestContent.Real);
        int brute = s.World.Spawn(new Position(F(5) + Fix.Half, F(5) + Fix.Half), new UnitTag(1, "brute"), new Health(35, 35), new WeaponRef("club"));
        int side = s.World.Spawn(new Position(F(6) + Fix.Half, F(5) + Fix.Half), new UnitTag(2, "gunner"), new Health(45, 45));
        int corner = s.World.Spawn(new Position(F(6) + Fix.Half, F(6) + Fix.Half), new UnitTag(2, "gunner"), new Health(45, 45));
        int twoAway = s.World.Spawn(new Position(F(7) + Fix.Half, F(3) + Fix.Half), new UnitTag(2, "gunner"), new Health(45, 45));
        var reach = new Reach(s.World, s.Content);
        Fix club = s.Content.WeaponOrNull("club")!.Range;
        Position at = s.World.Positions.Get(brute);
        Assert.True(reach.IsInReach(brute, at, side, club));
        Assert.True(reach.IsInReach(brute, at, corner, club));
        Assert.False(reach.IsInReach(brute, at, twoAway, club));
        s.Step();
        Assert.Single(s.Combat.LastTickEvents);
    }

    [Fact]
    public void Reach_to_a_building_is_measured_to_its_footprint_edge()
    {
        Simulation s = Simulation.Create(new MatchSetup(16, 16, 1UL), TestContent.Real);
        int brute = s.World.Spawn(new Position(F(3) + Fix.Half, F(5) + Fix.Half), new UnitTag(1, "brute"), new Health(35, 35), new WeaponRef("club"));
        int hall = s.World.Spawn(new Position(F(5), F(6)), new UnitTag(2, "hall"), new Health(400, 400));
        s.World.Footprints.Set(hall, new BuildingFootprint(4, 5, 2, 2));
        var reach = new Reach(s.World, s.Content);
        // Centre 3.5 to the footprint's west edge at x = 4 is 0.5, minus the brute's 0.45 radius.
        Assert.Equal(Fix.Half - s.Content.RadiusOf("brute"), reach.Gap(brute, s.World.Positions.Get(brute), hall));
        s.Step();
        Assert.Equal(395, s.World.Healths.Get(hall).Hp);
    }

    [Fact]
    public void Resource_nodes_are_never_attacked()
    {
        Simulation s = New();
        int attacker = Marine(s, F(2), F(2));
        int node = s.World.Spawn(new Position(F(3), F(2)), new UnitTag(2, "ore"), new Health(1000, 1000));
        s.World.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 250));
        Run(s, 10);
        Assert.Equal(1000, s.World.Healths.Get(node).Hp);

        s.World.Orders.Get(attacker).Add(new Order.Attack(node));
        s.Step();
        Assert.Empty(s.World.Orders.Get(attacker));
    }

    // Kotlin dropped the second shot here, so only the lower id fired.
    [Fact]
    public void Mutual_kills_in_one_tick_both_land()
    {
        Simulation s = New();
        int a = Marine(s, F(2), F(2));
        int b = Marine(s, F(4), F(2), faction: 2);
        s.World.Healths.Get(a).Hp = 5;
        s.World.Healths.Get(b).Hp = 5;
        s.Step();
        Assert.False(s.World.Healths.Has(a));
        Assert.False(s.World.Healths.Has(b));
        Assert.Equal(2, s.Combat.LastTickKills);
        Assert.Contains(s.World.RemovedEvents, e => e.EntityId == a && e.Reason == "death");
    }

    [Fact]
    public void Battle_saved_midway_continues_identically()
    {
        var battle = new BattleScenario(seed: 5UL);
        for (int tick = 0; tick < 100; tick++)
        {
            battle.Simulation.Step();
        }

        Simulation restored = Simulation.LoadState(battle.Simulation.SaveState(), TestContent.Real);
        for (int tick = 0; tick < 300; tick++)
        {
            battle.Simulation.Step();
            restored.Step();
            Assert.Equal(battle.Simulation.ComputeHash(), restored.ComputeHash());
        }
    }

    [Fact]
    public void Both_armies_trade_hits_and_take_losses()
    {
        var battle = new BattleScenario(seed: 5UL);
        Assert.Equal(new[] { 1, 2 }, battle.LivingFactions());
        var hitsByFaction = new System.Collections.Generic.Dictionary<int, int> { [1] = 0, [2] = 0 };
        int meleeHits = 0;
        var factionOf = new System.Collections.Generic.Dictionary<int, int>();
        foreach (int id in battle.Simulation.World.Tags)
        {
            factionOf[id] = battle.Simulation.World.Tags.Get(id).Faction;
        }

        for (int tick = 0; tick < 1200; tick++)
        {
            battle.Simulation.Step();
            foreach (AttackEvent hit in battle.Simulation.Combat.LastTickEvents)
            {
                hitsByFaction[factionOf[hit.Attacker]]++;
                meleeHits += hit.Damage == 5 ? 1 : 0; // Only the club deals 5.
            }
        }

        Assert.True(hitsByFaction[1] > 50 && hitsByFaction[2] > 50, $"hits {hitsByFaction[1]} / {hitsByFaction[2]}");
        Assert.True(battle.Simulation.World.Healths.Count < 60, "nobody died");
        Assert.True(meleeHits > 0, "melee units never landed a hit");
    }
}
