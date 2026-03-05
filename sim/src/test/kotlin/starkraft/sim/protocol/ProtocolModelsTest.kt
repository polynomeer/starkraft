package starkraft.sim.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ProtocolModelsTest {
    private val codec = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `handshake envelope round trip matches golden`() {
        val envelope =
            ProtocolEnvelope(
                simVersion = "1.0.0",
                buildHash = "abc123",
                message = ProtocolMessage.Handshake(clientName = "bot-a", requestedRoom = "room-1")
            )

        val json = codec.encodeToString(envelope)
        val golden = loadSharedGolden("shared-protocol/golden/v1-handshake-envelope.json")

        assertEquals(golden, json)
        assertEquals(envelope, codec.decodeFromString<ProtocolEnvelope>(json))
    }

    @Test
    fun `command batch envelope round trip matches golden`() {
        val envelope =
            ProtocolEnvelope(
                simVersion = "1.0.0",
                message =
                    ProtocolMessage.CommandBatch(
                        tick = 120,
                        commands =
                            listOf(
                                WireCommand(commandType = "move", requestId = "req-1"),
                                WireCommand(commandType = "attack", requestId = "req-2")
                            )
                    )
            )

        val json = codec.encodeToString(envelope)
        val golden = loadSharedGolden("shared-protocol/golden/v1-command-batch-envelope.json")

        assertEquals(golden, json)
        assertEquals(envelope, codec.decodeFromString<ProtocolEnvelope>(json))
    }

    @Test
    fun `compatibility matrix reports expected action`() {
        assertEquals(ProtocolCompatibility.COMPATIBLE, protocolCompatibility(1, 1))
        assertEquals(ProtocolCompatibility.UPGRADE_CLIENT, protocolCompatibility(1, 2))
        assertEquals(ProtocolCompatibility.UPGRADE_SERVER, protocolCompatibility(2, 1))
    }

    @Test
    fun `snapshot envelope round trip matches golden`() {
        val envelope =
            ProtocolEnvelope(
                simVersion = "1.0.0",
                message = ProtocolMessage.Snapshot(tick = 480, worldHash = 123456789L)
            )

        val json = codec.encodeToString(envelope)
        val golden = loadSharedGolden("shared-protocol/golden/v1-snapshot-envelope.json")

        assertEquals(golden, json)
        assertEquals(envelope, codec.decodeFromString<ProtocolEnvelope>(json))
    }

    @Test
    fun `schema file exists for protocol v1`() {
        val cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize()
        val direct = cwd.resolve("shared-protocol/schema/rts-protocol-v1.schema.json").normalize()
        val fromModule = cwd.resolve("../shared-protocol/schema/rts-protocol-v1.schema.json").normalize()
        assertTrue(
            java.nio.file.Files.exists(direct) || java.nio.file.Files.exists(fromModule),
            "missing shared-protocol schema"
        )
    }

    private fun loadSharedGolden(relativePath: String): String {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val direct = cwd.resolve(relativePath).normalize()
        val fromModule = cwd.resolve("../$relativePath").normalize()
        val selected: Path =
            when {
                Files.exists(direct) -> direct
                Files.exists(fromModule) -> fromModule
                else -> error("missing golden file: $relativePath")
            }
        return Files.readString(selected).trim()
    }
}
