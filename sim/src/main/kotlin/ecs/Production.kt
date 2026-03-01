package starkraft.sim.ecs

import starkraft.sim.data.DataRepo

enum class TrainFailureReason {
    MISSING_BUILDING,
    INVALID_UNIT,
    INVALID_BUILD_TIME,
    INCOMPATIBLE_PRODUCER,
    INSUFFICIENT_RESOURCES,
    QUEUE_FULL
}

class BuildingProductionSystem(
    private val world: World,
    private val map: MapGrid,
    private val occ: OccupancyGrid,
    private val data: DataRepo,
    private val resources: ResourceSystem? = null
) {
    private val maxQueueSize = 5
    private var buildingIds = IntArray(16)
    private var eventKinds = ByteArray(16)
    private var eventBuildingIds = IntArray(16)
    private var eventRemainingTicks = IntArray(16)
    private var eventSpawnedIds = IntArray(16)
    private var eventTypeIds = arrayOfNulls<String>(16)
    var lastTickEventCount: Int = 0
        private set

    fun clearTickEvents() {
        lastTickEventCount = 0
    }

    fun enqueue(buildingId: EntityId, typeId: String, buildTicks: Int, mineralCost: Int = 0, gasCost: Int = 0): Boolean {
        return enqueueResult(buildingId, typeId, buildTicks, mineralCost, gasCost) == null
    }

    fun enqueueResult(
        buildingId: EntityId,
        typeId: String,
        buildTicks: Int,
        mineralCost: Int = 0,
        gasCost: Int = 0
    ): TrainFailureReason? {
        if (!world.footprints.containsKey(buildingId)) return TrainFailureReason.MISSING_BUILDING
        if (buildTicks <= 0) return TrainFailureReason.INVALID_BUILD_TIME
        val unit =
            try {
                data.unit(typeId)
            } catch (_: NoSuchElementException) {
                return TrainFailureReason.INVALID_UNIT
            }
        val buildingType = world.tags[buildingId]?.typeId ?: return TrainFailureReason.MISSING_BUILDING
        if (unit.producerTypes.isNotEmpty() && !unit.producerTypes.contains(buildingType)) {
            return TrainFailureReason.INCOMPATIBLE_PRODUCER
        }
        val queue = world.productionQueues[buildingId]
        if (queue != null && queue.items.size >= maxQueueSize) return TrainFailureReason.QUEUE_FULL
        if (resources != null) {
            val faction = world.tags[buildingId]?.faction ?: return TrainFailureReason.MISSING_BUILDING
            if (!resources.spend(faction, mineralCost, gasCost)) return TrainFailureReason.INSUFFICIENT_RESOURCES
        }
        val actualQueue = queue ?: ProductionQueue().also { world.productionQueues[buildingId] = it }
        actualQueue.items.addLast(ProductionJob(typeId, buildTicks, mineralCost, gasCost))
        recordEvent(EVENT_ENQUEUE, buildingId, typeId, buildTicks, 0)
        return null
    }

    fun cancelLast(buildingId: EntityId): Boolean {
        val queue = world.productionQueues[buildingId] ?: return false
        val job = queue.items.removeLastOrNull() ?: return false
        if (resources != null) {
            val faction = world.tags[buildingId]?.faction ?: return false
            resources.refund(faction, job.mineralCost, job.gasCost)
        }
        recordEvent(EVENT_CANCEL, buildingId, job.typeId, job.remainingTicks, 0)
        if (queue.items.isEmpty()) {
            world.productionQueues.remove(buildingId)
        }
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
            if (job.remainingTicks > 0) {
                recordEvent(EVENT_PROGRESS, id, job.typeId, job.remainingTicks, 0)
                continue
            }
            job.remainingTicks = 0
            val footprint = world.footprints[id] ?: continue
            val spawnTile = findSpawnTile(footprint)
            if (spawnTile == null) {
                recordEvent(EVENT_PROGRESS, id, job.typeId, 0, 0)
                continue
            }
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
            recordEvent(EVENT_COMPLETE, id, job.typeId, 0, unitId)
            queue.items.removeFirst()
            if (queue.items.isEmpty()) {
                world.productionQueues.remove(id)
            }
        }
    }

    fun eventKind(index: Int): Byte = eventKinds[index]

    fun eventBuildingId(index: Int): Int = eventBuildingIds[index]

    fun eventRemainingTicks(index: Int): Int = eventRemainingTicks[index]

    fun eventSpawnedId(index: Int): Int = eventSpawnedIds[index]

    fun eventTypeId(index: Int): String? = eventTypeIds[index]

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

    private fun recordEvent(kind: Byte, buildingId: Int, typeId: String, remainingTicks: Int, spawnedId: Int) {
        val index = lastTickEventCount
        if (index >= eventKinds.size) {
            val nextSize = eventKinds.size * 2
            eventKinds = eventKinds.copyOf(nextSize)
            eventBuildingIds = eventBuildingIds.copyOf(nextSize)
            eventRemainingTicks = eventRemainingTicks.copyOf(nextSize)
            eventSpawnedIds = eventSpawnedIds.copyOf(nextSize)
            eventTypeIds = eventTypeIds.copyOf(nextSize)
        }
        eventKinds[index] = kind
        eventBuildingIds[index] = buildingId
        eventRemainingTicks[index] = remainingTicks
        eventSpawnedIds[index] = spawnedId
        eventTypeIds[index] = typeId
        lastTickEventCount = index + 1
    }

    companion object {
        const val EVENT_ENQUEUE: Byte = 1
        const val EVENT_PROGRESS: Byte = 2
        const val EVENT_COMPLETE: Byte = 3
        const val EVENT_CANCEL: Byte = 4
    }
}
