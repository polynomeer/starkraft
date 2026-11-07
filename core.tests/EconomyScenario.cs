using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>Two bases that harvest, train and research on their own, for the cross-OS hash.</summary>
internal sealed class EconomyScenario
{
    public Simulation Simulation { get; }

    public EconomyScenario()
    {
        Simulation = Simulation.Create(new MatchSetup(32, 32, 77UL), TestContent.Real);
        SetUpBase(faction: 1, hallX: 4, nodeX: 14);
        SetUpBase(faction: 2, hallX: 26, nodeX: 17);
    }

    private void SetUpBase(int faction, int hallX, int nodeX)
    {
        World world = Simulation.World;
        Simulation.Resources.Set(faction, 1000);
        int hall = Simulation.Buildings.Place(faction, "hall", hallX, 14).EntityId;
        for (int i = 0; i < 3; i++)
        {
            Simulation.Production.Enqueue(hall, i == 1 ? "gunner" : "artisan");
        }

        Simulation.Research.Enqueue(hall, "advanced_training");
        int node = world.Spawn(new Position(Fix.FromInt(nodeX) + Fix.Half, Fix.FromInt(15) + Fix.Half), new UnitTag(0, "ore"), new Health(1, 1));
        world.ResourceNodes.Set(node, new ResourceNode(ResourceKind.Ore, 400, 5));
        for (int i = 0; i < 3; i++)
        {
            int x = faction == 1 ? hallX + 3 + i : hallX - 2 - i;
            int worker = world.Spawn(new Position(Fix.FromInt(x) + Fix.Half, Fix.FromInt(16) + Fix.Half), new UnitTag(faction, "artisan"), new Health(40, 40));
            world.Harvesters.Set(worker, new Harvester(node, 5, Harvester.DefaultRange, int.MaxValue, Harvester.DefaultDropoffRange));
        }
    }
}
