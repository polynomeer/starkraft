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
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashSet
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

private class ClientPanel(
    private val session: ClientSession,
    private val renderer: ClientRenderer = SwingClientRenderer()
) : JPanel() {
    private val requestIds = ClientCommandIds()
    private val tileSize = 20
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragCurrentX = 0
    private var dragCurrentY = 0
    private var draggingSelection = false

    init {
        background = Color(0x12, 0x18, 0x1F)
        preferredSize = Dimension(640, 640)
        font = Font("Monospaced", Font.PLAIN, 12)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
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
                if (!draggingSelection) return
                dragCurrentX = e.x
                dragCurrentY = e.y
                repaint()
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        renderer.render(g, width, height, session.state)
        if (draggingSelection && isSelectionDrag()) {
            drawSelectionBox(g)
        }
    }

    private fun handleMouse(e: MouseEvent) {
        val snapshot = session.state.snapshot ?: return
        val worldX = e.x.toFloat() / tileSize.toFloat()
        val worldY = e.y.toFloat() / tileSize.toFloat()
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
                    additiveSelection = e.isShiftDown,
                    requestIds = requestIds
                )
        ) {
            is ClientIntent.Selection -> {
                session.append(intent)
                repaint()
            }
            is ClientIntent.Command -> session.append(intent)
            null -> return
        }
    }

    private fun handleSelectionBox(e: MouseEvent) {
        val snapshot = session.state.snapshot ?: return
        val intent =
            selectEntitiesInBox(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                startWorldX = dragStartX.toFloat() / tileSize.toFloat(),
                startWorldY = dragStartY.toFloat() / tileSize.toFloat(),
                endWorldX = e.x.toFloat() / tileSize.toFloat(),
                endWorldY = e.y.toFloat() / tileSize.toFloat(),
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
