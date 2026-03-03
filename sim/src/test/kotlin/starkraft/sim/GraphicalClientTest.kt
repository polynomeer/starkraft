package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.client.defaultClientInputPath
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.formatAckStatus
import java.nio.file.Paths

class GraphicalClientTest {
    @Test
    fun `default client input path is adjacent to snapshot file`() {
        val snapshotPath = Paths.get("/tmp/starkraft/live/snapshots.ndjson")

        assertEquals(
            Paths.get("/tmp/starkraft/live/client-input.ndjson"),
            defaultClientInputPath(snapshotPath)
        )
    }

    @Test
    fun `formats command ack status for hud`() {
        assertEquals("last ack: none", formatAckStatus(null))
        assertEquals(
            "last ack: ok move @12",
            formatAckStatus(ClientCommandAck(tick = 12, commandType = "move", accepted = true))
        )
        assertEquals(
            "last ack: fail build @13 reason=missingTech",
            formatAckStatus(ClientCommandAck(tick = 13, commandType = "build", accepted = false, reason = "missingTech"))
        )
    }
}
