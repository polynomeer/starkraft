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
}
