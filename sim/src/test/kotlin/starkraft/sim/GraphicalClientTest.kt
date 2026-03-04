package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandIds
import starkraft.sim.client.ClientGroundCommandMode
import starkraft.sim.client.ClientIntent
import starkraft.sim.client.ClientMapState
import starkraft.sim.client.applySelectionClick
import starkraft.sim.client.buildHoldIntent
import starkraft.sim.client.buildClientIntent
import starkraft.sim.client.buildUnitSelectionRecord
import starkraft.sim.client.buildPreviewSpec
import starkraft.sim.client.defaultClientInputPath
import starkraft.sim.client.ClientCommandAck
import starkraft.sim.client.ClientConstructionActivity
import starkraft.sim.client.CameraView
import starkraft.sim.client.ClientProductionActivity
import starkraft.sim.client.ClientResearchActivity
import starkraft.sim.client.ClientTickActivity
import starkraft.sim.client.formatAckStatus
import starkraft.sim.client.formatConstructionActivity
import starkraft.sim.client.formatProductionActivity
import starkraft.sim.client.formatResearchActivity
import starkraft.sim.client.formatTickActivity
import starkraft.sim.client.parseClientStreamLine
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.buildClientHudLines
import starkraft.sim.client.buildBuilderSummary
import starkraft.sim.client.buildConstructionSummary
import starkraft.sim.client.buildCommandButtons
import starkraft.sim.client.buildPathSummary
import starkraft.sim.client.buildProductionSummary
import starkraft.sim.client.buildResearchSummary
import starkraft.sim.client.buildRallySummary
import starkraft.sim.client.buildSelectionSummary
import starkraft.sim.client.buildTechSummary
import starkraft.sim.client.healthBarFillWidth
import starkraft.sim.client.commandButtonAt
import starkraft.sim.client.selectEntitiesInBox
import starkraft.sim.client.zoomCameraAt
import starkraft.sim.client.isBuildPreviewValid
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.ResourceNodeSnapshot
import java.nio.file.Paths

class GraphicalClientTest {
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
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 10, unlockedTechIds = listOf("AdvancedTraining"))),
                entities = listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 4f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 9, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 4f, dir = 0f, hp = 45, maxHp = 45, armor = 0),
                    EntitySnapshot(id = 11, faction = 1, typeId = "Worker", archetype = "worker", x = 6f, y = 4f, dir = 0f, hp = 20, maxHp = 20, armor = 0, buildTargetId = 30),
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
                        productionQueueSize = 2,
                        activeProductionType = "Marine",
                        activeProductionRemainingTicks = 9,
                        researchQueueSize = 2,
                        activeResearchTech = "AdvancedTraining",
                        activeResearchRemainingTicks = 8,
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
                "selection: Marinex1 Workerx1 Depotx1",
                "builders: active=1 targets=1",
                "construction: sites=1 remaining=6 Depotx1",
                "paths: active=1 remaining=5 goals=12,14x1",
                "production: labs=1 queue=2 active=Marinex1",
                "research: labs=1 queue=2 active=AdvancedTrainingx1",
                "rally: none",
                "tech: AdvancedTrainingx1",
                "activity: builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q2/c1/x1 trainFails=1[queueFull=1] research=q1/c0/x1 researchFails=1[invalidTech=1] @15",
                "construction state: total=2 f1=2 f2=0 remaining=10 @15",
                "production events: e1/p2/c0/x1 @15",
                "research events: e1/p2/c0/x1 @15",
                "last ack: ok move[cli-9] @15",
                "left: select/drag   shift+left: add/remove/add-box   middle-drag/wheel: pan/zoom",
                "right: move/attack/harvest   ctrl+right: attackMove",
                "keys: m move   a attackMove   p patrol   h hold   esc clear"
            ),
            buildClientHudLines(
                snapshot = snapshot,
                state = ClientSessionState(
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
            listOf("move", "attackMove", "patrol", "hold", "build:Depot", "build:ResourceDepot", "build:GasDepot", "clear"),
            buildCommandButtons(true).map { it.actionId }
        )
        assertEquals(
            listOf("build:Depot", "build:ResourceDepot", "build:GasDepot", "clear"),
            buildCommandButtons(false).map { it.actionId }
        )
    }

    @Test
    fun `locates command button by panel click`() {
        val move = commandButtonAt(width = 640, x = 640 - 150, y = 50, hasSelection = true)
        val clear = commandButtonAt(width = 640, x = 640 - 150, y = 50 + (7 * 34), hasSelection = true)

        assertEquals("move", move?.actionId)
        assertEquals("clear", clear?.actionId)
        assertEquals(null, commandButtonAt(width = 640, x = 20, y = 20, hasSelection = true))
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
    fun `builds unit selection records for client input`() {
        val record = buildUnitSelectionRecord(8, linkedSetOf(4, 9))

        assertEquals(8, record.tick)
        assertEquals("units", record.selectionType)
        assertArrayEquals(intArrayOf(4, 9), record.units)
    }

    @Test
    fun `computes health bar fill width safely`() {
        assertEquals(10, healthBarFillWidth(20, 10, 20))
        assertEquals(20, healthBarFillWidth(20, 50, 20))
        assertEquals(0, healthBarFillWidth(20, 0, 20))
        assertEquals(0, healthBarFillWidth(20, 10, 0))
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

        val intent = selectEntitiesInBox(snapshot, selected, 3.5f, 3.5f, 5.2f, 5.2f, additiveSelection = false)

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

        selectEntitiesInBox(snapshot, selected, 3.5f, 3.5f, 5.2f, 5.2f, additiveSelection = true)

        assertEquals(linkedSetOf(12, 4, 5), selected)
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
            buildClientIntent(snapshot, selected, 6f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val harvestIntent =
            buildClientIntent(snapshot, selected, 8f, 4f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val moveIntent =
            buildClientIntent(snapshot, selected, 10f, 10f, leftClick = false, rightClick = true, attackMoveModifier = false, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)
        val attackMoveIntent =
            buildClientIntent(snapshot, selected, 11f, 10f, leftClick = false, rightClick = true, attackMoveModifier = true, forcedGroundCommandType = null, additiveSelection = false, requestIds = ids)

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
}
