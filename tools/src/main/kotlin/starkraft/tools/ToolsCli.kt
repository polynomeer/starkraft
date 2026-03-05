package starkraft.tools

import starkraft.sim.replay.ReplayIO
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(buildUsage())
        return
    }
    when (args[0]) {
        "replay" -> runReplayCommand(args.drop(1))
        else -> error("Unknown command '${args[0]}'")
    }
}

private fun runReplayCommand(args: List<String>) {
    if (args.isEmpty()) error("Missing replay subcommand")
    when (args[0]) {
        "meta" -> {
            val path = parsePathArg(args, 1, "replay")
            val meta = ReplayIO.inspect(path)
            println("schema=${meta.schema}")
            println("legacy=${meta.legacy}")
            println("events=${meta.eventCount}")
            println("sizeBytes=${meta.fileSizeBytes}")
            println("replayHash=${meta.replayHash?.toString() ?: "missing"}")
            println("seed=${meta.seed?.toString() ?: "missing"}")
            println("mapId=${meta.mapId ?: "missing"}")
            println("buildVersion=${meta.buildVersion ?: "missing"}")
        }
        "fast-forward" -> {
            val path = parsePathArg(args, 1, "replay")
            val ticks = parseOptionalIntFlag(args.drop(2), "--ticks")
            val result = runReplay(path, tickLimit = ticks)
            println("finalTick=${result.finalTick}")
            println("worldHash=${result.worldHash}")
        }
        "verify" -> {
            val path = parsePathArg(args, 1, "replay")
            val extra = args.drop(2)
            val ticks = parseOptionalIntFlag(extra, "--ticks")
            val strictHash = extra.contains("--strictHash")
            val result = verifyReplay(path, tickLimit = ticks, strictHash = strictHash)
            println("replayHashStatus=${if (result.replayHashMatches) "ok" else "mismatch"}")
            println("recordedReplayHash=${result.recordedReplayHash?.toString() ?: "missing"}")
            println("computedReplayHash=${result.computedReplayHash}")
            println("finalTick=${result.finalTick}")
            println("worldHash=${result.worldHash}")
            if (!result.replayHashMatches) {
                exitProcess(1)
            }
        }
        else -> error("Unknown replay subcommand '${args[0]}'")
    }
}

private fun parsePathArg(args: List<String>, index: Int, label: String): Path {
    val raw = args.getOrNull(index) ?: error("Missing $label path")
    return Path.of(raw).toAbsolutePath().normalize()
}

private fun parseOptionalIntFlag(args: List<String>, name: String): Int? {
    val idx = args.indexOf(name)
    if (idx < 0) return null
    val raw = args.getOrNull(idx + 1) ?: error("Missing value for $name")
    return raw.toIntOrNull() ?: error("Invalid integer for $name: '$raw'")
}

private fun buildUsage(): String =
    """
    usage:
      replay meta <replay.json>
      replay fast-forward <replay.json> [--ticks N]
      replay verify <replay.json> [--ticks N] [--strictHash]
    """.trimIndent()
