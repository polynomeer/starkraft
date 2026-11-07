using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>Whole loops through the full tick, with real movement, which the ported tests did by moving units by hand.</summary>
public class EconomyFlowTests
{
    private static Simulation New() => Simulation.Create(new MatchSetup(24, 24, 1UL), TestContent.Real);

    private static Fix F(int value) => Fix.FromInt(value);

    private static void Run(Simulation s, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            s.Step();
        }
    }

    private static int Node(Simulation s, int x, int y, int remaining)
    {
        int id = s.World.Spawn(new Position(F(x) + Fix.Half, F(y) + Fix.Half), new UnitTag(0, "ore"), new Health(1, 1));
        s.World.ResourceNodes.Set(id, new ResourceNode(ResourceKind.Ore, remaining, 5));
        return id;
    }

    [Fact]
    public void Worker_walks_back_and_forth_and_fills_the_stockpile()
    {
        Simulation s = New();
        s.Resources.Set(1, 100);
        int hall = s.Buildings.Place(1, "hall", 4, 4).EntityId;
        int node = Node(s, 14, 5, 100);
        int worker = s.World.Spawn(new Position(F(6) + Fix.Half, F(5) + Fix.Half), new UnitTag(1, "artisan"), new Health(40, 40));
        s.World.Harvesters.Set(worker, new Harvester(node, 5, Harvester.DefaultRange, int.MaxValue, Harvester.DefaultDropoffRange));

        int deposits = 0;
        for (int tick = 0; tick < 400; tick++)
        {
            s.Step();
            deposits += s.Harvest.Events.Count(e => e.Kind == HarvestEventKind.Deposit && e.Dropoff == hall);
        }

        Assert.True(deposits >= 5, $"only {deposits} round trips");
        Assert.Equal(deposits * 5, s.World.Stockpile(1).Ore);
        Assert.Equal(100 - deposits * 5 - s.World.Harvesters.Get(worker).CargoAmount, s.World.ResourceNodes.Get(node).Remaining);
    }

    [Fact]
    public void Worker_builds_a_site_after_walking_to_it()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int worker = s.World.Spawn(new Position(F(2) + Fix.Half, F(2) + Fix.Half), new UnitTag(1, "artisan"), new Health(40, 40));
        // Real buildings take no build time yet, so this uses a site placed by hand.
        int site = s.World.Spawn(new Position(F(13), F(13)), new UnitTag(1, "hall"), new Health(1, 400));
        s.World.Footprints.Set(site, new BuildingFootprint(12, 12, 2, 2));
        for (int y = 12; y < 14; y++)
        {
            for (int x = 12; x < 14; x++)
            {
                s.World.Occupancy.AddStatic(x, y);
            }
        }

        s.World.ConstructionSites.Set(site, new ConstructionSite(40, 40, 400));
        s.World.BuilderTasks.Set(worker, site);
        s.World.Orders.Get(worker).Add(new Order.Construct(site));
        Run(s, 200);
        Assert.False(s.World.ConstructionSites.Has(site));
        Assert.Equal(400, s.World.Healths.Get(site).Hp);
    }

    [Fact]
    public void Trained_unit_walks_to_the_rally_point()
    {
        Simulation s = New();
        s.Resources.Set(1, 500);
        int hall = s.Buildings.Place(1, "hall", 4, 4).EntityId;
        Assert.Null(s.Production.Enqueue(hall, "gunner"));
        Run(s, 200);
        int gunner = Enumerable.Range(1, s.World.NextEntityId - 1).Single(id => s.World.Tags.TryGet(id, out UnitTag tag) && tag.TypeId == "gunner");
        // Hall centre (5,5) plus the rally offset (4,0).
        Assert.Equal((9, 5), (s.World.Positions.Get(gunner).X.FloorToInt(), s.World.Positions.Get(gunner).Y.FloorToInt()));
        Assert.Empty(s.World.Orders.Get(gunner));
    }

    [Fact]
    public void Group_sent_to_one_tile_spreads_around_it_and_finishes()
    {
        Simulation s = New();
        int[] ids = Enumerable.Range(0, 20)
            .Select(i => s.World.Spawn(new Position(F(2 + i % 5) + Fix.Half, F(2 + i / 5) + Fix.Half), new UnitTag(1, "gunner"), new Health(45, 45)))
            .ToArray();
        foreach (int id in ids)
        {
            s.World.Orders.Get(id).Add(new Order.Move(F(16), F(16)));
        }

        Run(s, 400);
        Assert.All(ids, id => Assert.Empty(s.World.Orders.Get(id)));
        int farthest = ids.Max(id => System.Math.Max(
            System.Math.Abs(s.World.Positions.Get(id).X.FloorToInt() - 16), System.Math.Abs(s.World.Positions.Get(id).Y.FloorToInt() - 16)));
        // 20 units would fit within 2 tiles; the crowd leaves gaps it cannot walk into, so some stop further out.
        Assert.True(farthest <= 5, $"a unit stopped {farthest} tiles away");
    }

    [Fact]
    public void Economy_saved_midway_continues_identically()
    {
        Simulation s = New();
        s.Resources.Set(1, 1000);
        int hall = s.Buildings.Place(1, "hall", 4, 4).EntityId;
        int node = Node(s, 14, 5, 500);
        for (int i = 0; i < 3; i++)
        {
            Assert.Null(s.Production.Enqueue(hall, "artisan"));
        }

        Assert.Null(s.Research.Enqueue(hall, "advanced_training"));
        int worker = s.World.Spawn(new Position(F(6) + Fix.Half, F(5) + Fix.Half), new UnitTag(1, "artisan"), new Health(40, 40));
        s.World.Harvesters.Set(worker, new Harvester(node, 5, Harvester.DefaultRange, int.MaxValue, Harvester.DefaultDropoffRange));
        Run(s, 30);

        Simulation restored = Simulation.LoadState(s.SaveState(), TestContent.Real);
        for (int tick = 0; tick < 200; tick++)
        {
            s.Step();
            restored.Step();
            Assert.Equal(s.ComputeHash(), restored.ComputeHash());
        }

        Assert.True(s.World.HasTech(1, "advanced_training"));
    }
}
