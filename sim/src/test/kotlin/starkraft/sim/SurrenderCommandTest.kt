package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MatchEndReason
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

class SurrenderCommandTest {
    @Test
    fun `surrender ends match with opposing faction winner`() {
        val world = World()
        world.spawn(Transform(2f, 2f), UnitTag(1, "Marine"), Health(45, 45), null)
        world.spawn(Transform(10f, 10f), UnitTag(2, "Zergling"), Health(35, 35), null)

        issue(
            cmd = Command.SurrenderFaction(tick = 1, faction = 1),
            world = world,
            recorder = NullRecorder()
        )

        assertTrue(world.matchEnded)
        assertEquals(2, world.winnerFaction)
        assertEquals(MatchEndReason.SURRENDER, world.matchEndReason)
    }

    @Test
    fun `invalid surrender faction does not end match`() {
        val world = World()
        world.spawn(Transform(2f, 2f), UnitTag(1, "Marine"), Health(45, 45), null)
        world.spawn(Transform(10f, 10f), UnitTag(2, "Zergling"), Health(35, 35), null)

        issue(
            cmd = Command.SurrenderFaction(tick = 1, faction = 9),
            world = world,
            recorder = NullRecorder()
        )

        assertFalse(world.matchEnded)
        assertEquals(null, world.winnerFaction)
        assertEquals(null, world.matchEndReason)
    }
}
