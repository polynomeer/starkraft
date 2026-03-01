package starkraft.sim.ecs

import starkraft.sim.data.DataRepo

class BuildingProductionSystem(
    private val world: World,
    private val map: MapGrid,
    private val occ: OccupancyGrid,
    private val data: DataRepo,
    private val resources: ResourceSystem? = null
) {
    private var buildingIds = IntArray(16)

    fun enqueue(buildingId: EntityId, typeId: String, buildTicks: Int, mineralCost: Int = 0, gasCost: Int = 0): Boolean {
        if (!world.footprints.containsKey(buildingId)) return false
        if (buildTicks <= 0) return false
        data.unit(typeId)
        if (resources != null) {
            val faction = world.tags[buildingId]?.faction ?: return false
            if (!resources.spend(faction, mineralCost, gasCost)) return false
        }
        val queue = world.productionQueues.getOrPut(buildingId) { ProductionQueue() }
        queue.items.addLast(ProductionJob(typeId, buildTicks))
        return true
    }

    fun tick() {
        var count = 0
        for (id in world.productionQueues.keys) {
            if (count >= buildingIds.size) {
                buildingIds = buildingIds.copyOf(buildingIds.size * 2)
            }
            buildingIds[count++] = id
        }
        for (i in 0 until count) {
            val id = buildingIds[i]
            val queue = world.productionQueues[id] ?: continue
            val job = queue.items.firstOrNull() ?: continue
            job.remainingTicks--
            if (job.remainingTicks > 0) continue
            val footprint = world.footprints[id] ?: continue
            val spawnTile = findSpawnTile(footprint) ?: continue
            val tag = world.tags[id] ?: continue
            val def = data.unit(job.typeId)
            val weapon = def.weaponId?.let { WeaponRef(it) }
            val unitId = world.spawn(
                Transform(spawnTile.first + 0.5f, spawnTile.second + 0.5f),
                UnitTag(tag.faction, job.typeId),
                Health(def.hp, def.hp, def.armor),
                weapon
            )
            world.visions[unitId] = Vision(6f)
            queue.items.removeFirst()
            if (queue.items.isEmpty()) {
                world.productionQueues.remove(id)
            }
        }
    }

    private fun findSpawnTile(footprint: BuildingFootprint): Pair<Int, Int>? {
        val minX = footprint.tileX - 1
        val minY = footprint.tileY - 1
        val maxX = footprint.tileX + footprint.width
        val maxY = footprint.tileY + footprint.height
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val onBorder = x == minX || x == maxX || y == minY || y == maxY
                if (!onBorder) continue
                if (!map.inBounds(x, y)) continue
                if (!map.isPassable(x, y)) continue
                if (occ.isBlocked(x, y)) continue
                return x to y
            }
        }
        return null
    }
}
