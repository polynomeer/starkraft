using System.Collections.Generic;
using Samjae.Core;

namespace Samjae.Bots;

/// <summary>Reads the simulation and returns the commands one player issues this tick. Must be deterministic.</summary>
public interface IBot
{
    int Player { get; }
    IReadOnlyList<PlayerCommand> Decide(Simulation simulation);
}

/// <summary>How a <see cref="SimpleBot"/> plays. Different settings make mirror matches end decisively.</summary>
public sealed record BotStyle(int TargetWorkers, int AttackWave, string[] ArmyMix, int DecisionInterval = 10)
{
    public static readonly BotStyle Gunners = new(6, 6, new[] { "gunner" });
    public static readonly BotStyle Mixed = new(8, 8, new[] { "gunner", "gunner", "brute" });
}

/// <summary>
/// Gathers with idle workers, trains workers then army, and sends idle army at the nearest enemy building once a
/// wave is ready. Exists to drive whole matches for testing, not to play well.
/// </summary>
public sealed class SimpleBot : IBot
{
    private readonly BotStyle _style;
    private int _trained;

    public SimpleBot(int player, BotStyle style)
    {
        Player = player;
        _style = style;
    }

    public int Player { get; }

    public IReadOnlyList<PlayerCommand> Decide(Simulation simulation)
    {
        var commands = new List<PlayerCommand>();
        if (simulation.Tick % _style.DecisionInterval != 0 || simulation.World.MatchEnded)
        {
            return commands;
        }

        var view = new BotView(simulation, Player);
        SendIdleWorkersToHarvest(view, commands);
        Train(view, commands);
        Attack(view, commands);
        return commands;
    }

    private void SendIdleWorkersToHarvest(BotView view, List<PlayerCommand> commands)
    {
        foreach (int worker in view.Workers)
        {
            if (!view.World.Harvesters.Has(worker) && view.World.Orders.Get(worker).Count == 0 && view.NearestNode(worker) is int node)
            {
                commands.Add(new PlayerCommand.Harvest(Player, new[] { worker }, node));
            }
        }
    }

    private void Train(BotView view, List<PlayerCommand> commands)
    {
        foreach (int hall in view.Halls)
        {
            if (view.World.ProductionQueues.TryGet(hall, out var queue) && queue.Count >= 2)
            {
                continue;
            }

            string type = view.Workers.Count < _style.TargetWorkers ? "artisan" : _style.ArmyMix[_trained % _style.ArmyMix.Length];
            UnitDef def = view.Simulation.Content.UnitOrNull(type)!;
            if (view.World.Stockpile(Player).Ore >= def.OreCost)
            {
                commands.Add(new PlayerCommand.Train(Player, hall, type));
                _trained += type == "artisan" ? 0 : 1;
            }
        }
    }

    private void Attack(BotView view, List<PlayerCommand> commands)
    {
        var idle = new List<int>();
        foreach (int unit in view.Army)
        {
            if (view.World.Orders.Get(unit).Count == 0)
            {
                idle.Add(unit);
            }
        }

        if (idle.Count < _style.AttackWave && !(idle.Count > 0 && view.Army.Count >= _style.AttackWave))
        {
            return;
        }

        if (view.EnemyTarget() is Position target)
        {
            commands.Add(new PlayerCommand.AttackMove(Player, idle, target.X, target.Y));
        }
    }
}

/// <summary>What a bot looks at, gathered once per decision.</summary>
internal sealed class BotView
{
    public Simulation Simulation { get; }
    public World World { get; }
    public List<int> Workers { get; } = new();
    public List<int> Army { get; } = new();
    public List<int> Halls { get; } = new();

    private readonly int _player;

    public BotView(Simulation simulation, int player)
    {
        Simulation = simulation;
        World = simulation.World;
        _player = player;
        foreach (int id in World.Tags)
        {
            if (World.Tags.Get(id).Faction != player || !World.IsAttackable(id))
            {
                continue;
            }

            string type = World.Tags.Get(id).TypeId;
            if (World.Footprints.Has(id))
            {
                if (type == "hall" && !World.ConstructionSites.Has(id))
                {
                    Halls.Add(id);
                }
            }
            else if (simulation.Content.UnitOrNull(type)?.Archetype == ConstructionSystem.WorkerArchetype)
            {
                Workers.Add(id);
            }
            else if (World.Weapons.Has(id))
            {
                Army.Add(id);
            }
        }
    }

    public int? NearestNode(int worker)
    {
        Position from = World.Positions.Get(worker);
        int? best = null;
        long bestDistance = long.MaxValue;
        foreach (int id in World.ResourceNodes)
        {
            if (World.ResourceNodes.Get(id).Kind != ResourceKind.Ore || World.ResourceNodes.Get(id).Remaining <= 0)
            {
                continue;
            }

            Position at = World.Positions.Get(id);
            long distance = Fix.LengthSquaredRaw(at.X - from.X, at.Y - from.Y);
            if (distance < bestDistance)
            {
                (best, bestDistance) = (id, distance);
            }
        }

        return best;
    }

    /// <summary>An enemy building if any is left, else any enemy. Lowest id first.</summary>
    public Position? EnemyTarget()
    {
        Position? fallback = null;
        foreach (int id in World.Tags)
        {
            if (!World.IsEnemyOf(id, _player) || World.Tags.Get(id).Faction <= 0)
            {
                continue;
            }

            if (World.Footprints.Has(id))
            {
                return World.Positions.Get(id);
            }

            fallback ??= World.Positions.Get(id);
        }

        return fallback;
    }
}

/// <summary>Plays bots against each other through the command entry point and records the replay.</summary>
public static class BotMatch
{
    public static (Replay Replay, Simulation Simulation) Play(Scenario scenario, GameContent content, IBot[] bots, int maxTicks)
    {
        Simulation simulation = scenario.Start(content);
        var recorder = new ReplayRecorder(scenario, simulation);
        while (!simulation.World.MatchEnded && simulation.Tick < maxTicks)
        {
            var commands = new List<PlayerCommand>();
            foreach (IBot bot in bots)
            {
                commands.AddRange(bot.Decide(simulation));
            }

            recorder.Step(commands);
        }

        return (recorder.Finish(), simulation);
    }
}
