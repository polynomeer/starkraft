using System;
using System.Collections.Generic;

namespace Samjae.Core;

public abstract record ScenarioEntity
{
    public sealed record Unit(int Faction, string TypeId, Fix X, Fix Y) : ScenarioEntity;
    public sealed record Building(int Faction, string TypeId, int TileX, int TileY) : ScenarioEntity;
    public sealed record Node(ResourceKind Kind, Fix X, Fix Y, int Amount, int YieldPerTick) : ScenarioEntity;
}

/// <summary>
/// Everything needed to start a match: the map, starting resources and starting entities. This replaces Kotlin's
/// script-only spawn commands, so a replay is its scenario plus the players' commands.
/// </summary>
public sealed record Scenario(
    int MapWidth,
    int MapHeight,
    ulong Seed,
    IReadOnlyList<(int X, int Y)> BlockedTiles,
    IReadOnlyList<(int Faction, int Ore, int Gas)> Stockpiles,
    IReadOnlyList<ScenarioEntity> Entities)
{
    public Simulation Start(GameContent content)
    {
        Simulation simulation = Simulation.Create(new MatchSetup(MapWidth, MapHeight, Seed), content);
        World world = simulation.World;
        foreach ((int x, int y) in BlockedTiles)
        {
            world.Map.SetBlocked(x, y, true);
        }

        foreach ((int faction, int ore, int gas) in Stockpiles)
        {
            simulation.Resources.Set(faction, ore, gas);
        }

        foreach (ScenarioEntity entity in Entities)
        {
            Spawn(simulation, entity);
        }

        return simulation;
    }

    private static void Spawn(Simulation simulation, ScenarioEntity entity)
    {
        World world = simulation.World;
        switch (entity)
        {
            case ScenarioEntity.Unit unit:
                UnitDef def = simulation.Content.UnitOrNull(unit.TypeId)
                    ?? throw new ArgumentException($"Scenario names unknown unit '{unit.TypeId}'.");
                WeaponRef? weapon = def.WeaponId == null ? null : new WeaponRef(def.WeaponId);
                world.Spawn(new Position(unit.X, unit.Y), new UnitTag(unit.Faction, def.Id), new Health(def.Hp, def.Hp, def.Armor), weapon, ProductionSystem.TrainedVisionRange);
                break;
            case ScenarioEntity.Building building:
                simulation.Buildings.PlaceFinished(building.Faction, building.TypeId, building.TileX, building.TileY);
                break;
            case ScenarioEntity.Node node:
                int id = world.Spawn(new Position(node.X, node.Y), new UnitTag(0, node.Kind == ResourceKind.Gas ? "gas" : "ore"), new Health(1, 1));
                world.ResourceNodes.Set(id, new ResourceNode(node.Kind, node.Amount, node.YieldPerTick));
                break;
        }
    }

    /// <summary>
    /// A mirrored two-player map: a hall, four workers and two ore fields per side, rocks scattered in mirror
    /// image from the seed. Faction 1 starts on the left, faction 2 on the right.
    /// </summary>
    public static Scenario Standard(ulong seed)
    {
        const int width = 48;
        const int height = 32;
        var random = new DeterministicRandom(seed);
        var blocked = new List<(int, int)>();
        for (int i = 0; i < 30; i++)
        {
            int x = 14 + random.NextInt(10);
            int y = random.NextInt(height);
            blocked.Add((x, y));
            blocked.Add((width - 1 - x, height - 1 - y));
        }

        var entities = new List<ScenarioEntity>();
        foreach ((int faction, bool left) in new[] { (1, true), (2, false) })
        {
            int Mirror(int x) => left ? x : width - 1 - x;
            entities.Add(new ScenarioEntity.Building(faction, "hall", left ? 4 : width - 6, 14));
            entities.Add(new ScenarioEntity.Node(ResourceKind.Ore, Centre(Mirror(10)), Centre(10), 1500, 5));
            entities.Add(new ScenarioEntity.Node(ResourceKind.Ore, Centre(Mirror(10)), Centre(21), 1500, 5));
            for (int i = 0; i < 4; i++)
            {
                entities.Add(new ScenarioEntity.Unit(faction, "artisan", Centre(Mirror(7)), Centre(13 + i * 2)));
            }
        }

        return new Scenario(width, height, seed, blocked, new[] { (1, 200, 0), (2, 200, 0) }, entities);
    }

    private static Fix Centre(int tile) => Fix.FromInt(tile) + Fix.Half;
}
