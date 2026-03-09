package starkraft.sim.client

import java.io.Closeable
import java.nio.file.Path
import java.util.LinkedHashSet

internal data class ClientSessionState(
    var snapshot: ClientSnapshot? = null,
    var mapState: ClientMapState? = null,
    var visionState: ClientVisionState? = null,
    var viewedFaction: Int? = 1,
    val selectedIds: LinkedHashSet<Int> = LinkedHashSet(),
    var viewState: ClientViewState = ClientViewState(),
    var lastAck: ClientCommandAck? = null,
    var lastConstructionActivity: ClientConstructionActivity? = null,
    var lastProductionActivity: ClientProductionActivity? = null,
    var lastResearchActivity: ClientResearchActivity? = null,
    var lastTickActivity: ClientTickActivity? = null
)

internal data class ClientViewState(
    val hasSelection: Boolean = false,
    val canTrain: Boolean = false,
    val canResearch: Boolean = false,
    val selectionHudLine: String? = null
)

internal class ClientSession(
    private val subscription: ClientStreamSubscription,
    private val inputSink: ClientInputSink,
    val state: ClientSessionState = ClientSessionState()
) : Closeable {
    constructor(
        snapshotPath: Path,
        inputPath: Path,
        state: ClientSessionState = ClientSessionState()
    ) : this(
        subscription = FileClientStreamSubscription(snapshotPath),
        inputSink = FileClientInputSink(inputPath),
        state = state
    )

    fun poll(): Boolean {
        val update = subscription.poll() ?: return false

        var changed = false
        val latestSnapshot = update.snapshot
        if (latestSnapshot != null && state.snapshot?.tick != latestSnapshot.tick) {
            state.snapshot = latestSnapshot
            syncSelection(latestSnapshot)
            changed = true
        }

        val latestMapState = update.mapState
        if (latestMapState != null && latestMapState != state.mapState) {
            state.mapState = latestMapState
            changed = true
        }

        if (update.visionChanges.isNotEmpty()) {
            val nextVisionState = applyVisionChanges(state.visionState, update.visionChanges)
            if (nextVisionState != state.visionState) {
                state.visionState = nextVisionState
                changed = true
            }
        }

        val latestAck = update.ack
        if (latestAck != null && latestAck != state.lastAck) {
            state.lastAck = latestAck
            changed = true
        }

        val latestConstructionActivity = update.constructionActivity
        if (latestConstructionActivity != null && latestConstructionActivity != state.lastConstructionActivity) {
            state.lastConstructionActivity = latestConstructionActivity
            changed = true
        }

        val latestProductionActivity = update.productionActivity
        if (latestProductionActivity != null && latestProductionActivity != state.lastProductionActivity) {
            state.lastProductionActivity = latestProductionActivity
            changed = true
        }

        val latestResearchActivity = update.researchActivity
        if (latestResearchActivity != null && latestResearchActivity != state.lastResearchActivity) {
            state.lastResearchActivity = latestResearchActivity
            changed = true
        }

        val latestTickActivity = update.tickActivity
        if (latestTickActivity != null && latestTickActivity != state.lastTickActivity) {
            state.lastTickActivity = latestTickActivity
            changed = true
        }

        return changed
    }

    fun clearSelection() {
        if (state.selectedIds.isEmpty()) return
        state.selectedIds.clear()
        rebuildViewState()
    }

    fun replaceSelection(ids: IntArray) {
        state.selectedIds.clear()
        for (i in ids.indices) {
            state.selectedIds.add(ids[i])
        }
        rebuildViewState()
    }

    fun refreshViewState() {
        rebuildViewState()
    }

    fun append(intent: ClientIntent) {
        when (intent) {
            is ClientIntent.Selection -> inputSink.append(intent.record)
            is ClientIntent.Command -> inputSink.append(intent.record)
        }
    }

    override fun close() {
        subscription.close()
        inputSink.close()
    }

    private fun syncSelection(snapshot: ClientSnapshot) {
        val liveIds = HashSet<Int>(snapshot.entities.size)
        for (entity in snapshot.entities) {
            liveIds.add(entity.id)
        }
        state.selectedIds.retainAll(liveIds)
        rebuildViewState()
    }

    private fun rebuildViewState() {
        state.viewState = deriveClientViewState(state.snapshot, state.selectedIds)
    }
}

internal data class ClientVisionState(
    val visibleTilesByFaction: Map<Int, Set<Pair<Int, Int>>> = emptyMap()
) {
    fun visibleTiles(faction: Int): Set<Pair<Int, Int>> = visibleTilesByFaction[faction] ?: emptySet()
}

internal fun applyVisionChanges(
    current: ClientVisionState?,
    changes: List<ClientVisionChange>
): ClientVisionState {
    val next = LinkedHashMap<Int, LinkedHashSet<Pair<Int, Int>>>()
    current?.visibleTilesByFaction?.forEach { (faction, tiles) ->
        next[faction] = LinkedHashSet(tiles)
    }
    for (change in changes) {
        val visibleTiles = next.getOrPut(change.faction) { LinkedHashSet() }
        val tile = change.x to change.y
        if (change.visible) {
            visibleTiles.add(tile)
        } else {
            visibleTiles.remove(tile)
        }
    }
    val normalized = LinkedHashMap<Int, Set<Pair<Int, Int>>>(next.size)
    for ((faction, tiles) in next) {
        normalized[faction] = tiles
    }
    return ClientVisionState(visibleTilesByFaction = normalized)
}

internal fun deriveClientViewState(
    snapshot: ClientSnapshot?,
    selectedIds: Set<Int>
): ClientViewState {
    if (snapshot == null || selectedIds.isEmpty()) return ClientViewState()
    val counts = LinkedHashMap<String, Int>()
    var canTrain = false
    var canResearch = false
    for (entity in snapshot.entities) {
        if (entity.id !in selectedIds) continue
        counts[entity.typeId] = (counts[entity.typeId] ?: 0) + 1
        if (entity.supportsTraining == true) canTrain = true
        if (entity.supportsResearch == true) canResearch = true
    }
    if (counts.isEmpty()) return ClientViewState()
    val summary = counts.entries.joinToString(" ") { "${it.key}x${it.value}" }
    return ClientViewState(
        hasSelection = true,
        canTrain = canTrain,
        canResearch = canResearch,
        selectionHudLine = "selection hud: $summary"
    )
}
