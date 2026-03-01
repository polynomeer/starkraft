package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.buildClientSnapshot
import starkraft.sim.client.renderClientSnapshotJson
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.Vision
import starkraft.sim.ecs.WeaponRef
import starkraft.sim.ecs.World
import starkraft.sim.ecs.services.FogGrid
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

        emitSnapshotLine("{\"tick\":1}", path)
        emitSnapshotLine("{\"tick\":2}", path)

        assertEquals("{\"tick\":1}\n{\"tick\":2}\n", Files.readString(path))
    }
}
