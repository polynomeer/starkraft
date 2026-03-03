package starkraft.sim.client

import java.nio.file.Paths

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: ConsoleClientKt <input.ndjson> [snapshot.ndjson|-]" }
    val inputPath = Paths.get(args[0]).toAbsolutePath().normalize()
    val subscription =
        if (args.size >= 2 && args[1] != "-") {
            FileClientStreamSubscription(Paths.get(args[1]).toAbsolutePath().normalize())
        } else {
            StdinClientStreamSubscription()
        }

    ClientSession(subscription, NdjsonClientInputSink(inputPath)).use { session ->
        while (session.poll()) {
            println(renderClientTextFrame(session.state))
        }
    }
}

internal fun renderClientTextFrame(state: ClientSessionState): String {
    val snapshot = state.snapshot ?: return "waiting for snapshots..."
    val factionSummary =
        snapshot.factions.joinToString(" ") { faction ->
            "f${faction.faction}=${faction.visibleTiles}"
        }
    return buildString {
        append("tick=")
        append(snapshot.tick)
        append(" selected=")
        append(state.selectedIds.size)
        append(" entities=")
        append(snapshot.entities.size)
        append(" resources=")
        append(snapshot.resourceNodes.size)
        append(" visible[")
        append(factionSummary)
        append("] ")
        append(formatAckStatus(state.lastAck))
    }
}
