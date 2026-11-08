using System.IO;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class ContentTests
{
    private const string Empty = """{"list":[]}""";

    private const string Weapons = """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownMs":300}]}""";

    private const string Units = """
        {"list":[
          {"id":"Marine","archetype":"infantry","hp":45,"armor":0,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"Gauss","oreCost":50,"gasCost":0,"buildMs":1500,"producerTypes":["Depot"],"requiredBuildingTypes":["Academy"]}
        ]}
        """;

    private const string Buildings = """
        {"list":[
          {"id":"Depot","archetype":"producer","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsResearch":true,"supportsRally":true,"supportsDropoff":true,"dropoffResourceKinds":["ore"],"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"oreCost":100},
          {"id":"Academy","archetype":"tech","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"productionQueueLimit":0,"oreCost":125,"requiredBuildingTypes":["Depot"],"requiredResearchIds":["AdvancedTraining"]},
          {"id":"ResourceDepot","archetype":"econDepot","hp":350,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsDropoff":true,"dropoffResourceKinds":["ore"],"productionQueueLimit":0,"oreCost":75},
          {"id":"GasDepot","archetype":"gasDepot","hp":325,"armor":1,"footprintWidth":2,"footprintHeight":2,"supportsDropoff":true,"dropoffResourceKinds":["gas"],"oreCost":90}
        ]}
        """;

    private const string Techs = """
        {"list":[
          {"id":"AdvancedTraining","buildMs":1200,"oreCost":75,"producerTypes":["Depot"],"requiredBuildingTypes":["Depot"]}
        ]}
        """;

    private static GameContent Sample() => ContentParser.Parse(Units, Weapons, Buildings, Techs);

    // Ported from DataRepoTest "resolves build and train specs from dedicated data defs".
    [Fact]
    public void Resolves_build_train_and_research_definitions()
    {
        GameContent content = Sample();

        BuildingDef depot = content.BuildingOrNull("Depot")!;
        Assert.Equal("producer", depot.Archetype);
        Assert.Equal(2, depot.FootprintWidth);
        Assert.Equal(2, depot.FootprintHeight);
        Assert.Equal(1, depot.PlacementClearance);
        Assert.True(depot.SupportsTraining && depot.SupportsResearch && depot.SupportsRally && depot.SupportsDropoff);
        Assert.Equal(new[] { ResourceKind.Ore }, depot.DropoffResourceKinds);
        Assert.Equal(3, depot.ProductionQueueLimit);
        Assert.Equal(Fix.FromInt(4), depot.RallyOffsetX);
        Assert.Equal(Fix.Zero, depot.RallyOffsetY);
        Assert.Equal(400, depot.Hp);

        BuildingDef academy = content.BuildingOrNull("Academy")!;
        Assert.Equal("tech", academy.Archetype);
        Assert.Equal(new[] { "Depot" }, academy.RequiredBuildingTypes);
        Assert.Equal(new[] { "AdvancedTraining" }, academy.RequiredResearchIds);
        Assert.False(academy.SupportsTraining);
        BuildingDef resourceDepot = content.BuildingOrNull("ResourceDepot")!;
        Assert.Equal("econDepot", resourceDepot.Archetype);
        Assert.False(resourceDepot.SupportsTraining || resourceDepot.SupportsRally);
        Assert.True(resourceDepot.SupportsDropoff);
        Assert.Equal(new[] { ResourceKind.Ore }, resourceDepot.DropoffResourceKinds);
        Assert.Equal(75, resourceDepot.OreCost);

        BuildingDef gasDepot = content.BuildingOrNull("GasDepot")!;
        Assert.Equal("gasDepot", gasDepot.Archetype);
        Assert.Equal(new[] { ResourceKind.Gas }, gasDepot.DropoffResourceKinds);

        UnitDef marine = content.UnitOrNull("Marine")!;
        Assert.Equal("infantry", marine.Archetype);
        Assert.True(marine.IsTrainable);
        Assert.Equal(30, marine.BuildTicks);
        Assert.Equal(50, marine.OreCost);
        Assert.Equal(new[] { "Depot" }, marine.ProducerTypes);
        Assert.Equal(new[] { "Academy" }, marine.RequiredBuildingTypes);

        TechDef tech = content.TechOrNull("AdvancedTraining")!;
        Assert.Equal(24, tech.BuildTicks);
        Assert.Equal(75, tech.OreCost);
        Assert.Equal(new[] { "Depot" }, tech.ProducerTypes);
    }

    // Ported from DataRepoTest "returns null for unsupported build or train specs".
    [Fact]
    public void Unknown_ids_and_untrainable_units_are_reported()
    {
        GameContent content = ContentParser.Parse(
            """{"list":[{"id":"Worker","archetype":"worker","hp":40,"radius":0.4,"speedTilesPerSecond":3.5}]}""", Empty, Empty, Empty);
        Assert.Null(content.BuildingOrNull("Worker"));
        Assert.Null(content.TechOrNull("AdvancedTraining"));
        Assert.Null(content.UnitOrNull("Nobody"));
        Assert.False(content.UnitOrNull("Worker")!.IsTrainable);
    }

    [Theory]
    [InlineData(0, 0)]
    [InlineData(1, 1)]
    [InlineData(50, 1)]
    [InlineData(51, 2)]
    [InlineData(160, 4)] // Rounds up from 3.2 ticks.
    [InlineData(240, 5)]
    [InlineData(1000, 20)]
    public void Milliseconds_round_up_to_whole_ticks(int milliseconds, int ticks)
    {
        Assert.Equal(ticks, GameContent.MillisecondsToTicks(milliseconds));
    }

    [Fact]
    public void Real_content_loads_with_converted_values()
    {
        GameContent content = TestContent.Real;
        Assert.Equal(3, content.Units.Count);
        Assert.Equal(3, content.Buildings.Count);
        Assert.Equal(Fix.FromRatio(19, 80), content.UnitOrNull("artisan")!.SpeedPerTick); // 4.75 tiles/s at 20 ticks/s
        Assert.Equal(30, content.UnitOrNull("gunner")!.BuildTicks);
        Assert.Equal(Fix.FromRatio(9, 2), content.WeaponOrNull("matchlock")!.Range);
        Assert.Equal(5, content.WeaponOrNull("matchlock")!.CooldownTicks);
        Assert.Equal(4, content.WeaponOrNull("club")!.CooldownTicks);
        Assert.Equal(Fix.FromRatio(9, 20), content.RadiusOf("brute"));
        Assert.Equal(GameContent.DefaultRadius, content.RadiusOf("not-a-unit"));
    }

    [Fact]
    public void Content_hash_follows_the_values()
    {
        Assert.Equal(Sample().Hash, Sample().Hash);
        GameContent changed = ContentParser.Parse(Units, Weapons.Replace("\"damage\":6", "\"damage\":7"), Buildings, Techs);
        Assert.NotEqual(Sample().Hash, changed.Hash);
    }

    [Fact]
    public void State_cannot_load_against_different_content()
    {
        byte[] state = Simulation.Create(new MatchSetup(8, 8, 1UL), TestContent.Real).SaveState();
        var error = Assert.Throws<InvalidDataException>(() => Simulation.LoadState(state, Sample()));
        Assert.Contains("different game content", error.Message);
    }

    [Theory]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1,"typo":1}]}""", "unknown key 'typo'")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4}]}""", "missing 'speedTilesPerSecond'")]
    [InlineData("""{"list":[{"archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1}]}""", "missing 'id'")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":0,"radius":0.4,"speedTilesPerSecond":1}]}""", "'hp' must be")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1.5,"radius":0.4,"speedTilesPerSecond":1}]}""", "'hp' must be")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":"1","radius":0.4,"speedTilesPerSecond":1}]}""", "'hp' must be")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1e2}]}""", "not usable")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1,"weaponId":"None"}]}""", "unknown weapon 'None'")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1,"producerTypes":["None"]}]}""", "unknown producer 'None'")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1},{"id":"A","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1}]}""", "Duplicate unit id 'A'")]
    [InlineData("""{"list":[{"id":"","archetype":"x","hp":1,"radius":0.4,"speedTilesPerSecond":1}]}""", "A unit has an empty id")]
    [InlineData("""{"list":[{"id":"A","archetype":"x","hp":1,"speedTilesPerSecond":1}]}""", "missing 'radius'")]
    [InlineData("""{"units":[]}""", "'list' array")]
    public void Invalid_units_are_rejected_with_a_clear_message(string units, string expected)
    {
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(units, Empty, Empty, Empty));
        Assert.Contains(expected, error.Message);
    }

    [Fact]
    public void Unknown_resource_kind_is_rejected()
    {
        const string buildings = """{"list":[{"id":"B","archetype":"x","hp":1,"footprintWidth":1,"footprintHeight":1,"dropoffResourceKinds":["minerals"]}]}""";
        var error = Assert.Throws<InvalidDataException>(() => ContentParser.Parse(Empty, Empty, buildings, Empty));
        Assert.Contains("unknown resource kind 'minerals'", error.Message);
    }
}
