package starkraft.tools

import starkraft.sim.replay.ReplayIO
import starkraft.sim.replay.ReplayHashRecorder
import starkraft.sim.replay.ReplayMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runToolsCli(args))
}

internal fun runToolsCli(args: Array<String>): Int {
    if (args.size < 3) {
        printUsage()
        return 1
    }
    return when {
        args[0] == "replay" && args[1] == "meta" -> runReplayMeta(args.drop(2))
        args[0] == "replay" && args[1] == "stats" -> runReplayStats(args.drop(2))
        args[0] == "replay" && args[1] == "verify-ndjson" -> runReplayVerifyNdjson(args.drop(2))
        args[0] == "replay" && args[1] == "verify" -> runReplayVerify(args.drop(2))
        args[0] == "replay" && args[1] == "fast-forward" -> runReplayFastForward(args.drop(2))
        args[0] == "map" && args[1] == "validate" -> runMapValidate(args[2])
        args[0] == "map" && args[1] == "generate" -> runMapGenerate(args.drop(2))
        args[0] == "data" && args[1] == "validate" -> runDataValidate(args.drop(2))
        else -> {
            printUsage()
            1
        }
    }
}

private fun runReplayMeta(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = resolvePath(args[0])
    val json = args.drop(1).any { it == "--json" }
    if (args.drop(1).any { it != "--json" }) {
        printUsage()
        return 1
    }
    val metadata = ReplayIO.inspect(path)
    if (json) {
        printStats(
            json = true,
            fields =
                buildJsonObject {
                    put("replay", path.toString())
                    put("schema", metadata.schema)
                    put("legacy", metadata.legacy)
                    put("events", metadata.eventCount)
                    put("sizeBytes", metadata.fileSizeBytes)
                    put("replayHash", metadata.replayHash?.toString() ?: "missing")
                    put("seed", metadata.seed?.toString() ?: "unknown")
                    put("mapId", metadata.mapId ?: "unknown")
                    put("buildVersion", metadata.buildVersion ?: "unknown")
                    put("winnerFaction", metadata.winnerFaction?.toString() ?: "unknown")
                    put("matchEndReason", metadata.matchEndReason ?: "unknown")
                }
        )
    } else {
        println(formatReplayMetadata(path, metadata))
    }
    return 0
}

private fun runReplayStats(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = args[0]
    val json = args.drop(1).any { it == "--json" }
    if (args.drop(1).any { it != "--json" }) {
        printUsage()
        return 1
    }
    val resolvedPath = resolvePath(path)
    return runReplayStatsResolved(resolvedPath, json)
}

private fun runReplayStatsResolved(path: Path, json: Boolean): Int {
    return try {
        val meta = ReplayIO.inspect(path)
        printStats(
            json = json,
            fields =
                buildJsonObject {
                    put("replay", path.toString())
                    put("format", "sim-json")
                    put("schema", meta.schema)
                    put("events", meta.eventCount)
                    put("replayHash", meta.replayHash?.toString() ?: "missing")
                }
        )
        0
    } catch (_: Exception) {
        try {
            val summary = summarizeNdjsonReplay(path)
            val verify = verifyNdjsonKeyframeHashes(path)
            val fields =
                buildJsonObject {
                    put("replay", path.toString())
                    put("format", "server-ndjson")
                    put("records", summary.records)
                    put("header", summary.headerCount)
                    put("command", summary.commandCount)
                    put("keyframe", summary.keyframeCount)
                    put("matchEnd", summary.matchEndCount)
                    put("keyframeHashMismatches", verify.mismatches)
                    if (verify.firstMismatchTick != null) {
                        put("firstMismatchTick", verify.firstMismatchTick)
                    }
                }
            printStats(json = json, fields = fields)
            if (verify.mismatches == 0) 0 else 2
        } catch (e: Exception) {
            if (json) {
                printStats(
                    json = true,
                    fields =
                        buildJsonObject {
                            put("replay", path.toString())
                            put("result", "invalid")
                            put("error", e.message ?: "unknown error")
                        }
                )
            } else {
                println("replay: $path")
                println("result: invalid")
                println("error: ${e.message}")
            }
            2
        }
    }
}

private fun printStats(json: Boolean, fields: JsonObject) {
    if (json) {
        println(Json.encodeToString(JsonObject.serializer(), fields))
        return
    }
    for ((key, value) in fields) {
        println("$key: ${value.toString().trim('\"')}")
    }
}

private fun runReplayVerifyNdjson(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = resolvePath(args[0])
    val json = args.drop(1).any { it == "--json" }
    if (args.drop(1).any { it != "--json" }) {
        printUsage()
        return 1
    }
    return try {
        val result = verifyNdjsonKeyframeHashes(path)
        if (json) {
            printStats(
                json = true,
                fields =
                    buildJsonObject {
                        put("replay", path.toString())
                        put("format", "server-ndjson")
                        put("keyframesChecked", result.keyframesChecked)
                        put("mismatches", result.mismatches)
                        if (result.firstMismatchTick != null) {
                            put("firstMismatchTick", result.firstMismatchTick)
                        }
                        put("result", if (result.mismatches == 0) "ok" else "mismatch")
                    }
            )
        } else {
            println("replay: $path")
            println("format: server-ndjson")
            println("keyframesChecked: ${result.keyframesChecked}")
            println("mismatches: ${result.mismatches}")
            if (result.firstMismatchTick != null) {
                println("firstMismatchTick: ${result.firstMismatchTick}")
            }
            if (result.mismatches == 0) println("result: ok") else println("result: mismatch")
        }
        if (result.mismatches == 0) 0 else 2
    } catch (e: Exception) {
        if (json) {
            printStats(
                json = true,
                fields =
                    buildJsonObject {
                        put("replay", path.toString())
                        put("result", "invalid")
                        put("error", e.message ?: "unknown error")
                    }
            )
        } else {
            println("replay: $path")
            println("result: invalid")
            println("error: ${e.message}")
        }
        2
    }
}

private fun runReplayVerify(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = resolvePath(args[0])
    val strictHash = args.drop(1).any { it == "--strictHash" }
    val json = args.drop(1).any { it == "--json" }
    if (args.drop(1).any { it != "--strictHash" && it != "--json" }) {
        printUsage()
        return 1
    }
    val commands = try {
        ReplayIO.load(path, strictHash = strictHash)
    } catch (e: Exception) {
        if (json) {
            printStats(
                json = true,
                fields =
                    buildJsonObject {
                        put("replay", path.toString())
                        put("result", "validation-error")
                        put("error", e.message ?: "unknown error")
                    }
            )
        } else {
            println("replay: $path")
            println("result: validation-error")
            println("error: ${e.message}")
        }
        return 2
    }
    val metadata = ReplayIO.inspect(path)
    val recorder = ReplayHashRecorder()
    for (command in commands) recorder.onCommand(command)
    val computedHash = recorder.value()
    val expectedHash = metadata.replayHash
    val result = if (expectedHash == null) {
        if (strictHash) "missing-hash" else "ok-legacy"
    } else if (expectedHash == computedHash) {
        "ok"
    } else {
        "mismatch"
    }
    val replayRun = fastForwardReplay(path)
    if (json) {
        printStats(
            json = true,
            fields =
                buildJsonObject {
                    put("replay", path.toString())
                    put("commands", commands.size)
                    put("expectedHash", expectedHash?.toString() ?: "missing")
                    put("computedHash", computedHash.toString())
                    put("worldHash", replayRun.finalWorldHash.toString())
                    put("result", result)
                }
        )
    } else {
        println("replay: $path")
        println("commands: ${commands.size}")
        println("expectedHash: ${expectedHash ?: "missing"}")
        println("computedHash: $computedHash")
        println("worldHash: ${replayRun.finalWorldHash}")
        println("result: $result")
    }
    return if (result == "mismatch" || result == "missing-hash") 2 else 0
}

private fun runReplayFastForward(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = resolvePath(args[0])
    var tickLimit: Int? = null
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--ticks" -> {
                tickLimit = args.getOrNull(i + 1)?.toIntOrNull() ?: return 1
                i += 2
            }
            else -> return 1
        }
    }
    val result = try {
        fastForwardReplay(path, tickLimit)
    } catch (e: Exception) {
        println("replay: $path")
        println("result: validation-error")
        println("error: ${e.message}")
        return 2
    }
    println("replay: $path")
    println("result: ok")
    println("finalTick: ${result.finalTick}")
    println("commandCount: ${result.commandCount}")
    println("worldHash: ${result.finalWorldHash}")
    return 0
}

private fun runMapValidate(pathArg: String): Int {
    val path = resolvePath(pathArg)
    val result = try {
        validateMap(path)
    } catch (e: Exception) {
        println("map: $path")
        println("result: invalid")
        println("error: ${e.message}")
        return 2
    }
    if (result.ok) {
        println("map: $path")
        println("result: ok")
        return 0
    }
    println("map: $path")
    println("result: invalid")
    for (error in result.errors) {
        println("error: $error")
    }
    return 2
}

private fun runMapGenerate(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val outPath = resolvePath(args[0])
    var width = 64
    var height = 64
    var seed = 1337L
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--width" -> {
                width = args.getOrNull(i + 1)?.toIntOrNull() ?: return 1
                i += 2
            }
            "--height" -> {
                height = args.getOrNull(i + 1)?.toIntOrNull() ?: return 1
                i += 2
            }
            "--seed" -> {
                seed = args.getOrNull(i + 1)?.toLongOrNull() ?: return 1
                i += 2
            }
            else -> return 1
        }
    }
    val payload = try {
        generateMap(outPath, width, height, seed)
    } catch (e: Exception) {
        println("map: $outPath")
        println("result: invalid")
        println("error: ${e.message}")
        return 2
    }
    println("map: $outPath")
    println("result: generated")
    println("id: ${payload.id}")
    println("width: ${payload.width}")
    println("height: ${payload.height}")
    println("blockedTiles: ${payload.blockedTiles.size}")
    println("weightedTiles: ${payload.weightedTiles.size}")
    return 0
}

private fun runDataValidate(args: List<String>): Int {
    if (args.size != 2 || args[0] != "--dir") {
        printUsage()
        return 1
    }
    val dir = resolvePath(args[1])
    val result = try {
        validateDataDir(dir)
    } catch (e: Exception) {
        println("dataDir: $dir")
        println("result: invalid")
        println("error: ${e.message}")
        return 2
    }
    if (result.ok) {
        println("dataDir: $dir")
        println("result: ok")
        return 0
    }
    println("dataDir: $dir")
    println("result: invalid")
    for (error in result.errors) {
        println("error: $error")
    }
    return 2
}

internal fun resolvePath(raw: String): Path {
    val path = Paths.get(raw)
    if (path.isAbsolute) return path.normalize()
    return repoRoot().resolve(path).normalize()
}

private fun repoRoot(): Path {
    var current = Paths.get("").toAbsolutePath().normalize()
    while (current.parent != null) {
        if (current.resolve("settings.gradle.kts").toFile().exists()) {
            return current
        }
        current = current.parent
    }
    return Paths.get("").toAbsolutePath().normalize()
}

internal fun formatReplayMetadata(path: Path, metadata: ReplayMetadata): String =
    buildString {
        appendLine("replay: $path")
        appendLine("schema: ${metadata.schema}")
        appendLine("legacy: ${metadata.legacy}")
        appendLine("events: ${metadata.eventCount}")
        appendLine("sizeBytes: ${metadata.fileSizeBytes}")
        appendLine("replayHash: ${metadata.replayHash ?: "missing"}")
        appendLine("seed: ${metadata.seed ?: "unknown"}")
        appendLine("mapId: ${metadata.mapId ?: "unknown"}")
        appendLine("buildVersion: ${metadata.buildVersion ?: "unknown"}")
        appendLine("winnerFaction: ${metadata.winnerFaction ?: "unknown"}")
        append("matchEndReason: ${metadata.matchEndReason ?: "unknown"}")
    }

private fun printUsage() {
    println(
        """
        Usage:
          replay meta <path> [--json]
          replay stats <path> [--json]
          replay verify-ndjson <path> [--json]
          replay verify <path> [--strictHash] [--json]
          replay fast-forward <path> [--ticks N]
          map validate <path>
          map generate <path> [--width N] [--height N] [--seed N]
          data validate --dir <path>
        """.trimIndent()
    )
}
