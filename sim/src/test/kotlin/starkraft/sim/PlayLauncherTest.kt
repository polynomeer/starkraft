package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.PlayPaths
import starkraft.sim.client.PlayPresetState
import starkraft.sim.client.PlayScenario
import starkraft.sim.client.CLIENT_EXIT_RESTART
import starkraft.sim.client.buildPlayClientCommand
import starkraft.sim.client.buildPlaySimCommand
import starkraft.sim.client.loadPlayPreset
import starkraft.sim.client.parsePlayControlState
import starkraft.sim.client.parsePlayPreset
import starkraft.sim.client.presetFilePath
import starkraft.sim.client.defaultPlayPaths
import starkraft.sim.client.readPlayScenario
import starkraft.sim.client.resetPlayFiles
import starkraft.sim.client.savePlayPreset
import starkraft.sim.client.renderPlayPreset
import starkraft.sim.client.renderPlayControlState
import starkraft.sim.client.shouldRestartPlay
import starkraft.sim.client.waitForInitialSnapshot
import starkraft.sim.client.writePlayScenario
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class PlayLauncherTest {
    @Test
    fun `builds default play paths under root`() {
        val root = Paths.get("/tmp/starkraft/play")

        val paths = defaultPlayPaths(root)

        assertEquals(Paths.get("/tmp/starkraft/play/snapshots.ndjson"), paths.snapshots)
        assertEquals(Paths.get("/tmp/starkraft/play/client-input.ndjson"), paths.input)
        assertEquals(Paths.get("/tmp/starkraft/play/play-control.txt"), paths.control)
        assertEquals(Paths.get("/tmp/starkraft/play/play-scenario.txt"), paths.scenario)
        assertEquals(Paths.get("/tmp/starkraft/play/presets"), paths.presetsDir)
        assertEquals(Paths.get("/tmp/starkraft/play/presets/quick.play"), presetFilePath(paths.presetsDir, "quick"))
    }

    @Test
    fun `builds sim command for one command play`() {
        val command =
            buildPlaySimCommand(
                javaBin = "/java",
                classpath = "/cp",
                snapshotPath = Paths.get("/tmp/snapshots.ndjson"),
                inputPath = Paths.get("/tmp/client-input.ndjson"),
                controlPath = Paths.get("/tmp/play-control.txt"),
                ticks = 5000,
                scenario = PlayScenario.SKIRMISH
            )

        assertEquals(
            listOf(
                "/java",
                "-cp",
                "/cp",
                "starkraft.sim.AppKt",
                "--snapshotEvery",
                "1",
                "--snapshotOut",
                "/tmp/snapshots.ndjson",
                "--inputTail",
                "/tmp/client-input.ndjson",
                "--playControlFile",
                "/tmp/play-control.txt",
                "--ticks",
                "5000"
            ),
            command
        )
    }

    @Test
    fun `builds scenario specific sim command`() {
        val command =
            buildPlaySimCommand(
                javaBin = "/java",
                classpath = "/cp",
                snapshotPath = Paths.get("/tmp/snapshots.ndjson"),
                inputPath = Paths.get("/tmp/client-input.ndjson"),
                controlPath = Paths.get("/tmp/play-control.txt"),
                ticks = 1000,
                scenario = PlayScenario.SCRIPTED
            )

        assertTrue(command.containsAll(listOf("--spawnScript", "sim/scripts/spawn.script", "--script", "sim/scripts/sample.script")))
    }

    @Test
    fun `builds client command for one command play`() {
        val command =
            buildPlayClientCommand(
                javaBin = "/java",
                classpath = "/cp",
                snapshotPath = Paths.get("/tmp/snapshots.ndjson"),
                inputPath = Paths.get("/tmp/client-input.ndjson"),
                controlPath = Paths.get("/tmp/play-control.txt"),
                scenarioPath = Paths.get("/tmp/play-scenario.txt"),
                rootPath = Paths.get("/tmp/play-root")
            )

        assertEquals(
            listOf(
                "/java",
                "-cp",
                "/cp",
                "starkraft.sim.client.GraphicalClientKt",
                "/tmp/snapshots.ndjson",
                "/tmp/client-input.ndjson",
                "/tmp/play-control.txt",
                "/tmp/play-scenario.txt",
                "/tmp/play-root"
            ),
            command
        )
    }

    @Test
    fun `reset play files creates fresh workspace files`(@TempDir tempDir: Path) {
        val paths =
            PlayPaths(
                tempDir,
                tempDir.resolve("snapshots.ndjson"),
                tempDir.resolve("client-input.ndjson"),
                tempDir.resolve("play-control.txt"),
                tempDir.resolve("play-scenario.txt"),
                tempDir.resolve("presets")
            )
        Files.writeString(paths.snapshots, "stale")
        Files.writeString(paths.input, "stale")
        Files.writeString(paths.control, "stale")
        Files.writeString(paths.scenario, "gas\n")

        resetPlayFiles(paths)

        assertTrue(Files.exists(paths.snapshots))
        assertTrue(Files.exists(paths.input))
        assertTrue(Files.exists(paths.control))
        assertTrue(Files.exists(paths.scenario))
        assertTrue(Files.notExists(paths.presetsDir.resolve("quick.play")))
        assertEquals("", Files.readString(paths.snapshots))
        assertEquals("", Files.readString(paths.input))
        assertEquals(PlayScenario.SKIRMISH.id, PlayScenario.fromId("skirmish").id)
        assertEquals("paused=0\nspeed=1\n", Files.readString(paths.control))
        assertEquals("gas\n", Files.readString(paths.scenario))
    }

    @Test
    fun `restart exit code is recognized`() {
        assertTrue(shouldRestartPlay(CLIENT_EXIT_RESTART))
        assertTrue(!shouldRestartPlay(0))
    }

    @Test
    fun `waitForInitialSnapshot detects written snapshot`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        Files.createFile(snapshotPath)
        val process = ProcessBuilder("bash", "-lc", "sleep 1").start()
        try {
            Files.writeString(snapshotPath, "{\"recordType\":\"snapshot\"}\n")
            assertTrue(waitForInitialSnapshot(process, snapshotPath, timeoutMs = 300L, pollMs = 5L))
        } finally {
            process.destroy()
            process.waitFor(200, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `waitForInitialSnapshot returns false when process exits without snapshots`(@TempDir tempDir: Path) {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        Files.createFile(snapshotPath)
        val process = ProcessBuilder("bash", "-lc", "exit 0").start()
        process.waitFor(200, TimeUnit.MILLISECONDS)
        assertTrue(!waitForInitialSnapshot(process, snapshotPath, timeoutMs = 200L, pollMs = 5L))
    }

    @Test
    fun `play control state round trips`() {
        val text = renderPlayControlState(starkraft.sim.client.PlayControlState(paused = true, speed = 3))

        assertEquals(starkraft.sim.client.PlayControlState(paused = true, speed = 3), parsePlayControlState(text))
    }

    @Test
    fun `play scenario file round trips`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("play-scenario.txt")

        writePlayScenario(path, PlayScenario.GAS)

        assertEquals(PlayScenario.GAS, readPlayScenario(path, PlayScenario.SKIRMISH))
        Files.writeString(path, "unknown\n")
        assertEquals(PlayScenario.SKIRMISH, readPlayScenario(path, PlayScenario.SKIRMISH))
    }

    @Test
    fun `play scenario cycles in a loop`() {
        assertEquals(PlayScenario.ECONOMY, PlayScenario.cycle(PlayScenario.SKIRMISH, 1))
        assertEquals(PlayScenario.SCRIPTED, PlayScenario.cycle(PlayScenario.SKIRMISH, -1))
    }

    @Test
    fun `play preset round trips`(@TempDir tempDir: Path) {
        val preset = PlayPresetState(PlayScenario.GAS, starkraft.sim.client.PlayControlState(paused = true, speed = 3))

        savePlayPreset(tempDir, "quick", preset)

        assertEquals(tempDir.resolve("quick.play"), presetFilePath(tempDir, "quick"))
        assertEquals(preset, loadPlayPreset(tempDir, "quick", PlayScenario.SKIRMISH))
        assertEquals(preset, parsePlayPreset(renderPlayPreset(preset), PlayScenario.SKIRMISH))
        assertEquals(null, loadPlayPreset(tempDir, "missing", PlayScenario.SKIRMISH))
    }
}
