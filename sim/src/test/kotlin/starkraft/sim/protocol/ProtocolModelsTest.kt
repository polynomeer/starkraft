package starkraft.sim.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        val golden =
            """{"protocolVersion":1,"simVersion":"1.0.0","buildHash":"abc123","message":{"type":"handshake","clientName":"bot-a","requestedRoom":"room-1"}}"""

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
        val golden =
            """{"protocolVersion":1,"simVersion":"1.0.0","message":{"type":"commandBatch","tick":120,"commands":[{"commandType":"move","requestId":"req-1"},{"commandType":"attack","requestId":"req-2"}]}}"""

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
    fun `schema file exists for protocol v1`() {
        val cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize()
        val direct = cwd.resolve("shared-protocol/schema/rts-protocol-v1.schema.json").normalize()
        val fromModule = cwd.resolve("../shared-protocol/schema/rts-protocol-v1.schema.json").normalize()
        assertTrue(
            java.nio.file.Files.exists(direct) || java.nio.file.Files.exists(fromModule),
            "missing shared-protocol schema"
        )
    }
}
