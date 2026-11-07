using System;
using System.IO;

namespace Samjae.Core;

public sealed record MatchSetup(int MapWidth, int MapHeight, ulong Seed, PathSettings? Paths = null);

/// <summary>
/// The rule core's only entry point. Hash, save and resync all share one serialised form, so anything
/// that affects play must be written by <see cref="SaveState"/>.
/// </summary>
public sealed class Simulation
{
    public const int TicksPerSecond = 20;

    private const int StateMagic = 0x4A53; // "SJ"
    private const int StateVersion = 3;

    private readonly ITickSystem[] _tickOrder;
    private DeterministicRandom _random;

    public int Tick { get; private set; }
    public World World { get; }
    public GameContent Content { get; }
    public PathfindingSystem Pathing { get; }
    public MovementSystem Movement { get; }

    private Simulation(World world, GameContent content, ulong randomState)
    {
        World = world;
        Content = content;
        _random = new DeterministicRandom(randomState);
        Pathing = new PathfindingSystem(world);
        Movement = new MovementSystem(world, content);

        // The tick order, in one place. Changing it changes every hash and replay.
        _tickOrder = new ITickSystem[]
        {
            new AliveSystem(world),
            new OccupancySystem(world),
            Pathing,
            Movement,
        };
    }

    public System.Collections.Generic.IEnumerable<string> SystemOrder
    {
        get
        {
            foreach (ITickSystem system in _tickOrder)
            {
                yield return system.GetType().Name;
            }
        }
    }

    public static Simulation Create(MatchSetup setup, GameContent content)
    {
        if (setup.MapWidth <= 0 || setup.MapHeight <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(setup), "Map size must be positive.");
        }

        return new Simulation(new World(setup.MapWidth, setup.MapHeight, setup.Paths), content, setup.Seed);
    }

    public void Step()
    {
        World.ClearRemovedEvents();
        foreach (ITickSystem system in _tickOrder)
        {
            system.Tick();
        }

        Tick++;
    }

    /// <summary>Serialises the whole state. BinaryWriter is little-endian on every platform.</summary>
    public byte[] SaveState()
    {
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);
        writer.Write(StateMagic);
        writer.Write(StateVersion);
        writer.Write(Content.Hash);
        writer.Write(World.Map.Width);
        writer.Write(World.Map.Height);
        PathSettings paths = World.PathRequests.Settings;
        writer.Write(paths.NodeBudgetPerTick);
        writer.Write(paths.QueueLimit);
        writer.Write(paths.EnqueuesPerTick);
        writer.Write(Tick);
        writer.Write(_random.State);
        WorldCodec.Write(writer, World);
        writer.Flush();
        return stream.ToArray();
    }

    /// <summary>Requires the same content the state was saved with, because rules read it every tick.</summary>
    public static Simulation LoadState(byte[] state, GameContent content)
    {
        using var reader = new BinaryReader(new MemoryStream(state));
        int magic = reader.ReadInt32();
        int version = reader.ReadInt32();
        if (magic != StateMagic || version != StateVersion)
        {
            throw new InvalidDataException($"Unsupported state: magic {magic}, version {version}.");
        }

        ulong contentHash = reader.ReadUInt64();
        if (contentHash != content.Hash)
        {
            throw new InvalidDataException("State was saved with different game content.");
        }

        int width = reader.ReadInt32();
        int height = reader.ReadInt32();
        var paths = new PathSettings(reader.ReadInt32(), reader.ReadInt32(), reader.ReadInt32());
        var world = new World(width, height, paths);
        int tick = reader.ReadInt32();
        ulong randomState = reader.ReadUInt64();
        WorldCodec.Read(reader, world);
        return new Simulation(world, content, randomState) { Tick = tick };
    }

    public ulong ComputeHash() => StateHash.Compute(SaveState());
}
