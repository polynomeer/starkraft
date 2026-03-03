package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.ClientWebSocketBridge
import starkraft.sim.client.openClientInputSink
import starkraft.sim.client.openClientStreamSubscription
import starkraft.sim.net.InputJson
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

class ClientWebSocketBridgeTest {
    @Test
    fun `websocket bridge relays snapshots and command input`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")
        val snapshotPort = ServerSocket(0).use { it.localPort }
        val inputPort = ServerSocket(0).use { it.localPort }

        ClientWebSocketBridge(snapshotPath, inputPath, snapshotPort, inputPort, pollMs = 5L).use { bridge ->
            bridge.start()

            val subscription = openClientStreamSubscription("ws://127.0.0.1:$snapshotPort")
            val sink = openClientInputSink("ws://127.0.0.1:$inputPort")
            subscription.use {
                sink.use {
                    Files.writeString(
                        snapshotPath,
                        "{\"recordType\":\"commandAck\",\"tick\":13,\"commandType\":\"hold\",\"requestId\":\"ws-1\",\"accepted\":true,\"reason\":null}\n"
                    )

                    var update = subscription.poll()
                    repeat(30) {
                        if (update != null) return@repeat
                        Thread.sleep(10)
                        update = subscription.poll()
                    }
                    assertEquals("ws-1", update?.ack?.requestId)

                    sink.append(
                        InputJson.InputCommandRecord(
                            tick = 14,
                            commandType = "move",
                            requestId = "ws-2",
                            units = intArrayOf(5),
                            x = 12f,
                            y = 13f
                        )
                    )

                    repeat(30) {
                        if (Files.exists(inputPath) && Files.readString(inputPath).contains("\"requestId\":\"ws-2\"")) return@repeat
                        Thread.sleep(10)
                    }
                    assertTrue(Files.readString(inputPath).contains("\"requestId\":\"ws-2\""))
                }
            }
        }
    }
}
