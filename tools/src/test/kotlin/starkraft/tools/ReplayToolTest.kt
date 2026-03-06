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

    @Test
    fun `verify ndjson keyframe hashes passes for matching hash`() {
        val replayPath = Files.createTempFile("starkraft-tools-ndjson-ok", ".jsonl")
        val tick = 3
        val hash = tick.toLong() * 1469598103934665603L + 31L
        Files.writeString(
            replayPath,
            """
            {"recordType":"header","protocolVersion":1}
            {"recordType":"keyframe","tick":$tick,"worldHash":$hash,"units":[]}
            """.trimIndent()
        )
        val result = verifyNdjsonKeyframeHashes(replayPath)
        assertTrue(result.keyframesChecked == 1)
        assertTrue(result.mismatches == 0)
    }

    @Test
    fun `verify ndjson keyframe hashes detects mismatch`() {
        val replayPath = Files.createTempFile("starkraft-tools-ndjson-bad", ".jsonl")
        Files.writeString(
            replayPath,
            """
            {"recordType":"header","protocolVersion":1}
            {"recordType":"keyframe","tick":2,"worldHash":123,"units":[]}
            """.trimIndent()
        )
        val result = verifyNdjsonKeyframeHashes(replayPath)
        assertTrue(result.keyframesChecked == 1)
        assertTrue(result.mismatches == 1)
    }
}
