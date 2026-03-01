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
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.World
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

class ProductionSystemTest {
    private fun testData(): DataRepo {
        val units =
            """
            {"list":[
              {"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","producerTypes":["Depot"]}
            ]}
            """.trimIndent()
        val weapons =
            """
            {"list":[
              {"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}
            ]}
            """.trimIndent()
        val buildings =
            """
            {"list":[
              {"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}
            ]}
            """.trimIndent()
        return DataRepo(units, weapons, buildings)
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
        val marineId = world.tags.entries.first { it.value.typeId == "Marine" }.key
        val move = world.orders[marineId]?.items?.firstOrNull() as? Order.Move
        assertEquals(11.0f, move?.tx)
        assertEquals(7.0f, move?.ty)
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
    fun `cancel train refunds resources and clears queue tail`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val data = testDataWithDepot()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        resources.set(1, 300, 0)
        val buildingId = buildings.place(1, "Depot", 6, 6, 2, 2, 400, mineralCost = 100)!!

        assertTrue(production.enqueue(buildingId, "Marine", 5, mineralCost = 50))
        assertEquals(150, world.stockpiles[1]?.minerals)
        assertTrue(production.cancelLast(buildingId))
        assertEquals(200, world.stockpiles[1]?.minerals)
        assertEquals(0, world.productionQueues.size)
    }

    @Test
    fun `production queue enforces max size`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val data = testDataWithDepot()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        resources.set(1, 1000, 0)
        val buildingId = buildings.place(1, "Depot", 6, 6, 2, 2, 400, mineralCost = 100)!!

        repeat(3) {
            assertTrue(production.enqueue(buildingId, "Marine", 5, mineralCost = 50))
        }
        assertFalse(production.enqueue(buildingId, "Marine", 5, mineralCost = 50))
        assertEquals(750, world.stockpiles[1]?.minerals)
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
    fun `rally command overrides producer default rally point`() {
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
            Command.Rally(0, -1, 15f, 16f),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds
        )
        issue(
            Command.Train(0, -1, "Marine", 2, 50, 0),
            world,
            NullRecorder(),
            data = data,
            labelMap = labels,
            labelIdMap = labelIds,
            production = production
        )

        production.tick()
        production.tick()

        val marineId = world.tags.entries.first { it.value.typeId == "Marine" }.key
        val move = world.orders[marineId]?.items?.firstOrNull() as? Order.Move
        assertEquals(15f, move?.tx)
        assertEquals(16f, move?.ty)
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

    @Test
    fun `production enqueue rejects incompatible producer type`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val data = testDataWithDepot()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        resources.set(1, 500, 0)
        val factoryId = buildings.place(1, "Factory", 6, 6, 2, 2, 500, mineralCost = 100)!!

        assertFalse(production.enqueue(factoryId, "Marine", 5, mineralCost = 50))
        assertEquals(400, world.stockpiles[1]?.minerals)
        assertEquals(0, world.productionQueues.size)
    }

    private fun testDataWithDepot(): DataRepo {
        val units =
            """
            {"list":[
              {"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Depot"]}
            ]}
            """.trimIndent()
        val weapons =
            """
            {"list":[
              {"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}
            ]}
            """.trimIndent()
        val buildings =
            """
            {"list":[
              {"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}
            ]}
            """.trimIndent()
        return DataRepo(units, weapons, buildings)
    }
}
