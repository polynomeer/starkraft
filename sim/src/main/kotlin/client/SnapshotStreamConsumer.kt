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
    val resourceSummaryGasRefundedFaction2: Int = 0
)

fun summarizeSnapshotStream(lines: Sequence<String>): SnapshotStreamSummary {
    val counts = linkedMapOf<String, Int>()
    var total = 0
    var mapId: String? = null
    var buildVersion: String? = null
    var seed: Long? = null
    var worldHash: Long? = null
    var replayHash: Long? = null
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
            "sessionStats" -> {
                worldHash = obj.long("finalWorldHash") ?: worldHash
                replayHash = obj.long("finalReplayHash") ?: replayHash
            }
            "sessionEnd" -> {
                worldHash = obj.long("worldHash") ?: worldHash
                replayHash = obj.long("replayHash") ?: replayHash
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
        resourceSummaryGasRefundedFaction2 = resourceSummaryGasRefundedFaction2
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
