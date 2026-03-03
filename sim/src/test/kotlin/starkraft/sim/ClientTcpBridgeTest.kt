package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.ClientTcpBridge
import starkraft.sim.client.openClientInputSink
import starkraft.sim.client.openClientStreamSubscription
import starkraft.sim.net.InputJson
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

class ClientTcpBridgeTest {
    @Test
    fun `tcp bridge relays snapshots and command input`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")
        val snapshotPort = ServerSocket(0).use { it.localPort }
        val inputPort = ServerSocket(0).use { it.localPort }

        ClientTcpBridge(snapshotPath, inputPath, snapshotPort, inputPort, pollMs = 5L).use { bridge ->
            bridge.start()

            val subscription = openClientStreamSubscription("tcp://127.0.0.1:$snapshotPort")
            val sink = openClientInputSink("tcp://127.0.0.1:$inputPort")
            subscription.use {
                sink.use {
                    Files.writeString(
                        snapshotPath,
                        "{\"recordType\":\"commandAck\",\"tick\":11,\"commandType\":\"move\",\"requestId\":\"bridge-1\",\"accepted\":true,\"reason\":null}\n"
                    )

                    var update = subscription.poll()
                    repeat(20) {
                        if (update != null) return@repeat
                        Thread.sleep(10)
                        update = subscription.poll()
                    }
                    assertEquals("bridge-1", update?.ack?.requestId)

                    sink.append(
                        InputJson.InputCommandRecord(
                            tick = 12,
                            commandType = "move",
                            requestId = "bridge-2",
                            units = intArrayOf(4),
                            x = 9f,
                            y = 10f
                        )
                    )

                    repeat(20) {
                        if (Files.exists(inputPath) && Files.readString(inputPath).contains("\"requestId\":\"bridge-2\"")) return@repeat
                        Thread.sleep(10)
                    }
                    assertTrue(Files.readString(inputPath).contains("\"requestId\":\"bridge-2\""))
                }
            }
        }
    }
}
