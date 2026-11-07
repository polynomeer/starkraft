namespace Samjae.Core;

/// <summary>SplitMix64. The state is a single value so it can be saved and hashed with the world.</summary>
public struct DeterministicRandom
{
    public ulong State;

    public DeterministicRandom(ulong seed) => State = seed;

    public ulong NextUInt64()
    {
        State = unchecked(State + 0x9E3779B97F4A7C15UL);
        ulong z = State;
        z = unchecked((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9UL);
        z = unchecked((z ^ (z >> 27)) * 0x94D049BB133111EBUL);
        return z ^ (z >> 31);
    }

    /// <summary>Returns a value in [0, exclusiveMax). The small modulo bias does not matter for gameplay rolls here.</summary>
    public int NextInt(int exclusiveMax) => (int)(NextUInt64() % (ulong)exclusiveMax);
}
