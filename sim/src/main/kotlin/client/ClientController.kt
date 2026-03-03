package starkraft.sim.client

import starkraft.sim.net.InputJson
import java.util.LinkedHashSet
import kotlin.math.abs
import kotlin.math.hypot

internal sealed interface ClientIntent {
    data class Selection(val record: InputJson.InputSelectionRecord) : ClientIntent
    data class Command(val record: InputJson.InputCommandRecord) : ClientIntent
}

internal class ClientCommandIds(prefix: String = "gc") {
    private val base = prefix
    private var next = 1L

    fun nextRequestId(): String = "$base-${next++}"
}

internal fun buildClientIntent(
    snapshot: ClientSnapshot,
    selectedIds: LinkedHashSet<Int>,
    worldX: Float,
    worldY: Float,
    leftClick: Boolean,
    rightClick: Boolean,
    attackMoveModifier: Boolean,
    additiveSelection: Boolean,
    requestIds: ClientCommandIds
): ClientIntent? {
    if (leftClick) {
        val selected = nearestEntity(snapshot, worldX, worldY) { it.faction == 1 }
        applySelectionClick(selectedIds, selected?.id, additive = additiveSelection)
        return ClientIntent.Selection(buildUnitSelectionRecord(snapshot.tick + 1, selectedIds))
    }
    if (!rightClick || selectedIds.isEmpty()) return null

    val enemy = nearestEntity(snapshot, worldX, worldY) { it.faction == 2 && distance(it.x, it.y, worldX, worldY) <= 0.8f }
    if (enemy != null) {
        return ClientIntent.Command(
            InputJson.InputCommandRecord(
                tick = snapshot.tick + 1,
                commandType = "attack",
                requestId = requestIds.nextRequestId(),
                units = selectedIds.toIntArray(),
                target = enemy.id
            )
        )
    }

    val node = nearestResourceNode(snapshot, worldX, worldY)
    if (node != null && distance(node.x, node.y, worldX, worldY) <= 0.8f) {
        return ClientIntent.Command(
            InputJson.InputCommandRecord(
                tick = snapshot.tick + 1,
                commandType = "harvest",
                requestId = requestIds.nextRequestId(),
                units = selectedIds.toIntArray(),
                target = node.id
            )
        )
    }

    return ClientIntent.Command(
        InputJson.InputCommandRecord(
            tick = snapshot.tick + 1,
            commandType = if (attackMoveModifier) "attackMove" else "move",
            requestId = requestIds.nextRequestId(),
            units = selectedIds.toIntArray(),
            x = worldX,
            y = worldY
        )
    )
}

internal fun nearestEntity(
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

internal fun nearestResourceNode(snapshot: ClientSnapshot, x: Float, y: Float): ResourceNodeSnapshot? {
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

internal fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(abs(ax - bx), abs(ay - by))
