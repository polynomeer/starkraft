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
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashSet
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

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

private class ClientPanel(
    private val session: ClientSession,
    private val renderer: ClientRenderer = SwingClientRenderer()
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

    init {
        background = Color(0x12, 0x18, 0x1F)
        preferredSize = Dimension(640, 640)
        font = Font("Monospaced", Font.PLAIN, 12)
        isFocusable = true
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
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
                    KeyEvent.VK_H -> {
                        val snapshot = session.state.snapshot ?: return
                        val hold = buildHoldIntent(snapshot, session.state.selectedIds, requestIds) ?: return
                        session.append(hold)
                        groundMode = null
                    }
                    KeyEvent.VK_ESCAPE -> {
                        session.state.selectedIds.clear()
                        groundMode = null
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
        if (draggingSelection && isSelectionDrag()) {
            drawSelectionBox(g)
        }
    }

    private fun handleMouse(e: MouseEvent) {
        val snapshot = session.state.snapshot ?: return
        val worldX = camera.screenToWorldX(e.x.toFloat())
        val worldY = camera.screenToWorldY(e.y.toFloat())
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
            "mode: ${groundMode?.name?.lowercase()?.replace('_', '-') ?: "default"}"
        )
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GraphicalClientKt <snapshot.ndjson|tcp://host:port> [input.ndjson|tcp://host:port]" }
    val snapshotSpec = args[0]
    val inputSpec =
        if (args.size >= 2) {
            args[1]
        } else {
            val snapshotPath = Paths.get(snapshotSpec).toAbsolutePath().normalize()
            defaultClientInputPath(snapshotPath).toString()
        }

    val session = ClientSession(openClientStreamSubscription(snapshotSpec), openClientInputSink(inputSpec))
    val panel = ClientPanel(session)
    val appLoop = ClientAppLoop(session) { panel.repaint() }

    val frame = JFrame("Starkraft Client")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane.add(panel)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    Timer(50) {
        appLoop.tick()
    }.start()
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
