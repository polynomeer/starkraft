package starkraft.sim.client

import starkraft.sim.net.InputJson
import java.awt.BasicStroke
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
    private val session: ClientSession
) : JPanel() {
    private val tileSize = 20
    private val requestIds = ClientCommandIds()
    private val friendlyColor = Color(0x4B, 0x8B, 0xFF)
    private val enemyColor = Color(0xE0, 0x5A, 0x47)
    private val neutralColor = Color(0xC8, 0xB0, 0x72)
    private val selectionColor = Color(0xF4, 0xE2, 0x71)

    init {
        background = Color(0x12, 0x18, 0x1F)
        preferredSize = Dimension(640, 640)
        font = Font("Monospaced", Font.PLAIN, 12)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                handleMouse(e)
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val snapshot = session.state.snapshot ?: run {
            g.color = Color.WHITE
            g.drawString("waiting for snapshots...", 16, 24)
            return
        }
        drawGrid(g, snapshot)
        drawResources(g, snapshot)
        drawEntities(g, snapshot)
        drawHud(g, snapshot)
    }

    private fun drawGrid(g: Graphics2D, snapshot: ClientSnapshot) {
        g.color = Color(0x22, 0x2A, 0x33)
        for (x in 0..snapshot.mapWidth) {
            val px = x * tileSize
            g.drawLine(px, 0, px, snapshot.mapHeight * tileSize)
        }
        for (y in 0..snapshot.mapHeight) {
            val py = y * tileSize
            g.drawLine(0, py, snapshot.mapWidth * tileSize, py)
        }
    }

    private fun drawResources(g: Graphics2D, snapshot: ClientSnapshot) {
        for (node in snapshot.resourceNodes) {
            val px = (node.x * tileSize).toInt()
            val py = (node.y * tileSize).toInt()
            g.color = if (node.kind == "gas") Color(0x3A, 0xC4, 0x92) else neutralColor
            g.fillOval(px - 7, py - 7, 14, 14)
            g.color = Color.BLACK
            g.drawString(node.remaining.toString(), px - 8, py - 10)
        }
    }

    private fun drawEntities(g: Graphics2D, snapshot: ClientSnapshot) {
        for (entity in snapshot.entities) {
            val px = (entity.x * tileSize).toInt()
            val py = (entity.y * tileSize).toInt()
            g.color =
                when (entity.faction) {
                    1 -> friendlyColor
                    2 -> enemyColor
                    else -> neutralColor
                }
            val radius = if (entity.footprintWidth != null && entity.footprintHeight != null) 9 else 6
            g.fillOval(px - radius, py - radius, radius * 2, radius * 2)
            if (entity.id in session.state.selectedIds) {
                g.color = selectionColor
                g.stroke = BasicStroke(2f)
                g.drawOval(px - radius - 4, py - radius - 4, (radius + 4) * 2, (radius + 4) * 2)
            }
        }
    }

    private fun drawHud(g: Graphics2D, snapshot: ClientSnapshot) {
        g.color = Color.WHITE
        g.drawString("tick=${snapshot.tick} selected=${session.state.selectedIds.size}", 12, height - 44)
        g.drawString(formatAckStatus(session.state.lastAck), 12, height - 28)
        g.drawString("left: select   shift+left: add/remove   right: move/attack/harvest", 12, height - 12)
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
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GraphicalClientKt <snapshot.ndjson> [input.ndjson]" }
    val snapshotPath = Paths.get(args[0]).toAbsolutePath().normalize()
    val inputPath = if (args.size >= 2) Paths.get(args[1]).toAbsolutePath().normalize() else defaultClientInputPath(snapshotPath)

    val session = ClientSession(snapshotPath, inputPath)
    val panel = ClientPanel(session)

    val frame = JFrame("Starkraft Client")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane.add(panel)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    Timer(50) {
        if (session.poll()) {
            panel.repaint()
        }
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
