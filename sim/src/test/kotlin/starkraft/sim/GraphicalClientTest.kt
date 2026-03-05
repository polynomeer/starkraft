package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandIds
import starkraft.sim.client.ClientGroundCommandMode
import starkraft.sim.client.ClientIntent
import starkraft.sim.client.ClientMapState
import starkraft.sim.client.applySelectionClick
import starkraft.sim.client.buildHoldIntent
import starkraft.sim.client.buildClientIntent
import starkraft.sim.client.buildCancelIntent
import starkraft.sim.client.buildQueueIntent
import starkraft.sim.client.buildUnitSelectionRecord
import starkraft.sim.client.buildFactionSelectionRecord
import starkraft.sim.client.buildTypeSelectionRecord
import starkraft.sim.client.buildArchetypeSelectionRecord
import starkraft.sim.client.buildAllSelectionRecord
import starkraft.sim.client.collectFactionSelectionIds
import starkraft.sim.client.collectTypeSelectionIds
import starkraft.sim.client.collectArchetypeSelectionIds
import starkraft.sim.client.collectAllSelectionIds
import starkraft.sim.client.collectIdleWorkerSelectionIds
import starkraft.sim.client.collectDamagedSelectionIds
import starkraft.sim.client.collectCombatSelectionIds
import starkraft.sim.client.collectProducerSelectionIds
import starkraft.sim.client.collectTrainingSelectionIds
import starkraft.sim.client.collectResearchSelectionIds
import starkraft.sim.client.collectConstructionSelectionIds
import starkraft.sim.client.collectHarvesterSelectionIds
import starkraft.sim.client.collectReturningHarvesterSelectionIds
import starkraft.sim.client.collectCargoHarvesterSelectionIds
import starkraft.sim.client.collectDropoffSelectionIds
import starkraft.sim.client.controlGroupFromKeyCode
import starkraft.sim.client.assignControlGroupSlot
import starkraft.sim.client.mergeControlGroupSlot
import starkraft.sim.client.recallControlGroupSlot
import starkraft.sim.client.formatControlGroupSummary
import starkraft.sim.client.computeSelectionCentroid
import starkraft.sim.client.clearControlGroupSlots
import starkraft.sim.client.activeControlGroupHighlight
import starkraft.sim.client.buildPreviewSpec
import starkraft.sim.client.centerCameraOnWorld
import starkraft.sim.client.defaultClientInputPath
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.ClientBuildCatalogEntry
import starkraft.sim.client.ClientCatalog
import starkraft.sim.client.ClientConstructionActivity
import starkraft.sim.client.CameraView
import starkraft.sim.client.ClientProductionActivity
import starkraft.sim.client.ClientQueueCatalogEntry
import starkraft.sim.client.ClientResearchActivity
import starkraft.sim.client.ClientTickActivity
import starkraft.sim.client.formatAckStatus
import starkraft.sim.client.formatConstructionActivity
import starkraft.sim.client.formatPlayControlOverlay
import starkraft.sim.client.formatPresetAvailability
import starkraft.sim.client.formatProductionActivity
import starkraft.sim.client.formatResearchActivity
import starkraft.sim.client.formatTickActivity
import starkraft.sim.client.parseClientStreamLine
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.buildClientHudLines
import starkraft.sim.client.buildBuilderSummary
import starkraft.sim.client.buildConstructionSummary
import starkraft.sim.client.buildCommandButtons
import starkraft.sim.client.buildCommandPanelStatusLines
import starkraft.sim.client.buildFogSummary
import starkraft.sim.client.buildEntityStatusLabel
import starkraft.sim.client.buildGameState
import starkraft.sim.client.buildPreviewLabel
import starkraft.sim.client.miniMapBounds
import starkraft.sim.client.miniMapPoint
import starkraft.sim.client.miniMapViewport
import starkraft.sim.client.buildPathSummary
import starkraft.sim.client.buildProductionSummary
import starkraft.sim.client.buildResearchSummary
import starkraft.sim.client.buildRallySummary
import starkraft.sim.client.buildSelectionSummary
import starkraft.sim.client.buildSelectionFactionSummary
import starkraft.sim.client.buildSelectionArchetypeSummary
import starkraft.sim.client.buildSelectionPositionSummary
import starkraft.sim.client.buildSelectionHealthSummary
import starkraft.sim.client.buildSelectionDurabilitySummary
import starkraft.sim.client.buildSelectionVisionSummary
import starkraft.sim.client.buildSelectionCargoSummary
import starkraft.sim.client.buildSelectionMobilitySummary
import starkraft.sim.client.buildSelectionWeaponSummary
import starkraft.sim.client.buildSelectionPathSummary
import starkraft.sim.client.buildSelectionOrderSummary
import starkraft.sim.client.buildSelectionTargetSummary
import starkraft.sim.client.buildSelectionRallySummary
import starkraft.sim.client.buildSelectionStructureSummary
import starkraft.sim.client.buildSelectionCombatSummary
import starkraft.sim.client.buildSelectionCapabilitySummary
import starkraft.sim.client.buildSelectionQueueSummary
import starkraft.sim.client.buildCommandAffordanceSummary
import starkraft.sim.client.buildScenarioOverlayLines
import starkraft.sim.client.buildPresetOverlayLines
import starkraft.sim.client.buildHelpOverlayLines
import starkraft.sim.client.buildStartOverlayLines
import starkraft.sim.client.buildTaskSummary
import starkraft.sim.client.buildTechSummary
import starkraft.sim.client.buildEconomySummary
import starkraft.sim.client.healthBarFillWidth
import starkraft.sim.client.isCommandButtonActive
import starkraft.sim.client.isCommandButtonEnabled
import starkraft.sim.client.commandButtonAt
import starkraft.sim.client.commandButtonTooltip
import starkraft.sim.client.fitCommandPanelStatusLine
import starkraft.sim.client.selectEntitiesInBox
import starkraft.sim.client.zoomCameraAt
import starkraft.sim.client.isBuildPreviewValid
import starkraft.sim.client.miniMapWorldPosition
import starkraft.sim.client.ClientVisionState
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.ResourceNodeSnapshot
import java.nio.file.Paths

class GraphicalClientTest {
    private val testCatalog =
        ClientCatalog(
            buildOptions = listOf(
                ClientBuildCatalogEntry("Depot", "Depot", 2, 2, 1, 100, 0),
                ClientBuildCatalogEntry("ResourceDepot", "ResourceDepot", 2, 2, 1, 75, 0),
                ClientBuildCatalogEntry("GasDepot", "GasDepot", 2, 2, 1, 90, 0)
            ),
            trainOptions = listOf(
                ClientQueueCatalogEntry("Worker", "Worker"),
                ClientQueueCatalogEntry("Marine", "Marine"),
                ClientQueueCatalogEntry("Zergling", "Zergling")
            ),
            researchOptions = listOf(
                ClientQueueCatalogEntry("AdvancedTraining", "AdvancedTraining")
            )
        )

    @Test
    fun `default client input path is adjacent to snapshot file`() {
        val snapshotPath = Paths.get("/tmp/starkraft/live/snapshots.ndjson")

        assertEquals(
            Paths.get("/tmp/starkraft/live/client-input.ndjson"),
            defaultClientInputPath(snapshotPath)
        )
    }

    @Test
    fun `formats command ack status for hud`() {
        assertEquals("last ack: none", formatAckStatus(null))
        assertEquals(
            "last ack: ok move[cli-1] @12",
            formatAckStatus(ClientCommandAck(tick = 12, commandType = "move", requestId = "cli-1", accepted = true))
        )
        assertEquals(
            "last ack: fail build @13 reason=missingTech",
            formatAckStatus(ClientCommandAck(tick = 13, commandType = "build", accepted = false, reason = "missingTech"))
        )
    }

    @Test
    fun `formats research activity for hud`() {
        assertEquals("research events: none", formatResearchActivity(null))
        assertEquals(
            "research events: e1/p2/c0/x1 @12",
            formatResearchActivity(ClientResearchActivity(tick = 12, enqueue = 1, progress = 2, complete = 0, cancel = 1))
        )
    }

    @Test
    fun `formats production activity for hud`() {
        assertEquals("production events: none", formatProductionActivity(null))
        assertEquals(
            "production events: e1/p2/c0/x1 @12",
            formatProductionActivity(ClientProductionActivity(tick = 12, enqueue = 1, progress = 2, complete = 0, cancel = 1))
        )
    }

    @Test
    fun `formats construction activity for hud`() {
        assertEquals("construction state: none", formatConstructionActivity(null))
        assertEquals(
            "construction state: total=2 f1=1 f2=1 remaining=8 @12",
            formatConstructionActivity(ClientConstructionActivity(tick = 12, total = 2, faction1 = 1, faction2 = 1, remainingTicks = 8))
        )
    }

    @Test
    fun `formats play control overlay`() {
        assertEquals("play: running x1", formatPlayControlOverlay(starkraft.sim.client.PlayControlState()))
        assertEquals("play: paused x4", formatPlayControlOverlay(starkraft.sim.client.PlayControlState(paused = true, speed = 4)))
    }

    @Test
    fun `formats preset availability line`() {
        assertEquals("presets: quick=ready alt=missing", formatPresetAvailability(quickAvailable = true, altAvailable = false))
    }

    @Test
    fun `builds scenario overlay lines`() {
        assertEquals(
            listOf(
                "scenario menu: enter apply  tab close",
                "  skirmish (current)",
                "> economy",
                "  gas",
                "  scripted"
            ),
            buildScenarioOverlayLines(open = true, activeScenario = starkraft.sim.client.PlayScenario.SKIRMISH, selectedScenario = starkraft.sim.client.PlayScenario.ECONOMY)
        )
        assertEquals(emptyList<String>(), buildScenarioOverlayLines(open = false, activeScenario = starkraft.sim.client.PlayScenario.SKIRMISH, selectedScenario = starkraft.sim.client.PlayScenario.SKIRMISH))
    }

    @Test
    fun `builds preset overlay lines`() {
        assertEquals(
            listOf(
                "preset menu: s save  l/enter load  f10 close",
                "> quick (ready)",
                "  alt (missing)"
            ),
            buildPresetOverlayLines(open = true, selectedSlot = "quick", quickAvailable = true, altAvailable = false)
        )
        assertEquals(emptyList<String>(), buildPresetOverlayLines(open = false, selectedSlot = "quick", quickAvailable = false, altAvailable = false))
    }

    @Test
    fun `builds help overlay lines`() {
        assertEquals(
            listOf(
                "help: f1 close  tab scenario menu  f10 preset menu",
                "help: left select  shift+left add/remove  right command  ctrl+right attackMove",
                "help: f2 select viewed faction  f3 select selected type  f4 archetype",
                "help: f11 select all units",
                "help: f12 select idle workers",
                "help: f select damaged units",
                "help: v select combat units",
                "help: n select producer buildings",
                "help: 1/2 view faction  3 observer  0 reset camera",
                "help: home center camera on selection",
                "help: end center camera on viewed faction",
                "help: z select training buildings  c select research buildings",
                "help: j select active construction sites",
                "help: k select active harvesters",
                "help: q select returning harvesters",
                "help: e select loaded harvesters",
                "help: d select resource drop-off buildings",
                "help: shift+4..9 set group  alt+4..9 add  4..9 recall  double-tap focus",
                "help: alt+0 clear control groups",
                "help: space pause  [/] speed  f5 restart  f8/f9 quick preset"
            ),
            buildHelpOverlayLines(open = true)
        )
        assertEquals(emptyList<String>(), buildHelpOverlayLines(open = false))
    }

    @Test
    fun `formats tick activity for hud`() {
        assertEquals("activity: none", formatTickActivity(null))
        assertEquals(
            "activity: builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q2/c1/x1 trainFails=1[queueFull=1] research=q1/c0/x1 researchFails=1[invalidTech=1] @12",
            formatTickActivity(
                ClientTickActivity(
                    tick = 12,
                    builds = 1,
                    buildsCancelled = 1,
                    buildFailures = 2,
                    buildFailureReasons = "invalidPlacement=1,insufficientResources=1",
                    trainsQueued = 2,
                    trainsCompleted = 1,
                    trainsCancelled = 1,
                    trainFailures = 1,
                    trainFailureReasons = "queueFull=1",
                    researchQueued = 1,
                    researchCancelled = 1,
                    researchFailures = 1,
                    researchFailureReasons = "invalidTech=1"
                )
            )
        )
    }

    @Test
    fun `builds shared hud lines for pluggable renderers`() {
        val snapshot =
            ClientSnapshot(
                tick = 15,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(
                    FactionSnapshot(
                        faction = 1,
                        visibleTiles = 10,
                        minerals = 300,
                        gas = 60,
                        dropoffBuildings = 1,
                        unlockedTechIds = listOf("AdvancedTraining")
                    )
                ),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0, weaponId = "Rifle", weaponCooldownTicks = 3, visionRange = 6f, orderQueueSize = 1, activeOrder = "move"),
                    EntitySnapshot(id = 9, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 11, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, visionRange = 5f, buildTargetId = 30, harvestPhase = "return", harvestTargetNodeId = 40, harvestCargoKind = "minerals", harvestCargoAmount = 6, orderQueueSize = 2, activeOrder = "attackMove"),
                    EntitySnapshot(
                        id = 12,
                        faction = 1,
                        typeId = "Depot",
                        archetype = "producer",
                        x = 7f,
                        y = 4f,
                        dir = 0f,
                        hp = 120,
                        maxHp = 400,
                        armor = 1,
                        underConstruction = true,
                        constructionRemainingTicks = 6,
                        constructionTotalTicks = 10,
                        footprintWidth = 2,
                        footprintHeight = 2,
                        productionQueueSize = 2,
                        activeProductionType = "Marine",
                        activeProductionRemainingTicks = 9,
                        researchQueueSize = 2,
                        activeResearchTech = "AdvancedTraining",
                        activeResearchRemainingTicks = 8,
                        visionRange = 7f,
                        supportsTraining = true,
                        supportsResearch = true,
                        supportsRally = true,
                        supportsDropoff = true,
                        rallyX = 14f,
                        rallyY = 10f,
                        pathRemainingNodes = 5,
                        pathGoalX = 12,
                        pathGoalY = 14
                    )
                ),
                resourceNodes = emptyList()
            )
        assertEquals(
            listOf(
                "tick=15 selected=3",
                "economy: f1 minerals=300 gas=60 dropoffs=1",
                "selection factions: f1=3",
                "selection: Marinex1 Workerx1 Depotx1",
                "selection roles: infantryx1 workerx1 producerx1",
                "selection pos: center=5.7,4.0 span=3.0x0.0",
                "selection hp: 185/465 (39%)",
                "selection durability: avgArmor=0.3 damaged=1/3",
                "selection vision: avg=6.0 min=5.0 max=7.0",
                "selection cargo: loaded=1 minerals=6 gas=0",
                "selection mobility: moving=2 pathing=1 stationary=1",
                "selection weapons: Riflex1 unarmed=2",
                "selection paths: active=1 avg=5.0 topGoal=12,14",
                "orders: queued=3 active=movex1 attackMovex1",
                "selection targets: build=1 harvestNodes=1 return=0",
                "selection rally: configured=1/1 top=14,10",
                "selection structures: total=1 constructing=1 area=4",
                "selection combat: armed=1 ready=0 cooling=1 unarmed=2 nextReady=3",
                "capabilities: train=1 research=1 rally=1 dropoff=1",
                "selection queues: prod=2@1 research=2@1",
                "commands: move=on train=on research=on viewSelect=on",
                "builders: active=1 targets=1",
                "construction: sites=1 remaining=6 Depotx1",
                "tasks: build=1 gather=0 return=1",
                "paths: active=1 remaining=5 goals=12,14x1",
                "fog: f1 visible=10 hidden=1014",
                "production: labs=1 queue=2 active=Marinex1",
                "research: labs=1 queue=2 active=AdvancedTrainingx1",
                "rally: 14.0,10.0x1",
                "tech: AdvancedTrainingx1",
                "activity: builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q2/c1/x1 trainFails=1[queueFull=1] research=q1/c0/x1 researchFails=1[invalidTech=1] @15",
                "construction state: total=2 f1=2 f2=0 remaining=10 @15",
                "production events: e1/p2/c0/x1 @15",
                "research events: e1/p2/c0/x1 @15",
                "last ack: ok move[cli-9] @15",
                "left: select/drag   shift+left: add/remove/add-box   middle-drag/wheel: pan/zoom",
                "right: move/attack/harvest   ctrl+right: attackMove",
                "keys: 1/2 faction 3 observer 4-9 recall dblTap focus shift+4-9 set alt+4-9 add alt+0 clearGroups m/a/p/h u/i/o/l x/t/y z/c d j/k/q/e home-center end-faction [/] spc f f1 f2-select f3-type f4-role f5/f6/f7 f8/f9(+shift alt) f10 f11-all f12-idle n-prod v-combat tab esc"
            ),
            buildClientHudLines(
                snapshot = snapshot,
                state = ClientSessionState(
                    visionState = ClientVisionState(visibleTilesByFaction = mapOf(1 to (0 until 10).map { it to 0 }.toSet())),
                    selectedIds = linkedSetOf(4, 11, 12),
                    lastAck = ClientCommandAck(tick = 15, commandType = "move", requestId = "cli-9", accepted = true),
                    lastConstructionActivity = ClientConstructionActivity(tick = 15, total = 2, faction1 = 2, faction2 = 0, remainingTicks = 10),
                    lastProductionActivity = ClientProductionActivity(tick = 15, enqueue = 1, progress = 2, cancel = 1),
                    lastResearchActivity = ClientResearchActivity(tick = 15, enqueue = 1, progress = 2, cancel = 1),
                    lastTickActivity = ClientTickActivity(
                        tick = 15,
                        builds = 1,
                        buildsCancelled = 1,
                        buildFailures = 2,
                        buildFailureReasons = "invalidPlacement=1,insufficientResources=1",
                        trainsQueued = 2,
                        trainsCompleted = 1,
                        trainsCancelled = 1,
                        trainFailures = 1,
                        trainFailureReasons = "queueFull=1",
                        researchQueued = 1,
                        researchCancelled = 1,
                        researchFailures = 1,
                        researchFailureReasons = "invalidTech=1"
                    )
                )
            )
        )
    }

    @Test
    fun `builds economy summary for viewed faction`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10, minerals = 125, gas = 40, dropoffBuildings = 2)),
                entities = emptyList(),
                resourceNodes = emptyList()
            )

        assertEquals("economy: f1 minerals=125 gas=40 dropoffs=2", buildEconomySummary(snapshot, 1))
        assertEquals("economy: observer", buildEconomySummary(snapshot, null))
        assertEquals("economy: f2 missing", buildEconomySummary(snapshot, 2))
    }

    @Test
    fun `builds selection health summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection hp: 30/50 (60%)", buildSelectionHealthSummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection hp: none", buildSelectionHealthSummary(snapshot, emptySet()))
        assertEquals("selection hp: none", buildSelectionHealthSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection combat summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, weaponId = "Rifle", weaponCooldownTicks = 0),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Marine", x = 2f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, weaponId = "Rifle", weaponCooldownTicks = 4),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Worker", x = 3f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "selection combat: armed=2 ready=1 cooling=1 unarmed=1 nextReady=4",
            buildSelectionCombatSummary(snapshot, linkedSetOf(1, 2, 3))
        )
        assertEquals("selection combat: none", buildSelectionCombatSummary(snapshot, emptySet()))
        assertEquals("selection combat: none", buildSelectionCombatSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection capability summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, supportsTraining = true, supportsResearch = true),
                    EntitySnapshot(id = 2, faction = 1, typeId = "ResourceDepot", x = 2f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, supportsDropoff = true),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Worker", x = 3f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "capabilities: train=1 research=1 rally=0 dropoff=1",
            buildSelectionCapabilitySummary(snapshot, linkedSetOf(1, 2, 3))
        )
        assertEquals("capabilities: none", buildSelectionCapabilitySummary(snapshot, emptySet()))
        assertEquals("capabilities: none", buildSelectionCapabilitySummary(snapshot, linkedSetOf(99)))
        assertEquals("capabilities: basic", buildSelectionCapabilitySummary(snapshot, linkedSetOf(3)))
    }

    @Test
    fun `builds command affordance summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, supportsTraining = true),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Lab", x = 2f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, supportsResearch = true)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "commands: move=on train=on research=on viewSelect=on",
            buildCommandAffordanceSummary(snapshot, linkedSetOf(1, 2), viewedFaction = 1)
        )
        assertEquals(
            "commands: move=off train=off research=off viewSelect=off",
            buildCommandAffordanceSummary(snapshot, emptySet(), viewedFaction = null)
        )
    }

    @Test
    fun `builds selection archetype summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", archetype = "infantry", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", archetype = "worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection roles: infantryx1 workerx1", buildSelectionArchetypeSummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection roles: none", buildSelectionArchetypeSummary(snapshot, emptySet()))
        assertEquals("selection roles: none", buildSelectionArchetypeSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection position summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 3f, y = 2f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection pos: center=2.0,1.5 span=2.0x1.0", buildSelectionPositionSummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection pos: none", buildSelectionPositionSummary(snapshot, emptySet()))
        assertEquals("selection pos: none", buildSelectionPositionSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection order summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, orderQueueSize = 2, activeOrder = "move"),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, orderQueueSize = 1, activeOrder = "attackMove"),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Depot", x = 3f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("orders: queued=3 active=movex1 attackMovex1", buildSelectionOrderSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("orders: none", buildSelectionOrderSummary(snapshot, emptySet()))
        assertEquals("orders: none", buildSelectionOrderSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection vision summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, visionRange = 6f),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, visionRange = 5f)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection vision: avg=5.5 min=5.0 max=6.0", buildSelectionVisionSummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection vision: none", buildSelectionVisionSummary(snapshot, emptySet()))
        assertEquals("selection vision: none", buildSelectionVisionSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection durability summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 1),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection durability: avgArmor=0.5 damaged=1/2", buildSelectionDurabilitySummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection durability: none", buildSelectionDurabilitySummary(snapshot, emptySet()))
        assertEquals("selection durability: none", buildSelectionDurabilitySummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection cargo summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Worker", x = 1f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, harvestCargoKind = "minerals", harvestCargoAmount = 5),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, harvestCargoKind = "gas", harvestCargoAmount = 3)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection cargo: loaded=2 minerals=5 gas=3", buildSelectionCargoSummary(snapshot, linkedSetOf(1, 2)))
        assertEquals("selection cargo: none", buildSelectionCargoSummary(snapshot, emptySet()))
        assertEquals("selection cargo: none", buildSelectionCargoSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection mobility summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, activeOrder = "move", pathRemainingNodes = 4),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, activeOrder = "attackMove"),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Depot", x = 3f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection mobility: moving=2 pathing=1 stationary=1", buildSelectionMobilitySummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection mobility: none", buildSelectionMobilitySummary(snapshot, emptySet()))
        assertEquals("selection mobility: none", buildSelectionMobilitySummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection weapon summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, weaponId = "Rifle"),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Tank", x = 2f, y = 1f, dir = 0f, hp = 60, maxHp = 60, armor = 1, weaponId = "Cannon"),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Worker", x = 3f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection weapons: Riflex1 Cannonx1 unarmed=1", buildSelectionWeaponSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection weapons: none", buildSelectionWeaponSummary(snapshot, emptySet()))
        assertEquals("selection weapons: none", buildSelectionWeaponSummary(snapshot, linkedSetOf(3)))
    }

    @Test
    fun `builds selection path summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 40, armor = 0, pathRemainingNodes = 4, pathGoalX = 9, pathGoalY = 9),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, pathRemainingNodes = 2, pathGoalX = 9, pathGoalY = 9),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Depot", x = 3f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection paths: active=2 avg=3.0 topGoal=9,9", buildSelectionPathSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection paths: none", buildSelectionPathSummary(snapshot, emptySet()))
        assertEquals("selection paths: none", buildSelectionPathSummary(snapshot, linkedSetOf(3)))
    }

    @Test
    fun `builds selection target summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Worker", x = 1f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, buildTargetId = 40),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Worker", x = 2f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, harvestTargetNodeId = 9, harvestReturnTargetId = 60),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Worker", x = 3f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0, harvestTargetNodeId = 9)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection targets: build=1 harvestNodes=1 return=1", buildSelectionTargetSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection targets: none", buildSelectionTargetSummary(snapshot, emptySet()))
        assertEquals("selection targets: none", buildSelectionTargetSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection rally summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, supportsRally = true, rallyX = 10f, rallyY = 12f),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Factory", x = 3f, y = 1f, dir = 0f, hp = 300, maxHp = 300, armor = 2, supportsRally = true),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Marine", x = 2f, y = 2f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection rally: configured=1/2 top=10,12", buildSelectionRallySummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection rally: none", buildSelectionRallySummary(snapshot, emptySet()))
        assertEquals("selection rally: none", buildSelectionRallySummary(snapshot, linkedSetOf(3)))
    }

    @Test
    fun `builds selection queue summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, productionQueueSize = 2),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Lab", x = 2f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, researchQueueSize = 3),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Worker", x = 3f, y = 1f, dir = 0f, hp = 10, maxHp = 10, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection queues: prod=2@1 research=3@1", buildSelectionQueueSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection queues: none", buildSelectionQueueSummary(snapshot, emptySet()))
        assertEquals("selection queues: none", buildSelectionQueueSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds selection structure summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo",
                buildVersion = "test",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 120, maxHp = 120, armor = 1, underConstruction = true, footprintWidth = 2, footprintHeight = 2),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Factory", x = 3f, y = 1f, dir = 0f, hp = 300, maxHp = 300, armor = 2, underConstruction = false, footprintWidth = 3, footprintHeight = 2),
                    EntitySnapshot(id = 3, faction = 1, typeId = "Marine", x = 2f, y = 2f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection structures: total=2 constructing=1 area=10", buildSelectionStructureSummary(snapshot, linkedSetOf(1, 2, 3)))
        assertEquals("selection structures: none", buildSelectionStructureSummary(snapshot, emptySet()))
        assertEquals("selection structures: none", buildSelectionStructureSummary(snapshot, linkedSetOf(3)))
    }

    @Test
    fun `builds selection summary with unit type counts`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 6, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection: Marinex2 Workerx1", buildSelectionSummary(snapshot, linkedSetOf(4, 5, 6)))
        assertEquals("selection: none", buildSelectionSummary(snapshot, emptySet()))
    }

    @Test
    fun `builds selection faction summary`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 1, typeId = "Worker", archetype = "worker", x = 5f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0),
                    EntitySnapshot(id = 6, faction = 2, typeId = "Marine", archetype = "infantry", x = 6f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("selection factions: f1=2 f2=1", buildSelectionFactionSummary(snapshot, linkedSetOf(4, 5, 6)))
        assertEquals("selection factions: none", buildSelectionFactionSummary(snapshot, emptySet()))
        assertEquals("selection factions: none", buildSelectionFactionSummary(snapshot, linkedSetOf(99)))
    }

    @Test
    fun `builds builder summary from selected workers`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 6, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, buildTargetId = 41),
                    EntitySnapshot(id = 7, faction = 1, typeId = "Worker", archetype = "worker", x = 7f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, buildTargetId = 41),
                    EntitySnapshot(id = 8, faction = 1, typeId = "Marine", archetype = "infantry", x = 8f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("builders: active=2 targets=1", buildBuilderSummary(snapshot, linkedSetOf(6, 7, 8)))
        assertEquals("builders: none", buildBuilderSummary(snapshot, linkedSetOf(8)))
    }

    @Test
    fun `builds research summary from selected labs`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Depot", archetype = "producer", x = 7f, y = 4f, dir = 0f, hp = 400, maxHp = 400, armor = 1, researchQueueSize = 2, activeResearchTech = "AdvancedTraining", activeResearchRemainingTicks = 8),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Lab", archetype = "tech", x = 8f, y = 4f, dir = 0f, hp = 250, maxHp = 250, armor = 1, researchQueueSize = 1, activeResearchTech = "ArmorUp", activeResearchRemainingTicks = 3),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Marine", archetype = "infantry", x = 9f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "research: labs=2 queue=3 active=AdvancedTrainingx1 ArmorUpx1",
            buildResearchSummary(snapshot, linkedSetOf(12, 13, 14))
        )
        assertEquals("research: none", buildResearchSummary(snapshot, linkedSetOf(14)))
    }

    @Test
    fun `builds rally summary from selected producers`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Depot", archetype = "producer", x = 7f, y = 4f, dir = 0f, hp = 400, maxHp = 400, armor = 1, rallyX = 14f, rallyY = 10f),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Factory", archetype = "producer", x = 8f, y = 4f, dir = 0f, hp = 250, maxHp = 250, armor = 1, rallyX = 14f, rallyY = 10f),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Marine", archetype = "infantry", x = 9f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("rally: 14.0,10.0x2", buildRallySummary(snapshot, linkedSetOf(12, 13, 14)))
        assertEquals("rally: none", buildRallySummary(snapshot, linkedSetOf(14)))
    }

    @Test
    fun `builds command panel buttons for selection state`() {
        assertEquals(
            listOf("move", "attackMove", "patrol", "hold", "train:Worker", "train:Marine", "train:Zergling", "research:AdvancedTraining", "cancelBuild", "cancelTrain", "cancelResearch", "build:Depot", "build:ResourceDepot", "build:GasDepot", "play:pause", "play:slower", "play:faster", "preset:save:quick", "preset:load:quick", "preset:save:alt", "preset:load:alt", "preset:menu", "scenario:menu", "scenario:prev", "scenario:next", "help:toggle", "view:centerSelection", "view:centerFaction", "view:resetCamera", "view:faction1", "view:faction2", "view:observer", "select:viewFaction", "select:selectedType", "select:selectedArchetype", "select:all", "select:idleWorkers", "select:damaged", "select:combat", "select:producers", "select:trainers", "select:researchers", "select:construction", "select:harvesters", "select:returningHarvesters", "select:cargoHarvesters", "select:dropoffs", "groups:clear", "clear"),
            buildCommandButtons(testCatalog, true, canTrain = true, canResearch = true).map { it.actionId }
        )
        assertEquals(
            listOf("move", "attackMove", "patrol", "hold", "cancelBuild", "cancelTrain", "cancelResearch", "build:Depot", "build:ResourceDepot", "build:GasDepot", "play:pause", "play:slower", "play:faster", "preset:save:quick", "preset:load:quick", "preset:save:alt", "preset:load:alt", "preset:menu", "scenario:menu", "scenario:prev", "scenario:next", "help:toggle", "view:centerSelection", "view:centerFaction", "view:resetCamera", "view:faction1", "view:faction2", "view:observer", "select:viewFaction", "select:selectedType", "select:selectedArchetype", "select:all", "select:idleWorkers", "select:damaged", "select:combat", "select:producers", "select:trainers", "select:researchers", "select:construction", "select:harvesters", "select:returningHarvesters", "select:cargoHarvesters", "select:dropoffs", "groups:clear", "clear"),
            buildCommandButtons(testCatalog, true, canTrain = false, canResearch = false).map { it.actionId }
        )
        assertEquals(
            listOf("build:Depot", "build:ResourceDepot", "build:GasDepot", "play:pause", "play:slower", "play:faster", "preset:save:quick", "preset:load:quick", "preset:save:alt", "preset:load:alt", "preset:menu", "scenario:menu", "scenario:prev", "scenario:next", "help:toggle", "view:centerSelection", "view:centerFaction", "view:resetCamera", "view:faction1", "view:faction2", "view:observer", "select:viewFaction", "select:selectedType", "select:selectedArchetype", "select:all", "select:idleWorkers", "select:damaged", "select:combat", "select:producers", "select:trainers", "select:researchers", "select:construction", "select:harvesters", "select:returningHarvesters", "select:cargoHarvesters", "select:dropoffs", "groups:clear", "clear"),
            buildCommandButtons(testCatalog, false, canTrain = false, canResearch = false).map { it.actionId }
        )
    }

    @Test
    fun `builds command panel status lines from overlay`() {
        assertEquals(
            listOf("play: paused x2", "scenario: gas", "mode: attack-move", "view: faction 1"),
            buildCommandPanelStatusLines(listOf("camera: zoom=1.0", "play: paused x2", "scenario: gas", "mode: attack-move", "view: faction 1"))
        )
        assertEquals(
            listOf("play: paused x2", "scenario: gas", "mode: build:Depot", "scenario menu: up/down choose  enter restart  tab close", "preset menu: s save  l/enter load  f10 close", "help: f1 close  tab scenario menu  f10 preset menu", "selection hud: Workerx2 Marinex1", "hint: Queue a move order with right click or ground mode", "groups: 4=3 5=2", "presets: quick=ready alt=missing", "notice: preset loaded: quick", "view: faction 1"),
            buildCommandPanelStatusLines(listOf("play: paused x2", "scenario: gas", "mode: build:Depot", "scenario menu: up/down choose  enter restart  tab close", "preset menu: s save  l/enter load  f10 close", "help: f1 close  tab scenario menu  f10 preset menu", "selection hud: Workerx2 Marinex1", "hint: Queue a move order with right click or ground mode", "groups: 4=3 5=2", "presets: quick=ready alt=missing", "notice: preset loaded: quick", "view: faction 1"))
        )
    }

    @Test
    fun `fits long command panel status lines`() {
        assertEquals("short line", fitCommandPanelStatusLine("short line"))
        assertEquals("hint: Queue a move or...", fitCommandPanelStatusLine("hint: Queue a move order with right click or ground mode"))
        assertEquals("a...", fitCommandPanelStatusLine("abcdef", maxChars = 4))
        assertEquals("abcdef", fitCommandPanelStatusLine("abcdef", maxChars = 3))
    }

    @Test
    fun `detects active command buttons from overlay state`() {
        val overlay =
            listOf(
                "mode: build:Depot",
                "view: faction 2",
                "preset menu: s save  l/enter load  f10 close",
                "scenario menu: choose scenario",
                "help: f1 close"
            )

        assertTrue(isCommandButtonActive("build:Depot", overlay))
        assertTrue(isCommandButtonActive("view:faction2", overlay))
        assertTrue(isCommandButtonActive("preset:menu", overlay))
        assertTrue(isCommandButtonActive("scenario:menu", overlay))
        assertTrue(isCommandButtonActive("help:toggle", overlay))
        assertFalse(isCommandButtonActive("move", overlay))
        assertFalse(isCommandButtonActive("view:observer", overlay))
    }

    @Test
    fun `evaluates command button availability`() {
        assertFalse(isCommandButtonEnabled("move", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = 1))
        assertTrue(isCommandButtonEnabled("move", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1))
        assertFalse(isCommandButtonEnabled("train:Marine", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1))
        assertTrue(isCommandButtonEnabled("train:Marine", hasSelection = true, canTrain = true, canResearch = false, viewedFaction = 1))
        assertFalse(isCommandButtonEnabled("research:AdvancedTraining", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1))
        assertFalse(isCommandButtonEnabled("select:viewFaction", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = null))
        assertTrue(isCommandButtonEnabled("select:viewFaction", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = 2))
        assertFalse(isCommandButtonEnabled("view:centerFaction", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = null))
        assertTrue(isCommandButtonEnabled("view:centerFaction", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = 1))
    }

    @Test
    fun `builds start overlay lines for early ticks`() {
        assertEquals(
            listOf(
                "Match start: LMB select, RMB command, Tab scenario menu",
                "play: running x1",
                "scenario: skirmish"
            ),
            buildStartOverlayLines(20, listOf("play: running x1", "scenario: skirmish"))
        )
        assertEquals(emptyList<String>(), buildStartOverlayLines(80, listOf("play: running x1", "scenario: skirmish")))
    }

    @Test
    fun `locates command button by panel click`() {
        val move = commandButtonAt(width = 640, x = 640 - 150, y = 82, catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val pause = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (14 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val presetLoad = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (18 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val presetMenu = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (21 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val scenarioMenu = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (22 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val scenarioNext = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (24 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val help = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (25 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val center = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (26 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val centerFaction = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (27 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val resetCamera = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (28 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val viewF1 = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (29 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val viewF2 = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (30 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val viewObserver = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (31 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val selectView = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (32 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val selectType = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (33 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val selectArchetype = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (34 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val selectAll = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (35 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val idleWorkers = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (36 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val damaged = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (37 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val combat = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (38 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val producers = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (39 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val trainers = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (40 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val researchers = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (41 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val construction = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (42 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val harvesters = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (43 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val returningHarvesters = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (44 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val cargoHarvesters = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (45 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val dropoffs = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (46 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val clearGroups = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (47 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)
        val clear = commandButtonAt(width = 640, x = 640 - 150, y = 82 + (48 * 34), catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true)

        assertEquals("move", move?.actionId)
        assertEquals("play:pause", pause?.actionId)
        assertEquals("preset:load:quick", presetLoad?.actionId)
        assertEquals("preset:menu", presetMenu?.actionId)
        assertEquals("scenario:menu", scenarioMenu?.actionId)
        assertEquals("scenario:next", scenarioNext?.actionId)
        assertEquals("help:toggle", help?.actionId)
        assertEquals("view:centerSelection", center?.actionId)
        assertEquals("view:centerFaction", centerFaction?.actionId)
        assertEquals("view:resetCamera", resetCamera?.actionId)
        assertEquals("view:faction1", viewF1?.actionId)
        assertEquals("view:faction2", viewF2?.actionId)
        assertEquals("view:observer", viewObserver?.actionId)
        assertEquals("select:viewFaction", selectView?.actionId)
        assertEquals("select:selectedType", selectType?.actionId)
        assertEquals("select:selectedArchetype", selectArchetype?.actionId)
        assertEquals("select:all", selectAll?.actionId)
        assertEquals("select:idleWorkers", idleWorkers?.actionId)
        assertEquals("select:damaged", damaged?.actionId)
        assertEquals("select:combat", combat?.actionId)
        assertEquals("select:producers", producers?.actionId)
        assertEquals("select:trainers", trainers?.actionId)
        assertEquals("select:researchers", researchers?.actionId)
        assertEquals("select:construction", construction?.actionId)
        assertEquals("select:harvesters", harvesters?.actionId)
        assertEquals("select:returningHarvesters", returningHarvesters?.actionId)
        assertEquals("select:cargoHarvesters", cargoHarvesters?.actionId)
        assertEquals("select:dropoffs", dropoffs?.actionId)
        assertEquals("groups:clear", clearGroups?.actionId)
        assertEquals("clear", clear?.actionId)
        assertEquals(null, commandButtonAt(width = 640, x = 20, y = 20, catalog = testCatalog, statusLineCount = 2, hasSelection = true, canTrain = true, canResearch = true))
    }

    @Test
    fun `builds command button tooltips`() {
        assertEquals("Advance and auto-engage enemies along the way", commandButtonTooltip("attackMove"))
        assertEquals("Enter placement mode for GasDepot", commandButtonTooltip("build:GasDepot"))
        assertEquals("Queue Marine on the first selected producer", commandButtonTooltip("train:Marine"))
        assertEquals("Load preset quick and restart into it", commandButtonTooltip("preset:load:quick"))
        assertEquals("Save the current scenario and speed into preset alt", commandButtonTooltip("preset:save:alt"))
        assertEquals("Open the preset menu to save/load quick or alt slots", commandButtonTooltip("preset:menu"))
        assertEquals("Open the scenario picker to restart into another setup", commandButtonTooltip("scenario:menu"))
        assertEquals("Switch to the next play scenario and restart", commandButtonTooltip("scenario:next"))
        assertEquals("Toggle in-game help and key hints", commandButtonTooltip("help:toggle"))
        assertEquals("Center camera on the current selection", commandButtonTooltip("view:centerSelection"))
        assertEquals("Center camera on the viewed faction", commandButtonTooltip("view:centerFaction"))
        assertEquals("Reset camera pan and zoom", commandButtonTooltip("view:resetCamera"))
        assertEquals("Switch camera/control focus to faction 1", commandButtonTooltip("view:faction1"))
        assertEquals("Switch camera/control focus to faction 2", commandButtonTooltip("view:faction2"))
        assertEquals("Switch camera to observer mode", commandButtonTooltip("view:observer"))
        assertEquals("Select all units for the currently viewed faction", commandButtonTooltip("select:viewFaction"))
        assertEquals("Select all units matching the first selected unit type", commandButtonTooltip("select:selectedType"))
        assertEquals("Select all units matching the first selected unit archetype", commandButtonTooltip("select:selectedArchetype"))
        assertEquals("Select all units on the current snapshot", commandButtonTooltip("select:all"))
        assertEquals("Select idle worker units in the current view scope", commandButtonTooltip("select:idleWorkers"))
        assertEquals("Select damaged units in the current view scope", commandButtonTooltip("select:damaged"))
        assertEquals("Select combat-capable units in the current view scope", commandButtonTooltip("select:combat"))
        assertEquals("Select producer buildings in the current view scope", commandButtonTooltip("select:producers"))
        assertEquals("Select buildings that support training in the current view scope", commandButtonTooltip("select:trainers"))
        assertEquals("Select buildings that support research in the current view scope", commandButtonTooltip("select:researchers"))
        assertEquals("Select buildings that are still under construction", commandButtonTooltip("select:construction"))
        assertEquals("Select worker units currently harvesting or returning cargo", commandButtonTooltip("select:harvesters"))
        assertEquals("Select worker units currently returning cargo", commandButtonTooltip("select:returningHarvesters"))
        assertEquals("Select worker units currently carrying cargo", commandButtonTooltip("select:cargoHarvesters"))
        assertEquals("Select buildings that accept resource drop-offs", commandButtonTooltip("select:dropoffs"))
        assertEquals("Clear all control groups", commandButtonTooltip("groups:clear"))
        assertEquals(null, commandButtonTooltip("unknown"))
    }

    @Test
    fun `builds cancel intents from selected buildings`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Depot", archetype = "producer", x = 7f, y = 4f, dir = 0f, hp = 120, maxHp = 400, armor = 1, underConstruction = true),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Depot", archetype = "producer", x = 8f, y = 4f, dir = 0f, hp = 400, maxHp = 400, armor = 1, productionQueueSize = 2, researchQueueSize = 1)
                ),
                resourceNodes = emptyList()
            )

        val ids = ClientCommandIds("cancel")
        assertEquals(12, buildCancelIntent(snapshot, linkedSetOf(12, 13), "cancelBuild", ids)?.record?.buildingId)
        assertEquals("cancel-2", buildCancelIntent(snapshot, linkedSetOf(12, 13), "cancelTrain", ids)?.record?.requestId)
        assertEquals(13, buildCancelIntent(snapshot, linkedSetOf(12, 13), "cancelResearch", ids)?.record?.buildingId)
    }

    @Test
    fun `builds queue intents from selected buildings`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Depot", archetype = "producer", x = 7f, y = 4f, dir = 0f, hp = 400, maxHp = 400, armor = 1, supportsTraining = true, supportsResearch = true),
                    EntitySnapshot(id = 13, faction = 1, typeId = "ResourceDepot", archetype = "econDepot", x = 8f, y = 4f, dir = 0f, hp = 350, maxHp = 350, armor = 1)
                ),
                resourceNodes = emptyList()
            )

        val ids = ClientCommandIds("queue")
        assertEquals("Worker", buildQueueIntent(snapshot, linkedSetOf(12, 13), "train", "Worker", ids)?.record?.typeId)
        assertEquals("Marine", buildQueueIntent(snapshot, linkedSetOf(12, 13), "train", "Marine", ids)?.record?.typeId)
        assertEquals("Zergling", buildQueueIntent(snapshot, linkedSetOf(12, 13), "train", "Zergling", ids)?.record?.typeId)
        assertEquals(12, buildQueueIntent(snapshot, linkedSetOf(12, 13), "research", "AdvancedTraining", ids)?.record?.buildingId)
    }

    @Test
    fun `builds production summary from selected producers`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Depot", archetype = "producer", x = 7f, y = 4f, dir = 0f, hp = 400, maxHp = 400, armor = 1, productionQueueSize = 2, activeProductionType = "Marine", activeProductionRemainingTicks = 8),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Lab", archetype = "producer", x = 8f, y = 4f, dir = 0f, hp = 250, maxHp = 250, armor = 1, productionQueueSize = 1, activeProductionType = "Medic", activeProductionRemainingTicks = 3),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Marine", archetype = "infantry", x = 9f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "production: labs=2 queue=3 active=Marinex1 Medicx1",
            buildProductionSummary(snapshot, linkedSetOf(12, 13, 14))
        )
        assertEquals("production: none", buildProductionSummary(snapshot, linkedSetOf(14)))
    }

    @Test
    fun `builds tech summary from unlocked faction techs`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(
                    FactionSnapshot(faction = 1, visibleTiles = 10, unlockedTechIds = listOf("AdvancedTraining", "ArmorUp")),
                    FactionSnapshot(faction = 2, visibleTiles = 8, unlockedTechIds = listOf("ArmorUp"))
                ),
                entities = emptyList(),
                resourceNodes = emptyList()
            )

        assertEquals("tech: AdvancedTrainingx1 ArmorUpx2", buildTechSummary(snapshot))
        assertEquals(
            "tech: none",
            buildTechSummary(
                snapshot.copy(factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)))
            )
        )
    }

    @Test
    fun `builds construction summary from selected sites`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(
                        id = 12,
                        faction = 1,
                        typeId = "Depot",
                        archetype = "producer",
                        x = 7f,
                        y = 4f,
                        dir = 0f,
                        hp = 120,
                        maxHp = 400,
                        armor = 1,
                        underConstruction = true,
                        constructionRemainingTicks = 6,
                        constructionTotalTicks = 10
                    ),
                    EntitySnapshot(
                        id = 13,
                        faction = 1,
                        typeId = "Factory",
                        archetype = "producer",
                        x = 8f,
                        y = 4f,
                        dir = 0f,
                        hp = 80,
                        maxHp = 300,
                        armor = 1,
                        underConstruction = true,
                        constructionRemainingTicks = 4,
                        constructionTotalTicks = 8
                    ),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Marine", archetype = "infantry", x = 9f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(
            "construction: sites=2 remaining=10 Depotx1 Factoryx1",
            buildConstructionSummary(snapshot, linkedSetOf(12, 13, 14))
        )
        assertEquals("construction: none", buildConstructionSummary(snapshot, linkedSetOf(14)))
    }

    @Test
    fun `builds task summary from selected workers`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Worker", archetype = "worker", x = 7f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, buildTargetId = 31),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Worker", archetype = "worker", x = 8f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, harvestPhase = "gather", harvestTargetNodeId = 50),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Worker", archetype = "worker", x = 9f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, harvestPhase = "return", harvestReturnTargetId = 60)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("tasks: build=1 gather=1 return=1", buildTaskSummary(snapshot, linkedSetOf(12, 13, 14)))
        assertEquals("tasks: none", buildTaskSummary(snapshot, emptySet()))
    }

    @Test
    fun `builds path summary from selected units`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 12, faction = 1, typeId = "Marine", archetype = "infantry", x = 7f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0, pathRemainingNodes = 6, pathGoalX = 14, pathGoalY = 10),
                    EntitySnapshot(id = 13, faction = 1, typeId = "Worker", archetype = "worker", x = 8f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, pathRemainingNodes = 4, pathGoalX = 14, pathGoalY = 10),
                    EntitySnapshot(id = 14, faction = 1, typeId = "Marine", archetype = "infantry", x = 9f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("paths: active=2 remaining=10 goals=14,10x2", buildPathSummary(snapshot, linkedSetOf(12, 13, 14)))
        assertEquals("paths: none", buildPathSummary(snapshot, linkedSetOf(14)))
    }

    @Test
    fun `builds selected entity status labels`() {
        assertEquals(
            "build 6",
            buildEntityStatusLabel(
                EntitySnapshot(
                    id = 12,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 7f,
                    y = 4f,
                    dir = 0f,
                    hp = 120,
                    maxHp = 400,
                    armor = 1,
                    underConstruction = true,
                    constructionRemainingTicks = 6
                )
            )
        )
        assertEquals(
            "train Marine 8",
            buildEntityStatusLabel(
                EntitySnapshot(
                    id = 13,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 7f,
                    y = 4f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    activeProductionType = "Marine",
                    activeProductionRemainingTicks = 8
                )
            )
        )
        assertEquals(
            "research AdvancedTraining 5",
            buildEntityStatusLabel(
                EntitySnapshot(
                    id = 14,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 7f,
                    y = 4f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    activeResearchTech = "AdvancedTraining",
                    activeResearchRemainingTicks = 5
                )
            )
        )
        assertEquals(
            null,
            buildEntityStatusLabel(
                EntitySnapshot(
                    id = 15,
                    faction = 1,
                    typeId = "Marine",
                    archetype = "infantry",
                    x = 7f,
                    y = 4f,
                    dir = 0f,
                    hp = 45,
                    maxHp = 45,
                    armor = 0
                )
            )
        )
    }

    @Test
    fun `builds simple victory and defeat states`() {
        val victorySnapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", archetype = "infantry", x = 2f, y = 2f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )
        val defeatSnapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities = listOf(
                    EntitySnapshot(id = 2, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 3f, y = 2f, dir = 0f, hp = 35, maxHp = 35, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals("Victory", buildGameState(victorySnapshot, 1)?.title)
        assertEquals("Victory", buildGameState(defeatSnapshot, 2)?.title)
        assertEquals(null, buildGameState(victorySnapshot.copy(entities = victorySnapshot.entities + EntitySnapshot(id = 3, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 4f, y = 4f, dir = 0f, hp = 35, maxHp = 35, armor = 0)), 1))
        assertEquals(null, buildGameState(victorySnapshot, null))
    }

    @Test
    fun `builds fog summary from streamed vision state`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 8,
                mapHeight = 8,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = emptyList(),
                resourceNodes = emptyList()
            )

        assertEquals(
            "fog: f1 visible=3 hidden=61",
            buildFogSummary(snapshot, ClientVisionState(visibleTilesByFaction = mapOf(1 to setOf(1 to 1, 2 to 2, 3 to 3))), 1)
        )
        assertEquals("fog: observer", buildFogSummary(snapshot, null, null))
    }

    @Test
    fun `builds unit selection records for client input`() {
        val record = buildUnitSelectionRecord(8, linkedSetOf(4, 9))

        assertEquals(8, record.tick)
        assertEquals("units", record.selectionType)
        assertArrayEquals(intArrayOf(4, 9), record.units)
    }

    @Test
    fun `builds faction selection records for client input`() {
        val record = buildFactionSelectionRecord(11, 2)

        assertEquals(11, record.tick)
        assertEquals("faction", record.selectionType)
        assertEquals(2, record.faction)
    }

    @Test
    fun `builds type selection records for client input`() {
        val record = buildTypeSelectionRecord(13, "Marine")

        assertEquals(13, record.tick)
        assertEquals("type", record.selectionType)
        assertEquals("Marine", record.typeId)
    }

    @Test
    fun `builds archetype selection records for client input`() {
        val record = buildArchetypeSelectionRecord(14, "worker")

        assertEquals(14, record.tick)
        assertEquals("archetype", record.selectionType)
        assertEquals("worker", record.archetype)
    }

    @Test
    fun `builds all selection records for client input`() {
        val record = buildAllSelectionRecord(15)

        assertEquals(15, record.tick)
        assertEquals("all", record.selectionType)
    }

    @Test
    fun `collects faction selection ids from snapshot`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 2, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 6, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertArrayEquals(intArrayOf(4, 6), collectFactionSelectionIds(snapshot, 1))
        assertArrayEquals(intArrayOf(5), collectFactionSelectionIds(snapshot, 2))
        assertArrayEquals(intArrayOf(4), collectTypeSelectionIds(snapshot, "Marine", 1))
        assertArrayEquals(intArrayOf(5), collectTypeSelectionIds(snapshot, "Marine", 2))
        assertArrayEquals(intArrayOf(4), collectArchetypeSelectionIds(snapshot, "infantry", 1))
        assertArrayEquals(intArrayOf(), collectArchetypeSelectionIds(snapshot, "worker", 2))
        assertArrayEquals(intArrayOf(4, 5, 6), collectAllSelectionIds(snapshot))
        assertArrayEquals(intArrayOf(6), collectIdleWorkerSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(6), collectIdleWorkerSelectionIds(snapshot, 1))
        assertArrayEquals(intArrayOf(), collectIdleWorkerSelectionIds(snapshot, 2))
        assertArrayEquals(intArrayOf(4, 5, 6), collectDamagedSelectionIds(snapshot.copy(entities = snapshot.entities.map { it.copy(hp = it.hp - 1) }), null))
        assertArrayEquals(intArrayOf(), collectDamagedSelectionIds(snapshot, 1))
        assertArrayEquals(intArrayOf(), collectCombatSelectionIds(snapshot, 1))
        assertArrayEquals(intArrayOf(4, 5), collectCombatSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 4 || it.id == 5) it.copy(weaponId = "Rifle") else it
        }), null))
        assertArrayEquals(intArrayOf(), collectProducerSelectionIds(snapshot, 1))
        assertArrayEquals(intArrayOf(6), collectProducerSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 6) it.copy(archetype = "producer") else it
        }), null))
        assertArrayEquals(intArrayOf(), collectTrainingSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(4), collectTrainingSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 4) it.copy(supportsTraining = true) else it
        }), null))
        assertArrayEquals(intArrayOf(), collectResearchSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(5), collectResearchSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 5) it.copy(supportsResearch = true) else it
        }), null))
        assertArrayEquals(intArrayOf(), collectConstructionSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(4), collectConstructionSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 4) it.copy(underConstruction = true) else it
        }), null))
        assertArrayEquals(intArrayOf(), collectHarvesterSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(6), collectHarvesterSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 6) it.copy(harvestPhase = "gather") else it
        }), null))
        assertArrayEquals(intArrayOf(), collectReturningHarvesterSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(6), collectReturningHarvesterSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 6) it.copy(harvestPhase = "return") else it
        }), null))
        assertArrayEquals(intArrayOf(), collectCargoHarvesterSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(6), collectCargoHarvesterSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 6) it.copy(harvestCargoAmount = 3) else it
        }), null))
        assertArrayEquals(intArrayOf(), collectDropoffSelectionIds(snapshot, null))
        assertArrayEquals(intArrayOf(4), collectDropoffSelectionIds(snapshot.copy(entities = snapshot.entities.map {
            if (it.id == 4) it.copy(supportsDropoff = true) else it
        }), null))
    }

    @Test
    fun `maps control group key codes`() {
        assertEquals(4, controlGroupFromKeyCode(java.awt.event.KeyEvent.VK_4))
        assertEquals(9, controlGroupFromKeyCode(java.awt.event.KeyEvent.VK_9))
        assertEquals(null, controlGroupFromKeyCode(java.awt.event.KeyEvent.VK_3))
    }

    @Test
    fun `stores and recalls control group ids`() {
        val groups = arrayOfNulls<IntArray>(10)
        assignControlGroupSlot(groups, 5, linkedSetOf(4, 9))
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 6, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertArrayEquals(intArrayOf(4), recallControlGroupSlot(groups, 5, snapshot))
        assertArrayEquals(intArrayOf(), recallControlGroupSlot(groups, 4, snapshot))
    }

    @Test
    fun `merges control group ids without duplicates`() {
        val groups = arrayOfNulls<IntArray>(10)
        assignControlGroupSlot(groups, 4, linkedSetOf(1, 2))
        mergeControlGroupSlot(groups, 4, linkedSetOf(2, 3))
        assertArrayEquals(intArrayOf(1, 2, 3), groups[4])
    }

    @Test
    fun `clears control group slots`() {
        val groups = arrayOfNulls<IntArray>(10)
        groups[4] = intArrayOf(1)
        groups[9] = intArrayOf(2, 3)
        clearControlGroupSlots(groups)
        assertEquals(null, groups[4])
        assertEquals(null, groups[9])
    }

    @Test
    fun `formats control group summary`() {
        val groups = arrayOfNulls<IntArray>(10)
        groups[4] = intArrayOf(1, 2, 3)
        groups[6] = intArrayOf(7)
        assertEquals("4=3 6=1", formatControlGroupSummary(groups))
        assertEquals("4=3 *6=1", formatControlGroupSummary(groups, highlightedGroup = 6))
        assertEquals("*4=3 6=1", formatControlGroupSummary(groups, highlightedGroup = 4))
        assertEquals(null, formatControlGroupSummary(arrayOfNulls(10)))
    }

    @Test
    fun `computes centroid for selected ids`() {
        val snapshot =
            ClientSnapshot(
                tick = 1,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 1)),
                entities = listOf(
                    EntitySnapshot(id = 1, faction = 1, typeId = "Marine", x = 2f, y = 4f, dir = 0f, hp = 1, maxHp = 1, armor = 0),
                    EntitySnapshot(id = 2, faction = 1, typeId = "Marine", x = 6f, y = 8f, dir = 0f, hp = 1, maxHp = 1, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        assertEquals(Pair(4f, 6f), computeSelectionCentroid(snapshot, intArrayOf(1, 2)))
        assertEquals(null, computeSelectionCentroid(snapshot, intArrayOf(99)))
    }

    @Test
    fun `expires control group highlight by ttl`() {
        assertEquals(4, activeControlGroupHighlight(lastGroup = 4, lastRecallAtNanos = 100L, nowNanos = 200L, ttlNanos = 200L))
        assertEquals(null, activeControlGroupHighlight(lastGroup = 4, lastRecallAtNanos = 100L, nowNanos = 400L, ttlNanos = 200L))
        assertEquals(null, activeControlGroupHighlight(lastGroup = null, lastRecallAtNanos = 100L, nowNanos = 200L, ttlNanos = 200L))
    }

    @Test
    fun `computes health bar fill width safely`() {
        assertEquals(10, healthBarFillWidth(20, 10, 20))
        assertEquals(20, healthBarFillWidth(20, 50, 20))
        assertEquals(0, healthBarFillWidth(20, 0, 20))
        assertEquals(0, healthBarFillWidth(20, 10, 0))
    }

    @Test
    fun `computes minimap geometry from world state`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 16,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = emptyList(),
                resourceNodes = emptyList()
            )
        val bounds = miniMapBounds(width = 640, height = 640)
        val point = miniMapPoint(bounds, snapshot, 16f, 8f)
        val viewport = miniMapViewport(bounds, snapshot, CameraView(panX = 0f, panY = 0f, zoom = 1f, baseTileSize = 20), 320, 160)

        assertEquals(144, bounds.width)
        assertEquals(144, bounds.height)
        assertEquals(bounds.x + 72, point.x)
        assertEquals(bounds.y + 72, point.y)
        assertEquals(72, viewport.width)
        assertEquals(72, viewport.height)
    }

    @Test
    fun `builds hold intent from current selection`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = emptyList(),
                resourceNodes = emptyList()
            )

        val intent = buildHoldIntent(snapshot, linkedSetOf(4, 5), ClientCommandIds("test"))

        assertEquals("hold", intent?.record?.commandType)
        assertEquals("test-1", intent?.record?.requestId)
        assertArrayEquals(intArrayOf(4, 5), intent?.record?.units)
    }

    @Test
    fun `build preview validity uses map and footprint blockers`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(
                        id = 12,
                        faction = 1,
                        typeId = "Depot",
                        archetype = "producer",
                        x = 7f,
                        y = 4f,
                        dir = 0f,
                        hp = 400,
                        maxHp = 400,
                        armor = 1,
                        footprintWidth = 2,
                        footprintHeight = 2,
                        placementClearance = 1
                    )
                ),
                resourceNodes = emptyList()
            )
        val mapState = ClientMapState(width = 32, height = 32, blockedTiles = setOf(1 to 1), staticOccupancyTiles = setOf(2 to 2))
        val depot = buildPreviewSpec("Depot")

        assertEquals(true, isBuildPreviewValid(mapState, snapshot, depot, 12, 12))
        assertEquals(false, isBuildPreviewValid(mapState, snapshot, depot, 1, 1))
        assertEquals(false, isBuildPreviewValid(mapState, snapshot, depot, 2, 2))
        assertEquals(false, isBuildPreviewValid(mapState, snapshot, depot, 7, 4))
    }

    @Test
    fun `build preview spec carries cost and label details`() {
        val depot = buildPreviewSpec("Depot")
        val label = buildPreviewLabel(depot, valid = true)

        assertEquals(100, depot?.mineralCost)
        assertEquals(0, depot?.gasCost)
        assertEquals("Depot", label?.title)
        assertEquals("cost=100/0", label?.cost)
        assertEquals("size=2x2 clr=1", label?.size)
        assertEquals(true, label?.valid)
    }

    @Test
    fun `camera converts between world and screen coordinates`() {
        val camera = CameraView(panX = 40f, panY = 10f, zoom = 1.5f, baseTileSize = 20)

        assertEquals(130f, camera.worldToScreenX(3f))
        assertEquals(70f, camera.worldToScreenY(2f))
        assertEquals(3f, camera.screenToWorldX(130f))
        assertEquals(2f, camera.screenToWorldY(70f))
    }

    @Test
    fun `zoom keeps cursor world position stable`() {
        val camera = CameraView(panX = 20f, panY = 30f, zoom = 1f, baseTileSize = 20)

        val zoomed = zoomCameraAt(camera, screenX = 120f, screenY = 90f, zoomFactor = 1.5f)

        assertEquals(camera.screenToWorldX(120f), zoomed.screenToWorldX(120f))
        assertEquals(camera.screenToWorldY(90f), zoomed.screenToWorldY(90f))
    }

    @Test
    fun `recenters camera on requested world position`() {
        val camera = CameraView(zoom = 1.5f, baseTileSize = 20)

        val centered = centerCameraOnWorld(camera, viewportWidth = 640, viewportHeight = 480, worldX = 10f, worldY = 6f)

        assertEquals(320f, centered.worldToScreenX(10f))
        assertEquals(240f, centered.worldToScreenY(6f))
    }

    @Test
    fun `applies additive selection clicks`() {
        val selected = linkedSetOf(4, 9)

        applySelectionClick(selected, clickedId = 12, additive = true)
        assertEquals(linkedSetOf(4, 9, 12), selected)

        applySelectionClick(selected, clickedId = 9, additive = true)
        assertEquals(linkedSetOf(4, 12), selected)

        applySelectionClick(selected, clickedId = 7, additive = false)
        assertEquals(linkedSetOf(7), selected)

        applySelectionClick(selected, clickedId = null, additive = false)
        assertEquals(linkedSetOf<Int>(), selected)
    }

    @Test
    fun `selects friendly units in drag box`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 1, typeId = "Worker", archetype = "worker", x = 5f, y = 5f, dir = 0f, hp = 20, maxHp = 20, armor = 0),
                    EntitySnapshot(id = 9, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 4.5f, y = 4.5f, dir = 0f, hp = 35, maxHp = 35, armor = 0)
                ),
                resourceNodes = emptyList()
            )
        val selected = linkedSetOf(12)

        val intent = selectEntitiesInBox(snapshot, selected, 1, 3.5f, 3.5f, 5.2f, 5.2f, additiveSelection = false)

        assertEquals(linkedSetOf(4, 5), selected)
        assertArrayEquals(intArrayOf(4, 5), intent.record.units)
    }

    @Test
    fun `adds drag box selection when additive`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 5, faction = 1, typeId = "Worker", archetype = "worker", x = 5f, y = 5f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
                ),
                resourceNodes = emptyList()
            )
        val selected = linkedSetOf(12)

        selectEntitiesInBox(snapshot, selected, 1, 3.5f, 3.5f, 5.2f, 5.2f, additiveSelection = true)

        assertEquals(linkedSetOf(12, 4, 5), selected)
    }

    @Test
    fun `observer selection can select non-faction-one units`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 9, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 4.5f, y = 4.5f, dir = 0f, hp = 35, maxHp = 35, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        val selected = linkedSetOf<Int>()
        val intent = selectEntitiesInBox(snapshot, selected, null, 4.4f, 4.4f, 4.6f, 4.6f, additiveSelection = false)

        assertEquals(linkedSetOf(9), selected)
        assertArrayEquals(intArrayOf(9), intent.record.units)
    }

    @Test
    fun `parses client stream updates through shared bridge`() {
        val ack =
            parseClientStreamLine(
                "{\"recordType\":\"commandAck\",\"tick\":13,\"commandType\":\"move\",\"requestId\":\"cli-7\",\"accepted\":true,\"reason\":null}"
            )

        assertNotNull(ack)
        assertEquals("move", ack?.ack?.commandType)
        assertEquals("cli-7", ack?.ack?.requestId)
        assertNull(ack?.snapshot)
    }

    @Test
    fun `parses research stream updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"research\",\"tick\":14,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"techId\":\"AdvancedTraining\",\"remainingTicks\":60},{\"kind\":\"cancel\",\"buildingId\":1,\"techId\":\"Stimpack\",\"remainingTicks\":12}]}"
            )

        assertNotNull(update)
        assertEquals(14, update?.researchActivity?.tick)
        assertEquals(1, update?.researchActivity?.enqueue)
        assertEquals(1, update?.researchActivity?.cancel)
        assertNull(update?.snapshot)
    }

    @Test
    fun `parses production stream updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"production\",\"tick\":14,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":60},{\"kind\":\"cancel\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":12}]}"
            )

        assertNotNull(update)
        assertEquals(14, update?.productionActivity?.tick)
        assertEquals(1, update?.productionActivity?.enqueue)
        assertEquals(1, update?.productionActivity?.cancel)
        assertNull(update?.snapshot)
    }

    @Test
    fun `parses construction state updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"constructionState\",\"tick\":14,\"entities\":[{\"entityId\":41,\"faction\":1,\"typeId\":\"Depot\",\"remainingTicks\":6},{\"entityId\":42,\"faction\":2,\"typeId\":\"Factory\",\"remainingTicks\":2}]}"
            )

        assertNotNull(update)
        assertEquals(14, update?.constructionActivity?.tick)
        assertEquals(2, update?.constructionActivity?.total)
        assertEquals(1, update?.constructionActivity?.faction1)
        assertEquals(1, update?.constructionActivity?.faction2)
        assertEquals(8, update?.constructionActivity?.remainingTicks)
        assertNull(update?.snapshot)
    }

    @Test
    fun `parses tick summary updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"tickSummary\",\"tick\":14,\"builds\":1,\"buildsCancelled\":1,\"buildFailures\":2,\"buildFailureReasons\":{\"invalidDefinition\":0,\"missingTech\":0,\"invalidFootprint\":0,\"invalidPlacement\":1,\"insufficientResources\":1},\"trainsQueued\":2,\"trainsCompleted\":1,\"trainsCancelled\":1,\"trainFailures\":1,\"trainFailureReasons\":{\"missingBuilding\":0,\"underConstruction\":0,\"missingTech\":0,\"invalidUnit\":0,\"invalidBuildTime\":0,\"incompatibleProducer\":0,\"insufficientResources\":0,\"queueFull\":1,\"nothingToCancel\":0},\"researchQueued\":1,\"researchCancelled\":1,\"researchCompleted\":0,\"researchFailures\":1,\"researchFailureReasons\":{\"missingBuilding\":0,\"underConstruction\":0,\"invalidTech\":1,\"missingTech\":0,\"incompatibleProducer\":0,\"insufficientResources\":0,\"alreadyUnlocked\":0,\"queueFull\":0,\"nothingToCancel\":0}}"
            )

        assertNotNull(update)
        assertEquals(14, update?.tickActivity?.tick)
        assertEquals(1, update?.tickActivity?.builds)
        assertEquals(1, update?.tickActivity?.buildsCancelled)
        assertEquals(2, update?.tickActivity?.buildFailures)
        assertEquals("invalidPlacement=1,insufficientResources=1", update?.tickActivity?.buildFailureReasons)
        assertEquals(2, update?.tickActivity?.trainsQueued)
        assertEquals(1, update?.tickActivity?.trainsCompleted)
        assertEquals(1, update?.tickActivity?.trainsCancelled)
        assertEquals(1, update?.tickActivity?.trainFailures)
        assertEquals("queueFull=1", update?.tickActivity?.trainFailureReasons)
        assertEquals(1, update?.tickActivity?.researchQueued)
        assertEquals(1, update?.tickActivity?.researchCancelled)
        assertEquals(1, update?.tickActivity?.researchFailures)
        assertEquals("invalidTech=1", update?.tickActivity?.researchFailureReasons)
        assertNull(update?.snapshot)
    }

    @Test
    fun `builds right click intents through shared controller`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10), FactionSnapshot(faction = 2, visibleTiles = 8)),
                entities =
                    listOf(
                        EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                        EntitySnapshot(id = 9, faction = 2, typeId = "Zergling", archetype = "lightMelee", x = 6f, y = 4f, dir = 0f, hp = 35, maxHp = 35, armor = 0)
                    ),
                resourceNodes = listOf(ResourceNodeSnapshot(id = 20, kind = "MineralField", x = 8f, y = 4f, remaining = 200, yieldPerTick = 0))
            )
        val selected = linkedSetOf(4)
        val ids = ClientCommandIds("test")

        val attackIntent =
            buildClientIntent(snapshot, selected, 1, 6f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val harvestIntent =
            buildClientIntent(snapshot, selected, 1, 8f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val moveIntent =
            buildClientIntent(snapshot, selected, 1, 10f, 10f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val attackMoveIntent =
            buildClientIntent(snapshot, selected, 1, 11f, 10f, leftClick = false, rightClick = true, attackMoveModifier = true, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)

        val attack = (attackIntent as ClientIntent.Command).record
        val harvest = (harvestIntent as ClientIntent.Command).record
        val move = (moveIntent as ClientIntent.Command).record
        val attackMove = (attackMoveIntent as ClientIntent.Command).record

        assertEquals("attack", attack.commandType)
        assertEquals("test-1", attack.requestId)
        assertEquals(9, attack.target)
        assertEquals("harvest", harvest.commandType)
        assertEquals("test-2", harvest.requestId)
        assertEquals(20, harvest.target)
        assertEquals("move", move.commandType)
        assertEquals("test-3", move.requestId)
        assertEquals(10f, move.x)
        assertEquals(10f, move.y)
        assertEquals("attackMove", attackMove.commandType)
        assertEquals("test-4", attackMove.requestId)
        assertEquals(11f, attackMove.x)
        assertEquals(10f, attackMove.y)
    }

    @Test
    fun `ground command mode overrides default ground order`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0)
                ),
                resourceNodes = emptyList()
            )

        val intent =
            buildClientIntent(
                snapshot,
                linkedSetOf(4),
                1,
                10f,
                10f,
                leftClick = false,
                rightClick = true,
                attackMoveModifier = false,
                forcedGroundCommandType = ClientGroundCommandMode.PATROL.commandType,
                additiveSelection = false,
                requestIds = ClientCommandIds("mode")
            ) as ClientIntent.Command

        assertEquals("patrol", intent.record.commandType)
        assertEquals("mode-1", intent.record.requestId)
    }

    @Test
    fun `parses map state updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"mapState\",\"width\":32,\"height\":32,\"blockedTiles\":[{\"x\":6,\"y\":14}],\"weightedTiles\":[],\"staticOccupancyTiles\":[{\"x\":7,\"y\":8}],\"resourceNodes\":[]}"
            )

        assertNotNull(update)
        assertEquals(32, update?.mapState?.width)
        assertTrue((6 to 14) in (update?.mapState?.blockedTiles ?: emptySet()))
        assertTrue((7 to 8) in (update?.mapState?.staticOccupancyTiles ?: emptySet()))
    }

    @Test
    fun `converts minimap clicks to world positions`() {
        val snapshot =
            ClientSnapshot(
                tick = 12,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 16,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10)),
                entities = emptyList(),
                resourceNodes = emptyList()
            )

        val center = miniMapWorldPosition(84, 84, 640, 640, snapshot)

        assertEquals(16f, center?.first)
        assertEquals(8f, center?.second)
        assertEquals(null, miniMapWorldPosition(400, 400, 640, 640, snapshot))
    }

    @Test
    fun `parses vision updates through shared bridge`() {
        val update =
            parseClientStreamLine(
                "{\"recordType\":\"vision\",\"tick\":14,\"changes\":[{\"faction\":1,\"x\":6,\"y\":14,\"visible\":true},{\"faction\":1,\"x\":7,\"y\":15,\"visible\":false}]}"
            )

        assertNotNull(update)
        assertEquals(2, update?.visionChanges?.size)
        assertEquals(6, update?.visionChanges?.first()?.x)
        assertEquals(false, update?.visionChanges?.last()?.visible)
    }
}
