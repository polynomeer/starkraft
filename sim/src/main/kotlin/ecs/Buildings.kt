package starkraft.sim.ecs

enum class BuildFailureReason {
    INVALID_FOOTPRINT,
    MISSING_TECH,
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
    fun canPlace(tileX: Int, tileY: Int, width: Int, height: Int, clearance: Int = 0): Boolean {
        if (width <= 0 || height <= 0) return false
        val minX = tileX - clearance
        val minY = tileY - clearance
        val maxX = tileX + width + clearance - 1
        val maxY = tileY + height + clearance - 1
        for (y in minY..maxY) {
            for (x in minX..maxX) {
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
        clearance: Int = 0,
        armor: Int = 0,
        mineralCost: Int = 0,
        gasCost: Int = 0,
        requiredBuildingTypes: List<String> = emptyList()
    ): EntityId? {
        return placeResult(
            faction,
            typeId,
            tileX,
            tileY,
            width,
            height,
            hp,
            clearance,
            armor,
            mineralCost,
            gasCost,
            requiredBuildingTypes
        ).entityId
    }

    fun placeResult(
        faction: Int,
        typeId: String,
        tileX: Int,
        tileY: Int,
        width: Int,
        height: Int,
        hp: Int,
        clearance: Int = 0,
        armor: Int = 0,
        mineralCost: Int = 0,
        gasCost: Int = 0,
        requiredBuildingTypes: List<String> = emptyList()
    ): BuildPlacementResult {
        if (width <= 0 || height <= 0 || hp <= 0) return BuildPlacementResult(failure = BuildFailureReason.INVALID_FOOTPRINT)
        if (missingRequiredBuildings(world, faction, requiredBuildingTypes).isNotEmpty()) {
            return BuildPlacementResult(failure = BuildFailureReason.MISSING_TECH)
        }
        if (!canPlace(tileX, tileY, width, height, clearance)) return BuildPlacementResult(failure = BuildFailureReason.INVALID_PLACEMENT)
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
        world.footprints[id] = BuildingFootprint(tileX, tileY, width, height, clearance)
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
