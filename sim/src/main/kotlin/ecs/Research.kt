package starkraft.sim.ecs

import starkraft.sim.data.DataRepo

enum class ResearchFailureReason {
    MISSING_BUILDING,
    UNDER_CONSTRUCTION,
    INVALID_TECH,
    MISSING_TECH,
    INCOMPATIBLE_PRODUCER,
    INSUFFICIENT_RESOURCES,
    ALREADY_UNLOCKED,
    QUEUE_FULL,
    NOTHING_TO_CANCEL
}

class ResearchSystem(
    private val world: World,
    private val data: DataRepo,
    private val resources: ResourceSystem? = null
) {
    private var buildingIds = IntArray(16)
    private var eventKinds = ByteArray(16)
    private var eventBuildingIds = IntArray(16)
    private var eventRemainingTicks = IntArray(16)
    private var eventTechIds = arrayOfNulls<String>(16)
    var lastTickEventCount: Int = 0
        private set

    fun clearTickEvents() {
        lastTickEventCount = 0
    }

    fun enqueueResult(
        buildingId: EntityId,
        techId: String,
        buildTicks: Int,
        mineralCost: Int = 0,
        gasCost: Int = 0
    ): ResearchFailureReason? {
        if (!world.footprints.containsKey(buildingId)) return ResearchFailureReason.MISSING_BUILDING
        if (world.constructionSites.containsKey(buildingId)) return ResearchFailureReason.UNDER_CONSTRUCTION
        val tag = world.tags[buildingId] ?: return ResearchFailureReason.MISSING_BUILDING
        if (world.unlockedTechs(tag.faction).contains(techId)) return ResearchFailureReason.ALREADY_UNLOCKED
        if (buildTicks <= 0) return ResearchFailureReason.INVALID_TECH
        val spec = data.researchSpec(techId) ?: return ResearchFailureReason.INVALID_TECH
        val buildingSpec = data.buildSpec(tag.typeId) ?: return ResearchFailureReason.MISSING_BUILDING
        if (!buildingSpec.supportsResearch) return ResearchFailureReason.INCOMPATIBLE_PRODUCER
        if (spec.producerTypes.isNotEmpty() && !spec.producerTypes.contains(tag.typeId)) {
            return ResearchFailureReason.INCOMPATIBLE_PRODUCER
        }
        if (missingRequiredBuildings(world, tag.faction, spec.requiredBuildingTypes).isNotEmpty()) {
            return ResearchFailureReason.MISSING_TECH
        }
        if (missingRequiredResearch(world, tag.faction, spec.requiredResearchIds).isNotEmpty()) {
            return ResearchFailureReason.MISSING_TECH
        }
        val queueLimit = buildingSpec.productionQueueLimit
        val queue = world.researchQueues[buildingId]
        if (queue != null && queue.items.size >= queueLimit) return ResearchFailureReason.QUEUE_FULL
        if (resources != null && !resources.spend(tag.faction, mineralCost, gasCost)) {
            return ResearchFailureReason.INSUFFICIENT_RESOURCES
        }
        val actualQueue = queue ?: ResearchQueue().also { world.researchQueues[buildingId] = it }
        actualQueue.items.addLast(ResearchJob(techId, buildTicks, mineralCost, gasCost))
        recordEvent(EVENT_ENQUEUE, buildingId, techId, buildTicks)
        return null
    }

    fun tick() {
        var count = 0
        for (id in world.researchQueues.keys) {
            if (count >= buildingIds.size) {
                buildingIds = buildingIds.copyOf(buildingIds.size * 2)
            }
            buildingIds[count++] = id
        }
        for (i in 0 until count) {
            val id = buildingIds[i]
            val queue = world.researchQueues[id] ?: continue
            val job = queue.items.firstOrNull() ?: continue
            job.remainingTicks--
            if (job.remainingTicks > 0) {
                recordEvent(EVENT_PROGRESS, id, job.techId, job.remainingTicks)
                continue
            }
            val faction = world.tags[id]?.faction ?: continue
            world.unlockTech(faction, job.techId)
            recordEvent(EVENT_COMPLETE, id, job.techId, 0)
            queue.items.removeFirst()
            if (queue.items.isEmpty()) {
                world.researchQueues.remove(id)
            }
        }
    }

    fun cancelLast(buildingId: EntityId): ResearchFailureReason? {
        if (!world.footprints.containsKey(buildingId)) return ResearchFailureReason.MISSING_BUILDING
        val queue = world.researchQueues[buildingId] ?: return ResearchFailureReason.NOTHING_TO_CANCEL
        val job = queue.items.removeLastOrNull() ?: return ResearchFailureReason.NOTHING_TO_CANCEL
        if (resources != null) {
            val faction = world.tags[buildingId]?.faction ?: return ResearchFailureReason.MISSING_BUILDING
            resources.refund(faction, job.mineralCost, job.gasCost)
        }
        recordEvent(EVENT_CANCEL, buildingId, job.techId, job.remainingTicks)
        if (queue.items.isEmpty()) {
            world.researchQueues.remove(buildingId)
        }
        return null
    }

    fun eventKind(index: Int): Byte = eventKinds[index]

    fun eventBuildingId(index: Int): Int = eventBuildingIds[index]

    fun eventRemainingTicks(index: Int): Int = eventRemainingTicks[index]

    fun eventTechId(index: Int): String? = eventTechIds[index]

    private fun recordEvent(kind: Byte, buildingId: Int, techId: String, remainingTicks: Int) {
        val index = lastTickEventCount
        if (index >= eventKinds.size) {
            val nextSize = eventKinds.size * 2
            eventKinds = eventKinds.copyOf(nextSize)
            eventBuildingIds = eventBuildingIds.copyOf(nextSize)
            eventRemainingTicks = eventRemainingTicks.copyOf(nextSize)
            eventTechIds = eventTechIds.copyOf(nextSize)
        }
        eventKinds[index] = kind
        eventBuildingIds[index] = buildingId
        eventRemainingTicks[index] = remainingTicks
        eventTechIds[index] = techId
        lastTickEventCount = index + 1
    }

    companion object {
        const val EVENT_ENQUEUE: Byte = 1
        const val EVENT_PROGRESS: Byte = 2
        const val EVENT_COMPLETE: Byte = 3
        const val EVENT_CANCEL: Byte = 4
    }
}
