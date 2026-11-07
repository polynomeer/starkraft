using System;
using System.IO;
using System.Linq;
using Samjae.Bots;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Bot matches played to the end, and replays that must reproduce them. Replaces Kotlin's ReplayIOTest,
/// ReplayHashTest and ReplayRunnerTest, which tested the old command-log format.
/// </summary>
public class ReplayTests
{
    public const int MaxTicks = 12_000;
    private const string WriteVariable = "SAMJAE_WRITE_REPLAYS";

    public static (Replay Replay, Simulation Simulation) PlayStandard(ulong seed) => BotMatch.Play(
        Scenario.Standard(seed), TestContent.Real,
        new IBot[] { new SimpleBot(1, BotStyle.Gunners), new SimpleBot(2, BotStyle.Mixed) }, MaxTicks);

    [Theory]
    [InlineData(1UL)]
    [InlineData(2UL)]
    public void Bot_match_is_played_to_a_winner(ulong seed)
    {
        (Replay replay, Simulation simulation) = PlayStandard(seed);
        Assert.True(simulation.World.MatchEnded, $"no result after {simulation.Tick} ticks");
        Assert.Equal(MatchEndReason.Elimination, replay.EndReason);
        Assert.NotNull(replay.Winner);
    }

    [Fact]
    public void Replay_survives_a_round_trip_through_json_and_reproduces_the_match()
    {
        (Replay replay, _) = PlayStandard(1UL);
        string json = ReplayCodec.Write(replay);
        Replay read = ReplayCodec.Read(json);
        Assert.Equal(json, ReplayCodec.Write(read));
        Assert.Null(ReplayPlayer.Verify(read, TestContent.Real));
    }

    [Fact]
    public void A_changed_command_is_caught_at_the_next_checkpoint()
    {
        (Replay replay, _) = PlayStandard(1UL);
        int index = replay.Ticks.ToList().FindIndex(t => t.Commands.Any(c => c is PlayerCommand.AttackMove));
        ReplayTick tick = replay.Ticks[index];
        var changed = tick.Commands.Select(c => c is PlayerCommand.AttackMove a ? a with { X = a.X - Fix.One } : c).ToList();
        var ticks = replay.Ticks.ToList();
        ticks[index] = tick with { Commands = changed };

        ReplayMismatch? mismatch = ReplayPlayer.Verify(replay with { Ticks = ticks }, TestContent.Real);
        Assert.NotNull(mismatch);
        Assert.True(mismatch!.Value.Tick > tick.Tick);
    }

    [Fact]
    public void Replay_refuses_different_content()
    {
        (Replay replay, _) = PlayStandard(2UL);
        Assert.Throws<InvalidDataException>(() => ReplayPlayer.Verify(replay with { ContentHash = 1 }, TestContent.Real));
    }

    /// <summary>
    /// Recorded matches committed under fixtures/replays. When a rule change alters them on purpose, regenerate with
    /// SAMJAE_WRITE_REPLAYS=1 and say why in the commit.
    /// </summary>
    [Theory]
    [InlineData("standard-seed1.json", 1UL)]
    [InlineData("standard-seed2.json", 2UL)]
    public void Recorded_replays_still_reproduce(string name, ulong seed)
    {
        string path = TestContent.RepositoryPath("core.tests/fixtures/replays/" + name);
        if (Environment.GetEnvironmentVariable(WriteVariable) == "1")
        {
            File.WriteAllText(path, ReplayCodec.Write(PlayStandard(seed).Replay));
        }

        Replay replay = ReplayCodec.Read(File.ReadAllText(path));
        ReplayMismatch? mismatch = ReplayPlayer.Verify(replay, TestContent.Real);
        Assert.True(mismatch == null, $"{name} diverged: {mismatch}");
    }
}
