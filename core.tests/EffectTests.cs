using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Statuses, triggers and area effects. Unknown "dummy" units have no data, so they have the default radius 0.4 and
/// no triggers. The blade fires every tick.
/// </summary>
public class EffectTests
{
    private const string Statuses = """
        {"list":[
          {"id":"frenzy","durationMs":10000,"maxStacks":3,"damagePerStack":2},
          {"id":"weakened","durationMs":250,"armorPerStack":-2},
          {"id":"guarded","durationMs":1000,"armorPerStack":3},
          {"id":"slowed","durationMs":1000,"speedPercentPerStack":-50}
        ]}
        """;

    private const string Units = """
        {"list":[
          {"id":"fighter","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"blade"},
          {"id":"runner","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0},
          {"id":"grower","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"blade",
           "triggers":[{"on":"kill","effects":[{"type":"applyStatus","status":"frenzy","target":{"shape":"self"}}]}]},
          {"id":"bomber","archetype":"infantry","hp":10,"radius":0.4,"speedTilesPerSecond":3.0,
           "triggers":[{"on":"death","effects":[{"type":"damage","amount":30,"target":{"shape":"circle","radius":1.0,"faction":"enemy"}}]}]},
          {"id":"thorn","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"blade",
           "triggers":[{"on":"damaged","effects":[{"type":"damage","amount":3,"target":{"shape":"other","faction":"enemy"}}]}]},
          {"id":"leech","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"blade",
           "triggers":[{"on":"hit","effects":[{"type":"heal","amount":4,"target":{"shape":"self"}}]}]}
        ]}
        """;

    private const string Weapons = """{"list":[{"id":"blade","damage":10,"range":4.0,"cooldownMs":50}]}""";
    private const string Empty = """{"list":[]}""";

    private static readonly GameContent Data = ContentParser.Parse(Units, Weapons, Empty, Empty, Statuses);

    /// <summary>Two mixed armies that use every effect, for the cross-platform hash check.</summary>
    internal static Simulation Skirmish()
    {
        Simulation s = New();
        string[] types = { "grower", "bomber", "thorn", "leech", "fighter" };
        for (int i = 0; i < 20; i++)
        {
            string type = types[i % types.Length];
            int left = Unit(s, type, 2 + i % 4, 4 + i / 4 * 4, faction: 1);
            int right = Unit(s, type, 29 - i % 4, 4 + i / 4 * 4, faction: 2);
            s.World.Orders.Get(left).Add(new Order.AttackMove(Tile(24), Tile(4 + i / 4 * 4)));
            s.World.Orders.Get(right).Add(new Order.AttackMove(Tile(7), Tile(4 + i / 4 * 4)));
        }

        return s;
    }

    private static Simulation New() => Simulation.Create(new MatchSetup(32, 32, 1UL), Data);

    private static Fix F(int value) => Fix.FromInt(value);

    private static Fix Tile(int value) => Fix.FromInt(value) + Fix.Half;

    private static int Unit(Simulation s, string type, int x, int y, int faction, int hp = 100, int armor = 0)
    {
        WeaponRef? weapon = Data.UnitOrNull(type)?.WeaponId is string weaponId ? new WeaponRef(weaponId) : null;
        return s.World.Spawn(new Position(Tile(x), Tile(y)), new UnitTag(faction, type), new Health(hp, hp, armor), weapon);
    }

    /// <summary>Far-away units on both sides, so victory does not end the match mid-test.</summary>
    private static void Bystanders(Simulation s)
    {
        Unit(s, "runner", 1, 30, faction: 1);
        Unit(s, "runner", 30, 30, faction: 2);
    }

    private static void Run(Simulation s, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            s.Step();
        }
    }

    private static StatusInstance? Status(Simulation s, int id, string statusId) =>
        s.World.Statuses.TryGet(id, out List<StatusInstance>? list) ? list.FirstOrDefault(x => x.StatusId == statusId) : null;

    private static void Give(Simulation s, int id, string statusId, int stacks = 1, int ticks = 100) =>
        s.World.Statuses.Set(id, new List<StatusInstance> { new(statusId, stacks, ticks) });

    [Fact]
    public void Statuses_count_down_before_any_other_system()
    {
        var order = New().SystemOrder.ToList();
        Assert.Equal(order.IndexOf("AliveSystem") + 1, order.IndexOf("StatusSystem"));
    }

    [Fact]
    public void Status_is_in_force_for_exactly_its_duration()
    {
        Simulation s = New();
        Bystanders(s);
        int unit = Unit(s, "runner", 5, 5, faction: 1);
        Give(s, unit, "weakened", ticks: 5);

        Run(s, 5);
        Assert.NotNull(Status(s, unit, "weakened"));
        Assert.Equal(0, Status(s, unit, "weakened")!.RemainingTicks);

        Run(s, 1);
        Assert.False(s.World.Statuses.Has(unit));
    }

    [Fact]
    public void Kill_trigger_stacks_to_the_limit_restarts_the_timer_and_adds_damage()
    {
        Simulation s = New();
        Bystanders(s);
        int grower = Unit(s, "grower", 5, 5, faction: 1);
        for (int i = 0; i < 4; i++)
        {
            Unit(s, "dummy", 6, 3 + i, faction: 2, hp: 10);
        }

        Run(s, 4);
        StatusInstance frenzy = Status(s, grower, "frenzy")!;
        Assert.Equal(3, frenzy.Stacks);
        Assert.Equal(Data.StatusOrNull("frenzy")!.DurationTicks, frenzy.RemainingTicks);

        int wall = Unit(s, "dummy", 6, 5, faction: 2, hp: 100);
        s.Step();
        AttackEvent hit = Assert.Single(s.Combat.LastTickEvents);
        Assert.Equal(wall, hit.Target);
        Assert.Equal(10 + 3 * 2, hit.Damage);
    }

    [Theory]
    [InlineData("weakened", 11)] // armor 1 - 2 = -1: negative armor adds damage
    [InlineData("guarded", 6)]   // armor 1 + 3 = 4
    public void Armor_statuses_change_weapon_damage(string status, int expected)
    {
        Simulation s = New();
        Bystanders(s);
        Unit(s, "fighter", 5, 5, faction: 1);
        int target = Unit(s, "dummy", 7, 5, faction: 2, armor: 1);
        Give(s, target, status);

        s.Step();
        Assert.Equal(expected, Assert.Single(s.Combat.LastTickEvents).Damage);
    }

    [Fact]
    public void Speed_status_scales_movement()
    {
        Simulation s = New();
        Bystanders(s);
        int normal = Unit(s, "runner", 2, 5, faction: 1);
        int slowed = Unit(s, "runner", 2, 8, faction: 1);
        Give(s, slowed, "slowed");
        s.World.Orders.Get(normal).Add(new Order.Move(Tile(25), Tile(5)));
        s.World.Orders.Get(slowed).Add(new Order.Move(Tile(25), Tile(8)));

        const int ticks = 20;
        Run(s, ticks);
        int normalRaw = (s.World.Positions.Get(normal).X - Tile(2)).Raw;
        int slowedRaw = (s.World.Positions.Get(slowed).X - Tile(2)).Raw;
        Assert.True(normalRaw > 0);
        Assert.InRange(slowedRaw * 2, normalRaw - ticks, normalRaw);
    }

    [Fact]
    public void Death_trigger_hits_enemies_whose_edge_is_inside_the_circle()
    {
        Simulation s = New();
        Bystanders(s);
        int fighter = Unit(s, "fighter", 7, 10, faction: 1);
        int bomber = Unit(s, "bomber", 10, 10, faction: 2, hp: 10);
        int enemyNear = Unit(s, "dummy", 11, 10, faction: 1, hp: 50); // edge 0.6 from the bomber's centre
        int enemyFar = Unit(s, "dummy", 12, 10, faction: 1, hp: 50);  // edge 1.6 away
        int allyNear = Unit(s, "dummy", 10, 11, faction: 2, hp: 50);
        s.World.Orders.Get(fighter).Add(new Order.Attack(bomber));

        s.Step();
        Assert.False(s.World.Positions.Has(bomber));
        Assert.Equal(20, s.World.Healths.Get(enemyNear).Hp);
        Assert.Equal(50, s.World.Healths.Get(enemyFar).Hp);
        Assert.Equal(50, s.World.Healths.Get(allyNear).Hp);
        Assert.Equal(100, s.World.Healths.Get(fighter).Hp);
        Assert.Equal(enemyNear, Assert.Single(s.Effects.LastTickEvents).Target);
    }

    [Fact]
    public void Kills_by_effect_damage_chain_until_the_depth_limit()
    {
        Simulation s = New();
        Bystanders(s);
        int fighter = Unit(s, "fighter", 7, 10, faction: 1);
        var bombers = new int[8];
        for (int i = 0; i < bombers.Length; i++)
        {
            bombers[i] = Unit(s, "bomber", 10 + i, 10, faction: i % 2 == 0 ? 2 : 1, hp: 10);
        }

        s.World.Orders.Get(fighter).Add(new Order.Attack(bombers[0]));
        s.Step();

        // The weapon kill is depth 0; effect kills at depths 1 to 4 fire again; the kill at depth 5 does not.
        bool[] alive = bombers.Select(id => s.World.Positions.Has(id)).ToArray();
        Assert.Equal(new[] { false, false, false, false, false, false, true, true }, alive);
        Assert.Equal(EffectSystem.MaxChainDepth + 1, s.Effects.LastTickKills);
    }

    [Fact]
    public void Damaged_trigger_answers_weapon_hits_but_not_effect_damage()
    {
        Simulation s = New();
        Bystanders(s);
        int a = Unit(s, "thorn", 5, 5, faction: 1);
        int b = Unit(s, "thorn", 7, 5, faction: 2);

        s.Step();
        Assert.Equal(100 - 10 - 3, s.World.Healths.Get(a).Hp);
        Assert.Equal(100 - 10 - 3, s.World.Healths.Get(b).Hp);
    }

    [Fact]
    public void Hit_trigger_heals_up_to_maximum_health()
    {
        Simulation s = New();
        Bystanders(s);
        int leech = Unit(s, "leech", 5, 5, faction: 1);
        s.World.Healths.Get(leech).Hp = 98;
        Unit(s, "dummy", 7, 5, faction: 2);

        s.Step();
        Assert.Equal(100, s.World.Healths.Get(leech).Hp);
    }

    [Fact]
    public void Kill_trigger_of_a_unit_that_died_in_the_same_tick_does_not_fire()
    {
        Simulation s = New();
        Bystanders(s);
        int grower = Unit(s, "grower", 5, 5, faction: 1, hp: 10);
        Unit(s, "fighter", 7, 5, faction: 2, hp: 10);

        s.Step();
        Assert.False(s.World.Positions.Has(grower));
        Assert.Empty(s.Effects.LastTickEvents);
    }

    /// <summary>Guards the cross-platform check: its skirmish must keep exercising every effect kind.</summary>
    [Fact]
    public void Skirmish_uses_every_effect_kind()
    {
        Simulation s = Skirmish();
        var kinds = new HashSet<EffectKind>();
        for (int tick = 0; tick <= 600; tick++)
        {
            s.Step();
            foreach (EffectEvent e in s.Effects.LastTickEvents)
            {
                kinds.Add(e.Kind);
            }
        }

        Assert.Equal(new[] { EffectKind.ApplyStatus, EffectKind.Damage, EffectKind.Heal }, kinds.Order().ToArray());
    }

    [Fact]
    public void Statuses_survive_save_and_load()
    {
        Simulation s = New();
        Bystanders(s);
        int grower = Unit(s, "grower", 5, 5, faction: 1);
        for (int i = 0; i < 6; i++)
        {
            Unit(s, "dummy", 6 + i % 2, 3 + i / 2, faction: 2, hp: 15);
        }

        Run(s, 3);
        Assert.True(s.World.Statuses.Has(grower));
        Simulation loaded = Simulation.LoadState(s.SaveState(), Data);
        Assert.Equal(s.ComputeHash(), loaded.ComputeHash());

        Run(s, 30);
        Run(loaded, 30);
        Assert.Equal(s.ComputeHash(), loaded.ComputeHash());
    }

    [Fact]
    public void Statuses_and_triggers_are_part_of_the_content_hash()
    {
        GameContent changed = ContentParser.Parse(Units, Weapons, Empty, Empty, Statuses.Replace("\"damagePerStack\":2", "\"damagePerStack\":3"));
        GameContent noTrigger = ContentParser.Parse(Units.Replace("\"amount\":30", "\"amount\":31"), Weapons, Empty, Empty, Statuses);
        Assert.NotEqual(Data.Hash, changed.Hash);
        Assert.NotEqual(Data.Hash, noTrigger.Hash);
    }

    [Theory]
    [InlineData("""{"type":"damage","amount":5,"target":{"shape":"circle","radius":2}}""", "needs 'faction'")]
    [InlineData("""{"type":"damage","amount":5,"target":{"shape":"self","radius":2}}""", "only a circle")]
    [InlineData("""{"type":"applyStatus","status":"missing","target":{"shape":"self"}}""", "unknown status 'missing'")]
    [InlineData("""{"type":"damage","amount":5,"status":"frenzy","target":{"shape":"self"}}""", "unknown key 'status'")]
    [InlineData("""{"type":"poison","target":{"shape":"self"}}""", "unknown effect type")]
    [InlineData("""{"type":"heal","amount":0,"target":{"shape":"self"}}""", "'amount'")]
    public void Bad_effects_are_rejected(string effect, string message)
    {
        string units = "{\"list\":[{\"id\":\"u\",\"archetype\":\"a\",\"hp\":1,\"radius\":0.4,\"speedTilesPerSecond\":1,"
            + "\"triggers\":[{\"on\":\"death\",\"effects\":[" + effect + "]}]}]}";
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(units, Empty, Empty, Empty, Statuses));
        Assert.Contains(message, error.Message);
    }

    [Theory]
    [InlineData("""[{"on":"spawn","effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}]""", "unknown trigger")]
    [InlineData("""[{"on":"kill","effects":[]}]""", "at least one effect")]
    [InlineData("""[{"on":"kill","effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]},{"on":"kill","effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}]""", "more than one")]
    public void Bad_triggers_are_rejected(string triggers, string message)
    {
        string units = "{\"list\":[{\"id\":\"u\",\"archetype\":\"a\",\"hp\":1,\"radius\":0.4,\"speedTilesPerSecond\":1,\"triggers\":" + triggers + "}]}";
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(units, Empty, Empty, Empty, Statuses));
        Assert.Contains(message, error.Message);
    }

    [Fact]
    public void Status_without_a_duration_is_rejected()
    {
        var error = Assert.Throws<InvalidDataException>(
            () => ContentParser.Parse(Empty, Empty, Empty, Empty, """{"list":[{"id":"x","durationMs":0}]}"""));
        Assert.Contains("durationMs", error.Message);
    }
}
