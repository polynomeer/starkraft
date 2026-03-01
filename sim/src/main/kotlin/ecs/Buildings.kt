package starkraft.sim.ecs

enum class BuildFailureReason {
    INVALID_FOOTPRINT,
    INVALID_PLACEMENT,
    INSUFFICIENT_RESOURCES
}

data class BuildPlacementResult(
    val entityId: EntityId? = null,
    val failure: BuildFailureReason? = null
)

class BuildingPlacementSystem(
    private val world: World,
    private val map: MapGrid,
    private val occ: OccupancyGrid,
    private val resources: ResourceSystem? = null
) {
    fun canPlace(tileX: Int, tileY: Int, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        for (y in tileY until tileY + height) {
            for (x in tileX until tileX + width) {
                if (!map.inBounds(x, y)) return false
                if (!map.isPassable(x, y)) return false
                if (occ.isBlocked(x, y)) return false
            }
        }
        return true
    }

    fun place(
        faction: Int,
        typeId: String,
        tileX: Int,
        tileY: Int,
        width: Int,
        height: Int,
        hp: Int,
        armor: Int = 0,
        mineralCost: Int = 0,
        gasCost: Int = 0
    ): EntityId? {
        return placeResult(faction, typeId, tileX, tileY, width, height, hp, armor, mineralCost, gasCost).entityId
    }

    fun placeResult(
        faction: Int,
        typeId: String,
        tileX: Int,
        tileY: Int,
        width: Int,
        height: Int,
        hp: Int,
        armor: Int = 0,
        mineralCost: Int = 0,
        gasCost: Int = 0
    ): BuildPlacementResult {
        if (width <= 0 || height <= 0 || hp <= 0) return BuildPlacementResult(failure = BuildFailureReason.INVALID_FOOTPRINT)
        if (!canPlace(tileX, tileY, width, height)) return BuildPlacementResult(failure = BuildFailureReason.INVALID_PLACEMENT)
        if (resources != null && !resources.spend(faction, mineralCost, gasCost)) {
            return BuildPlacementResult(failure = BuildFailureReason.INSUFFICIENT_RESOURCES)
        }
        for (y in tileY until tileY + height) {
            for (x in tileX until tileX + width) {
                occ.addStatic(x, y)
            }
        }
        val centerX = tileX.toFloat() + width.toFloat() * 0.5f
        val centerY = tileY.toFloat() + height.toFloat() * 0.5f
        val id = world.spawn(Transform(centerX, centerY), UnitTag(faction, typeId), Health(hp, hp, armor), w = null)
        world.footprints[id] = BuildingFootprint(tileX, tileY, width, height)
        return BuildPlacementResult(entityId = id)
    }

    fun remove(id: EntityId, reason: String = "buildingRemoved"): Boolean {
        val footprint = world.footprints[id] ?: return false
        for (y in footprint.tileY until footprint.tileY + footprint.height) {
            for (x in footprint.tileX until footprint.tileX + footprint.width) {
                occ.removeStatic(x, y)
            }
        }
        world.remove(id, reason)
        return true
    }
}
