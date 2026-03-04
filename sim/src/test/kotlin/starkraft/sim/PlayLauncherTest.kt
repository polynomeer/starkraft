package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.PlayPaths
import starkraft.sim.client.PlayScenario
import starkraft.sim.client.buildPlayClientCommand
import starkraft.sim.client.buildPlaySimCommand
import starkraft.sim.client.defaultPlayPaths
import starkraft.sim.client.resetPlayFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PlayLauncherTest {
    @Test
    fun `builds default play paths under root`() {
        val root = Paths.get("/tmp/starkraft/play")

        val paths = defaultPlayPaths(root)

        assertEquals(Paths.get("/tmp/starkraft/play/snapshots.ndjson"), paths.snapshots)
        assertEquals(Paths.get("/tmp/starkraft/play/client-input.ndjson"), paths.input)
    }

    @Test
    fun `builds sim command for one command play`() {
        val command =
            buildPlaySimCommand(
                javaBin = "/java",
                classpath = "/cp",
                snapshotPath = Paths.get("/tmp/snapshots.ndjson"),
                inputPath = Paths.get("/tmp/client-input.ndjson"),
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
                "--noSleep",
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
                inputPath = Paths.get("/tmp/client-input.ndjson")
            )

        assertEquals(
            listOf(
                "/java",
                "-cp",
                "/cp",
                "starkraft.sim.client.GraphicalClientKt",
                "/tmp/snapshots.ndjson",
                "/tmp/client-input.ndjson"
            ),
            command
        )
    }

    @Test
    fun `reset play files creates fresh workspace files`(@TempDir tempDir: Path) {
        val paths = PlayPaths(tempDir, tempDir.resolve("snapshots.ndjson"), tempDir.resolve("client-input.ndjson"))
        Files.writeString(paths.snapshots, "stale")
        Files.writeString(paths.input, "stale")

        resetPlayFiles(paths)

        assertTrue(Files.exists(paths.snapshots))
        assertTrue(Files.exists(paths.input))
        assertEquals("", Files.readString(paths.snapshots))
        assertEquals("", Files.readString(paths.input))
    }
}
