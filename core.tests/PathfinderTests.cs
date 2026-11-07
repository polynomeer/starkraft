using System;
using System.IO;
using Samjae.Core;
using Xunit;

namespace Samjae.Core.Tests;

public class PathfinderTests
{
    private const int Size = 16;

    private static (MapGrid Map, OccupancyGrid Occupancy, Pathfinder Finder, int[] Output) New(int size = Size)
    {
        var map = new MapGrid(size, size);
        var occupancy = new OccupancyGrid(size, size);
        return (map, occupancy, new Pathfinder(map, occupancy), new int[size * size]);
    }

    [Fact]
    public void Straight_run_compresses_to_its_two_ends()
    {
        var (_, _, finder, output) = New();
        Assert.Equal(2, finder.FindPath(1, 1, 8, 1, 1000, output));
        Assert.Equal(new[] { 1 * Size + 1, 1 * Size + 8 }, output[..2]);
        Assert.False(finder.LastBudgetExhausted);
    }

    [Fact]
    public void Start_on_the_goal_returns_one_node_without_searching()
    {
        var (_, _, finder, output) = New();
        Assert.Equal(1, finder.FindPath(3, 3, 3, 3, 1000, output));
        Assert.Equal(0, finder.LastNodesUsed);
    }

    [Fact]
    public void Goal_out_of_bounds_or_impassable_has_no_path()
    {
        var (map, _, finder, output) = New();
        map.SetBlocked(5, 5, true);
        Assert.Equal(0, finder.FindPath(1, 1, 5, 5, 1000, output));
        Assert.Equal(0, finder.FindPath(1, 1, Size, 0, 1000, output));
        Assert.Equal(0, finder.FindPath(-1, 1, 3, 3, 1000, output));
    }

    [Fact]
    public void Occupied_goal_needs_explicit_permission()
    {
        var (_, occupancy, finder, output) = New();
        occupancy.AddDynamic(5, 5);
        Assert.Equal(0, finder.FindPath(1, 1, 5, 5, 1000, output));
        Assert.True(finder.FindPath(1, 1, 5, 5, 1000, output, allowOccupiedGoal: true) > 0);
    }

    [Fact]
    public void Running_out_of_budget_is_reported()
    {
        var (_, _, finder, output) = New();
        Assert.Equal(0, finder.FindPath(0, 0, 15, 15, 3, output));
        Assert.True(finder.LastBudgetExhausted);
        Assert.Equal(3, finder.LastNodesUsed);
    }

    [Fact]
    public void Walled_off_goal_has_no_path_and_is_not_a_budget_failure()
    {
        var (map, _, finder, output) = New();
        for (int y = 0; y < Size; y++)
        {
            map.SetBlocked(7, y, true);
        }

        Assert.Equal(0, finder.FindPath(1, 1, 10, 1, 100000, output));
        Assert.False(finder.LastBudgetExhausted);
    }

    [Fact]
    public void Diagonal_never_cuts_a_blocked_corner()
    {
        var (map, _, finder, output) = New();
        map.SetBlocked(2, 1, true);
        Assert.True(finder.FindPath(1, 1, 2, 2, 1000, output) == 3, "must step around through (1,2)");
        Assert.Equal(1 * Size + 1, output[0]);
        Assert.Equal(2 * Size + 1, output[1]);
        Assert.Equal(2 * Size + 2, output[2]);
    }

    [Fact]
    public void Expensive_terrain_is_walked_around()
    {
        var (map, _, finder, output) = New();
        for (int y = 0; y < Size - 1; y++)
        {
            map.SetCost(5, y, Fix.FromInt(1000));
        }

        Assert.True(finder.FindPath(1, 1, 9, 1, 100000, output) > 2);
        bool crossedAtTheCheapTile = false;
        for (int i = 0; output[i] != 1 * Size + 9; i++)
        {
            crossedAtTheCheapTile |= output[i] / Size == Size - 1;
        }

        Assert.True(crossedAtTheCheapTile);
    }

    /// <summary>
    /// fixtures/kotlin-paths.txt holds the Kotlin Pathfinder's answers for 400 seeded maps: seed, found, straight steps,
    /// diagonal steps. Only "found" is compared. The Kotlin search is sound but its path reconstruction drops links, so
    /// its step counts come from paths cut short, which are often impossibly cheap.
    /// </summary>
    [Fact]
    public void Finds_a_path_on_the_same_maps_as_the_kotlin_pathfinder()
    {
        string[] lines = File.ReadAllLines(TestContent.RepositoryPath("core.tests/fixtures/kotlin-paths.txt"));
        Assert.Equal(400, lines.Length);
        foreach (string line in lines)
        {
            string[] parts = line.Split(' ');
            int seed = int.Parse(parts[0]);
            Assert.True(Solve(seed).Found == int.Parse(parts[1]), $"seed {seed}");
        }
    }

    [Fact]
    public void Paths_are_walkable_and_as_short_as_an_exhaustive_search()
    {
        int checkedPaths = 0;
        for (int seed = 1; seed <= 400; seed++)
        {
            (int found, int straight, int diagonal) = Solve(seed, out MapGrid map, out int[] nodes, out int length, out (int X, int Y) start, out (int X, int Y) goal);
            long? best = ExhaustiveCost(map, start, goal);
            Assert.Equal(best.HasValue, found == 1);
            if (found == 1)
            {
                AssertWalkable(map, nodes, length, seed);
                Assert.True(best == straight * StraightUnit + diagonal * DiagonalUnit, $"seed {seed}");
                checkedPaths++;
            }
        }

        Assert.True(checkedPaths > 200);
    }

    private const long StraightUnit = 100_000_000L;
    private const long DiagonalUnit = 141_421_356L;

    private static void AssertWalkable(MapGrid map, int[] nodes, int length, int seed)
    {
        for (int i = 1; i < length; i++)
        {
            int x = nodes[i - 1] % 32;
            int y = nodes[i - 1] / 32;
            int stepX = Math.Sign(nodes[i] % 32 - x);
            int stepY = Math.Sign(nodes[i] / 32 - y);
            while (x != nodes[i] % 32 || y != nodes[i] / 32)
            {
                bool cutsCorner = stepX != 0 && stepY != 0 && (map.IsBlocked(x + stepX, y) || map.IsBlocked(x, y + stepY));
                Assert.False(cutsCorner, $"seed {seed}: corner cut at ({x},{y})");
                x += stepX;
                y += stepY;
                Assert.True(map.IsPassable(x, y), $"seed {seed}: blocked tile ({x},{y}) on the path");
            }
        }
    }

    /// <summary>Plain Bellman-Ford relaxation, sharing no code or heuristic with the A* under test.</summary>
    private static long? ExhaustiveCost(MapGrid map, (int X, int Y) start, (int X, int Y) goal)
    {
        if (!map.IsPassable(goal.X, goal.Y))
        {
            return null;
        }

        var best = new long[32 * 32];
        Array.Fill(best, long.MaxValue);
        best[start.Y * 32 + start.X] = 0;
        for (bool changed = true; changed;)
        {
            changed = false;
            for (int i = 0; i < best.Length; i++)
            {
                changed |= best[i] != long.MaxValue && RelaxFrom(map, i, best);
            }
        }

        long cost = best[goal.Y * 32 + goal.X];
        return cost == long.MaxValue ? null : cost;
    }

    private static bool RelaxFrom(MapGrid map, int index, long[] best)
    {
        bool changed = false;
        int x = index % 32;
        int y = index / 32;
        for (int dy = -1; dy <= 1; dy++)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                bool diagonal = dx != 0 && dy != 0;
                if ((dx == 0 && dy == 0) || !map.IsPassable(x + dx, y + dy)
                    || (diagonal && (!map.IsPassable(x + dx, y) || !map.IsPassable(x, y + dy))))
                {
                    continue;
                }

                long next = best[index] + (diagonal ? DiagonalUnit : StraightUnit);
                int target = (y + dy) * 32 + x + dx;
                if (next < best[target])
                {
                    best[target] = next;
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static (int Found, int Straight, int Diagonal) Solve(int seed) => Solve(seed, out _, out _, out _, out _, out _);

    private static (int Found, int Straight, int Diagonal) Solve(
        int seed, out MapGrid solvedMap, out int[] nodes, out int length, out (int X, int Y) start, out (int X, int Y) goal)
    {
        int state = seed;
        int Next()
        {
            state = unchecked(state * 1103515245 + 12345);
            return (int)((uint)state >> 16) & 0x7FFF;
        }

        var (map, _, finder, output) = New(32);
        for (int y = 0; y < 32; y++)
        {
            for (int x = 0; x < 32; x++)
            {
                if (Next() % 100 < 22)
                {
                    map.SetBlocked(x, y, true);
                }
            }
        }

        int startX = Next() % 32;
        int startY = Next() % 32;
        int goalX = Next() % 32;
        int goalY = Next() % 32;
        map.SetBlocked(startX, startY, false);
        length = finder.FindPath(startX, startY, goalX, goalY, 100000, output);
        solvedMap = map;
        nodes = output;
        start = (startX, startY);
        goal = (goalX, goalY);
        int straight = 0;
        int diagonal = 0;
        for (int i = 1; i < length; i++)
        {
            int dx = Math.Abs(output[i] % 32 - output[i - 1] % 32);
            int dy = Math.Abs(output[i] / 32 - output[i - 1] / 32);
            diagonal += Math.Min(dx, dy);
            straight += Math.Max(dx, dy) - Math.Min(dx, dy);
        }

        return (length > 0 ? 1 : 0, straight, diagonal);
    }
}
