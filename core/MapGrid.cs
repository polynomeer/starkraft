using System;

namespace Samjae.Core;

public sealed class MapGrid
{
    private readonly bool[] _passable;
    private readonly Fix[] _cost;

    public int Width { get; }
    public int Height { get; }

    public MapGrid(int width, int height)
    {
        Width = width;
        Height = height;
        _passable = new bool[width * height];
        _cost = new Fix[width * height];
        Array.Fill(_passable, true);
        Array.Fill(_cost, Fix.One);
    }

    public bool InBounds(int x, int y) => x >= 0 && y >= 0 && x < Width && y < Height;
    public bool IsPassable(int x, int y) => InBounds(x, y) && _passable[y * Width + x];
    public bool IsBlocked(int x, int y) => InBounds(x, y) && !_passable[y * Width + x];

    public void SetBlocked(int x, int y, bool blocked)
    {
        if (InBounds(x, y))
        {
            _passable[y * Width + x] = !blocked;
        }
    }

    /// <summary>Out of bounds is <see cref="Fix.MaxValue"/>, so a caller adding costs must check bounds first.</summary>
    public Fix Cost(int x, int y) => InBounds(x, y) ? _cost[y * Width + x] : Fix.MaxValue;

    public void SetCost(int x, int y, Fix value)
    {
        if (InBounds(x, y))
        {
            _cost[y * Width + x] = value;
        }
    }

    internal void Write(System.IO.BinaryWriter writer)
    {
        for (int i = 0; i < _passable.Length; i++)
        {
            writer.Write(_passable[i]);
            writer.Write(_cost[i].Raw);
        }
    }

    internal void Read(System.IO.BinaryReader reader)
    {
        for (int i = 0; i < _passable.Length; i++)
        {
            _passable[i] = reader.ReadBoolean();
            _cost[i] = Fix.FromRaw(reader.ReadInt32());
        }
    }
}
