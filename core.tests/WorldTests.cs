using System;
using System.Collections.Generic;
using System.Linq;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class WorldTests
{
    private static World NewWorld() => new(16, 12);

    private static Position Origin() => new(Fix.Zero, Fix.Zero);

    [Fact]
    public void Spawn_assigns_ascending_ids_from_one_and_the_base_components()
    {
        World world = NewWorld();
        int first = world.Spawn(Origin(), new UnitTag(1, "gunner"), new Health(10, 10));
        int second = world.Spawn(Origin(), new UnitTag(2, "brute"), new Health(10, 10), new WeaponRef("club"), Fix.FromInt(5));
        Assert.Equal(new[] { 1, 2 }, new[] { first, second });
        Assert.True(world.Orders.Has(first) && world.RepathCooldowns.Has(first) && world.Stucks.Has(first));
        Assert.False(world.Weapons.Has(first));
        Assert.False(world.VisionRanges.Has(first));
        Assert.True(world.Weapons.Has(second));
        Assert.Equal(Fix.FromInt(5), world.VisionRanges.Get(second));
    }

    [Fact]
    public void Remove_clears_every_component_and_records_the_event()
    {
        Simulation simulation = WorldSamples.Populated();
        World world = simulation.World;
        world.Remove(1, "killed");
        Assert.False(world.Positions.Has(1) || world.Tags.Has(1) || world.Healths.Has(1) || world.Weapons.Has(1)
            || world.Orders.Has(1) || world.VisionRanges.Has(1) || world.PathFollows.Has(1)
            || world.RepathCooldowns.Has(1) || world.Stucks.Has(1) || world.AutoAttackTargets.Has(1));
        Assert.Equal(new RemovalEvent(1, 1, "gunner", "killed"), world.RemovedEvents[^1]);
    }

    [Fact]
    public void Remove_clears_references_held_by_other_entities()
    {
        World world = NewWorld();
        int attacker = world.Spawn(Origin(), new UnitTag(1, "gunner"), new Health(10, 10));
        int builder = world.Spawn(Origin(), new UnitTag(1, "artisan"), new Health(10, 10));
        int target = world.Spawn(Origin(), new UnitTag(2, "hall"), new Health(10, 10));
        world.AutoAttackTargets.Set(attacker, target);
        world.BuilderTasks.Set(builder, target);

        world.Remove(target);

        Assert.False(world.AutoAttackTargets.Has(attacker));
        Assert.False(world.BuilderTasks.Has(builder));
    }

    [Fact]
    public void Remove_of_an_unknown_id_records_an_event_without_a_type()
    {
        World world = NewWorld();
        world.Remove(42);
        Assert.Equal(new RemovalEvent(42, 0, null, "despawn"), world.RemovedEvents[0]);
    }

    [Fact]
    public void Step_clears_removal_events()
    {
        Simulation simulation = Simulation.Create(new MatchSetup(8, 8, 1UL), TestContent.Real);
        simulation.World.Remove(5);
        simulation.Step();
        Assert.Empty(simulation.World.RemovedEvents);
    }

    [Fact]
    public void Store_iterates_ascending_regardless_of_insertion_order()
    {
        var store = new EntityStore<int>();
        foreach (int id in new[] { 200, 3, 70, 1 })
        {
            store.Set(id, id);
        }

        store.Remove(70);
        store.Set(70, 70);
        Assert.Equal(new[] { 1, 3, 70, 200 }, Ids(store));
        Assert.Equal(4, store.Count);
    }

    [Fact]
    public void Store_allows_removal_while_iterating()
    {
        var store = new EntityStore<int>();
        foreach (int id in new[] { 1, 2, 3, 4 })
        {
            store.Set(id, id);
        }

        foreach (int id in store)
        {
            store.Remove(id == 1 ? 3 : id);
        }

        Assert.Equal(new[] { 1 }, Ids(store));
    }

    [Fact]
    public void Store_get_on_a_missing_id_throws()
    {
        var store = new EntityStore<int>();
        Assert.Throws<InvalidOperationException>(() => store.Get(1));
        Assert.False(store.Has(-1));
        Assert.False(store.Remove(5));
    }

    [Fact]
    public void Unlock_tech_reports_only_the_first_unlock()
    {
        World world = NewWorld();
        Assert.True(world.UnlockTech(1, "a"));
        Assert.False(world.UnlockTech(1, "a"));
        Assert.True(world.HasTech(1, "a"));
        Assert.False(world.HasTech(2, "a"));
    }

    [Fact]
    public void Map_treats_out_of_bounds_as_neither_passable_nor_blocked()
    {
        var map = new MapGrid(4, 4);
        Assert.True(map.IsPassable(1, 1));
        map.SetBlocked(1, 1, true);
        Assert.True(map.IsBlocked(1, 1));
        Assert.False(map.IsPassable(1, 1));
        Assert.False(map.IsPassable(-1, 0));
        Assert.False(map.IsBlocked(-1, 0));
        map.SetBlocked(9, 9, true);
        Assert.Equal(Fix.One, map.Cost(0, 0));
        Assert.Equal(Fix.MaxValue, map.Cost(4, 0));
        map.SetCost(0, 0, Fix.Half);
        Assert.Equal(Fix.Half, map.Cost(0, 0));
    }

    [Fact]
    public void Occupancy_counts_static_and_dynamic_separately()
    {
        var grid = new OccupancyGrid(4, 4);
        Assert.True(grid.IsBlocked(-1, 0));
        Assert.True(grid.IsStaticBlocked(4, 0));
        grid.AddStatic(1, 1);
        grid.AddStatic(1, 1);
        grid.RemoveStatic(1, 1);
        Assert.True(grid.IsStaticBlocked(1, 1));
        grid.RemoveStatic(1, 1);
        grid.RemoveStatic(1, 1);
        Assert.False(grid.IsStaticBlocked(1, 1));

        grid.AddDynamic(2, 2);
        Assert.True(grid.IsBlocked(2, 2));
        Assert.False(grid.IsStaticBlocked(2, 2));
        Assert.False(grid.IsBlockedAllowing(2, 2, 2, 2));
        Assert.True(grid.IsBlockedAllowing(2, 2, 0, 0));
        grid.ClearDynamic();
        Assert.False(grid.IsBlocked(2, 2));
    }

    [Fact]
    public void Populated_world_survives_a_save_and_load()
    {
        Simulation original = WorldSamples.Populated();
        Simulation restored = Simulation.LoadState(original.SaveState(), TestContent.Real);
        Assert.Equal(original.SaveState(), restored.SaveState());
        World world = restored.World;
        Assert.Equal(new Order.Patrol(Fix.One, Fix.Half, Fix.FromInt(3), Fix.FromInt(4), false), world.Orders.Get(1)[2]);
        Assert.Equal(ResourceKind.Gas, world.Harvesters.Get(3).CargoKind);
        Assert.Equal(MatchEndReason.Surrender, world.EndReason);
        Assert.True(world.HasTech(1, "advanced_training"));
        Assert.Equal(3, world.PathFollows.Get(1).Length);
        Assert.Equal(5, world.NextEntityId);
    }

    [Fact]
    public void Spare_path_capacity_does_not_change_the_hash()
    {
        Simulation a = WorldSamples.Populated();
        Simulation b = WorldSamples.Populated();
        b.World.PathFollows.Set(1, new PathFollow(new[] { 11, 12, 13, 99, 99, 99, 99 }, 3, 1, 4, 5));
        Assert.Equal(a.ComputeHash(), b.ComputeHash());
    }

    public static IEnumerable<object[]> Mutations() => new (string Name, Action<World> Change)[]
    {
        ("position", w => w.Positions.Get(1).X = Fix.FromRaw(w.Positions.Get(1).X.Raw + 1)),
        ("hp", w => w.Healths.Get(1).Hp--),
        ("armor", w => w.Healths.Get(2).Armor++),
        ("cooldown", w => w.Weapons.Get(1).CooldownTicks++),
        ("order removed", w => w.Orders.Get(1).RemoveAt(0)),
        ("order order", w => w.Orders.Get(1).Reverse()),
        ("vision", w => w.VisionRanges.Set(1, Fix.One)),
        ("path index", w => w.PathFollows.Get(1).Index++),
        ("path node", w => w.PathFollows.Get(1).Nodes[0]++),
        ("repath", w => w.RepathCooldowns.Set(1, 8)),
        ("footprint", w => w.Footprints.Set(2, new BuildingFootprint(6, 5, 2, 2, 1))),
        ("site", w => w.ConstructionSites.Get(2).RemainingTicks--),
        ("builder", w => w.BuilderTasks.Set(3, 1)),
        ("rally", w => w.RallyPoints.Set(2, new RallyPoint(Fix.One, Fix.One))),
        ("production", w => w.ProductionQueues.Get(2)[0].RemainingTicks--),
        ("research", w => w.ResearchQueues.Get(2)[0].RemainingTicks--),
        ("stuck", w => w.Stucks.Get(1).Ticks++),
        ("node", w => w.ResourceNodes.Get(4).Remaining--),
        ("cargo", w => w.Harvesters.Get(3).CargoAmount++),
        ("cargo kind", w => w.Harvesters.Get(3).CargoKind = null),
        ("auto target", w => w.AutoAttackTargets.Remove(1)),
        ("stockpile", w => w.Stockpile(1).Ore--),
        ("tech", w => w.UnlockTech(2, "advanced_training")),
        ("winner", w => w.WinnerFaction = 1),
        ("end reason", w => w.EndReason = MatchEndReason.Draw),
        ("ended", w => w.MatchEnded = false),
        ("map block", w => w.Map.SetBlocked(0, 0, true)),
        ("map cost", w => w.Map.SetCost(0, 0, Fix.Half)),
        ("static occupancy", w => w.Occupancy.AddStatic(0, 0)),
        ("dynamic occupancy", w => w.Occupancy.AddDynamic(0, 0)),
        ("path request", w => w.PathRequests.Enqueue(2)),
        ("next id", w => w.Spawn(Origin(), new UnitTag(1, "gunner"), new Health(1, 1))),
    }.Select(m => new object[] { m.Name, m.Change });

    [Theory]
    [MemberData(nameof(Mutations), DisableDiscoveryEnumeration = true)]
    public void Every_kind_of_state_changes_the_hash(string name, Action<World> change)
    {
        Simulation simulation = WorldSamples.Populated();
        ulong before = simulation.ComputeHash();
        change(simulation.World);
        Assert.True(before != simulation.ComputeHash(), name);

        Simulation restored = Simulation.LoadState(simulation.SaveState(), TestContent.Real);
        Assert.Equal(simulation.ComputeHash(), restored.ComputeHash());
    }

    private static List<int> Ids<T>(EntityStore<T> store)
    {
        var ids = new List<int>();
        foreach (int id in store)
        {
            ids.Add(id);
        }

        return ids;
    }
}
