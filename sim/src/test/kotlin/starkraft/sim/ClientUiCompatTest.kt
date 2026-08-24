package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.formatDeckCommandCaption
import starkraft.sim.client.formatEntityBadgeCode
import starkraft.sim.client.formatGroundPingBanner
import starkraft.sim.client.formatOverlayReadyBanner
import starkraft.sim.client.formatOverlayHintLine
import starkraft.sim.client.formatCenterFooterInstruction
import starkraft.sim.client.formatSelectionMetaSummary
import starkraft.sim.client.formatSelectionRosterSummary
import starkraft.sim.client.ClientGroundCommandMode
import starkraft.sim.client.GroundPingKind
import starkraft.sim.client.isCommandButtonQueued

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

        assertEquals("2 selected · combat 1 · structures 1 · prod 1 · tech 1 · rally 1/1", formatSelectionMetaSummary(selected))
        assertEquals("2 selected · combat 1 · structures 1", formatSelectionMetaSummary(selected, includeCapabilities = false))
    }

    @Test
    fun `formats compact entity badge codes for roster slots`() {
        assertEquals("MRN", formatEntityBadgeCode("Marine"))
        assertEquals("WRK", formatEntityBadgeCode("Worker"))
        assertEquals("RD", formatEntityBadgeCode("ResourceDepot"))
        assertEquals("GD", formatEntityBadgeCode("Gas_Depot"))
    }

    @Test
    fun `formats command captions with stable hotkey and state sections`() {
        assertEquals("Move [M]", formatDeckCommandCaption("Move", "M", null))
        assertEquals("Train [B] · 11t", formatDeckCommandCaption("Train", "B", "11t"))
        assertEquals("Cancel · x2", formatDeckCommandCaption("Cancel", null, "x2"))
    }

    @Test
    fun `formats ground ping confirmations for action banner`() {
        assertEquals("Move confirmed", formatGroundPingBanner(GroundPingKind.MOVE, queued = false))
        assertEquals("Move queued", formatGroundPingBanner(GroundPingKind.MOVE, queued = true))
        assertEquals("Attack ordered", formatGroundPingBanner(GroundPingKind.ATTACK, queued = false))
        assertEquals("Build queued", formatGroundPingBanner(GroundPingKind.BUILD, queued = true))
        assertEquals("Command rejected", formatGroundPingBanner(GroundPingKind.INVALID, queued = false))
    }

    @Test
    fun `formats armed overlay copy consistently across banner hint and footer`() {
        assertEquals("Move target ready", formatOverlayReadyBanner(null, null, ClientGroundCommandMode.MOVE))
        assertEquals("Attack target ready", formatOverlayReadyBanner(null, null, ClientGroundCommandMode.ATTACK_MOVE))
        assertEquals("Set rally point", formatOverlayReadyBanner(null, 7, null))
        assertEquals("Pick move point", formatOverlayHintLine(null, null, null, ClientGroundCommandMode.MOVE, hasSelection = true))
        assertEquals("Pick attack target", formatOverlayHintLine(null, null, null, ClientGroundCommandMode.ATTACK_MOVE, hasSelection = true))
        assertEquals("Place footprint", formatOverlayHintLine(null, "Depot", null, null, hasSelection = true))
        assertEquals("LMB/RMB attack  Shift queue  Esc cancel", formatCenterFooterInstruction(null, null, ClientGroundCommandMode.ATTACK_MOVE, hasSelection = true))
        assertEquals("LMB/RMB place  Shift queue  Esc cancel", formatCenterFooterInstruction("Depot", null, null, hasSelection = true))
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

    @Test
    fun `detects queued command buttons from selected structure state`() {
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
                    activeProductionType = "Marine",
                    productionQueueSize = 2,
                    activeResearchTech = "AdvancedTraining",
                    researchQueueSize = 1,
                    rallyX = 14f,
                    rallyY = 10f,
                    underConstruction = true
                )
            )

        assertTrue(isCommandButtonQueued("train:Marine", selected))
        assertTrue(isCommandButtonQueued("research:AdvancedTraining", selected))
        assertTrue(isCommandButtonQueued("cancelTrain", selected))
        assertTrue(isCommandButtonQueued("cancelResearch", selected))
        assertTrue(isCommandButtonQueued("cancelBuild", selected))
        assertTrue(isCommandButtonQueued("rally", selected))
        assertFalse(isCommandButtonQueued("attackMove", selected))
    }
}
