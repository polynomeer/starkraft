using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Ported from ResourceHarvestSystemTest. Workers are moved by hand between ticks, as in Kotlin. The two "harvest
/// command" cases set up the harvester directly; the command itself arrives in step 6.
/// </summary>
public class HarvestTests
{
    private static Simulation New() => Simulation.Create(new MatchSetup(32, 32, 1UL), EconomyContent.Standard);

    private static Position At(string x, string y) => new(Fix.FromText(x), Fix.FromText(y));

    private static int Building(Simulation s, string type, int faction, string x, string y, int tileX, int tileY)
    {
        int id = s.World.Spawn(At(x, y), new UnitTag(faction, type), new Health(400, 400));
        s.World.Footprints.Set(id, new BuildingFootprint(tileX, tileY, 2, 2));
        return id;
    }

    private static int Node(Simulation s, string x, string y, int remaining, ResourceKind kind = ResourceKind.Ore, int yieldPerTick = 0)
    {
        int id = s.World.Spawn(At(x, y), new UnitTag(0, kind == ResourceKind.Gas ? "GasGeyser" : "MineralField"), new Health(1, 1));
        s.World.ResourceNodes.Set(id, new ResourceNode(kind, remaining, yieldPerTick));
        return id;
    }

    private static int Worker(Simulation s, int faction, string x, string y, int node, int perTick)
    {
        int id = s.World.Spawn(At(x, y), new UnitTag(faction, "Worker"), new Health(40, 40));
        s.World.Harvesters.Set(id, new Harvester(node, perTick, Harvester.DefaultRange, int.MaxValue, Harvester.DefaultDropoffRange));
        return id;
    }

    private static void MoveTo(Simulation s, int id, string x, string y)
    {
        s.World.Positions.Get(id).X = Fix.FromText(x);
        s.World.Positions.Get(id).Y = Fix.FromText(y);
    }

    [Fact]
    public void Harvests_a_nearby_node_into_the_faction_stockpile()
    {
        Simulation s = New();
        s.Resources.Set(1, 100);
        int depot = Building(s, "Depot", 1, "6", "4", 5, 3);
        int node = Node(s, "4", "4", 10);
        int worker = Worker(s, 1, "4.5", "4", node, 3);

        s.Harvest.Tick();
        Assert.Equal(100, s.World.Stockpile(1).Ore);
        Assert.Equal(new HarvestEvent(HarvestEventKind.Pickup, 1, worker, node, depot, ResourceKind.Ore, 3), Assert.Single(s.Harvest.Events));

        MoveTo(s, worker, "6", "4");
        s.Harvest.Tick();
        Assert.Equal(103, s.World.Stockpile(1).Ore);
        Assert.Equal(new HarvestEvent(HarvestEventKind.Deposit, 1, worker, node, depot, ResourceKind.Ore, 3), Assert.Single(s.Harvest.Events));
        Assert.Equal(7, s.World.ResourceNodes.Get(node).Remaining);
        Assert.Equal(0, s.Harvest.PickedUp(1, ResourceKind.Ore));
        Assert.Empty(s.Harvest.Nodes);
    }

    [Fact]
    public void Stops_at_depletion_and_ignores_distant_workers()
    {
        Simulation s = New();
        Building(s, "Depot", 1, "10", "8", 9, 7);
        int node = Node(s, "8", "8", 5);
        int near = Worker(s, 1, "8.2", "8", node, 4);
        Worker(s, 1, "20", "20", node, 4);

        s.Harvest.Tick();
        MoveTo(s, near, "10", "8");
        s.Harvest.Tick();
        s.Harvest.Tick();
        Assert.Equal(4, s.World.Stockpile(1).Ore);
        Assert.Equal(1, s.World.ResourceNodes.Get(node).Remaining);
        Assert.Equal(0, s.Harvest.PickedUp(1, ResourceKind.Ore));
    }

    [Fact]
    public void Node_yield_caps_the_worker_harvest_rate()
    {
        Simulation s = New();
        Building(s, "Depot", 1, "6", "4", 5, 3);
        int node = Node(s, "4", "4", 10, yieldPerTick: 2);
        int worker = Worker(s, 1, "4.5", "4", node, 5);
        s.Harvest.Tick();
        Assert.Equal(2, s.Harvest.PickedUp(1, ResourceKind.Ore));
        Assert.Equal(8, s.World.ResourceNodes.Get(node).Remaining);
        Assert.Equal(2, s.World.Harvesters.Get(worker).CargoAmount);
    }

    [Fact]
    public void Worker_heads_back_to_its_node_after_a_deposit()
    {
        Simulation s = New();
        s.Resources.Set(1, 10);
        Building(s, "Depot", 1, "7", "6", 6, 5);
        int node = Node(s, "6", "6", 12, yieldPerTick: 1);
        int worker = Worker(s, 1, "6.4", "6", node, 1);
        s.Harvest.Tick();
        MoveTo(s, worker, "7", "6");
        s.Harvest.Tick();
        Assert.Equal(new Order.Move(Fix.FromInt(6), Fix.FromInt(6)), s.World.Orders.Get(worker).FirstOrDefault());
        Assert.Equal(11, s.World.Stockpile(1).Ore);
        Assert.Equal(11, s.World.ResourceNodes.Get(node).Remaining);
    }

    [Fact]
    public void Gas_goes_into_the_gas_stockpile()
    {
        Simulation s = New();
        s.Resources.Set(2, 0, 4);
        Building(s, "Depot", 2, "8", "6", 7, 5);
        int node = Node(s, "7", "6", 12, ResourceKind.Gas, 1);
        int worker = Worker(s, 2, "7.4", "6", node, 1);
        s.Harvest.Tick();
        MoveTo(s, worker, "8", "6");
        s.Harvest.Tick();
        Assert.Equal(new Order.Move(Fix.FromInt(7), Fix.FromInt(6)), s.World.Orders.Get(worker).FirstOrDefault());
        Assert.Equal(5, s.World.Stockpile(2).Gas);
        Assert.Equal(11, s.World.ResourceNodes.Get(node).Remaining);
    }

    [Fact]
    public void Prefers_a_dropoff_building_over_the_nearest_building()
    {
        Simulation s = New();
        Building(s, "Tower", 1, "5", "4", 4, 3);
        int depot = Building(s, "Depot", 1, "8", "4", 7, 3);
        int node = Node(s, "4", "4", 10);
        int worker = Worker(s, 1, "4.5", "4", node, 2);
        s.Harvest.Tick();
        Assert.Equal(depot, s.World.Harvesters.Get(worker).ReturnTargetId);
        Assert.Equal(new Order.Move(Fix.FromInt(8), Fix.FromInt(4)), s.World.Orders.Get(worker).FirstOrDefault());
        Assert.Equal(depot, Assert.Single(s.Harvest.Events).Dropoff);
    }

    [Fact]
    public void Uses_the_nearer_resource_depot()
    {
        Simulation s = New();
        Building(s, "Depot", 1, "9", "4", 8, 3);
        int resourceDepot = Building(s, "ResourceDepot", 1, "6", "4", 5, 3);
        int node = Node(s, "4", "4", 10);
        int worker = Worker(s, 1, "4.5", "4", node, 2);
        s.Harvest.Tick();
        Assert.Equal(resourceDepot, s.World.Harvesters.Get(worker).ReturnTargetId);
    }

    [Fact]
    public void Gas_cargo_prefers_a_gas_dropoff()
    {
        Simulation s = New();
        Building(s, "Depot", 2, "6", "4", 5, 3);
        int gasDepot = Building(s, "GasDepot", 2, "9", "4", 8, 3);
        int node = Node(s, "4", "4", 10, ResourceKind.Gas);
        int worker = Worker(s, 2, "4.5", "4", node, 2);
        s.Harvest.Tick();
        Assert.Equal(gasDepot, s.World.Harvesters.Get(worker).ReturnTargetId);
        Assert.Equal(new Order.Move(Fix.FromInt(9), Fix.FromInt(4)), s.World.Orders.Get(worker).FirstOrDefault());
    }

    [Fact]
    public void Several_workers_on_one_node_are_summed_into_one_entry()
    {
        Simulation s = New();
        int node = Node(s, "5", "5", 10);
        Worker(s, 1, "5.5", "5", node, 2);
        Worker(s, 1, "5.2", "5", node, 3);
        s.Harvest.Tick();
        Assert.Equal(new NodeHarvest(node, 5, 5, false), Assert.Single(s.Harvest.Nodes));
    }

    [Fact]
    public void Totals_are_tracked_by_faction()
    {
        Simulation s = New();
        int ore = Node(s, "5", "5", 10);
        int gas = Node(s, "7", "5", 10, ResourceKind.Gas);
        Worker(s, 1, "5.3", "5", ore, 2);
        Worker(s, 2, "7.2", "5", gas, 3);
        s.Harvest.Tick();
        Assert.Equal(2, s.Harvest.PickedUp(1, ResourceKind.Ore));
        Assert.Equal(0, s.Harvest.PickedUp(2, ResourceKind.Ore));
        Assert.Equal(0, s.Harvest.PickedUp(1, ResourceKind.Gas));
        Assert.Equal(3, s.Harvest.PickedUp(2, ResourceKind.Gas));
    }
}
