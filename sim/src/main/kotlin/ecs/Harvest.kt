package starkraft.sim.ecs

class ResourceHarvestSystem(
    private val world: World,
    private val resources: ResourceSystem
) {
    private var eventNodeIds = IntArray(8)
    private var eventHarvested = IntArray(8)
    private var eventRemaining = IntArray(8)
    private var eventDepleted = BooleanArray(8)
    var lastTickEventCount: Int = 0
        private set
    var lastTickHarvestedMinerals: Int = 0
        private set
    var lastTickHarvestedGas: Int = 0
        private set
    var lastTickHarvestedMineralsFaction1: Int = 0
        private set
    var lastTickHarvestedMineralsFaction2: Int = 0
        private set
    var lastTickHarvestedGasFaction1: Int = 0
        private set
    var lastTickHarvestedGasFaction2: Int = 0
        private set
    var lastTickDepletedNodes: Int = 0
        private set

    fun tick() {
        lastTickEventCount = 0
        lastTickHarvestedMinerals = 0
        lastTickHarvestedGas = 0
        lastTickHarvestedMineralsFaction1 = 0
        lastTickHarvestedMineralsFaction2 = 0
        lastTickHarvestedGasFaction1 = 0
        lastTickHarvestedGasFaction2 = 0
        lastTickDepletedNodes = 0
        for ((entityId, harvester) in world.harvesters) {
            val workerTag = world.tags[entityId] ?: continue
            val workerTransform = world.transforms[entityId] ?: continue
            val nodeTransform = world.transforms[harvester.targetNodeId] ?: continue
            val node = world.resourceNodes[harvester.targetNodeId] ?: continue
            if (node.remaining <= 0) continue
            val dx = nodeTransform.x - workerTransform.x
            val dy = nodeTransform.y - workerTransform.y
            val rangeSq = harvester.range * harvester.range
            if ((dx * dx) + (dy * dy) > rangeSq) continue
            val harvested = minOf(harvester.harvestPerTick, node.remaining)
            if (harvested <= 0) continue
            node.remaining -= harvested
            when (node.kind) {
                ResourceNode.KIND_GAS -> {
                    resources.stockpile(workerTag.faction).gas += harvested
                    lastTickHarvestedGas += harvested
                    when (workerTag.faction) {
                        1 -> lastTickHarvestedGasFaction1 += harvested
                        2 -> lastTickHarvestedGasFaction2 += harvested
                    }
                }
                else -> {
                    resources.stockpile(workerTag.faction).minerals += harvested
                    lastTickHarvestedMinerals += harvested
                    when (workerTag.faction) {
                        1 -> lastTickHarvestedMineralsFaction1 += harvested
                        2 -> lastTickHarvestedMineralsFaction2 += harvested
                    }
                }
            }
            if (node.remaining == 0) {
                lastTickDepletedNodes++
            }
            recordEvent(harvester.targetNodeId, harvested, node.remaining, node.remaining == 0)
        }
    }

    fun eventNodeId(index: Int): Int = eventNodeIds[index]

    fun eventHarvested(index: Int): Int = eventHarvested[index]

    fun eventRemaining(index: Int): Int = eventRemaining[index]

    fun eventDepleted(index: Int): Boolean = eventDepleted[index]

    private fun recordEvent(nodeId: Int, harvested: Int, remaining: Int, depleted: Boolean) {
        for (i in 0 until lastTickEventCount) {
            if (eventNodeIds[i] != nodeId) continue
            eventHarvested[i] += harvested
            eventRemaining[i] = remaining
            eventDepleted[i] = eventDepleted[i] || depleted
            return
        }
        val index = lastTickEventCount
        if (index >= eventNodeIds.size) {
            val nextSize = eventNodeIds.size * 2
            eventNodeIds = eventNodeIds.copyOf(nextSize)
            eventHarvested = eventHarvested.copyOf(nextSize)
            eventRemaining = eventRemaining.copyOf(nextSize)
            eventDepleted = eventDepleted.copyOf(nextSize)
        }
        eventNodeIds[index] = nodeId
        eventHarvested[index] = harvested
        eventRemaining[index] = remaining
        eventDepleted[index] = depleted
        lastTickEventCount = index + 1
    }
}
