using System;

namespace Samjae.Core;

/// <summary>Where a unit moves. Buildings and entity types with no unit definition are on the ground.</summary>
public enum MoveLayer : byte
{
    Ground = 1,
    Air = 2,
}

/// <summary>Which layers a weapon can hit.</summary>
[Flags]
public enum LayerMask : byte
{
    Ground = 1,
    Air = 2,
}

/// <summary>
/// Layer rules shared by occupancy, pathing, movement, combat and commands. Air units fly straight over terrain,
/// buildings and other units, take no tile, and can be hit only by weapons that reach the air. Effects and abilities
/// reach both layers.
/// </summary>
public static class Layers
{
    public static MoveLayer Of(World world, GameContent content, int id) =>
        world.Tags.TryGet(id, out UnitTag tag) && content.UnitOrNull(tag.TypeId) is UnitDef unit ? unit.Layer : MoveLayer.Ground;

    public static bool IsAir(World world, GameContent content, int id) => Of(world, content, id) == MoveLayer.Air;

    public static bool CanHit(WeaponDef weapon, MoveLayer layer) => (weapon.Targets & MaskOf(layer)) != 0;

    /// <summary>
    /// Whether an attack order on the target makes sense for this unit. A unit without a weapon may follow ground
    /// targets, as before layers existed, but not air ones.
    /// </summary>
    public static bool CanTarget(World world, GameContent content, int attacker, int target)
    {
        MoveLayer layer = Of(world, content, target);
        if (world.Weapons.TryGet(attacker, out WeaponRef weapon) && content.WeaponOrNull(weapon.Id) is WeaponDef def)
        {
            return CanHit(def, layer);
        }

        return layer == MoveLayer.Ground;
    }

    public static LayerMask MaskOf(MoveLayer layer) => layer == MoveLayer.Air ? LayerMask.Air : LayerMask.Ground;
}
