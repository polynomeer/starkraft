package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.ClientResearchActivity
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.ReaderClientStreamSubscription
import starkraft.sim.client.openClientInputSink
import starkraft.sim.client.openClientStreamSubscription
import starkraft.sim.client.renderClientTextFrame
import java.io.BufferedReader
import java.io.StringReader
import java.net.ServerSocket
import kotlin.concurrent.thread

class ConsoleClientTest {
    @Test
    fun `reader subscription parses newline-delimited client stream`() {
        val subscription =
            ReaderClientStreamSubscription(
                BufferedReader(
                    StringReader(
                        """
                        {"recordType":"snapshot","snapshot":{"tick":4,"mapId":"demo-map","buildVersion":"test-build","mapWidth":16,"mapHeight":16,"factions":[{"faction":1,"visibleTiles":6}],"entities":[{"id":7,"faction":1,"typeId":"Marine","archetype":"infantry","x":2.0,"y":3.0,"dir":0.0,"hp":45,"maxHp":45,"armor":0}],"resourceNodes":[]}}
                        {"recordType":"commandAck","tick":5,"commandType":"move","requestId":"cli-5","accepted":true,"reason":null}
                        """.trimIndent()
                    )
                )
            )

        subscription.use {
            val first = it.poll()
            val second = it.poll()
            val third = it.poll()

            assertEquals(4, first?.snapshot?.tick)
            assertEquals("move", second?.ack?.commandType)
            assertNull(third)
        }
    }

    @Test
    fun `console renderer summarizes current client state`() {
        val output =
            renderClientTextFrame(
                ClientSessionState(
                    snapshot =
                        ClientSnapshot(
                            tick = 9,
                            mapId = "demo-map",
                            buildVersion = "test-build",
                            mapWidth = 16,
                            mapHeight = 16,
                            factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 6), FactionSnapshot(faction = 2, visibleTiles = 4)),
                            entities =
                                listOf(
                                    EntitySnapshot(id = 7, faction = 1, typeId = "Marine", archetype = "infantry", x = 2f, y = 3f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                                    EntitySnapshot(
                                        id = 8,
                                        faction = 1,
                                        typeId = "Depot",
                                        archetype = "producer",
                                        x = 4f,
                                        y = 4f,
                                        dir = 0f,
                                        hp = 600,
                                        maxHp = 600,
                                        armor = 1,
                                        researchQueueSize = 2,
                                        activeResearchTech = "AdvancedTraining",
                                        activeResearchRemainingTicks = 8
                                    )
                                ),
                            resourceNodes = emptyList()
                        ),
                    selectedIds = linkedSetOf(7, 8),
                    lastAck = ClientCommandAck(tick = 9, commandType = "attackMove", requestId = "cli-5", accepted = true),
                    lastResearchActivity = ClientResearchActivity(tick = 9, enqueue = 1, progress = 2, cancel = 1)
                )
            )

        assertEquals(
            "tick=9 selected=2 entities=2 resources=0 visible[f1=6 f2=4] research: labs=1 queue=2 active=AdvancedTrainingx1 research events: e1/p2/c0/x1 @9 last ack: ok attackMove[cli-5] @9",
            output
        )
    }

    @Test
    fun `socket subscription and sink support tcp transport`() {
        ServerSocket(0).use { streamServer ->
            ServerSocket(0).use { inputServer ->
                val streamPort = streamServer.localPort
                val inputPort = inputServer.localPort
                val received = StringBuilder()

                val streamThread =
                    thread(start = true) {
                        streamServer.accept().use { socket ->
                            socket.getOutputStream().bufferedWriter().use { writer ->
                                writer.write("{\"recordType\":\"commandAck\",\"tick\":7,\"commandType\":\"move\",\"requestId\":\"sock-1\",\"accepted\":true,\"reason\":null}\n")
                                writer.flush()
                            }
                        }
                    }
                val inputThread =
                    thread(start = true) {
                        inputServer.accept().use { socket ->
                            socket.getInputStream().bufferedReader().use { reader ->
                                received.append(reader.readLine())
                            }
                        }
                    }

                val subscription = openClientStreamSubscription("tcp://127.0.0.1:$streamPort")
                val sink = openClientInputSink("tcp://127.0.0.1:$inputPort")
                subscription.use {
                    sink.use {
                        var update = subscription.poll()
                        while (update == null) {
                            update = subscription.poll()
                        }
                        assertEquals("sock-1", update.ack?.requestId)

                        sink.append(
                            starkraft.sim.net.InputJson.InputCommandRecord(
                                tick = 8,
                                commandType = "move",
                                requestId = "sock-2",
                                units = intArrayOf(4),
                                x = 10f,
                                y = 11f
                            )
                        )
                    }
                }

                streamThread.join()
                inputThread.join()
                assertTrue(received.contains("\"requestId\":\"sock-2\""))
                assertTrue(received.contains("\"commandType\":\"move\""))
            }
        }
    }
}
