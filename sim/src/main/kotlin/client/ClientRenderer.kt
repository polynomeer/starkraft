package starkraft.sim.client

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.util.LinkedHashMap

internal data class ClientCommandButton(
    val label: String,
    val actionId: String
)

internal fun commandButtonTooltip(actionId: String): String? =
    when {
        actionId == "move" -> "Queue a move order with right click or ground mode"
        actionId == "attackMove" -> "Advance and auto-engage enemies along the way"
        actionId == "patrol" -> "Patrol between the current position and a target point"
        actionId == "hold" -> "Hold position but still attack enemies already in range"
        actionId == "cancelBuild" -> "Cancel the first selected construction site and refund its cost"
        actionId == "cancelTrain" -> "Cancel the last queued training job on the first selected producer"
        actionId == "cancelResearch" -> "Cancel the last queued research job on the first selected lab"
        actionId.startsWith("build:") -> "Enter placement mode for ${actionId.removePrefix("build:")}"
        actionId.startsWith("train:") -> "Queue ${actionId.removePrefix("train:")} on the first selected producer"
        actionId.startsWith("research:") -> "Queue ${actionId.removePrefix("research:")} on the first selected research building"
        actionId == "play:pause" -> "Toggle play pause and resume"
        actionId == "play:slower" -> "Lower play speed by one step"
        actionId == "play:faster" -> "Raise play speed by one step"
        actionId == "preset:save:quick" -> "Save the current scenario and speed into preset quick"
        actionId == "preset:load:quick" -> "Load preset quick and restart into it"
        actionId == "preset:save:alt" -> "Save the current scenario and speed into preset alt"
        actionId == "preset:load:alt" -> "Load preset alt and restart into it"
        actionId == "scenario:prev" -> "Switch to the previous play scenario and restart"
        actionId == "scenario:next" -> "Switch to the next play scenario and restart"
        actionId == "clear" -> "Clear the current selection and command mode"
        else -> null
    }

internal interface ClientRenderer {
    fun render(graphics: Graphics2D, width: Int, height: Int, state: ClientSessionState, camera: CameraView, overlayLines: List<String> = emptyList())
}

internal data class BuildPreviewLabel(
    val title: String,
    val cost: String,
    val size: String,
    val valid: Boolean
)

internal data class ClientGameState(
    val title: String,
    val detail: String
)

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
        drawPathMarkers(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawRallyMarkers(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawTaskMarkers(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawStatusMarkers(graphics, state.selectedIds, snapshot, effectiveCamera)
        drawEntities(graphics, state.selectedIds, snapshot, effectiveCamera, state.viewedFaction)
        drawFog(graphics, snapshot, state.visionState, effectiveCamera, state.viewedFaction)
        drawMiniMap(graphics, width, height, snapshot, state.selectedIds, effectiveCamera)
        drawCommandPanel(graphics, width, height, state, overlayLines)
        drawHud(graphics, height, state, snapshot, overlayLines)
        drawStartOverlay(graphics, snapshot, overlayLines)
        drawGameStateOverlay(graphics, width, height, snapshot, state.viewedFaction)
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

    private fun drawEntities(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView, viewedFaction: Int?) {
        for (entity in snapshot.entities) {
            val px = camera.worldToScreenX(entity.x).toInt()
            val py = camera.worldToScreenY(entity.y).toInt()
            g.color =
                when {
                    entity.faction <= 0 -> neutralColor
                    viewedFaction == null -> if (entity.faction == 1) friendlyColor else enemyColor
                    entity.faction == viewedFaction -> friendlyColor
                    else -> enemyColor
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

    private fun drawPathMarkers(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView) {
        g.color = Color(0x79, 0xD7, 0xF2)
        g.stroke = BasicStroke(1.5f)
        for (entity in snapshot.entities) {
            if (entity.id !in selectedIds) continue
            val goalX = entity.pathGoalX ?: continue
            val goalY = entity.pathGoalY ?: continue
            if (entity.pathRemainingNodes <= 0) continue
            val startX = camera.worldToScreenX(entity.x).toInt()
            val startY = camera.worldToScreenY(entity.y).toInt()
            val endX = camera.worldToScreenX(goalX + 0.5f).toInt()
            val endY = camera.worldToScreenY(goalY + 0.5f).toInt()
            g.drawLine(startX, startY, endX, endY)
            g.drawRect(endX - 4, endY - 4, 8, 8)
        }
    }

    private fun drawTaskMarkers(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView) {
        val entitiesById = snapshot.entities.associateBy { it.id }
        val resourceNodesById = snapshot.resourceNodes.associateBy { it.id }
        g.stroke = BasicStroke(1.25f)
        for (entity in snapshot.entities) {
            if (entity.id !in selectedIds) continue
            val startX = camera.worldToScreenX(entity.x).toInt()
            val startY = camera.worldToScreenY(entity.y).toInt()
            entity.buildTargetId?.let { targetId ->
                val target = entitiesById[targetId] ?: return@let
                g.color = Color(0xF5, 0xCB, 0x5C)
                val endX = camera.worldToScreenX(target.x).toInt()
                val endY = camera.worldToScreenY(target.y).toInt()
                g.drawLine(startX, startY, endX, endY)
            }
            entity.harvestTargetNodeId?.let { targetId ->
                val target = resourceNodesById[targetId] ?: return@let
                g.color = Color(0x6E, 0xD3, 0x91)
                val endX = camera.worldToScreenX(target.x).toInt()
                val endY = camera.worldToScreenY(target.y).toInt()
                g.drawLine(startX, startY, endX, endY)
            }
            entity.harvestReturnTargetId?.let { targetId ->
                val target = entitiesById[targetId] ?: return@let
                g.color = Color(0xD3, 0x9A, 0x6E)
                val endX = camera.worldToScreenX(target.x).toInt()
                val endY = camera.worldToScreenY(target.y).toInt()
                g.drawLine(startX, startY, endX, endY)
            }
        }
    }

    private fun drawStatusMarkers(g: Graphics2D, selectedIds: Set<Int>, snapshot: ClientSnapshot, camera: CameraView) {
        for (entity in snapshot.entities) {
            if (entity.id !in selectedIds) continue
            val status = buildEntityStatusLabel(entity) ?: continue
            val px = camera.worldToScreenX(entity.x).toInt() + 10
            val py = camera.worldToScreenY(entity.y).toInt() - 14
            val boxWidth = (status.length * 7).coerceAtLeast(52)
            g.color = Color(0x10, 0x14, 0x19, 220)
            g.fillRect(px - 3, py - 10, boxWidth, 14)
            g.color = Color(0xF0, 0xF3, 0xF7)
            g.drawString(status, px, py)
        }
    }

    private fun drawFog(
        g: Graphics2D,
        snapshot: ClientSnapshot,
        visionState: ClientVisionState?,
        camera: CameraView,
        viewedFaction: Int?
    ) {
        val faction = viewedFaction ?: return
        val visibleTiles = visionState?.visibleTiles(faction)
        if (visibleTiles == null) return
        val tilePixels = (camera.baseTileSize * camera.zoom).toInt().coerceAtLeast(1)
        g.color = Color(0x05, 0x08, 0x0D, 170)
        for (y in 0 until snapshot.mapHeight) {
            for (x in 0 until snapshot.mapWidth) {
                if ((x to y) in visibleTiles) continue
                val left = camera.worldToScreenX(x.toFloat()).toInt()
                val top = camera.worldToScreenY(y.toFloat()).toInt()
                g.fillRect(left, top, tilePixels, tilePixels)
            }
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

    private fun drawCommandPanel(
        g: Graphics2D,
        width: Int,
        height: Int,
        state: ClientSessionState,
        overlayLines: List<String>
    ) {
        val panel = commandPanelBounds(width, height)
        g.color = Color(0x0F, 0x14, 0x1A, 210)
        g.fillRect(panel.x, panel.y, panel.width, panel.height)
        g.color = Color(0x42, 0x4F, 0x5A)
        g.drawRect(panel.x, panel.y, panel.width, panel.height)
        g.color = Color.WHITE
        g.drawString("Commands", panel.x + 12, panel.y + 20)
        val statusLines = buildCommandPanelStatusLines(overlayLines)
        for (i in statusLines.indices) {
            g.drawString(statusLines[i], panel.x + 12, panel.y + 38 + (i * 14))
        }
        val snapshot = state.snapshot
        val canTrain = snapshot != null && state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsTraining == true } }
        val canResearch = snapshot != null && state.selectedIds.any { id -> snapshot.entities.any { it.id == id && it.supportsResearch == true } }
        val buttons = buildCommandButtons(defaultClientCatalog(), state.selectedIds.isNotEmpty(), canTrain, canResearch)
        for (i in buttons.indices) {
            val bounds = commandButtonBounds(width, i, statusLines.size)
            g.color = Color(0x1B, 0x26, 0x31, 220)
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
            g.color = Color(0x61, 0x71, 0x80)
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
            g.color = Color.WHITE
            g.drawString(buttons[i].label, bounds.x + 10, bounds.y + 21)
        }
    }

    private fun drawMiniMap(
        g: Graphics2D,
        width: Int,
        height: Int,
        snapshot: ClientSnapshot,
        selectedIds: Set<Int>,
        camera: CameraView
    ) {
        val bounds = miniMapBounds(width, height)
        g.color = Color(0x0F, 0x14, 0x1A, 210)
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        g.color = Color(0x42, 0x4F, 0x5A)
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)

        for (node in snapshot.resourceNodes) {
            val point = miniMapPoint(bounds, snapshot, node.x, node.y)
            g.color = if (node.kind == "gas") Color(0x3A, 0xC4, 0x92) else neutralColor
            g.fillRect(point.x - 1, point.y - 1, 3, 3)
        }
        for (entity in snapshot.entities) {
            val point = miniMapPoint(bounds, snapshot, entity.x, entity.y)
            g.color =
                when (entity.faction) {
                    1 -> friendlyColor
                    2 -> enemyColor
                    else -> neutralColor
                }
            val size = if (entity.id in selectedIds) 4 else 3
            g.fillRect(point.x - (size / 2), point.y - (size / 2), size, size)
        }

        val viewport = miniMapViewport(bounds, snapshot, camera, width, height)
        g.color = selectionColor
        g.drawRect(viewport.x, viewport.y, viewport.width.coerceAtLeast(1), viewport.height.coerceAtLeast(1))
    }

    private fun drawGameStateOverlay(
        g: Graphics2D,
        width: Int,
        height: Int,
        snapshot: ClientSnapshot,
        viewedFaction: Int?
    ) {
        val gameState = buildGameState(snapshot, viewedFaction) ?: return
        val boxWidth = 260
        val boxHeight = 64
        val x = (width - boxWidth) / 2
        val y = 24
        g.color = Color(0x0A, 0x0E, 0x13, 220)
        g.fillRoundRect(x, y, boxWidth, boxHeight, 12, 12)
        g.color = if (gameState.title == "Victory") Color(0x7F, 0xE3, 0x7C) else Color(0xE3, 0x7C, 0x7C)
        g.drawRoundRect(x, y, boxWidth, boxHeight, 12, 12)
        g.color = Color.WHITE
        g.drawString(gameState.title, x + 12, y + 24)
        g.drawString(gameState.detail, x + 12, y + 44)
    }

    private fun drawStartOverlay(
        g: Graphics2D,
        snapshot: ClientSnapshot,
        overlayLines: List<String>
    ) {
        val lines = buildStartOverlayLines(snapshot.tick, overlayLines)
        if (lines.isEmpty()) return
        val x = 168
        val y = 16
        val width = 350
        val height = 18 + (lines.size * 14)
        g.color = Color(0x0A, 0x0E, 0x13, 205)
        g.fillRoundRect(x, y, width, height, 10, 10)
        g.color = Color(0x6C, 0x82, 0x96)
        g.drawRoundRect(x, y, width, height, 10, 10)
        g.color = Color(0xE4, 0xED, 0xF6)
        for (i in lines.indices) {
            g.drawString(lines[i], x + 10, y + 18 + (i * 14))
        }
    }
}

internal fun commandPanelBounds(width: Int, height: Int): Rectangle =
    Rectangle(width - 176, 12, 164, height - 24)

internal fun buildCommandPanelStatusLines(overlayLines: List<String>): List<String> =
    overlayLines.filter {
        it.startsWith("play:") ||
            it.startsWith("scenario:") ||
            it.startsWith("presets:") ||
            it.startsWith("notice:")
    }

internal fun buildStartOverlayLines(tick: Int, overlayLines: List<String>): List<String> {
    if (tick > 50) return emptyList()
    val status = buildCommandPanelStatusLines(overlayLines)
    return listOf("Match start: LMB select, RMB command, Tab scenario menu") + status
}

internal fun miniMapBounds(width: Int, height: Int): Rectangle =
    Rectangle(12, 12, minOf(144, width / 4), minOf(144, height / 4))

internal fun miniMapPoint(
    bounds: Rectangle,
    snapshot: ClientSnapshot,
    worldX: Float,
    worldY: Float
): Point {
    val x = bounds.x + ((worldX / snapshot.mapWidth.toFloat()) * bounds.width).toInt().coerceIn(0, bounds.width - 1)
    val y = bounds.y + ((worldY / snapshot.mapHeight.toFloat()) * bounds.height).toInt().coerceIn(0, bounds.height - 1)
    return Point(x, y)
}

internal fun miniMapViewport(
    bounds: Rectangle,
    snapshot: ClientSnapshot,
    camera: CameraView,
    screenWidth: Int,
    screenHeight: Int
): Rectangle {
    val left = camera.screenToWorldX(0f).coerceIn(0f, snapshot.mapWidth.toFloat())
    val top = camera.screenToWorldY(0f).coerceIn(0f, snapshot.mapHeight.toFloat())
    val right = camera.screenToWorldX(screenWidth.toFloat()).coerceIn(0f, snapshot.mapWidth.toFloat())
    val bottom = camera.screenToWorldY(screenHeight.toFloat()).coerceIn(0f, snapshot.mapHeight.toFloat())
    val topLeft = miniMapPoint(bounds, snapshot, left, top)
    val bottomRight = miniMapPoint(bounds, snapshot, right, bottom)
    return Rectangle(
        topLeft.x,
        topLeft.y,
        (bottomRight.x - topLeft.x).coerceAtLeast(1),
        (bottomRight.y - topLeft.y).coerceAtLeast(1)
    )
}

internal fun commandButtonBounds(width: Int, index: Int, statusLineCount: Int = 0): Rectangle {
    val panel = commandPanelBounds(width, 640)
    return Rectangle(panel.x + 10, panel.y + 28 + (statusLineCount * 14) + 8 + (index * 34), panel.width - 20, 26)
}

internal fun buildCommandButtons(hasSelection: Boolean): List<ClientCommandButton> {
    return buildCommandButtons(defaultClientCatalog(), hasSelection, canTrain = hasSelection, canResearch = hasSelection)
}

internal fun buildCommandButtons(
    catalog: ClientCatalog,
    hasSelection: Boolean,
    canTrain: Boolean,
    canResearch: Boolean
): List<ClientCommandButton> {
    val buttons =
        mutableListOf(
            ClientCommandButton("Move", "move"),
            ClientCommandButton("AttackMove", "attackMove"),
            ClientCommandButton("Patrol", "patrol"),
            ClientCommandButton("Hold", "hold"),
            ClientCommandButton("Cancel Build", "cancelBuild"),
            ClientCommandButton("Cancel Train", "cancelTrain"),
            ClientCommandButton("Cancel Research", "cancelResearch"),
            ClientCommandButton("Pause", "play:pause"),
            ClientCommandButton("Slower", "play:slower"),
            ClientCommandButton("Faster", "play:faster"),
            ClientCommandButton("Save Quick", "preset:save:quick"),
            ClientCommandButton("Load Quick", "preset:load:quick"),
            ClientCommandButton("Save Alt", "preset:save:alt"),
            ClientCommandButton("Load Alt", "preset:load:alt"),
            ClientCommandButton("Prev Scenario", "scenario:prev"),
            ClientCommandButton("Next Scenario", "scenario:next"),
            ClientCommandButton("Clear", "clear")
        )
    for ((index, option) in catalog.buildOptions.withIndex()) {
        buttons.add(7 + index, ClientCommandButton("Build ${option.label}", "build:${option.typeId}"))
    }
    if (canTrain) {
        for ((index, option) in catalog.trainOptions.withIndex()) {
            buttons.add(4 + index, ClientCommandButton("Train ${option.label}", "train:${option.typeId}"))
        }
    }
    if (canResearch) {
        val insertAt = 4 + if (canTrain) catalog.trainOptions.size else 0
        for ((index, option) in catalog.researchOptions.withIndex()) {
            buttons.add(insertAt + index, ClientCommandButton("Research ${option.label}", "research:${option.typeId}"))
        }
    }
    return if (hasSelection) {
        buttons
    } else {
        buttons.filter {
            it.actionId.startsWith("build:") ||
                it.actionId.startsWith("play:") ||
                it.actionId.startsWith("preset:") ||
                it.actionId.startsWith("scenario:") ||
                it.actionId == "clear"
        }
    }
}

internal fun commandButtonAt(
    width: Int,
    x: Int,
    y: Int,
    catalog: ClientCatalog,
    statusLineCount: Int = 0,
    hasSelection: Boolean,
    canTrain: Boolean = hasSelection,
    canResearch: Boolean = hasSelection
): ClientCommandButton? {
    val buttons = buildCommandButtons(catalog, hasSelection, canTrain, canResearch)
    for (i in buttons.indices) {
        if (commandButtonBounds(width, i, statusLineCount).contains(x, y)) return buttons[i]
    }
    return null
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
        buildTaskSummary(snapshot, state.selectedIds),
        buildPathSummary(snapshot, state.selectedIds),
        buildFogSummary(snapshot, state.visionState, state.viewedFaction),
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
        "keys: 1/2 faction 3 observer m/a/p/h u/i/o/l x/t/y [/] spc f5/f6/f7 f8/f9(+shift alt) f10 tab esc"
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

internal fun buildTaskSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "tasks: none"
    var builds = 0
    var gathers = 0
    var returns = 0
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        if (entity.buildTargetId != null) builds++
        when (entity.harvestPhase) {
            "gather" -> gathers++
            "return" -> returns++
        }
    }
    if (builds == 0 && gathers == 0 && returns == 0) return "tasks: none"
    return "tasks: build=$builds gather=$gathers return=$returns"
}

internal fun buildPathSummary(
    snapshot: ClientSnapshot,
    selectedIds: Set<Int>
): String {
    if (selectedIds.isEmpty()) return "paths: none"
    var active = 0
    var remaining = 0
    val goals = LinkedHashMap<String, Int>()
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        if (entity.pathRemainingNodes <= 0) continue
        active++
        remaining += entity.pathRemainingNodes
        val gx = entity.pathGoalX ?: continue
        val gy = entity.pathGoalY ?: continue
        val key = "$gx,$gy"
        goals[key] = (goals[key] ?: 0) + 1
    }
    if (active == 0) return "paths: none"
    return "paths: active=$active remaining=$remaining goals=" +
        goals.entries.joinToString(" ") { "${it.key}x${it.value}" }
}

internal fun buildFogSummary(
    snapshot: ClientSnapshot,
    visionState: ClientVisionState?,
    viewedFaction: Int?
): String {
    val faction = viewedFaction ?: return "fog: observer"
    val visibleTiles = visionState?.visibleTiles(faction) ?: return "fog: unavailable"
    val totalTiles = snapshot.mapWidth * snapshot.mapHeight
    return "fog: f$faction visible=${visibleTiles.size} hidden=${(totalTiles - visibleTiles.size).coerceAtLeast(0)}"
}

internal fun buildPreviewLabel(spec: BuildPreviewSpec?, valid: Boolean): BuildPreviewLabel? {
    if (spec == null) return null
    return BuildPreviewLabel(
        title = spec.typeId,
        cost = "cost=${spec.mineralCost}/${spec.gasCost}",
        size = "size=${spec.width}x${spec.height} clr=${spec.clearance}",
        valid = valid
    )
}

internal fun buildEntityStatusLabel(entity: EntitySnapshot): String? =
    when {
        entity.underConstruction -> "build ${entity.constructionRemainingTicks ?: 0}"
        entity.activeProductionType != null -> "train ${entity.activeProductionType} ${entity.activeProductionRemainingTicks}"
        entity.activeResearchTech != null -> "research ${entity.activeResearchTech} ${entity.activeResearchRemainingTicks}"
        else -> null
    }

internal fun buildGameState(snapshot: ClientSnapshot, viewedFaction: Int?): ClientGameState? {
    var friendlyAlive = 0
    var enemyAlive = 0
    for (entity in snapshot.entities) {
        if (entity.hp <= 0) continue
        when (entity.faction) {
            1 -> friendlyAlive++
            2 -> enemyAlive++
        }
    }
    if (viewedFaction == null) return null
    return when {
        viewedFaction == 1 && friendlyAlive > 0 && enemyAlive == 0 -> ClientGameState("Victory", "Enemy faction eliminated")
        viewedFaction == 1 && friendlyAlive == 0 && enemyAlive > 0 -> ClientGameState("Defeat", "Your faction has been eliminated")
        viewedFaction == 2 && enemyAlive > 0 && friendlyAlive == 0 -> ClientGameState("Victory", "Enemy faction eliminated")
        viewedFaction == 2 && enemyAlive == 0 && friendlyAlive > 0 -> ClientGameState("Defeat", "Your faction has been eliminated")
        else -> null
    }
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
