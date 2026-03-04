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
    val control: Path
)

internal fun defaultPlayPaths(root: Path): PlayPaths =
    PlayPaths(
        root = root,
        snapshots = root.resolve("snapshots.ndjson"),
        input = root.resolve("client-input.ndjson"),
        control = root.resolve("play-control.txt")
    )

internal enum class PlayScenario(val id: String, val simArgs: List<String>) {
    SKIRMISH("skirmish", emptyList()),
    ECONOMY("economy", listOf("--script", "sim/scripts/harvest-depot.script")),
    GAS("gas", listOf("--script", "sim/scripts/harvest-gas-depot.script")),
    SCRIPTED("scripted", listOf("--spawnScript", "sim/scripts/spawn.script", "--script", "sim/scripts/sample.script"));

    companion object {
        fun fromId(id: String?): PlayScenario =
            entries.firstOrNull { it.id == id } ?: error("unknown scenario '${id ?: ""}'")
    }
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
    controlPath: Path
): List<String> =
    listOf(
        javaBin,
        "-cp",
        classpath,
        "starkraft.sim.client.GraphicalClientKt",
        snapshotPath.toString(),
        inputPath.toString(),
        controlPath.toString()
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
    val javaBin = currentJavaBinary()
    val classpath = currentMainClasspath()

    while (true) {
        resetPlayFiles(paths)
        val simProcess =
            ProcessBuilder(buildPlaySimCommand(javaBin, classpath, paths.snapshots, paths.input, paths.control, ticks, scenario))
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
            val clientProcess =
                ProcessBuilder(buildPlayClientCommand(javaBin, classpath, paths.snapshots, paths.input, paths.control))
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
