package starkraft.sim.client

import java.io.Closeable
import java.nio.file.Path
import java.util.LinkedHashSet

internal data class ClientSessionState(
    var snapshot: ClientSnapshot? = null,
    val selectedIds: LinkedHashSet<Int> = LinkedHashSet(),
    var lastAck: ClientCommandAck? = null
)

internal class ClientSession(
    snapshotPath: Path,
    inputPath: Path,
    val state: ClientSessionState = ClientSessionState()
) : Closeable {
    private val tail = SnapshotTailReader(snapshotPath)
    private val inputSink = NdjsonClientInputSink(inputPath)

    fun poll(): Boolean {
        tail.poll()

        var changed = false
        val latestSnapshot = tail.latestSnapshot
        if (latestSnapshot != null && state.snapshot?.tick != latestSnapshot.tick) {
            state.snapshot = latestSnapshot
            syncSelection(latestSnapshot)
            changed = true
        }

        val latestAck = tail.latestAck
        if (latestAck != null && latestAck != state.lastAck) {
            state.lastAck = latestAck
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
        tail.close()
    }

    private fun syncSelection(snapshot: ClientSnapshot) {
        val liveIds = HashSet<Int>(snapshot.entities.size)
        for (entity in snapshot.entities) {
            liveIds.add(entity.id)
        }
        state.selectedIds.retainAll(liveIds)
    }
}
