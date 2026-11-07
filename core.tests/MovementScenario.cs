using System.Collections.Generic;
using Samjae.Core;

namespace Samjae.Core.Tests;

/// <summary>Many units on a map with scattered rocks, given new destinations regularly. Shared by the stress test and the cross-OS hash.</summary>
internal sealed class MovementScenario
{
    public const int UnitCount = 120;
    private const int OrderIntervalTicks = 48;

    private DeterministicRandom _random;

    public Simulation Simulation { get; }
    public List<int> Ids { get; } = new();

    public MovementScenario(ulong seed)
    {
        _random = new DeterministicRandom(seed);
        Simulation = Simulation.Create(new MatchSetup(32, 32, seed), TestContent.Real);
        World world = Simulation.World;
        for (int i = 0; i < 80; i++)
        {
            world.Map.SetBlocked(_random.NextInt(32), _random.NextInt(32), true);
        }

        for (int i = 0; i < UnitCount; i++)
        {
            Ids.Add(world.Spawn(RandomFreePosition(world), new UnitTag(1, "Test"), new Health(10, 10)));
            AddOrder(Ids[i]);
        }
    }

    public void StepOnce()
    {
        if (Simulation.Tick > 0 && Simulation.Tick % OrderIntervalTicks == 0)
        {
            foreach (int id in Ids)
            {
                AddOrder(id);
            }
        }

        Simulation.Step();
    }

    private Position RandomFreePosition(World world)
    {
        while (true)
        {
            var position = new Position(RandomCoordinate(), RandomCoordinate());
            if (world.Map.IsPassable(position.X.FloorToInt(), position.Y.FloorToInt()))
            {
                return position;
            }
        }
    }

    private void AddOrder(int id) =>
        Simulation.World.Orders.Get(id).Add(new Order.Move(RandomCoordinate(), RandomCoordinate()));

    private Fix RandomCoordinate() => Fix.FromInt(1 + _random.NextInt(30)) + Fix.FromRaw(_random.NextInt(Fix.OneRaw));
}
