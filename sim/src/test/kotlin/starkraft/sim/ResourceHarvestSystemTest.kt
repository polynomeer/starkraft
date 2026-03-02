package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.Harvester
import starkraft.sim.ecs.Health
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
    @Test
    fun `harvests nearby minerals into faction stockpile`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        resources.set(1, minerals = 100, gas = 0)

        val nodeId = world.spawn(Transform(4f, 4f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 10)
        val workerId = world.spawn(Transform(4.5f, 4f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId, harvestPerTick = 3)

        harvest.tick()

        assertEquals(103, world.stockpiles[1]?.minerals)
        assertEquals(7, world.resourceNodes[nodeId]?.remaining)
        assertEquals(3, harvest.lastTickHarvestedMinerals)
        assertEquals(0, harvest.lastTickHarvestedGas)
        assertEquals(3, harvest.lastTickHarvestedMineralsFaction1)
        assertEquals(0, harvest.lastTickHarvestedMineralsFaction2)
        assertEquals(0, harvest.lastTickDepletedNodes)
        assertEquals(1, harvest.lastTickEventCount)
        assertEquals(nodeId, harvest.eventNodeId(0))
        assertEquals(3, harvest.eventHarvested(0))
        assertEquals(7, harvest.eventRemaining(0))
    }

    @Test
    fun `stops at depletion and ignores distant workers`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        resources.set(1, minerals = 0, gas = 0)

        val nodeId = world.spawn(Transform(8f, 8f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 5)
        val nearWorker = world.spawn(Transform(8.2f, 8f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val farWorker = world.spawn(Transform(20f, 20f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.harvesters[nearWorker] = Harvester(targetNodeId = nodeId, harvestPerTick = 4)
        world.harvesters[farWorker] = Harvester(targetNodeId = nodeId, harvestPerTick = 4)

        harvest.tick()
        harvest.tick()
        harvest.tick()

        assertEquals(5, world.stockpiles[1]?.minerals)
        assertEquals(0, world.resourceNodes[nodeId]?.remaining)
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
        val workerId = world.spawn(Transform(6.4f, 6f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        val labelMap = HashMap<String, Int>()
        val labelIds = HashMap<Int, Int>()

        issue(Command.SpawnNode(0, "MineralField", 6f, 6f, 12, "ore", -1), world, recorder, labelMap = labelMap, labelIdMap = labelIds)
        val nodeId = labelMap.getValue("ore")
        issue(Command.Harvest(0, intArrayOf(workerId), nodeId), world, recorder)
        harvest.tick()

        assertEquals(nodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(Order.Move(6f, 6f), world.orders[workerId]?.items?.firstOrNull())
        assertEquals(11, world.stockpiles[1]?.minerals)
        assertEquals(11, world.resourceNodes[nodeId]?.remaining)
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
