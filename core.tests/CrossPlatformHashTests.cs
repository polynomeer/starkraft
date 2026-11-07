using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

/// <summary>
/// Produces the values CI compares between operating systems. When SAMJAE_HASH_OUT is set they are written
/// to that file. Arithmetic is fingerprinted too, because the state alone has no rules to exercise yet.
/// </summary>
public class CrossPlatformHashTests
{
    private const string OutputVariable = "SAMJAE_HASH_OUT";

    [Fact]
    public void Checkpoints_are_reproducible_and_written_for_ci()
    {
        string[] first = Checkpoints();
        Assert.Equal(first, Checkpoints());

        string? output = System.Environment.GetEnvironmentVariable(OutputVariable);
        if (!string.IsNullOrEmpty(output))
        {
            File.WriteAllText(output, string.Join('\n', first) + "\n");
        }
    }

    private static string[] Checkpoints()
    {
        var lines = new List<string>
        {
            Line("fix", FixFingerprint()),
            Line("content", TestContent.Real.Hash),
            Line("populated", WorldSamples.Populated().ComputeHash()),
        };
        var scenario = new MovementScenario(seed: 42UL);
        for (int tick = 0; tick <= 3000; tick++)
        {
            if (tick % 300 == 0)
            {
                lines.Add(Line($"tick-{tick}", scenario.Simulation.ComputeHash()));
            }

            scenario.StepOnce();
        }

        var battle = new BattleScenario(seed: 21UL);
        for (int tick = 0; tick <= 1200; tick++)
        {
            if (tick % 200 == 0)
            {
                lines.Add(Line($"battle-{tick}", battle.Simulation.ComputeHash()));
            }

            battle.Simulation.Step();
        }

        var economy = new EconomyScenario();
        for (int tick = 0; tick <= 1200; tick++)
        {
            if (tick % 200 == 0)
            {
                lines.Add(Line($"economy-{tick}", economy.Simulation.ComputeHash()));
            }

            economy.Simulation.Step();
        }

        return lines.ToArray();
    }

    private static string Line(string name, ulong value) =>
        $"{name} {value.ToString("x16", CultureInfo.InvariantCulture)}";

    private static ulong FixFingerprint()
    {
        var random = new DeterministicRandom(42UL);
        ulong hash = StateHash.Seed;
        Span<byte> buffer = stackalloc byte[4];
        for (int i = 0; i < 100_000; i++)
        {
            Fix a = Fix.FromRaw(random.NextInt(1 << 24) - (1 << 23));
            Fix b = Fix.FromRaw(Fix.OneRaw + random.NextInt(1 << 20)); // At least 1, so a / b stays in range.
            foreach (Fix value in new[] { a * b, a / b, a + b, a - b, Fix.Sqrt(b) })
            {
                BitConverter.TryWriteBytes(buffer, value.Raw);
                if (!BitConverter.IsLittleEndian)
                {
                    buffer.Reverse();
                }

                hash = StateHash.Append(hash, buffer);
            }
        }

        return hash;
    }
}
