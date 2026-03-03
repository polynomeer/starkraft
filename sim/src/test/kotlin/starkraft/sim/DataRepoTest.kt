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
                  {"id":"Marine","archetype":"infantry","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"gasCost":0,"buildTicks":75,"producerTypes":["Depot"]}
                ]}
                """.trimIndent(),
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """
                {"list":[
                  {"id":"Depot","archetype":"producer","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"supportsDropoff":true,"productionQueueLimit":3,"rallyOffsetX":4.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}
                ]}
                """.trimIndent()
            )

        val build = repo.buildSpec("Depot")
        val train = repo.trainSpec("Marine")

        assertEquals(2, build?.footprintWidth)
        assertEquals("producer", build?.archetype)
        assertEquals(2, build?.footprintHeight)
        assertEquals(1, build?.placementClearance)
        assertEquals(true, build?.supportsTraining)
        assertEquals(true, build?.supportsRally)
        assertEquals(true, build?.supportsDropoff)
        assertEquals(3, build?.productionQueueLimit)
        assertEquals(4f, build?.rallyOffsetX)
        assertEquals(0f, build?.rallyOffsetY)
        assertEquals(400, build?.hp)
        assertEquals("infantry", train?.archetype)
        assertEquals(75, train?.buildTicks)
        assertEquals(50, train?.mineralCost)
        assertEquals(listOf("Depot"), train?.producerTypes)
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
    }
}
