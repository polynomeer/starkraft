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
        "data" -> runDataCommand(args.drop(1))
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
    return resolveCliPath(raw)
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

private fun runDataCommand(args: List<String>) {
    if (args.isEmpty()) error("Missing data subcommand")
    when (args[0]) {
        "validate" -> {
            val tail = args.drop(1)
            val dir = parseOptionalStringFlag(tail, "--dir")?.let { resolveCliPath(it) }
            val units = parseDataPath(tail, "--units", dir, "units")
            val weapons = parseDataPath(tail, "--weapons", dir, "weapons")
            val buildings = parseDataPath(tail, "--buildings", dir, "buildings")
            val techs = parseDataPath(tail, "--techs", dir, "techs")
            val result =
                validateGameData(
                    DataValidationInput(
                        unitsPath = units,
                        weaponsPath = weapons,
                        buildingsPath = buildings,
                        techsPath = techs
                    )
                )
            println("valid=${result.valid}")
            if (result.errors.isEmpty()) {
                println("errors=0")
            } else {
                println("errors=${result.errors.size}")
                result.errors.forEach { println("error: $it") }
                exitProcess(1)
            }
        }
        else -> error("Unknown data subcommand '${args[0]}'")
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

private fun parseDataPath(args: List<String>, flag: String, dir: Path?, stem: String): Path {
    val explicit = parseOptionalStringFlag(args, flag)
    if (explicit != null) return resolveCliPath(explicit)
    val base = dir ?: error("Missing $flag and --dir was not provided")
    val jsonPath = base.resolve("$stem.json")
    if (java.nio.file.Files.exists(jsonPath)) return jsonPath.toAbsolutePath().normalize()
    val yamlPath = base.resolve("$stem.yaml")
    if (java.nio.file.Files.exists(yamlPath)) return yamlPath.toAbsolutePath().normalize()
    val ymlPath = base.resolve("$stem.yml")
    if (java.nio.file.Files.exists(ymlPath)) return ymlPath.toAbsolutePath().normalize()
    error("Could not resolve '$stem' data file from --dir $base")
}

private fun resolveCliPath(raw: String): Path {
    val path = Path.of(raw)
    if (path.isAbsolute) return path.normalize()
    val cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val root = resolveProjectRoot(cwd)
    val rootCandidate = root.resolve(path).normalize()
    if (java.nio.file.Files.exists(rootCandidate) || !java.nio.file.Files.exists(cwd.resolve(path))) {
        return rootCandidate
    }
    return cwd.resolve(path).normalize()
}

private fun resolveProjectRoot(start: Path): Path {
    var current: Path? = start
    while (current != null) {
        if (java.nio.file.Files.exists(current.resolve("settings.gradle.kts"))) return current
        current = current.parent
    }
    return start
}

private fun buildUsage(): String =
    """
    usage:
      replay meta <replay.json>
      replay fast-forward <replay.json> [--ticks N]
      replay verify <replay.json> [--ticks N] [--strictHash]
      map validate <map.json>
      map generate <map.json> [--width N --height N --seed S --blockedPct N --weightedPct N --id map-id]
      data validate (--dir <data-dir> | --units <path> --weapons <path> --buildings <path> --techs <path>)
    """.trimIndent()
