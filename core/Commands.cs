using System.Collections.Generic;

namespace Samjae.Core;

/// <summary>
/// Everything a player can ask for. Players, bots, the network and replays all enter through this one type.
/// <see cref="Player"/> is the faction issuing it, and a command only ever acts on that faction's own entities.
/// </summary>
public abstract record PlayerCommand(int Player)
{
    public sealed record Move(int Player, IReadOnlyList<int> Units, Fix X, Fix Y, bool Queued = false) : PlayerCommand(Player);
    public sealed record AttackMove(int Player, IReadOnlyList<int> Units, Fix X, Fix Y, bool Queued = false) : PlayerCommand(Player);
    public sealed record Patrol(int Player, IReadOnlyList<int> Units, Fix X, Fix Y, bool Queued = false) : PlayerCommand(Player);
    public sealed record Hold(int Player, IReadOnlyList<int> Units, bool Queued = false) : PlayerCommand(Player);
    public sealed record Attack(int Player, IReadOnlyList<int> Units, int Target, bool Queued = false) : PlayerCommand(Player);
    public sealed record Harvest(int Player, IReadOnlyList<int> Units, int Node, bool Queued = false) : PlayerCommand(Player);
    public sealed record Construct(int Player, IReadOnlyList<int> Units, int Site, bool Queued = false) : PlayerCommand(Player);
    public sealed record Build(int Player, string TypeId, int TileX, int TileY) : PlayerCommand(Player);
    public sealed record CancelBuild(int Player, int Building) : PlayerCommand(Player);
    public sealed record Train(int Player, int Building, string TypeId) : PlayerCommand(Player);
    public sealed record CancelTrain(int Player, int Building) : PlayerCommand(Player);
    public sealed record Research(int Player, int Building, string TechId) : PlayerCommand(Player);
    public sealed record CancelResearch(int Player, int Building) : PlayerCommand(Player);
    public sealed record Rally(int Player, int Building, Fix X, Fix Y) : PlayerCommand(Player);
    public sealed record Surrender(int Player) : PlayerCommand(Player);

    /// <summary>One caster, so a group order does not spend every caster's cooldown. Target is 0 unless the ability targets a unit; X and Y are read only for a point.</summary>
    public sealed record Cast(int Player, int Caster, string AbilityId, int Target = 0, Fix X = default, Fix Y = default, bool Queued = false) : PlayerCommand(Player);
}

public enum CommandRejection : byte
{
    MatchEnded = 1,
    NoOwnUnits,
    OutOfBounds,
    InvalidTarget,
    NotYourBuilding,
    UnsupportedRally,
    NotInMatch,
    Build,
    Train,
    Research,
    CancelBuild,
    NothingToCancel,
    Cast,
}

/// <summary>
/// The outcome of one command, in the order the commands were given. A rejected build, train or research
/// carries the system's own failure in <see cref="Detail"/>.
/// </summary>
public readonly record struct CommandResult(int Index, CommandRejection? Rejection, int AppliedUnits = 0, int EntityId = 0, string? Detail = null)
{
    public bool Accepted => Rejection == null;
}
