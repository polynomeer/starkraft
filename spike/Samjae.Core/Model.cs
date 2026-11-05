using System.Collections.Generic;

namespace Samjae.Core;

public sealed record MatchSetup(int MapWidth, int MapHeight, int UnitCount, int BuildingCount, ulong Seed)
{
    public static readonly MatchSetup Baseline = new(64, 64, 200, 20, 20261105UL);
}

/// <summary>The one entry point for player, bot, network and replay input.</summary>
public readonly record struct MoveCommand(int Player, IReadOnlyList<int> UnitIds, int TileX, int TileY);

public readonly record struct BuildingInfo(int Owner, int Kind, int TileX, int TileY, int Width, int Height);
