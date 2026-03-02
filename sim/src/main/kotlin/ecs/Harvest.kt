package starkraft.sim.ecs

class ResourceHarvestSystem(
    private val world: World,
    private val resources: ResourceSystem
) {
    var lastTickHarvestedMinerals: Int = 0
        private set
    var lastTickHarvestedGas: Int = 0
        private set
    var lastTickDepletedNodes: Int = 0
        private set

    fun tick() {
        lastTickHarvestedMinerals = 0
        lastTickHarvestedGas = 0
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
                }
                else -> {
                    resources.stockpile(workerTag.faction).minerals += harvested
                    lastTickHarvestedMinerals += harvested
                }
            }
            if (node.remaining == 0) {
                lastTickDepletedNodes++
            }
        }
    }
}
