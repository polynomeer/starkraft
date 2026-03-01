package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.BuildingPlacementSystem
import starkraft.sim.ecs.BuildingProductionSystem
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.World
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

class ProductionSystemTest {
    private fun testData(): DataRepo {
        val units =
            """
            {"list":[
              {"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss"}
            ]}
            """.trimIndent()
        val weapons =
            """
            {"list":[
              {"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}
            ]}
            """.trimIndent()
        return DataRepo(units, weapons)
    }

    @Test
    fun `production queue spawns unit when timer completes`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, testData(), resources)
        resources.set(1, 500, 0)
        val buildingId = buildings.place(1, "Depot", 6, 6, 2, 2, 400, mineralCost = 100)!!

        assertTrue(production.enqueue(buildingId, "Marine", 2, mineralCost = 50))
        assertEquals(350, world.stockpiles[1]?.minerals)
        production.tick()
        assertEquals(1, world.footprints.size)
        assertEquals(1, world.productionQueues.size)

        production.tick()

        val marines = world.tags.values.count { it.typeId == "Marine" }
        assertEquals(1, marines)
        assertEquals(0, world.productionQueues.size)
    }

    @Test
    fun `production waits while spawn ring is blocked`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, testData(), resources)
        resources.set(1, 500, 0)
        val buildingId = buildings.place(1, "Depot", 6, 6, 2, 2, 400, mineralCost = 100)!!

        for (y in 5..8) {
            for (x in 5..8) {
                val onBorder = x == 5 || x == 8 || y == 5 || y == 8
                if (onBorder) occ.addDynamic(x, y)
            }
        }
        assertTrue(production.enqueue(buildingId, "Marine", 1, mineralCost = 50))
        production.tick()

        assertFalse(world.tags.values.any { it.typeId == "Marine" })
        assertEquals(1, world.productionQueues.size)

        occ.clearDynamic()
        production.tick()

        assertTrue(world.tags.values.any { it.typeId == "Marine" })
    }

    @Test
    fun `production enqueue fails when resources are insufficient`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, testData(), resources)
        resources.set(1, 120, 0)
        val buildingId = buildings.place(1, "Depot", 6, 6, 2, 2, 400, mineralCost = 100)!!

        assertFalse(production.enqueue(buildingId, "Marine", 2, mineralCost = 50))
        assertEquals(20, world.stockpiles[1]?.minerals)
        assertEquals(0, world.productionQueues.size)
    }

    @Test
    fun `train command enqueues production via label`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val data = testDataWithDepot()
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        val labels = HashMap<String, Int>()
        val labelIds = HashMap<Int, Int>()
        resources.set(1, 300, 0)

        issue(
            Command.Build(0, 1, "Depot", 6, 6, 2, 2, 400, 0, 100, 0, "depot", -1),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds,
            buildings = buildings
        )
        issue(
            Command.Train(1, -1, "Marine", 2, 50, 0),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds,
            production = production
        )

        assertEquals(1, world.productionQueues.size)
        assertEquals(150, world.stockpiles[1]?.minerals)
    }

    @Test
    fun `build and train commands can resolve defaults from data`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val data = testDataWithDepot()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        val labels = HashMap<String, Int>()
        val labelIds = HashMap<Int, Int>()
        resources.set(1, 300, 0)

        issue(
            Command.Build(0, 1, "Depot", 6, 6, 0, 0, 0, 0, 0, 0, "depot", -1),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds,
            buildings = buildings
        )
        issue(
            Command.Train(1, -1, "Marine", 0, 0, 0),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds,
            production = production
        )

        assertEquals(1, world.footprints.size)
        assertEquals(1, world.productionQueues.size)
        assertEquals(150, world.stockpiles[1]?.minerals)
    }

    private fun testDataWithDepot(): DataRepo {
        val units =
            """
            {"list":[
              {"id":"Depot","hp":400,"armor":1,"speed":0.0,"mineralCost":100,"buildTicks":120,"footprintWidth":2,"footprintHeight":2},
              {"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75}
            ]}
            """.trimIndent()
        val weapons =
            """
            {"list":[
              {"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}
            ]}
            """.trimIndent()
        return DataRepo(units, weapons)
    }
}
