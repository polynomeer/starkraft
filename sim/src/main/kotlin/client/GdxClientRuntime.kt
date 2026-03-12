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
    private var hoverHint: String? = null
    private val controlGroups = arrayOfNulls<IntArray>(10)
    private var lastGroupRecall: Int? = null
    private var lastGroupRecallAtNanos: Long = 0L
    private var initialCameraApplied: Boolean = false
    private var activeScenario: PlayScenario? = null
    private var lastAutoRecoveredView: Int? = Int.MIN_VALUE
    private var attackWarningMessage: String? = null
    private var attackWarningUntilMillis: Long = 0L
    private var lastAttackAlertTick: Int = Int.MIN_VALUE
    private var pendingAttackAlertSound: Boolean = false
    private var pendingCompletionAlertSound: Boolean = false
    private var recentDamageEntityIds: Set<Int> = emptySet()
    private var recentDamageUntilMillis: Long = 0L
    private var recentGroundPing: GroundPing? = null
    private var recentGroundPingUntilMillis: Long = 0L
    private var recentCompletionEntityIds: Set<Int> = emptySet()
    private var recentCompletionKindsByEntityId: Map<Int, CompletionFlashKind> = emptyMap()
    private var recentCompletionUntilMillis: Long = 0L
    private var lastSnapshotTick: Int? = null
    private var previousEntitiesById: Map<Int, EntitySnapshot> = emptyMap()

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
    var zoomLocked: Boolean = true
    var debugVisible: Boolean = false
    var pauseOverlayVisible: Boolean = false
    var helpOverlayVisible: Boolean = false

    init {
        activeScenario = playScenario
    }

    val snapshot: ClientSnapshot?
        get() = session.state.snapshot

    fun tick() {
        session.poll()
        if (noticeMessage != null && System.currentTimeMillis() > noticeUntilMillis) {
            noticeMessage = null
        }
        if (attackWarningMessage != null && System.currentTimeMillis() > attackWarningUntilMillis) {
            attackWarningMessage = null
        }
        if (recentDamageEntityIds.isNotEmpty() && System.currentTimeMillis() > recentDamageUntilMillis) {
            recentDamageEntityIds = emptySet()
        }
        if (recentGroundPing != null && System.currentTimeMillis() > recentGroundPingUntilMillis) {
            recentGroundPing = null
        }
        if (recentCompletionEntityIds.isNotEmpty() && System.currentTimeMillis() > recentCompletionUntilMillis) {
            recentCompletionEntityIds = emptySet()
            recentCompletionKindsByEntityId = emptyMap()
        }
        maybeRaiseCompletionNotice()
        maybeRaiseAttackAlert()
    }

    fun noticeLine(): String? = noticeMessage?.let { "notice: $it" }
    fun attackWarningLine(): String? = attackWarningMessage
    fun consumeAttackAlertSound(): Boolean = pendingAttackAlertSound.also { pendingAttackAlertSound = false }
    fun consumeCompletionAlertSound(): Boolean = pendingCompletionAlertSound.also { pendingCompletionAlertSound = false }
    fun isDamageFlashActive(entityId: Int): Boolean = recentDamageEntityIds.contains(entityId) && System.currentTimeMillis() <= recentDamageUntilMillis
    fun currentGroundPing(): GroundPing? = recentGroundPing?.takeIf { System.currentTimeMillis() <= recentGroundPingUntilMillis }
    fun isCompletionFlashActive(entityId: Int): Boolean = recentCompletionEntityIds.contains(entityId) && System.currentTimeMillis() <= recentCompletionUntilMillis
    fun completionFlashKind(entityId: Int): CompletionFlashKind? = recentCompletionKindsByEntityId[entityId]?.takeIf { isCompletionFlashActive(entityId) }
    fun controlGroupSizes(): List<Pair<Int, Int>> = controlGroups.mapIndexedNotNull { index, ids -> ids?.takeIf { it.isNotEmpty() }?.size?.let { index to it } }

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
        controlGroupSummaryLine()?.let(lines::add)
        presetAvailabilityLine()?.let(lines::add)
        hoverHint?.let { lines.add("hint: $it") }
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
        val worldX = camera.screenToWorldX(screenX)
        val worldY = camera.screenToWorldY(screenY)
        if (buildModeTypeId != null) {
            placeBuildingAt(screenX, screenY)
            return
        }
        val intent =
            buildClientIntent(
                snapshot = snapshot,
                selectedIds = session.state.selectedIds,
                viewedFaction = session.state.viewedFaction,
                worldX = worldX,
                worldY = worldY,
                leftClick = false,
                rightClick = true,
                attackMoveModifier = attackMoveModifier,
                forcedGroundCommandType = groundMode?.commandType,
                additiveSelection = false,
                requestIds = requestIds
            ) ?: return
        if (intent is ClientIntent.Command) {
            session.append(intent)
            recentGroundPing = GroundPing(worldX, worldY, if (attackMoveModifier || groundMode == ClientGroundCommandMode.ATTACK_MOVE) GroundPingKind.ATTACK else GroundPingKind.MOVE)
            recentGroundPingUntilMillis = System.currentTimeMillis() + 900L
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
            recentGroundPing = GroundPing(tileX + 0.5f, tileY + 0.5f, GroundPingKind.INVALID)
            recentGroundPingUntilMillis = System.currentTimeMillis() + 900L
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
        recentGroundPing = GroundPing(tileX + (spec.width / 2f), tileY + (spec.height / 2f), GroundPingKind.BUILD)
        recentGroundPingUntilMillis = System.currentTimeMillis() + 900L
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
        initialCameraApplied = true
    }

    fun centerOnViewedFaction(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction ?: return
        val ids = collectFactionSelectionIds(snapshot, faction)
        val focus = computeSelectionCentroid(snapshot, ids) ?: return
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
        initialCameraApplied = true
    }

    fun zoomAt(screenX: Float, screenY: Float, factor: Float) {
        if (zoomLocked) return
        camera = zoomCameraAt(camera, screenX, screenY, factor)
        initialCameraApplied = true
    }

    fun panBy(deltaX: Float, deltaY: Float) {
        camera = camera.copy(panX = camera.panX + deltaX, panY = camera.panY + deltaY)
        initialCameraApplied = true
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
        showNotice(
            if (scenarioRestartRequired()) {
                "scenario=${playScenario.id} (restart required)"
            } else {
                "scenario=${playScenario.id}"
            }
        )
    }

    fun cycleScenarioAndRestart(delta: Int) {
        cycleScenario(delta)
        requestRestart()
    }

    fun applyScenarioAndRestart() {
        scenarioPath?.let { writePlayScenario(it, playScenario) }
        requestRestart()
    }

    fun scenarioRestartRequired(): Boolean = playScenario != activeScenario

    fun enterMatch(openGameScreen: () -> Unit) {
        if (scenarioRestartRequired()) {
            applyScenarioAndRestart()
        } else {
            openGameScreen()
        }
    }

    fun restartMatch() {
        requestRestart()
    }

    fun toggleDebug() {
        debugVisible = !debugVisible
    }

    fun toggleHelpOverlay() {
        helpOverlayVisible = !helpOverlayVisible
    }

    fun resetCamera() {
        camera = CameraView()
        initialCameraApplied = false
    }

    fun setHoverHint(text: String?) {
        hoverHint = text
    }

    fun centerFromMinimap(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): Boolean {
        val snapshot = session.state.snapshot ?: return false
        val world = gdxMiniMapWorldPosition(screenX, screenY, viewWidth, viewHeight, snapshot) ?: return false
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, world.first, world.second)
        initialCameraApplied = true
        return true
    }

    fun ensureInitialCamera(viewWidth: Int, viewHeight: Int) {
        if (initialCameraApplied) return
        val snapshot = session.state.snapshot ?: return
        val focus = snapshot.mapWidth / 2f to snapshot.mapHeight / 2f
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
        initialCameraApplied = true
    }

    fun constrainCamera(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val worldWidthPx = snapshot.mapWidth * camera.tileSize
        val worldHeightPx = snapshot.mapHeight * camera.tileSize
        val minPanX = viewWidth - worldWidthPx
        val minPanY = viewHeight - worldHeightPx
        val clampedPanX =
            if (worldWidthPx <= viewWidth) {
                (viewWidth - worldWidthPx) / 2f
            } else {
                camera.panX.coerceIn(minPanX, 0f)
            }
        val clampedPanY =
            if (worldHeightPx <= viewHeight) {
                (viewHeight - worldHeightPx) / 2f
            } else {
                camera.panY.coerceIn(minPanY, 0f)
            }
        camera = camera.copy(panX = clampedPanX, panY = clampedPanY)
    }

    fun ensurePlayableView(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val viewedFaction = session.state.viewedFaction ?: return
        val visibleTiles =
            snapshot.factions.firstOrNull { it.faction == viewedFaction }?.visibleTiles
                ?: session.state.visionState?.visibleTiles(viewedFaction)?.size
                ?: 0
        if (visibleTiles > 0) {
            lastAutoRecoveredView = Int.MIN_VALUE
            return
        }
        val fallbackFaction =
            snapshot.factions
                .filter { it.visibleTiles > 0 }
                .maxByOrNull { it.visibleTiles }
                ?.faction
        val recoveredView = fallbackFaction
        if (lastAutoRecoveredView == recoveredView) return
        if (fallbackFaction != null) {
            session.state.viewedFaction = fallbackFaction
            centerOnViewedFaction(viewWidth, viewHeight)
            showNotice("view auto-switched to f$fallbackFaction")
        } else {
            session.state.viewedFaction = null
            showNotice("view auto-switched to observer")
        }
        lastAutoRecoveredView = recoveredView
    }

    fun savePreset(name: String) {
        val root = playRoot ?: return
        savePlayPreset(root.resolve("presets"), name, PlayPresetState(playScenario, playControlState))
        showNotice("preset saved: $name")
    }

    fun loadPreset(name: String) {
        val root = playRoot ?: return
        val preset = loadPlayPreset(root.resolve("presets"), name, playScenario)
        if (preset == null) {
            showNotice("preset missing: $name")
            return
        }
        playScenario = preset.scenario
        playControlState = preset.control
        scenarioPath?.let { writePlayScenario(it, playScenario) }
        writePlayControl()
        showNotice(
            if (scenarioRestartRequired()) {
                "preset loaded: $name (restart required)"
            } else {
                "preset loaded: $name"
            }
        )
    }

    fun isPresetAvailable(name: String): Boolean {
        val root = playRoot ?: return false
        return Files.exists(presetFilePath(root.resolve("presets"), name))
    }

    fun presetAvailabilityLine(): String? {
        if (playRoot == null) return null
        return formatPresetAvailability(isPresetAvailable("quick"), isPresetAvailable("alt"))
    }

    fun controlGroupSummaryLine(): String? {
        val highlighted = activeControlGroupHighlight(lastGroupRecall, lastGroupRecallAtNanos, System.nanoTime())
        return formatControlGroupSummary(controlGroups, highlighted)?.let { "groups: $it" }
    }

    fun handleControlGroup(group: Int, assign: Boolean, add: Boolean, viewWidth: Int, viewHeight: Int) {
        when {
            assign -> {
                assignControlGroupSlot(controlGroups, group, session.state.selectedIds)
                showNotice("group $group set (${session.state.selectedIds.size})")
            }
            add -> {
                mergeControlGroupSlot(controlGroups, group, session.state.selectedIds)
                val size = controlGroups[group]?.size ?: 0
                showNotice("group $group add -> $size")
            }
            else -> recallControlGroup(group, viewWidth, viewHeight)
        }
    }

    fun clearControlGroups() {
        clearControlGroupSlots(controlGroups)
        lastGroupRecall = null
        showNotice("groups cleared")
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
            actionId == "selectTrainers" -> selectTrainers()
            actionId == "selectResearchers" -> selectResearchers()
            actionId == "selectConstruction" -> selectConstruction()
            actionId == "selectHarvesters" -> selectHarvesters()
            actionId == "selectReturning" -> selectReturningHarvesters()
            actionId == "selectCargo" -> selectCargoHarvesters()
            actionId == "selectDropoffs" -> selectDropoffs()
            actionId == "saveQuick" -> savePreset("quick")
            actionId == "loadQuick" -> loadPreset("quick")
            actionId == "saveAlt" -> savePreset("alt")
            actionId == "loadAlt" -> loadPreset("alt")
            actionId == "help" -> toggleHelpOverlay()
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
            if (state.hasSelection) {
                add(ClientCommandButton("Move", "move"))
                add(ClientCommandButton("Attack", "attackMove"))
                add(ClientCommandButton("Patrol", "patrol"))
                add(ClientCommandButton("Hold", "hold"))
                catalog.buildOptions
                    .filter { isActionEnabled("build:${it.typeId}") }
                    .forEach { add(ClientCommandButton("Build ${it.label}", "build:${it.typeId}")) }
                add(ClientCommandButton("Clear", "clear"))
            }
            if (state.canTrain) {
                catalog.trainOptions
                    .filter { isActionEnabled("train:${it.typeId}") }
                    .forEach { add(ClientCommandButton("Train ${it.label}", "train:${it.typeId}")) }
            }
            if (state.canResearch) {
                catalog.researchOptions
                    .filter { isActionEnabled("research:${it.typeId}") }
                    .forEach { add(ClientCommandButton("Research ${it.label}", "research:${it.typeId}")) }
            }
            listOf(
                ClientCommandButton("Cancel Build", "cancelBuild"),
                ClientCommandButton("Cancel Train", "cancelTrain"),
                ClientCommandButton("Cancel Research", "cancelResearch")
            ).filterTo(this) { isActionEnabled(it.actionId) }
            if (state.hasSelection) {
                add(ClientCommandButton("Center", "centerSelection"))
                if (debugVisible) {
                    listOf(
                        ClientCommandButton("Select Type", "selectType"),
                        ClientCommandButton("Select Role", "selectRole"),
                        ClientCommandButton("Damaged", "selectDamaged"),
                        ClientCommandButton("Combat", "selectCombat")
                    ).filterTo(this) { isActionEnabled(it.actionId) }
                }
            } else {
                listOf(
                    ClientCommandButton("Select F", "selectViewedFaction"),
                    ClientCommandButton("Idle", "selectIdleWorkers"),
                    ClientCommandButton("Select All", "selectAll")
                ).filterTo(this) { isActionEnabled(it.actionId) }
            }
            add(ClientCommandButton("Faction", "centerFaction"))
            add(ClientCommandButton("F1", "viewF1"))
            add(ClientCommandButton("F2", "viewF2"))
            add(ClientCommandButton("Obs", "observer"))
            add(ClientCommandButton("Pause", "pause"))
            add(ClientCommandButton("Help", "help"))
            add(ClientCommandButton("Debug", "debug"))
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
                    "selectTrainers" -> "select:trainers"
                    "selectResearchers" -> "select:researchers"
                    "selectConstruction" -> "select:construction"
                    "selectHarvesters" -> "select:harvesters"
                    "selectReturning" -> "select:returningHarvesters"
                    "selectCargo" -> "select:cargoHarvesters"
                    "selectDropoffs" -> "select:dropoffs"
                    else -> actionId
                },
            hasSelection = session.state.viewState.hasSelection,
            canTrain = session.state.viewState.canTrain,
            canResearch = session.state.viewState.canResearch,
            viewedFaction = session.state.viewedFaction
        )

    fun isActionActive(actionId: String): Boolean =
        when (actionId) {
            "move" -> groundMode == ClientGroundCommandMode.MOVE
            "attackMove" -> groundMode == ClientGroundCommandMode.ATTACK_MOVE
            "patrol" -> groundMode == ClientGroundCommandMode.PATROL
            "debug" -> debugVisible
            "help" -> helpOverlayVisible
            "viewF1" -> session.state.viewedFaction == 1
            "viewF2" -> session.state.viewedFaction == 2
            "observer" -> session.state.viewedFaction == null
            else -> actionId.startsWith("build:") && buildModeTypeId == actionId.removePrefix("build:")
        }

    fun actionHint(actionId: String): String? =
        when (actionId) {
            "pause" -> "Toggle play pause and resume"
            "slower" -> "Lower sim speed by one step"
            "faster" -> "Raise sim speed by one step"
            "debug" -> "Toggle extra runtime info in the command panel"
            "clear" -> "Clear selection and reset current mode"
            "centerSelection" -> "Center the camera on the current selection"
            "centerFaction" -> "Center the camera on the viewed faction"
            "viewF1" -> "Switch to faction 1 view"
            "viewF2" -> "Switch to faction 2 view"
            "observer" -> "Switch to observer view"
            "selectViewedFaction" -> "Select all units for the viewed faction"
            "selectType" -> "Select all units matching the first selected type"
            "selectRole" -> "Select all units matching the first selected archetype"
            "selectAll" -> "Select all entities in the current snapshot"
            "selectIdleWorkers" -> "Select idle workers in the current scope"
            "selectDamaged" -> "Select damaged units in the current scope"
            "selectCombat" -> "Select combat units in the current scope"
            "selectProducers" -> "Select producer buildings in the current scope"
            "selectTrainers" -> "Select training-capable buildings"
            "selectResearchers" -> "Select research-capable buildings"
            "selectConstruction" -> "Select unfinished structures"
            "selectHarvesters" -> "Select active harvesters"
            "selectReturning" -> "Select returning harvesters"
            "selectCargo" -> "Select workers carrying cargo"
            "selectDropoffs" -> "Select drop-off buildings"
            "saveQuick" -> "Save scenario and speed into the quick preset"
            "loadQuick" -> "Load the quick preset into the current workspace"
            "saveAlt" -> "Save scenario and speed into the alt preset"
            "loadAlt" -> "Load the alt preset into the current workspace"
            "help" -> "Toggle the in-game help overlay"
            "move" -> "Arm move mode for the next ground right click"
            "attackMove" -> "Arm attack-move mode for the next ground right click"
            "patrol" -> "Arm patrol mode for the next ground right click"
            "hold" -> "Issue hold position immediately"
            "cancelBuild" -> "Cancel build on the first selected construction site"
            "cancelTrain" -> "Cancel the last queued train job on the first selected producer"
            "cancelResearch" -> "Cancel the last queued research job on the first selected lab"
            else ->
                when {
                    actionId.startsWith("build:") -> "Enter placement mode for ${actionId.removePrefix("build:")}"
                    actionId.startsWith("train:") -> "Queue ${actionId.removePrefix("train:")} on the first selected trainer"
                    actionId.startsWith("research:") -> "Queue ${actionId.removePrefix("research:")} on the first selected lab"
                    else -> null
                }
        }

    fun mainMenuSummaryLines(): List<String> =
        listOfNotNull(
            "scenario: ${playScenario.id}",
            if (scenarioRestartRequired()) "scenario status: restart required before match" else "scenario status: live",
            "play: ${if (playControlState.paused) "paused" else "running"} x${playControlState.speed}",
            presetAvailabilityLine(),
            noticeLine()
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

    private fun selectTrainers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectTrainingSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "trainers (${ids.size})")
    }

    private fun selectResearchers() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectResearchSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "researchers (${ids.size})")
    }

    private fun selectConstruction() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectConstructionSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "construction (${ids.size})")
    }

    private fun selectHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "harvesters (${ids.size})")
    }

    private fun selectReturningHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectReturningHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "returning (${ids.size})")
    }

    private fun selectCargoHarvesters() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectCargoHarvesterSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "cargo (${ids.size})")
    }

    private fun selectDropoffs() {
        val snapshot = session.state.snapshot ?: return
        val ids = collectDropoffSelectionIds(snapshot, session.state.viewedFaction)
        applySelection(ids, buildUnitSelectionRecord(snapshot.tick + 1, ids.asList()), "dropoffs (${ids.size})")
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

    private fun maybeRaiseAttackAlert() {
        val damage = session.state.lastDamageActivity ?: return
        if (damage.tick == lastAttackAlertTick) return
        recentDamageEntityIds = damage.targetIds.toSet()
        recentDamageUntilMillis = System.currentTimeMillis() + 650L
        val snapshot = session.state.snapshot ?: return
        val viewedFaction = session.state.viewedFaction ?: return
        val affected =
            damage.targetIds.any { targetId ->
                snapshot.entities.firstOrNull { it.id == targetId }?.faction == viewedFaction
            }
        if (!affected) return
        lastAttackAlertTick = damage.tick
        attackWarningMessage = "Warning: under attack"
        attackWarningUntilMillis = System.currentTimeMillis() + 1800L
        pendingAttackAlertSound = true
    }

    private fun maybeRaiseCompletionNotice() {
        val snapshot = session.state.snapshot ?: return
        if (lastSnapshotTick == snapshot.tick) return
        val completed = LinkedHashMap<EntitySnapshot, CompletionFlashKind>()
        for (entity in snapshot.entities) {
            val previous = previousEntitiesById[entity.id] ?: continue
            val constructionComplete = previous.underConstruction && !entity.underConstruction
            val productionComplete =
                previous.activeProductionType != null &&
                    entity.activeProductionType == null &&
                    entity.productionQueueSize <= previous.productionQueueSize
            val researchComplete =
                previous.activeResearchTech != null &&
                    entity.activeResearchTech == null &&
                    entity.researchQueueSize <= previous.researchQueueSize
            when {
                constructionComplete -> completed[entity] = CompletionFlashKind.CONSTRUCTION
                researchComplete -> completed[entity] = CompletionFlashKind.RESEARCH
                productionComplete -> completed[entity] = CompletionFlashKind.PRODUCTION
            }
        }
        if (completed.isNotEmpty()) {
            recentCompletionEntityIds = completed.keys.map { it.id }.toSet()
            recentCompletionKindsByEntityId = completed.entries.associate { it.key.id to it.value }
            recentCompletionUntilMillis = System.currentTimeMillis() + 1200L
            pendingCompletionAlertSound = true
            val lead = completed.keys.first()
            val label =
                when {
                    completed[lead] == CompletionFlashKind.CONSTRUCTION -> "${lead.typeId} complete"
                    completed[lead] == CompletionFlashKind.RESEARCH -> "${lead.typeId} research complete"
                    else -> "${lead.typeId} ready"
                }
            showNotice(label)
        }
        previousEntitiesById = snapshot.entities.associateBy { it.id }
        lastSnapshotTick = snapshot.tick
    }

    private fun recallControlGroup(group: Int, viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val ids = recallControlGroupSlot(controlGroups, group, snapshot)
        if (ids.isEmpty()) {
            showNotice("group $group empty")
            return
        }
        val now = System.nanoTime()
        val focusOnRecall = lastGroupRecall == group && (now - lastGroupRecallAtNanos) <= 350_000_000L
        session.replaceSelection(ids)
        session.append(ClientIntent.Selection(buildUnitSelectionRecord(snapshot.tick + 1, ids.asList())))
        if (focusOnRecall) {
            val focus = computeSelectionCentroid(snapshot, ids)
            if (focus != null) {
                camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
            }
        }
        lastGroupRecall = group
        lastGroupRecallAtNanos = now
        showNotice("group $group recalled (${ids.size})")
    }
}

internal enum class CompletionFlashKind {
    CONSTRUCTION,
    PRODUCTION,
    RESEARCH
}

internal data class GroundPing(
    val worldX: Float,
    val worldY: Float,
    val kind: GroundPingKind
)

internal enum class GroundPingKind {
    MOVE,
    ATTACK,
    BUILD,
    INVALID
}
