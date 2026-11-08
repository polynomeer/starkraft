namespace Samjae.Core;

/// <summary>
/// Weapon reach measured from body edges, so a melee range means "touching" whatever the units' sizes. Units are
/// circles of their definition's radius; buildings are their footprint rectangle. Shared by combat and movement so
/// a unit stops walking exactly where it can start shooting.
/// </summary>
public sealed class Reach
{
    private readonly World _world;
    private readonly GameContent _content;

    public Reach(World world, GameContent content)
    {
        _world = world;
        _content = content;
    }

    public bool IsInReach(int attacker, Position from, int target, Fix range) => Gap(attacker, from, target) <= range;

    /// <summary>Distance between the attacker's edge and the target's edge. Negative when they overlap.</summary>
    public Fix Gap(int attacker, Position from, int target) => GapFromPoint(from, target) - RadiusOf(attacker);

    /// <summary>Distance from a point to the target's edge. Negative when the point is inside it.</summary>
    public Fix GapFromPoint(Position from, int target)
    {
        if (_world.Footprints.TryGet(target, out BuildingFootprint footprint))
        {
            return DistanceToRectangle(from, footprint);
        }

        Position to = _world.Positions.Get(target);
        return Fix.Length(to.X - from.X, to.Y - from.Y) - RadiusOf(target);
    }

    private Fix RadiusOf(int id) =>
        _world.Tags.TryGet(id, out UnitTag tag) ? _content.RadiusOf(tag.TypeId) : GameContent.DefaultRadius;

    private static Fix DistanceToRectangle(Position from, BuildingFootprint footprint)
    {
        Fix nearestX = Clamp(from.X, Fix.FromInt(footprint.TileX), Fix.FromInt(footprint.TileX + footprint.Width));
        Fix nearestY = Clamp(from.Y, Fix.FromInt(footprint.TileY), Fix.FromInt(footprint.TileY + footprint.Height));
        return Fix.Length(nearestX - from.X, nearestY - from.Y);
    }

    private static Fix Clamp(Fix value, Fix low, Fix high) => value < low ? low : value > high ? high : value;
}
