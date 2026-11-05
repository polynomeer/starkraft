using System;
using System.Collections.Generic;
using System.IO;

namespace Samjae.Core;

/// <summary>
/// P0 rule core: movement, crowding and buildings on a tile grid. No engine, IO or floating point.
/// Units live in dense arrays and are always visited in ascending id order, which is what makes a tick reproducible.
/// </summary>
public sealed class Simulation
{
    public const int TicksPerSecond = 20;
    public const int NoTarget = -1;

    private const int FieldCacheLimit = 256;
    private const int MaxArrivalTiles = 8;
    private static readonly Fix Speed = Fix.FromRatio(3, 25);
    private static readonly Fix UnitRadius = Fix.FromRatio(7, 20);
    private static readonly Fix PushLimit = Fix.FromRatio(3, 50);
    private static readonly (int X, int Y)[] Neighbours =
    {
        (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1),
    };

    private readonly Dictionary<int, FlowField> _fields = new();
    private readonly int[] _bucketHead;
    private readonly int[] _bucketNext;
    private readonly int[] _pushX;
    private readonly int[] _pushY;

    private DeterministicRandom _random;
    private readonly byte[] _blocked;
    private readonly BuildingInfo[] _buildings;
    private readonly int[] _x;
    private readonly int[] _y;
    private readonly byte[] _owner;
    private readonly int[] _target;
    private readonly byte[] _arrivalTiles;

    public int Tick { get; private set; }
    public int MapWidth { get; }
    public int MapHeight { get; }
    public int UnitCount => _x.Length;

    public ReadOnlySpan<int> UnitXRaw => _x;
    public ReadOnlySpan<int> UnitYRaw => _y;
    public ReadOnlySpan<byte> UnitOwner => _owner;
    public ReadOnlySpan<int> UnitTarget => _target;
    public ReadOnlySpan<byte> Blocked => _blocked;
    public ReadOnlySpan<BuildingInfo> Buildings => _buildings;

    private Simulation(int mapWidth, int mapHeight, int unitCount, int buildingCount)
    {
        MapWidth = mapWidth;
        MapHeight = mapHeight;
        _blocked = new byte[mapWidth * mapHeight];
        _buildings = new BuildingInfo[buildingCount];
        _x = new int[unitCount];
        _y = new int[unitCount];
        _owner = new byte[unitCount];
        _target = new int[unitCount];
        _arrivalTiles = new byte[unitCount];
        _bucketHead = new int[mapWidth * mapHeight];
        _bucketNext = new int[unitCount];
        _pushX = new int[unitCount];
        _pushY = new int[unitCount];
    }

    public static Simulation Create(MatchSetup setup)
    {
        var simulation = new Simulation(setup.MapWidth, setup.MapHeight, setup.UnitCount, setup.BuildingCount)
        {
            _random = new DeterministicRandom(setup.Seed),
        };
        simulation.PlaceBuildings();
        simulation.ScatterRocks();
        simulation.SpawnUnits();
        return simulation;
    }

    public void Step(ReadOnlySpan<MoveCommand> commands)
    {
        foreach (MoveCommand command in commands)
        {
            ApplyMove(command);
        }

        RebuildBuckets();
        ComputeCrowdPush();
        MoveUnits();
        Tick++;
    }

    public int NearestWalkableTile(int tileX, int tileY)
    {
        int startX = Math.Clamp(tileX, 0, MapWidth - 1);
        int startY = Math.Clamp(tileY, 0, MapHeight - 1);
        for (int radius = 0; radius < Math.Max(MapWidth, MapHeight); radius++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dx = -radius; dx <= radius; dx++)
                {
                    if (Math.Max(Math.Abs(dx), Math.Abs(dy)) != radius)
                    {
                        continue;
                    }

                    int x = startX + dx;
                    int y = startY + dy;
                    if (x >= 0 && y >= 0 && x < MapWidth && y < MapHeight && _blocked[y * MapWidth + x] == 0)
                    {
                        return y * MapWidth + x;
                    }
                }
            }
        }

        throw new InvalidOperationException("The map has no walkable tile.");
    }

    private void PlaceBuildings()
    {
        const int attemptsPerBuilding = 200;
        for (int i = 0; i < _buildings.Length; i++)
        {
            int size = 2 + _random.NextInt(3);
            int owner = i % 2;
            for (int attempt = 0; attempt < attemptsPerBuilding; attempt++)
            {
                int x = 2 + _random.NextInt(MapWidth - size - 4);
                int y = 2 + _random.NextInt(MapHeight - size - 4);
                if (!IsAreaFree(x - 1, y - 1, size + 2, size + 2))
                {
                    continue;
                }

                _buildings[i] = new BuildingInfo(owner, i % 3, x, y, size, size);
                FillBlocked(x, y, size, size);
                break;
            }

            if (_buildings[i].Width == 0)
            {
                throw new InvalidOperationException($"No room for building {i}.");
            }
        }
    }

    private void ScatterRocks()
    {
        int rocks = MapWidth * MapHeight / 40;
        for (int i = 0; i < rocks; i++)
        {
            int x = _random.NextInt(MapWidth);
            int y = _random.NextInt(MapHeight);
            _blocked[y * MapWidth + x] = 1;
        }
    }

    private void SpawnUnits()
    {
        int side = 1;
        while (side * side < _x.Length)
        {
            side++;
        }

        for (int id = 0; id < _x.Length; id++)
        {
            int owner = id % 2;
            int offset = id / 2;
            int rawX = offset % side;
            int rawY = offset / side;
            int tileX = owner == 0 ? 3 + rawX : MapWidth - 4 - rawX;
            int tileY = owner == 0 ? 3 + rawY : MapHeight - 4 - rawY;
            int tile = NearestWalkableTile(tileX, tileY);
            _owner[id] = (byte)owner;
            _x[id] = (Fix.FromInt(tile % MapWidth) + Fix.Half).Raw;
            _y[id] = (Fix.FromInt(tile / MapWidth) + Fix.Half).Raw;
            _target[id] = NoTarget;
        }
    }

    private bool IsAreaFree(int x, int y, int width, int height)
    {
        for (int ty = y; ty < y + height; ty++)
        {
            for (int tx = x; tx < x + width; tx++)
            {
                if (_blocked[ty * MapWidth + tx] != 0)
                {
                    return false;
                }
            }
        }

        return true;
    }

    private void FillBlocked(int x, int y, int width, int height)
    {
        for (int ty = y; ty < y + height; ty++)
        {
            for (int tx = x; tx < x + width; tx++)
            {
                _blocked[ty * MapWidth + tx] = 1;
            }
        }
    }

    private void ApplyMove(in MoveCommand command)
    {
        int goal = NearestWalkableTile(command.TileX, command.TileY);
        byte arrival = (byte)Math.Min(MaxArrivalTiles, IntegerRoot(command.UnitIds.Count));
        foreach (int id in command.UnitIds)
        {
            if (id >= 0 && id < _x.Length && _owner[id] == command.Player)
            {
                _target[id] = goal;
                _arrivalTiles[id] = arrival;
            }
        }
    }

    private static int IntegerRoot(int value)
    {
        int root = 0;
        while ((root + 1) * (root + 1) <= value)
        {
            root++;
        }

        return root;
    }

    private void RebuildBuckets()
    {
        Array.Fill(_bucketHead, -1);
        for (int id = _x.Length - 1; id >= 0; id--)
        {
            int tile = TileOf(id);
            _bucketNext[id] = _bucketHead[tile];
            _bucketHead[tile] = id;
        }
    }

    private int TileOf(int id) => (_y[id] >> Fix.FractionBits) * MapWidth + (_x[id] >> Fix.FractionBits);

    private void ComputeCrowdPush()
    {
        Array.Clear(_pushX);
        Array.Clear(_pushY);
        for (int id = 0; id < _x.Length; id++)
        {
            int tileX = _x[id] >> Fix.FractionBits;
            int tileY = _y[id] >> Fix.FractionBits;
            for (int ny = Math.Max(0, tileY - 1); ny <= Math.Min(MapHeight - 1, tileY + 1); ny++)
            {
                for (int nx = Math.Max(0, tileX - 1); nx <= Math.Min(MapWidth - 1, tileX + 1); nx++)
                {
                    PushAgainstBucket(id, _bucketHead[ny * MapWidth + nx]);
                }
            }
        }
    }

    private void PushAgainstBucket(int id, int other)
    {
        Fix diameter = UnitRadius + UnitRadius;
        for (; other != -1; other = _bucketNext[other])
        {
            if (other <= id)
            {
                continue;
            }

            Fix dx = Fix.FromRaw(_x[id] - _x[other]);
            Fix dy = Fix.FromRaw(_y[id] - _y[other]);
            if (dx.Raw > diameter.Raw || dx.Raw < -diameter.Raw || dy.Raw > diameter.Raw || dy.Raw < -diameter.Raw)
            {
                continue;
            }

            Fix distance = Fix.Sqrt(dx * dx + dy * dy);
            if (distance >= diameter)
            {
                continue;
            }

            ApplyPushPair(id, other, dx, dy, distance, diameter);
            StopIfNeighbourArrived(id, other);
        }
    }

    private void ApplyPushPair(int a, int b, Fix dx, Fix dy, Fix distance, Fix diameter)
    {
        if (distance == Fix.Zero)
        {
            (int X, int Y) direction = Neighbours[_random.NextInt(Neighbours.Length)];
            dx = Fix.FromInt(direction.X);
            dy = Fix.FromInt(direction.Y);
            distance = Fix.Sqrt(dx * dx + dy * dy);
        }

        Fix strength = (diameter - distance) * Fix.Half;
        int pushX = (dx / distance * strength).Raw;
        int pushY = (dy / distance * strength).Raw;
        _pushX[a] += pushX;
        _pushY[a] += pushY;
        _pushX[b] -= pushX;
        _pushY[b] -= pushY;
    }

    /// <summary>A unit near its goal stops once it touches a same-goal unit that already stopped, so groups fill the area.</summary>
    private void StopIfNeighbourArrived(int a, int b)
    {
        TryContagiousStop(a, b);
        TryContagiousStop(b, a);
    }

    private void TryContagiousStop(int moving, int stopped)
    {
        if (_target[moving] == NoTarget || _target[stopped] != NoTarget || _owner[moving] != _owner[stopped])
        {
            return;
        }

        FlowField field = FieldFor(_target[moving]);
        if (field.Distance[TileOf(moving)] <= _arrivalTiles[moving])
        {
            _target[moving] = NoTarget;
        }
    }

    private void MoveUnits()
    {
        for (int id = 0; id < _x.Length; id++)
        {
            (Fix moveX, Fix moveY) = DesiredMove(id);
            int pushX = Math.Clamp(_pushX[id], -PushLimit.Raw, PushLimit.Raw);
            int pushY = Math.Clamp(_pushY[id], -PushLimit.Raw, PushLimit.Raw);
            TranslateWithCollision(id, moveX.Raw + pushX, moveY.Raw + pushY);
        }
    }

    private (Fix X, Fix Y) DesiredMove(int id)
    {
        int goal = _target[id];
        if (goal == NoTarget)
        {
            return (Fix.Zero, Fix.Zero);
        }

        FlowField field = FieldFor(goal);
        int tile = TileOf(id);
        if (field.Distance[tile] == 0)
        {
            _target[id] = NoTarget;
            return (Fix.Zero, Fix.Zero);
        }

        int next = BestNeighbour(field, tile);
        if (next == -1)
        {
            _target[id] = NoTarget;
            return (Fix.Zero, Fix.Zero);
        }

        Fix toX = Fix.FromInt(next % MapWidth) + Fix.Half - Fix.FromRaw(_x[id]);
        Fix toY = Fix.FromInt(next / MapWidth) + Fix.Half - Fix.FromRaw(_y[id]);
        Fix length = Fix.Sqrt(toX * toX + toY * toY);
        return length == Fix.Zero ? (Fix.Zero, Fix.Zero) : (toX / length * Speed, toY / length * Speed);
    }

    private int BestNeighbour(FlowField field, int tile)
    {
        int x = tile % MapWidth;
        int y = tile / MapWidth;
        int best = -1;
        int bestDistance = field.Distance[tile];
        foreach ((int dx, int dy) in Neighbours)
        {
            int nx = x + dx;
            int ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= MapWidth || ny >= MapHeight || CutsBlockedCorner(x, y, dx, dy))
            {
                continue;
            }

            int candidate = ny * MapWidth + nx;
            if (_blocked[candidate] == 0 && field.Distance[candidate] < bestDistance)
            {
                best = candidate;
                bestDistance = field.Distance[candidate];
            }
        }

        return best;
    }

    private bool CutsBlockedCorner(int x, int y, int dx, int dy)
    {
        if (dx == 0 || dy == 0)
        {
            return false;
        }

        return _blocked[y * MapWidth + x + dx] != 0 || _blocked[(y + dy) * MapWidth + x] != 0;
    }

    private void TranslateWithCollision(int id, int deltaX, int deltaY)
    {
        int nextX = _x[id] + deltaX;
        if (IsWalkableRaw(nextX, _y[id]))
        {
            _x[id] = nextX;
        }

        int nextY = _y[id] + deltaY;
        if (IsWalkableRaw(_x[id], nextY))
        {
            _y[id] = nextY;
        }
    }

    private bool IsWalkableRaw(int rawX, int rawY)
    {
        int tileX = rawX >> Fix.FractionBits;
        int tileY = rawY >> Fix.FractionBits;
        return tileX >= 0 && tileY >= 0 && tileX < MapWidth && tileY < MapHeight
            && _blocked[tileY * MapWidth + tileX] == 0;
    }

    private FlowField FieldFor(int goal)
    {
        if (_fields.TryGetValue(goal, out FlowField? field))
        {
            return field;
        }

        if (_fields.Count >= FieldCacheLimit)
        {
            _fields.Clear();
        }

        field = new FlowField(MapWidth, MapHeight, _blocked, goal);
        _fields[goal] = field;
        return field;
    }

    /// <summary>Serialises the whole state with an explicit little-endian layout. Hash, save and resync share this one format.</summary>
    public byte[] SaveState()
    {
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);
        writer.Write(MapWidth);
        writer.Write(MapHeight);
        writer.Write(_x.Length);
        writer.Write(_buildings.Length);
        writer.Write(Tick);
        writer.Write(_random.State);
        writer.Write(_blocked);
        foreach (BuildingInfo b in _buildings)
        {
            writer.Write(b.Owner);
            writer.Write(b.Kind);
            writer.Write(b.TileX);
            writer.Write(b.TileY);
            writer.Write(b.Width);
            writer.Write(b.Height);
        }

        for (int id = 0; id < _x.Length; id++)
        {
            writer.Write(_x[id]);
            writer.Write(_y[id]);
            writer.Write(_owner[id]);
            writer.Write(_target[id]);
            writer.Write(_arrivalTiles[id]);
        }

        writer.Flush();
        return stream.ToArray();
    }

    public static Simulation LoadState(byte[] state)
    {
        using var reader = new BinaryReader(new MemoryStream(state));
        int width = reader.ReadInt32();
        int height = reader.ReadInt32();
        int units = reader.ReadInt32();
        int buildings = reader.ReadInt32();
        var simulation = new Simulation(width, height, units, buildings)
        {
            Tick = reader.ReadInt32(),
            _random = new DeterministicRandom(reader.ReadUInt64()),
        };
        reader.BaseStream.ReadExactly(simulation._blocked);
        for (int i = 0; i < buildings; i++)
        {
            simulation._buildings[i] = new BuildingInfo(
                reader.ReadInt32(), reader.ReadInt32(), reader.ReadInt32(),
                reader.ReadInt32(), reader.ReadInt32(), reader.ReadInt32());
        }

        for (int id = 0; id < units; id++)
        {
            simulation._x[id] = reader.ReadInt32();
            simulation._y[id] = reader.ReadInt32();
            simulation._owner[id] = reader.ReadByte();
            simulation._target[id] = reader.ReadInt32();
            simulation._arrivalTiles[id] = reader.ReadByte();
        }

        return simulation;
    }

    /// <summary>FNV-1a 64 over the saved state.</summary>
    public ulong ComputeHash()
    {
        ulong hash = 14695981039346656037UL;
        foreach (byte value in SaveState())
        {
            hash = unchecked((hash ^ value) * 1099511628211UL);
        }

        return hash;
    }
}
