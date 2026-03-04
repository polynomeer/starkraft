package starkraft.sim.client

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.util.LinkedHashMap

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
        val hudLines = buildClientHudLines(snapshot, state)
        val baseY = height - 12 - ((hudLines.size - 1) * 16)
        for (i in hudLines.indices) {
            g.drawString(hudLines[i], 12, baseY + (i * 16))
        }
    }
}

internal fun buildClientHudLines(
    snapshot: ClientSnapshot,
    state: ClientSessionState
): List<String> =
    listOf(
        "tick=${snapshot.tick} selected=${state.selectedIds.size}",
        buildSelectionSummary(snapshot, state.selectedIds),
        buildBuilderSummary(snapshot, state.selectedIds),
        buildConstructionSummary(snapshot, state.selectedIds),
        buildProductionSummary(snapshot, state.selectedIds),
        buildResearchSummary(snapshot, state.selectedIds),
        formatTickActivity(state.lastTickActivity),
        formatProductionActivity(state.lastProductionActivity),
        formatResearchActivity(state.lastResearchActivity),
        formatAckStatus(state.lastAck),
        "left: select   shift+left: add/remove   right: move/attack/harvest   ctrl+right: attackMove"
    )

internal fun buildSelectionSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "selection: none"
    val counts = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        counts[entity.typeId] = (counts[entity.typeId] ?: 0) + 1
    }
    if (counts.isEmpty()) return "selection: none"
    val summary = counts.entries.joinToString(" ") { "${it.key}x${it.value}" }
    return "selection: $summary"
}

internal fun buildBuilderSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "builders: none"
    var activeBuilders = 0
    val targets = LinkedHashMap<Int, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        val targetId = entity.buildTargetId ?: continue
        activeBuilders++
        targets[targetId] = (targets[targetId] ?: 0) + 1
    }
    if (activeBuilders == 0) return "builders: none"
    return "builders: active=$activeBuilders targets=${targets.size}"
}

internal fun buildResearchSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "research: none"
    var researchBuildings = 0
    var queued = 0
    val activeTechs = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        if (entity.researchQueueSize <= 0 && entity.activeResearchTech == null) continue
        researchBuildings++
        queued += entity.researchQueueSize
        entity.activeResearchTech?.let { tech ->
            activeTechs[tech] = (activeTechs[tech] ?: 0) + 1
        }
    }
    if (researchBuildings == 0) return "research: none"
    val active =
        if (activeTechs.isEmpty()) {
            "idle"
        } else {
            activeTechs.entries.joinToString(" ") { "${it.key}x${it.value}" }
    }
    return "research: labs=$researchBuildings queue=$queued active=$active"
}

internal fun buildProductionSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "production: none"
    var producers = 0
    var queued = 0
    val activeTypes = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        if (entity.productionQueueSize <= 0 && entity.activeProductionType == null) continue
        producers++
        queued += entity.productionQueueSize
        entity.activeProductionType?.let { typeId ->
            activeTypes[typeId] = (activeTypes[typeId] ?: 0) + 1
        }
    }
    if (producers == 0) return "production: none"
    val active =
        if (activeTypes.isEmpty()) {
            "idle"
        } else {
            activeTypes.entries.joinToString(" ") { "${it.key}x${it.value}" }
        }
    return "production: labs=$producers queue=$queued active=$active"
}

internal fun buildConstructionSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "construction: none"
    var sites = 0
    var remainingTicks = 0
    val buildingTypes = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        if (!entity.underConstruction) continue
        sites++
        remainingTicks += entity.constructionRemainingTicks ?: 0
        buildingTypes[entity.typeId] = (buildingTypes[entity.typeId] ?: 0) + 1
    }
    if (sites == 0) return "construction: none"
    val kinds = buildingTypes.entries.joinToString(" ") { "${it.key}x${it.value}" }
    return "construction: sites=$sites remaining=$remainingTicks $kinds"
}

internal fun formatResearchActivity(activity: ClientResearchActivity?): String =
    if (activity == null) {
        "research events: none"
    } else {
        "research events: e${activity.enqueue}/p${activity.progress}/c${activity.complete}/x${activity.cancel} @${activity.tick}"
    }

internal fun formatProductionActivity(activity: ClientProductionActivity?): String =
    if (activity == null) {
        "production events: none"
    } else {
        "production events: e${activity.enqueue}/p${activity.progress}/c${activity.complete}/x${activity.cancel} @${activity.tick}"
    }

internal fun formatTickActivity(activity: ClientTickActivity?): String =
    if (activity == null) {
        "activity: none"
    } else {
        "activity: builds=${activity.builds}/x${activity.buildsCancelled} " +
            "buildFails=${activity.buildFailures}[${activity.buildFailureReasons}] " +
            "train=q${activity.trainsQueued}/c${activity.trainsCompleted}/x${activity.trainsCancelled} " +
            "trainFails=${activity.trainFailures}[${activity.trainFailureReasons}] " +
            "research=q${activity.researchQueued}/c${activity.researchCompleted}/x${activity.researchCancelled} " +
            "researchFails=${activity.researchFailures}[${activity.researchFailureReasons}] @${activity.tick}"
    }
