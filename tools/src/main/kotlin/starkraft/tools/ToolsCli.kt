package starkraft.tools

import starkraft.sim.replay.ReplayIO
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
        """.trimIndent()
    )
}
