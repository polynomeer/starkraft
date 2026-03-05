package starkraft.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files

class ReplayToolsTest {
    @Test
    fun `replay verify accepts matching replay hash`() {
        val replay = Files.createTempFile("starkraft-tools-replay", ".json")
        ReplayIO.save(
            replay,
            listOf(
                Command.Spawn(0, 1, "Marine", 2f, 2f),
                Command.Move(1, intArrayOf(1), 10f, 10f)
            )
        )

        val result = verifyReplay(replay)

        assertTrue(result.replayHashMatches)
        assertEquals(1, result.finalTick)
    }

    @Test
    fun `replay fast forward honors tick limit`() {
        val replay = Files.createTempFile("starkraft-tools-replay", ".json")
        ReplayIO.save(
            replay,
            listOf(
                Command.Spawn(0, 1, "Marine", 2f, 2f),
                Command.Move(1, intArrayOf(1), 10f, 10f),
                Command.Move(20, intArrayOf(1), 12f, 12f)
            )
        )

        val result = runReplay(replay, tickLimit = 5)

        assertEquals(4, result.finalTick)
    }
}
