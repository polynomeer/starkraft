package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandIds
import starkraft.sim.client.ClientIntent
import starkraft.sim.client.applySelectionClick
import starkraft.sim.client.buildClientIntent
import starkraft.sim.client.buildUnitSelectionRecord
import starkraft.sim.client.defaultClientInputPath
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.formatAckStatus
import starkraft.sim.client.parseClientStreamLine
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.buildClientHudLines
import starkraft.sim.client.buildSelectionSummary
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.ResourceNodeSnapshot
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
    fun `builds shared hud lines for pluggable renderers`() {
        val snapshot =
            ClientSnapshot(
                tick = 15,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 9, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )
        assertEquals(
            listOf(
                "tick=15 selected=2",
                "selection: Marinex2",
                "last ack: ok move[cli-9] @15",
                "left: select   shift+left: add/remove   right: move/attack/harvest   ctrl+right: attackMove"
            ),
            buildClientHudLines(
                snapshot = snapshot,
                state = ClientSessionState(selectedIds = linkedSetOf(4, 9), lastAck = ClientCommandAck(tick = 15, commandType = "move", requestId = "cli-9", accepted = true))
            )
        )
    }

    @Test
    fun `builds selection summary with unit type counts`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 6, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection: Marinex2 Workerx1", buildSelectionSummary(snapshot, linkedSetOf(4, 5, 6)))
        assertEquals("selection: none", buildSelectionSummary(snapshot, emptySet()))
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

    @Test
    fun `parses client stream updates through shared bridge`() {
        val ack =
            parseClientStreamLine(
                "{\"recordType\":\"commandAck\",\"tick\":13,\"commandType\":\"move\",\"requestId\":\"cli-7\",\"accepted\":true,\"reason\":null}"
            )

        assertNotNull(ack)
        assertEquals("move", ack?.ack?.commandType)
        assertEquals("cli-7", ack?.ack?.requestId)
        assertNull(ack?.snapshot)
    }

    @Test
    fun `builds right click intents through shared controller`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities =
                    listOf(
                        EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                        EntitySnapshot(id = 9, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 6f, y = 4f, dir = 0f, hp = 35, maxHp = 35, armor = 0)
                    ),
                resourceNodes = listOf(ResourceNodeSnapshot(id = 20, kind = "MineralField", x = 8f, y = 4f, remaining = 200, yieldPerTick = 0))
            )
        val selected = linkedSetOf(4)
        val ids = ClientCommandIds("test")

        val attackIntent =
            buildClientIntent(snapshot, selected, 6f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, additiveSelection = false, requestIds = ids)
        val harvestIntent =
            buildClientIntent(snapshot, selected, 8f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, additiveSelection = false, requestIds = ids)
        val moveIntent =
            buildClientIntent(snapshot, selected, 10f, 10f, leftClick = false, rightClick = true, attackMoveModifier = false, additiveSelection = false, requestIds = ids)
        val attackMoveIntent =
            buildClientIntent(snapshot, selected, 11f, 10f, leftClick = false, rightClick = true, attackMoveModifier = true, additiveSelection = false, requestIds = ids)

        val attack = (attackIntent as ClientIntent.Command).record
        val harvest = (harvestIntent as ClientIntent.Command).record
        val move = (moveIntent as ClientIntent.Command).record
        val attackMove = (attackMoveIntent as ClientIntent.Command).record

        assertEquals("attack", attack.commandType)
        assertEquals("test-1", attack.requestId)
        assertEquals(9, attack.target)
        assertEquals("harvest", harvest.commandType)
        assertEquals("test-2", harvest.requestId)
        assertEquals(20, harvest.target)
        assertEquals("move", move.commandType)
        assertEquals("test-3", move.requestId)
        assertEquals(10f, move.x)
        assertEquals(10f, move.y)
        assertEquals("attackMove", attackMove.commandType)
        assertEquals("test-4", attackMove.requestId)
        assertEquals(11f, attackMove.x)
        assertEquals(10f, attackMove.y)
    }
}
