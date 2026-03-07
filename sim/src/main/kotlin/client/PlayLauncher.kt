package starkraft.sim.client

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories

data class PlayPaths(
    val root: Path,
    val snapshots: Path,
    val input: Path,
    val control: Path,
    val scenario: Path,
    val presetsDir: Path
)

internal fun defaultPlayPaths(root: Path): PlayPaths =
    PlayPaths(
        root = root,
        snapshots = root.resolve("snapshots.ndjson"),
        input = root.resolve("client-input.ndjson"),
        control = root.resolve("play-control.txt"),
        scenario = root.resolve("play-scenario.txt"),
        presetsDir = root.resolve("presets")
    )

internal enum class PlayScenario(val id: String, val simArgs: List<String>) {
    SKIRMISH("skirmish", emptyList()),
    ECONOMY("economy", listOf("--script", "sim/scripts/harvest-depot.script")),
    GAS("gas", listOf("--script", "sim/scripts/harvest-gas-depot.script")),
    SCRIPTED("scripted", listOf("--spawnScript", "sim/scripts/spawn.script", "--script", "sim/scripts/sample.script"));

    companion object {
        fun fromId(id: String?): PlayScenario =
            entries.firstOrNull { it.id == id } ?: error("unknown scenario '${id ?: ""}'")

        fun cycle(current: PlayScenario, delta: Int): PlayScenario {
            val nextIndex = Math.floorMod(current.ordinal + delta, entries.size)
            return entries[nextIndex]
        }
    }
}

internal fun writePlayScenario(path: Path, scenario: PlayScenario) {
    Files.writeString(path, scenario.id + "\n")
}

internal fun readPlayScenario(path: Path, fallback: PlayScenario): PlayScenario {
    if (!Files.exists(path)) return fallback
    val id = Files.readString(path).lineSequence().firstOrNull()?.trim().orEmpty()
    return runCatching { PlayScenario.fromId(id) }.getOrDefault(fallback)
}

internal data class PlayPresetState(
    val scenario: PlayScenario,
    val control: PlayControlState
)

internal fun presetFilePath(presetsDir: Path, name: String): Path =
    presetsDir.resolve("$name.play")

internal fun renderPlayPreset(state: PlayPresetState): String =
    buildString {
        append("scenario=")
        append(state.scenario.id)
        append('\n')
        append(renderPlayControlState(state.control))
    }

internal fun parsePlayPreset(text: String, fallbackScenario: PlayScenario): PlayPresetState {
    var scenario = fallbackScenario
    val controlLines = ArrayList<String>()
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.startsWith("scenario=")) {
            scenario = runCatching { PlayScenario.fromId(trimmed.substringAfter('=')) }.getOrDefault(fallbackScenario)
        } else {
            controlLines.add(trimmed)
        }
    }
    return PlayPresetState(scenario = scenario, control = parsePlayControlState(controlLines.joinToString("\n")))
}

internal fun savePlayPreset(presetsDir: Path, name: String, state: PlayPresetState) {
    Files.createDirectories(presetsDir)
    Files.writeString(presetFilePath(presetsDir, name), renderPlayPreset(state))
}

internal fun loadPlayPreset(presetsDir: Path, name: String, fallbackScenario: PlayScenario): PlayPresetState? {
    val path = presetFilePath(presetsDir, name)
    if (!Files.exists(path)) return null
    return parsePlayPreset(Files.readString(path), fallbackScenario)
}

internal fun currentJavaBinary(): String =
    File(System.getProperty("java.home"), "bin/java").absolutePath

internal fun currentMainClasspath(): String =
    System.getProperty("java.class.path")

internal fun buildPlaySimCommand(
    javaBin: String,
    classpath: String,
    snapshotPath: Path,
    inputPath: Path,
    controlPath: Path,
    ticks: Int,
    scenario: PlayScenario
): List<String> =
    listOf(
        javaBin,
        "-cp",
        classpath,
        "starkraft.sim.AppKt",
        "--snapshotEvery",
        "1",
        "--snapshotOut",
        snapshotPath.toString(),
        "--inputTail",
        inputPath.toString(),
        "--playControlFile",
        controlPath.toString(),
        "--ticks",
        ticks.toString()
    ) + scenario.simArgs

internal fun buildPlayClientCommand(
    javaBin: String,
    classpath: String,
    snapshotPath: Path,
    inputPath: Path,
    controlPath: Path,
    scenarioPath: Path,
    rootPath: Path
): List<String> =
    listOf(
        javaBin,
        "-cp",
        classpath,
        "starkraft.sim.client.GraphicalClientKt",
        snapshotPath.toString(),
        inputPath.toString(),
        controlPath.toString(),
        scenarioPath.toString(),
        rootPath.toString()
    )

internal fun resetPlayFiles(paths: PlayPaths) {
    Files.deleteIfExists(paths.snapshots)
    Files.deleteIfExists(paths.input)
    Files.deleteIfExists(paths.control)
    Files.createFile(paths.snapshots)
    Files.createFile(paths.input)
    Files.writeString(paths.control, renderPlayControlState(PlayControlState()))
}

internal fun shouldRestartPlay(exitCode: Int): Boolean = exitCode == CLIENT_EXIT_RESTART

internal fun waitForInitialSnapshot(
    simProcess: Process,
    snapshotPath: Path,
    timeoutMs: Long = 5000L,
    pollMs: Long = 25L
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(pollMs)
    while (System.currentTimeMillis() <= deadline) {
        if (Files.exists(snapshotPath) && runCatching { Files.size(snapshotPath) > 0L }.getOrDefault(false)) {
            return true
        }
        if (!simProcess.isAlive) {
            return Files.exists(snapshotPath) && runCatching { Files.size(snapshotPath) > 0L }.getOrDefault(false)
        }
        Thread.sleep(pollMs.coerceAtLeast(1L))
    }
    return Files.exists(snapshotPath) && runCatching { Files.size(snapshotPath) > 0L }.getOrDefault(false)
}

fun main(args: Array<String>) {
    val root =
        if (args.isNotEmpty()) {
            Paths.get(args[0]).toAbsolutePath().normalize()
        } else {
            Paths.get(System.getProperty("java.io.tmpdir"), "starkraft", "play").toAbsolutePath().normalize()
        }
    val ticks =
        if (args.size >= 2) {
            args[1].toIntOrNull()?.takeIf { it > 0 } ?: error("ticks must be a positive integer")
        } else {
            5000
        }
    val scenario =
        if (args.size >= 3) {
            PlayScenario.fromId(args[2])
        } else {
            PlayScenario.SKIRMISH
        }

    root.createDirectories()
    val paths = defaultPlayPaths(root)
    Files.createDirectories(paths.presetsDir)
    writePlayScenario(paths.scenario, scenario)
    val javaBin = currentJavaBinary()
    val classpath = currentMainClasspath()

    while (true) {
        val activeScenario = readPlayScenario(paths.scenario, scenario)
        resetPlayFiles(paths)
        val simProcess =
            ProcessBuilder(buildPlaySimCommand(javaBin, classpath, paths.snapshots, paths.input, paths.control, ticks, activeScenario))
                .inheritIO()
                .start()

        val shutdownHook = Thread {
            if (simProcess.isAlive) {
                simProcess.destroy()
                simProcess.waitFor()
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            if (!waitForInitialSnapshot(simProcess, paths.snapshots)) {
                val exitCode = if (simProcess.isAlive) null else simProcess.exitValue()
                if (simProcess.isAlive) {
                    simProcess.destroy()
                    simProcess.waitFor()
                }
                error("sim startup failed: no snapshots produced within 5s (exitCode=$exitCode)")
            }
            val clientProcess =
                ProcessBuilder(buildPlayClientCommand(javaBin, classpath, paths.snapshots, paths.input, paths.control, paths.scenario, paths.root))
                    .inheritIO()
                    .start()
            val exitCode = clientProcess.waitFor()
            if (!shouldRestartPlay(exitCode)) {
                return
            }
        } finally {
            if (simProcess.isAlive) {
                simProcess.destroy()
                simProcess.waitFor()
            }
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        }
    }
}
