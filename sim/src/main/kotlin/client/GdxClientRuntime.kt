package starkraft.sim.client

import starkraft.sim.net.InputJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.floor

internal class GdxClientRuntime(
    val session: ClientSession,
    private val controlPath: Path?,
    private val scenarioPath: Path?,
    private val playRoot: Path?,
    private val requestRestart: () -> Unit
) {
    private companion object {
        const val NOTICE_DURATION_MS = 2000L
        const val ATTACK_WARNING_DURATION_MS = 1800L
        const val DAMAGE_FLASH_DURATION_MS = 820L
        const val GROUND_PING_DURATION_MS = 720L
        const val MINIMAP_CONFIRM_DURATION_MS = 320L
        const val COMPLETION_FLASH_DURATION_MS = 1700L
        const val DEATH_BURST_DURATION_MS = 980L
        const val DEATH_REMAINS_DURATION_MS = 2600L
        const val SELECTION_CONFIRM_PULSE_DURATION_MS = 420L
    }

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
    private var pendingCommandSoundKind: CommandSoundKind? = null
    private var pendingCombatSoundKind: CombatSoundKind? = null
    private var pendingDeathSoundKind: DeathSoundKind? = null
    private var pendingCompletionAlertSound: Boolean = false
    private var recentDamageEntityIds: Set<Int> = emptySet()
    private var recentDamageKindsByEntityId: Map<Int, CombatSoundKind> = emptyMap()
    private var recentDamageUntilMillis: Long = 0L
    private var recentGroundPing: GroundPing? = null
    private var recentGroundPingUntilMillis: Long = 0L
    private var recentMinimapConfirm: MinimapConfirm? = null
    private var recentMinimapConfirmUntilMillis: Long = 0L
    private var recentCompletionEntityIds: Set<Int> = emptySet()
    private var recentCompletionKindsByEntityId: Map<Int, CompletionFlashKind> = emptyMap()
    private var recentCompletionUntilMillis: Long = 0L
    private var recentSelectionPulseIds: Set<Int> = emptySet()
    private var recentSelectionPulseUntilMillis: Long = 0L
    private val recentDeathBursts = ArrayList<DeathBurst>()
    private val recentDeathRemains = ArrayList<DeathRemains>()
    private var lastSnapshotTick: Int? = null
    private var previousEntitiesById: Map<Int, EntitySnapshot> = emptyMap()
    private var cameraTarget: CameraView? = null

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
            recentDamageKindsByEntityId = emptyMap()
        }
        if (recentGroundPing != null && System.currentTimeMillis() > recentGroundPingUntilMillis) {
            recentGroundPing = null
        }
        if (recentMinimapConfirm != null && System.currentTimeMillis() > recentMinimapConfirmUntilMillis) {
            recentMinimapConfirm = null
        }
        if (recentCompletionEntityIds.isNotEmpty() && System.currentTimeMillis() > recentCompletionUntilMillis) {
            recentCompletionEntityIds = emptySet()
            recentCompletionKindsByEntityId = emptyMap()
        }
        if (recentSelectionPulseIds.isNotEmpty() && System.currentTimeMillis() > recentSelectionPulseUntilMillis) {
            recentSelectionPulseIds = emptySet()
        }
        if (recentDeathBursts.isNotEmpty()) {
            val now = System.currentTimeMillis()
            recentDeathBursts.removeAll { now > it.expiresAtMillis }
        }
        if (recentDeathRemains.isNotEmpty()) {
            val now = System.currentTimeMillis()
            recentDeathRemains.removeAll { now > it.expiresAtMillis }
        }
        maybeRaiseCompletionNotice()
        maybeRaiseAttackAlert()
        updateCameraGlide()
    }

    fun noticeLine(): String? = noticeMessage?.let { "notice: $it" }
    fun noticeKind(): NoticeKind? =
        when {
            noticeMessage == null -> null
            noticeMessage!!.startsWith("trade ") -> NoticeKind.TRADE
            noticeMessage!!.contains(" lost") -> NoticeKind.LOSS
            noticeMessage!!.contains(" down") -> NoticeKind.KILL
            else -> NoticeKind.INFO
        }
    fun hoverHintLine(): String? = hoverHint
    fun attackWarningLine(): String? = attackWarningMessage
    fun isStructureLossWarning(): Boolean = attackWarningMessage == "Warning: structure lost"
    fun consumeAttackAlertSound(): Boolean = pendingAttackAlertSound.also { pendingAttackAlertSound = false }
    fun consumeCommandSoundKind(): CommandSoundKind? = pendingCommandSoundKind.also { pendingCommandSoundKind = null }
    fun consumeCombatSoundKind(): CombatSoundKind? = pendingCombatSoundKind.also { pendingCombatSoundKind = null }
    fun consumeDeathSoundKind(): DeathSoundKind? = pendingDeathSoundKind.also { pendingDeathSoundKind = null }
    fun consumeCompletionAlertSound(): Boolean = pendingCompletionAlertSound.also { pendingCompletionAlertSound = false }
    fun isDamageFlashActive(entityId: Int): Boolean = recentDamageEntityIds.contains(entityId) && System.currentTimeMillis() <= recentDamageUntilMillis
    fun damageImpactKind(entityId: Int): CombatSoundKind? = recentDamageKindsByEntityId[entityId]?.takeIf { isDamageFlashActive(entityId) }
    fun currentGroundPing(): GroundPing? = recentGroundPing?.takeIf { System.currentTimeMillis() <= recentGroundPingUntilMillis }
    fun currentMinimapConfirm(): MinimapConfirm? = recentMinimapConfirm?.takeIf { System.currentTimeMillis() <= recentMinimapConfirmUntilMillis }
    fun isCompletionFlashActive(entityId: Int): Boolean = recentCompletionEntityIds.contains(entityId) && System.currentTimeMillis() <= recentCompletionUntilMillis
    fun completionFlashKind(entityId: Int): CompletionFlashKind? = recentCompletionKindsByEntityId[entityId]?.takeIf { isCompletionFlashActive(entityId) }
    fun selectionConfirmPulse(entityId: Int): Float {
        if (entityId !in recentSelectionPulseIds) return 0f
        val remaining = recentSelectionPulseUntilMillis - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        return (remaining.toFloat() / SELECTION_CONFIRM_PULSE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
    }
    fun activeDeathBursts(): List<DeathBurst> = recentDeathBursts.filter { System.currentTimeMillis() <= it.expiresAtMillis }
    fun activeDeathRemains(): List<DeathRemains> = recentDeathRemains.filter { System.currentTimeMillis() <= it.expiresAtMillis }
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
        if ((buildModeTypeId != null || groundMode != null) && session.state.selectedIds.isNotEmpty()) {
            if (buildModeTypeId != null) {
                placeBuildingAt(screenX, screenY)
                return
            }
            val worldX = camera.screenToWorldX(screenX)
            val worldY = camera.screenToWorldY(screenY)
            val intent =
                buildClientIntent(
                    snapshot = snapshot,
                    selectedIds = session.state.selectedIds,
                    viewedFaction = session.state.viewedFaction,
                    worldX = worldX,
                    worldY = worldY,
                    leftClick = false,
                    rightClick = true,
                    attackMoveModifier = false,
                    forcedGroundCommandType = groundMode?.commandType,
                    additiveSelection = false,
                    requestIds = requestIds
                ) ?: return
            if (intent is ClientIntent.Command) {
                session.append(intent)
                recentGroundPing = GroundPing(worldX, worldY, if (intent.record.commandType == "attack" || intent.record.commandType == "attackMove") GroundPingKind.ATTACK else GroundPingKind.MOVE)
                recentGroundPingUntilMillis = System.currentTimeMillis() + GROUND_PING_DURATION_MS
                pendingCommandSoundKind =
                    if (intent.record.commandType == "attack" || intent.record.commandType == "attackMove") {
                        CommandSoundKind.ATTACK
                    } else {
                        CommandSoundKind.MOVE
                    }
                groundMode = null
            }
            return
        }
        val beforeSelection = session.state.selectedIds.toSet()
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
        triggerSelectionPulse(beforeSelection)
    }

    fun issueSelectionBox(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        additiveSelection: Boolean
    ) {
        val snapshot = session.state.snapshot ?: return
        val beforeSelection = session.state.selectedIds.toSet()
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
        triggerSelectionPulse(beforeSelection)
    }

    private fun triggerSelectionPulse(previousSelection: Set<Int>) {
        val current = session.state.selectedIds.toSet()
        if (current.isEmpty() || current == previousSelection) return
        recentSelectionPulseIds = current
        recentSelectionPulseUntilMillis = System.currentTimeMillis() + SELECTION_CONFIRM_PULSE_DURATION_MS
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
            recentGroundPingUntilMillis = System.currentTimeMillis() + GROUND_PING_DURATION_MS
            pendingCommandSoundKind =
                if (intent.record.commandType == "attack" || intent.record.commandType == "attackMove") {
                    CommandSoundKind.ATTACK
                } else {
                    CommandSoundKind.MOVE
                }
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
            recentGroundPingUntilMillis = System.currentTimeMillis() + GROUND_PING_DURATION_MS
            pendingCommandSoundKind = CommandSoundKind.INVALID
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
        recentGroundPingUntilMillis = System.currentTimeMillis() + GROUND_PING_DURATION_MS
        pendingCommandSoundKind = CommandSoundKind.BUILD
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
        queueCameraCenter(viewWidth, viewHeight, focus.first, focus.second)
        initialCameraApplied = true
    }

    fun centerOnViewedFaction(viewWidth: Int, viewHeight: Int) {
        val snapshot = session.state.snapshot ?: return
        val faction = session.state.viewedFaction ?: return
        val ids = collectFactionSelectionIds(snapshot, faction)
        val focus = computeSelectionCentroid(snapshot, ids) ?: return
        queueCameraCenter(viewWidth, viewHeight, focus.first, focus.second)
        initialCameraApplied = true
    }

    fun zoomAt(screenX: Float, screenY: Float, factor: Float) {
        if (zoomLocked) return
        camera = zoomCameraAt(camera, screenX, screenY, factor)
        initialCameraApplied = true
    }

    fun panBy(deltaX: Float, deltaY: Float) {
        camera = camera.copy(panX = camera.panX + deltaX, panY = camera.panY + deltaY)
        cameraTarget = null
        initialCameraApplied = true
    }

    fun nudgePanBy(deltaX: Float, deltaY: Float) {
        val base = cameraTarget ?: camera
        cameraTarget = base.copy(panX = base.panX + deltaX, panY = base.panY + deltaY)
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
        cameraTarget = null
        initialCameraApplied = false
    }

    fun setHoverHint(text: String?) {
        hoverHint = text
    }

    fun centerFromMinimap(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): Boolean {
        val snapshot = session.state.snapshot ?: return false
        val world = gdxMiniMapWorldPosition(screenX, screenY, viewWidth, viewHeight, snapshot) ?: return false
        queueCameraCenter(viewWidth, viewHeight, world.first, world.second)
        recentMinimapConfirm = MinimapConfirm(world.first, world.second)
        recentMinimapConfirmUntilMillis = System.currentTimeMillis() + MINIMAP_CONFIRM_DURATION_MS
        initialCameraApplied = true
        return true
    }

    fun dragCenterFromMinimap(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): Boolean {
        val snapshot = session.state.snapshot ?: return false
        val world = gdxMiniMapWorldPosition(screenX, screenY, viewWidth, viewHeight, snapshot) ?: return false
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, world.first, world.second)
        cameraTarget = null
        recentMinimapConfirm = MinimapConfirm(world.first, world.second)
        recentMinimapConfirmUntilMillis = System.currentTimeMillis() + MINIMAP_CONFIRM_DURATION_MS
        initialCameraApplied = true
        return true
    }

    fun ensureInitialCamera(viewWidth: Int, viewHeight: Int) {
        if (initialCameraApplied) return
        val snapshot = session.state.snapshot ?: return
        val focus = snapshot.mapWidth / 2f to snapshot.mapHeight / 2f
        camera = centerCameraOnWorld(camera, viewWidth, viewHeight, focus.first, focus.second)
        cameraTarget = null
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
        cameraTarget =
            cameraTarget?.copy(
                panX =
                    if (worldWidthPx <= viewWidth) {
                        (viewWidth - worldWidthPx) / 2f
                    } else {
                        cameraTarget?.panX?.coerceIn(minPanX, 0f) ?: clampedPanX
                    },
                panY =
                    if (worldHeightPx <= viewHeight) {
                        (viewHeight - worldHeightPx) / 2f
                    } else {
                        cameraTarget?.panY?.coerceIn(minPanY, 0f) ?: clampedPanY
                    }
            )
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
            "pause" -> "Toggle sim pause"
            "slower" -> "Sim speed down"
            "faster" -> "Sim speed up"
            "debug" -> "Toggle extra info"
            "clear" -> "Clear selection"
            "centerSelection" -> "Center on selection"
            "centerFaction" -> "Center on view"
            "viewF1" -> "View faction 1"
            "viewF2" -> "View faction 2"
            "observer" -> "View observer"
            "selectViewedFaction" -> "Select viewed faction"
            "selectType" -> "Select same type"
            "selectRole" -> "Select same role"
            "selectAll" -> "Select all"
            "selectIdleWorkers" -> "Select idle workers"
            "selectDamaged" -> "Select damaged"
            "selectCombat" -> "Select combat"
            "selectProducers" -> "Select producers"
            "selectTrainers" -> "Select trainers"
            "selectResearchers" -> "Select labs"
            "selectConstruction" -> "Select unfinished"
            "selectHarvesters" -> "Select harvesters"
            "selectReturning" -> "Select returning"
            "selectCargo" -> "Select cargo"
            "selectDropoffs" -> "Select dropoffs"
            "saveQuick" -> "Save quick preset"
            "loadQuick" -> "Load quick preset"
            "saveAlt" -> "Save alt preset"
            "loadAlt" -> "Load alt preset"
            "help" -> "Toggle help"
            "move" -> "Arm move"
            "attackMove" -> "Arm attack"
            "patrol" -> "Arm patrol"
            "hold" -> "Hold now"
            "cancelBuild" -> "Cancel build"
            "cancelTrain" -> "Cancel train"
            "cancelResearch" -> "Cancel research"
            else ->
                when {
                    actionId.startsWith("build:") -> "Place ${actionId.removePrefix("build:")}"
                    actionId.startsWith("train:") -> "Queue ${actionId.removePrefix("train:")}"
                    actionId.startsWith("research:") -> "Queue ${actionId.removePrefix("research:")}"
                    else -> null
                }
        }

    fun mainMenuSummaryLines(): List<String> =
        listOfNotNull(
            "scenario: ${playScenario.id}",
            if (scenarioRestartRequired()) "status: restart required" else "status: live",
            "play: ${if (playControlState.paused) "paused" else "run"} x${playControlState.speed}",
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
        noticeUntilMillis = System.currentTimeMillis() + NOTICE_DURATION_MS
    }

    private fun queueCameraCenter(viewWidth: Int, viewHeight: Int, worldX: Float, worldY: Float) {
        cameraTarget = centerCameraOnWorld(camera, viewWidth, viewHeight, worldX, worldY)
    }

    private fun updateCameraGlide() {
        val target = cameraTarget ?: return
        val nextPanX = glideValue(camera.panX, target.panX)
        val nextPanY = glideValue(camera.panY, target.panY)
        camera = camera.copy(panX = nextPanX, panY = nextPanY)
        if (abs(nextPanX - target.panX) < 0.5f && abs(nextPanY - target.panY) < 0.5f) {
            camera = target
            cameraTarget = null
        }
    }

    private fun glideValue(current: Float, target: Float): Float = current + ((target - current) * 0.24f)

    private fun maybeRaiseAttackAlert() {
        val damage = session.state.lastDamageActivity ?: return
        if (damage.tick == lastAttackAlertTick) return
        recentDamageEntityIds = damage.targetIds.toSet()
        recentDamageUntilMillis = System.currentTimeMillis() + DAMAGE_FLASH_DURATION_MS
        if (damage.targetIds.isNotEmpty()) {
            val snapshot = session.state.snapshot
            val damageKinds = LinkedHashMap<Int, CombatSoundKind>(damage.targetIds.size)
            damage.attackerIds.forEachIndexed { index, attackerId ->
                val targetId = damage.targetIds.getOrNull(index) ?: return@forEachIndexed
                val kind = snapshot?.entities?.firstOrNull { it.id == attackerId }?.let(::combatSoundKindForAttacker) ?: CombatSoundKind.RANGED
                damageKinds[targetId] = kind
            }
            recentDamageKindsByEntityId = damageKinds
            pendingCombatSoundKind = damageKinds.values.firstOrNull() ?: CombatSoundKind.RANGED
        }
        val snapshot = session.state.snapshot ?: return
        val viewedFaction = session.state.viewedFaction ?: return
        val affected =
            damage.targetIds.any { targetId ->
                snapshot.entities.firstOrNull { it.id == targetId }?.faction == viewedFaction
            }
        if (!affected) return
        lastAttackAlertTick = damage.tick
        attackWarningMessage = "Warning: under attack"
        attackWarningUntilMillis = System.currentTimeMillis() + ATTACK_WARNING_DURATION_MS
        pendingAttackAlertSound = true
    }

    private fun maybeRaiseCompletionNotice() {
        val snapshot = session.state.snapshot ?: return
        if (lastSnapshotTick == snapshot.tick) return
        maybeRaiseDeathBursts(snapshot)
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
            recentCompletionUntilMillis = System.currentTimeMillis() + COMPLETION_FLASH_DURATION_MS
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

    private fun maybeRaiseDeathBursts(snapshot: ClientSnapshot) {
        if (previousEntitiesById.isEmpty()) return
        val now = System.currentTimeMillis()
        val liveIds = snapshot.entities.asSequence().map { it.id }.toHashSet()
        val vanished = previousEntitiesById.values.filter { it.id !in liveIds && it.faction > 0 }
        if (vanished.isEmpty()) return
        var deathSoundKind: DeathSoundKind? = null
        val viewedFaction = session.state.viewedFaction
        for (entity in vanished) {
            recentDeathBursts +=
                DeathBurst(
                    entityId = entity.id,
                    x = entity.x,
                    y = entity.y,
                    isStructure = entity.footprintWidth != null && entity.footprintHeight != null,
                    faction = entity.faction,
                    typeId = entity.typeId,
                    expiresAtMillis = now + DEATH_BURST_DURATION_MS
                )
            recentDeathRemains +=
                DeathRemains(
                    entityId = entity.id,
                    x = entity.x,
                    y = entity.y,
                    isStructure = entity.footprintWidth != null && entity.footprintHeight != null,
                    faction = entity.faction,
                    typeId = entity.typeId,
                    expiresAtMillis = now + DEATH_REMAINS_DURATION_MS
                )
            deathSoundKind =
                when {
                    entity.footprintWidth != null && entity.footprintHeight != null -> DeathSoundKind.STRUCTURE
                    entity.typeId.contains("Marine", ignoreCase = true) -> DeathSoundKind.MARINE
                    entity.typeId.contains("Zergling", ignoreCase = true) -> DeathSoundKind.ZERGLING
                    deathSoundKind == null -> DeathSoundKind.UNIT
                    else -> deathSoundKind
                }
        }
        if (viewedFaction != null) {
            val friendlyLosses = vanished.filter { it.faction == viewedFaction }
            val enemyLosses = vanished.filter { it.faction != viewedFaction }
            when {
                friendlyLosses.isNotEmpty() && enemyLosses.isNotEmpty() ->
                    showNotice("trade ${summarizeDeaths(enemyLosses)}/${summarizeDeaths(friendlyLosses)}")
                friendlyLosses.isNotEmpty() ->
                    showNotice("${summarizeDeaths(friendlyLosses)} lost")
                enemyLosses.isNotEmpty() ->
                    showNotice("${summarizeDeaths(enemyLosses)} down")
            }
            if (friendlyLosses.any { it.footprintWidth != null && it.footprintHeight != null }) {
                attackWarningMessage = "Warning: structure lost"
                attackWarningUntilMillis = System.currentTimeMillis() + ATTACK_WARNING_DURATION_MS
                pendingAttackAlertSound = true
            }
        }
        if (deathSoundKind != null) {
            pendingDeathSoundKind = deathSoundKind
        }
    }

    private fun summarizeDeaths(entities: List<EntitySnapshot>): String {
        if (entities.isEmpty()) return "0"
        if (entities.size == 1) {
            val entity = entities.first()
            return when {
                entity.footprintWidth != null && entity.footprintHeight != null -> entity.typeId.lowercase()
                else -> entity.typeId.lowercase()
            }
        }
        val firstType = entities.first().typeId
        return if (entities.all { it.typeId == firstType }) "${entities.size} ${firstType.lowercase()}s" else "${entities.size} units"
    }

    private fun isMeleeAttacker(entity: EntitySnapshot): Boolean =
        entity.weaponId?.contains("Claw", ignoreCase = true) == true ||
            entity.typeId.contains("Zergling", ignoreCase = true)

    private fun combatSoundKindForAttacker(entity: EntitySnapshot): CombatSoundKind =
        when {
            entity.typeId.contains("Marine", ignoreCase = true) -> CombatSoundKind.MARINE_RANGED
            entity.typeId.contains("Zergling", ignoreCase = true) -> CombatSoundKind.ZERGLING_MELEE
            isMeleeAttacker(entity) -> CombatSoundKind.MELEE
            else -> CombatSoundKind.RANGED
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
                queueCameraCenter(viewWidth, viewHeight, focus.first, focus.second)
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

internal data class MinimapConfirm(
    val worldX: Float,
    val worldY: Float
)

internal enum class GroundPingKind {
    MOVE,
    ATTACK,
    BUILD,
    INVALID
}

internal enum class CommandSoundKind {
    MOVE,
    ATTACK,
    BUILD,
    INVALID
}

internal data class DeathBurst(
    val entityId: Int,
    val x: Float,
    val y: Float,
    val isStructure: Boolean,
    val faction: Int,
    val typeId: String,
    val expiresAtMillis: Long
)

internal data class DeathRemains(
    val entityId: Int,
    val x: Float,
    val y: Float,
    val isStructure: Boolean,
    val faction: Int,
    val typeId: String,
    val expiresAtMillis: Long
)

internal enum class CombatSoundKind {
    MARINE_RANGED,
    ZERGLING_MELEE,
    RANGED,
    MELEE
}

internal enum class DeathSoundKind {
    UNIT,
    MARINE,
    ZERGLING,
    STRUCTURE
}

internal enum class NoticeKind {
    KILL,
    LOSS,
    TRADE,
    INFO
}
