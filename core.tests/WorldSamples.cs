using System.Collections.Generic;
using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>A world with every kind of state filled in, so serialisation tests cannot pass by leaving something empty.</summary>
internal static class WorldSamples
{
    public static Simulation Populated()
    {
        Simulation simulation = Simulation.Create(new MatchSetup(16, 12, 99UL), TestContent.Real);
        World world = simulation.World;
        world.Map.SetBlocked(3, 4, true);
        world.Map.SetCost(5, 6, Fix.FromRatio(3, 2));
        world.Occupancy.AddStatic(7, 8);
        world.Occupancy.AddDynamic(9, 2);
        world.PathRequests.Enqueue(1);
        Fill(world);
        return simulation;
    }

    private static void Fill(World world)
    {
        int unit = world.Spawn(At(2, 3), new UnitTag(1, "gunner"), new Health(45, 45), new WeaponRef("matchlock", 2), Fix.FromInt(8));
        world.Orders.Get(unit).AddRange(new Order[]
        {
            new Order.Move(Fix.FromInt(1), Fix.Half),
            new Order.AttackMove(Fix.FromInt(2), Fix.One),
            new Order.Patrol(Fix.One, Fix.Half, Fix.FromInt(3), Fix.FromInt(4), false),
            new Order.Hold(),
            new Order.Construct(2),
            new Order.Attack(3),
        });
        world.PathFollows.Set(unit, new PathFollow(new[] { 11, 12, 13, 0, 0 }, 3, 1, 4, 5));
        world.RepathCooldowns.Set(unit, 7);
        world.AutoAttackTargets.Set(unit, 3);

        int building = world.Spawn(At(6, 6), new UnitTag(1, "hall"), new Health(100, 400, 1));
        world.Footprints.Set(building, new BuildingFootprint(5, 5, 2, 2, 1));
        world.ConstructionSites.Set(building, new ConstructionSite(10, 20, 400, 100, 5));
        world.RallyPoints.Set(building, new RallyPoint(Fix.FromInt(9), Fix.One));
        world.ProductionQueues.Set(building, new List<ProductionJob> { new("gunner", 30, 50, 1) });
        world.ResearchQueues.Set(building, new List<ResearchJob> { new("advanced_training", 24, 75, 2) });

        int worker = world.Spawn(At(1, 1), new UnitTag(2, "artisan"), new Health(40, 40));
        world.BuilderTasks.Set(worker, building);
        var harvester = new Harvester(4) { CargoKind = ResourceKind.Gas, CargoAmount = 3, ReturnTargetId = building };
        world.Harvesters.Set(worker, harvester);

        int node = world.Spawn(At(10, 10), new UnitTag(0, "ore"), new Health(1, 1));
        world.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 500, 2));

        world.Stockpile(1).Ore = 500;
        world.Stockpile(2).Gas = 7;
        world.UnlockTech(1, "advanced_training");
        world.MatchEnded = true;
        world.WinnerFaction = 2;
        world.EndReason = MatchEndReason.Surrender;
    }

    private static Position At(int x, int y) => new(Fix.FromInt(x), Fix.FromInt(y));
}
