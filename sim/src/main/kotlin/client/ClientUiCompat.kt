package starkraft.sim.client

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class VisibleTileRange(
    val startX: Int,
    val endXExclusive: Int,
    val startY: Int,
    val endYExclusive: Int
) {
    fun contains(x: Int, y: Int): Boolean =
        x in startX until endXExclusive && y in startY until endYExclusive

    fun tileCount(): Int = (endXExclusive - startX).coerceAtLeast(0) * (endYExclusive - startY).coerceAtLeast(0)
}

internal fun computeVisibleTileRange(
    camera: CameraView,
    screenWidth: Int,
    screenHeight: Int,
    mapWidth: Int,
    mapHeight: Int,
    paddingTiles: Int = 2
): VisibleTileRange {
    val startX = (floor(camera.screenToWorldX(0f)).toInt() - paddingTiles).coerceIn(0, mapWidth)
    val startY = (floor(camera.screenToWorldY(0f)).toInt() - paddingTiles).coerceIn(0, mapHeight)
    val endXExclusive = (floor(camera.screenToWorldX(screenWidth.toFloat())).toInt() + paddingTiles + 2).coerceIn(startX, mapWidth)
    val endYExclusive = (floor(camera.screenToWorldY(screenHeight.toFloat())).toInt() + paddingTiles + 2).coerceIn(startY, mapHeight)
    return VisibleTileRange(startX, endXExclusive, startY, endYExclusive)
}

internal fun shouldRenderDetailedTerrainForTileSize(tileSize: Float): Boolean = tileSize >= 24f

internal fun shouldRenderTerrainSprites(tileSize: Float, visibleRange: VisibleTileRange): Boolean =
    tileSize >= 18f && visibleRange.tileCount() <= 900

internal fun resolveMiniMapSampleStep(mapWidth: Int, mapHeight: Int): Int =
    when {
        maxOf(mapWidth, mapHeight) >= 128 -> 6
        maxOf(mapWidth, mapHeight) >= 96 -> 4
        maxOf(mapWidth, mapHeight) >= 64 -> 3
        else -> 2
    }

internal fun computeCommandHintRowHeight(screenWidth: Int): Int =
    if (screenWidth < 1360) 20 else 22

internal fun formatFactionOverviewLine(factions: List<FactionSnapshot>, viewedFaction: Int?): String {
    if (factions.isEmpty()) return "No factions"
    return factions.joinToString("  |  ") { faction ->
        buildString {
            append("F")
            append(faction.faction)
            if (viewedFaction == faction.faction) append("*")
            append(" ")
            append(faction.minerals)
            append("/")
            append(faction.gas)
            append(" v")
            append(faction.visibleTiles)
        }
    }
}

internal fun formatCommandHeaderLine(
    overlayModeLabel: String,
    productionCount: Int,
    productionPage: Int,
    productionPageSize: Int = 6
): String {
    val pageCount = ((productionCount + productionPageSize - 1) / productionPageSize).coerceAtLeast(1)
    val normalizedPage = productionPage.coerceIn(0, pageCount - 1)
    val base = "Orders · ${overlayModeLabel.uppercase()}"
    return if (productionCount > productionPageSize) "$base · ${normalizedPage + 1}/$pageCount" else base
}

internal fun formatCommandButtonText(actionId: String, fallbackLabel: String): String =
    when (actionId) {
        "attackMove" -> "Attack"
        "centerSelection" -> "Center"
        "viewF1" -> "F1 View"
        "viewF2" -> "F2 View"
        "observer" -> "Observer"
        "pause" -> "Pause"
        "help" -> "Help"
        "debug" -> "Debug"
        "build:Depot" -> "Depot"
        "build:ResourceDepot" -> "Expand"
        "build:GasDepot" -> "Refine"
        "cancelBuild" -> "Cancel B"
        "cancelTrain" -> "Cancel T"
        "cancelResearch" -> "Cancel R"
        "patrol" -> "Patrol"
        "move" -> "Move"
        "hold" -> "Hold"
        "clear" -> "Clear"
        else -> fallbackLabel
    }

internal fun compactCommandHint(raw: String): String {
    val cleaned =
        raw
            .removePrefix("Switch to ")
            .removeSuffix(" view")
            .replace("faction ", "f")
            .replace("selection", "sel")
            .replace("attack", "atk")
            .replace("command", "cmd")
            .replace("current ", "")
            .replace("camera ", "")
            .replace("scenario ", "")
            .replace("preset", "pst")
            .replace("production", "prod")
            .replace("research", "tech")
            .replace("resource", "res")
            .replace("toggle ", "")
            .trim()
    return if (cleaned.length <= 16) cleaned else cleaned.take(13).trimEnd() + "..."
}

internal fun resolveCommandButtonHotkey(actionId: String): String? =
    when (actionId) {
        "move" -> "M"
        "attackMove" -> "A"
        "patrol" -> "P"
        "hold" -> "H"
        "clear" -> "Esc"
        "centerSelection" -> "Hm"
        "viewF1" -> "1"
        "viewF2" -> "2"
        "observer" -> "3"
        "pause" -> "Sp"
        "help" -> "F1"
        "debug" -> "Tb"
        "selectViewedFaction" -> "F2"
        "selectType" -> "F3"
        "selectRole" -> "F4"
        "selectAll" -> "F11"
        "selectIdleWorkers" -> "F12"
        "build:Depot" -> "B"
        "build:ResourceDepot" -> "R"
        "build:GasDepot" -> "G"
        "rally" -> "S"
        else -> null
    }

internal fun buildHudFooterLine(hasSelection: Boolean, commandArmed: Boolean): String =
    when {
        commandArmed -> "LMB/RMB confirm  Shift queue  Esc cancel"
        hasSelection -> "LMB reselect  RMB order  Shift queue  drag box add"
        else -> "LMB select  RMB move  drag box select"
    }

internal fun buildMinimapHintLine(commandArmed: Boolean): String =
    if (commandArmed) "armed: minimap/world click confirm + shift queue" else "minimap drag camera"

internal fun formatSelectionPageLabel(selectedCount: Int, pageIndex: Int, pageCount: Int): String =
    if (selectedCount <= 0) "Pg 0/0 · 0" else "Pg ${pageIndex + 1}/$pageCount · $selectedCount"

internal fun formatControlGroupDeckLine(groups: List<Pair<Int, Int>>): String =
    if (groups.isEmpty()) "Groups idle" else groups.take(3).joinToString("  ") { (group, count) -> "$group:$count" }

internal fun resolveQueueHeaderLine(hasProduction: Boolean, hasResearch: Boolean, underConstruction: Boolean): String =
    when {
        hasProduction && hasResearch -> "PIPELINE"
        hasResearch -> "RESEARCH"
        hasProduction -> "PRODUCTION"
        underConstruction -> "CONSTRUCT"
        else -> "QUEUE"
    }

internal fun formatQueueStatusLine(
    productionType: String?,
    productionQueueSize: Int,
    productionRemainingTicks: Int,
    researchTech: String?,
    researchQueueSize: Int,
    researchRemainingTicks: Int,
    underConstruction: Boolean,
    constructionRemainingTicks: Int?
): String {
    val parts = buildList {
        if (productionQueueSize > 0 || productionType != null) {
            add(buildString {
                append("Train ")
                append(productionType ?: "queue")
                if (productionQueueSize > 0) append(" x$productionQueueSize")
                if (productionRemainingTicks > 0) append(" · ${productionRemainingTicks}t")
            })
        }
        if (researchQueueSize > 0 || researchTech != null) {
            add(buildString {
                append("Tech ")
                append(researchTech ?: "queue")
                if (researchQueueSize > 0) append(" x$researchQueueSize")
                if (researchRemainingTicks > 0) append(" · ${researchRemainingTicks}t")
            })
        }
        if (underConstruction) {
            add(buildString {
                append("Build")
                constructionRemainingTicks?.takeIf { it > 0 }?.let { append(" · ${it}t") }
            })
        }
    }
    return if (parts.isEmpty()) "Idle" else parts.joinToString("  |  ")
}

internal fun smoothMetricSample(previous: Float, sample: Float): Float =
    if (previous <= 0f) sample else (previous * 0.82f) + (sample * 0.18f)

internal fun formatPerfMillis(value: Float): String {
    val roundedInt = value.roundToInt().toFloat()
    return if (abs(value - roundedInt) <= 0.05f) {
        roundedInt.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}

internal fun shouldRenderCompletionOverlay(damageActive: Boolean, completionActive: Boolean): Boolean =
    completionActive && !damageActive

internal fun resolveFallbackTeamTintStrength(typeId: String, isStructure: Boolean): Float =
    when {
        isStructure -> 0.08f
        typeId.contains("Worker", ignoreCase = true) -> 0.06f
        typeId.contains("Marine", ignoreCase = true) -> 0.05f
        typeId.contains("Zergling", ignoreCase = true) -> 0.04f
        else -> 0.05f
    }

internal fun resolveEffectAccentColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(1.00f, 0.74f, 0.34f, 1f), 0.54f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.96f, 0.54f, 0.28f, 1f), 0.62f)
        typeId.contains("Marine", ignoreCase = true) -> factionColor.cpy().lerp(Color(1.00f, 0.70f, 0.36f, 1f), 0.42f)
        else -> factionColor.cpy().lerp(Color(0.92f, 0.66f, 0.34f, 1f), 0.46f)
    }

internal fun resolveEffectDebrisColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(0.46f, 0.42f, 0.36f, 1f), 0.72f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.42f, 0.28f, 0.22f, 1f), 0.74f)
        typeId.contains("Marine", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.38f, 0.40f, 0.42f, 1f), 0.68f)
        else -> factionColor.cpy().lerp(Color(0.34f, 0.34f, 0.34f, 1f), 0.70f)
    }

internal fun resolveEffectSmokeColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(0.16f, 0.18f, 0.18f, 1f), 0.86f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.22f, 0.18f, 0.16f, 1f), 0.84f)
        else -> factionColor.cpy().lerp(Color(0.18f, 0.20f, 0.22f, 1f), 0.84f)
    }

internal fun resolveResourceCoreColor(kind: String): Color =
    if (kind.equals("gas", ignoreCase = true)) Color(0.26f, 0.82f, 0.60f, 1f) else Color(0.86f, 0.74f, 0.34f, 1f)

internal fun resolveResourceGlowColor(kind: String): Color =
    if (kind.equals("gas", ignoreCase = true)) Color(0.58f, 1.00f, 0.84f, 1f) else Color(1.00f, 0.95f, 0.72f, 1f)

internal fun resolveSilhouetteBodyColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(0.18f, 0.21f, 0.24f, 1f), 0.78f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.24f, 0.18f, 0.16f, 1f), 0.72f)
        typeId.contains("Worker", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.16f, 0.22f, 0.24f, 1f), 0.70f)
        typeId.contains("Marine", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.18f, 0.20f, 0.24f, 1f), 0.74f)
        else -> factionColor.cpy().lerp(Color(0.17f, 0.19f, 0.22f, 1f), 0.74f)
    }

internal fun resolveSilhouetteStripeColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(0.84f, 0.88f, 0.92f, 1f), 0.14f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.78f, 0.96f, 0.72f, 1f), 0.12f)
        typeId.contains("Worker", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.78f, 0.92f, 1.00f, 1f), 0.10f)
        typeId.contains("Marine", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.90f, 0.96f, 1.00f, 1f), 0.10f)
        else -> factionColor.cpy().lerp(Color.WHITE, 0.08f)
    }

internal fun resolveSilhouetteTrimColor(typeId: String, isStructure: Boolean, factionColor: Color): Color =
    when {
        isStructure -> factionColor.cpy().lerp(Color(0.90f, 0.94f, 0.98f, 1f), 0.30f)
        typeId.contains("Zergling", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.94f, 0.90f, 0.74f, 1f), 0.24f)
        typeId.contains("Worker", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.90f, 0.98f, 1.00f, 1f), 0.22f)
        typeId.contains("Marine", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.94f, 0.97f, 1.00f, 1f), 0.26f)
        else -> factionColor.cpy().lerp(Color.WHITE, 0.30f)
    }

internal fun resolveStructureRoofColor(typeId: String, factionColor: Color): Color =
    when {
        typeId.contains("GasDepot", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.22f, 0.30f, 0.28f, 1f), 0.78f)
        typeId.contains("ResourceDepot", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.28f, 0.26f, 0.22f, 1f), 0.78f)
        typeId.contains("Depot", ignoreCase = true) -> factionColor.cpy().lerp(Color(0.26f, 0.28f, 0.32f, 1f), 0.78f)
        else -> factionColor.cpy().lerp(Color(0.24f, 0.27f, 0.30f, 1f), 0.78f)
    }
