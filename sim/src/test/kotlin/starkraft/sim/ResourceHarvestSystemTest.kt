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
        assertEquals(0, harvest.lastTickDepletedNodes)
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

        val nodeId = world.spawn(Transform(6f, 6f), UnitTag(1, "MineralField"), Health(1000, 1000), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 12)
        val workerId = world.spawn(Transform(6.4f, 6f), UnitTag(1, "Worker"), Health(40, 40), w = null)

        issue(Command.Harvest(0, intArrayOf(workerId), nodeId), world, recorder)
        harvest.tick()

        assertEquals(nodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(Order.Move(6f, 6f), world.orders[workerId]?.items?.firstOrNull())
        assertEquals(11, world.stockpiles[1]?.minerals)
        assertEquals(11, world.resourceNodes[nodeId]?.remaining)
    }
}
