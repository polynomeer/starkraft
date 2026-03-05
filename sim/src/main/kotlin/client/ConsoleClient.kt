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
    val viewSummary = buildViewedFactionSummary(state.viewedFaction)
    val economySummary = buildEconomySummary(snapshot, state.viewedFaction)
    val selectionFactionSummary = buildSelectionFactionSummary(snapshot, state.selectedIds)
    val selectionHudSummary = buildSelectionSummary(snapshot, state.selectedIds).replaceFirst("selection:", "selection hud:")
    val selectionRoleSummary = buildSelectionArchetypeSummary(snapshot, state.selectedIds)
    val selectionPositionSummary = buildSelectionPositionSummary(snapshot, state.selectedIds)
    val selectionVisionSummary = buildSelectionVisionSummary(snapshot, state.selectedIds)
    val selectionDurabilitySummary = buildSelectionDurabilitySummary(snapshot, state.selectedIds)
    val selectionCargoSummary = buildSelectionCargoSummary(snapshot, state.selectedIds)
    val selectionMobilitySummary = buildSelectionMobilitySummary(snapshot, state.selectedIds)
    val selectionWeaponSummary = buildSelectionWeaponSummary(snapshot, state.selectedIds)
    val selectionOrderSummary = buildSelectionOrderSummary(snapshot, state.selectedIds)
    val selectionTargetSummary = buildSelectionTargetSummary(snapshot, state.selectedIds)
    val selectionRallySummary = buildSelectionRallySummary(snapshot, state.selectedIds)
    val selectionStructureSummary = buildSelectionStructureSummary(snapshot, state.selectedIds)
    val selectionCombatSummary = buildSelectionCombatSummary(snapshot, state.selectedIds)
    val selectionCapabilitySummary = buildSelectionCapabilitySummary(snapshot, state.selectedIds)
    val selectionQueueSummary = buildSelectionQueueSummary(snapshot, state.selectedIds)
    val commandAffordanceSummary = buildCommandAffordanceSummary(snapshot, state.selectedIds, state.viewedFaction)
    val builderSummary = buildBuilderSummary(snapshot, state.selectedIds)
    val constructionSummary = buildConstructionSummary(snapshot, state.selectedIds)
    val productionSummary = buildProductionSummary(snapshot, state.selectedIds)
    val researchSummary = buildResearchSummary(snapshot, state.selectedIds)
    val techSummary = buildTechSummary(snapshot)
    val tickActivity = formatTickActivity(state.lastTickActivity)
    val constructionActivity = formatConstructionActivity(state.lastConstructionActivity)
    val productionActivity = formatProductionActivity(state.lastProductionActivity)
    val researchActivity = formatResearchActivity(state.lastResearchActivity)
    return buildString {
        append("tick=")
        append(snapshot.tick)
        append(" view=")
        append(viewSummary)
        append(" selected=")
        append(state.selectedIds.size)
        append(" entities=")
        append(snapshot.entities.size)
        append(" resources=")
        append(snapshot.resourceNodes.size)
        append(" visible[")
        append(factionSummary)
        append("] ")
        append(economySummary)
        append(" ")
        append(selectionFactionSummary)
        append(" ")
        append(selectionHudSummary)
        append(" ")
        append(selectionRoleSummary)
        append(" ")
        append(selectionPositionSummary)
        append(" ")
        append(selectionVisionSummary)
        append(" ")
        append(selectionDurabilitySummary)
        append(" ")
        append(selectionCargoSummary)
        append(" ")
        append(selectionMobilitySummary)
        append(" ")
        append(selectionWeaponSummary)
        append(" ")
        append(selectionOrderSummary)
        append(" ")
        append(selectionTargetSummary)
        append(" ")
        append(selectionRallySummary)
        append(" ")
        append(selectionStructureSummary)
        append(" ")
        append(selectionCombatSummary)
        append(" ")
        append(selectionCapabilitySummary)
        append(" ")
        append(selectionQueueSummary)
        append(" ")
        append(commandAffordanceSummary)
        append(" ")
        append(builderSummary)
        append(" ")
        append(constructionSummary)
        append(" ")
        append(productionSummary)
        append(" ")
        append(researchSummary)
        append(" ")
        append(techSummary)
        append(" ")
        append(tickActivity)
        append(" ")
        append(constructionActivity)
        append(" ")
        append(productionActivity)
        append(" ")
        append(researchActivity)
        append(" ")
        append(formatAckStatus(state.lastAck))
    }
}

private fun buildViewedFactionSummary(viewedFaction: Int?): String =
    if (viewedFaction == null) {
        "observer"
    } else {
        "f$viewedFaction"
    }
