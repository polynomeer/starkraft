package starkraft.sim.ecs

import starkraft.sim.data.DataRepo

class ResourceHarvestSystem(
    private val world: World,
    private val resources: ResourceSystem,
    private val data: DataRepo? = null
) {
    private var eventNodeIds = IntArray(8)
    private var eventHarvested = IntArray(8)
    private var eventRemaining = IntArray(8)
    private var eventDepleted = BooleanArray(8)
    private var cycleEventKinds = ByteArray(8)
    private var cycleEventWorkers = IntArray(8)
    private var cycleEventNodes = IntArray(8)
    private var cycleEventDropoffs = IntArray(8)
    private var cycleEventKindsCarried = arrayOfNulls<String>(8)
    private var cycleEventAmounts = IntArray(8)
    var lastTickEventCount: Int = 0
        private set
    var lastTickCycleEventCount: Int = 0
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
    var lastTickPickupCount: Int = 0
        private set
    var lastTickDepositCount: Int = 0
        private set
    var lastTickPickupAmount: Int = 0
        private set
    var lastTickDepositAmount: Int = 0
        private set

    fun tick() {
        lastTickEventCount = 0
        lastTickCycleEventCount = 0
        lastTickHarvestedMinerals = 0
        lastTickHarvestedGas = 0
        lastTickHarvestedMineralsFaction1 = 0
        lastTickHarvestedMineralsFaction2 = 0
        lastTickHarvestedGasFaction1 = 0
        lastTickHarvestedGasFaction2 = 0
        lastTickDepletedNodes = 0
        lastTickPickupCount = 0
        lastTickDepositCount = 0
        lastTickPickupAmount = 0
        lastTickDepositAmount = 0
        for ((entityId, harvester) in world.harvesters) {
            val workerTag = world.tags[entityId] ?: continue
            val workerTransform = world.transforms[entityId] ?: continue
            if (harvester.cargoAmount > 0) {
                tickReturnTrip(entityId, workerTag.faction, workerTransform, harvester)
                continue
            }

            val nodeId = harvester.targetNodeId
            if (nodeId < 0) continue
            val nodeTransform = world.transforms[nodeId] ?: continue
            val node = world.resourceNodes[nodeId] ?: continue
            if (node.remaining <= 0) continue
            val dx = nodeTransform.x - workerTransform.x
            val dy = nodeTransform.y - workerTransform.y
            val rangeSq = harvester.range * harvester.range
            if ((dx * dx) + (dy * dy) > rangeSq) {
                ensureLeadingMove(entityId, nodeTransform.x, nodeTransform.y)
                continue
            }
            val harvested = minOf(harvester.harvestPerTick, harvester.cargoCapacity, node.remaining)
            if (harvested <= 0) continue
            node.remaining -= harvested
            harvester.cargoKind = node.kind
            harvester.cargoAmount = harvested
            harvester.returnTargetId = findNearestDropoff(entityId, workerTag.faction)
            when (node.kind) {
                ResourceNode.KIND_GAS -> {
                    lastTickHarvestedGas += harvested
                    when (workerTag.faction) {
                        1 -> lastTickHarvestedGasFaction1 += harvested
                        2 -> lastTickHarvestedGasFaction2 += harvested
                    }
                }
                else -> {
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
            val returnTransform = world.transforms[harvester.returnTargetId]
            if (returnTransform != null) {
                ensureLeadingMove(entityId, returnTransform.x, returnTransform.y)
            }
            recordEvent(nodeId, harvested, node.remaining, node.remaining == 0)
            recordCycleEvent(EVENT_PICKUP, entityId, nodeId, harvester.returnTargetId, node.kind, harvested)
            lastTickPickupCount++
            lastTickPickupAmount += harvested
        }
    }

    fun eventNodeId(index: Int): Int = eventNodeIds[index]

    fun eventHarvested(index: Int): Int = eventHarvested[index]

    fun eventRemaining(index: Int): Int = eventRemaining[index]

    fun eventDepleted(index: Int): Boolean = eventDepleted[index]

    fun cycleEventKind(index: Int): Byte = cycleEventKinds[index]

    fun cycleEventWorker(index: Int): Int = cycleEventWorkers[index]

    fun cycleEventNode(index: Int): Int = cycleEventNodes[index]

    fun cycleEventDropoff(index: Int): Int = cycleEventDropoffs[index]

    fun cycleEventResourceKind(index: Int): String? = cycleEventKindsCarried[index]

    fun cycleEventAmount(index: Int): Int = cycleEventAmounts[index]

    private fun tickReturnTrip(entityId: Int, faction: Int, workerTransform: Transform, harvester: Harvester) {
        val returnId =
            if (harvester.returnTargetId >= 0 && world.footprints.containsKey(harvester.returnTargetId)) {
                harvester.returnTargetId
            } else {
                findNearestDropoff(entityId, faction)
            }
        harvester.returnTargetId = returnId
        if (returnId < 0) {
            return
        }
        val returnTransform = world.transforms[returnId] ?: return
        val dx = returnTransform.x - workerTransform.x
        val dy = returnTransform.y - workerTransform.y
        val rangeSq = harvester.dropoffRange * harvester.dropoffRange
        if ((dx * dx) + (dy * dy) > rangeSq) {
            ensureLeadingMove(entityId, returnTransform.x, returnTransform.y)
            return
        }
        val cargoKind = harvester.cargoKind
        val cargoAmount = harvester.cargoAmount
        when (cargoKind) {
            ResourceNode.KIND_GAS -> {
                resources.stockpile(faction).gas += cargoAmount
            }
            else -> {
                resources.stockpile(faction).minerals += cargoAmount
            }
        }
        recordCycleEvent(EVENT_DEPOSIT, entityId, harvester.targetNodeId, returnId, cargoKind, cargoAmount)
        lastTickDepositCount++
        lastTickDepositAmount += cargoAmount
        harvester.cargoKind = null
        harvester.cargoAmount = 0
        harvester.returnTargetId = -1
        val nextNodeId = harvester.targetNodeId
        if (nextNodeId >= 0) {
            val nextNode = world.resourceNodes[nextNodeId]
            val nextTransform = world.transforms[nextNodeId]
            if (nextNode != null && nextNode.remaining > 0 && nextTransform != null) {
                ensureLeadingMove(entityId, nextTransform.x, nextTransform.y)
            }
        }
    }

    private fun ensureLeadingMove(entityId: Int, x: Float, y: Float) {
        val queue = world.orders[entityId]?.items ?: return
        val first = queue.firstOrNull()
        if (first is Order.Move) {
            if (first.tx == x && first.ty == y) return
            queue.removeFirst()
        }
        queue.addFirst(Order.Move(x, y))
    }

    private fun findNearestDropoff(entityId: Int, faction: Int): Int {
        val workerTransform = world.transforms[entityId] ?: return -1
        var bestProducerId = -1
        var bestProducerDist = Float.POSITIVE_INFINITY
        var bestAnyId = -1
        var bestAnyDist = Float.POSITIVE_INFINITY
        for ((buildingId, footprint) in world.footprints) {
            val tag = world.tags[buildingId] ?: continue
            if (tag.faction != faction) continue
            val buildingTransform = world.transforms[buildingId] ?: continue
            val dx = buildingTransform.x - workerTransform.x
            val dy = buildingTransform.y - workerTransform.y
            val dist = (dx * dx) + (dy * dy)
            if (dist < bestAnyDist) {
                bestAnyDist = dist
                bestAnyId = buildingId
            }
            val buildSpec = data?.buildSpec(tag.typeId)
            if (buildSpec?.supportsDropoff == true && dist < bestProducerDist) {
                bestProducerDist = dist
                bestProducerId = buildingId
            }
        }
        return if (bestProducerId >= 0) bestProducerId else bestAnyId
    }

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

    private fun recordCycleEvent(
        kind: Byte,
        workerId: Int,
        nodeId: Int,
        dropoffId: Int,
        resourceKind: String?,
        amount: Int
    ) {
        val index = lastTickCycleEventCount
        if (index >= cycleEventKinds.size) {
            val nextSize = cycleEventKinds.size * 2
            cycleEventKinds = cycleEventKinds.copyOf(nextSize)
            cycleEventWorkers = cycleEventWorkers.copyOf(nextSize)
            cycleEventNodes = cycleEventNodes.copyOf(nextSize)
            cycleEventDropoffs = cycleEventDropoffs.copyOf(nextSize)
            cycleEventKindsCarried = cycleEventKindsCarried.copyOf(nextSize)
            cycleEventAmounts = cycleEventAmounts.copyOf(nextSize)
        }
        cycleEventKinds[index] = kind
        cycleEventWorkers[index] = workerId
        cycleEventNodes[index] = nodeId
        cycleEventDropoffs[index] = dropoffId
        cycleEventKindsCarried[index] = resourceKind
        cycleEventAmounts[index] = amount
        lastTickCycleEventCount = index + 1
    }

    companion object {
        const val EVENT_PICKUP: Byte = 1
        const val EVENT_DEPOSIT: Byte = 2
    }
}
