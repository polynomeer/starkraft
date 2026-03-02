package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.World

class ResourceSystemTest {
    @Test
    fun `spend and refund stockpile deterministically`() {
        val world = World()
        val resources = ResourceSystem(world)

        resources.set(faction = 1, minerals = 150, gas = 25)

        assertTrue(resources.canAfford(1, minerals = 100, gas = 20))
        assertTrue(resources.spend(1, minerals = 100, gas = 20))
        assertEquals(50, world.stockpiles[1]?.minerals)
        assertEquals(5, world.stockpiles[1]?.gas)

        assertFalse(resources.spend(1, minerals = 60, gas = 0))
        resources.refund(1, minerals = 25, gas = 10)
        assertEquals(75, world.stockpiles[1]?.minerals)
        assertEquals(15, world.stockpiles[1]?.gas)
    }

    @Test
    fun `records spend and refund events per tick`() {
        val world = World()
        val resources = ResourceSystem(world)

        resources.set(faction = 1, minerals = 200, gas = 50)
        resources.clearTickEvents()

        assertTrue(resources.spend(1, minerals = 75, gas = 20))
        resources.refund(1, minerals = 25, gas = 5)

        assertEquals(2, resources.lastTickEventCount)
        assertEquals(ResourceSystem.EVENT_SPEND, resources.eventKind(0))
        assertEquals(1, resources.eventFaction(0))
        assertEquals(75, resources.eventMinerals(0))
        assertEquals(20, resources.eventGas(0))
        assertEquals(ResourceSystem.EVENT_REFUND, resources.eventKind(1))
        assertEquals(25, resources.eventMinerals(1))
        assertEquals(5, resources.eventGas(1))
    }
}
