package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.ClientConstructionActivity
import starkraft.sim.client.ClientProductionActivity
import starkraft.sim.client.ClientResearchActivity
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.ClientTickActivity
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
                            factions = listOf(
                                FactionSnapshot(faction = 1, visibleTiles = 6, minerals = 275, gas = 40, dropoffBuildings = 1, unlockedTechIds = listOf("AdvancedTraining")),
                                FactionSnapshot(faction = 2, visibleTiles = 4, minerals = 500, gas = 0, dropoffBuildings = 0)
                            ),
                            entities =
                                listOf(
                                    EntitySnapshot(id = 7, faction = 1, typeId = "Marine", archetype = "infantry", x = 2f, y = 3f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                                    EntitySnapshot(
                                        id = 9,
                                        faction = 1,
                                        typeId = "Worker",
                                        archetype = "worker",
                                        x = 3f,
                                        y = 3f,
                                        dir = 0f,
                                        hp = 20,
                                        maxHp = 20,
                                        armor = 0,
                                        buildTargetId = 8
                                    ),
                                    EntitySnapshot(
                                        id = 8,
                                        faction = 1,
                                        typeId = "Depot",
                                        archetype = "producer",
                                        x = 4f,
                                        y = 4f,
                                        dir = 0f,
                                        hp = 120,
                                        maxHp = 600,
                                        armor = 1,
                                        underConstruction = true,
                                        constructionRemainingTicks = 6,
                                        constructionTotalTicks = 10,
                                        productionQueueSize = 2,
                                        activeProductionType = "Marine",
                                        activeProductionRemainingTicks = 9,
                                        researchQueueSize = 2,
                                        activeResearchTech = "AdvancedTraining",
                                        activeResearchRemainingTicks = 8,
                                        supportsTraining = true,
                                        supportsResearch = true,
                                        supportsDropoff = true
                                    )
                                ),
                            resourceNodes = emptyList()
                        ),
                    selectedIds = linkedSetOf(7, 8, 9),
                    lastAck = ClientCommandAck(tick = 9, commandType = "attackMove", requestId = "cli-5", accepted = true),
                    lastConstructionActivity = ClientConstructionActivity(tick = 9, total = 2, faction1 = 2, faction2 = 0, remainingTicks = 10),
                    lastProductionActivity = ClientProductionActivity(tick = 9, enqueue = 1, progress = 2, cancel = 1),
                    lastResearchActivity = ClientResearchActivity(tick = 9, enqueue = 1, progress = 2, cancel = 1),
                    lastTickActivity =
                        ClientTickActivity(
                            tick = 9,
                            builds = 1,
                            buildsCancelled = 1,
                            buildFailures = 2,
                            buildFailureReasons = "invalidPlacement=1,insufficientResources=1",
                            trainsQueued = 2,
                            trainsCompleted = 1,
                            trainsCancelled = 1,
                            trainFailures = 1,
                            trainFailureReasons = "queueFull=1",
                            researchQueued = 1,
                            researchCancelled = 1,
                            researchFailures = 1,
                            researchFailureReasons = "invalidTech=1"
                        )
                )
            )

        assertEquals(
            "tick=9 view=f1 selected=3 entities=3 resources=0 visible[f1=6 f2=4] economy: f1 minerals=275 gas=40 dropoffs=1 selection hud: Marinex1 Workerx1 Depotx1 selection combat: armed=0 ready=0 cooling=0 unarmed=3 nextReady=0 capabilities: train=1 research=1 rally=0 dropoff=1 builders: active=1 targets=1 construction: sites=1 remaining=6 Depotx1 production: labs=1 queue=2 active=Marinex1 research: labs=1 queue=2 active=AdvancedTrainingx1 tech: AdvancedTrainingx1 activity: builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q2/c1/x1 trainFails=1[queueFull=1] research=q1/c0/x1 researchFails=1[invalidTech=1] @9 construction state: total=2 f1=2 f2=0 remaining=10 @9 production events: e1/p2/c0/x1 @9 research events: e1/p2/c0/x1 @9 last ack: ok attackMove[cli-5] @9",
            output
        )
    }

    @Test
    fun `console renderer reports observer view when no faction selected`() {
        val output =
            renderClientTextFrame(
                ClientSessionState(
                    snapshot =
                        ClientSnapshot(
                            tick = 4,
                            mapId = "demo-map",
                            buildVersion = "test-build",
                            mapWidth = 16,
                            mapHeight = 16,
                            factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 6), FactionSnapshot(faction = 2, visibleTiles = 4)),
                            entities = emptyList(),
                            resourceNodes = emptyList()
                        ),
                    viewedFaction = null
                )
            )

        assertTrue(output.contains("view=observer"))
        assertTrue(output.contains("economy: observer"))
        assertTrue(output.contains("selection hud: none"))
        assertTrue(output.contains("selection combat: none"))
        assertTrue(output.contains("capabilities: none"))
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
