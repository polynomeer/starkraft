using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>The fixed command script shared by the hash check and the client benchmark, so both exercise the same match.</summary>
public sealed class BaselineScenario
{
    private const int TicksBetweenOrders = 100;
    private const int SquadsPerPlayer = 4;

    private DeterministicRandom _random;

    public BaselineScenario(ulong seed) => _random = new DeterministicRandom(seed);

    public void AppendCommands(Simulation simulation, List<MoveCommand> commands)
    {
        if (simulation.Tick % TicksBetweenOrders != 0)
        {
            return;
        }

        for (int player = 0; player < 2; player++)
        {
            for (int squad = 0; squad < SquadsPerPlayer; squad++)
            {
                var ids = new List<int>();
                for (int id = 0; id < simulation.UnitCount; id++)
                {
                    if (simulation.UnitOwner[id] == player && (id / 2) % SquadsPerPlayer == squad)
                    {
                        ids.Add(id);
                    }
                }

                int tileX = _random.NextInt(simulation.MapWidth);
                int tileY = _random.NextInt(simulation.MapHeight);
                commands.Add(new MoveCommand(player, ids, tileX, tileY));
            }
        }
    }
}
