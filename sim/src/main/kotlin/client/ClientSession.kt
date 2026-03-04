package starkraft.sim.client

import java.io.Closeable
import java.nio.file.Path
import java.util.LinkedHashSet

internal data class ClientSessionState(
    var snapshot: ClientSnapshot? = null,
    val selectedIds: LinkedHashSet<Int> = LinkedHashSet(),
    var lastAck: ClientCommandAck? = null,
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

        val latestAck = update.ack
        if (latestAck != null && latestAck != state.lastAck) {
            state.lastAck = latestAck
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
