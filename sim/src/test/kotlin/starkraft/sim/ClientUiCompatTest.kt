package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.formatSelectionMetaSummary
import starkraft.sim.client.formatSelectionRosterSummary

class ClientUiCompatTest {
    @Test
    fun `formats selection meta summary with production and rally counts`() {
        val selected =
            listOf(
                EntitySnapshot(
                    id = 4,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 5f,
                    y = 6f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    supportsTraining = true,
                    supportsResearch = true,
                    supportsRally = true,
                    rallyX = 14f,
                    rallyY = 10f
                ),
                EntitySnapshot(
                    id = 5,
                    faction = 1,
                    typeId = "Marine",
                    archetype = "infantry",
                    x = 8f,
                    y = 7f,
                    dir = 0f,
                    hp = 45,
                    maxHp = 45,
                    armor = 0,
                    weaponId = "Gauss"
                )
            )

        assertEquals("2 sel · c1 · w0 · s1 · prod 1 · tech 1 · rally 1/1", formatSelectionMetaSummary(selected))
    }

    @Test
    fun `formats single structure roster summary with queue and rally detail`() {
        val selected =
            listOf(
                EntitySnapshot(
                    id = 4,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 5f,
                    y = 6f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    supportsTraining = true,
                    supportsResearch = true,
                    supportsRally = true,
                    rallyX = 14f,
                    rallyY = 10f,
                    activeProductionType = "Marine",
                    productionQueueSize = 2,
                    activeResearchTech = "AdvancedTraining",
                    researchQueueSize = 1
                )
            )

        assertEquals(
            "Depot · producer · train · tech · rally 14,10 · prod Marine x2 · tech AdvancedTraining x1",
            formatSelectionRosterSummary(selected)
        )
    }

    @Test
    fun `formats multi selection roster summary with aggregate active systems`() {
        val selected =
            listOf(
                EntitySnapshot(
                    id = 4,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 5f,
                    y = 6f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    activeProductionType = "Marine",
                    productionQueueSize = 1,
                    rallyX = 14f,
                    rallyY = 10f
                ),
                EntitySnapshot(
                    id = 5,
                    faction = 1,
                    typeId = "Marine",
                    archetype = "infantry",
                    x = 8f,
                    y = 7f,
                    dir = 0f,
                    hp = 45,
                    maxHp = 45,
                    armor = 0,
                    weaponId = "Gauss"
                ),
                EntitySnapshot(
                    id = 6,
                    faction = 1,
                    typeId = "Academy",
                    archetype = "tech",
                    x = 9f,
                    y = 7f,
                    dir = 0f,
                    hp = 250,
                    maxHp = 250,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    activeResearchTech = "AdvancedTraining",
                    researchQueueSize = 1
                )
            )

        assertEquals(
            "Ros Depot:1   Marine:1   Academy:1   prod:1   tech:1   rally:1",
            formatSelectionRosterSummary(selected)
        )
    }
}
