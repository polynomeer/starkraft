using System;
using System.IO;

namespace Samjae.Core;

/// <summary>Static occupants are buildings. Dynamic occupants are units and are rebuilt every tick.</summary>
public sealed class OccupancyGrid
{
    private readonly int[] _dynamicCounts;
    private readonly int[] _staticCounts;

    public int Width { get; }
    public int Height { get; }

    public OccupancyGrid(int width, int height)
    {
        Width = width;
        Height = height;
        _dynamicCounts = new int[width * height];
        _staticCounts = new int[width * height];
    }

    public void ClearDynamic() => Array.Clear(_dynamicCounts);

    public void AddDynamic(int x, int y)
    {
        if (InBounds(x, y))
        {
            _dynamicCounts[y * Width + x]++;
        }
    }

    public void AddStatic(int x, int y)
    {
        if (InBounds(x, y))
        {
            _staticCounts[y * Width + x]++;
        }
    }

    public void RemoveStatic(int x, int y)
    {
        if (InBounds(x, y) && _staticCounts[y * Width + x] > 0)
        {
            _staticCounts[y * Width + x]--;
        }
    }

    public bool IsStaticBlocked(int x, int y) => !InBounds(x, y) || _staticCounts[y * Width + x] > 0;

    public bool IsBlocked(int x, int y)
    {
        if (!InBounds(x, y))
        {
            return true;
        }

        int index = y * Width + x;
        return _staticCounts[index] + _dynamicCounts[index] > 0;
    }

    /// <summary>Like <see cref="IsBlocked"/>, except units on the allowed tile do not count, so a unit does not block itself.</summary>
    public bool IsBlockedAllowing(int x, int y, int allowX, int allowY)
    {
        if (!InBounds(x, y))
        {
            return true;
        }

        return x == allowX && y == allowY ? _staticCounts[y * Width + x] > 0 : IsBlocked(x, y);
    }

    internal void Write(BinaryWriter writer)
    {
        for (int i = 0; i < _staticCounts.Length; i++)
        {
            writer.Write(_staticCounts[i]);
            writer.Write(_dynamicCounts[i]);
        }
    }

    internal void Read(BinaryReader reader)
    {
        for (int i = 0; i < _staticCounts.Length; i++)
        {
            _staticCounts[i] = reader.ReadInt32();
            _dynamicCounts[i] = reader.ReadInt32();
        }
    }

    private bool InBounds(int x, int y) => x >= 0 && y >= 0 && x < Width && y < Height;
}
