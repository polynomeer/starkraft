package starkraft.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

class ToolsOutputSchemaTest {
    @Test
    fun `json outputs satisfy schema examples required keys`() {
        val replayPath = Files.createTempFile("starkraft-tools-schema-replay", ".json")
        ReplayIO.save(
            replayPath,
            listOf(Command.Move(tick = 0, units = intArrayOf(1), x = 4f, y = 5f))
        )
        val mapPath = Files.createTempFile("starkraft-tools-schema-map", ".json")

        val commandOutputs =
            mapOf(
                "replay-meta" to runAndCapture("replay", "meta", replayPath.pathString, "--json"),
                "replay-stats" to runAndCapture("replay", "stats", replayPath.pathString, "--json"),
                "replay-verify" to runAndCapture("replay", "verify", replayPath.pathString, "--json"),
                "replay-fast-forward" to runAndCapture("replay", "fast-forward", replayPath.pathString, "--json"),
                "map-generate" to runAndCapture("map", "generate", mapPath.pathString, "--width", "8", "--height", "8", "--seed", "7", "--json"),
                "map-validate" to runAndCapture("map", "validate", mapPath.pathString, "--json"),
                "data-validate" to runAndCapture("data", "validate", "--dir", "sim/src/main/resources/data", "--json")
            )

        val schemaPath = repoRoot().resolve(Paths.get("tools", "schema", "tools-output-v1.schema.json"))
        val schemaRoot = Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject
        val examples = schemaRoot["examples"]!!.jsonArray
        for (example in examples) {
            val exampleObj = example.jsonObject
            val command = exampleObj["command"]!!.jsonPrimitive.content
            val output = commandOutputs[command] ?: continue
            assertEquals(1, output["outputVersion"]!!.jsonPrimitive.content.toInt())
            val required = exampleObj["requiredKeys"]!!.jsonArray
            assertHasKeys(command, output, required)
        }
    }

    private fun assertHasKeys(command: String, output: JsonObject, required: JsonArray) {
        for (k in required) {
            val key = k.jsonPrimitive.content
            assertTrue(output.containsKey(key), "missing key '$key' for command $command: $output")
        }
    }

    private fun runAndCapture(vararg args: String): JsonObject {
        val out = ByteArrayOutputStream()
        val original = System.out
        val code: Int
        try {
            System.setOut(PrintStream(out))
            code = runToolsCli(args.toList().toTypedArray())
        } finally {
            System.setOut(original)
        }
        assertEquals(0, code, "command failed: ${args.joinToString(" ")}")
        return Json.parseToJsonElement(out.toString().trim()).jsonObject
    }

    private fun repoRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        while (current.parent != null) {
            if (current.resolve("settings.gradle.kts").toFile().exists()) return current
            current = current.parent
        }
        return Paths.get("").toAbsolutePath().normalize()
    }
}
