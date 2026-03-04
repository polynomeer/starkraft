package starkraft.sim.client

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: ConsoleClientKt <input.ndjson|tcp://host:port> [snapshot.ndjson|tcp://host:port|-]" }
    val inputSpec = args[0]
    val subscription =
        if (args.size >= 2 && args[1] != "-") {
            openClientStreamSubscription(args[1])
        } else {
            StdinClientStreamSubscription()
        }

    ClientSession(subscription, openClientInputSink(inputSpec)).use { session ->
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
    val researchSummary = buildResearchSummary(snapshot, state.selectedIds)
    val tickActivity = formatTickActivity(state.lastTickActivity)
    val researchActivity = formatResearchActivity(state.lastResearchActivity)
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
        append(researchSummary)
        append(" ")
        append(tickActivity)
        append(" ")
        append(researchActivity)
        append(" ")
        append(formatAckStatus(state.lastAck))
    }
}
