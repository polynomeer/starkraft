using System.Collections.Generic;

namespace Samjae.Core;

public readonly record struct ReplayTick(int Tick, IReadOnlyList<PlayerCommand> Commands);

public readonly record struct ReplayCheckpoint(int Tick, ulong Hash);

/// <summary>
/// A match as data: the scenario, the content it ran with, every tick's commands, and state hashes along the way.
/// Replaying it must give the same hashes on any machine; a difference means a rule or platform changed the result.
/// </summary>
public sealed record Replay(
    ulong ContentHash,
    Scenario Scenario,
    IReadOnlyList<ReplayTick> Ticks,
    IReadOnlyList<ReplayCheckpoint> Checkpoints,
    int EndTick,
    int? Winner,
    MatchEndReason? EndReason)
{
    public const int CheckpointInterval = 200;
}

/// <summary>Records commands and checkpoints while a match runs.</summary>
public sealed class ReplayRecorder
{
    private readonly Scenario _scenario;
    private readonly Simulation _simulation;
    private readonly List<ReplayTick> _ticks = new();
    private readonly List<ReplayCheckpoint> _checkpoints = new();

    public ReplayRecorder(Scenario scenario, Simulation simulation)
    {
        _scenario = scenario;
        _simulation = simulation;
        _checkpoints.Add(new ReplayCheckpoint(0, simulation.ComputeHash()));
    }

    public IReadOnlyList<CommandResult> Step(IReadOnlyList<PlayerCommand> commands)
    {
        if (commands.Count > 0)
        {
            _ticks.Add(new ReplayTick(_simulation.Tick, commands));
        }

        PlayerCommand[] batch = new PlayerCommand[commands.Count];
        for (int i = 0; i < batch.Length; i++)
        {
            batch[i] = commands[i];
        }

        IReadOnlyList<CommandResult> results = _simulation.Step(batch);
        if (_simulation.Tick % Replay.CheckpointInterval == 0)
        {
            _checkpoints.Add(new ReplayCheckpoint(_simulation.Tick, _simulation.ComputeHash()));
        }

        return results;
    }

    public Replay Finish()
    {
        var checkpoints = new List<ReplayCheckpoint>(_checkpoints);
        if (checkpoints[^1].Tick != _simulation.Tick)
        {
            checkpoints.Add(new ReplayCheckpoint(_simulation.Tick, _simulation.ComputeHash()));
        }

        World world = _simulation.World;
        return new Replay(_simulation.Content.Hash, _scenario, _ticks, checkpoints, _simulation.Tick, world.WinnerFaction, world.EndReason);
    }
}

public readonly record struct ReplayMismatch(int Tick, ulong Expected, ulong Actual);

public static class ReplayPlayer
{
    /// <summary>Runs the replay and returns the first checkpoint that differs, or null if every one matches.</summary>
    public static ReplayMismatch? Verify(Replay replay, GameContent content)
    {
        if (replay.ContentHash != content.Hash)
        {
            throw new System.IO.InvalidDataException("Replay was recorded with different game content.");
        }

        Simulation simulation = replay.Scenario.Start(content);
        var commandsByTick = new Dictionary<int, IReadOnlyList<PlayerCommand>>();
        foreach (ReplayTick tick in replay.Ticks)
        {
            commandsByTick[tick.Tick] = tick.Commands;
        }

        int next = 0;
        while (true)
        {
            while (next < replay.Checkpoints.Count && replay.Checkpoints[next].Tick == simulation.Tick)
            {
                ulong actual = simulation.ComputeHash();
                if (actual != replay.Checkpoints[next].Hash)
                {
                    return new ReplayMismatch(simulation.Tick, replay.Checkpoints[next].Hash, actual);
                }

                next++;
            }

            if (simulation.Tick >= replay.EndTick)
            {
                return null;
            }

            PlayerCommand[] batch = commandsByTick.TryGetValue(simulation.Tick, out var commands) ? ToArray(commands) : System.Array.Empty<PlayerCommand>();
            simulation.Step(batch);
        }
    }

    private static PlayerCommand[] ToArray(IReadOnlyList<PlayerCommand> commands)
    {
        var array = new PlayerCommand[commands.Count];
        for (int i = 0; i < array.Length; i++)
        {
            array[i] = commands[i];
        }

        return array;
    }
}
