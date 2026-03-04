package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.client.buildPlayClientCommand
import starkraft.sim.client.buildPlaySimCommand
import starkraft.sim.client.defaultPlayPaths
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
                ticks = 5000
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
}
