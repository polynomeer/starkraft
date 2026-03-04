package starkraft.sim.client

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.util.LinkedHashMap

internal interface ClientRenderer {
    fun render(graphics: Graphics2D, width: Int, height: Int, state: ClientSessionState, camera: CameraView, overlayLines: List<String> = emptyList())
}

internal class SwingClientRenderer(
    private val tileSize: Int = 20
) : ClientRenderer {
    private val friendlyColor = Color(0x4B, 0x8B, 0xFF)
    private val enemyColor = Color(0xE0, 0x5A, 0x47)
    private val neutralColor = Color(0xC8, 0xB0, 0x72)
    private val selectionColor = Color(0xF4, 0xE2, 0x71)

    override fun render(graphics: Graphics2D, width: Int, height: Int, state: ClientSessionState, camera: CameraView, overlayLines: List<String>) {
        val snapshot = state.snapshot ?: run {
            graphics.color = Color.WHITE
            graphics.drawString("waiting for snapshots...", 16, 24)
            return
        }
        val effectiveCamera = camera.copy(baseTileSize = tileSize)
        drawGrid(graphics, snapshot, effectiveCamera)
        drawResources(graphics, snapshot, effectiveCamera)
        drawRallyMarkers(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawEntities(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawHud(graphics, height, state, snapshot, overlayLines)
    }

    private fun drawGrid(g: Graphics2D, snapshot: ClientSnapshot, camera: CameraView) {
        g.color = Color(0x22, 0x2A, 0x33)
        for (x in 0..snapshot.mapWidth) {
            val px = camera.worldToScreenX(x.toFloat()).toInt()
            g.drawLine(px, 0, px, camera.worldToScreenY(snapshot.mapHeight.toFloat()).toInt())
        }
        for (y in 0..snapshot.mapHeight) {
            val py = camera.worldToScreenY(y.toFloat()).toInt()
            g.drawLine(0, py, camera.worldToScreenX(snapshot.mapWidth.toFloat()).toInt(), py)
        }
    }

    private fun drawResources(g: Graphics2D, snapshot: ClientSnapshot, camera: CameraView) {
        for (node in snapshot.resourceNodes) {
            val px = camera.worldToScreenX(node.x).toInt()
            val py = camera.worldToScreenY(node.y).toInt()
            g.color = if (node.kind == "gas") Color(0x3A, 0xC4, 0x92) else neutralColor
            g.fillOval(px - 7, py - 7, 14, 14)
            g.color = Color.BLACK
            g.drawString(node.remaining.toString(), px - 8, py - 10)
        }
    }

    private fun drawEntities(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView) {
        for (entity in snapshot.entities) {
            val px = camera.worldToScreenX(entity.x).toInt()
            val py = camera.worldToScreenY(entity.y).toInt()
            g.color =
                when (entity.faction) {
                    1 -> friendlyColor
                    2 -> enemyColor
                    else -> neutralColor
                }
            val radius = if (entity.footprintWidth != null && entity.footprintHeight != null) 9 else 6
            g.fillOval(px - radius, py - radius, radius * 2, radius * 2)
            drawHealthBar(g, px, py, radius, entity.hp, entity.maxHp)
            if (entity.id in selectedIds) {
                g.color = selectionColor
                g.stroke = BasicStroke(2f)
                g.drawOval(px - radius - 4, py - radius - 4, (radius + 4) * 2, (radius + 4) * 2)
            }
        }
    }

    private fun drawHealthBar(g: Graphics2D, px: Int, py: Int, radius: Int, hp: Int, maxHp: Int) {
        val barWidth = radius * 2 + 4
        val barHeight = 4
        val x = px - (barWidth / 2)
        val y = py - radius - 10
        g.color = Color(0x20, 0x20, 0x20, 220)
        g.fillRect(x, y, barWidth, barHeight)
        g.color = when {
            hp * 100 >= maxHp * 66 -> Color(0x45, 0xD4, 0x6B)
            hp * 100 >= maxHp * 33 -> Color(0xE5, 0xB6, 0x34)
            else -> Color(0xD7, 0x4A, 0x4A)
        }
        g.fillRect(x, y, healthBarFillWidth(barWidth, hp, maxHp), barHeight)
    }

    private fun drawRallyMarkers(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView) {
        g.color = Color(0x8D, 0xF1, 0x8B)
        g.stroke = BasicStroke(1.5f)
        for (entity in snapshot.entities) {
            if (entity.id !in selectedIds) continue
            val rallyX = entity.rallyX ?: continue
            val rallyY = entity.rallyY ?: continue
            val startX = camera.worldToScreenX(entity.x).toInt()
            val startY = camera.worldToScreenY(entity.y).toInt()
            val endX = camera.worldToScreenX(rallyX).toInt()
            val endY = camera.worldToScreenY(rallyY).toInt()
            g.drawLine(startX, startY, endX, endY)
            g.drawOval(endX - 5, endY - 5, 10, 10)
        }
    }

    private fun drawHud(
        g: Graphics2D,
        height: Int,
        state: ClientSessionState,
        snapshot: ClientSnapshot,
        overlayLines: List<String>
    ) {
        g.color = Color.WHITE
        val hudLines = buildClientHudLines(snapshot, state) + overlayLines
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
        buildRallySummary(snapshot, state.selectedIds),
        buildTechSummary(snapshot),
        formatTickActivity(state.lastTickActivity),
        formatConstructionActivity(state.lastConstructionActivity),
        formatProductionActivity(state.lastProductionActivity),
        formatResearchActivity(state.lastResearchActivity),
        formatAckStatus(state.lastAck),
        "left: select/drag   shift+left: add/remove/add-box   middle-drag/wheel: pan/zoom",
        "right: move/attack/harvest   ctrl+right: attackMove",
        "keys: m move   a attackMove   p patrol   h hold   esc clear"
    )

internal fun healthBarFillWidth(barWidth: Int, hp: Int, maxHp: Int): Int {
    if (barWidth <= 0 || hp <= 0 || maxHp <= 0) return 0
    return ((barWidth.toLong() * hp.toLong()) / maxHp.toLong()).toInt().coerceIn(0, barWidth)
}

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

internal fun buildRallySummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "rally: none"
    val rallies = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        val x = entity.rallyX ?: continue
        val y = entity.rallyY ?: continue
        val key = "${"%.1f".format(x)},${"%.1f".format(y)}"
        rallies[key] = (rallies[key] ?: 0) + 1
    }
    if (rallies.isEmpty()) return "rally: none"
    return "rally: " + rallies.entries.joinToString(" ") { "${it.key}x${it.value}" }
}

internal fun buildTechSummary(snapshot: ClientSnapshot): String {
    val techs = LinkedHashMap<String, Int>()
    for (faction in snapshot.factions) {
        for (techId in faction.unlockedTechIds) {
            techs[techId] = (techs[techId] ?: 0) + 1
        }
    }
    if (techs.isEmpty()) return "tech: none"
    return "tech: " + techs.entries.joinToString(" ") { "${it.key}x${it.value}" }
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

internal fun formatConstructionActivity(activity: ClientConstructionActivity?): String =
    if (activity == null) {
        "construction state: none"
    } else {
        "construction state: total=${activity.total} f1=${activity.faction1} f2=${activity.faction2} remaining=${activity.remainingTicks} @${activity.tick}"
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
