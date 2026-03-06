package starkraft.tools

import starkraft.sim.replay.ReplayIO
import starkraft.sim.replay.ReplayHashRecorder
import starkraft.sim.replay.ReplayMetadata
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val exitCode = runToolsCli(args)
    if (exitCode != 0) {
        throw IllegalStateException("tools command failed (exitCode=$exitCode)")
    }
}

internal fun runToolsCli(args: Array<String>): Int {
    if (args.size < 3) {
        printUsage()
        return 1
    }
    return when {
        args[0] == "replay" && args[1] == "meta" -> runReplayMeta(args[2])
        args[0] == "replay" && args[1] == "verify" -> runReplayVerify(args.drop(2))
        args[0] == "map" && args[1] == "validate" -> runMapValidate(args[2])
        args[0] == "map" && args[1] == "generate" -> runMapGenerate(args.drop(2))
        args[0] == "data" && args[1] == "validate" -> runDataValidate(args.drop(2))
        else -> {
            printUsage()
            1
        }
    }
}

private fun runReplayMeta(pathArg: String): Int {
    val path = resolvePath(pathArg)
    val metadata = ReplayIO.inspect(path)
    println(formatReplayMetadata(path, metadata))
    return 0
}

private fun runReplayVerify(args: List<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    val path = resolvePath(args[0])
    val strictHash = args.drop(1).any { it == "--strictHash" }
    val commands = try {
        ReplayIO.load(path, strictHash = strictHash)
    } catch (e: IllegalStateException) {
        println("replay: $path")
        println("result: validation-error")
        println("error: ${e.message}")
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
    println("replay: $path")
    println("commands: ${commands.size}")
    println("expectedHash: ${expectedHash ?: "missing"}")
    println("computedHash: $computedHash")
    println("result: $result")
    return if (result == "mismatch" || result == "missing-hash") 2 else 0
}

private fun runMapValidate(pathArg: String): Int {
    val path = resolvePath(pathArg)
    val result = validateMap(path)
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
    val payload = generateMap(outPath, width, height, seed)
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
    val result = validateDataDir(dir)
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
          replay meta <path>
          replay verify <path> [--strictHash]
          map validate <path>
          map generate <path> [--width N] [--height N] [--seed N]
          data validate --dir <path>
        """.trimIndent()
    )
}
