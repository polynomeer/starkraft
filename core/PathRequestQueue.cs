using System.IO;

namespace Samjae.Core;

/// <summary>Limits are per tick and part of the match setup, because they change when units receive paths.</summary>
public sealed record PathSettings(int NodeBudgetPerTick, int QueueLimit, int EnqueuesPerTick)
{
    /// <summary>The Kotlin limits of 2,000 nodes and 50 requests per tick at 50 ticks/s, kept equal per second at 20 ticks/s.</summary>
    public static readonly PathSettings Default = new(5000, 256, 125);
}

/// <summary>Units waiting for a path. Requests carried over between ticks affect play, so this is saved and hashed.</summary>
public sealed class PathRequestQueue
{
    private readonly int[] _ids;
    private int _enqueuedThisTick;

    public PathSettings Settings { get; }
    public int Count { get; private set; }

    public PathRequestQueue(PathSettings settings)
    {
        Settings = settings;
        _ids = new int[settings.QueueLimit];
    }

    public bool Enqueue(int id)
    {
        if (_enqueuedThisTick >= Settings.EnqueuesPerTick || Count >= _ids.Length)
        {
            return false;
        }

        _ids[Count++] = id;
        _enqueuedThisTick++;
        return true;
    }

    public void BeginTick() => _enqueuedThisTick = 0;

    internal int IdAt(int index) => _ids[index];
    internal void SetId(int index, int id) => _ids[index] = id;
    internal void Reset(int count) => Count = count;

    internal void Write(BinaryWriter writer)
    {
        writer.Write(_enqueuedThisTick);
        writer.Write(Count);
        for (int i = 0; i < Count; i++)
        {
            writer.Write(_ids[i]);
        }
    }

    internal void Read(BinaryReader reader)
    {
        _enqueuedThisTick = reader.ReadInt32();
        Count = reader.ReadInt32();
        for (int i = 0; i < Count; i++)
        {
            _ids[i] = reader.ReadInt32();
        }
    }
}
