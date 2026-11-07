using System.Linq;
using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>Two armies of real content units attack-moving into each other on a map with rocks.</summary>
internal sealed class BattleScenario
{
    public Simulation Simulation { get; }

    public BattleScenario(ulong seed)
    {
        var random = new DeterministicRandom(seed);
        Simulation = Simulation.Create(new MatchSetup(40, 24, seed), TestContent.Real);
        World world = Simulation.World;
        for (int i = 0; i < 40; i++)
        {
            world.Map.SetBlocked(10 + random.NextInt(20), random.NextInt(24), true);
        }

        for (int i = 0; i < 30; i++)
        {
            Spawn(world, faction: 1, x: 2 + i % 5, y: 3 + i / 5 * 3, i);
            Spawn(world, faction: 2, x: 37 - i % 5, y: 3 + i / 5 * 3, i);
        }
    }

    /// <summary>Counts from the world itself, not the alive snapshot, which is empty before the first tick.</summary>
    public bool OneSideRemains() => LivingFactions().Length <= 1;

    public int[] LivingFactions()
    {
        World world = Simulation.World;
        var factions = new System.Collections.Generic.SortedSet<int>();
        foreach (int id in world.Healths)
        {
            if (world.Healths.Get(id).Hp > 0 && world.Tags.TryGet(id, out UnitTag tag))
            {
                factions.Add(tag.Faction);
            }
        }

        return factions.ToArray();
    }

    private static void Spawn(World world, int faction, int x, int y, int index)
    {
        bool melee = index % 3 == 0;
        string type = melee ? "brute" : "gunner";
        int hp = melee ? 35 : 45;
        int id = world.Spawn(
            new Position(Fix.FromInt(x) + Fix.Half, Fix.FromInt(y) + Fix.Half),
            new UnitTag(faction, type),
            new Health(hp, hp),
            new WeaponRef(melee ? "club" : "matchlock"));
        // A shared, occupied goal tile gets no path at all (see the P1 plan), so each army aims past the middle.
        int targetX = faction == 1 ? 30 : 9;
        world.Orders.Get(id).Add(new Order.AttackMove(Fix.FromInt(targetX), Fix.FromInt(y)));
    }
}
