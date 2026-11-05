using System;

namespace Samjae.Core;

/// <summary>Breadth-first distance from one goal tile. Derived from the map, so it is a cache and never part of the state.</summary>
internal sealed class FlowField
{
    public const ushort Unreachable = ushort.MaxValue;

    private static readonly (int X, int Y)[] Cardinals = { (1, 0), (-1, 0), (0, 1), (0, -1) };

    public readonly ushort[] Distance;

    public FlowField(int width, int height, ReadOnlySpan<byte> blocked, int goalIndex)
    {
        Distance = new ushort[width * height];
        Array.Fill(Distance, Unreachable);
        var queue = new int[width * height];
        int head = 0;
        int tail = 0;
        Distance[goalIndex] = 0;
        queue[tail++] = goalIndex;

        while (head < tail)
        {
            int current = queue[head++];
            int x = current % width;
            int y = current / width;
            foreach ((int dx, int dy) in Cardinals)
            {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                {
                    continue;
                }

                int next = ny * width + nx;
                if (blocked[next] == 0 && Distance[next] == Unreachable)
                {
                    Distance[next] = (ushort)(Distance[current] + 1);
                    queue[tail++] = next;
                }
            }
        }
    }
}
