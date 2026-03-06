package starkraft.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.pathString

class ToolsCliTest {
    @Test
    fun `replay json commands expose stable keys`() {
        val replayPath = Files.createTempFile("starkraft-tools-json-contract", ".json")
        ReplayIO.save(
            replayPath,
            listOf(
                Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f),
                Command.Move(tick = 1, units = intArrayOf(2), x = 5f, y = 6f)
            )
        )
        val metaJson = runAndCaptureJson("replay", "meta", replayPath.pathString, "--json")
        val statsJson = runAndCaptureJson("replay", "stats", replayPath.pathString, "--json")
        val verifyJson = runAndCaptureJson("replay", "verify", replayPath.pathString, "--json")
        val fastForwardJson = runAndCaptureJson("replay", "fast-forward", replayPath.pathString, "--json")

        assertTrue(metaJson.containsKey("schema"))
        assertTrue(metaJson.containsKey("events"))
        assertTrue(metaJson.containsKey("replayHash"))

        assertEquals("sim-json", statsJson["format"]?.toString()?.trim('"'))
        assertEquals("ok", statsJson["result"]?.toString()?.trim('"'))
        assertTrue(statsJson.containsKey("schema"))
        assertTrue(statsJson.containsKey("events"))

        assertEquals("ok", verifyJson["result"]?.toString()?.trim('"'))
        assertTrue(verifyJson.containsKey("expectedHash"))
        assertTrue(verifyJson.containsKey("computedHash"))
        assertTrue(verifyJson.containsKey("worldHash"))

        assertEquals("ok", fastForwardJson["result"]?.toString()?.trim('"'))
        assertTrue(fastForwardJson.containsKey("finalTick"))
        assertTrue(fastForwardJson.containsKey("commandCount"))
        assertTrue(fastForwardJson.containsKey("worldHash"))
    }

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
    fun `replay meta command accepts json output flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-meta-json", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f))
        )
        val code = runToolsCli(arrayOf("replay", "meta", replayPath.pathString, "--json"))
        assertEquals(0, code)
    }

    @Test
    fun `replay meta command rejects unknown flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-meta-flag", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f))
        )
        val code = runToolsCli(arrayOf("replay", "meta", replayPath.pathString, "--bad"))
        assertEquals(1, code)
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
    fun `replay verify accepts json output flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify-json", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f))
        )
        val code = runToolsCli(arrayOf("replay", "verify", replayPath.pathString, "--json"))
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
    fun `replay verify rejects unknown flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify-flag", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 1, units = intArrayOf(1), x = 2f, y = 3f))
        )
        val code = runToolsCli(arrayOf("replay", "verify", replayPath.pathString, "--bad"))
        assertEquals(1, code)
    }

    @Test
    fun `replay stats command succeeds for sim replay`() {
        val replayPath = Files.createTempFile("starkraft-tools-stats", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f))
        )
        val code = runToolsCli(arrayOf("replay", "stats", replayPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `replay stats command accepts json output flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-stats-json", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f))
        )
        val code = runToolsCli(arrayOf("replay", "stats", replayPath.pathString, "--json"))
        assertEquals(0, code)
    }

    @Test
    fun `replay stats command succeeds for server ndjson replay`() {
        val replayPath = Files.createTempFile("starkraft-tools-stats-ndjson", ".jsonl")
        Files.writeString(
            replayPath,
            """
            {"recordType":"header","protocolVersion":1}
            {"recordType":"command","ack":{"tick":1}}
            {"recordType":"keyframe","tick":1,"worldHash":1469598103934665634,"units":[]}
            {"recordType":"matchEnd","tick":2}
            """.trimIndent()
        )
        val code = runToolsCli(arrayOf("replay", "stats", replayPath.pathString))
        assertEquals(0, code)
    }

    @Test
    fun `replay stats command fails for server ndjson hash mismatch`() {
        val replayPath = Files.createTempFile("starkraft-tools-stats-ndjson-bad", ".jsonl")
        Files.writeString(
            replayPath,
            """
            {"recordType":"header","protocolVersion":1}
            {"recordType":"keyframe","tick":1,"worldHash":1,"units":[]}
            """.trimIndent()
        )
        val code = runToolsCli(arrayOf("replay", "stats", replayPath.pathString))
        assertEquals(2, code)
    }

    @Test
    fun `replay stats command rejects unknown flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-stats-bad-flag", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f))
        )
        val code = runToolsCli(arrayOf("replay", "stats", replayPath.pathString, "--bad"))
        assertEquals(1, code)
    }

    @Test
    fun `replay verify-ndjson command fails on keyframe hash mismatch`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify-ndjson", ".jsonl")
        Files.writeString(
            replayPath,
            """{"recordType":"keyframe","tick":2,"worldHash":1,"units":[]}"""
        )
        val code = runToolsCli(arrayOf("replay", "verify-ndjson", replayPath.pathString))
        assertEquals(2, code)
    }

    @Test
    fun `replay verify-ndjson accepts json output flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify-ndjson-json", ".jsonl")
        Files.writeString(
            replayPath,
            """{"recordType":"keyframe","tick":1,"worldHash":1469598103934665634,"units":[]}"""
        )
        val code = runToolsCli(arrayOf("replay", "verify-ndjson", replayPath.pathString, "--json"))
        assertEquals(0, code)
    }

    @Test
    fun `replay verify-ndjson rejects unknown flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-verify-ndjson-flag", ".jsonl")
        Files.writeString(
            replayPath,
            """{"recordType":"keyframe","tick":1,"worldHash":1469598103934665634,"units":[]}"""
        )
        val code = runToolsCli(arrayOf("replay", "verify-ndjson", replayPath.pathString, "--bad"))
        assertEquals(1, code)
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
    fun `replay fast-forward accepts json output flag`() {
        val replayPath = Files.createTempFile("starkraft-tools-ff-json", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 3f, y = 4f))
        )
        val code = runToolsCli(arrayOf("replay", "fast-forward", replayPath.pathString, "--json"))
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
    fun `map validate command accepts json output flag`() {
        val mapPath = Files.createTempFile("starkraft-tools-map-json", ".json")
        Files.writeString(
            mapPath,
            """{"schema":1,"id":"demo","width":4,"height":4}"""
        )
        val json = runAndCaptureJson("map", "validate", mapPath.pathString, "--json")
        assertEquals("ok", json["result"]?.toString()?.trim('"'))
        assertEquals("0", json["errors"]?.toString())
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
    fun `map generate command accepts json output flag`() {
        val mapPath = Files.createTempFile("starkraft-tools-map-gen-json", ".json")
        Files.deleteIfExists(mapPath)
        val json = runAndCaptureJson("map", "generate", mapPath.pathString, "--width", "7", "--height", "6", "--seed", "11", "--json")
        assertEquals("generated", json["result"]?.toString()?.trim('"'))
        assertTrue(mapPath.exists())
    }

    @Test
    fun `data validate command succeeds for sim data`() {
        val code = runToolsCli(arrayOf("data", "validate", "--dir", "sim/src/main/resources/data"))
        assertEquals(0, code)
    }

    @Test
    fun `data validate command accepts json output flag`() {
        val json = runAndCaptureJson("data", "validate", "--dir", "sim/src/main/resources/data", "--json")
        assertEquals("ok", json["result"]?.toString()?.trim('"'))
        assertEquals("0", json["errors"]?.toString())
    }

    @Test
    fun `map validate returns non-zero for missing file`() {
        val code = runToolsCli(arrayOf("map", "validate", "/tmp/no-such-map-${System.nanoTime()}.json"))
        assertEquals(2, code)
    }

    private fun runAndCaptureJson(vararg args: String): JsonObject {
        val originalOut = System.out
        val out = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(out))
            val code = runToolsCli(args.toList().toTypedArray())
            assertEquals(0, code)
        } finally {
            System.setOut(originalOut)
        }
        val text = out.toString().trim()
        return Json.parseToJsonElement(text).jsonObject
    }
}
