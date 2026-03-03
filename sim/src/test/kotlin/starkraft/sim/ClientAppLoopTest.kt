package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.ClientAppLoop
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.ClientSession
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.ClientStreamState
import starkraft.sim.client.ClientStreamSubscription
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.NdjsonClientInputSink
import java.nio.file.Path

class ClientAppLoopTest {
    @Test
    fun `loop only notifies on session changes`(@TempDir tempDir: Path) {
        val inputPath = tempDir.resolve("client-input.ndjson")
        val updates =
            ArrayDeque(
                listOf(
                    ClientStreamState(
                        snapshot =
                            ClientSnapshot(
                                tick = 1,
                                mapId = "demo-map",
                                buildVersion = "test-build",
                                mapWidth = 16,
                                mapHeight = 16,
                                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 4)),
                                entities = listOf(EntitySnapshot(id = 7, faction = 1, typeId = "Worker", archetype = "worker", x = 2f, y = 3f, dir = 0f, hp = 20, maxHp = 20, armor = 0)),
                                resourceNodes = emptyList()
                            )
                    ),
                    ClientStreamState(),
                    ClientStreamState(
                        ack = ClientCommandAck(tick = 2, commandType = "move", requestId = "req-2", accepted = true)
                    )
                )
            )
        val session =
            ClientSession(
                subscription = TestSubscription(updates),
                inputSink = NdjsonClientInputSink(inputPath),
                state = ClientSessionState()
            )
        var notifications = 0
        val loop = ClientAppLoop(session) { notifications++ }

        session.use {
            assertTrue(loop.tick())
            assertFalse(loop.tick())
            assertTrue(loop.tick())
        }

        assertEquals(2, notifications)
    }

    private class TestSubscription(
        private val updates: ArrayDeque<ClientStreamState>
    ) : ClientStreamSubscription {
        override fun poll(): ClientStreamState? = if (updates.isEmpty()) null else updates.removeFirst()
        override fun close() = Unit
    }
}
