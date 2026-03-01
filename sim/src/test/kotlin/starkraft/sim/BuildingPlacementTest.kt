package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.BuildingPlacementSystem
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

class BuildingPlacementTest {
    @Test
    fun `place and remove building updates occupancy`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val buildings = BuildingPlacementSystem(world, map, occ)

        val id = buildings.place(faction = 1, typeId = "Depot", tileX = 4, tileY = 5, width = 2, height = 3, hp = 400)

        assertNotNull(id)
        assertEquals(1, world.footprints.size)
        assertTrue(occ.isBlocked(4, 5))
        assertTrue(occ.isBlocked(5, 7))

        assertTrue(buildings.remove(id!!))
        assertEquals(0, world.footprints.size)
        assertFalse(occ.isBlocked(4, 5))
        assertFalse(occ.isBlocked(5, 7))
    }

    @Test
    fun `building footprint blocks path tiles`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val buildings = BuildingPlacementSystem(world, map, occ)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val out = IntArray(64)

        val id = buildings.place(faction = 1, typeId = "Barracks", tileX = 6, tileY = 6, width = 2, height = 2, hp = 600)
        assertNotNull(id)

        val len = pathfinder.findPath(5, 6, 8, 6, 500, out)

        assertTrue(len > 0)
        for (i in 0 until len) {
            val node = out[i]
            val x = node % map.width
            val y = node / map.width
            assertFalse(x in 6..7 && y in 6..7, "path should not cross building footprint")
        }
    }

    @Test
    fun `building placement spends resources and fails when unaffordable`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        resources.set(faction = 1, minerals = 125, gas = 0)

        val first = buildings.place(faction = 1, typeId = "Depot", tileX = 2, tileY = 2, width = 2, height = 2, hp = 400, mineralCost = 100)
        val second = buildings.place(faction = 1, typeId = "Depot", tileX = 6, tileY = 2, width = 2, height = 2, hp = 400, mineralCost = 100)

        assertNotNull(first)
        assertNull(second)
        assertEquals(25, world.stockpiles[1]?.minerals)
    }

    @Test
    fun `build command places footprint through issue`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        resources.set(faction = 1, minerals = 150, gas = 0)
        val data =
            DataRepo(
                """{"list":[]}""",
                """{"list":[]}""",
                """{"list":[{"id":"Depot","hp":400,"armor":0,"footprintWidth":2,"footprintHeight":2,"mineralCost":100,"gasCost":0}]}"""
            )

        issue(
            Command.Build(0, 1, "Depot", 8, 8, 2, 2, 400, 0, 100, 0),
            world,
            NullRecorder(),
            data = data,
            buildings = buildings
        )

        assertEquals(1, world.footprints.size)
        assertTrue(occ.isBlocked(8, 8))
        assertEquals(50, world.stockpiles[1]?.minerals)
    }
}
