package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            Command.Attack(10, intArrayOf(2), 7),
            Command.Spawn(20, 1, "Marine", 3f, 4f, 6f, label = "alpha", labelId = -1)
        )
        ReplayIO.save(tmp, cmds)
        val loaded = ReplayIO.load(tmp)
        val payload = Files.readString(tmp)
        assertTrue(payload.contains("\"replayHash\""))
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
        is Command.Spawn -> {
            require(b is Command.Spawn)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.vision, b.vision)
            assertEquals(a.label, b.label)
            assertEquals(a.labelId, b.labelId)
        }
    }
}
