using System;

namespace Samjae.Core;

/// <summary>
/// A* over the tile grid with an indexed binary heap. Scores are integers with 16 fraction bits, held in longs so
/// large terrain costs cannot overflow. All buffers are scratch space, so nothing here is game state.
/// </summary>
public sealed class Pathfinder
{
    private static readonly long DiagonalStep = Fix.FromText("1.41421356").Raw;
    private static readonly long StraightStep = Fix.OneRaw;
    private static readonly int[] DirX = { 1, 0, -1, 0, 1, 1, -1, -1 };
    private static readonly int[] DirY = { 0, 1, 0, -1, 1, -1, 1, -1 };

    private const byte Open = 1;
    private const byte Closed = 2;

    private readonly MapGrid _map;
    private readonly OccupancyGrid _occupancy;
    private readonly bool _allowCornerCut;
    private readonly int _width;

    private readonly int[] _visitId;
    private readonly byte[] _state;
    private readonly long[] _gScore;
    private readonly long[] _fScore;
    private readonly int[] _parent;
    private readonly int[] _heap;
    private readonly int[] _heapPosition;
    private readonly int[] _scratchPath;
    private int _heapSize;
    private int _searchId = 1;

    public int LastNodesUsed { get; private set; }
    public bool LastBudgetExhausted { get; private set; }

    public Pathfinder(MapGrid map, OccupancyGrid occupancy, bool allowCornerCut = false)
    {
        _map = map;
        _occupancy = occupancy;
        _allowCornerCut = allowCornerCut;
        _width = map.Width;
        int size = map.Width * map.Height;
        _visitId = new int[size];
        _state = new byte[size];
        _gScore = new long[size];
        _fScore = new long[size];
        _parent = new int[size];
        _heap = new int[size];
        _heapPosition = new int[size];
        _scratchPath = new int[size];
    }

    /// <summary>Writes waypoints from start to goal, with straight runs compressed, and returns their count. Zero means no path.</summary>
    public int FindPath(int startX, int startY, int goalX, int goalY, int maxNodes, int[] output, bool allowOccupiedGoal = false)
    {
        LastNodesUsed = 0;
        LastBudgetExhausted = false;
        if (!_map.InBounds(startX, startY) || !_map.InBounds(goalX, goalY) || !_map.IsPassable(goalX, goalY))
        {
            return 0;
        }

        bool alreadyThere = startX == goalX && startY == goalY;
        if (_occupancy.IsBlocked(goalX, goalY) && !alreadyThere && !allowOccupiedGoal)
        {
            return 0;
        }

        if (alreadyThere)
        {
            output[0] = Pack(startX, startY);
            return 1;
        }

        BeginSearch(startX, startY, goalX, goalY);
        int start = Pack(startX, startY);
        int goal = Pack(goalX, goalY);
        while (_heapSize > 0)
        {
            if (LastNodesUsed >= maxNodes)
            {
                LastBudgetExhausted = true;
                return 0;
            }

            int current = PopHeap();
            _state[current] = Closed;
            LastNodesUsed++;
            if (current == goal)
            {
                return BuildPath(start, goal, output);
            }

            ExpandNeighbours(current, goalX, goalY, allowOccupiedGoal);
        }

        return 0;
    }

    private void BeginSearch(int startX, int startY, int goalX, int goalY)
    {
        _searchId++;
        if (_searchId == int.MaxValue)
        {
            Array.Clear(_visitId);
            _searchId = 1;
        }

        _heapSize = 0;
        OpenNode(Pack(startX, startY), 0, Heuristic(startX, startY, goalX, goalY));
    }

    private void ExpandNeighbours(int current, int goalX, int goalY, bool allowOccupiedGoal)
    {
        int currentX = current % _width;
        int currentY = current / _width;
        long currentG = _gScore[current];
        for (int i = 0; i < DirX.Length; i++)
        {
            int nextX = currentX + DirX[i];
            int nextY = currentY + DirY[i];
            bool isGoalExempt = allowOccupiedGoal && nextX == goalX && nextY == goalY;
            if (!_map.IsPassable(nextX, nextY) || (_occupancy.IsBlocked(nextX, nextY) && !isGoalExempt))
            {
                continue;
            }

            if (i >= 4 && !_allowCornerCut && (IsClosedOff(nextX, currentY) || IsClosedOff(currentX, nextY)))
            {
                continue;
            }

            long step = i < 4 ? StraightStep : DiagonalStep;
            long nextG = currentG + (step * _map.Cost(nextX, nextY).Raw >> Fix.FractionBits);
            Relax(Pack(nextX, nextY), current, nextG, Heuristic(nextX, nextY, goalX, goalY));
        }
    }

    private bool IsClosedOff(int x, int y) => !_map.IsPassable(x, y) || _occupancy.IsBlocked(x, y);

    private void Relax(int node, int parent, long g, long heuristic)
    {
        if (_visitId[node] != _searchId)
        {
            OpenNode(node, g, g + heuristic);
            _parent[node] = parent;
        }
        else if (_state[node] == Open && g < _gScore[node])
        {
            _gScore[node] = g;
            _fScore[node] = g + heuristic;
            _parent[node] = parent;
            SiftUp(_heapPosition[node]);
        }
    }

    private void OpenNode(int node, long g, long f)
    {
        _visitId[node] = _searchId;
        _state[node] = Open;
        _gScore[node] = g;
        _fScore[node] = f;
        _parent[node] = -1;
        _heap[_heapSize] = node;
        _heapPosition[node] = _heapSize;
        _heapSize++;
        SiftUp(_heapSize - 1);
    }

    private int BuildPath(int start, int goal, int[] output)
    {
        int length = 0;
        for (int node = goal; node != -1 && node != start; node = _parent[node])
        {
            _scratchPath[length++] = node;
        }

        _scratchPath[length++] = start;
        Array.Reverse(_scratchPath, 0, length);
        return CompressStraightRuns(length, output);
    }

    private int CompressStraightRuns(int length, int[] output)
    {
        int count = 0;
        int previous = _scratchPath[0];
        int previousDx = 0;
        int previousDy = 0;
        output[count++] = previous;
        for (int i = 1; i < length; i++)
        {
            int node = _scratchPath[i];
            int dx = node % _width - previous % _width;
            int dy = node / _width - previous / _width;
            if (i > 1 && (dx != previousDx || dy != previousDy))
            {
                output[count++] = previous;
            }

            previousDx = dx;
            previousDy = dy;
            previous = node;
        }

        output[count++] = previous;
        return count;
    }

    private static long Heuristic(int x, int y, int goalX, int goalY)
    {
        int dx = Math.Abs(goalX - x);
        int dy = Math.Abs(goalY - y);
        long smaller = Math.Min(dx, dy);
        long total = dx + dy;
        return total * StraightStep + smaller * (DiagonalStep - 2 * StraightStep);
    }

    private int Pack(int x, int y) => y * _width + x;

    private int PopHeap()
    {
        int smallest = _heap[0];
        int last = _heap[--_heapSize];
        _heap[0] = last;
        _heapPosition[last] = 0;
        _heapPosition[smallest] = -1;
        SiftDown(0);
        return smallest;
    }

    private void SiftUp(int index)
    {
        while (index > 0)
        {
            int parent = (index - 1) / 2;
            if (_fScore[_heap[index]] >= _fScore[_heap[parent]])
            {
                return;
            }

            Swap(index, parent);
            index = parent;
        }
    }

    private void SiftDown(int index)
    {
        while (true)
        {
            int left = index * 2 + 1;
            int right = left + 1;
            if (left >= _heapSize)
            {
                return;
            }

            int smallest = right < _heapSize && _fScore[_heap[right]] < _fScore[_heap[left]] ? right : left;
            if (_fScore[_heap[smallest]] >= _fScore[_heap[index]])
            {
                return;
            }

            Swap(index, smallest);
            index = smallest;
        }
    }

    private void Swap(int a, int b)
    {
        (_heap[a], _heap[b]) = (_heap[b], _heap[a]);
        _heapPosition[_heap[a]] = a;
        _heapPosition[_heap[b]] = b;
    }
}
