package starkraft.sim.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val consumerJson = Json { ignoreUnknownKeys = true }

data class SnapshotStreamSummary(
    val totalRecords: Int,
    val countsByType: Map<String, Int>,
    val mapId: String? = null,
    val buildVersion: String? = null,
    val seed: Long? = null,
    val worldHash: Long? = null,
    val replayHash: Long? = null,
    val resourceNodeChangeCount: Int = 0,
    val resourceNodeHarvestedTotal: Int = 0,
    val resourceNodeDepletedCount: Int = 0,
    val resourceNodeHarvestedMineralsFaction1: Int = 0,
    val resourceNodeHarvestedMineralsFaction2: Int = 0,
    val resourceNodeHarvestedGasFaction1: Int = 0,
    val resourceNodeHarvestedGasFaction2: Int = 0,
    val currentResourceNodeCount: Int = 0,
    val currentResourceNodeRemainingTotal: Int = 0,
    val resourceDeltaEventCount: Int = 0,
    val resourceSpendMinerals: Int = 0,
    val resourceSpendGas: Int = 0,
    val resourceRefundMinerals: Int = 0,
    val resourceRefundGas: Int = 0,
    val resourceSummaryMineralsSpentFaction1: Int = 0,
    val resourceSummaryMineralsSpentFaction2: Int = 0,
    val resourceSummaryGasSpentFaction1: Int = 0,
    val resourceSummaryGasSpentFaction2: Int = 0,
    val resourceSummaryMineralsRefundedFaction1: Int = 0,
    val resourceSummaryMineralsRefundedFaction2: Int = 0,
    val resourceSummaryGasRefundedFaction1: Int = 0,
    val resourceSummaryGasRefundedFaction2: Int = 0,
    val producerCount: Int = 0,
    val trainingProducerCount: Int = 0,
    val rallyProducerCount: Int = 0,
    val dropoffProducerCount: Int = 0,
    val mineralDropoffProducerCount: Int = 0,
    val gasDropoffProducerCount: Int = 0,
    val dropoffStateCount: Int = 0,
    val dropoffStateFaction1Count: Int = 0,
    val dropoffStateFaction2Count: Int = 0,
    val builderCount: Int = 0,
    val builderFaction1Count: Int = 0,
    val builderFaction2Count: Int = 0,
    val builderTargetCount: Int = 0,
    val constructionCount: Int = 0,
    val constructionFaction1Count: Int = 0,
    val constructionFaction2Count: Int = 0,
    val constructionRemainingTicks: Int = 0,
    val buildPlacedCount: Int = 0,
    val buildCancelledCount: Int = 0,
    val buildFailureCount: Int = 0,
    val buildFailureReasons: Map<String, Int> = emptyMap(),
    val maxProducerQueueLimit: Int = 0,
    val harvesterCount: Int = 0,
    val harvesterGatherCount: Int = 0,
    val harvesterReturnCount: Int = 0,
    val harvesterCargoTotal: Int = 0,
    val harvesterRetargetCount: Int = 0,
    val harvesterRetargetWorkers: Int = 0,
    val sessionHarvesterRetargets: Int = 0,
    val harvestCyclePickupCount: Int = 0,
    val harvestCycleDepositCount: Int = 0,
    val harvestCyclePickupAmount: Int = 0,
    val harvestCycleDepositAmount: Int = 0,
    val productionEnqueueCount: Int = 0,
    val productionProgressCount: Int = 0,
    val productionCompleteCount: Int = 0,
    val productionCancelCount: Int = 0,
    val researchEnqueueCount: Int = 0,
    val researchProgressCount: Int = 0,
    val researchCompleteCount: Int = 0,
    val researchCancelCount: Int = 0,
    val combatAttackCount: Int = 0,
    val combatKillCount: Int = 0,
    val combatDamageEventCount: Int = 0,
    val combatTotalDamage: Int = 0,
    val combatDeathDespawnCount: Int = 0,
    val pathRequestCount: Int = 0,
    val pathSolvedCount: Int = 0,
    val pathReplanCount: Int = 0,
    val pathAssignedCount: Int = 0,
    val pathProgressCount: Int = 0,
    val pathCompletedCount: Int = 0,
    val visionChangeCount: Int = 0,
    val visionVisibleFaction1: Int = 0,
    val visionHiddenFaction1: Int = 0,
    val visionVisibleFaction2: Int = 0,
    val visionHiddenFaction2: Int = 0,
    val finalVisibleTilesFaction1: Int? = null,
    val finalVisibleTilesFaction2: Int? = null,
    val commandAckAcceptedCount: Int = 0,
    val commandAckRejectedCount: Int = 0,
    val commandAckRejectReasons: Map<String, Int> = emptyMap(),
    val archetypeSelectionCount: Int = 0,
    val archetypeMoveCommandCount: Int = 0,
    val archetypeAttackCommandCount: Int = 0,
    val archetypesUsed: List<String> = emptyList()
)

fun summarizeSnapshotStream(lines: Sequence<String>): SnapshotStreamSummary {
    val counts = linkedMapOf<String, Int>()
    var total = 0
    var mapId: String? = null
    var buildVersion: String? = null
    var seed: Long? = null
    var worldHash: Long? = null
    var replayHash: Long? = null
    var resourceNodeChangeCount = 0
    var resourceNodeHarvestedTotal = 0
    var resourceNodeDepletedCount = 0
    var resourceNodeHarvestedMineralsFaction1 = 0
    var resourceNodeHarvestedMineralsFaction2 = 0
    var resourceNodeHarvestedGasFaction1 = 0
    var resourceNodeHarvestedGasFaction2 = 0
    val resourceNodeRemainingById = linkedMapOf<Int, Int>()
    var resourceDeltaEventCount = 0
    var resourceSpendMinerals = 0
    var resourceSpendGas = 0
    var resourceRefundMinerals = 0
    var resourceRefundGas = 0
    var resourceSummaryMineralsSpentFaction1 = 0
    var resourceSummaryMineralsSpentFaction2 = 0
    var resourceSummaryGasSpentFaction1 = 0
    var resourceSummaryGasSpentFaction2 = 0
    var resourceSummaryMineralsRefundedFaction1 = 0
    var resourceSummaryMineralsRefundedFaction2 = 0
    var resourceSummaryGasRefundedFaction1 = 0
    var resourceSummaryGasRefundedFaction2 = 0
    var producerCount = 0
    var trainingProducerCount = 0
    var rallyProducerCount = 0
    var dropoffProducerCount = 0
    var mineralDropoffProducerCount = 0
    var gasDropoffProducerCount = 0
    var dropoffStateCount = 0
    var dropoffStateFaction1Count = 0
    var dropoffStateFaction2Count = 0
    var builderCount = 0
    var builderFaction1Count = 0
    var builderFaction2Count = 0
    var builderTargetCount = 0
    var constructionCount = 0
    var constructionFaction1Count = 0
    var constructionFaction2Count = 0
    var constructionRemainingTicks = 0
    var buildPlacedCount = 0
    var buildCancelledCount = 0
    var buildFailureCount = 0
    var buildFailureReasons = linkedMapOf<String, Int>()
    var maxProducerQueueLimit = 0
    var harvesterCount = 0
    var harvesterGatherCount = 0
    var harvesterReturnCount = 0
    var harvesterCargoTotal = 0
    var harvesterRetargetCount = 0
    val harvesterRetargetWorkers = linkedSetOf<Int>()
    var sessionHarvesterRetargets = 0
    var harvestCyclePickupCount = 0
    var harvestCycleDepositCount = 0
    var harvestCyclePickupAmount = 0
    var harvestCycleDepositAmount = 0
    var productionEnqueueCount = 0
    var productionProgressCount = 0
    var productionCompleteCount = 0
    var productionCancelCount = 0
    var researchEnqueueCount = 0
    var researchProgressCount = 0
    var researchCompleteCount = 0
    var researchCancelCount = 0
    var combatAttackCount = 0
    var combatKillCount = 0
    var combatDamageEventCount = 0
    var combatTotalDamage = 0
    var combatDeathDespawnCount = 0
    var pathRequestCount = 0
    var pathSolvedCount = 0
    var pathReplanCount = 0
    var pathAssignedCount = 0
    var pathProgressCount = 0
    var pathCompletedCount = 0
    var visionChangeCount = 0
    var visionVisibleFaction1 = 0
    var visionHiddenFaction1 = 0
    var visionVisibleFaction2 = 0
    var visionHiddenFaction2 = 0
    var finalVisibleTilesFaction1: Int? = null
    var finalVisibleTilesFaction2: Int? = null
    var commandAckAcceptedCount = 0
    var commandAckRejectedCount = 0
    val commandAckRejectReasons = linkedMapOf<String, Int>()
    var archetypeSelectionCount = 0
    var archetypeMoveCommandCount = 0
    var archetypeAttackCommandCount = 0
    val archetypesUsed = linkedSetOf<String>()

    for (line in lines) {
        if (line.isBlank()) continue
        total++
        val obj = consumerJson.parseToJsonElement(line).jsonObject
        val type = obj.string("recordType") ?: "unknown"
        counts[type] = (counts[type] ?: 0) + 1
        when (type) {
            "sessionStart" -> {
                mapId = obj.string("mapId")
                buildVersion = obj.string("buildVersion")
                seed = obj.long("seed")
            }
            "mapState" -> {
                val nodes = obj.array("resourceNodes")
                for (node in nodes) {
                    val id = node.int("id") ?: continue
                    resourceNodeRemainingById[id] = node.int("remaining") ?: 0
                }
            }
            "sessionStats" -> {
                worldHash = obj.long("finalWorldHash") ?: worldHash
                replayHash = obj.long("finalReplayHash") ?: replayHash
                buildPlacedCount = obj.int("builds") ?: buildPlacedCount
                buildCancelledCount = obj.int("buildsCancelled") ?: buildCancelledCount
                buildFailureCount = obj.int("buildFailures") ?: buildFailureCount
                buildFailureReasons = linkedMapOf()
                obj.jsonObj("buildFailureReasons")?.let { reasons ->
                    for ((reason, value) in reasons) {
                        val count = value.toString().toIntOrNull() ?: continue
                        if (count > 0) buildFailureReasons[reason] = count
                    }
                }
                finalVisibleTilesFaction1 = obj.int("finalVisibleTilesFaction1") ?: finalVisibleTilesFaction1
                finalVisibleTilesFaction2 = obj.int("finalVisibleTilesFaction2") ?: finalVisibleTilesFaction2
                resourceNodeHarvestedMineralsFaction1 =
                    obj.int("harvestedMineralsFaction1") ?: resourceNodeHarvestedMineralsFaction1
                resourceNodeHarvestedMineralsFaction2 =
                    obj.int("harvestedMineralsFaction2") ?: resourceNodeHarvestedMineralsFaction2
                resourceNodeHarvestedGasFaction1 =
                    obj.int("harvestedGasFaction1") ?: resourceNodeHarvestedGasFaction1
                resourceNodeHarvestedGasFaction2 =
                    obj.int("harvestedGasFaction2") ?: resourceNodeHarvestedGasFaction2
                sessionHarvesterRetargets = obj.int("harvesterRetargets") ?: sessionHarvesterRetargets
            }
            "sessionEnd" -> {
                worldHash = obj.long("worldHash") ?: worldHash
                replayHash = obj.long("replayHash") ?: replayHash
            }
            "selection" -> {
                if (obj.string("selectionType") == "archetype") {
                    archetypeSelectionCount++
                    obj.string("archetype")?.let(archetypesUsed::add)
                }
            }
            "command" -> {
                when (obj.string("commandType")) {
                    "moveArchetype" -> {
                        archetypeMoveCommandCount++
                        obj.string("archetype")?.let(archetypesUsed::add)
                    }
                    "attackArchetype" -> {
                        archetypeAttackCommandCount++
                        obj.string("archetype")?.let(archetypesUsed::add)
                    }
                }
            }
            "commandAck" -> {
                if (obj.bool("accepted") == true) {
                    commandAckAcceptedCount++
                } else {
                    commandAckRejectedCount++
                    obj.string("reason")?.let { reason ->
                        commandAckRejectReasons[reason] = (commandAckRejectReasons[reason] ?: 0) + 1
                    }
                }
            }
            "resourceDelta" -> {
                val events = obj.array("events")
                for (event in events) {
                    resourceDeltaEventCount++
                    val kind = event.string("kind")
                    val minerals = event.int("minerals") ?: 0
                    val gas = event.int("gas") ?: 0
                    if (kind == "refund") {
                        resourceRefundMinerals += minerals
                        resourceRefundGas += gas
                    } else {
                        resourceSpendMinerals += minerals
                        resourceSpendGas += gas
                    }
                }
            }
            "resourceNode" -> {
                val nodes = obj.array("nodes")
                resourceNodeChangeCount += nodes.size
                for (node in nodes) {
                    val id = node.int("id") ?: continue
                    resourceNodeHarvestedTotal += node.int("harvested") ?: 0
                    if (node.bool("depleted") == true) resourceNodeDepletedCount++
                    resourceNodeRemainingById[id] = node.int("remaining") ?: 0
                }
            }
            "resourceDeltaSummary" -> {
                val factions = obj.array("factions")
                for (faction in factions) {
                    when (faction.int("faction")) {
                        1 -> {
                            resourceSummaryMineralsSpentFaction1 += faction.int("mineralsSpent") ?: 0
                            resourceSummaryGasSpentFaction1 += faction.int("gasSpent") ?: 0
                            resourceSummaryMineralsRefundedFaction1 += faction.int("mineralsRefunded") ?: 0
                            resourceSummaryGasRefundedFaction1 += faction.int("gasRefunded") ?: 0
                        }
                        2 -> {
                            resourceSummaryMineralsSpentFaction2 += faction.int("mineralsSpent") ?: 0
                            resourceSummaryGasSpentFaction2 += faction.int("gasSpent") ?: 0
                            resourceSummaryMineralsRefundedFaction2 += faction.int("mineralsRefunded") ?: 0
                            resourceSummaryGasRefundedFaction2 += faction.int("gasRefunded") ?: 0
                        }
                    }
                }
            }
            "producerState" -> {
                val entities = obj.array("entities")
                producerCount = entities.size
                trainingProducerCount = 0
                rallyProducerCount = 0
                dropoffProducerCount = 0
                mineralDropoffProducerCount = 0
                gasDropoffProducerCount = 0
                maxProducerQueueLimit = 0
                for (entity in entities) {
                    if (entity.bool("supportsTraining") == true) trainingProducerCount++
                    if (entity.bool("supportsRally") == true) rallyProducerCount++
                    if (entity.bool("supportsDropoff") == true) {
                        dropoffProducerCount++
                        val kinds = entity.stringArray("dropoffResourceKinds")
                        if ("minerals" in kinds) mineralDropoffProducerCount++
                        if ("gas" in kinds) gasDropoffProducerCount++
                    }
                    val limit = entity.int("productionQueueLimit") ?: 0
                    if (limit > maxProducerQueueLimit) maxProducerQueueLimit = limit
                }
            }
            "dropoffState" -> {
                val entities = obj.array("entities")
                dropoffStateCount = entities.size
                dropoffStateFaction1Count = 0
                dropoffStateFaction2Count = 0
                for (entity in entities) {
                    when (entity.int("faction")) {
                        1 -> dropoffStateFaction1Count++
                        2 -> dropoffStateFaction2Count++
                    }
                }
            }
            "builderState" -> {
                val entities = obj.array("entities")
                builderCount = entities.size
                builderFaction1Count = 0
                builderFaction2Count = 0
                builderTargetCount = entities.mapNotNull { it.int("targetBuildingId") }.toSet().size
                for (entity in entities) {
                    when (entity.int("faction")) {
                        1 -> builderFaction1Count++
                        2 -> builderFaction2Count++
                    }
                }
            }
            "constructionState" -> {
                val entities = obj.array("entities")
                constructionCount = entities.size
                constructionFaction1Count = 0
                constructionFaction2Count = 0
                constructionRemainingTicks = 0
                for (entity in entities) {
                    constructionRemainingTicks += entity.int("remainingTicks") ?: 0
                    when (entity.int("faction")) {
                        1 -> constructionFaction1Count++
                        2 -> constructionFaction2Count++
                    }
                }
            }
            "harvesterState" -> {
                val entities = obj.array("entities")
                harvesterCount = entities.size
                harvesterGatherCount = 0
                harvesterReturnCount = 0
                harvesterCargoTotal = 0
                for (entity in entities) {
                    when (entity.string("phase")) {
                        "return" -> harvesterReturnCount++
                        else -> harvesterGatherCount++
                    }
                    harvesterCargoTotal += entity.int("cargoAmount") ?: 0
                }
            }
            "harvesterRetarget" -> {
                val events = obj.array("events")
                harvesterRetargetCount += events.size
                for (event in events) {
                    val workerId = event.int("workerId") ?: continue
                    harvesterRetargetWorkers.add(workerId)
                }
            }
            "harvestCycle" -> {
                val events = obj.array("events")
                for (event in events) {
                    val amount = event.int("amount") ?: 0
                    when (event.string("kind")) {
                        "deposit" -> {
                            harvestCycleDepositCount++
                            harvestCycleDepositAmount += amount
                        }
                        else -> {
                            harvestCyclePickupCount++
                            harvestCyclePickupAmount += amount
                        }
                    }
                }
            }
            "production" -> {
                val events = obj.array("events")
                for (event in events) {
                    when (event.string("kind")) {
                        "enqueue" -> productionEnqueueCount++
                        "complete" -> productionCompleteCount++
                        "cancel" -> productionCancelCount++
                        else -> productionProgressCount++
                    }
                }
            }
            "research" -> {
                val events = obj.array("events")
                for (event in events) {
                    when (event.string("kind")) {
                        "enqueue" -> researchEnqueueCount++
                        "complete" -> researchCompleteCount++
                        "cancel" -> researchCancelCount++
                        else -> researchProgressCount++
                    }
                }
            }
            "metrics" -> {
                pathRequestCount += obj.int("pathRequests") ?: 0
                pathSolvedCount += obj.int("pathSolved") ?: 0
                pathReplanCount += obj.int("replans") ?: 0
            }
            "pathAssigned" -> {
                pathAssignedCount += obj.array("entities").size
            }
            "pathProgress" -> {
                val entities = obj.array("entities")
                pathProgressCount += entities.size
                for (entity in entities) {
                    if (entity.bool("completed") == true) pathCompletedCount++
                }
            }
            "vision" -> {
                val changes = obj.array("changes")
                visionChangeCount += changes.size
                for (change in changes) {
                    when (change.int("faction")) {
                        1 -> {
                            if (change.bool("visible") == true) visionVisibleFaction1++ else visionHiddenFaction1++
                        }
                        2 -> {
                            if (change.bool("visible") == true) visionVisibleFaction2++ else visionHiddenFaction2++
                        }
                    }
                }
            }
            "combat" -> {
                combatAttackCount += obj.int("attacks") ?: 0
                combatKillCount += obj.int("kills") ?: 0
            }
            "damage" -> {
                val events = obj.array("events")
                combatDamageEventCount += events.size
                for (event in events) {
                    combatTotalDamage += event.int("damage") ?: 0
                }
            }
            "despawn" -> {
                val entities = obj.array("entities")
                for (entity in entities) {
                    when (entity.string("reason")) {
                        "death" -> combatDeathDespawnCount++
                        "resourceDepleted" -> {
                            val id = entity.int("entityId") ?: continue
                            resourceNodeRemainingById.remove(id)
                        }
                    }
                }
            }
        }
    }

    return SnapshotStreamSummary(
        totalRecords = total,
        countsByType = counts,
        mapId = mapId,
        buildVersion = buildVersion,
        seed = seed,
        worldHash = worldHash,
        replayHash = replayHash,
        resourceNodeChangeCount = resourceNodeChangeCount,
        resourceNodeHarvestedTotal = resourceNodeHarvestedTotal,
        resourceNodeDepletedCount = resourceNodeDepletedCount,
        resourceNodeHarvestedMineralsFaction1 = resourceNodeHarvestedMineralsFaction1,
        resourceNodeHarvestedMineralsFaction2 = resourceNodeHarvestedMineralsFaction2,
        resourceNodeHarvestedGasFaction1 = resourceNodeHarvestedGasFaction1,
        resourceNodeHarvestedGasFaction2 = resourceNodeHarvestedGasFaction2,
        currentResourceNodeCount = resourceNodeRemainingById.values.count { it > 0 },
        currentResourceNodeRemainingTotal = resourceNodeRemainingById.values.sum(),
        resourceDeltaEventCount = resourceDeltaEventCount,
        resourceSpendMinerals = resourceSpendMinerals,
        resourceSpendGas = resourceSpendGas,
        resourceRefundMinerals = resourceRefundMinerals,
        resourceRefundGas = resourceRefundGas,
        resourceSummaryMineralsSpentFaction1 = resourceSummaryMineralsSpentFaction1,
        resourceSummaryMineralsSpentFaction2 = resourceSummaryMineralsSpentFaction2,
        resourceSummaryGasSpentFaction1 = resourceSummaryGasSpentFaction1,
        resourceSummaryGasSpentFaction2 = resourceSummaryGasSpentFaction2,
        resourceSummaryMineralsRefundedFaction1 = resourceSummaryMineralsRefundedFaction1,
        resourceSummaryMineralsRefundedFaction2 = resourceSummaryMineralsRefundedFaction2,
        resourceSummaryGasRefundedFaction1 = resourceSummaryGasRefundedFaction1,
        resourceSummaryGasRefundedFaction2 = resourceSummaryGasRefundedFaction2,
        producerCount = producerCount,
        trainingProducerCount = trainingProducerCount,
        rallyProducerCount = rallyProducerCount,
        dropoffProducerCount = dropoffProducerCount,
        mineralDropoffProducerCount = mineralDropoffProducerCount,
        gasDropoffProducerCount = gasDropoffProducerCount,
        dropoffStateCount = dropoffStateCount,
        dropoffStateFaction1Count = dropoffStateFaction1Count,
        dropoffStateFaction2Count = dropoffStateFaction2Count,
        builderCount = builderCount,
        builderFaction1Count = builderFaction1Count,
        builderFaction2Count = builderFaction2Count,
        builderTargetCount = builderTargetCount,
        constructionCount = constructionCount,
        constructionFaction1Count = constructionFaction1Count,
        constructionFaction2Count = constructionFaction2Count,
        constructionRemainingTicks = constructionRemainingTicks,
        buildPlacedCount = buildPlacedCount,
        buildCancelledCount = buildCancelledCount,
        buildFailureCount = buildFailureCount,
        buildFailureReasons = buildFailureReasons,
        maxProducerQueueLimit = maxProducerQueueLimit,
        harvesterCount = harvesterCount,
        harvesterGatherCount = harvesterGatherCount,
        harvesterReturnCount = harvesterReturnCount,
        harvesterCargoTotal = harvesterCargoTotal,
        harvesterRetargetCount = harvesterRetargetCount,
        harvesterRetargetWorkers = harvesterRetargetWorkers.size,
        sessionHarvesterRetargets = sessionHarvesterRetargets,
        harvestCyclePickupCount = harvestCyclePickupCount,
        harvestCycleDepositCount = harvestCycleDepositCount,
        harvestCyclePickupAmount = harvestCyclePickupAmount,
        harvestCycleDepositAmount = harvestCycleDepositAmount,
        productionEnqueueCount = productionEnqueueCount,
        productionProgressCount = productionProgressCount,
        productionCompleteCount = productionCompleteCount,
        productionCancelCount = productionCancelCount,
        researchEnqueueCount = researchEnqueueCount,
        researchProgressCount = researchProgressCount,
        researchCompleteCount = researchCompleteCount,
        researchCancelCount = researchCancelCount,
        combatAttackCount = combatAttackCount,
        combatKillCount = combatKillCount,
        combatDamageEventCount = combatDamageEventCount,
        combatTotalDamage = combatTotalDamage,
        combatDeathDespawnCount = combatDeathDespawnCount,
        pathRequestCount = pathRequestCount,
        pathSolvedCount = pathSolvedCount,
        pathReplanCount = pathReplanCount,
        pathAssignedCount = pathAssignedCount,
        pathProgressCount = pathProgressCount,
        pathCompletedCount = pathCompletedCount,
        visionChangeCount = visionChangeCount,
        visionVisibleFaction1 = visionVisibleFaction1,
        visionHiddenFaction1 = visionHiddenFaction1,
        visionVisibleFaction2 = visionVisibleFaction2,
        visionHiddenFaction2 = visionHiddenFaction2,
        finalVisibleTilesFaction1 = finalVisibleTilesFaction1,
        finalVisibleTilesFaction2 = finalVisibleTilesFaction2,
        commandAckAcceptedCount = commandAckAcceptedCount,
        commandAckRejectedCount = commandAckRejectedCount,
        commandAckRejectReasons = commandAckRejectReasons,
        archetypeSelectionCount = archetypeSelectionCount,
        archetypeMoveCommandCount = archetypeMoveCommandCount,
        archetypeAttackCommandCount = archetypeAttackCommandCount,
        archetypesUsed = archetypesUsed.toList()
    )
}

fun renderSnapshotStreamSummary(path: Path, summary: SnapshotStreamSummary): String {
    val lines = ArrayList<String>(summary.countsByType.size + 4)
    lines.add("snapshot stream summary:")
    lines.add("path=${path.toAbsolutePath().normalize()}")
    lines.add("records=${summary.totalRecords}")
    val counts =
        summary.countsByType.entries.joinToString(separator = " ") { (type, count) ->
            "$type=$count"
        }
    lines.add("types=$counts")
    lines.add(
        "session: mapId=${summary.mapId} buildVersion=${summary.buildVersion} " +
            "seed=${summary.seed} worldHash=${summary.worldHash} replayHash=${summary.replayHash}"
    )
    if (summary.resourceDeltaEventCount > 0 || summary.countsByType["resourceDeltaSummary"] != null) {
        lines.add(
            "economy: events=${summary.resourceDeltaEventCount} " +
                "spend=${summary.resourceSpendMinerals}/${summary.resourceSpendGas} " +
                "refund=${summary.resourceRefundMinerals}/${summary.resourceRefundGas} " +
                "f1=${summary.resourceSummaryMineralsSpentFaction1}/${summary.resourceSummaryGasSpentFaction1}->" +
                "${summary.resourceSummaryMineralsRefundedFaction1}/${summary.resourceSummaryGasRefundedFaction1} " +
                "f2=${summary.resourceSummaryMineralsSpentFaction2}/${summary.resourceSummaryGasSpentFaction2}->" +
                "${summary.resourceSummaryMineralsRefundedFaction2}/${summary.resourceSummaryGasRefundedFaction2}"
        )
    }
    if (summary.commandAckAcceptedCount > 0 || summary.commandAckRejectedCount > 0) {
        val reasons =
            summary.commandAckRejectReasons.entries.joinToString(",") { (reason, count) ->
                "$reason=$count"
            }
        lines.add(
            "acks: ok=${summary.commandAckAcceptedCount} fail=${summary.commandAckRejectedCount}" +
                if (reasons.isNotEmpty()) " reasons=$reasons" else ""
        )
    }
    if (summary.resourceNodeChangeCount > 0 || summary.countsByType["resourceNode"] != null) {
        lines.add(
            "resourceNodes: changed=${summary.resourceNodeChangeCount} " +
                "harvested=${summary.resourceNodeHarvestedTotal} " +
                "f1=${summary.resourceNodeHarvestedMineralsFaction1}/${summary.resourceNodeHarvestedGasFaction1} " +
                "f2=${summary.resourceNodeHarvestedMineralsFaction2}/${summary.resourceNodeHarvestedGasFaction2} " +
                "depleted=${summary.resourceNodeDepletedCount} " +
                "active=${summary.currentResourceNodeCount} remaining=${summary.currentResourceNodeRemainingTotal}"
        )
    }
    if (summary.producerCount > 0 || summary.countsByType["production"] != null) {
        lines.add(
            "producers: total=${summary.producerCount} training=${summary.trainingProducerCount} " +
                "rally=${summary.rallyProducerCount} dropoff=${summary.dropoffProducerCount} " +
                "minerals=${summary.mineralDropoffProducerCount} gas=${summary.gasDropoffProducerCount} " +
                "maxQueue=${summary.maxProducerQueueLimit} " +
                "prod=e${summary.productionEnqueueCount}/p${summary.productionProgressCount}/" +
                "c${summary.productionCompleteCount}/x${summary.productionCancelCount}"
        )
    }
    if (
        summary.researchEnqueueCount > 0 ||
        summary.researchProgressCount > 0 ||
        summary.researchCompleteCount > 0 ||
        summary.researchCancelCount > 0 ||
        summary.countsByType["research"] != null
    ) {
        lines.add(
            "research: e${summary.researchEnqueueCount}/p${summary.researchProgressCount}/" +
                "c${summary.researchCompleteCount}/x${summary.researchCancelCount}"
        )
    }
    if (summary.dropoffStateCount > 0 || summary.countsByType["dropoffState"] != null) {
        lines.add(
            "dropoffs: total=${summary.dropoffStateCount} " +
                "f1=${summary.dropoffStateFaction1Count} f2=${summary.dropoffStateFaction2Count}"
        )
    }
    if (summary.builderCount > 0 || summary.countsByType["builderState"] != null) {
        lines.add(
            "builders: total=${summary.builderCount} f1=${summary.builderFaction1Count} " +
                "f2=${summary.builderFaction2Count} targets=${summary.builderTargetCount}"
        )
    }
    if (summary.buildPlacedCount > 0 || summary.buildCancelledCount > 0 || summary.buildFailureCount > 0) {
        val reasons =
            summary.buildFailureReasons.entries.joinToString(",") { (reason, count) ->
                "$reason=$count"
            }
        lines.add(
            "builds: placed=${summary.buildPlacedCount} canceled=${summary.buildCancelledCount} " +
                "fail=${summary.buildFailureCount}" +
                if (reasons.isNotEmpty()) " reasons=$reasons" else ""
        )
    }
    if (summary.constructionCount > 0 || summary.countsByType["constructionState"] != null) {
        lines.add(
            "construction: total=${summary.constructionCount} " +
                "f1=${summary.constructionFaction1Count} f2=${summary.constructionFaction2Count} " +
                "remaining=${summary.constructionRemainingTicks}"
        )
    }
    if (summary.harvesterCount > 0 || summary.countsByType["harvesterState"] != null) {
        lines.add(
            "harvesters: total=${summary.harvesterCount} gather=${summary.harvesterGatherCount} " +
                "return=${summary.harvesterReturnCount} cargo=${summary.harvesterCargoTotal}"
        )
    }
    if (summary.harvesterRetargetCount > 0 || summary.countsByType["harvesterRetarget"] != null) {
        lines.add(
            "harvesterRetarget: events=${summary.harvesterRetargetCount} " +
                "workers=${summary.harvesterRetargetWorkers} total=${summary.sessionHarvesterRetargets}"
        )
    }
    if (summary.harvestCyclePickupCount > 0 || summary.harvestCycleDepositCount > 0 || summary.countsByType["harvestCycle"] != null) {
        lines.add(
            "harvestCycle: pickup=${summary.harvestCyclePickupCount}/${summary.harvestCyclePickupAmount} " +
                "deposit=${summary.harvestCycleDepositCount}/${summary.harvestCycleDepositAmount}"
        )
    }
    if (
        summary.combatAttackCount > 0 ||
        summary.combatDamageEventCount > 0 ||
        summary.combatDeathDespawnCount > 0 ||
        summary.countsByType["combat"] != null
    ) {
        lines.add(
            "combat: attacks=${summary.combatAttackCount} kills=${summary.combatKillCount} " +
                "damageEvents=${summary.combatDamageEventCount} damage=${summary.combatTotalDamage} " +
                "deathDespawns=${summary.combatDeathDespawnCount}"
        )
    }
    if (
        summary.pathRequestCount > 0 ||
        summary.pathAssignedCount > 0 ||
        summary.pathProgressCount > 0 ||
        summary.countsByType["metrics"] != null
    ) {
        lines.add(
            "pathing: req=${summary.pathRequestCount} solved=${summary.pathSolvedCount} " +
                "replans=${summary.pathReplanCount} assigned=${summary.pathAssignedCount} " +
                "progress=${summary.pathProgressCount} completed=${summary.pathCompletedCount}"
        )
    }
    if (summary.visionChangeCount > 0 || summary.countsByType["vision"] != null || summary.finalVisibleTilesFaction1 != null) {
        lines.add(
            "vision: changes=${summary.visionChangeCount} " +
                "f1=+${summary.visionVisibleFaction1}/-${summary.visionHiddenFaction1} " +
                "f2=+${summary.visionVisibleFaction2}/-${summary.visionHiddenFaction2} " +
                "final=${summary.finalVisibleTilesFaction1}/${summary.finalVisibleTilesFaction2}"
        )
    }
    if (
        summary.archetypeSelectionCount > 0 ||
        summary.archetypeMoveCommandCount > 0 ||
        summary.archetypeAttackCommandCount > 0
    ) {
        lines.add(
            "archetypes: select=${summary.archetypeSelectionCount} " +
                "move=${summary.archetypeMoveCommandCount} " +
                "attack=${summary.archetypeAttackCommandCount} " +
                "ids=${summary.archetypesUsed.joinToString(",")}"
        )
    }
    return lines.joinToString(separator = "\n")
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: SnapshotStreamConsumer <path-to-ndjson>" }
    val path = Paths.get(args[0])
    require(Files.exists(path)) { "snapshot stream not found: $path" }
    Files.newBufferedReader(path).use { reader ->
        val summary = summarizeSnapshotStream(reader.lineSequence())
        println(renderSnapshotStreamSummary(path, summary))
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.toString()?.trim('"')

private fun JsonObject.long(key: String): Long? =
    this[key]?.toString()?.toLongOrNull()

private fun JsonObject.int(key: String): Int? =
    this[key]?.toString()?.toIntOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    this[key]?.toString()?.toBooleanStrictOrNull()

private fun JsonObject.jsonObj(key: String): JsonObject? =
    this[key]?.jsonObject

private fun JsonObject.array(key: String): List<JsonObject> =
    this[key]
        ?.toString()
        ?.let { consumerJson.parseToJsonElement(it) }
        ?.let { element ->
            element.jsonArray.mapNotNull { child ->
                child as? JsonObject
            }
        }
        ?: emptyList()

private fun JsonObject.stringArray(key: String): List<String> =
    this[key]
        ?.toString()
        ?.let { consumerJson.parseToJsonElement(it) }
        ?.jsonArray
        ?.map { it.toString().trim('"') }
        ?: emptyList()
