package starkraft.sim.client

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories

data class PlayPaths(
    val root: Path,
    val snapshots: Path,
    val input: Path
)

internal fun defaultPlayPaths(root: Path): PlayPaths =
    PlayPaths(
        root = root,
        snapshots = root.resolve("snapshots.ndjson"),
        input = root.resolve("client-input.ndjson")
    )

internal fun currentJavaBinary(): String =
    File(System.getProperty("java.home"), "bin/java").absolutePath

internal fun currentMainClasspath(): String =
    System.getProperty("java.class.path")

internal fun buildPlaySimCommand(
    javaBin: String,
    classpath: String,
    snapshotPath: Path,
    inputPath: Path,
    ticks: Int
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
        "--noSleep",
        "--ticks",
        ticks.toString()
    )

internal fun buildPlayClientCommand(
    javaBin: String,
    classpath: String,
    snapshotPath: Path,
    inputPath: Path
): List<String> =
    listOf(
        javaBin,
        "-cp",
        classpath,
        "starkraft.sim.client.GraphicalClientKt",
        snapshotPath.toString(),
        inputPath.toString()
    )

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

    root.createDirectories()
    val paths = defaultPlayPaths(root)
    val javaBin = currentJavaBinary()
    val classpath = currentMainClasspath()

    val simProcess =
        ProcessBuilder(buildPlaySimCommand(javaBin, classpath, paths.snapshots, paths.input, ticks))
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
            ProcessBuilder(buildPlayClientCommand(javaBin, classpath, paths.snapshots, paths.input))
                .inheritIO()
                .start()
        clientProcess.waitFor()
    } finally {
        if (simProcess.isAlive) {
            simProcess.destroy()
            simProcess.waitFor()
        }
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}
