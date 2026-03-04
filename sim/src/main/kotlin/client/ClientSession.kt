package starkraft.sim.client

import java.io.Closeable
import java.nio.file.Path
import java.util.LinkedHashSet

internal data class ClientSessionState(
    var snapshot: ClientSnapshot? = null,
    var mapState: ClientMapState? = null,
    var visionState: ClientVisionState? = null,
    val selectedIds: LinkedHashSet<Int> = LinkedHashSet(),
    var lastAck: ClientCommandAck? = null,
    var lastConstructionActivity: ClientConstructionActivity? = null,
    var lastProductionActivity: ClientProductionActivity? = null,
    var lastResearchActivity: ClientResearchActivity? = null,
    var lastTickActivity: ClientTickActivity? = null
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
