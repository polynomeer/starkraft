package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.Harvester
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.BuildingFootprint
import starkraft.sim.ecs.ResourceHarvestSystem
import starkraft.sim.ecs.ResourceNode
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.ecs.Order
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayHashRecorder

class ResourceHarvestSystemTest {
    private fun harvestData(): DataRepo =
        DataRepo(
            """{"list":[{"id":"Worker","archetype":"worker","hp":40,"buildTicks":30,"producerTypes":["Depot"]}]}""",
            """{"list":[]}""",
            """
            {"list":[
              {"id":"Depot","archetype":"producer","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"supportsDropoff":true,"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0},
              {"id":"ResourceDepot","archetype":"econDepot","hp":350,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"supportsDropoff":true,"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":75,"gasCost":0},
              {"id":"Tower","archetype":"defense","hp":300,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}
            ]}
            """.trimIndent()
        )

    @Test
    fun `harvests nearby minerals into faction stockpile`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        resources.set(1, minerals = 100, gas = 0)

        val depotId = world.spawn(Transform(6f, 4f), UnitTag(1, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(5, 3, 2, 2)
        val nodeId = world.spawn(Transform(4f, 4f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 10)
        val workerId = world.spawn(Transform(4.5f, 4f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId, harvestPerTick = 3)

        harvest.tick()
        assertEquals(100, world.stockpiles[1]?.minerals)
        assertEquals(1, harvest.lastTickCycleEventCount)
        assertEquals(ResourceHarvestSystem.EVENT_PICKUP, harvest.cycleEventKind(0))
        assertEquals(workerId, harvest.cycleEventWorker(0))
        assertEquals(nodeId, harvest.cycleEventNode(0))
        assertEquals(depotId, harvest.cycleEventDropoff(0))
        assertEquals("minerals", harvest.cycleEventResourceKind(0))
        assertEquals(3, harvest.cycleEventAmount(0))
        world.transforms[workerId]?.x = 6f
        world.transforms[workerId]?.y = 4f
        harvest.tick()

        assertEquals(103, world.stockpiles[1]?.minerals)
        assertEquals(1, harvest.lastTickCycleEventCount)
        assertEquals(ResourceHarvestSystem.EVENT_DEPOSIT, harvest.cycleEventKind(0))
        assertEquals(workerId, harvest.cycleEventWorker(0))
        assertEquals(nodeId, harvest.cycleEventNode(0))
        assertEquals(depotId, harvest.cycleEventDropoff(0))
        assertEquals("minerals", harvest.cycleEventResourceKind(0))
        assertEquals(3, harvest.cycleEventAmount(0))
        assertEquals(7, world.resourceNodes[nodeId]?.remaining)
        assertEquals(0, harvest.lastTickHarvestedMinerals)
        assertEquals(0, harvest.lastTickHarvestedGas)
        assertEquals(0, harvest.lastTickHarvestedMineralsFaction1)
        assertEquals(0, harvest.lastTickHarvestedMineralsFaction2)
        assertEquals(0, harvest.lastTickDepletedNodes)
        assertEquals(0, harvest.lastTickEventCount)
    }

    @Test
    fun `stops at depletion and ignores distant workers`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        resources.set(1, minerals = 0, gas = 0)

        val depotId = world.spawn(Transform(10f, 8f), UnitTag(1, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(9, 7, 2, 2)
        val nodeId = world.spawn(Transform(8f, 8f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 5)
        val nearWorker = world.spawn(Transform(8.2f, 8f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val farWorker = world.spawn(Transform(20f, 20f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[nearWorker] = Harvester(targetNodeId = nodeId, harvestPerTick = 4)
        world.harvesters[farWorker] = Harvester(targetNodeId = nodeId, harvestPerTick = 4)

        harvest.tick()
        world.transforms[nearWorker]?.x = 10f
        world.transforms[nearWorker]?.y = 8f
        harvest.tick()
        harvest.tick()

        assertEquals(4, world.stockpiles[1]?.minerals)
        assertEquals(1, world.resourceNodes[nodeId]?.remaining)
        assertEquals(0, harvest.lastTickHarvestedMinerals)
        assertEquals(0, harvest.lastTickHarvestedGas)
        assertEquals(0, harvest.lastTickDepletedNodes)
    }

    @Test
    fun `harvest command assigns workers to a resource node`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val recorder = ReplayHashRecorder()
        resources.set(1, minerals = 10, gas = 0)
        val depotId = world.spawn(Transform(7f, 6f), UnitTag(1, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(6, 5, 2, 2)
        val workerId = world.spawn(Transform(6.4f, 6f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val labelMap = HashMap<String, Int>()
        val labelIds = HashMap<Int, Int>()

        issue(Command.SpawnNode(0, "MineralField", 6f, 6f, 12, "ore", -1), world, recorder, labelMap = labelMap, labelIdMap = labelIds)
        val nodeId = labelMap.getValue("ore")
        issue(Command.Harvest(0, intArrayOf(workerId), nodeId), world, recorder)
        harvest.tick()
        world.transforms[workerId]?.x = 7f
        world.transforms[workerId]?.y = 6f
        harvest.tick()

        assertEquals(nodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(Order.Move(6f, 6f), world.orders[workerId]?.items?.firstOrNull())
        assertEquals(11, world.stockpiles[1]?.minerals)
        assertEquals(11, world.resourceNodes[nodeId]?.remaining)
    }

    @Test
    fun `harvest command collects gas into gas stockpile`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val recorder = ReplayHashRecorder()
        resources.set(2, minerals = 0, gas = 4)
        val depotId = world.spawn(Transform(8f, 6f), UnitTag(2, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(7, 5, 2, 2)
        val workerId = world.spawn(Transform(7.4f, 6f), UnitTag(2, "Worker"), Health(40, 40), w = null)
        val labelMap = HashMap<String, Int>()
        val labelIds = HashMap<Int, Int>()

        issue(Command.SpawnNode(0, "GasGeyser", 7f, 6f, 12, "geyser", -1), world, recorder, labelMap = labelMap, labelIdMap = labelIds)
        val nodeId = labelMap.getValue("geyser")
        issue(Command.Harvest(0, intArrayOf(workerId), nodeId), world, recorder)
        harvest.tick()
        world.transforms[workerId]?.x = 8f
        world.transforms[workerId]?.y = 6f
        harvest.tick()

        assertEquals(nodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(Order.Move(7f, 6f), world.orders[workerId]?.items?.firstOrNull())
        assertEquals(5, world.stockpiles[2]?.gas)
        assertEquals(0, harvest.lastTickHarvestedGasFaction2)
        assertEquals(11, world.resourceNodes[nodeId]?.remaining)
    }

    @Test
    fun `harvest prefers producer dropoff buildings over nearest footprint`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources, harvestData())

        val towerId = world.spawn(Transform(5f, 4f), UnitTag(1, "Tower"), Health(300, 300), w = null)
        world.footprints[towerId] = BuildingFootprint(4, 3, 2, 2)
        val depotId = world.spawn(Transform(8f, 4f), UnitTag(1, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(7, 3, 2, 2)
        val nodeId = world.spawn(Transform(4f, 4f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 10)
        val workerId = world.spawn(Transform(4.5f, 4f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId, harvestPerTick = 2)

        harvest.tick()

        assertEquals(depotId, world.harvesters[workerId]?.returnTargetId)
        assertEquals(Order.Move(8f, 4f), world.orders[workerId]?.items?.firstOrNull())
        assertEquals(1, harvest.lastTickCycleEventCount)
        assertEquals(ResourceHarvestSystem.EVENT_PICKUP, harvest.cycleEventKind(0))
        assertEquals(depotId, harvest.cycleEventDropoff(0))
    }

    @Test
    fun `harvest uses explicit resource depot dropoff`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources, harvestData())

        val depotId = world.spawn(Transform(9f, 4f), UnitTag(1, "Depot"), Health(400, 400), w = null)
        world.footprints[depotId] = BuildingFootprint(8, 3, 2, 2)
        val resourceDepotId = world.spawn(Transform(6f, 4f), UnitTag(1, "ResourceDepot"), Health(350, 350), w = null)
        world.footprints[resourceDepotId] = BuildingFootprint(5, 3, 2, 2)
        val nodeId = world.spawn(Transform(4f, 4f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 10)
        val workerId = world.spawn(Transform(4.5f, 4f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId, harvestPerTick = 2)

        harvest.tick()

        assertEquals(resourceDepotId, world.harvesters[workerId]?.returnTargetId)
        assertEquals(Order.Move(6f, 4f), world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `aggregates multiple workers on one node into one event`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val nodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 10)
        val workerA = world.spawn(Transform(5.5f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val workerB = world.spawn(Transform(5.2f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[workerA] = Harvester(targetNodeId = nodeId, harvestPerTick = 2)
        world.harvesters[workerB] = Harvester(targetNodeId = nodeId, harvestPerTick = 3)

        harvest.tick()

        assertEquals(1, harvest.lastTickEventCount)
        assertEquals(nodeId, harvest.eventNodeId(0))
        assertEquals(5, harvest.eventHarvested(0))
        assertEquals(5, harvest.eventRemaining(0))
        assertEquals(false, harvest.eventDepleted(0))
    }

    @Test
    fun `tracks harvest totals by faction`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val mineralNode = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val gasNode = world.spawn(Transform(7f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        world.resourceNodes[mineralNode] = ResourceNode(remaining = 10)
        world.resourceNodes[gasNode] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 10)
        val worker1 = world.spawn(Transform(5.3f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val worker2 = world.spawn(Transform(7.2f, 5f), UnitTag(2, "Worker"), Health(40, 40), w = null)
        world.harvesters[worker1] = Harvester(targetNodeId = mineralNode, harvestPerTick = 2)
        world.harvesters[worker2] = Harvester(targetNodeId = gasNode, harvestPerTick = 3)

        harvest.tick()

        assertEquals(2, harvest.lastTickHarvestedMineralsFaction1)
        assertEquals(0, harvest.lastTickHarvestedMineralsFaction2)
        assertEquals(0, harvest.lastTickHarvestedGasFaction1)
        assertEquals(3, harvest.lastTickHarvestedGasFaction2)
    }
}
