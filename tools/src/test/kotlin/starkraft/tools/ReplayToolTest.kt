package starkraft.tools

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files

class ReplayToolTest {
    @Test
    fun `fast-forward replay returns non-zero hash`() {
        val replayPath = Files.createTempFile("starkraft-tools-fast-forward", ".json")
        ReplayIO.save(
            replayPath,
            listOf(
                Command.Move(tick = 0, units = intArrayOf(1, 2), x = 8f, y = 8f),
                Command.Move(tick = 4, units = intArrayOf(3), x = 12f, y = 10f)
            )
        )
        val result = fastForwardReplay(replayPath, tickLimit = null)
        assertTrue(result.commandCount == 2)
        assertTrue(result.finalWorldHash != 0L)
    }
}
