package starkraft.sim.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.hypot

private val clientJson = Json { ignoreUnknownKeys = true }

private data class ClientViewState(
    var snapshot: ClientSnapshot? = null,
    val selectedIds: LinkedHashSet<Int> = LinkedHashSet(),
    var lastAck: ClientCommandAck? = null
)

internal data class ClientCommandAck(
    val tick: Int,
    val commandType: String,
    val requestId: String? = null,
    val accepted: Boolean,
    val reason: String? = null
)

private class SnapshotTail(path: Path) {
    init {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        if (!Files.exists(path)) Files.createFile(path)
    }

    private val file = RandomAccessFile(path.toFile(), "r")
    var latestSnapshot: ClientSnapshot? = null
        private set
    var latestAck: ClientCommandAck? = null
        private set

    fun poll() {
        while (true) {
            val line = file.readLine() ?: break
            if (line.isBlank()) continue
            val obj = clientJson.parseToJsonElement(line).jsonObject
            when (obj["recordType"]?.jsonPrimitive?.content) {
                "snapshot" -> {
                    val snapshot = obj["snapshot"] ?: continue
                    latestSnapshot = clientJson.decodeFromJsonElement(ClientSnapshot.serializer(), snapshot)
                }
                "commandAck" -> {
                    latestAck =
                        ClientCommandAck(
                            tick = obj["tick"]?.jsonPrimitive?.content?.toInt() ?: 0,
                            commandType = obj["commandType"]?.jsonPrimitive?.content ?: "unknown",
                            requestId = obj["requestId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
                            accepted = obj["accepted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                            reason = obj["reason"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                        )
                }
            }
        }
    }
}

private class CommandAppender(private val path: Path) {
    private val json = Json { encodeDefaults = true }

    init {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        if (!Files.exists(path)) Files.createFile(path)
    }

    fun append(record: InputJson.InputCommandRecord) {
        Files.writeString(
            path,
            json.encodeToString(InputJson.InputCommandRecord.serializer(), record) + "\n",
            StandardOpenOption.APPEND
        )
    }
}

private class ClientPanel(
    private val state: ClientViewState,
    private val commandAppender: CommandAppender
) : JPanel() {
    private val tileSize = 20
    private var nextRequestId = 1L
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
        val snapshot = state.snapshot ?: run {
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
            if (entity.id in state.selectedIds) {
                g.color = selectionColor
                g.stroke = BasicStroke(2f)
                g.drawOval(px - radius - 4, py - radius - 4, (radius + 4) * 2, (radius + 4) * 2)
            }
        }
    }

    private fun drawHud(g: Graphics2D, snapshot: ClientSnapshot) {
        g.color = Color.WHITE
        g.drawString("tick=${snapshot.tick} selected=${state.selectedIds.size}", 12, height - 44)
        g.drawString(formatAckStatus(state.lastAck), 12, height - 28)
        g.drawString("left click: select   right click: move/attack/harvest", 12, height - 12)
    }

    private fun handleMouse(e: MouseEvent) {
        val snapshot = state.snapshot ?: return
        val worldX = e.x.toFloat() / tileSize.toFloat()
        val worldY = e.y.toFloat() / tileSize.toFloat()
        if (SwingUtilities.isLeftMouseButton(e)) {
            val selected = nearestEntity(snapshot, worldX, worldY) { it.faction == 1 }
            state.selectedIds.clear()
            if (selected != null) state.selectedIds.add(selected.id)
            repaint()
            return
        }
        if (!SwingUtilities.isRightMouseButton(e) || state.selectedIds.isEmpty()) return

        val enemy = nearestEntity(snapshot, worldX, worldY) { it.faction == 2 && distance(it.x, it.y, worldX, worldY) <= 0.8f }
        if (enemy != null) {
            commandAppender.append(
                InputJson.InputCommandRecord(
                    tick = snapshot.tick + 1,
                    commandType = "attack",
                    requestId = nextRequestId(),
                    units = state.selectedIds.toIntArray(),
                    target = enemy.id
                )
            )
            return
        }
        val node = nearestResourceNode(snapshot, worldX, worldY)
        if (node != null && distance(node.x, node.y, worldX, worldY) <= 0.8f) {
            commandAppender.append(
                InputJson.InputCommandRecord(
                    tick = snapshot.tick + 1,
                    commandType = "harvest",
                    requestId = nextRequestId(),
                    units = state.selectedIds.toIntArray(),
                    target = node.id
                )
            )
            return
        }
        commandAppender.append(
            InputJson.InputCommandRecord(
                tick = snapshot.tick + 1,
                commandType = "move",
                requestId = nextRequestId(),
                units = state.selectedIds.toIntArray(),
                x = worldX,
                y = worldY
            )
        )
    }

    private fun nearestEntity(
        snapshot: ClientSnapshot,
        x: Float,
        y: Float,
        predicate: (EntitySnapshot) -> Boolean
    ): EntitySnapshot? {
        var best: EntitySnapshot? = null
        var bestDistance = Float.MAX_VALUE
        for (entity in snapshot.entities) {
            if (!predicate(entity)) continue
            val d = distance(entity.x, entity.y, x, y)
            if (d < bestDistance) {
                bestDistance = d
                best = entity
            }
        }
        return best
    }

    private fun nearestResourceNode(snapshot: ClientSnapshot, x: Float, y: Float): ResourceNodeSnapshot? {
        var best: ResourceNodeSnapshot? = null
        var bestDistance = Float.MAX_VALUE
        for (node in snapshot.resourceNodes) {
            val d = distance(node.x, node.y, x, y)
            if (d < bestDistance) {
                bestDistance = d
                best = node
            }
        }
        return best
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(abs(ax - bx), abs(ay - by))

    private fun nextRequestId(): String = "gc-${nextRequestId++}"
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GraphicalClientKt <snapshot.ndjson> [input.ndjson]" }
    val snapshotPath = Paths.get(args[0]).toAbsolutePath().normalize()
    val inputPath = if (args.size >= 2) Paths.get(args[1]).toAbsolutePath().normalize() else defaultClientInputPath(snapshotPath)

    val state = ClientViewState()
    val tail = SnapshotTail(snapshotPath)
    val commandAppender = CommandAppender(inputPath)
    val panel = ClientPanel(state, commandAppender)

    val frame = JFrame("Starkraft Client")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane.add(panel)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    Timer(50) {
        tail.poll()
        val latest = tail.latestSnapshot ?: return@Timer
        state.lastAck = tail.latestAck ?: state.lastAck
        if (state.snapshot?.tick != latest.tick) {
            state.snapshot = latest
            state.selectedIds.retainAll(latest.entities.mapTo(HashSet()) { it.id })
            panel.repaint()
        } else if (tail.latestAck != null) {
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
