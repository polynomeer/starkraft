package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files

class ReplayIOTest {
    @Test
    fun roundTripCommands() {
        val tmp = Files.createTempFile("starkraft-replay", ".json")
        val cmds = listOf(
            Command.Move(0, intArrayOf(1, 2, 3), 5.5f, 6.5f),
            Command.Attack(10, intArrayOf(2), 7)
        )
        ReplayIO.save(tmp, cmds)
        val loaded = ReplayIO.load(tmp)
        assertEquals(cmds.size, loaded.size)
        for (i in cmds.indices) {
            assertCommandsEqual(cmds[i], loaded[i])
        }
    }
}

private fun assertCommandsEqual(a: Command, b: Command) {
    when (a) {
        is Command.Move -> {
            require(b is Command.Move)
            assertEquals(a.tick, b.tick)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.Attack -> {
            require(b is Command.Attack)
            assertEquals(a.tick, b.tick)
            assertEquals(a.target, b.target)
            assertEquals(a.units.toList(), b.units.toList())
        }
    }
}
