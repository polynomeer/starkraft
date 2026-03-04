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
    when (typeId) {
        "Depot" -> BuildPreviewSpec(typeId, 2, 2, 1, 100, 0)
        "ResourceDepot" -> BuildPreviewSpec(typeId, 2, 2, 1, 75, 0)
        "GasDepot" -> BuildPreviewSpec(typeId, 2, 2, 1, 90, 0)
        else -> null
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
    private val controlPath: Path? = null,
    private val scenarioPath: Path? = null,
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

    init {
        if (controlPath != null && Files.exists(controlPath)) {
            playControlState = parsePlayControlState(Files.readString(controlPath))
        }
        if (scenarioPath != null) {
            playScenario = readPlayScenario(scenarioPath, PlayScenario.SKIRMISH)
        }
        background = Color(0x12, 0x18, 0x1F)
        preferredSize = Dimension(640, 640)
        font = Font("Monospaced", Font.PLAIN, 12)
        isFocusable = true
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
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> camera = camera.copy(panX = camera.panX + delta)
                    KeyEvent.VK_RIGHT -> camera = camera.copy(panX = camera.panX - delta)
                    KeyEvent.VK_UP -> camera = camera.copy(panY = camera.panY + delta)
                    KeyEvent.VK_DOWN -> camera = camera.copy(panY = camera.panY - delta)
                    KeyEvent.VK_W -> camera = camera.copy(panY = camera.panY + delta)
                    KeyEvent.VK_S -> camera = camera.copy(panY = camera.panY - delta)
                    KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> camera = zoomCameraAt(camera, width / 2f, height / 2f, 1.1f)
                    KeyEvent.VK_MINUS -> camera = zoomCameraAt(camera, width / 2f, height / 2f, 0.9f)
                    KeyEvent.VK_0 -> camera = CameraView()
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
                        buildQueueIntent(snapshot, session.state.selectedIds, "train", "Worker", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_I -> {
                        val snapshot = session.state.snapshot ?: return
                        buildQueueIntent(snapshot, session.state.selectedIds, "train", "Marine", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_O -> {
                        val snapshot = session.state.snapshot ?: return
                        buildQueueIntent(snapshot, session.state.selectedIds, "train", "Zergling", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_L -> {
                        val snapshot = session.state.snapshot ?: return
                        buildQueueIntent(snapshot, session.state.selectedIds, "research", "AdvancedTraining", requestIds)?.let(session::append)
                    }
                    KeyEvent.VK_SPACE -> togglePause()
                    KeyEvent.VK_OPEN_BRACKET -> adjustSpeed(-1)
                    KeyEvent.VK_CLOSE_BRACKET -> adjustSpeed(1)
                    KeyEvent.VK_F6 -> cycleScenario(-1)
                    KeyEvent.VK_F7 -> cycleScenario(1)
                    KeyEvent.VK_F5 -> requestRestart()
                    KeyEvent.VK_ESCAPE -> {
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
            "scenario: ${playScenario.id}"
        )

    private fun handleCommandPanelClick(e: MouseEvent): Boolean {
        val snapshot = session.state.snapshot
        val canTrain = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsTraining == true } }
        val canResearch = snapshot != null && session.state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsResearch == true } }
        val button = commandButtonAt(width, e.x, e.y, session.state.selectedIds.isNotEmpty(), canTrain, canResearch) ?: return false
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
            "train:Worker" -> {
                val snapshot = session.state.snapshot ?: return true
                buildQueueIntent(snapshot, session.state.selectedIds, "train", "Worker", requestIds)?.let(session::append)
            }
            "train:Marine" -> {
                val snapshot = session.state.snapshot ?: return true
                buildQueueIntent(snapshot, session.state.selectedIds, "train", "Marine", requestIds)?.let(session::append)
            }
            "train:Zergling" -> {
                val snapshot = session.state.snapshot ?: return true
                buildQueueIntent(snapshot, session.state.selectedIds, "train", "Zergling", requestIds)?.let(session::append)
            }
            "research:AdvancedTraining" -> {
                val snapshot = session.state.snapshot ?: return true
                buildQueueIntent(snapshot, session.state.selectedIds, "research", "AdvancedTraining", requestIds)?.let(session::append)
            }
            "clear" -> {
                session.state.selectedIds.clear()
                groundMode = null
                buildModeTypeId = null
            }
            else -> {
                if (button.actionId.startsWith("build:")) {
                    buildModeTypeId = button.actionId.removePrefix("build:")
                    groundMode = null
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
        writePlayScenario(path, playScenario)
        requestRestart()
    }
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GraphicalClientKt <snapshot.ndjson|tcp://host:port> [input.ndjson|tcp://host:port] [play-control.txt] [play-scenario.txt]" }
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

    val session = ClientSession(openClientStreamSubscription(snapshotSpec), openClientInputSink(inputSpec))
    var restartRequested = false
    val panel = ClientPanel(session, controlPath = controlPath, scenarioPath = scenarioPath) { restartRequested = true }
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

internal fun buildUnitSelectionRecord(
    tick: Int,
    selectedIds: Collection<Int>
): InputJson.InputSelectionRecord =
    InputJson.InputSelectionRecord(
        tick = tick,
        selectionType = "units",
        units = selectedIds.toIntArray()
    )

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
