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
        """.trimIndent()
    )
}
