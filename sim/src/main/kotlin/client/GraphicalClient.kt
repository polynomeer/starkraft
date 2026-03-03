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
        renderer.render(g, width, height, session.state)
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
