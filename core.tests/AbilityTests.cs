using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Targeted abilities and cooldowns. The shaman has no weapon, so anything that happens to its targets comes from its
/// abilities. Shamans walk 0.15 tiles a tick; runners have no weapon either.
/// </summary>
public class AbilityTests
{
    private const string Statuses = """
        {"list":[
          {"id":"weakened","durationMs":1000,"armorPerStack":-2},
          {"id":"guarded","durationMs":1000,"armorPerStack":3}
        ]}
        """;

    private const string Abilities = """
        {"list":[
          {"id":"bell","target":"point","range":3.0,"cooldownMs":500,
           "effects":[{"type":"damage","amount":20,"target":{"shape":"circle","radius":1.0,"faction":"enemy"}}]},
          {"id":"mend","target":"unit","targetFaction":"ally","range":2.0,"cooldownMs":250,
           "effects":[{"type":"heal","amount":30,"target":{"shape":"other"}}]},
          {"id":"hex","target":"unit","targetFaction":"enemy","range":5.0,"cooldownMs":1000,
           "effects":[{"type":"applyStatus","status":"weakened","target":{"shape":"other"}}]},
          {"id":"roar","target":"none",
           "effects":[{"type":"applyStatus","status":"guarded","target":{"shape":"circle","radius":2.0,"faction":"ally"}}]},
          {"id":"raise","target":"point","range":2.0,"cooldownMs":2000,
           "effects":[{"type":"summon","unit":"runner","count":2,"at":"other","lifetimeMs":250}]}
        ]}
        """;

    private const string Units = """
        {"list":[
          {"id":"shaman","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":3.0,
           "abilities":["bell","mend","hex","roar","raise"]},
          {"id":"runner","archetype":"infantry","hp":100,"radius":0.4,"speedTilesPerSecond":1.0},
          {"id":"bomber","archetype":"infantry","hp":10,"radius":0.4,"speedTilesPerSecond":1.0,
           "triggers":[{"on":"death","effects":[{"type":"damage","amount":30,"target":{"shape":"circle","radius":1.0,"faction":"enemy"}}]}]}
        ]}
        """;

    private const string Empty = """{"list":[]}""";

    private static readonly GameContent Data = ContentParser.Parse(Units, Empty, Empty, Empty, Statuses, Abilities);

    private static Simulation New() => Simulation.Create(new MatchSetup(32, 32, 1UL), Data);

    private static Fix Tile(int value) => Fix.FromInt(value) + Fix.Half;

    private static int Unit(Simulation s, string type, int x, int y, int faction, int hp = 100) =>
        s.World.Spawn(new Position(Tile(x), Tile(y)), new UnitTag(faction, type), new Health(hp, 100, 0));

    /// <summary>Far-away units on both sides, so victory does not end the match mid-test.</summary>
    private static void Bystanders(Simulation s)
    {
        Unit(s, "runner", 1, 30, faction: 1);
        Unit(s, "runner", 30, 30, faction: 2);
    }

    private static CommandResult Order(Simulation s, PlayerCommand command) => s.Step(new[] { command })[0];

    /// <summary>Steps until a cast happens and returns how many steps that took, or fails.</summary>
    private static int StepsUntilCast(Simulation s, int limit)
    {
        for (int i = 1; i <= limit; i++)
        {
            s.Step();
            if (s.Abilities.LastTickCasts.Count > 0)
            {
                return i;
            }
        }

        throw new Xunit.Sdk.XunitException($"No cast within {limit} ticks.");
    }

    private static bool Has(Simulation s, int id, string status) =>
        s.World.Statuses.TryGet(id, out List<StatusInstance>? list) && list.Any(x => x.StatusId == status);

    [Fact]
    public void Abilities_are_cast_after_movement_and_before_combat()
    {
        var order = New().SystemOrder.ToList();
        Assert.Equal(order.IndexOf("MovementSystem") + 1, order.IndexOf("AbilitySystem"));
        Assert.Equal(order.IndexOf("AbilitySystem") + 1, order.IndexOf("CombatSystem"));
    }

    [Fact]
    public void Untargeted_cast_happens_the_tick_it_is_ordered_and_reaches_allies_around_the_caster()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);
        int near = Unit(s, "runner", 6, 6, faction: 1);
        int far = Unit(s, "runner", 9, 5, faction: 1);
        int enemy = Unit(s, "runner", 5, 6, faction: 2);

        CommandResult result = Order(s, new PlayerCommand.Cast(1, shaman, "roar"));

        Assert.True(result.Accepted);
        Assert.Equal(new CastEvent(shaman, "roar", 0, Fix.Zero, Fix.Zero), Assert.Single(s.Abilities.LastTickCasts));
        Assert.True(Has(s, shaman, "guarded"));
        Assert.True(Has(s, near, "guarded"));
        Assert.False(Has(s, far, "guarded"));
        Assert.False(Has(s, enemy, "guarded"));
        Assert.Empty(s.World.Orders.Get(shaman));
    }

    [Fact]
    public void Point_cast_walks_into_range_then_hits_only_enemies_around_the_point()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 2, 5, faction: 1);
        int enemy = Unit(s, "runner", 12, 5, faction: 2);
        int ally = Unit(s, "runner", 12, 6, faction: 1);

        Assert.True(Order(s, new PlayerCommand.Cast(1, shaman, "bell", X: Tile(12), Y: Tile(5))).Accepted);
        Assert.Empty(s.Abilities.LastTickCasts);
        StepsUntilCast(s, 100);

        // Range 3 from the caster's edge (radius 0.4) to the point: it stops within 3.4 tiles of the point's centre.
        Position at = s.World.Positions.Get(shaman);
        Fix distance = Fix.Length(Tile(12) - at.X, Tile(5) - at.Y);
        Assert.True(distance <= Fix.FromRatio(34, 10), $"cast from {distance}");
        Assert.True(distance > Fix.FromRatio(31, 10), $"walked too far: {distance}");
        Assert.Equal(80, s.World.Healths.Get(enemy).Hp);
        Assert.Equal(100, s.World.Healths.Get(ally).Hp);
        Assert.False(s.World.PathFollows.Has(shaman));
        Assert.Empty(s.World.Orders.Get(shaman));
    }

    [Fact]
    public void Unit_cast_follows_a_moving_target()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 2, 10, faction: 1);
        int friend = Unit(s, "runner", 10, 10, faction: 1, hp: 40);
        s.World.Orders.Get(friend).Add(new Order.Move(Tile(20), Tile(10)));

        Assert.True(Order(s, new PlayerCommand.Cast(1, shaman, "mend", Target: friend)).Accepted);
        StepsUntilCast(s, 200);

        Assert.Equal(70, s.World.Healths.Get(friend).Hp);
        Assert.True(s.World.Positions.Get(friend).X > Tile(10), "the target had moved");
    }

    [Fact]
    public void Cooldown_is_exactly_its_length_and_a_cast_on_cooldown_waits_instead_of_failing()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);
        int friend = Unit(s, "runner", 6, 5, faction: 1, hp: 10);

        Assert.True(Order(s, new PlayerCommand.Cast(1, shaman, "mend", Target: friend)).Accepted);
        Assert.Single(s.Abilities.LastTickCasts);

        // mend's 250 ms is 5 ticks: the next cast comes 5 ticks after the first.
        Assert.True(Order(s, new PlayerCommand.Cast(1, shaman, "mend", Target: friend)).Accepted);
        Assert.Empty(s.Abilities.LastTickCasts);
        Assert.Equal(4, StepsUntilCast(s, 10));
        Assert.Equal(70, s.World.Healths.Get(friend).Hp);
    }

    [Fact]
    public void Each_ability_has_its_own_cooldown()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);
        int enemy = Unit(s, "runner", 8, 5, faction: 2);

        Order(s, new PlayerCommand.Cast(1, shaman, "hex", Target: enemy));
        Assert.True(Has(s, enemy, "weakened"));
        Order(s, new PlayerCommand.Cast(1, shaman, "roar"));

        Assert.Equal("roar", Assert.Single(s.Abilities.LastTickCasts).AbilityId);
        Assert.Equal("hex", Assert.Single(s.World.Cooldowns.Get(shaman)).AbilityId);
    }

    [Theory]
    [InlineData("unknown", 0, 5, 5, "UnknownAbility")]
    [InlineData("mend", 3, 0, 0, "InvalidTarget")]
    [InlineData("hex", 2, 0, 0, "InvalidTarget")]
    [InlineData("hex", 99, 0, 0, "InvalidTarget")]
    [InlineData("bell", 0, 40, 5, "OutOfBounds")]
    public void Bad_casts_are_rejected(string ability, int target, int x, int y, string detail)
    {
        Simulation s = New();
        int shaman = Unit(s, "shaman", 5, 5, faction: 1); // id 1
        Unit(s, "runner", 6, 5, faction: 1); // id 2
        Unit(s, "runner", 7, 5, faction: 2); // id 3

        CommandResult result = Order(s, new PlayerCommand.Cast(1, shaman, ability, target, Tile(x), Tile(y)));

        Assert.Equal(CommandRejection.Cast, result.Rejection);
        Assert.Equal(detail, result.Detail);
        Assert.Empty(s.World.Orders.Get(shaman));
    }

    [Fact]
    public void Only_your_own_caster_can_cast()
    {
        Simulation s = New();
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);

        Assert.Equal(CommandRejection.NoOwnUnits, Order(s, new PlayerCommand.Cast(2, shaman, "roar")).Rejection);
    }

    [Fact]
    public void Cast_is_dropped_when_its_target_dies_first()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 2, 5, faction: 1);
        int enemy = Unit(s, "runner", 20, 5, faction: 2);

        Order(s, new PlayerCommand.Cast(1, shaman, "hex", Target: enemy));
        s.Step();
        s.World.Remove(enemy, "death");
        s.Step();

        Assert.Empty(s.World.Orders.Get(shaman));
        Assert.False(s.World.PathFollows.Has(shaman));
        Assert.False(s.World.Cooldowns.Has(shaman));
    }

    [Fact]
    public void Kill_by_a_cast_sets_off_the_victims_death_trigger()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);
        int bomber = Unit(s, "bomber", 7, 5, faction: 2, hp: 10);
        int ally = Unit(s, "runner", 8, 5, faction: 1);

        Order(s, new PlayerCommand.Cast(1, shaman, "bell", X: Tile(7), Y: Tile(5)));

        Assert.False(s.World.Positions.Has(bomber));
        Assert.Equal(70, s.World.Healths.Get(ally).Hp); // the bell spares allies; the bomber's death does not
        Assert.Equal(1, s.Effects.LastTickKills);
    }

    [Fact]
    public void Point_summon_appears_at_the_point_and_expires()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);

        Order(s, new PlayerCommand.Cast(1, shaman, "raise", X: Tile(6), Y: Tile(7)));

        int[] summoned = s.Effects.LastTickEvents.Where(e => e.Kind == EffectKind.Summon).Select(e => e.Target).ToArray();
        Assert.Equal(2, summoned.Length);
        Assert.Equal((Tile(6), Tile(7)), (s.World.Positions.Get(summoned[0]).X, s.World.Positions.Get(summoned[0]).Y));
        Assert.All(summoned, id => Assert.Equal(new UnitTag(1, "runner"), s.World.Tags.Get(id)));

        for (int i = 0; i < 6; i++)
        {
            s.Step();
        }

        Assert.All(summoned, id => Assert.False(s.World.Positions.Has(id)));
    }

    [Fact]
    public void Cooldowns_and_cast_orders_survive_save_and_load()
    {
        Simulation s = New();
        Bystanders(s);
        int shaman = Unit(s, "shaman", 5, 5, faction: 1);
        int enemy = Unit(s, "runner", 20, 5, faction: 2);
        Order(s, new PlayerCommand.Cast(1, shaman, "roar"));
        s.World.Cooldowns.Set(shaman, new List<AbilityCooldown> { new("roar", 7) });
        Order(s, new PlayerCommand.Cast(1, shaman, "hex", Target: enemy));

        Simulation loaded = Simulation.LoadState(s.SaveState(), Data);

        Assert.Equal(s.ComputeHash(), loaded.ComputeHash());
        Assert.Equal(new Order.Cast("hex", enemy, Fix.Zero, Fix.Zero), Assert.Single(loaded.World.Orders.Get(shaman)));
        AbilityCooldown cooldown = Assert.Single(loaded.World.Cooldowns.Get(shaman));
        Assert.Equal(("roar", 6), (cooldown.AbilityId, cooldown.RemainingTicks));
        for (int i = 0; i < 50; i++)
        {
            s.Step();
            loaded.Step();
        }

        Assert.Equal(s.ComputeHash(), loaded.ComputeHash());
    }

    [Fact]
    public void Cast_commands_round_trip_through_json()
    {
        var command = new PlayerCommand.Cast(2, 7, "bell", 0, Fix.FromRatio(5, 2), Fix.FromInt(3), Queued: true);
        var buffer = new MemoryStream();
        using (var writer = new Utf8JsonWriter(buffer))
        {
            CommandJson.Write(writer, command);
        }

        using JsonDocument document = JsonDocument.Parse(buffer.ToArray());
        Assert.Equal(command, CommandJson.Read(document.RootElement));
    }

    [Fact]
    public void Abilities_are_part_of_the_content_hash()
    {
        GameContent changed = ContentParser.Parse(Units, Empty, Empty, Empty, Statuses, Abilities.Replace("\"cooldownMs\":500", "\"cooldownMs\":550"));
        GameContent unlisted = ContentParser.Parse(Units.Replace(",\"raise\"", ""), Empty, Empty, Empty, Statuses, Abilities);

        Assert.NotEqual(Data.Hash, changed.Hash);
        Assert.NotEqual(Data.Hash, unlisted.Hash);
    }

    [Theory]
    [InlineData("""{"id":"x","target":"unit","range":1,"effects":[{"type":"heal","amount":1,"target":{"shape":"other"}}]}""", "'targetFaction' is required")]
    [InlineData("""{"id":"x","target":"point","targetFaction":"ally","range":1,"effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}""", "'targetFaction' is required")]
    [InlineData("""{"id":"x","target":"none","range":1,"effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}""", "'range' is required")]
    [InlineData("""{"id":"x","target":"point","effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}""", "'range' is required")]
    [InlineData("""{"id":"x","target":"none","effects":[{"type":"heal","amount":1,"target":{"shape":"other"}}]}""", "uses 'other'")]
    [InlineData("""{"id":"x","target":"point","range":1,"effects":[{"type":"heal","amount":1,"target":{"shape":"other"}}]}""", "uses 'other'")]
    [InlineData("""{"id":"x","target":"point","range":1,"effects":[{"type":"summon","copyOther":true,"at":"other"}]}""", "uses 'other'")]
    [InlineData("""{"id":"x","target":"none","effects":[{"type":"summon","unit":"runner","at":"other"}]}""", "uses 'other'")]
    [InlineData("""{"id":"x","target":"none","effects":[]}""", "needs at least one effect")]
    [InlineData("""{"id":"x","target":"self","effects":[{"type":"heal","amount":1,"target":{"shape":"self"}}]}""", "unknown ability target")]
    public void Bad_abilities_are_rejected(string ability, string message)
    {
        string abilities = "{\"list\":[" + ability + "]}";
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(Units.Replace("\"abilities\":[\"bell\",\"mend\",\"hex\",\"roar\",\"raise\"]", "\"abilities\":[]"), Empty, Empty, Empty, Statuses, abilities));
        Assert.Contains(message, error.Message);
    }

    [Theory]
    [InlineData("[\"bell\",\"nope\"]", "unknown ability 'nope'")]
    [InlineData("[\"bell\",\"bell\"]", "more than once")]
    public void Bad_ability_lists_are_rejected(string list, string message)
    {
        string units = Units.Replace("[\"bell\",\"mend\",\"hex\",\"roar\",\"raise\"]", list);
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(units, Empty, Empty, Empty, Statuses, Abilities));
        Assert.Contains(message, error.Message);
    }

    /// <summary>Shamans on both sides casting every few ticks, for the cross-platform hash check.</summary>
    internal sealed class Duel
    {
        private readonly List<int> _left = new();
        private readonly List<int> _right = new();
        private int _tick;

        public Simulation Simulation { get; } = New();

        public Duel()
        {
            for (int i = 0; i < 6; i++)
            {
                _left.Add(Unit(Simulation, i % 2 == 0 ? "shaman" : "bomber", 4, 4 + i * 4, faction: 1));
                _right.Add(Unit(Simulation, i % 2 == 0 ? "shaman" : "bomber", 27, 4 + i * 4, faction: 2));
            }
        }

        public void Step()
        {
            var commands = new List<PlayerCommand>();
            if (_tick % 15 == 0)
            {
                AddCasts(commands, 1, _left, _right);
                AddCasts(commands, 2, _right, _left);
            }

            Simulation.Step(commands.ToArray());
            _tick++;
        }

        private void AddCasts(List<PlayerCommand> commands, int player, List<int> own, List<int> enemies)
        {
            World world = Simulation.World;
            for (int i = 0; i < own.Count; i += 2)
            {
                int caster = own[i];
                int enemy = enemies.FirstOrDefault(world.IsAttackable);
                if (!world.IsAttackable(caster) || enemy == 0)
                {
                    continue;
                }

                Position at = world.Positions.Get(enemy);
                string[] cycle = { "bell", "hex", "raise", "roar", "mend" };
                string ability = cycle[(_tick / 15 + i) % cycle.Length];
                commands.Add(ability switch
                {
                    "hex" => new PlayerCommand.Cast(player, caster, ability, Target: enemy, Queued: true),
                    "mend" => new PlayerCommand.Cast(player, caster, ability, Target: own[(i + 2) % own.Count], Queued: true),
                    "roar" => new PlayerCommand.Cast(player, caster, ability, Queued: true),
                    _ => new PlayerCommand.Cast(player, caster, ability, X: at.X, Y: at.Y, Queued: true),
                });
            }
        }
    }

    [Fact]
    public void Duel_casts_every_kind_of_ability()
    {
        var duel = new Duel();
        var cast = new HashSet<string>();
        for (int i = 0; i < 600; i++)
        {
            duel.Step();
            foreach (CastEvent e in duel.Simulation.Abilities.LastTickCasts)
            {
                cast.Add(e.AbilityId);
            }
        }

        Assert.Equal(new[] { "bell", "hex", "mend", "raise", "roar" }, cast.OrderBy(x => x));
    }
}
