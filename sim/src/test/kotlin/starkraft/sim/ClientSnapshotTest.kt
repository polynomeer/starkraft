package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.buildClientSnapshot
import starkraft.sim.client.CombatEventRecord
import starkraft.sim.client.DespawnEventRecord
import starkraft.sim.client.renderClientSnapshotJson
import starkraft.sim.client.renderCombatStreamRecordJson
import starkraft.sim.client.renderCommandStreamRecordJson
import starkraft.sim.client.renderDespawnStreamRecordJson
import starkraft.sim.client.renderMetricsStreamRecordJson
import starkraft.sim.client.renderOrderAppliedStreamRecordJson
import starkraft.sim.client.renderSelectionStreamRecordJson
import starkraft.sim.client.renderSnapshotSessionEndJson
import starkraft.sim.client.renderSnapshotSessionStartJson
import starkraft.sim.client.renderSnapshotStreamRecordJson
import starkraft.sim.client.renderSpawnStreamRecordJson
import starkraft.sim.client.renderTickSummaryStreamRecordJson
import starkraft.sim.client.MetricsFactionRecord
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.Vision
import starkraft.sim.ecs.WeaponRef
import starkraft.sim.ecs.World
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.net.Command
import java.nio.file.Files

class ClientSnapshotTest {
    @Test
    fun `builds sorted client snapshot from world state`() {
        val world = World()
        val map = MapGrid(32, 32)
        val fog1 = FogGrid(16, 16, 1f)
        val fog2 = FogGrid(16, 16, 1f)
        val idA = world.spawn(Transform(4f, 5f, 0.5f), UnitTag(2, "Zergling"), Health(30, 35, 1), WeaponRef("Claw", 3))
        val idB = world.spawn(Transform(1f, 2f, 1.5f), UnitTag(1, "Marine"), Health(40, 45, 0), WeaponRef("Gauss", 1))
        world.visions[idA] = Vision(6f)
        world.visions[idB] = Vision(7f)
        world.orders[idA]?.items?.addLast(Order.Attack(idB))
        world.orders[idB]?.items?.addLast(Order.Move(9f, 9f))
        fog1.markVisible(1f, 2f, 2f)
        fog2.markVisible(4f, 5f, 3f)

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 12,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = 7L,
            fogByFaction = mapOf(1 to fog1, 2 to fog2)
        )

        assertEquals(12, snapshot.tick)
        assertEquals("demo-map", snapshot.mapId)
        assertEquals("test-build", snapshot.buildVersion)
        assertEquals(7L, snapshot.seed)
        assertEquals(listOf(idA, idB).sorted(), snapshot.entities.map { it.id })
        val entitiesById = snapshot.entities.associateBy { it.id }
        assertEquals("Attack", entitiesById[idA]?.activeOrder)
        assertEquals("Move", entitiesById[idB]?.activeOrder)
        assertEquals(listOf(1, 2), snapshot.factions.map { it.faction })
        assertTrue(snapshot.factions[0].visibleTiles > 0)
        assertTrue(snapshot.factions[1].visibleTiles > 0)
    }

    @Test
    fun `renders compact client snapshot json`() {
        val world = World()
        val map = MapGrid(8, 8)
        val fog = FogGrid(8, 8, 1f)
        world.spawn(Transform(1f, 1f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 1,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = null,
            fogByFaction = mapOf(1 to fog)
        )

        val json = renderClientSnapshotJson(snapshot, pretty = false)

        assertTrue(!json.contains("\n"))
        assertTrue(json.startsWith("{\"tick\":1,"))
        assertTrue(json.contains("\"entities\":[{\"id\":"))
    }

    @Test
    fun `emits snapshot ticks on configured cadence`() {
        assertEquals(true, shouldEmitSnapshotAtTick(0, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(1, 5))
        assertEquals(true, shouldEmitSnapshotAtTick(5, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(6, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(5, 0))
    }

    @Test
    fun `writes snapshot lines to ndjson file`() {
        val path = Files.createTempFile("starkraft-snapshot", ".ndjson")
        Files.deleteIfExists(path)

        emitSnapshotLine("{\"recordType\":\"snapshot\",\"sequence\":0,\"tick\":1,\"snapshot\":{\"tick\":1}}", path)
        emitSnapshotLine("{\"recordType\":\"snapshot\",\"sequence\":1,\"tick\":2,\"snapshot\":{\"tick\":2}}", path)

        assertEquals(
            "{\"recordType\":\"snapshot\",\"sequence\":0,\"tick\":1,\"snapshot\":{\"tick\":1}}\n" +
                "{\"recordType\":\"snapshot\",\"sequence\":1,\"tick\":2,\"snapshot\":{\"tick\":2}}\n",
            Files.readString(path)
        )
    }

    @Test
    fun `renders snapshot stream record json`() {
        val world = World()
        val map = MapGrid(8, 8)
        val fog = FogGrid(8, 8, 1f)
        world.spawn(Transform(1f, 1f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 3,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = null,
            fogByFaction = mapOf(1 to fog)
        )

        val json = renderSnapshotStreamRecordJson(snapshot, sequence = 4L, pretty = false)

        assertTrue(json.startsWith("{\"recordType\":\"snapshot\",\"sequence\":4,\"tick\":3,"))
        assertTrue(json.contains("\"snapshot\":{\"tick\":3"))
    }

    @Test
    fun `renders snapshot session start json`() {
        val json = renderSnapshotSessionStartJson(0L, "demo-map", "test-build", 7L, pretty = false)

        assertEquals(
            "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
            json
        )
    }

    @Test
    fun `renders snapshot session end json`() {
        val json = renderSnapshotSessionEndJson(sequence = 5L, tick = 15, worldHash = 123L, replayHash = 456L, pretty = false)

        assertEquals(
            "{\"recordType\":\"sessionEnd\",\"sequence\":5,\"tick\":15,\"worldHash\":123,\"replayHash\":456}",
            json
        )
    }

    @Test
    fun `increments snapshot sequence monotonically`() {
        val state = longArrayOf(0L)
        assertEquals(0L, nextStreamSequence(state))
        assertEquals(1L, nextStreamSequence(state))
        assertEquals(2L, nextStreamSequence(state))
    }

    @Test
    fun `renders command stream record json`() {
        val json = renderCommandStreamRecordJson(Command.Move(3, intArrayOf(7, 8), 4f, 5f), sequence = 9L, pretty = false)

        assertEquals(
            "{\"recordType\":\"command\",\"sequence\":9,\"tick\":3,\"commandType\":\"move\",\"units\":[7,8],\"faction\":null,\"typeId\":null,\"target\":null,\"x\":4.0,\"y\":5.0,\"vision\":null,\"label\":null,\"labelId\":null}",
            json
        )
    }

    @Test
    fun `renders metrics stream record json`() {
        val json =
            renderMetricsStreamRecordJson(
                sequence = 10L,
                tick = 4,
                factions = listOf(MetricsFactionRecord(1, 5, 20), MetricsFactionRecord(2, 4, 18)),
                pathRequests = 3,
                pathSolved = 2,
                pathQueueSize = 7,
                avgPathLength = 6.5f,
                replans = 2,
                replansBlocked = 1,
                replansStuck = 1,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"metrics\",\"sequence\":10,\"tick\":4,\"factions\":[{\"faction\":1,\"alive\":5,\"visibleTiles\":20},{\"faction\":2,\"alive\":4,\"visibleTiles\":18}],\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":7,\"avgPathLength\":6.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1}",
            json
        )
    }

    @Test
    fun `renders combat stream record json`() {
        val json =
            renderCombatStreamRecordJson(
                sequence = 11L,
                tick = 6,
                attacks = 2,
                kills = 1,
                events =
                    listOf(
                        CombatEventRecord(attackerId = 3, targetId = 8, damage = 6, targetHp = 12, killed = false),
                        CombatEventRecord(attackerId = 4, targetId = 9, damage = 9, targetHp = -1, killed = true)
                    ),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"combat\",\"sequence\":11,\"tick\":6,\"attacks\":2,\"kills\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
            json
        )
    }

    @Test
    fun `renders despawn stream record json`() {
        val json =
            renderDespawnStreamRecordJson(
                sequence = 12L,
                tick = 7,
                entities =
                    listOf(
                        DespawnEventRecord(entityId = 9, faction = 2, typeId = "Zergling", reason = "death"),
                        DespawnEventRecord(entityId = 14, faction = 1, typeId = "Marine", reason = "despawn")
                    ),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"despawn\",\"sequence\":12,\"tick\":7,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"},{\"entityId\":14,\"faction\":1,\"typeId\":\"Marine\",\"reason\":\"despawn\"}]}",
            json
        )
    }

    @Test
    fun `renders tick summary stream record json`() {
        val json =
            renderTickSummaryStreamRecordJson(
                sequence = 13L,
                tick = 8,
                aliveTotal = 7,
                visibleTilesFaction1 = 20,
                visibleTilesFaction2 = 18,
                pathRequests = 3,
                pathSolved = 2,
                pathQueueSize = 6,
                avgPathLength = 5.5f,
                replans = 2,
                replansBlocked = 1,
                replansStuck = 1,
                attacks = 4,
                kills = 1,
                despawns = 1,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"tickSummary\",\"sequence\":13,\"tick\":8,\"aliveTotal\":7,\"visibleTilesFaction1\":20,\"visibleTilesFaction2\":18,\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":6,\"avgPathLength\":5.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1,\"attacks\":4,\"kills\":1,\"despawns\":1}",
            json
        )
    }

    @Test
    fun `renders spawn stream record json`() {
        val json =
            renderSpawnStreamRecordJson(
                sequence = 14L,
                tick = 9,
                entityId = 21,
                faction = 1,
                typeId = "Marine",
                x = 3f,
                y = 4f,
                vision = 7f,
                label = "alpha",
                labelId = -1,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"spawn\",\"sequence\":14,\"tick\":9,\"entityId\":21,\"faction\":1,\"typeId\":\"Marine\",\"x\":3.0,\"y\":4.0,\"vision\":7.0,\"label\":\"alpha\",\"labelId\":-1}",
            json
        )
    }

    @Test
    fun `renders selection stream record json`() {
        val json =
            renderSelectionStreamRecordJson(
                sequence = 15L,
                tick = 10,
                selectionType = "faction",
                faction = 2,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"selection\",\"sequence\":15,\"tick\":10,\"selectionType\":\"faction\",\"units\":[],\"faction\":2,\"typeId\":null}",
            json
        )
    }

    @Test
    fun `renders order applied stream record json`() {
        val json =
            renderOrderAppliedStreamRecordJson(
                sequence = 16L,
                tick = 11,
                orderType = "attack",
                units = intArrayOf(3, 4, 5),
                target = 9,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"orderApplied\",\"sequence\":16,\"tick\":11,\"orderType\":\"attack\",\"units\":[3,4,5],\"target\":9,\"x\":null,\"y\":null}",
            json
        )
    }
}
