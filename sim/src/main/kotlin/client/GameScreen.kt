package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.abs

internal class GameScreen(
    private val game: StarkraftGdxGame,
    private val assets: GdxUiAssets,
    private val runtime: GdxClientRuntime
) : ScreenAdapter() {
    private val worldRenderer = GdxWorldRenderer(assets)
    private val stage = Stage(ScreenViewport())
    private val topBar = Table()
    private val economyLabel = Label("", assets.bodyLabelStyle)
    private val modeLabel = Label("", assets.accentLabelStyle)
    private val statusBadgeLabel = Label("", assets.bodyLabelStyle)
    private val statusHeader = Label("Field Status", assets.titleLabelStyle)
    private val selectionMetaLabel = Label("", assets.mutedLabelStyle)
    private val hudLinesLabel = Label("", assets.bodyLabelStyle)
    private val selectionLabel = Label("", assets.accentLabelStyle)
    private val statusContent = Table()
    private val statusScroll = ScrollPane(statusContent)
    private val commandHeaderLabel = Label("Commands", assets.titleLabelStyle)
    private val buttonTable = Table()
    private val commandScroll = ScrollPane(buttonTable)
    private val actionBanner = Table()
    private val actionBannerLabel = Label("", assets.bodyLabelStyle)
    private val pauseOverlay = Table()
    private val helpOverlay = Table()
    private val helpLabel = Label("", assets.mutedLabelStyle)
    private val footerLabel = Label("", assets.mutedLabelStyle)
    private var dragSelection: DragSelectionBox? = null

    init {
        buildHud()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, GameInputController())
    }

    override fun render(delta: Float) {
        runtime.tick()
        runtime.ensureInitialCamera(Gdx.graphics.width, Gdx.graphics.height)
        refreshHud()
        worldRenderer.render(runtime, Gdx.graphics.width, Gdx.graphics.height, dragSelection)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }

    private fun buildHud() {
        val root =
            Table().apply {
                setFillParent(true)
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
            }

        topBar.apply {
            background = assets.panelDrawable(Color(0.05f, 0.09f, 0.12f, 0.78f))
            pad(8f, 12f, 8f, 12f)
            add(economyLabel).left().expandX().fillX()
            add(modeLabel).center().padLeft(12f).padRight(12f)
            add(statusBadgeLabel).right()
        }

        val leftPanel =
            Table().apply {
                background = assets.panelDrawable(Color(0.05f, 0.10f, 0.14f, 0.78f))
                pad(12f)
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
                add(statusHeader).left().row()
                add(statusScroll).top().left().padTop(6f).row()
            }
        statusContent.top().left()
        statusScroll.setFadeScrollBars(false)
        statusScroll.setScrollingDisabled(true, false)
        statusScroll.setOverscroll(false, false)
        statusContent.add(selectionLabel).left().top().row()
        statusContent.add(selectionMetaLabel).left().top().padTop(2f).row()
        statusContent.add(hudLinesLabel).left().top().padTop(6f).row()
        statusContent.add(footerLabel).left().top().padTop(10f).row()

        val rightPanel =
            Table().apply {
                background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.84f))
                pad(10f)
                top()
            }
        buttonTable.top().left()
        commandScroll.setFadeScrollBars(false)
        commandScroll.setScrollingDisabled(true, false)
        rightPanel.add(commandHeaderLabel).left().row()
        rightPanel.add(commandScroll).top()

        actionBanner.apply {
            background = assets.panelDrawable(Color(0.08f, 0.13f, 0.16f, 0.82f))
            pad(6f, 12f, 6f, 12f)
            add(actionBannerLabel).center()
        }

        root.add(topBar).colspan(3).expandX().fillX().top().pad(10f, 10f, 0f, 10f).row()
        root.add(leftPanel).left().top().pad(10f)
        root.add().expand().fill()
        root.add(rightPanel).right().bottom().pad(10f).row()
        root.add().expandY().fillY()
        root.add(actionBanner).bottom().padBottom(10f)
        root.add().expandY().fillY()
        stage.addActor(root)

        pauseOverlay.apply {
            setFillParent(true)
            isVisible = false
            background = assets.panelDrawable(Color(0.03f, 0.04f, 0.06f, 0.92f))
            defaults().pad(8f)
            add(Label("Paused", assets.titleLabelStyle)).row()
            add(makeButton("Resume", style = assets.primaryButtonStyle()) { runtime.togglePauseOverlay() }).width(240f).row()
            add(makeButton("Toggle Sim Pause", style = assets.secondaryButtonStyle()) { runtime.togglePlayPause() }).width(240f).row()
            add(makeButton("Restart Match", style = assets.secondaryButtonStyle()) { runtime.restartMatch() }).width(240f).row()
            add(makeButton("Save Quick", style = assets.subtleButtonStyle()) { runtime.savePreset("quick") }).width(240f).row()
            add(makeButton("Load Quick", style = assets.subtleButtonStyle()) { runtime.loadPreset("quick") }).width(240f).row()
            add(makeButton("Save Alt", style = assets.subtleButtonStyle()) { runtime.savePreset("alt") }).width(240f).row()
            add(makeButton("Load Alt", style = assets.subtleButtonStyle()) { runtime.loadPreset("alt") }).width(240f).row()
            add(makeButton("Main Menu", style = assets.subtleButtonStyle()) {
                runtime.togglePauseOverlay()
                game.openMainMenu()
            }).width(240f).row()
            add(makeButton("Quit", style = assets.subtleButtonStyle()) { Gdx.app.exit() }).width(240f).row()
        }
        stage.addActor(pauseOverlay)

        helpOverlay.apply {
            setFillParent(true)
            isVisible = false
            top().left()
            pad(18f)
            background = assets.panelDrawable(Color(0.04f, 0.07f, 0.10f, 0.92f))
            add(helpLabel).left().top()
        }
        stage.addActor(helpOverlay)
    }

    private fun refreshHud() {
        val snapshot = runtime.snapshot
        val statusWidth = (Gdx.graphics.width * 0.24f).coerceIn(240f, 360f)
        val statusHeight = (Gdx.graphics.height * 0.42f).coerceIn(220f, 420f)
        val commandWidth = (Gdx.graphics.width * 0.20f).coerceIn(220f, 310f)
        val commandHeight = (Gdx.graphics.height * 0.40f).coerceIn(220f, 380f)
        val compactLines = compactHudLines()
        selectionLabel.setWrap(true)
        selectionMetaLabel.setWrap(true)
        hudLinesLabel.setWrap(true)
        footerLabel.setWrap(true)
        selectionLabel.setWidth(statusWidth)
        selectionMetaLabel.setWidth(statusWidth)
        hudLinesLabel.setWidth(statusWidth)
        footerLabel.setWidth(statusWidth)
        statusScroll.setSize(statusWidth + 8f, statusHeight)
        commandScroll.setSize(commandWidth, commandHeight)
        selectionLabel.setText(runtime.session.state.viewState.selectionHudLine ?: "selection hud: none")
        selectionMetaLabel.setText(buildSelectionMetaLine())
        hudLinesLabel.setText(compactLines.joinToString("\n"))
        footerLabel.setText("LMB select  RMB command  MMB/scroll camera  F1 help  F5 restart  F6/F7 scenario")
        statusHeader.setText(if (runtime.debugVisible) "Field Status + Debug" else "Field Status")
        commandHeaderLabel.setText("Commands ${runtime.overlayModeLabel()}")
        economyLabel.setText(buildTopEconomyLine())
        modeLabel.setText(buildTopModeLine())
        statusBadgeLabel.setText(buildStatusBadgeLine())
        actionBannerLabel.setText(buildActionBannerLine())
        pauseOverlay.isVisible = runtime.pauseOverlayVisible
        helpOverlay.isVisible = runtime.helpOverlayVisible
        helpLabel.setText(buildHelpOverlayLines(runtime.helpOverlayVisible).joinToString("\n"))
        actionBanner.isVisible = actionBannerLabel.text.toString().isNotBlank()
        statusScroll.layout()

        buttonTable.clearChildren()
        buttonTable.defaults().padBottom(4f)
        for (button in runtime.buttonModels()) {
            val actor = makeButton(
                button.label,
                runtime.actionHint(button.actionId),
                if (button.actionId == "pause" || button.actionId == "help") assets.secondaryButtonStyle() else assets.subtleButtonStyle()
            ) { runtime.executeAction(button.actionId, Gdx.graphics.width, Gdx.graphics.height) }
            actor.isDisabled = !runtime.isActionEnabled(button.actionId)
            actor.isChecked = runtime.isActionActive(button.actionId)
            buttonTable.add(actor).width(commandWidth - 10f).left().padBottom(4f).row()
        }
        if (runtime.debugVisible && snapshot != null) {
            buttonTable.add(Label("debug: entities=${snapshot.entities.size} resources=${snapshot.resourceNodes.size}", assets.mutedLabelStyle)).left().padTop(10f).row()
        }
    }

    private fun compactHudLines(): List<String> {
        val lines = runtime.currentHudLines()
        val preferredPrefixes =
            listOf(
                "mode=",
                "play:",
                "scenario=",
                "economy:",
                "selection factions:",
                "selection classes:",
                "selection health:",
                "selection orders:",
                "selection tasks:",
                "selection path:",
                "production:",
                "research:",
                "fog:",
                "groups:",
                "presets:",
                "notice:",
                "hint:"
            )
        val picked = ArrayList<String>()
        for (prefix in preferredPrefixes) {
            lines.firstOrNull { it.startsWith(prefix) }?.let(picked::add)
        }
        if (picked.isEmpty()) {
            picked.addAll(lines.take(8))
        }
        return picked.distinct().take(10)
    }

    private fun buildSelectionMetaLine(): String {
        val snapshot = runtime.snapshot ?: return "No live snapshot"
        if (runtime.session.state.selectedIds.isEmpty()) {
            return "Viewed faction ${runtime.session.state.viewedFaction?.let { "f$it" } ?: "observer"} · entities ${snapshot.entities.size}"
        }
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val combat = selected.count { it.weaponId != null }
        val workers = selected.count { it.archetype == "worker" }
        val structures = selected.count { it.footprintWidth != null && it.footprintHeight != null }
        return "selected=${selected.size} combat=$combat workers=$workers structures=$structures"
    }

    private fun buildTopEconomyLine(): String {
        val snapshot = runtime.snapshot ?: return "Awaiting snapshot"
        val viewedFaction = runtime.session.state.viewedFaction
        val faction = snapshot.factions.firstOrNull { it.faction == viewedFaction }
        return if (faction != null) {
            "Tick ${snapshot.tick}  F${faction.faction}  minerals ${faction.minerals}  gas ${faction.gas}  vis ${faction.visibleTiles}"
        } else {
            "Tick ${snapshot.tick}  Observer  entities ${snapshot.entities.size}  nodes ${snapshot.resourceNodes.size}"
        }
    }

    private fun buildTopModeLine(): String {
        val mode = runtime.overlayModeLabel()
        val viewed = runtime.session.state.viewedFaction?.let { "f$it" } ?: "observer"
        return "Mode ${mode.uppercase()}  View ${viewed.uppercase()}"
    }

    private fun buildStatusBadgeLine(): String {
        val snapshot = runtime.snapshot ?: return "SYNC"
        if (snapshot.matchEnded) {
            return buildGameState(snapshot, runtime.session.state.viewedFaction)?.title?.uppercase() ?: "ENDED"
        }
        return if (runtime.pauseOverlayVisible) "PAUSED" else if (runtime.playControlState.paused) "SIM PAUSED" else "LIVE"
    }

    private fun buildActionBannerLine(): String {
        runtime.noticeLine()?.removePrefix("notice: ")?.let { return it }
        runtime.currentHudLines().firstOrNull { it.startsWith("hint:") }?.removePrefix("hint: ")?.let { return it }
        val selectionCount = runtime.session.state.selectedIds.size
        return when {
            runtime.buildModeTypeId != null -> "Place ${runtime.buildModeTypeId} with right click"
            runtime.groundMode != null -> "Issue ${runtime.overlayModeLabel()} with right click"
            selectionCount > 0 -> "$selectionCount selected · home to center · esc to clear"
            else -> "Drag to select · right click to command · mouse wheel to zoom"
        }
    }

    private fun makeButton(
        text: String,
        hint: String? = null,
        style: TextButton.TextButtonStyle = assets.primaryButtonStyle(),
        onClick: () -> Unit
    ): TextButton =
        TextButton(text, style).apply {
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        onClick()
                    }

                    override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint(hint ?: text)
                    }

                    override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint(null)
                    }
                }
            )
            addListener(
                object : ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint(null)
                    }
                }
            )
        }

    private inner class GameInputController : InputAdapter() {
        private var dragging = false
        private var panning = false
        private var minimapDragging = false
        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (runtime.pauseOverlayVisible) return false
            when (button) {
                Input.Buttons.MIDDLE -> {
                    panning = true
                    lastX = screenX.toFloat()
                    lastY = screenY.toFloat()
                    return true
                }
                Input.Buttons.LEFT -> {
                    if (runtime.centerFromMinimap(screenX.toFloat(), screenY.toFloat(), Gdx.graphics.width, Gdx.graphics.height)) {
                        minimapDragging = true
                        dragSelection = null
                        return true
                    }
                    startX = screenX.toFloat()
                    startY = screenY.toFloat()
                    lastX = startX
                    lastY = startY
                    dragging = true
                    return true
                }
                Input.Buttons.RIGHT -> {
                    runtime.issueRightClick(screenX.toFloat(), screenY.toFloat(), Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
                    return true
                }
            }
            return false
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (minimapDragging) {
                runtime.centerFromMinimap(screenX.toFloat(), screenY.toFloat(), Gdx.graphics.width, Gdx.graphics.height)
                return true
            }
            if (panning) {
                runtime.panBy(screenX - lastX, screenY - lastY)
                lastX = screenX.toFloat()
                lastY = screenY.toFloat()
                return true
            }
            if (dragging) {
                dragSelection = DragSelectionBox(startX, startY, screenX.toFloat(), screenY.toFloat(), true)
                lastX = screenX.toFloat()
                lastY = screenY.toFloat()
                return true
            }
            return false
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (button == Input.Buttons.MIDDLE) {
                panning = false
                return true
            }
            if (button == Input.Buttons.LEFT && minimapDragging) {
                minimapDragging = false
                return true
            }
            if (button != Input.Buttons.LEFT || !dragging) return false
            val additive = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            if (abs(screenX - startX) >= 6f || abs(screenY - startY) >= 6f) {
                runtime.issueSelectionBox(startX, startY, screenX.toFloat(), screenY.toFloat(), additive)
            } else {
                runtime.issueLeftClick(screenX.toFloat(), screenY.toFloat(), additive)
            }
            dragging = false
            dragSelection = null
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            val factor = if (amountY < 0f) 1.1f else 0.9f
            runtime.zoomAt(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), factor)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.ESCAPE -> {
                    if (runtime.pauseOverlayVisible) {
                        runtime.togglePauseOverlay()
                    } else if (runtime.session.state.selectedIds.isNotEmpty() || runtime.groundMode != null || runtime.buildModeTypeId != null) {
                        runtime.clearSelection()
                    } else {
                        runtime.togglePauseOverlay()
                    }
                }
                Input.Keys.SPACE -> runtime.togglePlayPause()
                Input.Keys.F1 -> runtime.toggleHelpOverlay()
                Input.Keys.TAB -> runtime.toggleDebug()
                Input.Keys.LEFT -> runtime.panBy(28f, 0f)
                Input.Keys.RIGHT -> runtime.panBy(-28f, 0f)
                Input.Keys.UP -> runtime.panBy(0f, 28f)
                Input.Keys.DOWN -> runtime.panBy(0f, -28f)
                Input.Keys.EQUALS, Input.Keys.PLUS -> runtime.zoomAt(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f, 1.1f)
                Input.Keys.MINUS -> runtime.zoomAt(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f, 0.9f)
                Input.Keys.NUM_0 -> {
                    if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)) {
                        runtime.clearControlGroups()
                    } else {
                        runtime.resetCamera()
                    }
                }
                Input.Keys.NUM_1 -> runtime.setViewFaction(1)
                Input.Keys.NUM_2 -> runtime.setViewFaction(2)
                Input.Keys.NUM_3 -> runtime.setViewFaction(null)
                Input.Keys.M -> runtime.executeAction("move", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.A -> runtime.executeAction("attackMove", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.P -> runtime.executeAction("patrol", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.H -> runtime.executeAction("hold", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.B -> runtime.executeAction("build:Depot", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.R -> runtime.executeAction("build:ResourceDepot", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.G -> runtime.executeAction("build:GasDepot", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.U -> runtime.catalog.trainOptions.getOrNull(0)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, Gdx.graphics.height) }
                Input.Keys.I -> runtime.catalog.trainOptions.getOrNull(1)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, Gdx.graphics.height) }
                Input.Keys.O -> runtime.catalog.trainOptions.getOrNull(2)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, Gdx.graphics.height) }
                Input.Keys.L -> runtime.catalog.researchOptions.firstOrNull()?.let { runtime.executeAction("research:${it.typeId}", Gdx.graphics.width, Gdx.graphics.height) }
                Input.Keys.X -> runtime.executeAction("cancelBuild", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.T -> runtime.executeAction("cancelTrain", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.Y -> runtime.executeAction("cancelResearch", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F2 -> runtime.executeAction("selectViewedFaction", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F3 -> runtime.executeAction("selectType", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F4 -> runtime.executeAction("selectRole", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F11 -> runtime.executeAction("selectAll", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F12 -> runtime.executeAction("selectIdleWorkers", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.F5 -> runtime.restartMatch()
                Input.Keys.F6 -> runtime.cycleScenarioAndRestart(-1)
                Input.Keys.F7 -> runtime.cycleScenarioAndRestart(1)
                Input.Keys.F -> runtime.executeAction("selectDamaged", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.V -> runtime.executeAction("selectCombat", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.N -> runtime.executeAction("selectProducers", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.Z -> runtime.executeAction("selectTrainers", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.C -> runtime.executeAction("selectResearchers", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.J -> runtime.executeAction("selectConstruction", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.K -> runtime.executeAction("selectHarvesters", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.Q -> runtime.executeAction("selectReturning", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.E -> runtime.executeAction("selectCargo", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.D -> runtime.executeAction("selectDropoffs", Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.HOME -> runtime.centerOnSelection(Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.END -> runtime.centerOnViewedFaction(Gdx.graphics.width, Gdx.graphics.height)
                Input.Keys.LEFT_BRACKET -> runtime.adjustSpeed(-1)
                Input.Keys.RIGHT_BRACKET -> runtime.adjustSpeed(1)
                Input.Keys.NUM_4 -> handleGroupKey(4)
                Input.Keys.NUM_5 -> handleGroupKey(5)
                Input.Keys.NUM_6 -> handleGroupKey(6)
                Input.Keys.NUM_7 -> handleGroupKey(7)
                Input.Keys.NUM_8 -> handleGroupKey(8)
                Input.Keys.NUM_9 -> handleGroupKey(9)
            }
            return true
        }

        private fun handleGroupKey(group: Int) {
            val assign = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            val add = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
            runtime.handleControlGroup(group, assign = assign, add = add, viewWidth = Gdx.graphics.width, viewHeight = Gdx.graphics.height)
        }
    }
}
