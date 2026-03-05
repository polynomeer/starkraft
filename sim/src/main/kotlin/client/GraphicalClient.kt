package starkraft.sim.client

import starkraft.sim.net.InputJson
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashSet
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager

internal const val CLIENT_EXIT_RESTART = 75

internal data class CameraView(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val baseTileSize: Int = 20
) {
    val tileSize: Float get() = baseTileSize * zoom

    fun worldToScreenX(worldX: Float): Float = (worldX * tileSize) + panX
    fun worldToScreenY(worldY: Float): Float = (worldY * tileSize) + panY
    fun screenToWorldX(screenX: Float): Float = (screenX - panX) / tileSize
    fun screenToWorldY(screenY: Float): Float = (screenY - panY) / tileSize
}

internal fun zoomCameraAt(camera: CameraView, screenX: Float, screenY: Float, zoomFactor: Float): CameraView {
    val clampedZoom = (camera.zoom * zoomFactor).coerceIn(0.5f, 3.0f)
    if (clampedZoom == camera.zoom) return camera
    val worldX = camera.screenToWorldX(screenX)
    val worldY = camera.screenToWorldY(screenY)
    val next = camera.copy(zoom = clampedZoom)
    return next.copy(
        panX = screenX - (worldX * next.tileSize),
        panY = screenY - (worldY * next.tileSize)
    )
}

internal fun centerCameraOnWorld(
    camera: CameraView,
    viewportWidth: Int,
    viewportHeight: Int,
    worldX: Float,
    worldY: Float
): CameraView =
    camera.copy(
        panX = (viewportWidth / 2f) - (worldX * camera.tileSize),
        panY = (viewportHeight / 2f) - (worldY * camera.tileSize)
    )

internal fun miniMapWorldPosition(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    snapshot: ClientSnapshot
): Pair<Float, Float>? {
    val bounds = miniMapBounds(width, height)
    if (!bounds.contains(x, y)) return null
    val worldX = (((x - bounds.x).toFloat() / bounds.width.toFloat()) * snapshot.mapWidth).coerceIn(0f, snapshot.mapWidth.toFloat())
    val worldY = (((y - bounds.y).toFloat() / bounds.height.toFloat()) * snapshot.mapHeight).coerceIn(0f, snapshot.mapHeight.toFloat())
    return worldX to worldY
}

internal data class BuildPreviewSpec(
    val typeId: String,
    val width: Int,
    val height: Int,
    val clearance: Int,
    val mineralCost: Int,
    val gasCost: Int
)

internal fun buildPreviewSpec(typeId: String): BuildPreviewSpec? =
    defaultClientCatalog().buildOptions.firstOrNull { it.typeId == typeId }?.let {
        BuildPreviewSpec(it.typeId, it.width, it.height, it.clearance, it.mineralCost, it.gasCost)
    }

internal fun isBuildPreviewValid(
    mapState: ClientMapState?,
    snapshot: ClientSnapshot?,
    spec: BuildPreviewSpec?,
    tileX: Int,
    tileY: Int
): Boolean {
    if (mapState == null || snapshot == null || spec == null) return false
    val minX = tileX - spec.clearance
    val minY = tileY - spec.clearance
    val maxX = tileX + spec.width + spec.clearance - 1
    val maxY = tileY + spec.height + spec.clearance - 1
    if (minX < 0 || minY < 0 || maxX >= mapState.width || maxY >= mapState.height) return false
    for (x in minX..maxX) {
        for (y in minY..maxY) {
            if ((x to y) in mapState.blockedTiles) return false
            if ((x to y) in mapState.staticOccupancyTiles) return false
        }
    }
    for (entity in snapshot.entities) {
        val width = entity.footprintWidth ?: continue
        val height = entity.footprintHeight ?: continue
        val clearance = entity.placementClearance ?: 0
        val entityTileX = kotlin.math.floor(entity.x).toInt()
        val entityTileY = kotlin.math.floor(entity.y).toInt()
        val entityMinX = entityTileX - clearance
        val entityMinY = entityTileY - clearance
        val entityMaxX = entityTileX + width + clearance - 1
        val entityMaxY = entityTileY + height + clearance - 1
        if (maxX >= entityMinX && minX <= entityMaxX && maxY >= entityMinY && minY <= entityMaxY) {
            return false
        }
    }
    return true
}

internal fun buildCancelIntent(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>,
    commandType: String,
    requestIds: ClientCommandIds
): ClientIntent.Command? {
    val target =
        snapshot.entities.firstOrNull { entity ->
            if (entity.id !in selectedIds) return@firstOrNull false
            when (commandType) {
                "cancelBuild" -> entity.underConstruction
                "cancelTrain" -> entity.productionQueueSize > 0 || entity.activeProductionType != null
                "cancelResearch" -> entity.researchQueueSize > 0 || entity.activeResearchTech != null
                else -> false
            }
        } ?: return null
    return ClientIntent.Command(
        InputJson.InputCommandRecord(
            tick = snapshot.tick + 1,
            commandType = commandType,
            requestId = requestIds.nextRequestId(),
            buildingId = target.id
        )
    )
}

internal fun buildQueueIntent(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>,
    commandType: String,
    typeId: String,
    requestIds: ClientCommandIds
): ClientIntent.Command? {
    val target =
        snapshot.entities.firstOrNull { entity ->
            if (entity.id !in selectedIds) return@firstOrNull false
            when (commandType) {
                "train" -> entity.supportsTraining == true
                "research" -> entity.supportsResearch == true
                else -> false
            }
        } ?: return null
    return ClientIntent.Command(
        InputJson.InputCommandRecord(
            tick = snapshot.tick + 1,
            commandType = commandType,
            requestId = requestIds.nextRequestId(),
            buildingId = target.id,
            typeId = typeId
        )
    )
}

private class ClientPanel(
    private val session: ClientSession,
    private val renderer: ClientRenderer = SwingClientRenderer(),
    private val catalog: ClientCatalog = defaultClientCatalog(),
    private val controlPath: Path? = null,
    private val scenarioPath: Path? = null,
    private val playRoot: Path? = null,
    private val requestRestart: () -> Unit = {}
) : JPanel() {
    private val requestIds = ClientCommandIds()
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragCurrentX = 0
    private var dragCurrentY = 0
    private var draggingSelection = false
    private var panningCamera = false
    private var panLastX = 0
    private var panLastY = 0
    private var camera = CameraView()
    private var groundMode: ClientGroundCommandMode? = null
    private var buildModeTypeId: String? = null
    private var playControlState = PlayControlState()
    private var playScenario = PlayScenario.SKIRMISH
    private var scenarioMenuOpen = false
    private var scenarioMenuSelection = PlayScenario.SKIRMISH
    private var presetMenuOpen = false
    private var presetMenuSelection = "quick"
    private var helpOverlayOpen = false
    private var noticeMessage: String? = null
    private var noticeUntilNanos: Long = 0L
    private val controlGroups = arrayOfNulls<IntArray>(10)
    private var lastGroupRecall: Int? = null
    private var lastGroupRecallAtNanos: Long = 0L

    init {
        if (controlPath != null && Files.exists(controlPath)) {
            playControlState = parsePlayControlState(Files.readString(controlPath))
        }
        if (scenarioPath != null) {
            playScenario = readPlayScenario(scenarioPath, PlayScenario.SKIRMISH)
            scenarioMenuSelection = playScenario
        }
        background = Color(0x12, 0x18, 0x1F)
        preferredSize = Dimension(640, 640)
        font = Font("Monospaced", Font.PLAIN, 12)
        isFocusable = true
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (SwingUtilities.isLeftMouseButton(e) && handleCommandPanelClick(e)) {
                    repaint()
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e) && handleMiniMapClick(e)) {
                    repaint()
                    return
                }
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panningCamera = true
                    panLastX = e.x
                    panLastY = e.y
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartX = e.x
                    dragStartY = e.y
                    dragCurrentX = e.x
                    dragCurrentY = e.y
                    draggingSelection = true
                    return
                }
                handleMouse(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panningCamera = false
                    return
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val wasDragging = draggingSelection
                draggingSelection = false
                if (!wasDragging) return
                dragCurrentX = e.x
                dragCurrentY = e.y
                if (isSelectionDrag()) {
                    handleSelectionBox(e)
                } else {
                    handleMouse(e)
                }
                repaint()
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (panningCamera) {
                    camera =
                        camera.copy(
                            panX = camera.panX + (e.x - panLastX),
                            panY = camera.panY + (e.y - panLastY)
                        )
                    panLastX = e.x
                    panLastY = e.y
                    repaint()
                    return
                }
                if (!draggingSelection) return
                dragCurrentX = e.x
                dragCurrentY = e.y
                repaint()
            }
        })
        addMouseWheelListener { e: MouseWheelEvent ->
            requestFocusInWindow()
            val factor = if (e.preciseWheelRotation < 0.0) 1.1f else 0.9f
            camera = zoomCameraAt(camera, e.x.toFloat(), e.y.toFloat(), factor)
            repaint()
        }
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val delta = 24f
                if (e.keyCode == KeyEvent.VK_F10) {
                    togglePresetMenu()
                    repaint()
                    return
                }
                if (e.keyCode == KeyEvent.VK_F1) {
                    helpOverlayOpen = !helpOverlayOpen
                    repaint()
                    return
                }
                if (presetMenuOpen) {
                    when (e.keyCode) {
                        KeyEvent.VK_UP -> cyclePresetMenu(-1)
                        KeyEvent.VK_DOWN -> cyclePresetMenu(1)
                        KeyEvent.VK_S -> savePreset(presetMenuSelection)
                        KeyEvent.VK_L, KeyEvent.VK_ENTER -> loadPreset(presetMenuSelection)
                        KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB, KeyEvent.VK_F10 -> presetMenuOpen = false
                        else -> return
                    }
                    repaint()
                    return
                }
                val group = controlGroupFromKeyCode(e.keyCode)
                if (group != null) {
                    if (e.isShiftDown) {
                        assignControlGroup(group)
                        lastGroupRecall = null
                    } else if (e.isAltDown) {
                        addToControlGroup(group)
                        lastGroupRecall = null
                    } else {
                        val now = System.nanoTime()
                        val focusOnRecall = lastGroupRecall == group && (now - lastGroupRecallAtNanos) <= CONTROL_GROUP_DOUBLE_TAP_WINDOW_NANOS
                        recallControlGroup(group, focusOnRecall)
                        lastGroupRecall = group
                        lastGroupRecallAtNanos = now
                    }
                    repaint()
                    return
                }
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> camera = camera.copy(panX = camera.panX + delta)
                    KeyEvent.VK_RIGHT -> camera = camera.copy(panX = camera.panX - delta)
                    KeyEvent.VK_UP -> if (scenarioMenuOpen) cycleScenarioMenu(-1) else camera = camera.copy(panY = camera.panY + delta)
                    KeyEvent.VK_DOWN -> if (scenarioMenuOpen) cycleScenarioMenu(1) else camera = camera.copy(panY = camera.panY - delta)
                    KeyEvent.VK_W -> camera = camera.copy(panY = camera.panY + delta)
                    KeyEvent.VK_S -> camera = camera.copy(panY = camera.panY - delta)
                    KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> camera = zoomCameraAt(camera, width / 2f, height / 2f, 1.1f)
                    KeyEvent.VK_MINUS -> camera = zoomCameraAt(camera, width / 2f, height / 2f, 0.9f)
                    KeyEvent.VK_0 -> {
                        if (e.isAltDown) {
                            clearControlGroups()
                        } else {
                            camera = CameraView()
                        }
                    }
                    KeyEvent.VK_M -> groundMode = ClientGroundCommandMode.MOVE
                    KeyEvent.VK_A -> groundMode = ClientGroundCommandMode.ATTACK_MOVE
                    KeyEvent.VK_P -> groundMode = ClientGroundCommandMode.PATROL
                    KeyEvent.VK_B -> {
                        buildModeTypeId = "Depot"
                        groundMode = null
                    }
                    KeyEvent.VK_R -> {
                        buildModeTypeId = "ResourceDepot"
                        groundMode = null
                    }
                    KeyEvent.VK_G -> {
                        buildModeTypeId = "GasDepot"
                        groundMode = null
                    }
                    KeyEvent.VK_H -> {
                        val snapshot = session.state.snapshot ?: return
                        val hold = buildHoldIntent(snapshot, session.state.selectedIds, requestIds) ?: return
                        session.append(hold)
                        groundMode = null
                        buildModeTypeId = null
                    }
                    KeyEvent.VK_1 -> session.state.viewedFaction = 1
                    KeyEvent.VK_2 -> session.state.viewedFaction = 2
                    KeyEvent.VK_3 -> session.state.viewedFaction = null
                    KeyEvent.VK_F2 -> {
                        selectViewedFaction()
                    }
                    KeyEvent.VK_F3 -> {
                        selectSelectedType()
                    }
                    KeyEvent.VK_F4 -> {
                        selectSelectedArchetype()
                    }
                    KeyEvent.VK_F11 -> {
                        selectAllVisible()
                    }
                    KeyEvent.VK_F12 -> {
                        selectIdleWorkers()
                    }
                    KeyEvent.VK_F -> {
                        selectDamagedUnits()
                    }
                    KeyEvent.VK_V -> {
                        selectCombatUnits()
                    }
                    KeyEvent.VK_N -> {
                        selectProducers()
                    }
                    KeyEvent.VK_HOME -> {
                        centerOnSelection()
                    }
                    KeyEvent.VK_END -> {
                        centerOnViewedFaction()
                    }
                    KeyEvent.VK_Z -> {
                        selectTrainingBuildings()
                    }
                    KeyEvent.VK_C -> {
                        selectResearchBuildings()
                    }
                    KeyEvent.VK_J -> {
                        selectConstructionSites()
                    }
                    KeyEvent.VK_K -> {
                        selectHarvesters()
                    }
                    KeyEvent.VK_Q -> {
                        selectReturningHarvesters()
                    }
                    KeyEvent.VK_E -> {
                        selectCargoHarvesters()
                    }
                    KeyEvent.VK_D -> {
                        selectDropoffBuildings()
                    }
                    KeyEvent.VK_X -> {
                        val snapshot = session.state.snapshot ?: return
                        buildCancelIntent(snapshot, session.state.selectedIds, "cancelBuild", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_T -> {
                        val snapshot = session.state.snapshot ?: return
                        buildCancelIntent(snapshot, session.state.selectedIds, "cancelTrain", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_Y -> {
                        val snapshot = session.state.snapshot ?: return
                        buildCancelIntent(snapshot, session.state.selectedIds, "cancelResearch", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_U -> {
                        val snapshot = session.state.snapshot ?: return
                        catalog.trainOptions.getOrNull(0)?.let {
                            buildQueueIntent(snapshot, session.state.selectedIds, "train", it.typeId, requestIds)?.let(session::append)
                        }
                    }
                    KeyEvent.VK_I -> {
                        val snapshot = session.state.snapshot ?: return
                        catalog.trainOptions.getOrNull(1)?.let {
                            buildQueueIntent(snapshot, session.state.selectedIds, "train", it.typeId, requestIds)?.let(session::append)
                        }
                    }
                    KeyEvent.VK_O -> {
                        val snapshot = session.state.snapshot ?: return
                        catalog.trainOptions.getOrNull(2)?.let {
                            buildQueueIntent(snapshot, session.state.selectedIds, "train", it.typeId, requestIds)?.let(session::append)
                        }
                    }
                    KeyEvent.VK_L -> {
                        val snapshot = session.state.snapshot ?: return
                        catalog.researchOptions.firstOrNull()?.let {
                            buildQueueIntent(snapshot, session.state.selectedIds, "research", it.typeId, requestIds)?.let(session::append)
                        }
                    }
                    KeyEvent.VK_SPACE -> togglePause()
                    KeyEvent.VK_OPEN_BRACKET -> adjustSpeed(-1)
                    KeyEvent.VK_CLOSE_BRACKET -> adjustSpeed(1)
                    KeyEvent.VK_F6 -> cycleScenario(-1)
                    KeyEvent.VK_F7 -> cycleScenario(1)
                    KeyEvent.VK_F8 -> savePreset(if (e.isShiftDown) "alt" else "quick")
                    KeyEvent.VK_F9 -> loadPreset(if (e.isShiftDown) "alt" else "quick")
                    KeyEvent.VK_TAB -> toggleScenarioMenu()
                    KeyEvent.VK_ENTER -> {
                        if (scenarioMenuOpen) applyScenarioSelection()
                    }
                    KeyEvent.VK_F5 -> requestRestart()
                    KeyEvent.VK_ESCAPE -> {
                        if (scenarioMenuOpen) {
                            scenarioMenuOpen = false
                            repaint()
                            return
                        }
                        session.state.selectedIds.clear()
                        groundMode = null
                        buildModeTypeId = null
                    }
                    else -> return
                }
                repaint()
            }
        })
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val snapshot = session.state.snapshot
        val canTrain = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsTraining == true } }
        val canResearch = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsResearch == true } }
        val statusLineCount = buildCommandPanelStatusLines(buildOverlayLines()).size
        val button =
            commandButtonAt(
                width,
                event.x,
                event.y,
                catalog,
                statusLineCount = statusLineCount,
                hasSelection = session.state.selectedIds.isNotEmpty(),
                canTrain = canTrain,
                canResearch = canResearch
            ) ?: return null
        return commandButtonTooltip(button.actionId)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        renderer.render(g, width, height, session.state, camera, buildOverlayLines())
        drawBuildPreview(g)
        if (draggingSelection && isSelectionDrag()) {
            drawSelectionBox(g)
        }
    }

    private fun handleMouse(e: MouseEvent) {
        val snapshot = session.state.snapshot ?: return
        val worldX = camera.screenToWorldX(e.x.toFloat())
        val worldY = camera.screenToWorldY(e.y.toFloat())
        if (SwingUtilities.isRightMouseButton(e) && buildModeTypeId != null) {
            val typeId = buildModeTypeId ?: return
            val spec = buildPreviewSpec(typeId) ?: return
            val tileX = kotlin.math.floor(worldX).toInt()
            val tileY = kotlin.math.floor(worldY).toInt()
            if (isBuildPreviewValid(session.state.mapState, snapshot, spec, tileX, tileY)) {
                session.append(
                    ClientIntent.Command(
                        InputJson.InputCommandRecord(
                            tick = snapshot.tick + 1,
                            commandType = "build",
                            requestId = requestIds.nextRequestId(),
                            faction = 1,
                            typeId = typeId,
                            tileX = tileX,
                            tileY = tileY
                        )
                    )
                )
                buildModeTypeId = null
            }
            repaint()
            return
        }
        when (
            val intent =
                buildClientIntent(
                    snapshot = snapshot,
                    selectedIds = session.state.selectedIds,
                    worldX = worldX,
                    worldY = worldY,
                    leftClick = SwingUtilities.isLeftMouseButton(e),
                    rightClick = SwingUtilities.isRightMouseButton(e),
                    attackMoveModifier = e.isControlDown,
                    forcedGroundCommandType = groundMode?.commandType,
                    additiveSelection = e.isShiftDown,
                    viewedFaction = session.state.viewedFaction,
                    requestIds = requestIds
                )
        ) {
            is ClientIntent.Selection -> {
                session.append(intent)
                repaint()
            }
            is ClientIntent.Command -> {
                session.append(intent)
                groundMode = null
                buildModeTypeId = null
            }
            null -> return
        }
    }

    private fun handleSelectionBox(e: MouseEvent) {
        val snapshot = session.state.snapshot ?: return
        val intent =
            selectEntitiesInBox(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                viewedFaction = session.state.viewedFaction,
                startWorldX = camera.screenToWorldX(dragStartX.toFloat()),
                startWorldY = camera.screenToWorldY(dragStartY.toFloat()),
                endWorldX = camera.screenToWorldX(e.x.toFloat()),
                endWorldY = camera.screenToWorldY(e.y.toFloat()),
                additiveSelection = e.isShiftDown
            )
        session.append(intent)
    }

    private fun isSelectionDrag(): Boolean =
        kotlin.math.abs(dragCurrentX - dragStartX) >= 6 || kotlin.math.abs(dragCurrentY - dragStartY) >= 6

    private fun drawSelectionBox(g: Graphics2D) {
        val minX = minOf(dragStartX, dragCurrentX)
        val minY = minOf(dragStartY, dragCurrentY)
        val width = kotlin.math.abs(dragCurrentX - dragStartX)
        val height = kotlin.math.abs(dragCurrentY - dragStartY)
        g.color = Color(0xF4, 0xE2, 0x71, 48)
        g.fillRect(minX, minY, width, height)
        g.color = Color(0xF4, 0xE2, 0x71)
        g.drawRect(minX, minY, width, height)
    }

    private fun buildOverlayLines(): List<String> =
        listOf(
            "camera: zoom=${"%.2f".format(camera.zoom)} pan=${camera.panX.toInt()}/${camera.panY.toInt()}",
            "mode: ${buildModeTypeId?.let { "build:$it" } ?: groundMode?.name?.lowercase()?.replace('_', '-') ?: "default"}",
            "view: ${session.state.viewedFaction?.let { "faction $it" } ?: "observer"}",
            formatPlayControlOverlay(playControlState),
            "scenario: ${playScenario.id}",
            "preset: quick"
        ) + listOfNotNull(
            selectionHudLine(),
            controlGroupSummaryLine(),
            presetAvailabilityLine(),
            currentNoticeLine()
        ) + buildScenarioOverlayLines(scenarioMenuOpen, playScenario, scenarioMenuSelection) +
            buildPresetOverlayLines(
                open = presetMenuOpen,
                selectedSlot = presetMenuSelection,
                quickAvailable = isPresetAvailable("quick"),
                altAvailable = isPresetAvailable("alt")
            ) + buildHelpOverlayLines(helpOverlayOpen)

    private fun handleCommandPanelClick(e: MouseEvent): Boolean {
        val snapshot = session.state.snapshot
        val canTrain = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsTraining == true } }
        val canResearch = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsResearch == true } }
        val statusLineCount = buildCommandPanelStatusLines(buildOverlayLines()).size
        val button =
            commandButtonAt(
                width,
                e.x,
                e.y,
                catalog,
                statusLineCount = statusLineCount,
                hasSelection = session.state.selectedIds.isNotEmpty(),
                canTrain = canTrain,
                canResearch = canResearch
            ) ?: return false
        when (button.actionId) {
            "move" -> {
                groundMode = ClientGroundCommandMode.MOVE
                buildModeTypeId = null
            }
            "attackMove" -> {
                groundMode = ClientGroundCommandMode.ATTACK_MOVE
                buildModeTypeId = null
            }
            "patrol" -> {
                groundMode = ClientGroundCommandMode.PATROL
                buildModeTypeId = null
            }
            "hold" -> {
                val snapshot = session.state.snapshot ?: return true
                val hold = buildHoldIntent(snapshot, session.state.selectedIds, requestIds)
                if (hold != null) session.append(hold)
                groundMode = null
                buildModeTypeId = null
            }
            "cancelBuild", "cancelTrain", "cancelResearch" -> {
                val snapshot = session.state.snapshot ?: return true
                buildCancelIntent(snapshot, session.state.selectedIds, button.actionId, requestIds)?.let(session::append)
            }
            "clear" -> {
                session.state.selectedIds.clear()
                groundMode = null
                buildModeTypeId = null
            }
            "scenario:prev" -> cycleScenario(-1)
            "scenario:next" -> cycleScenario(1)
            "play:pause" -> togglePause()
            "play:slower" -> adjustSpeed(-1)
            "play:faster" -> adjustSpeed(1)
            "preset:save:quick" -> savePreset("quick")
            "preset:load:quick" -> loadPreset("quick")
            "preset:save:alt" -> savePreset("alt")
            "preset:load:alt" -> loadPreset("alt")
            "preset:menu" -> togglePresetMenu()
            "help:toggle" -> helpOverlayOpen = !helpOverlayOpen
            "view:centerSelection" -> centerOnSelection()
            "view:centerFaction" -> centerOnViewedFaction()
            "view:resetCamera" -> {
                camera = CameraView()
                showNotice("camera reset")
            }
            "view:faction1" -> {
                session.state.viewedFaction = 1
                showNotice("view faction 1")
            }
            "view:faction2" -> {
                session.state.viewedFaction = 2
                showNotice("view faction 2")
            }
            "view:observer" -> {
                session.state.viewedFaction = null
                showNotice("view observer")
            }
            "select:viewFaction" -> selectViewedFaction()
            "select:selectedType" -> selectSelectedType()
            "select:selectedArchetype" -> selectSelectedArchetype()
            "select:all" -> selectAllVisible()
            "select:idleWorkers" -> selectIdleWorkers()
            "select:damaged" -> selectDamagedUnits()
            "select:combat" -> selectCombatUnits()
            "select:producers" -> selectProducers()
            "select:trainers" -> selectTrainingBuildings()
            "select:researchers" -> selectResearchBuildings()
            "select:construction" -> selectConstructionSites()
            "select:harvesters" -> selectHarvesters()
            "select:returningHarvesters" -> selectReturningHarvesters()
            "select:cargoHarvesters" -> selectCargoHarvesters()
            "select:dropoffs" -> selectDropoffBuildings()
            "groups:clear" -> clearControlGroups()
            "scenario:menu" -> toggleScenarioMenu()
            else -> {
                if (button.actionId.startsWith("build:")) {
                    buildModeTypeId = button.actionId.removePrefix("build:")
                    groundMode = null
                } else if (button.actionId.startsWith("train:")) {
                    val snapshot = session.state.snapshot ?: return true
                    val typeId = button.actionId.removePrefix("train:")
                    buildQueueIntent(snapshot, session.state.selectedIds, "train", typeId, requestIds)?.let(session::append)
                } else if (button.actionId.startsWith("research:")) {
                    val snapshot = session.state.snapshot ?: return true
                    val typeId = button.actionId.removePrefix("research:")
                    buildQueueIntent(snapshot, session.state.selectedIds, "research", typeId, requestIds)?.let(session::append)
                }
            }
        }
        return true
    }

    private fun handleMiniMapClick(e: MouseEvent): Boolean {
        val snapshot = session.state.snapshot ?: return false
        val world = miniMapWorldPosition(e.x, e.y, width, height, snapshot) ?: return false
        camera = centerCameraOnWorld(camera, width, height, world.first, world.second)
        return true
    }

    private fun drawBuildPreview(g: Graphics2D) {
        val snapshot = session.state.snapshot ?: return
        val mapState = session.state.mapState ?: return
        val typeId = buildModeTypeId ?: return
        val spec = buildPreviewSpec(typeId) ?: return
        val mouse = mousePosition ?: return
        val tileX = kotlin.math.floor(camera.screenToWorldX(mouse.x.toFloat())).toInt()
        val tileY = kotlin.math.floor(camera.screenToWorldY(mouse.y.toFloat())).toInt()
        val valid = isBuildPreviewValid(mapState, snapshot, spec, tileX, tileY)
        val startX = camera.worldToScreenX(tileX.toFloat()).toInt()
        val startY = camera.worldToScreenY(tileY.toFloat()).toInt()
        val width = (spec.width * camera.tileSize).toInt()
        val height = (spec.height * camera.tileSize).toInt()
        g.color = if (valid) Color(0x4A, 0xD7, 0x7D, 72) else Color(0xD7, 0x4A, 0x4A, 72)
        g.fillRect(startX, startY, width, height)
        g.color = if (valid) Color(0x4A, 0xD7, 0x7D) else Color(0xD7, 0x4A, 0x4A)
        g.drawRect(startX, startY, width, height)
        val label = buildPreviewLabel(spec, valid) ?: return
        val labelX = startX
        val labelY = (startY - 28).coerceAtLeast(18)
        g.color = Color(0x10, 0x14, 0x19, 220)
        g.fillRect(labelX - 4, labelY - 12, 156, 34)
        g.color = if (label.valid) Color(0xE8, 0xF3, 0xEA) else Color(0xF7, 0xD0, 0xD0)
        g.drawString(label.title, labelX, labelY)
        g.drawString("${label.cost} ${label.size}", labelX, labelY + 14)
    }

    private fun togglePause() {
        playControlState = playControlState.copy(paused = !playControlState.paused)
        writePlayControl()
    }

    private fun adjustSpeed(delta: Int) {
        playControlState = playControlState.copy(speed = clampPlaySpeed(playControlState.speed + delta))
        writePlayControl()
    }

    private fun writePlayControl() {
        val path = controlPath ?: return
        Files.writeString(path, renderPlayControlState(playControlState))
    }

    private fun cycleScenario(delta: Int) {
        val path = scenarioPath ?: return
        playScenario = PlayScenario.cycle(playScenario, delta)
        scenarioMenuSelection = playScenario
        writePlayScenario(path, playScenario)
        requestRestart()
    }

    private fun toggleScenarioMenu() {
        if (scenarioPath == null) return
        scenarioMenuOpen = !scenarioMenuOpen
        scenarioMenuSelection = playScenario
        if (scenarioMenuOpen) presetMenuOpen = false
    }

    private fun cycleScenarioMenu(delta: Int) {
        if (!scenarioMenuOpen) return
        scenarioMenuSelection = PlayScenario.cycle(scenarioMenuSelection, delta)
    }

    private fun applyScenarioSelection() {
        val path = scenarioPath ?: return
        playScenario = scenarioMenuSelection
        writePlayScenario(path, playScenario)
        scenarioMenuOpen = false
        requestRestart()
    }

    private fun togglePresetMenu() {
        if (playRoot == null) return
        presetMenuOpen = !presetMenuOpen
        if (presetMenuOpen) scenarioMenuOpen = false
    }

    private fun cyclePresetMenu(delta: Int) {
        val slots = listOf("quick", "alt")
        val current = slots.indexOf(presetMenuSelection).let { if (it < 0) 0 else it }
        presetMenuSelection = slots[Math.floorMod(current + delta, slots.size)]
    }

    private fun savePreset(name: String) {
        val root = playRoot ?: return
        savePlayPreset(root.resolve("presets"), name, PlayPresetState(playScenario, playControlState))
        showNotice("preset saved: $name")
    }

    private fun loadPreset(name: String) {
        val root = playRoot ?: return
        val preset = loadPlayPreset(root.resolve("presets"), name, playScenario)
        if (preset == null) {
            showNotice("preset missing: $name")
            return
        }
        playScenario = preset.scenario
        scenarioMenuSelection = preset.scenario
        playControlState = preset.control
        scenarioPath?.let { writePlayScenario(it, playScenario) }
        writePlayControl()
        showNotice("preset loaded: $name")
        requestRestart()
    }

    private fun showNotice(message: String) {
        noticeMessage = message
        noticeUntilNanos = System.nanoTime() + 2_000_000_000L
    }

    private fun currentNoticeLine(): String? {
        val message = noticeMessage ?: return null
        return if (System.nanoTime() <= noticeUntilNanos) {
            "notice: $message"
        } else {
            noticeMessage = null
            null
        }
    }

    private fun presetAvailabilityLine(): String? {
        val root = playRoot ?: return null
        val presetsDir = root.resolve("presets")
        val quick = Files.exists(presetFilePath(presetsDir, "quick"))
        val alt = Files.exists(presetFilePath(presetsDir, "alt"))
        return formatPresetAvailability(quick, alt)
    }

    private fun selectionHudLine(): String? {
        val snapshot = session.state.snapshot ?: return null
        val selected = session.state.selectedIds
        if (selected.isEmpty()) return null
        return "selection hud: ${buildSelectionSummary(snapshot, selected).removePrefix("selection: ")}"
    }

    private fun controlGroupSummaryLine(): String? {
        val highlighted = activeControlGroupHighlight(lastGroupRecall, lastGroupRecallAtNanos, System.nanoTime())
        val summary = formatControlGroupSummary(controlGroups, highlighted)
        return summary?.let { "groups: $it" }
    }

    private fun selectViewedFaction() {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction
        if (faction == null) {
            showNotice("select faction first (1/2)")
            return
        }
        val ids = collectFactionSelectionIds(snapshot, faction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildFactionSelectionRecord(snapshot.tick + 1, faction)
            )
        )
        showNotice("selected ${ids.size} units (f$faction)")
    }

    private fun centerOnSelection() {
        val snapshot = session.state.snapshot ?: return
        val ids = session.state.selectedIds
        if (ids.isEmpty()) {
            showNotice("no selection to center")
            return
        }
        val center = computeSelectionCentroid(snapshot, ids.toIntArray())
        if (center == null) {
            showNotice("selection not found")
            return
        }
        camera = centerCameraOnWorld(camera, width, height, center.first, center.second)
        showNotice("camera centered")
    }

    private fun centerOnViewedFaction() {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction
        if (faction == null) {
            showNotice("select faction first (1/2)")
            return
        }
        val ids = collectFactionSelectionIds(snapshot, faction)
        val center = computeSelectionCentroid(snapshot, ids)
        if (center == null) {
            showNotice("no units for faction $faction")
            return
        }
        camera = centerCameraOnWorld(camera, width, height, center.first, center.second)
        showNotice("camera centered on f$faction")
    }

    private fun selectSelectedType() {
        val snapshot = session.state.snapshot ?: return
        val firstSelectedId = session.state.selectedIds.firstOrNull()
        if (firstSelectedId == null) {
            showNotice("select a unit first")
            return
        }
        val selected = snapshot.entities.firstOrNull { it.id == firstSelectedId }
        if (selected == null) {
            showNotice("selected unit missing")
            return
        }
        val faction = session.state.viewedFaction ?: selected.faction
        val ids = collectTypeSelectionIds(snapshot, selected.typeId, faction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildTypeSelectionRecord(snapshot.tick + 1, selected.typeId)
            )
        )
        showNotice("selected ${ids.size} ${selected.typeId}")
    }

    private fun selectSelectedArchetype() {
        val snapshot = session.state.snapshot ?: return
        val firstSelectedId = session.state.selectedIds.firstOrNull()
        if (firstSelectedId == null) {
            showNotice("select a unit first")
            return
        }
        val selected = snapshot.entities.firstOrNull { it.id == firstSelectedId }
        if (selected == null) {
            showNotice("selected unit missing")
            return
        }
        val archetype = selected.archetype
        if (archetype.isNullOrBlank()) {
            showNotice("selected unit has no archetype")
            return
        }
        val faction = session.state.viewedFaction ?: selected.faction
        val ids = collectArchetypeSelectionIds(snapshot, archetype, faction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildArchetypeSelectionRecord(snapshot.tick + 1, archetype)
            )
        )
        showNotice("selected ${ids.size} $archetype")
    }

    private fun selectAllVisible() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectAllSelectionIds(snapshot)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildAllSelectionRecord(snapshot.tick + 1)
            )
        )
        showNotice("selected all (${ids.size})")
    }

    private fun selectIdleWorkers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectIdleWorkerSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected idle workers (${ids.size})")
    }

    private fun selectDamagedUnits() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectDamagedSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected damaged (${ids.size})")
    }

    private fun selectCombatUnits() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectCombatSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected combat (${ids.size})")
    }

    private fun selectProducers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectProducerSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected producers (${ids.size})")
    }

    private fun selectTrainingBuildings() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectTrainingSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected trainers (${ids.size})")
    }

    private fun selectResearchBuildings() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectResearchSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected researchers (${ids.size})")
    }

    private fun selectConstructionSites() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectConstructionSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected construction (${ids.size})")
    }

    private fun selectHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected harvesters (${ids.size})")
    }

    private fun selectReturningHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectReturningHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected returning (${ids.size})")
    }

    private fun selectCargoHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectCargoHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected cargo (${ids.size})")
    }

    private fun selectDropoffBuildings() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectDropoffSelectionIds(snapshot, session.state.viewedFaction)
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        showNotice("selected dropoffs (${ids.size})")
    }

    private fun assignControlGroup(group: Int) {
        assignControlGroupSlot(controlGroups, group, session.state.selectedIds)
        showNotice("group $group set (${session.state.selectedIds.size})")
    }

    private fun recallControlGroup(group: Int, focusOnRecall: Boolean = false) {
        val snapshot = session.state.snapshot ?: return
        val ids = recallControlGroupSlot(controlGroups, group, snapshot)
        if (ids.isEmpty()) {
            showNotice("group $group empty")
            return
        }
        session.state.selectedIds.clear()
        for (i in ids.indices) session.state.selectedIds.add(ids[i])
        session.append(
            ClientIntent.Selection(
                buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())
            )
        )
        if (focusOnRecall) {
            val focus = computeSelectionCentroid(snapshot, ids)
            if (focus != null) {
                camera = centerCameraOnWorld(camera, width, height, focus.first, focus.second)
            }
        }
        showNotice("group $group recalled (${ids.size})")
    }

    private fun addToControlGroup(group: Int) {
        mergeControlGroupSlot(controlGroups, group, session.state.selectedIds)
        val size = controlGroups[group]?.size ?: 0
        showNotice("group $group add (${session.state.selectedIds.size}) -> $size")
    }

    private fun clearControlGroups() {
        clearControlGroupSlots(controlGroups)
        lastGroupRecall = null
        showNotice("groups cleared")
    }

    private fun isPresetAvailable(name: String): Boolean {
        val root = playRoot ?: return false
        return Files.exists(presetFilePath(root.resolve("presets"), name))
    }
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GraphicalClientKt <snapshot.ndjson|tcp://host:port> [input.ndjson|tcp://host:port] [play-control.txt] [play-scenario.txt] [play-root]" }
    val snapshotSpec = args[0]
    val inputSpec =
        if (args.size >= 2) {
            args[1]
        } else {
            val snapshotPath = Paths.get(snapshotSpec).toAbsolutePath().normalize()
            defaultClientInputPath(snapshotPath).toString()
        }
    val controlPath = if (args.size >= 3) Paths.get(args[2]).toAbsolutePath().normalize() else null
    val scenarioPath = if (args.size >= 4) Paths.get(args[3]).toAbsolutePath().normalize() else null
    val playRoot = if (args.size >= 5) Paths.get(args[4]).toAbsolutePath().normalize() else scenarioPath?.parent

    val session = ClientSession(openClientStreamSubscription(snapshotSpec), openClientInputSink(inputSpec))
    var restartRequested = false
    val panel = ClientPanel(session, controlPath = controlPath, scenarioPath = scenarioPath, playRoot = playRoot) { restartRequested = true }
    val appLoop = ClientAppLoop(session) { panel.repaint() }

    val frame = JFrame("Starkraft Client")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane.add(panel)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true
    panel.setFocusTraversalKeysEnabled(false)
    panel.focusTraversalKeysEnabled = false
    panel.requestFocusInWindow()

    Timer(50) {
        appLoop.tick()
        if (restartRequested) {
            frame.dispose()
        }
    }.start()

    frame.addWindowListener(
        object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                if (restartRequested) {
                    System.exit(CLIENT_EXIT_RESTART)
                }
            }
        }
    )
}

internal fun defaultClientInputPath(snapshotPath: Path): Path =
    snapshotPath.parent.resolve("client-input.ndjson").normalize()

internal fun formatAckStatus(ack: ClientCommandAck?): String =
    when {
        ack == null -> "last ack: none"
        ack.accepted -> "last ack: ok ${ack.commandType}${formatRequestIdSuffix(ack)} @${ack.tick}"
        else -> "last ack: fail ${ack.commandType}${formatRequestIdSuffix(ack)} @${ack.tick} reason=${ack.reason}"
    }

private fun formatRequestIdSuffix(ack: ClientCommandAck): String = ack.requestId?.let { "[$it]" } ?: ""

internal fun formatPlayControlOverlay(state: PlayControlState): String =
    "play: ${if (state.paused) "paused" else "running"} x${clampPlaySpeed(state.speed)}"

internal fun formatPresetAvailability(quickAvailable: Boolean, altAvailable: Boolean): String =
    "presets: quick=${if (quickAvailable) "ready" else "missing"} alt=${if (altAvailable) "ready" else "missing"}"

internal fun buildScenarioOverlayLines(
    open: Boolean,
    activeScenario: PlayScenario,
    selectedScenario: PlayScenario
): List<String> {
    if (!open) return emptyList()
    val lines = ArrayList<String>(PlayScenario.entries.size + 2)
    lines.add("scenario menu: enter apply  tab close")
    for (scenario in PlayScenario.entries) {
        val marker = if (scenario == selectedScenario) ">" else " "
        val active = if (scenario == activeScenario) " (current)" else ""
        lines.add("$marker ${scenario.id}$active")
    }
    return lines
}

internal fun buildPresetOverlayLines(
    open: Boolean,
    selectedSlot: String,
    quickAvailable: Boolean,
    altAvailable: Boolean
): List<String> {
    if (!open) return emptyList()
    fun line(name: String, ready: Boolean): String {
        val marker = if (selectedSlot == name) ">" else " "
        val status = if (ready) "ready" else "missing"
        return "$marker $name ($status)"
    }
    return listOf(
        "preset menu: s save  l/enter load  f10 close",
        line("quick", quickAvailable),
        line("alt", altAvailable)
    )
}

internal fun buildHelpOverlayLines(open: Boolean): List<String> {
    if (!open) return emptyList()
    return listOf(
        "help: f1 close  tab scenario menu  f10 preset menu",
        "help: left select  shift+left add/remove  right command  ctrl+right attackMove",
        "help: f2 select viewed faction  f3 select selected type  f4 archetype",
        "help: f11 select all units",
        "help: f12 select idle workers",
        "help: f select damaged units",
        "help: v select combat units",
        "help: n select producer buildings",
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
    )
}

private const val CONTROL_GROUP_DOUBLE_TAP_WINDOW_NANOS = 350_000_000L
private const val CONTROL_GROUP_HIGHLIGHT_TTL_NANOS = 2_500_000_000L

internal fun activeControlGroupHighlight(
    lastGroup: Int?,
    lastRecallAtNanos: Long,
    nowNanos: Long,
    ttlNanos: Long = CONTROL_GROUP_HIGHLIGHT_TTL_NANOS
): Int? {
    if (lastGroup == null) return null
    if (lastRecallAtNanos <= 0L) return null
    return if (nowNanos - lastRecallAtNanos <= ttlNanos) lastGroup else null
}

internal fun controlGroupFromKeyCode(keyCode: Int): Int? =
    when (keyCode) {
        KeyEvent.VK_4 -> 4
        KeyEvent.VK_5 -> 5
        KeyEvent.VK_6 -> 6
        KeyEvent.VK_7 -> 7
        KeyEvent.VK_8 -> 8
        KeyEvent.VK_9 -> 9
        else -> null
    }

internal fun buildUnitSelectionRecord(
    tick: Int,
    selectedIds: Collection<Int>
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "units",
        units = selectedIds.toIntArray()
    )

internal fun buildFactionSelectionRecord(
    tick: Int,
    faction: Int
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "faction",
        faction = faction
    )

internal fun buildTypeSelectionRecord(
    tick: Int,
    typeId: String
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "type",
        typeId = typeId
    )

internal fun buildArchetypeSelectionRecord(
    tick: Int,
    archetype: String
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "archetype",
        archetype = archetype
    )

internal fun buildAllSelectionRecord(
    tick: Int
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "all"
    )

internal fun collectFactionSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (entity.faction != faction) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectTypeSelectionIds(
    snapshot: ClientSnapshot,
    typeId: String,
    faction: Int
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (entity.faction != faction || entity.typeId != typeId) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectArchetypeSelectionIds(
    snapshot: ClientSnapshot,
    archetype: String,
    faction: Int
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (entity.faction != faction || entity.archetype != archetype) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectAllSelectionIds(
    snapshot: ClientSnapshot
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        out[count++] = snapshot.entities[i].id
    }
    return out.copyOf(count)
}

internal fun collectIdleWorkerSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.archetype != "worker") continue
        if (entity.buildTargetId != null) continue
        if (entity.harvestPhase != null) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectDamagedSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.hp >= entity.maxHp) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectCombatSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.weaponId == null) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectProducerSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.archetype != "producer") continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectTrainingSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.supportsTraining != true) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectResearchSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.supportsResearch != true) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectConstructionSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (!entity.underConstruction) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectHarvesterSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.archetype != "worker") continue
        if (entity.harvestPhase == null) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectReturningHarvesterSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.archetype != "worker") continue
        if (entity.harvestPhase != "return") continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectCargoHarvesterSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.archetype != "worker") continue
        if ((entity.harvestCargoAmount ?: 0) <= 0) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun collectDropoffSelectionIds(
    snapshot: ClientSnapshot,
    faction: Int?
): IntArray {
    val out = IntArray(snapshot.entities.size)
    var count = 0
    for (i in snapshot.entities.indices) {
        val entity = snapshot.entities[i]
        if (faction != null && entity.faction != faction) continue
        if (entity.supportsDropoff != true) continue
        out[count++] = entity.id
    }
    return out.copyOf(count)
}

internal fun assignControlGroupSlot(
    groups: Array<IntArray?>,
    group: Int,
    selectedIds: Set<Int>
) {
    if (group !in groups.indices) return
    groups[group] = selectedIds.toIntArray()
}

internal fun mergeControlGroupSlot(
    groups: Array<IntArray?>,
    group: Int,
    selectedIds: Set<Int>
) {
    if (group !in groups.indices) return
    if (selectedIds.isEmpty()) return
    val existing = groups[group] ?: IntArray(0)
    val merged = LinkedHashSet<Int>(existing.size + selectedIds.size)
    for (i in existing.indices) merged.add(existing[i])
    merged.addAll(selectedIds)
    groups[group] = merged.toIntArray()
}

internal fun clearControlGroupSlots(groups: Array<IntArray?>) {
    for (i in groups.indices) groups[i] = null
}

internal fun recallControlGroupSlot(
    groups: Array<IntArray?>,
    group: Int,
    snapshot: ClientSnapshot
): IntArray {
    if (group !in groups.indices) return IntArray(0)
    val stored = groups[group] ?: return IntArray(0)
    val out = IntArray(stored.size)
    var count = 0
    for (i in stored.indices) {
        val id = stored[i]
        if (snapshot.entities.any { it.id == id }) {
            out[count++] = id
        }
    }
    return out.copyOf(count)
}

internal fun formatControlGroupSummary(
    groups: Array<IntArray?>,
    highlightedGroup: Int? = null
): String? {
    val parts = ArrayList<String>(6)
    for (group in 4..9) {
        val size = groups.getOrNull(group)?.size ?: 0
        if (size <= 0) continue
        val prefix = if (highlightedGroup == group) "*" else ""
        parts.add("${prefix}${group}=$size")
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(" ")
}

internal fun computeSelectionCentroid(
    snapshot: ClientSnapshot,
    ids: IntArray
): Pair<Float, Float>? {
    if (ids.isEmpty()) return null
    var sumX = 0f
    var sumY = 0f
    var count = 0
    for (i in ids.indices) {
        val id = ids[i]
        val entity = snapshot.entities.firstOrNull { it.id == id } ?: continue
        sumX += entity.x
        sumY += entity.y
        count++
    }
    if (count == 0) return null
    return Pair(sumX / count, sumY / count)
}

internal fun applySelectionClick(
    selectedIds: LinkedHashSet<Int>,
    clickedId: Int?,
    additive: Boolean
) {
    if (!additive) {
        selectedIds.clear()
        if (clickedId != null) selectedIds.add(clickedId)
        return
    }
    if (clickedId == null) return
    if (!selectedIds.add(clickedId)) {
        selectedIds.remove(clickedId)
    }
}
