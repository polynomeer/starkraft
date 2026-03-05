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
        "map" -> runMapCommand(args.drop(1))
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

private fun runMapCommand(args: List<String>) {
    if (args.isEmpty()) error("Missing map subcommand")
    when (args[0]) {
        "validate" -> {
            val path = parsePathArg(args, 1, "map")
            val map = loadToolMap(path)
            val result = validateToolMap(map)
            println("valid=${result.valid}")
            if (result.errors.isEmpty()) {
                println("errors=0")
            } else {
                println("errors=${result.errors.size}")
                result.errors.forEach { println("error: $it") }
                exitProcess(1)
            }
        }
        "generate" -> {
            val outPath = parsePathArg(args, 1, "output map")
            val tail = args.drop(2)
            val width = parseOptionalIntFlag(tail, "--width") ?: 64
            val height = parseOptionalIntFlag(tail, "--height") ?: 64
            val blocked = parseOptionalIntFlag(tail, "--blockedPct") ?: 8
            val weighted = parseOptionalIntFlag(tail, "--weightedPct") ?: 10
            val seed = parseOptionalLongFlag(tail, "--seed") ?: 1337L
            val id = parseOptionalStringFlag(tail, "--id") ?: "generated-${width}x$height-$seed"
            val map = generateToolMap(width, height, seed, blocked, weighted, id)
            saveToolMap(outPath, map)
            println("generated=${outPath.toAbsolutePath().normalize()}")
            println("id=${map.id}")
            println("size=${map.width}x${map.height}")
            println("blocked=${map.blockedTiles.size}")
            println("weighted=${map.weightedTiles.size}")
        }
        else -> error("Unknown map subcommand '${args[0]}'")
    }
}

private fun parseOptionalLongFlag(args: List<String>, name: String): Long? {
    val idx = args.indexOf(name)
    if (idx < 0) return null
    val raw = args.getOrNull(idx + 1) ?: error("Missing value for $name")
    return raw.toLongOrNull() ?: error("Invalid long for $name: '$raw'")
}

private fun parseOptionalStringFlag(args: List<String>, name: String): String? {
    val idx = args.indexOf(name)
    if (idx < 0) return null
    return args.getOrNull(idx + 1) ?: error("Missing value for $name")
}

private fun buildUsage(): String =
    """
    usage:
      replay meta <replay.json>
      replay fast-forward <replay.json> [--ticks N]
      replay verify <replay.json> [--ticks N] [--strictHash]
      map validate <map.json>
      map generate <map.json> [--width N --height N --seed S --blockedPct N --weightedPct N --id map-id]
    """.trimIndent()
