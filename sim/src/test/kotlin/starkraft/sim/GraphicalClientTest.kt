package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import starkraft.sim.client.applySelectionClick
import starkraft.sim.client.buildUnitSelectionRecord
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
            "last ack: ok move[cli-1] @12",
            formatAckStatus(ClientCommandAck(tick = 12, commandType = "move", requestId = "cli-1", accepted = true))
        )
        assertEquals(
            "last ack: fail build @13 reason=missingTech",
            formatAckStatus(ClientCommandAck(tick = 13, commandType = "build", accepted = false, reason = "missingTech"))
        )
    }

    @Test
    fun `builds unit selection records for client input`() {
        val record = buildUnitSelectionRecord(8, linkedSetOf(4, 9))

        assertEquals(8, record.tick)
        assertEquals("units", record.selectionType)
        assertArrayEquals(intArrayOf(4, 9), record.units)
    }

    @Test
    fun `applies additive selection clicks`() {
        val selected = linkedSetOf(4, 9)

        applySelectionClick(selected, clickedId = 12, additive = true)
        assertEquals(linkedSetOf(4, 9, 12), selected)

        applySelectionClick(selected, clickedId = 9, additive = true)
        assertEquals(linkedSetOf(4, 12), selected)

        applySelectionClick(selected, clickedId = 7, additive = false)
        assertEquals(linkedSetOf(7), selected)

        applySelectionClick(selected, clickedId = null, additive = false)
        assertEquals(linkedSetOf<Int>(), selected)
    }
}
