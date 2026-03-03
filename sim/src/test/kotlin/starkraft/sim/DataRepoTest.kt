package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo

class DataRepoTest {
    @Test
    fun `resolves build and train specs from dedicated data defs`() {
        val repo =
            DataRepo(
                """
                {"list":[
                  {"id":"Marine","archetype":"infantry","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"gasCost":0,"buildTicks":75,"producerTypes":["Depot"],"requiredBuildingTypes":["Academy"]}
                ]}
                """.trimIndent(),
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """
                {"list":[
                  {"id":"Depot","archetype":"producer","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsResearch":true,"supportsRally":true,"supportsDropoff":true,"dropoffResourceKinds":["minerals"],"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0},
                  {"id":"Academy","archetype":"tech","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"supportsDropoff":false,"dropoffResourceKinds":[],"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":125,"gasCost":0,"requiredBuildingTypes":["Depot"],"requiredResearchIds":["AdvancedTraining"]},
                  {"id":"ResourceDepot","archetype":"econDepot","hp":350,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"supportsDropoff":true,"dropoffResourceKinds":["minerals"],"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":75,"gasCost":0},
                  {"id":"GasDepot","archetype":"gasDepot","hp":325,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"supportsDropoff":true,"dropoffResourceKinds":["gas"],"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":90,"gasCost":0}
                ]}
                """.trimIndent(),
                """
                {"list":[
                  {"id":"AdvancedTraining","buildTicks":60,"mineralCost":75,"gasCost":0,"producerTypes":["Depot"],"requiredBuildingTypes":["Depot"]}
                ]}
                """.trimIndent()
            )

        val build = repo.buildSpec("Depot")
        val academy = repo.buildSpec("Academy")
        val resourceDepot = repo.buildSpec("ResourceDepot")
        val gasDepot = repo.buildSpec("GasDepot")
        val train = repo.trainSpec("Marine")
        val research = repo.researchSpec("AdvancedTraining")

        assertEquals(2, build?.footprintWidth)
        assertEquals("producer", build?.archetype)
        assertEquals(2, build?.footprintHeight)
        assertEquals(1, build?.placementClearance)
        assertEquals(true, build?.supportsTraining)
        assertEquals(true, build?.supportsResearch)
        assertEquals(true, build?.supportsRally)
        assertEquals(true, build?.supportsDropoff)
        assertEquals(listOf("minerals"), build?.dropoffResourceKinds)
        assertEquals(3, build?.productionQueueLimit)
        assertEquals(4f, build?.rallyOffsetX)
        assertEquals(0f, build?.rallyOffsetY)
        assertEquals(400, build?.hp)
        assertEquals("tech", academy?.archetype)
        assertEquals(listOf("Depot"), academy?.requiredBuildingTypes)
        assertEquals(listOf("AdvancedTraining"), academy?.requiredResearchIds)
        assertEquals("econDepot", resourceDepot?.archetype)
        assertEquals(false, resourceDepot?.supportsTraining)
        assertEquals(false, resourceDepot?.supportsRally)
        assertEquals(true, resourceDepot?.supportsDropoff)
        assertEquals(listOf("minerals"), resourceDepot?.dropoffResourceKinds)
        assertEquals(75, resourceDepot?.mineralCost)
        assertEquals("gasDepot", gasDepot?.archetype)
        assertEquals(listOf("gas"), gasDepot?.dropoffResourceKinds)
        assertEquals("infantry", train?.archetype)
        assertEquals(75, train?.buildTicks)
        assertEquals(50, train?.mineralCost)
        assertEquals(listOf("Depot"), train?.producerTypes)
        assertEquals(listOf("Academy"), train?.requiredBuildingTypes)
        assertEquals(60, research?.buildTicks)
        assertEquals(75, research?.mineralCost)
        assertEquals(listOf("Depot"), research?.producerTypes)
        assertEquals("producer", repo.buildingArchetype("Depot"))
        assertEquals("infantry", repo.unitArchetype("Marine"))
    }

    @Test
    fun `returns null for unsupported build or train specs`() {
        val repo =
            DataRepo(
                """
                {"list":[
                  {"id":"Worker","archetype":"worker","hp":40,"armor":0,"speed":0.07}
                ]}
                """.trimIndent(),
                """{"list":[]}""",
                """{"list":[]}"""
            )

        assertNull(repo.buildSpec("Worker"))
        assertNull(repo.trainSpec("Worker"))
        assertNull(repo.researchSpec("AdvancedTraining"))
    }
}
