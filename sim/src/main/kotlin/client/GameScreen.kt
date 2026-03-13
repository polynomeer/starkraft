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
    private val edgePanMargin = 20f
    private val edgePanSpeed = 14f
    private val topBar = Table()
    private val economyLabel = Label("", assets.bodyLabelStyle)
    private val topSelectionLabel = Label("", assets.mutedLabelStyle)
    private val modeLabel = Label("", assets.accentLabelStyle)
    private val statusBadgeLabel = Label("", assets.bodyLabelStyle)
    private val statusHeader = Label("Battlefield", assets.titleLabelStyle)
    private val centerHeaderLabel = Label("Selected", assets.titleLabelStyle)
    private val selectionMetaLabel = Label("", assets.mutedLabelStyle)
    private val factionOverviewLabel = Label("", assets.mutedLabelStyle)
    private val hudLinesLabel = Label("", assets.bodyLabelStyle)
    private val selectionLabel = Label("", assets.accentLabelStyle)
    private val centerStatusLabel = Label("", assets.bodyLabelStyle)
    private val queueStatusLabel = Label("", assets.mutedLabelStyle)
    private val queueHeaderLabel = Label("QUEUE", assets.mutedLabelStyle)
    private val selectionRosterLabel = Label("", assets.bodyLabelStyle)
    private val centerFooterLabel = Label("home center  esc clear  tab debug", assets.mutedLabelStyle)
    private val portraitFrame = Table()
    private val portraitLabel = Label("NO\nUNIT", assets.titleLabelStyle)
    private val healthLabel = Label("", assets.bodyLabelStyle)
    private val healthBarBack = Table()
    private val healthBarFill = Table()
    private val selectionGrid = Table()
    private val selectionPager = Table()
    private val selectionPageLabel = Label("", assets.mutedLabelStyle)
    private val controlGroupsLabel = Label("", assets.mutedLabelStyle)
    private val controlGroupButtons = Table()
    private val commandHeaderLabel = Label("Command Deck", assets.titleLabelStyle)
    private val commandHintLabel = Label("", assets.mutedLabelStyle)
    private val buttonTable = Table()
    private val commandScroll = ScrollPane(buttonTable)
    private val actionBanner = Table()
    private val actionBannerLabel = Label("", assets.bodyLabelStyle)
    private val attackWarningTable = Table()
    private val attackWarningLabel = Label("", assets.alertLabelStyle)
    private val minimapFrame = Table()
    private val minimapTitle = Label("Tac Map", assets.titleLabelStyle)
    private val minimapHint = Label("click or drag to move camera", assets.mutedLabelStyle)
    private val bottomHud = Table()
    private val leftHudColumn = Table()
    private val statusCard = Table()
    private val centerCard = Table()
    private val commandCard = Table()
    private val pauseOverlay = Table()
    private val helpOverlay = Table()
    private val screenFade = Table()
    private val helpLabel = Label("", assets.mutedLabelStyle)
    private val footerLabel = Label("", assets.mutedLabelStyle)
    private var dragSelection: DragSelectionBox? = null
    private var selectionPage = 0
    private var productionPage = 0
    private var lastSelectionSignature = ""
    private var focusedSelectionId: Int? = null
    private var screenFadeAlpha = 1f

    init {
        buildHud()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, GameInputController())
    }

    override fun render(delta: Float) {
        val worldViewportHeight = computeWorldViewportHeight(Gdx.graphics.height)
        runtime.tick()
        runtime.ensurePlayableView(Gdx.graphics.width, worldViewportHeight)
        runtime.ensureInitialCamera(Gdx.graphics.width, worldViewportHeight)
        applyEdgePan(worldViewportHeight.toFloat())
        runtime.constrainCamera(Gdx.graphics.width, worldViewportHeight)
        refreshHud()
        if (runtime.consumeAttackAlertSound()) {
            assets.alertSound.play(0.7f)
        }
        if (runtime.consumeCompletionAlertSound()) {
            assets.completeSound.play(0.55f)
        }
        worldRenderer.render(runtime, Gdx.graphics.width, Gdx.graphics.height, worldViewportHeight, dragSelection)
        updateScreenFade(delta)
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
            background = null
            pad(4f, 8f, 4f, 8f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f))
                    pad(2f, 6f, 2f, 6f)
                    add(economyLabel).left()
                }
            ).left().expandX().fillX()
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.78f))
                    pad(2f, 6f, 2f, 6f)
                    add(topSelectionLabel).center()
                }
            ).center().padLeft(10f).padRight(10f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.10f, 0.18f, 0.16f, 0.82f))
                    pad(2f, 6f, 2f, 6f)
                    add(modeLabel).center()
                }
            ).center().padRight(10f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.86f))
                    pad(2f, 6f, 2f, 6f)
                    add(statusBadgeLabel).right()
                }
            ).right()
        }

        minimapFrame.apply {
            background = null
            pad(0f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
        }

        statusCard.apply {
            background = assets.panelDrawable(Color(0.04f, 0.09f, 0.12f, 0.92f))
            pad(12f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(2f).expandY().fillY().padRight(5f)
                    add(statusHeader).left()
                }
            ).left().row()
            add(Table().apply { background = assets.panelDrawable(Color(0.20f, 0.44f, 0.50f, 0.85f)) }).height(2f).expandX().fillX().padTop(6f).row()
            add(factionOverviewLabel).left().expandX().fillX().padTop(6f).row()
            add(hudLinesLabel).left().expandX().fillX().padTop(8f)
        }

        commandCard.apply {
            background = null
            pad(4f)
            top()
            add(
                Table().apply {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.96f))
                            pad(4f, 8f, 3f, 8f)
                            add(commandHeaderLabel).left()
                        }
                    ).left().padRight(4f)
                    add().expandX().fillX()
                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftProductionPage(-1) }).width(22f).height(18f).padRight(3f)
                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftProductionPage(1) }).width(22f).height(18f)
                }
            ).expandX().fillX().row()
            add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.50f, 0.85f)) }).height(2f).expandX().fillX().padTop(6f).row()
            add(
                Table().apply {
                    pad(2f, 2f, 0f, 2f)
                    add(
                        Table().apply {
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(2f).expandY().fillY().padRight(4f)
                            add(commandHintLabel).left().expandX().fillX()
                        }
                    ).expandX().fillX()
                }
            ).expandX().fillX().padTop(3f).row()
            add(actionBanner).left().expandX().fillX().padTop(3f).row()
        }
        buttonTable.top().left()
        buttonTable.defaults().left()
        commandScroll.setFadeScrollBars(false)
        commandScroll.setScrollingDisabled(true, false)
        commandCard.add(commandScroll).top().left().padTop(6f)

        actionBanner.apply {
            background = null
            pad(4f, 8f, 4f, 8f)
            add(actionBannerLabel).center()
        }

        centerCard.apply {
            background = null
            pad(2f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    add(centerHeaderLabel).left()
                    add().expandX().fillX()
                    add(healthLabel).right()
                }
            ).expandX().fillX().padBottom(1f).row()
            add(Table().apply { background = assets.panelDrawable(Color(0.20f, 0.44f, 0.50f, 0.85f)) }).height(1f).expandX().fillX().padTop(2f).row()
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.14f, 0.20f, 0.24f, 0.92f))
                    pad(1f, 4f, 1f, 4f)
                    add(selectionLabel).left().expandX().fillX()
                }
            ).left().expandX().fillX().padTop(2f).row()
            add(Table().apply { background = assets.panelDrawable(Color(0.09f, 0.15f, 0.19f, 0.90f)) }).height(1f).expandX().fillX().padTop(1f).row()
            add(
                Table().apply {
                    add(
                        portraitFrame.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.20f, 0.18f, 0.98f))
                            pad(3f)
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.34f, 0.40f, 0.16f, 0.92f))
                                }
                            ).height(2f).expandX().fillX().row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.08f, 0.11f, 0.09f, 0.96f))
                                    pad(5f, 4f, 3f, 4f)
                                    add(portraitLabel).center().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.16f, 0.24f, 0.28f, 0.76f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(healthLabel).center()
                                        }
                                    ).padTop(4f)
                                }
                            ).expand().fill().padTop(3f)
                        }
                    ).size(62f, 62f).top().left().padRight(4f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f))
                                    pad(1f, 5f, 1f, 5f)
                                    add(selectionMetaLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().row()
                            add(Table().apply { background = assets.panelDrawable(Color(0.10f, 0.15f, 0.19f, 0.88f)) }).height(1f).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.16f, 0.24f, 0.28f, 0.44f))
                                }
                            ).height(2f).expandX().fillX().padTop(1f).row()
                            add(
                                healthBarBack.apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.14f, 0.16f, 1f))
                                    clearChildren()
                                    add(healthBarFill.apply {
                                        background = assets.panelDrawable(Color(0.22f, 0.78f, 0.42f, 1f))
                                    }).expandY().fillY().left()
                                    add().expandX().fillX()
                                }
                            ).width(122f).height(7f).left().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f))
                                    pad(1f, 5f, 1f, 5f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.76f)) }).width(2f).expandY().fillY().padRight(4f)
                                    add(Label("STATUS", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.06f, 0.10f, 0.14f, 0.52f))
                                    pad(2f, 4f, 2f, 4f)
                                    add(centerStatusLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(currentQueueCardTone())
                                    pad(2f, 4f, 2f, 4f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(currentQueueHeaderBackgroundTone())
                                            pad(1f, 4f, 1f, 4f)
                                            add(queueHeaderLabel).left()
                                        }
                                    ).left().padRight(4f)
                                    add(queueStatusLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f))
                                    pad(1f, 5f, 1f, 5f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.90f, 0.52f, 0.74f)) }).width(2f).expandY().fillY().padRight(4f)
                                    add(Label("ROSTER", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(selectionGrid).left().expandX().fillX().padTop(2f).row()
                            add(
                                selectionPager.apply {
                                    clearChildren()
                                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftSelectionPage(-1) }).width(20f).height(18f).padRight(2f)
                                    add(selectionPageLabel).expandX().left()
                                    add(controlGroupButtons).right().padRight(2f)
                                    add(controlGroupsLabel).right().padRight(2f)
                                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftSelectionPage(1) }).width(20f).height(18f)
                                }
                            ).expandX().fillX()
                        }
                    ).expandX().fillX().top()
                }
            ).expandX().fillX().padTop(2f).row()
        }

        bottomHud.apply {
            background = null
            pad(0f, 18f, 8f, 18f)
            add().width(208f).bottom()
            add(wrapHudPanel(centerCard, Color(0.20f, 0.44f, 0.50f, 0.92f))).width(266f).bottom().padRight(10f)
            add().expandX().fillX()
            add(wrapHudPanel(commandCard, Color(0.22f, 0.38f, 0.46f, 0.92f))).width(278f).right().bottom()
        }

        root.top()
        root.add(wrapTopStrip(topBar)).expandX().fillX().pad(12f, 20f, 0f, 20f).row()
        root.add().expand().fill().row()
        root.add(bottomHud).expandX().fillX().bottom()
        stage.addActor(root)

        leftHudColumn.apply {
            setFillParent(true)
            bottom().left()
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(minimapFrame).padLeft(20f).padBottom(12f)
        }
        stage.addActor(leftHudColumn)

        attackWarningTable.apply {
            setFillParent(true)
            top()
            isVisible = false
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.22f, 0.05f, 0.05f, 0.86f))
                    pad(2f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.66f, 0.18f, 0.14f, 0.94f))
                            pad(8f, 18f, 8f, 18f)
                            add(Table().apply { background = assets.panelDrawable(Color(1.00f, 0.84f, 0.70f, 0.82f)) }).width(3f).expandY().fillY().padRight(8f)
                            add(attackWarningLabel).center()
                        }
                    )
                }
            ).padTop(18f)
        }
        stage.addActor(attackWarningTable)

        pauseOverlay.apply {
            setFillParent(true)
            isVisible = false
            background = assets.panelDrawable(Color(0.03f, 0.04f, 0.06f, 0.86f))
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.05f, 0.09f, 0.13f, 0.96f))
                    pad(12f)
                    defaults().pad(5f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.16f, 0.28f, 0.34f, 0.92f))
                            pad(4f, 10f, 4f, 10f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(3f).expandY().fillY().padRight(6f)
                            add(Label("PAUSED", assets.titleLabelStyle)).left()
                        }
                    ).width(248f).left().row()
                    add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.82f)) }).height(2f).width(248f).padBottom(8f).row()
                    add(makeButton("Resume", style = assets.primaryButtonStyle()) { runtime.togglePauseOverlay() }).width(248f).row()
                    add(makeButton("Toggle Sim Pause", style = assets.secondaryButtonStyle()) { runtime.togglePlayPause() }).width(248f).row()
                    add(makeButton("Restart Match", style = assets.secondaryButtonStyle()) { runtime.restartMatch() }).width(248f).row()
                    add(makeButton("Save Quick", style = assets.subtleButtonStyle()) { runtime.savePreset("quick") }).width(248f).row()
                    add(makeButton("Load Quick", style = assets.subtleButtonStyle()) { runtime.loadPreset("quick") }).width(248f).row()
                    add(makeButton("Save Alt", style = assets.subtleButtonStyle()) { runtime.savePreset("alt") }).width(248f).row()
                    add(makeButton("Load Alt", style = assets.subtleButtonStyle()) { runtime.loadPreset("alt") }).width(248f).row()
                    add(makeButton("Main Menu", style = assets.subtleButtonStyle()) {
                        runtime.togglePauseOverlay()
                        game.openMainMenu()
                    }).width(248f).row()
                    add(makeButton("Quit", style = assets.subtleButtonStyle()) { Gdx.app.exit() }).width(248f).row()
                }
            )
        }
        stage.addActor(pauseOverlay)

        helpOverlay.apply {
            setFillParent(true)
            isVisible = false
            top().left()
            pad(16f)
            background = assets.panelDrawable(Color(0.03f, 0.05f, 0.08f, 0.78f))
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.05f, 0.09f, 0.13f, 0.96f))
                    pad(9f, 11f, 9f, 11f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.16f, 0.28f, 0.34f, 0.92f))
                            pad(4f, 10f, 4f, 10f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(3f).expandY().fillY().padRight(6f)
                            add(Label("HELP", assets.titleLabelStyle)).left()
                        }
                    ).left().row()
                    add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.82f)) }).height(2f).expandX().fillX().padTop(6f).padBottom(8f).row()
                    add(helpLabel).left().top()
                }
            ).left().top()
        }
        stage.addActor(helpOverlay)

        screenFade.apply {
            setFillParent(true)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            background = assets.panelDrawable(Color(0f, 0f, 0f, 1f))
            color.a = screenFadeAlpha
        }
        stage.addActor(screenFade)
    }

    private fun refreshHud() {
        val snapshot = runtime.snapshot
        val width = Gdx.graphics.width
        val height = Gdx.graphics.height
        val minimapBounds = gdxMiniMapBounds(width, height)
        val minimapWidth = minimapBounds.width
        val minimapHeight = minimapBounds.height
        val centerWidth = (width * 0.188f).coerceIn(228f, 292f)
        val commandWidth = (width * 0.186f).coerceIn(244f, 300f)
        val commandHeight = (height * 0.096f).coerceIn(82f, 106f)
        val commandButtonHeight = if (width >= 1440) 23f else 21f
        val commandColumns = 3
        val commandCellWidth = (commandWidth / commandColumns) - 2f
        val commandActorWidth = commandCellWidth - 18f
        val centerHeight = (height * 0.146f).coerceIn(120f, 152f)
        val commandShellHeight = (commandHeight + 28f).coerceIn(108f, 134f)
        val minimapShellHeight = minimapHeight
        val hudShellHeight = maxOf(minimapShellHeight, centerHeight, commandShellHeight)
        val unifiedPanelHeight = hudShellHeight
        selectionLabel.setWrap(true)
        selectionMetaLabel.setWrap(true)
        factionOverviewLabel.setWrap(true)
        centerStatusLabel.setWrap(true)
        queueStatusLabel.setWrap(true)
        selectionRosterLabel.setWrap(true)
        hudLinesLabel.setWrap(true)
        footerLabel.setWrap(true)
        centerFooterLabel.setWrap(true)
        minimapTitle.setText("Tac Map  ${runtime.session.state.viewedFaction?.let { "F$it" } ?: "Obs"}")
        selectionLabel.setWidth(centerWidth)
        selectionMetaLabel.setWidth(centerWidth)
        centerStatusLabel.setWidth(centerWidth)
        queueStatusLabel.setWidth(centerWidth)
        selectionRosterLabel.setWidth(centerWidth)
        hudLinesLabel.setWidth(minimapWidth)
        factionOverviewLabel.setWidth(minimapWidth)
        footerLabel.setWidth(minimapWidth)
        centerFooterLabel.setWidth(centerWidth)
        minimapHint.setWidth(minimapWidth - 20f)
        minimapFrame.setSize(minimapWidth, minimapHeight)
        centerCard.setSize(centerWidth, unifiedPanelHeight - 6f)
        commandCard.setSize(commandWidth, unifiedPanelHeight - 6f)
        commandScroll.setSize(commandWidth - 8f, commandHeight)
        bottomHud.setHeight(hudShellHeight)
        buttonTable.defaults().pad(0f, 0f, 4f, 4f)
        selectionLabel.setText(buildSelectionHeadline())
        selectionMetaLabel.setText(buildSelectionMetaLine())
        centerStatusLabel.setText(buildCenterStatusLine())
        queueStatusLabel.setText(buildQueueStatusLine())
        queueHeaderLabel.setText(buildQueueHeaderLine())
        centerStatusLabel.color = currentCenterStatusTone()
        queueHeaderLabel.color = currentQueueHeaderTone()
        queueStatusLabel.color = currentQueueStatusTone()
        selectionRosterLabel.setText(buildSelectionRosterLine())
        factionOverviewLabel.setText(buildFactionOverviewLine())
        portraitLabel.setText(buildPortraitText())
        healthLabel.setText(buildHealthLine())
        updateHealthBar()
        rebuildSelectionGrid()
        hudLinesLabel.setText(buildStatusSummaryLines().joinToString("\n"))
        footerLabel.setText("LMB select  RMB order  drag box select")
        statusHeader.setText("Battlefield")
        centerHeaderLabel.setText(if (runtime.session.state.selectedIds.isEmpty()) "Selected" else "Selection")
        val groupedButtons = commandGroups(runtime.buttonModels())
        commandHeaderLabel.setText(buildCommandHeader(groupedButtons))
        economyLabel.setText(buildTopEconomyLine())
        topSelectionLabel.setText(buildTopSelectionLine())
        modeLabel.setText(buildTopModeLine())
        statusBadgeLabel.setText(buildStatusBadgeLine())
        statusBadgeLabel.color = currentStatusBadgeTone()
        val actionBannerText = buildActionBannerLine()
        actionBannerLabel.setText(actionBannerText)
        commandHintLabel.setText(buildCommandHintLine())
        attackWarningLabel.setText(runtime.attackWarningLine() ?: "")
        attackWarningTable.isVisible = runtime.attackWarningLine() != null
        centerFooterLabel.setText(buildCenterFooterLine())
        syncSelectionPage(snapshot)
        updateSelectionPager(snapshot)
        pauseOverlay.isVisible = runtime.pauseOverlayVisible
        helpOverlay.isVisible = runtime.helpOverlayVisible
        helpLabel.setText(buildHelpOverlayLines(runtime.helpOverlayVisible).joinToString("\n"))
        val showActionBanner = actionBannerText.isNotBlank()
        actionBanner.isVisible = showActionBanner
        actionBanner.background = if (showActionBanner) assets.panelDrawable(currentActionBannerTone()) else null
        actionBanner.pad(if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f, if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f)
        bottomHud.invalidateHierarchy()
        buttonTable.clearChildren()
        groupedButtons.forEachIndexed { groupIndex, group ->
            if (group.second.isEmpty()) return@forEachIndexed
            buttonTable.add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.02f, 0.05f, 0.08f, 0.98f))
                    pad(4f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(commandGroupHeaderTone(group.first))
                            pad(2f, 6f, 2f, 6f)
                            add(Table().apply { background = assets.panelDrawable(commandGroupAccentTone(group.first)) }).width(2f).expandY().fillY().padRight(4f)
                            add(Label(group.first.uppercase(), assets.accentLabelStyle)).left()
                        }
                    ).colspan(commandColumns).left().padBottom(4f).row()
                    group.second.forEachIndexed { index, button ->
                        val actor = makeButton(
                            commandButtonLabel(button),
                            runtime.actionHint(button.actionId),
                            commandButtonStyle(button.actionId)
                        ) { runtime.executeAction(button.actionId, Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height)) }
                        actor.isDisabled = !runtime.isActionEnabled(button.actionId)
                        actor.isChecked = runtime.isActionActive(button.actionId)
                        actor.color =
                            when {
                                actor.isDisabled -> Color(0.66f, 0.70f, 0.74f, 0.55f)
                                actor.isChecked -> Color(1.00f, 0.98f, 0.82f, 1f)
                                else -> Color.WHITE
                            }
                        val cardTone =
                            when {
                                button.actionId.startsWith("build") ->
                                    Color(0.30f, 0.24f, 0.08f, 0.98f)
                                button.actionId.startsWith("train") || button.actionId.startsWith("research") ->
                                    Color(0.22f, 0.20f, 0.10f, 0.98f)
                                button.actionId == "move" || button.actionId == "hold" ->
                                    Color(0.07f, 0.24f, 0.28f, 0.98f)
                                button.actionId == "patrol" ->
                                    Color(0.10f, 0.20f, 0.30f, 0.98f)
                                button.actionId == "attackMove" ->
                                    Color(0.36f, 0.14f, 0.10f, 0.98f)
                                else -> Color(0.16f, 0.18f, 0.22f, 0.98f)
                            }
                        val frameTone =
                            when {
                                actor.isDisabled -> Color(0.05f, 0.06f, 0.08f, 0.94f)
                                actor.isChecked -> Color(0.30f, 0.34f, 0.16f, 0.98f)
                                else -> cardTone
                            }
                        val shellTone =
                            when {
                                actor.isDisabled -> Color(0.01f, 0.03f, 0.05f, 0.88f)
                                actor.isChecked -> Color(0.38f, 0.32f, 0.12f, 0.98f)
                                else -> Color(0.01f, 0.03f, 0.05f, 0.98f)
                            }
                        add(
                            Table().apply {
                                background = assets.panelDrawable(shellTone)
                                pad(1f)
                                add(
                                    Table().apply {
                                        background = assets.panelDrawable(frameTone)
                                        pad(1f)
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                        when {
                                                            actor.isDisabled -> Color(0.16f, 0.18f, 0.20f, 0.28f)
                                                            actor.isChecked -> Color(1.00f, 0.90f, 0.44f, 0.82f)
                                                            button.actionId == "attackMove" -> Color(1.00f, 0.54f, 0.30f, 0.72f)
                                                            button.actionId.startsWith("build:") -> Color(0.96f, 0.78f, 0.34f, 0.72f)
                                                            button.actionId.startsWith("train:") || button.actionId.startsWith("research:") -> Color(0.64f, 0.78f, 1.00f, 0.72f)
                                                            else -> Color(0.56f, 0.88f, 0.96f, 0.64f)
                                                        }
                                                    )
                                            }
                                        ).width(2f).expandY().fillY().padRight(2f)
                                        add(
                                            buildCommandGlyph(
                                                actionId = button.actionId,
                                                disabled = actor.isDisabled,
                                                checked = actor.isChecked
                                            )
                                        ).size(10f, 10f).left().padRight(4f)
                                        add(actor).width(commandActorWidth).height(commandButtonHeight).left()
                                    }
                                ).expand().fill()
                            }
                        ).width(commandCellWidth).left()
                        if ((index + 1) % commandColumns == 0) {
                            row()
                        }
                    }
                    if (group.second.size % commandColumns != 0) {
                        row()
                    }
                }
            ).colspan(commandColumns).left().fillX().expandX().row()
            if (groupIndex != groupedButtons.lastIndex) {
                buttonTable.add().height(0f).colspan(commandColumns).row()
            }
        }
        if (runtime.debugVisible && snapshot != null) {
            buttonTable.add(Label("debug: entities=${snapshot.entities.size} resources=${snapshot.resourceNodes.size}", assets.mutedLabelStyle)).colspan(commandColumns).left().padTop(6f).row()
        }
    }

    private fun buildStatusSummaryLines(): List<String> {
        val lines = runtime.currentHudLines()
        val preferredPrefixes =
            listOf(
                "economy:",
                "selection classes:",
                "selection health:",
                "selection orders:",
                "selection tasks:",
                "selection path:",
                "production:",
                "research:",
                "fog:",
                "last ack:"
            )
        val picked = ArrayList<String>()
        for (prefix in preferredPrefixes) {
            lines.firstOrNull { it.startsWith(prefix) }?.let(picked::add)
        }
        if (runtime.session.state.viewedFaction != null && buildTopEconomyLine().contains("vis 0")) {
            picked.add(0, "warning: current faction has no vision, press 1/2/3 to switch view")
        }
        if (picked.isEmpty()) {
            picked.addAll(lines.take(6))
        }
        return picked.distinct().take(if (runtime.debugVisible) 10 else 7)
    }

    private fun buildSelectionHeadline(): String {
        val raw = runtime.session.state.viewState.selectionHudLine
        if (!raw.isNullOrBlank() && !raw.equals("selection hud: none", ignoreCase = true)) {
            return raw
        }
        val snapshot = runtime.snapshot ?: return "Awaiting view"
        return if (runtime.session.state.selectedIds.isEmpty()) {
            val faction = runtime.session.state.viewedFaction?.let { "f$it" } ?: "observer"
            "$faction · ${snapshot.entities.size} live"
        } else {
            "#${runtime.session.state.selectedIds.first()} · ${runtime.session.state.selectedIds.size}"
        }
    }

    private fun buildPortraitText(): String {
        val snapshot = runtime.snapshot ?: return "NO\nDATA"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) {
            return runtime.session.state.viewedFaction?.let { "F$it\nVIEW" } ?: "OBS\nVIEW"
        }
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        return if (selected.size == 1) {
            "${(lead.typeId ?: "UNIT").take(8).uppercase()}\n${(lead.archetype ?: "ROLE").take(8).uppercase()}"
        } else {
            "${selected.size}\n${(lead.typeId ?: "UNIT").take(7).uppercase()}"
        }
    }

    private fun buildHealthLine(): String {
        val snapshot = runtime.snapshot ?: return "HP -"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "HP -"
        val hp = selected.sumOf { it.hp }
        val maxHp = selected.sumOf { it.maxHp.coerceAtLeast(1) }
        return "HP $hp/$maxHp"
    }

    private fun buildSelectionRosterLine(): String {
        val snapshot = runtime.snapshot ?: return "No roster"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) {
            return "No card"
        }
        val counts =
            selected
                .groupingBy { it.typeId ?: "Unknown" }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(4)
                .joinToString("   ") { "${it.key}:${it.value}" }
        return "Ros $counts"
    }

    private fun buildFactionOverviewLine(): String {
        val snapshot = runtime.snapshot ?: return "No telemetry"
        if (snapshot.factions.isEmpty()) return "No faction data"
        return snapshot.factions.joinToString("\n") { faction ->
            val viewed = if (runtime.session.state.viewedFaction == faction.faction) " <" else ""
            "F${faction.faction}  M${faction.minerals}  G${faction.gas}  Vis${faction.visibleTiles}$viewed"
        }
    }

    private fun wrapHudPanel(content: Table, tone: Color): Table =
        Table().apply {
            background = null
            add(Table().apply { background = assets.panelDrawable(tone) }).height(2f).expandX().fillX().row()
            add(
                Table().apply {
                    add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.WHITE, 0.08f)) }).width(2f).expandY().fillY()
                    add(
                        Table().apply {
                            background = null
                            pad(4f, 6f, 6f, 6f)
                            add(content).expand().fill()
                        }
                    ).expand().fill()
                    add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.BLACK, 0.16f)) }).width(2f).expandY().fillY()
                }
            ).expand().fill().row()
            add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.BLACK, 0.12f)) }).height(2f).expandX().fillX()
        }

    private fun wrapTopStrip(content: Table): Table =
        Table().apply {
            background = null
            add(content).expandX().fillX().row()
            add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.78f)) }).height(1f).expandX().fillX().padTop(3f)
        }

    private fun buildCommandGlyph(actionId: String, disabled: Boolean, checked: Boolean): Table {
        val frameColor =
            when {
                disabled -> Color(0.04f, 0.05f, 0.07f, 0.94f)
                checked -> Color(0.34f, 0.40f, 0.16f, 0.98f)
                else -> Color(0.05f, 0.09f, 0.12f, 0.95f)
            }
        val accentColor =
            when {
                disabled -> Color(1f, 1f, 1f, 0.04f)
                checked -> Color(1f, 0.96f, 0.62f, 0.18f)
                actionId == "attackMove" -> Color(1.00f, 0.62f, 0.38f, 0.22f)
                actionId.startsWith("build:") -> Color(1.00f, 0.82f, 0.44f, 0.18f)
                actionId.startsWith("train:") || actionId.startsWith("research:") -> Color(0.82f, 0.88f, 1.00f, 0.18f)
                else -> Color(1f, 1f, 1f, 0.10f)
            }
        return Table().apply {
            background = assets.panelDrawable(frameColor)
            pad(1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(accentColor)
                    when {
                        actionId == "attackMove" -> {
                            add(Table().apply { background = assets.panelDrawable(Color(1.00f, 0.72f, 0.42f, if (disabled) 0.16f else 0.82f)) }).width(2f).height(8f).padRight(1f)
                            add(Table().apply { background = assets.panelDrawable(Color(1.00f, 0.72f, 0.42f, if (disabled) 0.16f else 0.82f)) }).width(6f).height(2f)
                        }
                        actionId == "move" || actionId == "patrol" || actionId == "hold" -> {
                            add(Table().apply { background = assets.panelDrawable(Color(0.62f, 0.90f, 0.96f, if (disabled) 0.14f else 0.78f)) }).width(6f).height(2f).row()
                            add(Table().apply { background = assets.panelDrawable(Color(0.62f, 0.90f, 0.96f, if (disabled) 0.14f else 0.78f)) }).width(2f).height(5f)
                        }
                        actionId.startsWith("build:") -> {
                            add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.82f, 0.42f, if (disabled) 0.14f else 0.82f)) }).size(6f, 2f).row()
                            add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.82f, 0.42f, if (disabled) 0.14f else 0.82f)) }).size(2f, 6f)
                        }
                        actionId.startsWith("train:") || actionId.startsWith("research:") -> {
                            add(Table().apply { background = assets.panelDrawable(Color(0.72f, 0.82f, 1.00f, if (disabled) 0.14f else 0.82f)) }).width(7f).height(2f).row()
                            add(Table().apply { background = assets.panelDrawable(Color(0.72f, 0.82f, 1.00f, if (disabled) 0.14f else 0.82f)) }).width(5f).height(2f)
                        }
                        else -> {
                            add(Table().apply { background = assets.panelDrawable(Color(1f, 1f, 1f, if (disabled) 0.10f else 0.48f)) }).size(4f, 4f)
                        }
                    }
                }
            ).size(10f, 10f)
        }
    }

    private fun wrapMinimapPanel(content: Table): Table =
        Table().apply {
            background = assets.panelDrawable(Color(0.01f, 0.03f, 0.05f, 0.82f))
            pad(1f, 1f, 3f, 1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.14f, 0.21f, 0.27f, 0.28f))
                    pad(1f, 1f, 3f, 1f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.26f, 0.34f, 0.38f, 0.95f))
                                }
                            ).size(16f, 4f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.09f, 0.14f, 0.17f, 0.95f))
                                }
                            ).width(8f).height(2f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.18f, 0.24f, 0.28f, 0.95f))
                                }
                            ).width(4f).height(8f).left().padBottom(1f).row()
                            add(content).expand().fill().row()
                            add(
                                Table().apply {
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.08f, 0.12f, 0.15f, 0.95f))
                                        }
                                    ).width(26f).height(2f).right().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.20f, 0.28f, 0.32f, 0.95f))
                                        }
                                    ).width(10f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.15f, 0.18f, 0.95f))
                                        }
                                    ).width(14f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.24f, 0.30f, 0.34f, 0.95f))
                                        }
                                    ).width(5f).height(5f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.12f, 0.18f, 0.21f, 0.95f))
                                        }
                                    ).width(12f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.18f, 0.24f, 0.28f, 0.95f))
                                        }
                                    ).width(4f).height(6f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.08f, 0.12f, 0.15f, 0.95f))
                                        }
                                    ).width(8f).height(2f).right().padTop(1f)
                                }
                            ).right().padTop(1f)
                        }
                    ).expand().fill()
                }
            ).expand().fill()
        }

    private fun buildCommandHeader(groups: List<Pair<String, List<ClientCommandButton>>>): String {
        val productionGroup = groups.firstOrNull { it.first == "Production" }?.second.orEmpty()
        val pageCount = ((productionGroup.size + 5) / 6).coerceAtLeast(1)
        productionPage = productionPage.coerceIn(0, pageCount - 1)
        return if (productionGroup.size > 6) {
            "Cmd ${runtime.overlayModeLabel()} ${productionPage + 1}/$pageCount"
        } else {
            "Cmd ${runtime.overlayModeLabel()}"
        }
    }

    private fun buildCenterStatusLine(): String {
        val snapshot = runtime.snapshot ?: return "No status"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "No status"
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        val statusBits = buildList {
            lead.activeOrder?.takeIf { it.isNotBlank() }?.let { add("ord ${it.lowercase()}") }
            if (lead.orderQueueSize > 0) add("q ${lead.orderQueueSize}")
            if (lead.pathRemainingNodes > 0) add("path ${lead.pathRemainingNodes}")
            lead.activeProductionType?.let { add("prod $it") }
            lead.activeResearchTech?.let { add("tech $it") }
            if (lead.underConstruction) add("construct")
            lead.harvestPhase?.let { add("harvest ${it.lowercase()}") }
            if (lead.harvestCargoAmount != null && lead.harvestCargoAmount > 0) {
                add("cargo ${lead.harvestCargoKind ?: "res"}:${lead.harvestCargoAmount}")
            }
        }
        return if (statusBits.isEmpty()) "Ready" else statusBits.joinToString(" · ")
    }

    private fun buildCenterFooterLine(): String =
        when {
            runtime.buildModeTypeId != null -> "right click place  esc cancel build"
            runtime.groundMode != null -> "right click confirm order  esc cancel"
            runtime.session.state.selectedIds.isNotEmpty() -> "home center  esc clear  shift add"
            else -> "drag select  right click order  middle drag pan"
        }

    private fun buildQueueStatusLine(): String {
        val snapshot = runtime.snapshot ?: return "No queue"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "Idle"
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        val parts = buildList {
            if (lead.productionQueueSize > 0 || lead.activeProductionType != null) {
                add(
                    buildString {
                        append("prod ")
                        append(lead.activeProductionType ?: "queue")
                        if (lead.productionQueueSize > 0) append(" x${lead.productionQueueSize}")
                        if (lead.activeProductionRemainingTicks > 0) append(" ${lead.activeProductionRemainingTicks}t")
                    }
                )
            }
            if (lead.researchQueueSize > 0 || lead.activeResearchTech != null) {
                add(
                    buildString {
                        append("res ")
                        append(lead.activeResearchTech ?: "queue")
                        if (lead.researchQueueSize > 0) append(" x${lead.researchQueueSize}")
                        if (lead.activeResearchRemainingTicks > 0) append(" ${lead.activeResearchRemainingTicks}t")
                    }
                )
            }
            if (lead.underConstruction) {
                add(
                    buildString {
                        append("build")
                        lead.constructionRemainingTicks?.let { append(" ${it}t") }
                    }
                )
            }
        }
        return if (parts.isEmpty()) "Idle" else parts.joinToString("  |  ")
    }

    private fun buildQueueHeaderLine(): String {
        val snapshot = runtime.snapshot ?: return "QUEUE"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "QUEUE"
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        return when {
            lead.activeResearchTech != null || lead.researchQueueSize > 0 -> "RESEARCH"
            lead.activeProductionType != null || lead.productionQueueSize > 0 -> "PRODUCTION"
            lead.underConstruction -> "CONSTRUCT"
            else -> "QUEUE"
        }
    }

    private fun updateHealthBar() {
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        val ratio =
            if (selected.isEmpty()) {
                0f
            } else {
                val hp = selected.sumOf { it.hp }.toFloat()
                val maxHp = selected.sumOf { it.maxHp.coerceAtLeast(1) }.toFloat().coerceAtLeast(1f)
                (hp / maxHp).coerceIn(0f, 1f)
            }
        val barWidth = 180f
        val fillWidth = (barWidth * ratio).coerceAtLeast(if (ratio > 0f) 4f else 0f)
        healthBarFill.clearChildren()
        healthBarBack.clearChildren()
        healthBarFill.background =
            assets.panelDrawable(
                when {
                    ratio >= 0.66f -> Color(0.22f, 0.78f, 0.42f, 1f)
                    ratio >= 0.33f -> Color(0.87f, 0.73f, 0.20f, 1f)
                    else -> Color(0.84f, 0.30f, 0.25f, 1f)
                }
            )
        healthBarBack.add(healthBarFill).width(fillWidth).expandY().fillY().left()
        healthBarBack.add().expandX().fillX()
    }

    private fun rebuildSelectionGrid() {
        selectionGrid.clearChildren()
        val snapshot = runtime.snapshot ?: return
        val selectedEntities = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val pageSize = 8
        val pageCount = ((selectedEntities.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        selectionPage = selectionPage.coerceIn(0, pageCount - 1)
        val pageStart = selectionPage * pageSize
        val selected = selectedEntities.drop(pageStart).take(pageSize)
        if (selected.isEmpty()) {
            selectionGrid.add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.12f, 0.16f, 0.74f))
                    pad(4f, 8f, 4f, 8f)
                    add(Label("No slots", assets.mutedLabelStyle)).left()
                }
            ).left()
            return
        }
        selectionGrid.defaults().pad(0f, 2f, 2f, 0f)
        selected.forEachIndexed { index, entity ->
            selectionGrid.add(buildSelectionSlot(entity)).size(38f, 38f)
            if ((index + 1) % 4 == 0) {
                selectionGrid.row()
            }
        }
    }

    private fun buildSelectionSlot(entity: EntitySnapshot): Table {
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        val focused = focusedSelectionId == entity.id || (focusedSelectionId == null && runtime.session.state.selectedIds.firstOrNull() == entity.id)
        val tone =
            when {
                entity.weaponId != null -> Color(0.17f, 0.31f, 0.39f, 0.96f)
                entity.footprintWidth != null -> Color(0.28f, 0.24f, 0.15f, 0.96f)
                else -> Color(0.16f, 0.25f, 0.18f, 0.96f)
            }
        val hpColor =
            when {
                hpRatio >= 0.66f -> Color(0.22f, 0.78f, 0.42f, 1f)
                hpRatio >= 0.33f -> Color(0.87f, 0.73f, 0.20f, 1f)
                else -> Color(0.84f, 0.30f, 0.25f, 1f)
            }
        val shortName = (entity.typeId ?: "?").take(3).uppercase()
        return Table().apply {
            background = assets.panelDrawable(if (focused) Color(0.24f, 0.34f, 0.12f, 0.96f) else Color(0.08f, 0.12f, 0.16f, 0.92f))
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            pad(1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(if (focused) Color(0.34f, 0.42f, 0.10f, 0.98f) else tone)
                    pad(if (focused) 2f else 1.5f)
                    add(Table().apply { background = assets.panelDrawable(if (focused) Color(0.98f, 0.92f, 0.56f, 0.72f) else Color(1f, 1f, 1f, 0.08f)) }).height(2f).expandX().fillX().padBottom(2f).row()
                    add(Label(shortName, assets.titleLabelStyle)).center().expandX().fillX().row()
                    add(Label(entity.id.toString(), assets.mutedLabelStyle)).center().row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(if (focused) Color(0.16f, 0.20f, 0.08f, 1f) else Color(0.09f, 0.11f, 0.13f, 1f))
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(hpColor)
                                }
                            ).width(20f * hpRatio.coerceIn(0f, 1f)).height(4f).left()
                            add().expandX().fillX()
                        }
                    ).width(20f).height(4f).padTop(2f)
                }
            ).expand().fill()
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        if (tapCount >= 2) {
                            runtime.session.replaceSelection(intArrayOf(entity.id))
                            focusedSelectionId = entity.id
                            runtime.centerOnSelection(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                        } else {
                            focusedSelectionId = entity.id
                        }
                    }

                    override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint("Focus ${entity.typeId} #${entity.id}")
                    }

                    override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint(null)
                    }
                }
            )
        }
    }

    private fun syncSelectionPage(snapshot: ClientSnapshot?) {
        val signature = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }?.joinToString(",") { it.id.toString() }.orEmpty()
        if (signature != lastSelectionSignature) {
            selectionPage = 0
            val selectedIds = runtime.session.state.selectedIds
            if (focusedSelectionId != null && focusedSelectionId !in selectedIds) {
                focusedSelectionId = null
            }
            lastSelectionSignature = signature
        }
        if (focusedSelectionId == null) {
            focusedSelectionId = runtime.session.state.selectedIds.firstOrNull()
        }
    }

    private fun updateSelectionPager(snapshot: ClientSnapshot?) {
        val selectedCount = snapshot?.entities?.count { it.id in runtime.session.state.selectedIds } ?: 0
        val pageSize = 8
        val pageCount = ((selectedCount + pageSize - 1) / pageSize).coerceAtLeast(1)
        selectionPage = selectionPage.coerceIn(0, pageCount - 1)
        selectionPageLabel.setText(if (selectedCount == 0) "Page 0/0" else "Page ${selectionPage + 1}/$pageCount")
        controlGroupsLabel.setText(runtime.controlGroupSummaryLine() ?: "Groups empty")
        rebuildControlGroupButtons()
    }

    private fun shiftSelectionPage(delta: Int) {
        val snapshot = runtime.snapshot ?: return
        val selectedCount = snapshot.entities.count { it.id in runtime.session.state.selectedIds }
        val pageSize = 8
        val pageCount = ((selectedCount + pageSize - 1) / pageSize).coerceAtLeast(1)
        selectionPage = (selectionPage + delta).coerceIn(0, pageCount - 1)
        rebuildSelectionGrid()
        updateSelectionPager(snapshot)
    }

    private fun rebuildControlGroupButtons() {
        controlGroupButtons.clearChildren()
        controlGroupButtons.defaults().padRight(2f)
        runtime.controlGroupSizes().forEach { (group, count) ->
            controlGroupButtons.add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.12f, 0.16f, 0.92f))
                    pad(1f)
                    add(
                        makeButton("$group:$count", style = assets.subtleButtonStyle()) {
                            runtime.handleControlGroup(group, assign = false, add = false, viewWidth = Gdx.graphics.width, viewHeight = computeWorldViewportHeight(Gdx.graphics.height))
                        }
                    ).height(18f)
                }
            ).height(18f)
        }
    }

    private fun updateScreenFade(delta: Float) {
        if (screenFadeAlpha <= 0f) {
            screenFade.isVisible = false
            return
        }
        screenFadeAlpha = (screenFadeAlpha - (delta * 1.8f)).coerceAtLeast(0f)
        screenFade.isVisible = screenFadeAlpha > 0f
        screenFade.color.a = screenFadeAlpha
    }

    private fun resolveFocusedEntity(snapshot: ClientSnapshot, selected: List<EntitySnapshot>): EntitySnapshot? =
        focusedSelectionId?.let { focusId -> selected.firstOrNull { it.id == focusId } }

    private fun applyEdgePan(worldViewportHeight: Float) {
        if (runtime.pauseOverlayVisible || runtime.helpOverlayVisible) return
        if (Gdx.input.isTouched) return
        var deltaX = 0f
        var deltaY = 0f
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        val width = Gdx.graphics.width.toFloat()
        val height = worldViewportHeight
        if (mouseX <= edgePanMargin) deltaX += edgePanSpeed
        if (mouseX >= width - edgePanMargin) deltaX -= edgePanSpeed
        if (mouseY <= edgePanMargin) deltaY += edgePanSpeed
        if (mouseY >= height - edgePanMargin) deltaY -= edgePanSpeed
        if (deltaX != 0f || deltaY != 0f) {
            runtime.panBy(deltaX, deltaY)
        }
    }

    private fun computeWorldViewportHeight(screenHeight: Int): Int {
        val reservedHudHeight = (screenHeight * 0.128f).coerceIn(96f, 120f)
        return (screenHeight - reservedHudHeight).toInt().coerceAtLeast(240)
    }

    private fun buildSelectionMetaLine(): String {
        val snapshot = runtime.snapshot ?: return "No live snapshot"
        if (runtime.session.state.selectedIds.isEmpty()) {
            return "${runtime.session.state.viewedFaction?.let { "f$it" } ?: "obs"} · ${snapshot.entities.size}"
        }
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val combat = selected.count { it.weaponId != null }
        val workers = selected.count { it.archetype == "worker" }
        val structures = selected.count { it.footprintWidth != null && it.footprintHeight != null }
        return "${selected.size} sel · c$combat · w$workers · s$structures"
    }

    private fun buildTopEconomyLine(): String {
        val snapshot = runtime.snapshot ?: return "Awaiting"
        val viewedFaction = runtime.session.state.viewedFaction
        val faction = snapshot.factions.firstOrNull { it.faction == viewedFaction }
        return if (faction != null) {
            "T${snapshot.tick}  F${faction.faction}  M${faction.minerals}  G${faction.gas}  V${faction.visibleTiles}"
        } else {
            "T${snapshot.tick}  OBS  E${snapshot.entities.size}  N${snapshot.resourceNodes.size}"
        }
    }

    private fun buildTopModeLine(): String {
        val mode = runtime.overlayModeLabel()
        val viewed = runtime.session.state.viewedFaction?.let { "f$it" } ?: "observer"
        return "${mode.uppercase()}  ${viewed.uppercase()}"
    }

    private fun buildTopSelectionLine(): String {
        val snapshot = runtime.snapshot ?: return "No sel"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "No sel"
        val lead = selected.first()
        return if (selected.size == 1) {
            "${lead.typeId} ${lead.hp}/${lead.maxHp}"
        } else {
            "${selected.size}x ${lead.typeId}"
        }
    }

    private fun buildStatusBadgeLine(): String {
        val snapshot = runtime.snapshot ?: return "SYNC"
        if (snapshot.matchEnded) {
            return buildGameState(snapshot, runtime.session.state.viewedFaction)?.title?.uppercase() ?: "ENDED"
        }
        return if (runtime.pauseOverlayVisible) "PAUSED" else if (runtime.playControlState.paused) "SIM HOLD" else "LIVE"
    }

    private fun buildActionBannerLine(): String {
        runtime.noticeLine()?.removePrefix("notice: ")?.let { return it }
        val selectionCount = runtime.session.state.selectedIds.size
        return when {
            runtime.buildModeTypeId != null -> "Place ${runtime.buildModeTypeId}  RMB confirm"
            runtime.groundMode != null -> "${runtime.overlayModeLabel().uppercase()}  RMB confirm"
            selectionCount > 0 -> ""
            else -> ""
        }
    }

    private fun buildCommandHintLine(): String =
        runtime.hoverHintLine()
            ?: when {
                runtime.buildModeTypeId != null -> "Build armed"
                runtime.groundMode != null -> "Order armed"
                runtime.session.state.selectedIds.isNotEmpty() -> "Cmd ready"
                else -> "Select to unlock orders"
            }

    private fun currentActionBannerTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.16f, 0.24f, 0.30f, 0.76f)
            runtime.groundMode != null -> Color(0.14f, 0.22f, 0.18f, 0.76f)
            runtime.noticeLine() != null -> Color(0.18f, 0.16f, 0.10f, 0.76f)
            else -> Color(0.08f, 0.14f, 0.18f, 0.62f)
        }

    private fun currentCenterStatusTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.60f, 0.70f, 0.76f, 0.92f)
            buildCenterStatusLine() == "Ready" -> Color(0.62f, 0.88f, 0.96f, 0.92f)
            else -> Color(0.90f, 0.94f, 0.98f, 0.94f)
        }

    private fun currentQueueHeaderTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(1.00f, 0.86f, 0.46f, 0.96f)
            "RESEARCH" -> Color(0.74f, 0.86f, 1.00f, 0.96f)
            "CONSTRUCT" -> Color(0.70f, 0.98f, 0.78f, 0.96f)
            else -> Color(0.62f, 0.72f, 0.78f, 0.92f)
        }

    private fun currentQueueStatusTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(1.00f, 0.92f, 0.68f, 0.94f)
            "RESEARCH" -> Color(0.84f, 0.92f, 1.00f, 0.94f)
            "CONSTRUCT" -> Color(0.82f, 1.00f, 0.88f, 0.94f)
            else -> Color(0.66f, 0.74f, 0.80f, 0.88f)
        }

    private fun currentQueueCardTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(0.14f, 0.12f, 0.08f, 0.74f)
            "RESEARCH" -> Color(0.10f, 0.12f, 0.18f, 0.74f)
            "CONSTRUCT" -> Color(0.10f, 0.15f, 0.12f, 0.74f)
            else -> Color(0.08f, 0.12f, 0.16f, 0.62f)
        }

    private fun currentQueueHeaderBackgroundTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(0.36f, 0.28f, 0.10f, 0.84f)
            "RESEARCH" -> Color(0.18f, 0.26f, 0.40f, 0.84f)
            "CONSTRUCT" -> Color(0.18f, 0.30f, 0.20f, 0.84f)
            else -> Color(0.18f, 0.28f, 0.34f, 0.82f)
        }

    private fun currentStatusBadgeTone(): Color =
        when {
            runtime.attackWarningLine() != null -> Color(1.00f, 0.70f, 0.58f, 0.98f)
            runtime.noticeLine() != null -> Color(1.00f, 0.90f, 0.62f, 0.98f)
            runtime.playControlState.paused -> Color(0.82f, 0.88f, 0.96f, 0.96f)
            else -> Color(0.62f, 0.96f, 0.80f, 0.96f)
        }

    private fun commandGroupHeaderTone(group: String): Color =
        when (group) {
            "Orders" -> Color(0.10f, 0.20f, 0.26f, 0.86f)
            "Production" -> Color(0.24f, 0.20f, 0.10f, 0.86f)
            "Utility" -> Color(0.14f, 0.18f, 0.22f, 0.86f)
            else -> Color(0.12f, 0.22f, 0.27f, 0.82f)
        }

    private fun commandGroupAccentTone(group: String): Color =
        when (group) {
            "Orders" -> Color(0.58f, 0.88f, 0.96f, 0.82f)
            "Production" -> Color(0.98f, 0.90f, 0.52f, 0.78f)
            "Utility" -> Color(0.74f, 0.96f, 0.82f, 0.78f)
            else -> Color(0.58f, 0.88f, 0.96f, 0.72f)
        }

    private fun commandButtonStyle(actionId: String): TextButton.TextButtonStyle =
        when {
            actionId == "pause" || actionId == "help" || actionId == "debug" -> assets.secondaryButtonStyle()
            actionId.startsWith("build:") || actionId.startsWith("train:") || actionId.startsWith("research:") -> assets.primaryButtonStyle()
            actionId == "move" || actionId == "attackMove" || actionId == "patrol" || actionId == "hold" -> assets.primaryButtonStyle()
            else -> assets.subtleButtonStyle()
        }

    private fun commandButtonLabel(button: ClientCommandButton): String {
        val baseLabel =
            when (button.actionId) {
                "attackMove" -> "Attack"
                "centerSelection" -> "Ctr"
                "viewF1" -> "F1"
                "viewF2" -> "F2"
                "observer" -> "Obs"
                "pause" -> "Pse"
                "help" -> "Hlp"
                "debug" -> "Dbg"
                "build:Depot" -> "Depot"
                "build:ResourceDepot" -> "Exp"
                "build:GasDepot" -> "Gas"
                "cancelBuild" -> "Stop B"
                "cancelTrain" -> "Stop T"
                "cancelResearch" -> "Stop R"
                "patrol" -> "Pat"
                "move" -> "Move"
                "hold" -> "Hold"
                else -> button.label
            }
        val hotkey =
            when (button.actionId) {
                "move" -> "M"
                "attackMove" -> "A"
                "patrol" -> "P"
                "hold" -> "H"
                "clear" -> "E"
                "centerSelection" -> "Hm"
                "viewF1" -> "1"
                "viewF2" -> "2"
                "observer" -> "3"
                "pause" -> "Sp"
                "help" -> "F1"
                "debug" -> "Tb"
                "selectViewedFaction" -> "F2"
                "selectType" -> "F3"
                "selectRole" -> "F4"
                "selectAll" -> "F11"
                "selectIdleWorkers" -> "F12"
                "build:Depot" -> "B"
                "build:ResourceDepot" -> "R"
                "build:GasDepot" -> "G"
                else -> null
            }
        return if (hotkey == null) baseLabel else "$baseLabel $hotkey"
    }

    private fun commandGroups(buttons: List<ClientCommandButton>): List<Pair<String, List<ClientCommandButton>>> {
        val primary = buttons.filter { it.actionId in setOf("move", "attackMove", "patrol", "hold", "clear", "centerSelection") }
        val allProduction = buttons.filter { it.actionId.startsWith("build:") || it.actionId.startsWith("train:") || it.actionId.startsWith("research:") || it.actionId.startsWith("cancel") }
        val productionPageSize = 6
        val productionPageCount = ((allProduction.size + productionPageSize - 1) / productionPageSize).coerceAtLeast(1)
        productionPage = productionPage.coerceIn(0, productionPageCount - 1)
        val production = allProduction.drop(productionPage * productionPageSize).take(productionPageSize)
        val utility =
            buttons.filter {
                it.actionId in setOf("viewF1", "viewF2", "observer", "pause", "help") ||
                    (runtime.debugVisible && it.actionId == "debug")
        }
        return listOf("Orders" to primary, "Production" to production, "Utility" to utility)
    }

    private fun shiftProductionPage(direction: Int) {
        val productionButtons =
            runtime.buttonModels().count {
                it.actionId.startsWith("build:") ||
                    it.actionId.startsWith("train:") ||
                    it.actionId.startsWith("research:") ||
                    it.actionId.startsWith("cancel")
            }
        val pageCount = ((productionButtons + 5) / 6).coerceAtLeast(1)
        productionPage = (productionPage + direction).coerceIn(0, pageCount - 1)
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
        private var rightClickHandled = false

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
                    rightClickHandled = true
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
            if (button == Input.Buttons.RIGHT) {
                if (!rightClickHandled) {
                    runtime.issueRightClick(screenX.toFloat(), screenY.toFloat(), Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
                }
                rightClickHandled = false
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
                Input.Keys.M -> runtime.executeAction("move", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.A -> runtime.executeAction("attackMove", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.P -> runtime.executeAction("patrol", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.H -> runtime.executeAction("hold", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.B -> runtime.executeAction("build:Depot", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.R -> runtime.executeAction("build:ResourceDepot", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.G -> runtime.executeAction("build:GasDepot", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.U -> runtime.catalog.trainOptions.getOrNull(0)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height)) }
                Input.Keys.I -> runtime.catalog.trainOptions.getOrNull(1)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height)) }
                Input.Keys.O -> runtime.catalog.trainOptions.getOrNull(2)?.let { runtime.executeAction("train:${it.typeId}", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height)) }
                Input.Keys.L -> runtime.catalog.researchOptions.firstOrNull()?.let { runtime.executeAction("research:${it.typeId}", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height)) }
                Input.Keys.X -> runtime.executeAction("cancelBuild", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.T -> runtime.executeAction("cancelTrain", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.Y -> runtime.executeAction("cancelResearch", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F2 -> runtime.executeAction("selectViewedFaction", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F3 -> runtime.executeAction("selectType", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F4 -> runtime.executeAction("selectRole", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F11 -> runtime.executeAction("selectAll", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F12 -> runtime.executeAction("selectIdleWorkers", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.F5 -> runtime.restartMatch()
                Input.Keys.F6 -> runtime.cycleScenarioAndRestart(-1)
                Input.Keys.F7 -> runtime.cycleScenarioAndRestart(1)
                Input.Keys.F -> runtime.executeAction("selectDamaged", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.V -> runtime.executeAction("selectCombat", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.N -> runtime.executeAction("selectProducers", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.Z -> runtime.executeAction("selectTrainers", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.C -> runtime.executeAction("selectResearchers", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.J -> runtime.executeAction("selectConstruction", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.K -> runtime.executeAction("selectHarvesters", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.Q -> runtime.executeAction("selectReturning", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.E -> runtime.executeAction("selectCargo", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.D -> runtime.executeAction("selectDropoffs", Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.HOME -> runtime.centerOnSelection(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
                Input.Keys.END -> runtime.centerOnViewedFaction(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
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
            runtime.handleControlGroup(group, assign = assign, add = add, viewWidth = Gdx.graphics.width, viewHeight = computeWorldViewportHeight(Gdx.graphics.height))
        }
    }
}
