package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MatchEndReason
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.VictorySystem
import starkraft.sim.ecs.World

class VictorySystemTest {
    @Test
    fun `declares winner when only one faction has non-resource units`() {
        val world = World()
        val victory = VictorySystem(world)
        world.spawn(Transform(2f, 2f), UnitTag(1, "Marine"), Health(45, 45), null)
        world.spawn(Transform(3f, 2f), UnitTag(1, "MineralField"), Health(1000, 1000), null)

        victory.tick()

        assertTrue(world.matchEnded)
        assertEquals(1, world.winnerFaction)
        assertEquals(MatchEndReason.ELIMINATION, world.matchEndReason)
    }

    @Test
    fun `does not end while two factions still have combat units`() {
        val world = World()
        val victory = VictorySystem(world)
        world.spawn(Transform(2f, 2f), UnitTag(1, "Marine"), Health(45, 45), null)
        world.spawn(Transform(10f, 10f), UnitTag(2, "Zergling"), Health(35, 35), null)

        victory.tick()

        assertFalse(world.matchEnded)
        assertEquals(null, world.winnerFaction)
        assertEquals(null, world.matchEndReason)
    }

    @Test
    fun `declares draw when no combat factions remain`() {
        val world = World()
        val victory = VictorySystem(world)

        victory.tick()

        assertTrue(world.matchEnded)
        assertEquals(null, world.winnerFaction)
        assertEquals(MatchEndReason.DRAW, world.matchEndReason)
    }
}
