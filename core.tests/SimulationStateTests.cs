using System;
using System.IO;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class SimulationStateTests
{
    private static readonly MatchSetup Setup = new(64, 64, 20261105UL);

    private static Simulation RunTicks(int ticks)
    {
        Simulation simulation = Simulation.Create(Setup, TestContent.Real);
        for (int i = 0; i < ticks; i++)
        {
            simulation.Step();
        }

        return simulation;
    }

    [Fact]
    public void Same_setup_gives_the_same_hash()
    {
        Assert.Equal(RunTicks(100).ComputeHash(), RunTicks(100).ComputeHash());
    }

    [Fact]
    public void Hash_changes_with_tick_seed_and_map()
    {
        ulong baseline = RunTicks(100).ComputeHash();
        Assert.NotEqual(baseline, RunTicks(101).ComputeHash());
        Assert.NotEqual(Simulation.Create(Setup, TestContent.Real).ComputeHash(), Simulation.Create(Setup with { Seed = 7 }, TestContent.Real).ComputeHash());
        Assert.NotEqual(Simulation.Create(Setup, TestContent.Real).ComputeHash(), Simulation.Create(Setup with { MapWidth = 32 }, TestContent.Real).ComputeHash());
    }

    [Fact]
    public void Save_and_load_continue_to_the_same_hash()
    {
        Simulation original = RunTicks(50);
        Simulation restored = Simulation.LoadState(original.SaveState(), TestContent.Real);
        Assert.Equal(original.ComputeHash(), restored.ComputeHash());

        original.Step();
        restored.Step();
        Assert.Equal(original.ComputeHash(), restored.ComputeHash());
    }

    [Fact]
    public void Load_rejects_unknown_versions_and_truncated_data()
    {
        byte[] state = RunTicks(1).SaveState();
        byte[] wrongVersion = (byte[])state.Clone();
        wrongVersion[4] = 99;
        Assert.Throws<InvalidDataException>(() => Simulation.LoadState(wrongVersion, TestContent.Real));
        Assert.Throws<EndOfStreamException>(() => Simulation.LoadState(state[..^1], TestContent.Real));
    }

    [Theory]
    [InlineData(0, 64)]
    [InlineData(64, -1)]
    public void Create_rejects_invalid_map_sizes(int width, int height)
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Simulation.Create(new MatchSetup(width, height, 1UL), TestContent.Real));
    }

    [Fact]
    public void Random_sequence_is_pinned()
    {
        // Published SplitMix64 output for seed 0. A change here would silently fork every replay.
        var random = new DeterministicRandom(0UL);
        Assert.Equal(0xE220A8397B1DCDAFUL, random.NextUInt64());
        Assert.Equal(0x6E789E6AA1B965F4UL, random.NextUInt64());
    }

    [Fact]
    public void State_hash_matches_published_fnv1a_values()
    {
        Assert.Equal(0xCBF29CE484222325UL, StateHash.Compute(ReadOnlySpan<byte>.Empty));
        Assert.Equal(0xAF63DC4C8601EC8CUL, StateHash.Compute("a"u8));
    }
}
