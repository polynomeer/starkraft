package starkraft.sim.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    val replayHash: Long? = null
)

fun summarizeSnapshotStream(lines: Sequence<String>): SnapshotStreamSummary {
    val counts = linkedMapOf<String, Int>()
    var total = 0
    var mapId: String? = null
    var buildVersion: String? = null
    var seed: Long? = null
    var worldHash: Long? = null
    var replayHash: Long? = null

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
        }
    }

    return SnapshotStreamSummary(
        totalRecords = total,
        countsByType = counts,
        mapId = mapId,
        buildVersion = buildVersion,
        seed = seed,
        worldHash = worldHash,
        replayHash = replayHash
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
