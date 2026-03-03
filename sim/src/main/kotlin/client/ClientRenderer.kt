package starkraft.sim.client

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D

internal interface ClientRenderer {
    fun render(graphics: Graphics2D, width: Int, height: Int, state: ClientSessionState)
}

internal class SwingClientRenderer(
    private val tileSize: Int = 20
) : ClientRenderer {
    private val friendlyColor = Color(0x4B, 0x8B, 0xFF)
    private val enemyColor = Color(0xE0, 0x5A, 0x47)
    private val neutralColor = Color(0xC8, 0xB0, 0x72)
    private val selectionColor = Color(0xF4, 0xE2, 0x71)

    override fun render(graphics: Graphics2D, width: Int, height: Int, state: ClientSessionState) {
        val snapshot = state.snapshot ?: run {
            graphics.color = Color.WHITE
            graphics.drawString("waiting for snapshots...", 16, 24)
            return
        }
        drawGrid(graphics, snapshot)
        drawResources(graphics, snapshot)
        drawEntities(graphics, state.selectedIds, snapshot)
        drawHud(graphics, height, state, snapshot)
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

    private fun drawEntities(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot) {
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
            if (entity.id in selectedIds) {
                g.color = selectionColor
                g.stroke = BasicStroke(2f)
                g.drawOval(px - radius - 4, py - radius - 4, (radius + 4) * 2, (radius + 4) * 2)
            }
        }
    }

    private fun drawHud(
        g: Graphics2D,
        height: Int,
        state: ClientSessionState,
        snapshot: ClientSnapshot
    ) {
        g.color = Color.WHITE
        val hudLines = buildClientHudLines(snapshot.tick, state.selectedIds.size, state.lastAck)
        g.drawString(hudLines[0], 12, height - 44)
        g.drawString(hudLines[1], 12, height - 28)
        g.drawString(hudLines[2], 12, height - 12)
    }
}

internal fun buildClientHudLines(
    tick: Int,
    selectedCount: Int,
    ack: ClientCommandAck?
): List<String> =
    listOf(
        "tick=$tick selected=$selectedCount",
        formatAckStatus(ack),
        "left: select   shift+left: add/remove   right: move/attack/harvest   ctrl+right: attackMove"
    )
