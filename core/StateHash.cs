using System;

namespace Samjae.Core;

/// <summary>FNV-1a 64. Written out here so the value can never change with a runtime or platform.</summary>
public static class StateHash
{
    public const ulong Seed = 14695981039346656037UL;

    public static ulong Compute(ReadOnlySpan<byte> bytes) => Append(Seed, bytes);

    public static ulong Append(ulong hash, ReadOnlySpan<byte> bytes)
    {
        foreach (byte value in bytes)
        {
            hash = unchecked((hash ^ value) * 1099511628211UL);
        }

        return hash;
    }
}
