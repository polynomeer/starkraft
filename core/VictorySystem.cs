using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>Ends the match when at most one faction has anything left alive. Resource nodes and faction 0 do not count.</summary>
public sealed class VictorySystem : ITickSystem
{
    private readonly World _world;

    public VictorySystem(World world) => _world = world;

    public void Tick()
    {
        if (_world.MatchEnded)
        {
            return;
        }

        SortedSet<int> factions = LivingFactions(_world);
        if (factions.Count > 1)
        {
            return;
        }

        _world.MatchEnded = true;
        _world.WinnerFaction = factions.Count == 1 ? factions.Min : null;
        _world.EndReason = factions.Count == 1 ? MatchEndReason.Elimination : MatchEndReason.Draw;
    }

    public static SortedSet<int> LivingFactions(World world)
    {
        var factions = new SortedSet<int>();
        foreach (int id in world.Tags)
        {
            int faction = world.Tags.Get(id).Faction;
            if (faction > 0 && world.IsAttackable(id))
            {
                factions.Add(faction);
            }
        }

        return factions;
    }
}

/// <summary>Tiles each faction can see this tick. Rebuilt every tick from state, so it is never saved.</summary>
public sealed class VisionSystem : ITickSystem
{
    private readonly World _world;
    private readonly SortedDictionary<int, bool[]> _visible = new();

    public VisionSystem(World world) => _world = world;

    public bool IsVisible(int faction, int x, int y) =>
        _world.Map.InBounds(x, y) && _visible.TryGetValue(faction, out bool[]? tiles) && tiles[y * _world.Map.Width + x];

    public int VisibleCount(int faction) =>
        _visible.TryGetValue(faction, out bool[]? tiles) ? System.Array.FindAll(tiles, t => t).Length : 0;

    public void Tick()
    {
        foreach (bool[] tiles in _visible.Values)
        {
            System.Array.Clear(tiles);
        }

        foreach (int id in _world.AliveIds)
        {
            if (_world.VisionRanges.TryGet(id, out Fix range) && _world.Positions.TryGet(id, out Position position)
                && _world.Tags.TryGet(id, out UnitTag tag) && tag.Faction > 0)
            {
                Reveal(TilesFor(tag.Faction), position, range);
            }
        }
    }

    private bool[] TilesFor(int faction)
    {
        if (!_visible.TryGetValue(faction, out bool[]? tiles))
        {
            tiles = new bool[_world.Map.Width * _world.Map.Height];
            _visible[faction] = tiles;
        }

        return tiles;
    }

    /// <summary>A disc of whole tiles: the range rounded up, measured between tile indices, as in Kotlin.</summary>
    private void Reveal(bool[] tiles, Position position, Fix range)
    {
        int radius = (range.Raw + Fix.OneRaw - 1) >> Fix.FractionBits;
        int centreX = position.X.FloorToInt();
        int centreY = position.Y.FloorToInt();
        for (int y = System.Math.Max(0, centreY - radius); y <= System.Math.Min(_world.Map.Height - 1, centreY + radius); y++)
        {
            for (int x = System.Math.Max(0, centreX - radius); x <= System.Math.Min(_world.Map.Width - 1, centreX + radius); x++)
            {
                int dx = x - centreX;
                int dy = y - centreY;
                if (dx * dx + dy * dy <= radius * radius)
                {
                    tiles[y * _world.Map.Width + x] = true;
                }
            }
        }
    }
}
