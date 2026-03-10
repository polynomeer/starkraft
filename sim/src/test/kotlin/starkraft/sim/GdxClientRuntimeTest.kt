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
    fun `changing scenario marks restart required and enterMatch restarts`(@TempDir tempDir: Path) {
        var restarted = false
        var opened = false
        val runtime = runtime(tempDir, onRestart = { restarted = true })

        runtime.cycleScenario(1)
        runtime.enterMatch { opened = true }

        assertTrue(runtime.scenarioRestartRequired())
        assertTrue(restarted)
        assertFalse(opened)
        assertTrue(runtime.mainMenuSummaryLines().any { it.contains("restart required") })
    }

    @Test
    fun `enterMatch opens game immediately when scenario is live`(@TempDir tempDir: Path) {
        var restarted = false
        var opened = false
        val runtime = runtime(tempDir, onRestart = { restarted = true })

        runtime.enterMatch { opened = true }

        assertFalse(runtime.scenarioRestartRequired())
        assertFalse(restarted)
        assertTrue(opened)
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
    fun `initial camera centers on the viewed faction`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)

        runtime.ensureInitialCamera(viewWidth = 1280, viewHeight = 720)

        assertEquals(510f, runtime.camera.panX)
        assertEquals(230f, runtime.camera.panY)
    }

    @Test
    fun `camera is clamped back inside the map bounds`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)
        runtime.camera = runtime.camera.copy(panX = 500f, panY = 500f)

        runtime.constrainCamera(viewWidth = 640, viewHeight = 480)

        assertEquals(0f, runtime.camera.panX)
        assertEquals(0f, runtime.camera.panY)
    }

    @Test
    fun `view auto-recovers to a faction with vision`(@TempDir tempDir: Path) {
        val runtime =
            runtime(
                tempDir,
                snapshot =
                    ClientSnapshot(
                        tick = 11,
                        mapId = "demo-map",
                        buildVersion = "test-build",
                        mapWidth = 32,
                        mapHeight = 32,
                        factions =
                            listOf(
                                FactionSnapshot(faction = 1, visibleTiles = 0),
                                FactionSnapshot(faction = 2, visibleTiles = 24)
                            ),
                        entities =
                            listOf(
                                EntitySnapshot(id = 8, faction = 2, typeId = "Marine", archetype = "infantry", x = 20f, y = 20f, dir = 0f, hp = 45, maxHp = 45, armor = 0, weaponId = "Gauss")
                            ),
                        resourceNodes = emptyList()
                    )
            )

        runtime.ensurePlayableView(viewWidth = 1280, viewHeight = 720)

        assertEquals(2, runtime.session.state.viewedFaction)
        assertTrue(runtime.noticeLine()?.contains("auto-switched") == true)
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

    @Test
    fun `button deck keeps advanced filters behind debug mode`(@TempDir tempDir: Path) {
        val runtime = runtime(tempDir)
        runtime.session.state.selectedIds.add(4)
        runtime.session.refreshViewState()

        val defaultButtons = runtime.buttonModels().map { it.actionId }

        assertTrue("move" in defaultButtons)
        assertTrue("clear" in defaultButtons)
        assertFalse("selectType" in defaultButtons)
        assertFalse("selectRole" in defaultButtons)

        runtime.toggleDebug()
        val debugButtons = runtime.buttonModels().map { it.actionId }

        assertTrue("selectType" in debugButtons)
        assertTrue("selectRole" in debugButtons)
    }

    private fun runtime(
        tempDir: Path,
        onRestart: () -> Unit = {},
        snapshot: ClientSnapshot =
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
    ): GdxClientRuntime {
        val snapshotPath = tempDir.resolve("snapshots.ndjson")
        val inputPath = tempDir.resolve("client-input.ndjson")
        val controlPath = tempDir.resolve("play-control.txt")
        val scenarioPath = tempDir.resolve("play-scenario.txt")
        Files.writeString(controlPath, "paused=0\nspeed=1\n")
        Files.writeString(scenarioPath, "skirmish\n")
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
            requestRestart = onRestart
        )
    }
}
