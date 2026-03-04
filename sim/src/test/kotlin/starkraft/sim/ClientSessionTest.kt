package starkraft.sim

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.ClientIntent
import starkraft.sim.client.ClientResearchActivity
import starkraft.sim.client.ClientSession
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.ClientStreamState
import starkraft.sim.client.ClientStreamSubscription
import starkraft.sim.client.ClientTickActivity
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FileClientInputSink
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.net.InputJson
import java.nio.file.Files
import java.nio.file.Path

class ClientSessionTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `poll updates session state and prunes dead selections`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)),
                resourceNodes = emptyList()
            )
        Files.writeString(
            snapshotPath,
            "{\"recordType\":\"snapshot\",\"snapshot\":${json.encodeToString(ClientSnapshot.serializer(), snapshot)}}\n" +
                "{\"recordType\":\"commandAck\",\"tick\":12,\"commandType\":\"move\",\"requestId\":\"cli-1\",\"accepted\":true,\"reason\":null}\n"
        )

        ClientSession(snapshotPath = snapshotPath, inputPath = inputPath, state = ClientSessionState(selectedIds = linkedSetOf(4, 9))).use { session ->
            assertTrue(session.poll())
            assertEquals(12, session.state.snapshot?.tick)
            assertEquals(linkedSetOf(4), session.state.selectedIds)
            assertEquals("move", session.state.lastAck?.commandType)
            assertEquals("cli-1", session.state.lastAck?.requestId)

            assertFalse(session.poll())
        }
    }

    @Test
    fun `session works with non-file stream subscriptions`(@TempDir tempDir: Path) {
        val inputPath = tempDir.resolve("client-input.ndjson")
        val snapshot =
            ClientSnapshot(
                tick = 3,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 16,
                mapHeight = 16,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 4)),
                entities = listOf(EntitySnapshot(id = 7, faction = 1, typeId = "Worker", archetype = "worker", x = 2f, y = 3f, dir = 0f, hp = 20, maxHp = 20, armor = 0)),
                resourceNodes = emptyList()
            )

        ClientSession(
            subscription =
                TestSubscription(
                    ArrayDeque(
                        listOf(
                            ClientStreamState(snapshot = snapshot),
                            ClientStreamState(ack = starkraft.sim.client.ClientCommandAck(tick = 4, commandType = "move", requestId = "req-1", accepted = true)),
                            ClientStreamState(researchActivity = ClientResearchActivity(tick = 5, enqueue = 1, cancel = 1)),
                            ClientStreamState(
                                tickActivity =
                                    ClientTickActivity(
                                        tick = 5,
                                        builds = 1,
                                        buildsCancelled = 1,
                                        buildFailures = 2,
                                        researchQueued = 1,
                                        researchCancelled = 1,
                                        researchCompleted = 0,
                                        researchFailures = 1
                                    )
                            )
                        )
                    )
                ),
            inputSink = FileClientInputSink(inputPath),
            state = ClientSessionState(selectedIds = linkedSetOf(7, 8))
        ).use { session ->
            assertTrue(session.poll())
            assertEquals(linkedSetOf(7), session.state.selectedIds)
            assertEquals(3, session.state.snapshot?.tick)

            assertTrue(session.poll())
            assertEquals("req-1", session.state.lastAck?.requestId)

            assertTrue(session.poll())
            assertEquals(1, session.state.lastResearchActivity?.enqueue)
            assertEquals(1, session.state.lastResearchActivity?.cancel)

            assertTrue(session.poll())
            assertEquals(1, session.state.lastTickActivity?.builds)
            assertEquals(1, session.state.lastTickActivity?.buildsCancelled)
            assertEquals(2, session.state.lastTickActivity?.buildFailures)
            assertEquals(1, session.state.lastTickActivity?.researchQueued)

            assertFalse(session.poll())
        }
    }

    @Test
    fun `append writes command records through shared sink`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")

        ClientSession(snapshotPath, inputPath).use { session ->
            session.append(
                ClientIntent.Command(
                    InputJson.InputCommandRecord(
                        tick = 7,
                        commandType = "move",
                        requestId = "cli-7",
                        units = intArrayOf(4, 9),
                        x = 10f,
                        y = 12f
                    )
                )
            )
        }

        val record = json.decodeFromString(InputJson.InputCommandRecord.serializer(), Files.readString(inputPath).trim())
        assertEquals(7, record.tick)
        assertEquals("move", record.commandType)
        assertEquals("cli-7", record.requestId)
        assertArrayEquals(intArrayOf(4, 9), record.units)
        assertEquals(10f, record.x)
        assertEquals(12f, record.y)
    }

    private class TestSubscription(
        private val updates: ArrayDeque<ClientStreamState>
    ) : ClientStreamSubscription {
        override fun poll(): ClientStreamState? = if (updates.isEmpty()) null else updates.removeFirst()
        override fun close() = Unit
    }
}
