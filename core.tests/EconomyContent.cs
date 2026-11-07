using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>
/// Test data for the ported economy tests, in the shape the Kotlin tests used. Kotlin passed build times and costs
/// with each call; the C# core reads them from data, so they live here. 50 ms is one tick.
/// </summary>
internal static class EconomyContent
{
    public const string Worker = """{"id":"Worker","archetype":"worker","hp":40,"radius":0.35,"speedTilesPerSecond":4.0,"buildMs":1500,"producerTypes":["Depot"]}""";
    public const string Marine = """{"id":"Marine","archetype":"infantry","hp":45,"radius":0.4,"speedTilesPerSecond":3.0,"weaponId":"Gauss","oreCost":50,"buildMs":100,"producerTypes":["Depot"]}""";
    public const string Gauss = """{"id":"Gauss","damage":6,"range":4.0,"cooldownMs":300}""";
    public const string Depot = """{"id":"Depot","archetype":"producer","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"supportsTraining":true,"supportsResearch":true,"supportsRally":true,"supportsDropoff":true,"dropoffResourceKinds":["ore"],"productionQueueLimit":3,"rallyOffsetX":4.0,"oreCost":100}""";
    public const string AdvancedTraining = """{"id":"AdvancedTraining","buildMs":150,"oreCost":75,"producerTypes":["Depot"]}""";

    public static GameContent Build(string units, string buildings, string techs = "", string weapons = Gauss) =>
        ContentParser.Parse(List(units), List(weapons), List(buildings), List(techs));

    public static GameContent Standard { get; } = Build(
        Worker + "," + Marine,
        Depot + "," +
        """
        {"id":"ResourceDepot","archetype":"econDepot","hp":350,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsDropoff":true,"dropoffResourceKinds":["ore"],"productionQueueLimit":0,"oreCost":75},
        {"id":"GasDepot","archetype":"gasDepot","hp":325,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsDropoff":true,"dropoffResourceKinds":["gas"],"productionQueueLimit":0,"oreCost":90},
        {"id":"Tower","archetype":"defense","hp":300,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"productionQueueLimit":0,"oreCost":100},
        {"id":"ClearDepot","archetype":"producer","hp":400,"footprintWidth":2,"footprintHeight":2,"placementClearance":1},
        {"id":"Barracks","archetype":"producer","hp":600,"footprintWidth":2,"footprintHeight":2},
        {"id":"Tall","archetype":"producer","hp":400,"footprintWidth":2,"footprintHeight":3},
        {"id":"SlowDepot","archetype":"producer","hp":400,"buildMs":200,"footprintWidth":2,"footprintHeight":2,"supportsTraining":true,"supportsResearch":true,"productionQueueLimit":3,"oreCost":100},
        {"id":"Factory","archetype":"producer","hp":450,"footprintWidth":2,"footprintHeight":2,"oreCost":100,"requiredBuildingTypes":["Depot"]},
        {"id":"Academy","archetype":"tech","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"requiredResearchIds":["AdvancedTraining"]}
        """,
        AdvancedTraining);

    private static string List(string items) => "{\"list\":[" + items + "]}";
}
