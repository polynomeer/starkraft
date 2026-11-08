using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Ground and air. Flyers walk 0.15 tiles a tick. The club hits only the ground (the default), the bow both layers,
/// and the flak only the air.
/// </summary>
public class LayerTests
{
    private const string Weapons = """
        {"list":[
          {"id":"club","damage":10,"range":0.5,"cooldownMs":50},
          {"id":"bow","damage":10,"range":4.0,"cooldownMs":50,"targets":["ground","air"]},
          {"id":"flak","damage":10,"range":4.0,"cooldownMs":50,"targets":["air"]}
        ]}
        """;

    private const string Units = """
        {"list":[
          {"id":"brute","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"club"},
          {"id":"archer","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"bow"},
          {"id":"gunner","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"flak"},
          {"id":"walker","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0},
          {"id":"flyer","archetype":"flyer","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"layer":"air"},
          {"id":"hawk","archetype":"flyer","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"layer":"air","weaponId":"bow"},
          {"id":"bomber","archetype":"infantry","hp":10,"radius":0.4,"speedTilesPerSecond":3.0,
           "triggers":[{"on":"death","effects":[{"type":"damage","amount":30,"target":{"shape":"circle","radius":1.0,"faction":"enemy"}}]}]}
        ]}
        """;

    private const string Empty = """{"list":[]}""";

    private static readonly GameContent Data = ContentParser.Parse(Units, Weapons, Empty, Empty);

    private static Simulation New(PathSettings? paths = null) => Simulation.Create(new MatchSetup(32, 32, 1UL, paths), Data);

    private static Fix Tile(int value) => Fix.FromInt(value) + Fix.Half;

    private static int Unit(Simulation s, string type, int x, int y, int faction, int hp = 100)
    {
        WeaponRef? weapon = Data.UnitOrNull(type)?.WeaponId is string id ? new WeaponRef(id) : null;
        return s.World.Spawn(new Position(Tile(x), Tile(y)), new UnitTag(faction, type), new Health(hp, hp, 0), weapon);
    }

    private static void Bystanders(Simulation s)
    {
        Unit(s, "walker", 1, 30, faction: 1);
        Unit(s, "walker", 30, 30, faction: 2);
    }

    private static void Run(Simulation s, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            s.Step();
        }
    }

    private static (Fix X, Fix Y) At(Simulation s, int id) => (s.World.Positions.Get(id).X, s.World.Positions.Get(id).Y);

    /// <summary>A wall across the map at x = 8, which ground units cannot get past, with an optional gap in the middle.</summary>
    private static void Wall(Simulation s, bool gap = false)
    {
        for (int y = 0; y < 32; y++)
        {
            s.World.Map.SetBlocked(8, y, !gap || y < 12 || y > 19);
        }
    }

    /// <summary>Movement stops within a twentieth of a tile of the goal's centre.</summary>
    private static void AssertArrived(Simulation s, int id, int x, int y)
    {
        (Fix atX, Fix atY) = At(s, id);
        Assert.True(Fix.Length(atX - Tile(x), atY - Tile(y)) <= Fix.FromRatio(1, 20), $"at {atX}, {atY}");
    }

    [Fact]
    public void Flyer_crosses_a_wall_in_a_straight_line()
    {
        Simulation s = New();
        Bystanders(s);
        Wall(s);
        int flyer = Unit(s, "flyer", 2, 5, faction: 1);
        int walker = Unit(s, "walker", 2, 7, faction: 1);
        s.Step(new PlayerCommand[]
        {
            new PlayerCommand.Move(1, new[] { flyer }, Tile(14), Tile(5)),
            new PlayerCommand.Move(1, new[] { walker }, Tile(14), Tile(7)),
        });

        for (int i = 0; i < 90; i++)
        {
            s.Step();
            Assert.Equal(Tile(5), At(s, flyer).Y);
        }

        AssertArrived(s, flyer, 14, 5);
        Assert.Empty(s.World.Orders.Get(flyer));
        Assert.True(At(s, walker).X < Fix.FromInt(8), "the wall still stops the ground");
    }

    [Fact]
    public void Flyer_path_costs_no_search_budget()
    {
        Simulation s = New(new PathSettings(NodeBudgetPerTick: 1, QueueLimit: 64, EnqueuesPerTick: 64));
        Bystanders(s);
        int flyer = Unit(s, "flyer", 2, 5, faction: 1);

        s.Step(new PlayerCommand[] { new PlayerCommand.Move(1, new[] { flyer }, Tile(20), Tile(20)) });
        s.Step();

        Assert.Equal(0, s.Pathing.LastTickNodesUsed);
        PathFollow path = s.World.PathFollows.Get(flyer);
        Assert.Equal((1, 20, 20), (path.Length, path.GoalX, path.GoalY));
    }

    [Fact]
    public void Flyers_take_no_tile_so_ground_units_and_other_flyers_share_it()
    {
        Simulation s = New();
        Bystanders(s);
        int hovering = Unit(s, "flyer", 10, 10, faction: 1);
        int flyer = Unit(s, "flyer", 4, 10, faction: 1);
        int walker = Unit(s, "walker", 10, 4, faction: 1);
        s.Step(new PlayerCommand[]
        {
            new PlayerCommand.Move(1, new[] { flyer }, Tile(10), Tile(10)),
            new PlayerCommand.Move(1, new[] { walker }, Tile(10), Tile(10)),
        });
        Assert.False(s.World.Occupancy.IsBlocked(4, 10), "a flyer leaves no mark on the grid");

        Run(s, 80);

        Assert.Equal((Tile(10), Tile(10)), At(s, hovering));
        AssertArrived(s, flyer, 10, 10);
        AssertArrived(s, walker, 10, 10);
    }

    [Theory]
    [InlineData("brute", "flyer", false)]
    [InlineData("archer", "flyer", true)]
    [InlineData("archer", "walker", true)]
    [InlineData("gunner", "flyer", true)]
    [InlineData("gunner", "walker", false)]
    [InlineData("hawk", "walker", true)]
    public void Weapons_hit_only_the_layers_they_name(string shooter, string target, bool hits)
    {
        Simulation s = New();
        Bystanders(s);
        Unit(s, shooter, 5, 5, faction: 1);
        int victim = Unit(s, target, 6, 5, faction: 2);

        s.Step();

        Assert.Equal(hits ? 90 : 100, s.World.Healths.Get(victim).Hp);
    }

    [Fact]
    public void Attack_order_skips_units_that_cannot_hit_the_target()
    {
        Simulation s = New();
        Bystanders(s);
        int brute = Unit(s, "brute", 5, 5, faction: 1);
        int archer = Unit(s, "archer", 5, 6, faction: 1);
        int walker = Unit(s, "walker", 5, 7, faction: 1);
        int flyer = Unit(s, "flyer", 20, 5, faction: 2);
        int ground = Unit(s, "walker", 20, 8, faction: 2);
        s.World.Orders.Get(brute).Add(new Order.Hold());

        CommandResult mixed = s.Step(new PlayerCommand[] { new PlayerCommand.Attack(1, new[] { brute, archer, walker }, flyer) })[0];
        CommandResult none = s.Step(new PlayerCommand[] { new PlayerCommand.Attack(1, new[] { brute, walker }, flyer) })[0];
        CommandResult unarmed = s.Step(new PlayerCommand[] { new PlayerCommand.Attack(1, new[] { walker }, ground) })[0];

        Assert.Equal(1, mixed.AppliedUnits);
        Assert.Equal(new Order.Attack(flyer), s.World.Orders.Get(archer)[0]);
        Assert.IsType<Order.Hold>(Assert.Single(s.World.Orders.Get(brute)));
        Assert.Equal(CommandRejection.InvalidTarget, none.Rejection);
        Assert.True(unarmed.Accepted, "an unarmed unit may still follow a ground target");
    }

    [Fact]
    public void Attack_move_does_not_stop_for_enemies_it_cannot_hit()
    {
        Simulation s = New();
        Bystanders(s);
        int brute = Unit(s, "brute", 2, 5, faction: 1);
        Unit(s, "flyer", 6, 5, faction: 2);

        s.Step(new PlayerCommand[] { new PlayerCommand.AttackMove(1, new[] { brute }, Tile(12), Tile(5)) });
        Run(s, 80);

        AssertArrived(s, brute, 12, 5);
    }

    [Fact]
    public void Hawk_chases_a_ground_target_across_a_wall()
    {
        Simulation s = New();
        Bystanders(s);
        Wall(s);
        int hawk = Unit(s, "hawk", 2, 5, faction: 1);
        int prey = Unit(s, "walker", 16, 5, faction: 2);

        s.Step(new PlayerCommand[] { new PlayerCommand.Attack(1, new[] { hawk }, prey) });
        Run(s, 120);

        Assert.False(s.World.Positions.Has(prey));
        Assert.True(At(s, hawk).X > Fix.FromInt(8));
    }

    [Fact]
    public void Area_effects_reach_both_layers()
    {
        Simulation s = New();
        Bystanders(s);
        Unit(s, "archer", 4, 5, faction: 1);
        Unit(s, "bomber", 6, 5, faction: 2, hp: 10);
        int flyer = Unit(s, "flyer", 7, 5, faction: 1);

        s.Step();

        Assert.Equal(70, s.World.Healths.Get(flyer).Hp);
    }

    [Fact]
    public void Layers_and_weapon_targets_are_part_of_the_content_hash()
    {
        GameContent grounded = ContentParser.Parse(Units.Replace("\"layer\":\"air\",\"weaponId\"", "\"weaponId\""), Weapons, Empty, Empty);
        GameContent flakless = ContentParser.Parse(Units, Weapons.Replace("\"targets\":[\"air\"]", "\"targets\":[\"ground\"]"), Empty, Empty);

        Assert.NotEqual(Data.Hash, grounded.Hash);
        Assert.NotEqual(Data.Hash, flakless.Hash);
        Assert.Equal(MoveLayer.Ground, Data.UnitOrNull("brute")!.Layer);
        Assert.Equal(LayerMask.Ground, Data.WeaponOrNull("club")!.Targets);
    }

    [Theory]
    [InlineData("\"layer\":\"air\"", "\"layer\":\"water\"", "", "unknown layer 'water'")]
    [InlineData("", "", "\"targets\":[]", "needs at least one layer")]
    [InlineData("", "", "\"targets\":[\"space\"]", "unknown target layer 'space'")]
    public void Bad_layers_are_rejected(string unitFrom, string unitTo, string targets, string message)
    {
        string units = unitFrom == "" ? Units : Units.Replace(unitFrom, unitTo);
        string weapons = targets == "" ? Weapons : Weapons.Replace("\"targets\":[\"air\"]", targets);
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(units, weapons, Empty, Empty));
        Assert.Contains(message, error.Message);
    }

    /// <summary>Hawks and flak gunners against archers and brutes, with a gap in the wall for the ground, for the cross-platform hash check.</summary>
    internal static Simulation Skies()
    {
        Simulation s = New();
        Wall(s, gap: true);
        string[] left = { "hawk", "gunner", "archer", "flyer" };
        string[] right = { "hawk", "brute", "archer", "gunner" };
        for (int i = 0; i < 16; i++)
        {
            int a = Unit(s, left[i % left.Length], 2 + i % 3, 2 + i * 2 - i % 2, faction: 1);
            int b = Unit(s, right[i % right.Length], 29 - i % 3, 2 + i * 2 - i % 2, faction: 2);
            s.World.Orders.Get(a).Add(new Order.AttackMove(Tile(26), Tile(2 + i * 2 - i % 2)));
            s.World.Orders.Get(b).Add(new Order.AttackMove(Tile(4), Tile(2 + i * 2 - i % 2)));
        }

        return s;
    }

    [Fact]
    public void Skies_fight_on_both_layers()
    {
        Simulation s = Skies();
        var hitLayers = new HashSet<(MoveLayer, MoveLayer)>();
        for (int i = 0; i < 600; i++)
        {
            s.Step();
            foreach (AttackEvent hit in s.Combat.LastTickEvents)
            {
                if (s.World.Tags.Has(hit.Attacker) && s.World.Tags.Has(hit.Target))
                {
                    hitLayers.Add((Layers.Of(s.World, Data, hit.Attacker), Layers.Of(s.World, Data, hit.Target)));
                }
            }
        }

        Assert.True(hitLayers.Count == 4, string.Join(",", hitLayers));
    }
}
