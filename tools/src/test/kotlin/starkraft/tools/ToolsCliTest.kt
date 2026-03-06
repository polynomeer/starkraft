package starkraft.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.pathString

class ToolsCliTest {
    @Test
    fun `replay meta command succeeds for saved replay`() {
        val replayPath = Files.createTempFile("starkraft-tools-meta", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f)),
            seed = 42L,
            mapId = "test-map",
            buildVersion = "test-build"
        )

        val code = runToolsCli(arrayOf("replay", "meta", replayPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `replay verify command succeeds for hash-matching replay`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f))
        )

        val code = runToolsCli(arrayOf("replay", "verify", replayPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `replay verify strict hash rejects legacy replay array`() {
        val replayPath = Files.createTempFile("starkraft-tools-legacy", ".json")
        Files.writeString(
            replayPath,
            """
            [{"type":"move","tick":1,"units":[1],"x":2.0,"y":3.0}]
            """.trimIndent()
        )

        val code = runToolsCli(arrayOf("replay", "verify", replayPath.pathString, "--strictHash"))
        assertEquals(2, code)
    }

    @Test
    fun `replay fast-forward command succeeds`() {
        val replayPath = Files.createTempFile("starkraft-tools-ff", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f))
        )
        val code = runToolsCli(arrayOf("replay", "fast-forward", replayPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `resolve path returns absolute path`() {
        val resolved = resolvePath("sim/scripts/sample.script")
        assertTrue(resolved.isAbsolute)
    }

    @Test
    fun `map validate command succeeds for valid map`() {
        val mapPath = Files.createTempFile("starkraft-tools-map", ".json")
        Files.writeString(
            mapPath,
            """{"schema":1,"id":"demo","width":4,"height":4}"""
        )
        val code = runToolsCli(arrayOf("map", "validate", mapPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `map generate command writes output file`() {
        val mapPath = Files.createTempFile("starkraft-tools-map-gen", ".json")
        Files.deleteIfExists(mapPath)
        val code = runToolsCli(arrayOf("map", "generate", mapPath.pathString, "--width", "7", "--height", "6", "--seed", "11"))
        assertEquals(0, code)
        assertTrue(mapPath.exists())
    }

    @Test
    fun `data validate command succeeds for sim data`() {
        val code = runToolsCli(arrayOf("data", "validate", "--dir", "sim/src/main/resources/data"))
        assertEquals(0, code)
    }
}
