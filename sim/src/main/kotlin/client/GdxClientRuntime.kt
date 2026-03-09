package starkraft.sim.client

import starkraft.sim.net.InputJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.floor

internal class GdxClientRuntime(
    val session: ClientSession,
    private val controlPath: Path?,
    private val scenarioPath: Path?,
    private val playRoot: Path?,
    private val requestRestart: () -> Unit
) {
    private val requestIds = ClientCommandIds("gdx")
    private var noticeMessage: String? = null
    private var noticeUntilMillis: Long = 0L

    val catalog: ClientCatalog = defaultClientCatalog()
    var camera: CameraView = CameraView()
    var groundMode: ClientGroundCommandMode? = null
    var buildModeTypeId: String? = null
    var playControlState: PlayControlState =
        if (controlPath != null && Files.exists(controlPath)) {
            parsePlayControlState(Files.readString(controlPath))
        } else {
            PlayControlState()
        }
    var playScenario: PlayScenario =
        if (scenarioPath != null) {
            readPlayScenario(scenarioPath, PlayScenario.SKIRMISH)
        } else {
            PlayScenario.SKIRMISH
        }
    var debugVisible: Boolean = false
    var pauseOverlayVisible: Boolean = false

    val snapshot: ClientSnapshot?
        get() = session.state.snapshot

    fun tick() {
        session.poll()
        if (noticeMessage != null && System.currentTimeMillis() > noticeUntilMillis) {
            noticeMessage = null
        }
    }

    fun noticeLine(): String? = noticeMessage?.let { "notice: $it" }

    fun overlayModeLabel(): String =
        buildModeTypeId?.let { "build:$it" }
            ?: groundMode?.name?.lowercase()?.replace('_', '-')
            ?: "default"

    fun playStateLabel(): String = formatPlayControlOverlay(playControlState)

    fun currentHudLines(): List<String> {
        val snapshot = session.state.snapshot ?: return listOf("waiting for snapshots...")
        val lines = buildClientHudLines(snapshot, session.state).toMutableList()
        lines.add(0, "mode=${overlayModeLabel()} view=${session.state.viewedFaction?.let { "f$it" } ?: "observer"}")
        lines.add(1, playStateLabel())
        lines.add(2, "scenario=${playScenario.id}")
        noticeLine()?.let(lines::add)
        return lines
    }

    fun issueLeftClick(screenX: Float, screenY: Float, additiveSelection: Boolean) {
        val snapshot = session.state.snapshot ?: return
        val intent =
            buildClientIntent(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                viewedFaction = session.state.viewedFaction,
                worldX = camera.screenToWorldX(screenX),
                worldY = camera.screenToWorldY(screenY),
                leftClick = true,
                rightClick = false,
                attackMoveModifier = false,
                additiveSelection = additiveSelection,
                requestIds = requestIds
            ) ?: return
        session.append(intent)
        session.refreshViewState()
    }

    fun issueSelectionBox(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        additiveSelection: Boolean
    ) {
        val snapshot = session.state.snapshot ?: return
        val intent =
            selectEntitiesInBox(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                viewedFaction = session.state.viewedFaction,
                startWorldX = camera.screenToWorldX(startX),
                startWorldY = camera.screenToWorldY(startY),
                endWorldX = camera.screenToWorldX(endX),
                endWorldY = camera.screenToWorldY(endY),
                additiveSelection = additiveSelection
            )
        session.append(intent)
        session.refreshViewState()
    }

    fun issueRightClick(screenX: Float, screenY: Float, attackMoveModifier: Boolean) {
        val snapshot = session.state.snapshot ?: return
        if (buildModeTypeId != null) {
            placeBuildingAt(screenX, screenY)
            return
        }
        val intent =
            buildClientIntent(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                viewedFaction = session.state.viewedFaction,
                worldX = camera.screenToWorldX(screenX),
                worldY = camera.screenToWorldY(screenY),
                leftClick = false,
                rightClick = true,
                attackMoveModifier = attackMoveModifier,
                forcedGroundCommandType = groundMode?.commandType,
                additiveSelection = false,
                requestIds = requestIds
            ) ?: return
        if (intent is ClientIntent.Command) {
            session.append(intent)
            groundMode = null
        }
    }

    fun placeBuildingAt(screenX: Float, screenY: Float) {
        val snapshot = session.state.snapshot ?: return
        val mapState = session.state.mapState ?: return
        val typeId = buildModeTypeId ?: return
        val spec = buildPreviewSpec(typeId) ?: return
        val tileX = floor(camera.screenToWorldX(screenX)).toInt()
        val tileY = floor(camera.screenToWorldY(screenY)).toInt()
        if (!isBuildPreviewValid(mapState, snapshot, spec, tileX, tileY)) {
            showNotice("invalid build placement")
            return
        }
        session.append(
            ClientIntent.Command(
                InputJson.InputCommandRecord(
                    tick = snapshot.tick + 1,
                    commandType = "build",
                    requestId = requestIds.nextRequestId(),
                    faction = session.state.viewedFaction ?: 1,
                    typeId = typeId,
                    tileX = tileX,
                    tileY = tileY
                )
            )
        )
        buildModeTypeId = null
    }

    fun setViewFaction(faction: Int?) {
        session.state.viewedFaction = faction
    }

    fun clearSelection() {
        session.clearSelection()
        groundMode = null
        buildModeTypeId = null
    }

    fun centerOnSelection(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val ids = session.state.selectedIds.toIntArray()
        val focus = computeSelectionCentroid(snapshot, ids) ?: return
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
    }

    fun centerOnViewedFaction(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction ?: return
        val ids = collectFactionSelectionIds(snapshot, faction)
        val focus = computeSelectionCentroid(snapshot, ids) ?: return
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
    }

    fun zoomAt(screenX: Float, screenY: Float, factor: Float) {
        camera = zoomCameraAt(camera, screenX, screenY, factor)
    }

    fun panBy(deltaX: Float, deltaY: Float) {
        camera = camera.copy(panX = camera.panX + deltaX, panY = camera.panY + deltaY)
    }

    fun togglePauseOverlay() {
        pauseOverlayVisible = !pauseOverlayVisible
    }

    fun togglePlayPause() {
        playControlState = playControlState.copy(paused = !playControlState.paused)
        writePlayControl()
    }

    fun adjustSpeed(delta: Int) {
        playControlState = playControlState.copy(speed = clampPlaySpeed(playControlState.speed + delta))
        writePlayControl()
    }

    fun cycleScenario(delta: Int) {
        val path = scenarioPath ?: return
        playScenario = PlayScenario.cycle(playScenario, delta)
        writePlayScenario(path, playScenario)
        showNotice("scenario=${playScenario.id}")
    }

    fun cycleScenarioAndRestart(delta: Int) {
        cycleScenario(delta)
        requestRestart()
    }

    fun applyScenarioAndRestart() {
        scenarioPath?.let { writePlayScenario(it, playScenario) }
        requestRestart()
    }

    fun restartMatch() {
        requestRestart()
    }

    fun toggleDebug() {
        debugVisible = !debugVisible
    }

    fun resetCamera() {
        camera = CameraView()
    }

    fun executeAction(actionId: String, viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot
        when {
            actionId == "move" -> {
                groundMode = ClientGroundCommandMode.MOVE
                buildModeTypeId = null
            }
            actionId == "attackMove" -> {
                groundMode = ClientGroundCommandMode.ATTACK_MOVE
                buildModeTypeId = null
            }
            actionId == "patrol" -> {
                groundMode = ClientGroundCommandMode.PATROL
                buildModeTypeId = null
            }
            actionId == "hold" -> {
                val hold = snapshot?.let { buildHoldIntent(it, session.state.selectedIds, requestIds) }
                if (hold != null) session.append(hold)
                groundMode = null
                buildModeTypeId = null
            }
            actionId == "cancelBuild" || actionId == "cancelTrain" || actionId == "cancelResearch" -> {
                snapshot?.let { buildCancelIntent(it, session.state.selectedIds, actionId, requestIds) }?.let(session::append)
            }
            actionId == "pause" -> togglePlayPause()
            actionId == "slower" -> adjustSpeed(-1)
            actionId == "faster" -> adjustSpeed(1)
            actionId == "debug" -> toggleDebug()
            actionId == "clear" -> clearSelection()
            actionId == "centerSelection" -> centerOnSelection(viewWidth, viewHeight)
            actionId == "centerFaction" -> centerOnViewedFaction(viewWidth, viewHeight)
            actionId == "viewF1" -> setViewFaction(1)
            actionId == "viewF2" -> setViewFaction(2)
            actionId == "observer" -> setViewFaction(null)
            actionId == "selectViewedFaction" -> selectViewedFaction()
            actionId == "selectType" -> selectSelectedType()
            actionId == "selectRole" -> selectSelectedArchetype()
            actionId == "selectAll" -> selectAll()
            actionId == "selectIdleWorkers" -> selectIdleWorkers()
            actionId == "selectDamaged" -> selectDamaged()
            actionId == "selectCombat" -> selectCombat()
            actionId == "selectProducers" -> selectProducers()
            actionId.startsWith("build:") -> {
                buildModeTypeId = actionId.removePrefix("build:")
                groundMode = null
            }
            actionId.startsWith("train:") -> {
                val typeId = actionId.removePrefix("train:")
                snapshot?.let { buildQueueIntent(it, session.state.selectedIds, "train", typeId, requestIds) }?.let(session::append)
            }
            actionId.startsWith("research:") -> {
                val typeId = actionId.removePrefix("research:")
                snapshot?.let { buildQueueIntent(it, session.state.selectedIds, "research", typeId, requestIds) }?.let(session::append)
            }
        }
    }

    fun buttonModels(): List<ClientCommandButton> {
        val state = session.state.viewState
        return buildList {
            add(ClientCommandButton("Move", "move"))
            add(ClientCommandButton("Attack", "attackMove"))
            add(ClientCommandButton("Patrol", "patrol"))
            add(ClientCommandButton("Hold", "hold"))
            catalog.buildOptions.forEach { add(ClientCommandButton("Build ${it.label}", "build:${it.typeId}")) }
            if (state.canTrain) catalog.trainOptions.forEach { add(ClientCommandButton("Train ${it.label}", "train:${it.typeId}")) }
            if (state.canResearch) catalog.researchOptions.forEach { add(ClientCommandButton("Research ${it.label}", "research:${it.typeId}")) }
            add(ClientCommandButton("Cancel Build", "cancelBuild"))
            add(ClientCommandButton("Cancel Train", "cancelTrain"))
            add(ClientCommandButton("Cancel Research", "cancelResearch"))
            add(ClientCommandButton("Pause", "pause"))
            add(ClientCommandButton("Slower", "slower"))
            add(ClientCommandButton("Faster", "faster"))
            add(ClientCommandButton("Debug", "debug"))
            add(ClientCommandButton("Center", "centerSelection"))
            add(ClientCommandButton("Faction", "centerFaction"))
            add(ClientCommandButton("F1", "viewF1"))
            add(ClientCommandButton("F2", "viewF2"))
            add(ClientCommandButton("Obs", "observer"))
            add(ClientCommandButton("Select F", "selectViewedFaction"))
            add(ClientCommandButton("Select Type", "selectType"))
            add(ClientCommandButton("Select Role", "selectRole"))
            add(ClientCommandButton("Select All", "selectAll"))
            add(ClientCommandButton("Idle", "selectIdleWorkers"))
            add(ClientCommandButton("Damaged", "selectDamaged"))
            add(ClientCommandButton("Combat", "selectCombat"))
            add(ClientCommandButton("Producers", "selectProducers"))
            add(ClientCommandButton("Clear", "clear"))
        }
    }

    fun isActionEnabled(actionId: String): Boolean =
        isCommandButtonEnabled(
            actionId =
                when (actionId) {
                    "pause" -> "play:pause"
                    "slower" -> "play:slower"
                    "faster" -> "play:faster"
                    "centerSelection" -> "view:centerSelection"
                    "centerFaction" -> "view:centerFaction"
                    "viewF1" -> "view:faction1"
                    "viewF2" -> "view:faction2"
                    "observer" -> "view:observer"
                    "selectViewedFaction" -> "select:viewFaction"
                    "selectType" -> "select:selectedType"
                    "selectRole" -> "select:selectedArchetype"
                    "selectAll" -> "select:all"
                    "selectIdleWorkers" -> "select:idleWorkers"
                    "selectDamaged" -> "select:damaged"
                    "selectCombat" -> "select:combat"
                    "selectProducers" -> "select:producers"
                    else -> actionId
                },
            hasSelection = session.state.viewState.hasSelection,
            canTrain = session.state.viewState.canTrain,
            canResearch = session.state.viewState.canResearch,
            viewedFaction = session.state.viewedFaction
        )

    private fun selectViewedFaction() {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction ?: return
        val ids = collectFactionSelectionIds(snapshot, faction)
        applySelection(ids, buildFactionSelectionRecord(snapshot.tick + 1, faction), "selected ${ids.size} units")
    }

    private fun selectSelectedType() {
        val snapshot = session.state.snapshot ?: return
        val first = session.state.selectedIds.firstOrNull() ?: return
        val entity = snapshot.entities.firstOrNull { it.id == first } ?: return
        val faction = session.state.viewedFaction ?: entity.faction
        val ids = collectTypeSelectionIds(snapshot, entity.typeId, faction)
        applySelection(ids, buildTypeSelectionRecord(snapshot.tick + 1, entity.typeId), "selected ${entity.typeId}")
    }

    private fun selectSelectedArchetype() {
        val snapshot = session.state.snapshot ?: return
        val first = session.state.selectedIds.firstOrNull() ?: return
        val entity = snapshot.entities.firstOrNull { it.id == first } ?: return
        val archetype = entity.archetype ?: return
        val faction = session.state.viewedFaction ?: entity.faction
        val ids = collectArchetypeSelectionIds(snapshot, archetype, faction)
        applySelection(ids, buildArchetypeSelectionRecord(snapshot.tick + 1, archetype), "selected $archetype")
    }

    private fun selectAll() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectAllSelectionIds(snapshot)
        applySelection(ids, buildAllSelectionRecord(snapshot.tick + 1), "selected all (${ids.size})")
    }

    private fun selectIdleWorkers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectIdleWorkerSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "idle workers (${ids.size})")
    }

    private fun selectDamaged() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectDamagedSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "damaged (${ids.size})")
    }

    private fun selectCombat() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectCombatSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "combat (${ids.size})")
    }

    private fun selectProducers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectProducerSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "producers (${ids.size})")
    }

    private fun applySelection(ids: IntArray, record: InputJson.InputSelectionRecord, notice: String) {
        session.replaceSelection(ids)
        session.append(ClientIntent.Selection(record))
        showNotice(notice)
    }

    private fun writePlayControl() {
        val path = controlPath ?: return
        Files.writeString(path, renderPlayControlState(playControlState))
    }

    private fun showNotice(message: String) {
        noticeMessage = message
        noticeUntilMillis = System.currentTimeMillis() + 2500L
    }
}
