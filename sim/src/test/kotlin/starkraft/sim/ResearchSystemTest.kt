package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.BuildFailureReason
import starkraft.sim.ecs.BuildingPlacementSystem
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.ResearchFailureReason
import starkraft.sim.ecs.ResearchSystem
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.TrainFailureReason
import starkraft.sim.ecs.World
import starkraft.sim.ecs.BuildingProductionSystem

class ResearchSystemTest {
    private fun data(): DataRepo =
        DataRepo(
            """
            {"list":[
              {"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","buildTicks":75,"producerTypes":["Depot"],"requiredResearchIds":["AdvancedTraining"]}
            ]}
            """.trimIndent(),
            """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
            """
            {"list":[
              {"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"supportsTraining":true,"supportsResearch":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0},
              {"id":"Academy","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"requiredResearchIds":["AdvancedTraining"]}
            ]}
            """.trimIndent(),
            """
            {"list":[
              {"id":"AdvancedTraining","buildTicks":3,"mineralCost":75,"gasCost":0,"producerTypes":["Depot"]}
            ]}
            """.trimIndent()
        )

    @Test
    fun `research completes and unlocks faction tech`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val research = ResearchSystem(world, data(), resources)
        resources.set(1, 500, 0)
        val depotId = buildings.place(1, "Depot", 4, 4, 2, 2, 400, mineralCost = 100)!!

        assertEquals(null, research.enqueueResult(depotId, "AdvancedTraining", 3, mineralCost = 75))
        research.tick()
        research.tick()
        assertTrue(world.unlockedTechs(1).isEmpty())

        research.tick()

        assertTrue(world.unlockedTechs(1).contains("AdvancedTraining"))
        assertEquals(0, world.researchQueues.size)
    }

    @Test
    fun `research unlock enables gated train and build`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val repo = data()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val production = BuildingProductionSystem(world, map, occ, repo, resources)
        val research = ResearchSystem(world, repo, resources)
        resources.set(1, 1000, 0)
        val depotId = buildings.place(1, "Depot", 4, 4, 2, 2, 400, mineralCost = 100)!!

        assertEquals(TrainFailureReason.MISSING_TECH, production.enqueueResult(depotId, "Marine", 75))
        val academyBlocked =
            buildings.placeResult(1, "Academy", 8, 4, 2, 2, 250, requiredResearchIds = listOf("AdvancedTraining"))
        assertEquals(BuildFailureReason.MISSING_TECH, academyBlocked.failure)

        assertEquals(null, research.enqueueResult(depotId, "AdvancedTraining", 3, mineralCost = 75))
        repeat(3) { research.tick() }

        assertEquals(null, production.enqueueResult(depotId, "Marine", 75))
        val academyAllowed =
            buildings.placeResult(1, "Academy", 8, 4, 2, 2, 250, requiredResearchIds = listOf("AdvancedTraining"))
        assertEquals(null, academyAllowed.failure)
    }

    @Test
    fun `under construction producer cannot research`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val resources = ResourceSystem(world)
        val repo = data()
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val research = ResearchSystem(world, repo, resources)
        resources.set(1, 500, 0)
        val depotId = buildings.place(1, "Depot", 4, 4, 2, 2, 400, buildTicks = 2, mineralCost = 100)!!

        assertEquals(
            ResearchFailureReason.UNDER_CONSTRUCTION,
            research.enqueueResult(depotId, "AdvancedTraining", 3, mineralCost = 75)
        )
    }
}
