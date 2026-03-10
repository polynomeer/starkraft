package starkraft.sim

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import starkraft.sim.client.ClientSession
import starkraft.sim.client.ClientSessionState
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.GdxClientRuntime
import starkraft.sim.client.PlayControlState
import starkraft.sim.client.PlayScenario
import java.nio.file.Files
import java.nio.file.Path

class GdxClientRuntimeTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `togglePlayPause writes updated control file`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)

        runtime.togglePlayPause()
        runtime.adjustSpeed(2)

        assertEquals("paused=1\nspeed=3\n", Files.readString(tempDir.resolve("play-control.txt")))
    }

    @Test
    fun `save and load preset round trips scenario and control state`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)
        runtime.playScenario = PlayScenario.GAS
        runtime.playControlState = PlayControlState(paused = true, speed = 4)

        runtime.savePreset("quick")

        runtime.playScenario = PlayScenario.SKIRMISH
        runtime.playControlState = PlayControlState()
        runtime.loadPreset("quick")

        assertEquals(PlayScenario.GAS, runtime.playScenario)
        assertEquals(PlayControlState(paused = true, speed = 4), runtime.playControlState)
        assertTrue(runtime.isPresetAvailable("quick"))
        assertEquals("gas\n", Files.readString(tempDir.resolve("play-scenario.txt")))
        assertEquals("paused=1\nspeed=4\n", Files.readString(tempDir.resolve("play-control.txt")))
    }

    @Test
    fun `control groups assign recall and clear`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)
        runtime.session.state.selectedIds.addAll(listOf(4, 5))

        runtime.handleControlGroup(group = 4, assign = true, add = false, viewWidth = 800, viewHeight = 600)
        runtime.clearSelection()
        runtime.handleControlGroup(group = 4, assign = false, add = false, viewWidth = 800, viewHeight = 600)

        assertEquals(linkedSetOf(4, 5), runtime.session.state.selectedIds)
        assertTrue(runtime.controlGroupSummaryLine()?.contains("4=2") == true)

        runtime.clearControlGroups()

        assertFalse(runtime.controlGroupSummaryLine()?.contains("4=") == true)
    }

    @Test
    fun `minimap click recenters the camera`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)

        val centered = runtime.centerFromMinimap(screenX = 50f, screenY = 50f, viewWidth = 1280, viewHeight = 720)

        assertTrue(centered)
        assertTrue(runtime.camera.panX != 0f || runtime.camera.panY != 0f)
    }

    @Test
    fun `minimap click outside bounds is ignored`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)

        val centered = runtime.centerFromMinimap(screenX = 500f, screenY = 500f, viewWidth = 1280, viewHeight = 720)

        assertFalse(centered)
        assertEquals(0f, runtime.camera.panX)
        assertEquals(0f, runtime.camera.panY)
    }

    @Test
    fun `hover hint and menu summary expose current ui state`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)

        runtime.setHoverHint("Build Depot")

        assertTrue(runtime.currentHudLines().any { it == "hint: Build Depot" })
        assertTrue(runtime.mainMenuSummaryLines().any { it.contains("scenario: skirmish") })
        runtime.setHoverHint(null)
        assertNull(runtime.currentHudLines().firstOrNull { it.startsWith("hint:") })
    }

    private fun runtime(tempDir: Path): GdxClientRuntime {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")
        val controlPath = tempDir.resolve("play-control.txt")
        val scenarioPath = tempDir.resolve("play-scenario.txt")
        Files.writeString(controlPath, "paused=0\nspeed=1\n")
        Files.writeString(scenarioPath, "skirmish\n")

        val snapshot =
            ClientSnapshot(
                tick = 7,
                mapId = "demo-map",
                buildVersion = "test-build",
                mapWidth = 32,
                mapHeight = 32,
                factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 8)),
                entities =
                    listOf(
                        EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 6f, dir = 0f, hp = 45, maxHp = 45, armor = 0, weaponId = "Gauss"),
                        EntitySnapshot(id = 5, faction = 1, typeId = "Worker", archetype = "worker", x = 8f, y = 7f, dir = 0f, hp = 40, maxHp = 40, armor = 0)
                    ),
                resourceNodes = emptyList()
            )
        Files.writeString(
            snapshotPath,
            "{\"recordType\":\"snapshot\",\"snapshot\":${json.encodeToString(ClientSnapshot.serializer(), snapshot)}}\n"
        )

        val session = ClientSession(snapshotPath, inputPath, ClientSessionState())
        session.poll()
        return GdxClientRuntime(
            session = session,
            controlPath = controlPath,
            scenarioPath = scenarioPath,
            playRoot = tempDir,
            requestRestart = {}
        )
    }
}
