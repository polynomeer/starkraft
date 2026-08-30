package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.abs

internal class GameScreen(
    private val game: StarkraftGdxGame,
    private val assets: GdxUiAssets,
    private val runtime: GdxClientRuntime
) : ScreenAdapter() {
    private companion object {
        const val HUD_SELECTION_PULSE_DURATION_MS = 260L
        const val COMMAND_CLICK_PULSE_DURATION_MS = 180L
        const val SLOT_CLICK_PULSE_DURATION_MS = 180L
        const val SELECTION_DRAG_THRESHOLD_PX = 8f
        const val KEYBOARD_PAN_STEP = 24f
    }

    private val worldRenderer = GdxWorldRenderer(assets)
    private val stage = Stage(ScreenViewport())
    private val edgePanMargin = 20f
    private val edgePanSpeed = 12f
    private val middlePanScale = 0.88f
    private val middlePanDeadzone = 0.75f
    private val topBar = Table()
    private val topSelectionShell = Table()
    private val topSelectionCard = Table()
    private val topModeShell = Table()
    private val topModeCard = Table()
    private val topStatusShell = Table()
    private val topStatusCard = Table()
    private val economyLabel = Label("", assets.bodyLabelStyle)
    private val topSelectionLabel = Label("", assets.mutedLabelStyle)
    private val modeLabel = Label("", assets.accentLabelStyle)
    private val statusBadgeLabel = Label("", assets.bodyLabelStyle)
    private val centerHeaderLabel = Label("Selected", assets.titleLabelStyle)
    private val selectionMetaLabel = Label("", assets.mutedLabelStyle)
    private val selectionLabel = Label("", assets.accentLabelStyle)
    private val centerStatusLabel = Label("", assets.bodyLabelStyle)
    private val centerStatusStrip = Table()
    private val queueStatusLabel = Label("", assets.mutedLabelStyle)
    private val queueStatusStrip = Table()
    private val queueHeaderLabel = Label("QUEUE", assets.mutedLabelStyle)
    private val selectionRosterLabel = Label("", assets.bodyLabelStyle)
    private val centerFooterLabel = Label("home center  esc clear  tab debug", assets.mutedLabelStyle)
    private val portraitFrame = Table()
    private val portraitLabel = Label("NO\nUNIT", assets.titleLabelStyle)
    private val healthLabel = Label("", assets.bodyLabelStyle)
    private val portraitHealthLabel = Label("", assets.bodyLabelStyle)
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
    private val attackWarningCard = Table()
    private val attackWarningAccent = Table()
    private val attackWarningLabel = Label("", assets.alertLabelStyle)
    private val minimapFrame = Table()
    private val minimapHeaderCard = Table()
    private val minimapHeaderAccent = Table()
    private val minimapFooterCard = Table()
    private val minimapFooterAccent = Table()
    private val minimapTitle = Label("Tac Map", assets.titleLabelStyle)
    private val minimapHint = Label("map drag", assets.mutedLabelStyle)
    private val bottomHud = Table()
    private val leftHudColumn = Table()
    private val centerCard = Table()
    private val commandCard = Table()
    private val selectionHeadlineCard = Table()
    private val commandHintCard = Table()
    private val pauseOverlay = Table()
    private val pauseHeaderCard = Table()
    private val helpOverlay = Table()
    private val helpHeaderCard = Table()
    private val screenFade = Table()
    private val helpLabel = Label("", assets.mutedLabelStyle)
    private val footerLabel = Label("", assets.mutedLabelStyle)
    private var dragSelection: DragSelectionBox? = null
    private var selectionPage = 0
    private var productionPage = 0
    private var lastSelectionSignature = ""
    private var focusedSelectionId: Int? = null
    private var selectionHudPulseUntilMillis = 0L
    private var topModePulseUntilMillis = 0L
    private var lastTopModeSignature = ""
    private var topStatusPulseUntilMillis = 0L
    private var lastTopStatusSignature = ""
    private var overlayHeaderPulseUntilMillis = 0L
    private var lastOverlaySignature = ""
    private var bannerPulseUntilMillis = 0L
    private var lastBannerSignature = ""
    private var lastCommandPanelSignature = ""
    private val commandPulseUntilMillis = HashMap<String, Long>()
    private val slotPulseUntilMillis = HashMap<Int, Long>()
    private var screenFadeAlpha = 1f
    private var soundVariantTick = 0
    private val soundCooldownUntilMillis = HashMap<String, Long>()
    private var middlePanActive = false
    private var minimapPanActive = false
    private var selectionDragActive = false
    private lateinit var topSelectionCell: Cell<*>
    private lateinit var topModeCell: Cell<*>
    private lateinit var topStatusCell: Cell<*>
    private lateinit var leftHudSpacerCell: Cell<*>
    private lateinit var centerHudCell: Cell<*>
    private lateinit var commandHudCell: Cell<*>
    private lateinit var commandHeaderCell: Cell<*>
    private lateinit var commandHintCell: Cell<*>
    private lateinit var commandHintInnerCell: Cell<*>
    private lateinit var commandScrollCell: Cell<*>

    init {
        centerHeaderLabel.setFontScale(0.94f)
        commandHeaderLabel.setFontScale(0.94f)
        queueHeaderLabel.setFontScale(0.86f)
        economyLabel.setFontScale(0.92f)
        topSelectionLabel.setFontScale(0.88f)
        modeLabel.setFontScale(0.88f)
        statusBadgeLabel.setFontScale(0.88f)
        commandHintLabel.setFontScale(0.80f)
        healthLabel.setFontScale(0.82f)
        portraitHealthLabel.setFontScale(0.78f)
        commandHintLabel.setWrap(false)
        commandHintLabel.setEllipsis(true)
        commandHintLabel.setAlignment(Align.left)
        selectionMetaLabel.setFontScale(0.80f)
        centerStatusLabel.setFontScale(0.72f)
        queueStatusLabel.setFontScale(0.72f)
        selectionRosterLabel.setFontScale(0.78f)
        footerLabel.setFontScale(0.84f)
        centerFooterLabel.setFontScale(0.78f)
        selectionPageLabel.setFontScale(0.78f)
        controlGroupsLabel.setFontScale(0.78f)
        minimapHint.setFontScale(0.82f)
        buildHud()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, GameInputController())
    }

    override fun render(delta: Float) {
        val worldViewportHeight = computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height)
        runtime.tick()
        runtime.ensurePlayableView(Gdx.graphics.width, worldViewportHeight)
        runtime.ensureInitialCamera(Gdx.graphics.width, worldViewportHeight)
        applyEdgePan(worldViewportHeight.toFloat())
        runtime.constrainCamera(Gdx.graphics.width, worldViewportHeight)
        refreshHud()
        if (runtime.consumeAttackAlertSound()) {
            playSoundVariant(assets.alertSound, 0.7f, 0.98f, 1.04f)
        }
        when (runtime.consumeCommandSoundKind()) {
            CommandSoundKind.MOVE -> playSoundVariant("cmd-move", assets.moveSound, 0.48f, 0.98f, 1.05f, 30L)
            CommandSoundKind.ATTACK -> playSoundVariant("cmd-attack", assets.attackSound, 0.55f, 0.97f, 1.05f, 30L)
            CommandSoundKind.BUILD -> playSoundVariant("cmd-build", assets.buildSound, 0.52f, 0.98f, 1.04f, 45L)
            CommandSoundKind.INVALID -> playSoundVariant("cmd-invalid", assets.invalidSound, 0.48f, 0.94f, 1.00f, 45L)
            null -> Unit
        }
        when (runtime.consumeCombatSoundKind()) {
            CombatSoundKind.MARINE_RANGED -> playSoundVariant(assets.marineCombatSound, 0.46f, 0.96f, 1.06f)
            CombatSoundKind.ZERGLING_MELEE -> playSoundVariant(assets.zerglingCombatSound, 0.46f, 0.93f, 1.03f)
            CombatSoundKind.MELEE -> playSoundVariant(assets.meleeCombatSound, 0.44f, 0.95f, 1.04f)
            CombatSoundKind.RANGED -> playSoundVariant(assets.rangedCombatSound, 0.42f, 0.97f, 1.05f)
            null -> Unit
        }
        when (runtime.consumeDeathSoundKind()) {
            DeathSoundKind.UNIT -> playSoundVariant("death-unit", assets.deathSound, 0.56f, 0.94f, 1.03f, 55L)
            DeathSoundKind.MARINE -> playSoundVariant("death-marine", assets.marineDeathSound, 0.58f, 0.95f, 1.04f, 55L)
            DeathSoundKind.ZERGLING -> playSoundVariant("death-zergling", assets.zerglingDeathSound, 0.56f, 0.92f, 1.01f, 45L)
            DeathSoundKind.STRUCTURE -> {
                playSoundVariant("death-structure-main", assets.structureDeathSound, 0.60f, 0.92f, 1.00f, 80L)
                playSoundVariant("death-structure-tail", assets.structureDeathTailSound, 0.38f, 0.88f, 0.96f, 120L)
            }
            null -> Unit
        }
        if (runtime.consumeCompletionAlertSound()) {
            playSoundVariant(assets.completeSound, 0.55f, 0.98f, 1.04f)
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

    private fun playSoundVariant(sound: com.badlogic.gdx.audio.Sound, baseVolume: Float, minPitch: Float, maxPitch: Float) {
        soundVariantTick += 1
        val phase = ((soundVariantTick * 37) % 100) / 100f
        val pitch = minPitch + ((maxPitch - minPitch) * phase)
        val volume = (baseVolume * (0.94f + ((1f - phase) * 0.10f))).coerceIn(0f, 1f)
        sound.play(volume, pitch, 0f)
    }

    private fun playSoundVariant(key: String, sound: com.badlogic.gdx.audio.Sound, baseVolume: Float, minPitch: Float, maxPitch: Float, cooldownMillis: Long) {
        val now = System.currentTimeMillis()
        if ((soundCooldownUntilMillis[key] ?: 0L) > now) return
        soundCooldownUntilMillis[key] = now + cooldownMillis
        playSoundVariant(sound, baseVolume, minPitch, maxPitch)
    }

    private fun buildHud() {
        val colors = assets.theme.colors
        val initialTopBarLayout = computeTopBarLayout(1280)
        val root =
            Table().apply {
                setFillParent(true)
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
            }

        topBar.apply {
            background = null
            pad(0f, 2f, 0f, 2f)
            commandHeaderCell = add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.60f })
                    pad(1f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.74f })
                            pad(1f, 2f, 1f, 2f)
                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.78f }) }).width(2f).expandY().fillY().padRight(3f)
                            add(economyLabel).left()
                        }
                    ).left().expandX().fillX()
                }
            ).left().expandX().fillX()
            topSelectionCell = add(
                topSelectionShell.apply {
                    background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.58f })
                    pad(1f)
                    add(
                        topSelectionCard.apply {
                            background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.72f })
                            pad(1f, 2f, 1f, 2f)
                            add(topSelectionLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(initialTopBarLayout.selectionWidth.toFloat()).center().padLeft(2f).padRight(2f)
            topModeCell = add(
                topModeShell.apply {
                    background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.58f })
                    pad(1f)
                    add(
                        topModeCard.apply {
                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.selection, 0.14f).also { it.a = 0.72f })
                            pad(1f, 2f, 1f, 2f)
                            add(modeLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(initialTopBarLayout.modeWidth.toFloat()).center().padRight(2f)
            topStatusCell = add(
                topStatusShell.apply {
                    background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.60f })
                    pad(1f)
                    add(
                        topStatusCard.apply {
                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.18f).also { it.a = 0.74f })
                            pad(1f, 2f, 1f, 2f)
                            add(statusBadgeLabel).right()
                        }
                    ).expandX().fillX()
                }
            ).width(initialTopBarLayout.statusWidth.toFloat()).right()
        }

        minimapFrame.apply {
            background = null
            pad(0f)
            top()
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                minimapHeaderCard.apply {
                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.selection, 0.12f).also { it.a = 0.62f })
                    pad(1f, 4f, 1f, 4f)
                    add(minimapHeaderAccent.apply { background = assets.panelDrawable(colors.selectionSoft.cpy().also { it.a = 0.74f }) }).width(2f).expandY().fillY().padRight(4f)
                    add(minimapTitle).left()
                    add().expandX().fillX()
                    add(minimapHint).right()
                }
            ).expandX().fillX().pad(3f, 3f, 0f, 3f).row()
            add().expand().fill().row()
            add(
                minimapFooterCard.apply {
                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.48f })
                    pad(1f, 4f, 1f, 4f)
                    add(minimapFooterAccent.apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.66f }) }).width(1f).expandY().fillY().padRight(4f)
                    add(footerLabel).left().expandX().fillX()
                }
            ).expandX().fillX().pad(0f, 3f, 3f, 3f)
        }

        commandCard.apply {
            background = null
            pad(3f)
            top()
            val initialCommandLayout = computeCommandDeckLayout(1280, 720)
            commandHeaderCell =
                add(
                    Table().apply {
                        add(
                            Table().apply {
                                background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.76f })
                                pad(1f)
                                add(
                                    Table().apply {
                                        background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.18f).also { it.a = 0.78f })
                                        pad(initialCommandLayout.headerPadY.toFloat(), 6f, initialCommandLayout.headerPadY.toFloat(), 6f)
                                        add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.82f }) }).width(2f).expandY().fillY().padRight(4f)
                                        add(commandHeaderLabel).left()
                                    }
                                )
                            }
                        ).left().padRight(initialCommandLayout.sectionGap.toFloat())
                        add().expandX().fillX()
                        add(makeButton("<", style = assets.subtleButtonStyle()) { shiftProductionPage(-1) }).width(initialCommandLayout.pagerButtonWidth.toFloat()).height(initialCommandLayout.pagerButtonHeight.toFloat()).padRight(initialCommandLayout.buttonGap.toFloat())
                        add(makeButton(">", style = assets.subtleButtonStyle()) { shiftProductionPage(1) }).width(initialCommandLayout.pagerButtonWidth.toFloat()).height(initialCommandLayout.pagerButtonHeight.toFloat())
                    }
                ).expandX().fillX()
            row()
            add(Table().apply { background = assets.panelDrawable(colors.borderDefault.cpy().also { it.a = 0.62f }) }).height(1f).expandX().fillX().padTop(5f).row()
            commandHintCell =
                add(
                    commandHintCard.apply {
                        pad(2f, 2f, 0f, 2f)
                        commandHintInnerCell =
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.68f })
                                    pad(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.12f).also { it.a = 0.72f })
                                            pad(initialCommandLayout.headerPadY.toFloat(), 4f, initialCommandLayout.headerPadY.toFloat(), 4f)
                                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.82f }) }).width(2f).expandY().fillY().padRight(4f)
                                            add(commandHintLabel).width(140f).left().fillX()
                                        }
                                    ).width((initialCommandLayout.hintWidth - 4).toFloat()).height(computeCommandHintRowHeight(1280).toFloat()).left()
                                }
                            ).width(initialCommandLayout.hintWidth.toFloat()).left()
                    }
                ).expandX().fillX().padTop(initialCommandLayout.sectionGap.toFloat())
            row()
            add(actionBanner).left().expandX().fillX().padTop(initialCommandLayout.sectionGap.toFloat()).row()
        }
        buttonTable.top().left()
        buttonTable.defaults().left()
        commandScroll.setFadeScrollBars(false)
        commandScroll.setScrollingDisabled(true, false)
        commandScrollCell = commandCard.add(commandScroll).top().left().padTop(6f)

        actionBanner.apply {
            background = null
            pad(0f)
            add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.48f })
                    pad(1f, 5f, 1f, 5f)
                    add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.78f }) }).width(2f).expandY().fillY().padRight(4f)
                    add(actionBannerLabel).center()
                }
            ).center()
        }

        centerCard.apply {
            background = null
            pad(0f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            val initialCenterLayout = computeCenterPanelLayout(1280)
            add(
                Table().apply {
                    add(centerHeaderLabel).left()
                    add().expandX().fillX()
                    add(healthLabel).right()
                }
            ).expandX().fillX().padBottom(0f).row()
            add(Table().apply { background = assets.panelDrawable(colors.borderDefault.cpy().also { it.a = 0.62f }) }).height(1f).expandX().fillX().padTop(1f).row()
            add(
                selectionHeadlineCard.apply {
                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.selection, 0.10f).also { it.a = 0.70f })
                    pad(1f, initialCenterLayout.headerPadX.toFloat(), 1f, initialCenterLayout.headerPadX.toFloat())
                    add(selectionLabel).left().expandX().fillX()
                }
            ).left().expandX().fillX().padTop(1f).row()
            add(Table().apply { background = assets.panelDrawable(colors.borderSubtle.cpy().also { it.a = 0.60f }) }).height(1f).expandX().fillX().padTop(1f).row()
            add(
                Table().apply {
                    add(
                        portraitFrame.apply {
                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.build, 0.08f).also { it.a = 0.80f })
                            pad(1f)
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.production.cpy().lerp(colors.textPrimary, 0.20f).also { it.a = 0.76f })
                                }
                            ).height(1f).expandX().fillX().row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.76f })
                                    pad(3f, initialCenterLayout.bodyPadX.toFloat(), 1f, initialCenterLayout.bodyPadX.toFloat())
                                    add(portraitLabel).center().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.14f).also { it.a = 0.58f })
                                            pad(1f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                            add(portraitHealthLabel).center()
                                        }
                                    ).padTop(initialCenterLayout.sectionGap.toFloat())
                                }
                            ).expand().fill().padTop(initialCenterLayout.sectionGap.toFloat())
                        }
                    ).size(initialCenterLayout.portraitSize.toFloat(), initialCenterLayout.portraitSize.toFloat()).top().left().padRight(initialCenterLayout.portraitGap.toFloat())
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.12f).also { it.a = 0.62f })
                                    pad(1f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                    add(selectionMetaLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().row()
                            add(Table().apply { background = assets.panelDrawable(colors.borderSubtle.cpy().also { it.a = 0.58f }) }).height(1f).expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.command.cpy().lerp(colors.panelStrong, 0.44f).also { it.a = 0.30f })
                                }
                            ).height(2f).expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                healthBarBack.apply {
                                    background = assets.panelDrawable(colors.panelAlt.cpy())
                                    clearChildren()
                                    add(healthBarFill.apply {
                                        background = assets.panelDrawable(colors.selection.cpy())
                                    }).expandY().fillY().left()
                                    add().expandX().fillX()
                                }
                            ).width(initialCenterLayout.healthBarWidth.toFloat()).height(initialCenterLayout.healthBarHeight.toFloat()).left().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.12f).also { it.a = 0.62f })
                                    pad(1f, initialCenterLayout.headerPadX.toFloat(), 1f, initialCenterLayout.headerPadX.toFloat())
                                    add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.68f }) }).width(1f).expandY().fillY().padRight(initialCenterLayout.sectionGap.toFloat() + 1f)
                                    add(Label("STATUS", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.38f })
                                    pad(initialCenterLayout.bodyPadY.toFloat(), initialCenterLayout.bodyPadX.toFloat(), initialCenterLayout.bodyPadY.toFloat(), initialCenterLayout.bodyPadX.toFloat())
                                    add(centerStatusStrip).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.34f })
                                    pad(0.5f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                    add(centerStatusLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(currentQueueCardTone())
                                    pad(initialCenterLayout.bodyPadY.toFloat(), initialCenterLayout.bodyPadX.toFloat(), initialCenterLayout.bodyPadY.toFloat(), initialCenterLayout.bodyPadX.toFloat())
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(currentQueueHeaderBackgroundTone())
                                            pad(0.5f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                            add(queueHeaderLabel).left()
                                        }
                                    ).left().padRight(initialCenterLayout.sectionGap.toFloat() + 1f)
                                    add(queueStatusStrip).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.34f })
                                    pad(0.5f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                    add(queueStatusLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.production, 0.10f).also { it.a = 0.62f })
                                    pad(0.5f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                    add(Table().apply { background = assets.panelDrawable(colors.production.cpy().also { it.a = 0.64f }) }).width(1f).expandY().fillY().padRight(initialCenterLayout.sectionGap.toFloat() + 1f)
                                    add(Label("ROSTER", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.30f })
                                    pad(0.5f, initialCenterLayout.headerPadX.toFloat(), 0.5f, initialCenterLayout.headerPadX.toFloat())
                                    add(selectionRosterLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(1f).row()
                            add(selectionGrid).left().expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
                            add(
                                selectionPager.apply {
                                    clearChildren()
                                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftSelectionPage(-1) }).width(initialCenterLayout.pagerButtonSize.toFloat()).height(initialCenterLayout.pagerButtonSize.toFloat()).padRight(initialCenterLayout.sectionGap.toFloat())
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.68f })
                                            pad(1f, initialCenterLayout.headerPadX.toFloat(), 1f, initialCenterLayout.headerPadX.toFloat())
                                            add(selectionPageLabel).left()
                                        }
                                    ).width(initialCenterLayout.pagerLabelWidth.toFloat()).left().padRight(initialCenterLayout.sectionGap.toFloat())
                                    add(controlGroupButtons).minWidth(56f).right().padRight(initialCenterLayout.sectionGap.toFloat())
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.68f })
                                            pad(1f, initialCenterLayout.headerPadX.toFloat(), 1f, initialCenterLayout.headerPadX.toFloat())
                                            add(controlGroupsLabel).right()
                                        }
                                    ).width(initialCenterLayout.groupSummaryWidth.toFloat()).right().padRight(initialCenterLayout.sectionGap.toFloat())
                                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftSelectionPage(1) }).width(initialCenterLayout.pagerButtonSize.toFloat()).height(initialCenterLayout.pagerButtonSize.toFloat())
                                }
                            ).expandX().fillX()
                        }
                    ).expandX().fillX().top()
                }
            ).expandX().fillX().padTop(initialCenterLayout.sectionGap.toFloat()).row()
        }

        bottomHud.apply {
            background = null
            pad(0f, 18f, 8f, 18f)
            leftHudSpacerCell = add().width(208f).bottom()
            centerHudCell = add(wrapHudPanel(centerCard, colors.selection.cpy().lerp(colors.borderDefault, 0.60f).also { it.a = 0.92f })).width(266f).bottom().padRight(10f)
            add().expandX().fillX()
            commandHudCell = add(wrapHudPanel(commandCard, colors.command.cpy().lerp(colors.borderDefault, 0.44f).also { it.a = 0.92f })).width(278f).right().bottom()
        }

        root.top()
        root.add(wrapTopStrip(topBar)).expandX().fillX().pad(6f, 18f, 0f, 18f).row()
        root.add().expand().fill().row()
        root.add(bottomHud).expandX().fillX().bottom()
        stage.addActor(root)

        leftHudColumn.apply {
            setFillParent(true)
            bottom().left()
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    add(wrapMinimapPanel(minimapFrame)).left()
                }
            ).padLeft(16f).padBottom(14f)
        }
        stage.addActor(leftHudColumn)

        attackWarningTable.apply {
            setFillParent(true)
            top()
            isVisible = false
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelStrong.cpy().lerp(colors.danger, 0.22f).also { it.a = 0.56f })
                    pad(1f)
                    add(
                        attackWarningCard.apply {
                            background = assets.panelDrawable(colors.panelStrong.cpy().lerp(colors.danger, 0.50f).also { it.a = 0.76f })
                            pad(6f, 14f, 6f, 14f)
                            add(attackWarningAccent.apply { background = assets.panelDrawable(colors.warningSoft.cpy().also { it.a = 0.72f }) }).width(2f).expandY().fillY().padRight(6f)
                            add(attackWarningLabel).center()
                        }
                    )
                }
            ).padTop(14f)
        }
        stage.addActor(attackWarningTable)

        pauseOverlay.apply {
            setFillParent(true)
            isVisible = false
            background = assets.panelDrawable(colors.scrim.cpy().also { it.a = 0.62f })
            add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.76f })
                    pad(10f)
                    defaults().pad(4f)
                    add(
                        pauseHeaderCard.apply {
                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.20f).also { it.a = 0.72f })
                            pad(3f, 8f, 3f, 8f)
                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.72f }) }).width(2f).expandY().fillY().padRight(5f)
                            add(Label("PAUSED", assets.titleLabelStyle)).left()
                        }
                    ).width(248f).left().row()
                    add(Table().apply { background = assets.panelDrawable(colors.borderDefault.cpy().also { it.a = 0.58f }) }).height(1f).width(248f).padBottom(6f).row()
                    add(makeButton("Resume", style = assets.primaryButtonStyle()) { runtime.togglePauseOverlay() }).width(248f).row()
                    add(makeButton("Sim Pause", style = assets.secondaryButtonStyle()) { runtime.togglePlayPause() }).width(248f).row()
                    add(makeButton("Restart", style = assets.secondaryButtonStyle()) { runtime.restartMatch() }).width(248f).row()
                    add(makeButton("Save Q", style = assets.subtleButtonStyle()) { runtime.savePreset("quick") }).width(248f).row()
                    add(makeButton("Load Q", style = assets.subtleButtonStyle()) { runtime.loadPreset("quick") }).width(248f).row()
                    add(makeButton("Save A", style = assets.subtleButtonStyle()) { runtime.savePreset("alt") }).width(248f).row()
                    add(makeButton("Load A", style = assets.subtleButtonStyle()) { runtime.loadPreset("alt") }).width(248f).row()
                    add(makeButton("Menu", style = assets.subtleButtonStyle()) {
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
            pad(14f)
            background = assets.panelDrawable(colors.scrim.cpy().also { it.a = 0.56f })
            add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.74f })
                    pad(8f, 10f, 8f, 10f)
                    add(
                        helpHeaderCard.apply {
                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.20f).also { it.a = 0.72f })
                            pad(3f, 8f, 3f, 8f)
                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.72f }) }).width(2f).expandY().fillY().padRight(5f)
                            add(Label("HELP", assets.titleLabelStyle)).left()
                        }
                    ).left().row()
                    add(Table().apply { background = assets.panelDrawable(colors.borderDefault.cpy().also { it.a = 0.58f }) }).height(1f).expandX().fillX().padTop(5f).padBottom(6f).row()
                    add(helpLabel).left().top()
                }
            ).left().top()
        }
        stage.addActor(helpOverlay)

        screenFade.apply {
            setFillParent(true)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            background = assets.panelDrawable(colors.scrim.cpy().also { it.a = 1f })
            color.a = screenFadeAlpha
        }
        stage.addActor(screenFade)
    }

    private fun refreshHud() {
        val snapshot = runtime.snapshot
        val width = Gdx.graphics.width
        val height = Gdx.graphics.height
        val minimapBounds = gdxMiniMapBounds(width, height)
        val bottomHudLayout = computeBottomHudLayout(width, height)
        val commandDeckLayout = computeCommandDeckLayout(width, height)
        val selectedEntities = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        val structureDeck = usesStructureCommandDeck(selectedEntities)
        val commandDeckPresentation = computeCommandDeckPresentation(commandDeckLayout, structureDeck)
        val centerPanelLayout = computeCenterPanelLayout(width)
        val topBarLayout = computeTopBarLayout(width)
        val minimapWidth = minimapBounds.width
        val minimapHeight = minimapBounds.height
        val centerWidth = bottomHudLayout.centerWidth.toFloat()
        val commandWidth = bottomHudLayout.commandWidth.toFloat()
        val commandHeight = commandDeckPresentation.scrollHeight.toFloat()
        val commandButtonHeight = commandDeckPresentation.buttonHeight.toFloat()
        val commandColumns = commandDeckPresentation.columns
        val commandCellWidth = (commandWidth / commandColumns) - 2f
        val commandActorWidth = commandCellWidth - commandDeckPresentation.actorInset.toFloat()
        val commandHintWidth = commandDeckLayout.hintWidth.toFloat()
        val commandHintInnerWidth = (commandDeckLayout.hintWidth - 4).toFloat().coerceAtLeast(96f)
        val commandHintHeight = computeCommandHintRowHeight(width).toFloat()
        val centerHeight = (height * 0.146f).coerceIn(120f, 152f)
        val hudShellHeight = computeBottomHudHeight(width, height).toFloat()
        val unifiedPanelHeight = hudShellHeight
        topSelectionCell.width(topBarLayout.selectionWidth.toFloat())
        topModeCell.width(topBarLayout.modeWidth.toFloat())
        topStatusCell.width(topBarLayout.statusWidth.toFloat())
        leftHudSpacerCell.width(bottomHudLayout.leftSlotWidth.toFloat())
        centerHudCell.width(centerWidth)
        commandHudCell.width(commandWidth)
        commandHeaderCell.width(commandWidth)
        commandHintCell.width(commandWidth)
        commandHintInnerCell.width(commandHintWidth).height(commandHintHeight)
        commandScrollCell.width(commandWidth - 8f).height(commandHeight)
        selectionLabel.setWrap(true)
        selectionMetaLabel.setWrap(true)
        queueStatusLabel.setWrap(true)
        selectionRosterLabel.setWrap(true)
        footerLabel.setWrap(true)
        centerFooterLabel.setWrap(true)
        minimapTitle.setText("Tac Map  ${runtime.session.state.viewedFaction?.let { "F$it" } ?: "Obs"}")
        minimapTitle.color = currentMinimapTitleTone()
        minimapHint.color = currentMinimapHintTone()
        selectionLabel.setWidth(centerWidth)
        selectionMetaLabel.setWidth(centerWidth)
        queueStatusLabel.setWidth(centerWidth)
        selectionRosterLabel.setWidth(centerWidth)
        footerLabel.setWidth(minimapWidth - 12f)
        centerFooterLabel.setWidth(centerWidth)
        minimapHint.setWidth(minimapWidth - 20f)
        minimapFrame.setSize(minimapWidth, minimapHeight)
        centerCard.setSize(centerWidth, unifiedPanelHeight - 6f)
        commandCard.setSize(commandWidth, unifiedPanelHeight - 6f)
        commandScroll.setSize(commandWidth - 8f, commandHeight)
        portraitFrame.setSize(centerPanelLayout.portraitSize.toFloat(), centerPanelLayout.portraitSize.toFloat())
        bottomHud.setHeight(hudShellHeight)
        buttonTable.defaults().pad(0f, 0f, commandDeckLayout.buttonGap.toFloat(), commandDeckLayout.buttonGap.toFloat())
        selectionLabel.setText(buildSelectionHeadline())
        selectionMetaLabel.setText(buildSelectionMetaLine())
        centerStatusLabel.setText(buildCenterStatusLine())
        rebuildCenterStatusStrip()
        queueStatusLabel.setText(buildQueueStatusLine())
        rebuildQueueStatusStrip()
        queueHeaderLabel.setText(buildQueueHeaderLine())
        selectionLabel.color = currentSelectionHeadlineTone()
        selectionMetaLabel.color = currentSelectionMetaTone()
        centerStatusLabel.color = currentCenterStatusTone()
        queueHeaderLabel.color = currentQueueHeaderTone()
        queueStatusLabel.color = currentQueueStatusTone()
        selectionRosterLabel.setText(buildSelectionRosterLine())
        selectionRosterLabel.color = currentRosterTone()
        portraitLabel.setText(buildPortraitText())
        val healthLine = buildHealthLine()
        healthLabel.setText(healthLine)
        portraitHealthLabel.setText(healthLine)
        updateHealthBar()
        rebuildSelectionGrid()
        footerLabel.setText(buildMinimapHintLine(runtime.isGameplayCommandArmed()))
        centerHeaderLabel.setText(if (runtime.session.state.selectedIds.isEmpty()) "Selected" else "Selection")
        val groupedButtons = commandGroups(runtime.buttonModels())
        commandHeaderLabel.setText(buildCommandHeader(groupedButtons))
        commandHeaderLabel.color = currentCommandHeaderTone(groupedButtons)
        economyLabel.setText(buildTopEconomyLine())
        economyLabel.color = currentTopEconomyTone()
        topSelectionLabel.setText(buildTopSelectionLine())
        topSelectionLabel.color = currentTopSelectionTone()
        modeLabel.setText(buildTopModeLine())
        modeLabel.color = currentTopModeTone()
        statusBadgeLabel.setText(buildStatusBadgeLine())
        statusBadgeLabel.color = currentStatusBadgeTone()
        val actionBannerText = buildActionBannerLine()
        actionBannerLabel.setText(actionBannerText)
        actionBannerLabel.color = currentActionBannerTextTone()
        val commandHintText = formatFixedHudHintSlot(buildCommandHintLine())
        commandHintLabel.setText(commandHintText)
        commandHintLabel.color = currentCommandHintTextTone()
        commandHintLabel.setWidth((commandHintInnerWidth - 12f).coerceAtLeast(64f))
        val bannerPulse = bannerPulse()
        commandHintCard.background = assets.panelDrawable(currentCommandHintPulseTone(bannerPulse))
        minimapHeaderCard.background = assets.panelDrawable(currentMinimapHeaderCardTone())
        minimapHeaderAccent.background = assets.panelDrawable(currentMinimapHeaderAccentTone())
        minimapFooterCard.background = assets.panelDrawable(currentMinimapFooterCardTone())
        minimapFooterAccent.background = assets.panelDrawable(currentMinimapFooterAccentTone())
        val selectionPulse = selectionHudPulse()
        topSelectionShell.background = assets.panelDrawable(currentTopSelectionShellTone(selectionPulse))
        topSelectionCard.background = assets.panelDrawable(currentTopSelectionCardTone(selectionPulse))
        val topModePulse = topModePulse()
        topModeShell.background = assets.panelDrawable(currentTopModeShellTone(topModePulse))
        topModeCard.background = assets.panelDrawable(currentTopModeCardTone(topModePulse))
        val topStatusPulse = topStatusPulse()
        topStatusShell.background = assets.panelDrawable(currentTopStatusShellTone(topStatusPulse))
        topStatusCard.background = assets.panelDrawable(currentTopStatusCardTone(topStatusPulse))
        selectionHeadlineCard.background = assets.panelDrawable(currentSelectionHeadlinePulseTone(selectionPulse))
        portraitFrame.background = assets.panelDrawable(currentPortraitFrameTone(selectionPulse))
        attackWarningLabel.setText(buildAttackWarningText())
        attackWarningCard.background = assets.panelDrawable(currentAttackWarningCardTone())
        attackWarningAccent.background = assets.panelDrawable(currentAttackWarningAccentTone())
        attackWarningTable.isVisible = runtime.attackWarningLine() != null
        centerFooterLabel.setText(buildCenterFooterLine())
        syncSelectionPage(snapshot)
        updateSelectionPager(snapshot)
        pauseOverlay.isVisible = runtime.pauseOverlayVisible
        helpOverlay.isVisible = runtime.helpOverlayVisible
        val overlayPulse = overlayHeaderPulse()
        pauseHeaderCard.background = assets.panelDrawable(currentPauseHeaderPulseTone(overlayPulse))
        helpHeaderCard.background = assets.panelDrawable(currentHelpHeaderPulseTone(overlayPulse))
        helpLabel.setText(buildHelpOverlayLines(runtime.helpOverlayVisible).joinToString("\n"))
        val showActionBanner = actionBannerText.isNotBlank()
        actionBanner.isVisible = showActionBanner
        actionBanner.background = if (showActionBanner) assets.panelDrawable(currentActionBannerPulseTone(bannerPulse)) else null
        actionBanner.pad(if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f, if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f)
        val commandPanelSignature =
            buildCommandPanelSignature(
                groupedButtons,
                snapshot,
                commandDeckLayout,
                commandDeckPresentation,
                commandWidth,
                commandButtonHeight
            )
        if (commandPanelSignature != lastCommandPanelSignature) {
            lastCommandPanelSignature = commandPanelSignature
            rebuildCommandPanel(groupedButtons, snapshot, commandDeckLayout, commandDeckPresentation, commandColumns, commandCellWidth, commandActorWidth, commandButtonHeight)
            bottomHud.invalidateHierarchy()
        }
    }

    private fun rebuildCommandPanel(
        groupedButtons: List<Pair<String, List<ClientCommandButton>>>,
        snapshot: ClientSnapshot?,
        commandDeckLayout: CommandDeckLayout,
        commandDeckPresentation: CommandDeckPresentation,
        commandColumns: Int,
        commandCellWidth: Float,
        commandActorWidth: Float,
        commandButtonHeight: Float
    ) {
        buttonTable.clearChildren()
        groupedButtons.forEachIndexed { groupIndex, group ->
            if (group.second.isEmpty()) return@forEachIndexed
            buttonTable.add(
                Table().apply {
                    background = assets.panelDrawable(commandGroupShellTone(group.first))
                    pad(commandDeckLayout.groupPad.toFloat())
                    add(
                        Table().apply {
                            background = assets.panelDrawable(commandGroupHeaderTone(group.first))
                            pad(commandDeckLayout.headerPadY.toFloat(), commandDeckLayout.headerPadX.toFloat(), commandDeckLayout.headerPadY.toFloat(), commandDeckLayout.headerPadX.toFloat())
                            add(Table().apply { background = assets.panelDrawable(commandGroupAccentTone(group.first)) }).width(2f).expandY().fillY().padRight(4f)
                            add(Label(group.first.uppercase(), assets.accentLabelStyle)).left()
                        }
                    ).colspan(commandColumns).left().padBottom(commandDeckLayout.sectionGap.toFloat()).row()
                    group.second.forEachIndexed { index, button ->
                        val activePulse = uiPulse()
                        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
                        val queued = isCommandButtonQueued(button.actionId, selected)
                        val actor = makeButton(
                            commandButtonLabel(button, snapshot),
                            runtime.actionHint(button.actionId),
                            commandButtonStyle(button.actionId),
                            runtime.disabledActionHint(button.actionId)
                        ) {
                            runActionWithPulse(button.actionId)
                        }
                        actor.isDisabled = !runtime.isActionEnabled(button.actionId)
                        actor.isChecked = runtime.isActionActive(button.actionId)
                        actor.color =
                            when {
                                actor.isDisabled -> assets.theme.colors.textDisabled.cpy().also { it.a = 0.55f }
                                actor.isChecked -> assets.theme.colors.textPrimary.cpy().lerp(assets.theme.colors.warningSoft, 0.32f).also { it.a = 0.94f }
                                queued -> assets.theme.colors.textPrimary.cpy().lerp(assets.theme.colors.selectionSoft, 0.16f).also { it.a = 0.94f }
                                else -> Color.WHITE
                            }
                        val cardTone = commandButtonCardTone(button.actionId)
                        val clickPulse = commandClickPulse(button.actionId)
                        val frameTone = commandButtonFrameTone(button.actionId, actor.isDisabled, actor.isChecked, queued, activePulse, clickPulse, cardTone)
                        val shellTone = commandButtonShellTone(button.actionId, actor.isDisabled, actor.isChecked, queued, activePulse, clickPulse)
                        add(
                            Table().apply {
                                background = assets.panelDrawable(shellTone)
                                pad(0.5f)
                                add(
                                    Table().apply {
                                        background = assets.panelDrawable(frameTone)
                                        pad(0.5f)
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                        commandButtonEdgeTone(button.actionId, actor.isDisabled, actor.isChecked, queued, activePulse, clickPulse)
                                                    )
                                            }
                                        ).height(1f).expandX().fillX().colspan(4).padBottom(0.5f).row()
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                        commandButtonRailTone(button.actionId, actor.isDisabled, actor.isChecked, queued, activePulse)
                                                    )
                                            }
                                        ).width(1.5f).expandY().fillY().padRight(1.5f)
                                        add(
                                            buildCommandGlyph(
                                                actionId = button.actionId,
                                                disabled = actor.isDisabled,
                                                checked = actor.isChecked
                                            )
                                        ).size(
                                            if (commandDeckPresentation.buttonHeight <= 17) 8f else 9f,
                                            if (commandDeckPresentation.buttonHeight <= 17) 8f else 9f
                                        ).left().padRight(commandDeckLayout.buttonGap.toFloat())
                                        add(actor).width(commandActorWidth).height(commandButtonHeight).left().expandX().fillX()
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                            commandButtonCornerTone(actor.isDisabled, actor.isChecked)
                                                        )
                                                    }
                                        ).size(3f, 3f).padLeft(commandDeckLayout.buttonGap.toFloat())
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
            buttonTable.add(Label("dbg: e=${snapshot.entities.size} r=${snapshot.resourceNodes.size}", assets.mutedLabelStyle)).colspan(commandColumns).left().padTop(6f).row()
        }
    }

    private fun buildCommandPanelSignature(
        groupedButtons: List<Pair<String, List<ClientCommandButton>>>,
        snapshot: ClientSnapshot?,
        commandDeckLayout: CommandDeckLayout,
        commandDeckPresentation: CommandDeckPresentation,
        commandWidth: Float,
        commandButtonHeight: Float
    ): String =
        buildString {
            append(runtime.overlayModeLabel())
            append('|')
            append(runtime.debugVisible)
            append('|')
            append(commandDeckLayout.scrollHeight)
            append('|')
            append(commandDeckLayout.buttonHeight)
            append('|')
            append(commandDeckPresentation.columns)
            append('|')
            append(commandDeckPresentation.buttonHeight)
            append('|')
            append(commandDeckPresentation.scrollHeight)
            append('|')
            append(commandDeckPresentation.actorInset)
            append('|')
            append(commandWidth.toInt())
            append('|')
            append(commandButtonHeight.toInt())
            append('|')
            append(snapshot?.entities?.size ?: -1)
            append('|')
            append(snapshot?.resourceNodes?.size ?: -1)
            val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
            groupedButtons.forEach { (groupName, buttons) ->
                append('|')
                append(groupName)
                buttons.forEach { button ->
                    append(';')
                    append(button.actionId)
                    append(':')
                    append(runtime.isActionEnabled(button.actionId))
                    append(':')
                    append(runtime.isActionActive(button.actionId))
                    append(':')
                    append(commandPulseUntilMillis[button.actionId]?.let { it > System.currentTimeMillis() } == true)
                    append(':')
                    append(formatDeckCommandButtonStateSuffix(button.actionId, selected) ?: "-")
                }
            }
        }

    private fun buildSelectionHeadline(): String {
        val raw = runtime.session.state.viewState.selectionHudLine
        if (!raw.isNullOrBlank() && !raw.equals("selection hud: none", ignoreCase = true)) {
            val snapshot = runtime.snapshot
            val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
            if (selected.isEmpty()) return raw
        }
        val snapshot = runtime.snapshot ?: return "Awaiting"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val focused = resolveFocusedEntity(snapshot, selected)
        return formatSelectionHeadline(selected, focused, runtime.session.state.viewedFaction, snapshot.entities.size)
    }

    private fun buildPortraitText(): String {
        val snapshot = runtime.snapshot ?: return "NO\nDATA"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) {
            return runtime.session.state.viewedFaction?.let { "F$it\nVIEW" } ?: "OBS\nVIEW"
        }
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        return if (selected.size == 1) {
            "${compactPortraitType(lead.typeId)}\n${compactPortraitRole(lead.archetype)}"
        } else {
            "${selected.size} UN\n${compactPortraitType(lead.typeId)}"
        }
    }

    private fun compactPortraitType(typeId: String?): String =
        (typeId ?: "UNIT")
            .replace('_', ' ')
            .uppercase()
            .take(12)

    private fun compactPortraitRole(archetype: String?): String =
        (archetype ?: "ROLE")
            .replace('_', ' ')
            .uppercase()
            .take(12)

    private fun buildHealthLine(): String {
        val snapshot = runtime.snapshot ?: return "HP -"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "HP -"
        val hp = selected.sumOf { it.hp }
        val maxHp = selected.sumOf { it.maxHp.coerceAtLeast(1) }
        return "HP $hp/$maxHp"
    }

    private fun buildSelectionRosterLine(): String {
        val snapshot = runtime.snapshot ?: return "No ros"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        return formatSelectionRosterSummary(selected)
    }

    private fun wrapHudPanel(
        content: Table,
        tone: Color,
        innerPad: Float = 4f,
        contentFillAlpha: Float = 0f
    ): Table =
        Table().apply {
            background = null
            add(Table().apply { background = assets.panelDrawable(tone.cpy().apply { a *= 0.76f }) }).height(1f).expandX().fillX().row()
            add(
                Table().apply {
                    add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.WHITE, 0.08f).apply { a *= 0.72f }) }).width(1f).expandY().fillY()
                    add(
                        Table().apply {
                            background =
                                if (contentFillAlpha > 0f) {
                                    assets.panelDrawable(assets.theme.colors.panelAlt.cpy().also { it.a = contentFillAlpha })
                                } else {
                                    null
                                }
                            pad(2f, innerPad, innerPad, innerPad)
                            add(content).expand().fill()
                        }
                    ).expand().fill()
                    add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.BLACK, 0.16f).apply { a *= 0.72f }) }).width(1f).expandY().fillY()
                }
            ).expand().fill().row()
            add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.BLACK, 0.12f).apply { a *= 0.72f }) }).height(1f).expandX().fillX()
        }

    private fun wrapTopStrip(content: Table): Table =
        Table().apply {
            background = null
            add(content).expandX().fillX().row()
            add(
                Table().apply {
                    background = assets.panelDrawable(assets.theme.colors.borderDefault.cpy().also { it.a = 0.54f })
                }
            ).height(1f).expandX().fillX().padTop(2f)
        }

    private fun buildCommandGlyph(actionId: String, disabled: Boolean, checked: Boolean): Table {
        val colors = assets.theme.colors
        val frameColor =
            when {
                disabled -> colors.panelAlt.cpy().also { it.a = 0.94f }
                checked -> colors.panelStrong.cpy().lerp(colors.production, 0.26f).also { it.a = 0.98f }
                else -> colors.panelAlt.cpy().also { it.a = 0.95f }
            }
        val accentColor =
            when {
                disabled -> colors.textPrimary.cpy().also { it.a = 0.04f }
                checked -> colors.production.cpy().also { it.a = 0.18f }
                actionId == "attackMove" -> colors.warning.cpy().also { it.a = 0.22f }
                actionId == "rally" -> colors.build.cpy().also { it.a = 0.20f }
                actionId.startsWith("build:") -> colors.production.cpy().also { it.a = 0.18f }
                actionId.startsWith("train:") -> colors.production.cpy().also { it.a = 0.18f }
                actionId.startsWith("research:") -> colors.research.cpy().also { it.a = 0.18f }
                else -> colors.textPrimary.cpy().also { it.a = 0.10f }
            }
        return Table().apply {
            background = assets.panelDrawable(frameColor)
            pad(1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(accentColor)
                    when {
                        actionId == "attackMove" -> {
                            add(Table().apply { background = assets.panelDrawable(colors.warning.cpy().also { it.a = if (disabled) 0.16f else 0.82f }) }).width(2f).height(8f).padRight(1f)
                            add(Table().apply { background = assets.panelDrawable(colors.warning.cpy().also { it.a = if (disabled) 0.16f else 0.82f }) }).width(6f).height(2f)
                        }
                        actionId == "move" || actionId == "patrol" || actionId == "hold" -> {
                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = if (disabled) 0.14f else 0.78f }) }).width(6f).height(2f).row()
                            add(Table().apply { background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = if (disabled) 0.14f else 0.78f }) }).width(2f).height(5f)
                        }
                        actionId == "rally" -> {
                            add(Table().apply { background = assets.panelDrawable(colors.build.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).size(7f, 2f).row()
                            add(Table().apply { background = assets.panelDrawable(colors.build.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).size(2f, 2f).padTop(1f)
                        }
                        actionId.startsWith("build:") -> {
                            add(Table().apply { background = assets.panelDrawable(colors.production.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).size(6f, 2f).row()
                            add(Table().apply { background = assets.panelDrawable(colors.production.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).size(2f, 6f)
                        }
                        actionId.startsWith("train:") -> {
                            add(Table().apply { background = assets.panelDrawable(colors.production.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).width(7f).height(2f).row()
                            add(Table().apply { background = assets.panelDrawable(colors.production.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).width(3f).height(5f)
                        }
                        actionId.startsWith("research:") -> {
                            add(Table().apply { background = assets.panelDrawable(colors.research.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).width(7f).height(2f).row()
                            add(Table().apply { background = assets.panelDrawable(colors.research.cpy().also { it.a = if (disabled) 0.14f else 0.82f }) }).width(5f).height(2f)
                        }
                        else -> {
                            add(Table().apply { background = assets.panelDrawable(colors.textPrimary.cpy().also { it.a = if (disabled) 0.10f else 0.48f }) }).size(4f, 4f)
                        }
                    }
                }
            ).size(10f, 10f)
        }
    }

    private fun wrapMinimapPanel(content: Table): Table =
        Table().apply {
            val colors = assets.theme.colors
            background = assets.panelDrawable(colors.scrim.cpy().also { it.a = 0.68f })
            pad(1f, 1f, 2f, 1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.22f })
                    pad(1f, 1f, 2f, 1f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.commandSoft.cpy().also { it.a = 0.66f })
                                }
                            ).size(12f, 3f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.18f).also { it.a = 0.78f })
                                }
                            ).width(6f).height(2f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.78f })
                                }
                            ).width(3f).height(6f).left().padBottom(1f).row()
                            add(content).expand().fill().row()
                            add(
                                Table().apply {
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.78f })
                                        }
                                    ).width(20f).height(2f).right().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.command, 0.14f).also { it.a = 0.78f })
                                        }
                                    ).width(8f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.78f })
                                        }
                                    ).width(10f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panel.cpy().lerp(colors.selection, 0.12f).also { it.a = 0.78f })
                                        }
                                    ).width(4f).height(4f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelSoft.cpy().also { it.a = 0.78f })
                                        }
                                    ).width(9f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelStrong.cpy().also { it.a = 0.78f })
                                        }
                                    ).width(3f).height(5f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(colors.panelAlt.cpy().also { it.a = 0.78f })
                                        }
                                    ).width(6f).height(2f).right().padTop(1f)
                                }
                            ).right().padTop(1f)
                        }
                    ).expand().fill()
                }
            ).expand().fill()
        }

    private fun buildCommandHeader(groups: List<Pair<String, List<ClientCommandButton>>>): String {
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        return resolveCommandDeckHeader(runtime.overlayModeLabel(), groups, selected, productionPage)
    }

    private fun buildCenterStatusLine(): String {
        val snapshot = runtime.snapshot ?: return "No status"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return "No status"
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        val statusBits = buildList {
            lead.activeOrder?.takeIf { it.isNotBlank() }?.let { add("O ${it.lowercase().take(6)}") }
            if (lead.orderQueueSize > 0) add("Q${lead.orderQueueSize}")
            if (lead.pathRemainingNodes > 0) add("P${lead.pathRemainingNodes}")
            lead.activeProductionType?.let { add("P ${it.take(6)}") }
            lead.activeResearchTech?.let { add("R ${it.take(6)}") }
            if (lead.underConstruction) add("B")
            lead.harvestPhase?.let { add("H ${it.lowercase().take(6)}") }
            if (lead.harvestCargoAmount != null && lead.harvestCargoAmount > 0) {
                add("C ${(lead.harvestCargoKind ?: "res").take(3)}:${lead.harvestCargoAmount}")
            }
        }
        return if (statusBits.isEmpty()) "Ready" else statusBits.joinToString(" · ")
    }

    private fun rebuildCenterStatusStrip() {
        centerStatusStrip.clearChildren()
        centerStatusStrip.defaults().left().pad(0f, 0f, 0f, 3f)
        val bits = buildCenterStatusBits()
        if (bits.isEmpty()) {
            val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.READY)
            centerStatusStrip.add(buildStatusChip("RDY", tones.first, tones.second))
            return
        }
        bits.forEachIndexed { index, bit ->
            centerStatusStrip.add(buildStatusChip(bit.label, bit.background, bit.accent))
            if (index == 2) {
                centerStatusStrip.row()
            }
        }
    }

    private data class StatusChip(
        val label: String,
        val background: Color,
        val accent: Color
    )

    private fun buildCenterStatusBits(): List<StatusChip> {
        val snapshot = runtime.snapshot ?: return emptyList()
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return emptyList()
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        val queueChipTone = resolveQueueChipTone(lead)
        return buildList {
            lead.activeOrder?.takeIf { it.isNotBlank() }?.let {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.COMMAND)
                add(StatusChip("O ${it.lowercase().take(4)}", tones.first, tones.second))
            }
            if (lead.orderQueueSize > 0) add(StatusChip("Q${lead.orderQueueSize}", queueChipTone.first, queueChipTone.second))
            if (lead.pathRemainingNodes > 0) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.COMMAND)
                add(StatusChip("P${lead.pathRemainingNodes}", tones.first, tones.second))
            }
            lead.activeProductionType?.let {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.PRODUCTION)
                add(StatusChip("P ${it.take(4)}", tones.first, tones.second))
            }
            lead.activeResearchTech?.let {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.RESEARCH)
                add(StatusChip("R ${it.take(4)}", tones.first, tones.second))
            }
            if (lead.underConstruction) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.BUILD)
                add(StatusChip("BLD", tones.first, tones.second))
            }
            lead.harvestPhase?.let {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.BUILD)
                add(StatusChip("H ${it.lowercase().take(3)}", tones.first, tones.second))
            }
            if (lead.harvestCargoAmount != null && lead.harvestCargoAmount > 0) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.BUILD)
                add(
                    StatusChip(
                        "C ${(lead.harvestCargoKind ?: "res").take(1).uppercase()}${lead.harvestCargoAmount}",
                        tones.first,
                        tones.second
                    )
                )
            }
        }.take(6)
    }

    private fun resolveQueueChipTone(entity: EntitySnapshot): Pair<Color, Color> {
        val panel = assets.theme.colors.panel
        val order = entity.activeOrder?.lowercase()
        val phase = entity.harvestPhase?.lowercase()
        return when {
            order == "attack" || order == "attackmove" ->
                panel.cpy().lerp(assets.theme.colors.warning, 0.24f).also { it.a = 0.92f } to assets.theme.colors.warning.cpy().also { it.a = 0.92f }
            phase == "gather" || phase == "return" || order == "construct" ->
                panel.cpy().lerp(assets.theme.colors.production, 0.24f).also { it.a = 0.92f } to
                    if (entity.harvestCargoKind == "gas") assets.theme.colors.build.cpy().also { it.a = 0.92f } else assets.theme.colors.production.cpy().also { it.a = 0.90f }
            order == "patrol" ->
                panel.cpy().lerp(assets.theme.colors.research, 0.24f).also { it.a = 0.92f } to assets.theme.colors.research.cpy().also { it.a = 0.92f }
            order == "hold" ->
                panel.cpy().lerp(assets.theme.colors.panelStrong, 0.24f).also { it.a = 0.92f } to assets.theme.colors.textSecondary.cpy().also { it.a = 0.92f }
            entity.buildTargetId != null || entity.rallyX != null || entity.rallyY != null ->
                panel.cpy().lerp(assets.theme.colors.build, 0.24f).also { it.a = 0.92f } to assets.theme.colors.build.cpy().also { it.a = 0.90f }
            else ->
                panel.cpy().lerp(assets.theme.colors.command, 0.20f).also { it.a = 0.90f } to assets.theme.colors.commandSoft.cpy().also { it.a = 0.88f }
        }
    }

    private fun buildStatusChip(text: String, backgroundTone: Color, accentTone: Color): Table =
        Table().apply {
            background = assets.panelDrawable(backgroundTone)
            pad(1f, 2f, 1f, 2f)
            add(Table().apply { background = assets.panelDrawable(accentTone) }).width(2f).height(8f).padRight(3f)
            add(Label(text.uppercase(), assets.mutedLabelStyle).apply { color = assets.theme.colors.textPrimary }).left()
        }

    private fun buildCenterFooterLine(): String =
        formatCenterFooterInstruction(
            buildModeTypeId = runtime.buildModeTypeId,
            rallyModeBuildingId = runtime.rallyModeBuildingId,
            groundMode = runtime.groundMode,
            hasSelection = runtime.session.state.selectedIds.isNotEmpty()
        )

    private fun rebuildQueueStatusStrip() {
        queueStatusStrip.clearChildren()
        queueStatusStrip.defaults().left().pad(0f, 0f, 0f, 3f)
        val bits = buildQueueStatusBits()
        if (bits.isEmpty()) {
            val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.IDLE)
            queueStatusStrip.add(buildStatusChip("IDLE", tones.first, tones.second))
            return
        }
        bits.forEachIndexed { index, bit ->
            queueStatusStrip.add(buildStatusChip(bit.label, bit.background, bit.accent))
            if (index == 1) {
                queueStatusStrip.row()
            }
        }
    }

    private fun buildQueueStatusBits(): List<StatusChip> {
        val snapshot = runtime.snapshot ?: return emptyList()
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        if (selected.isEmpty()) return emptyList()
        val lead = resolveFocusedEntity(snapshot, selected) ?: selected.first()
        return buildList {
            if (lead.productionQueueSize > 0 || lead.activeProductionType != null) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.PRODUCTION)
                add(
                    StatusChip(
                        "P ${(lead.activeProductionType ?: "Q").take(4)}",
                        tones.first,
                        tones.second
                    )
                )
                if (lead.productionQueueSize > 0) {
                    add(StatusChip("x${lead.productionQueueSize}", tones.first.cpy(), tones.second.cpy()))
                }
                if (lead.activeProductionRemainingTicks > 0) {
                    add(StatusChip("${lead.activeProductionRemainingTicks}t", tones.first.cpy(), tones.second.cpy()))
                }
            }
            if (lead.researchQueueSize > 0 || lead.activeResearchTech != null) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.RESEARCH)
                add(
                    StatusChip(
                        "R ${(lead.activeResearchTech ?: "Q").take(4)}",
                        tones.first,
                        tones.second
                    )
                )
                if (lead.researchQueueSize > 0) {
                    add(StatusChip("x${lead.researchQueueSize}", tones.first.cpy(), tones.second.cpy()))
                }
                if (lead.activeResearchRemainingTicks > 0) {
                    add(StatusChip("${lead.activeResearchRemainingTicks}t", tones.first.cpy(), tones.second.cpy()))
                }
            }
            if (lead.underConstruction) {
                val tones = statusChipTonePair(assets.theme.colors, StatusChipRole.BUILD)
                add(StatusChip("BLD", tones.first, tones.second))
                lead.constructionRemainingTicks?.takeIf { it > 0 }?.let {
                    add(StatusChip("${it}t", tones.first.cpy(), tones.second.cpy()))
                }
            }
            if (lead.supportsRally == true) {
                val rallyTones = statusChipTonePair(assets.theme.colors, StatusChipRole.BUILD)
                add(
                    StatusChip(
                        "RLY",
                        rallyTones.first,
                        rallyTones.second
                    )
                )
                if (lead.rallyX != null && lead.rallyY != null) {
                    val commandTones = statusChipTonePair(assets.theme.colors, StatusChipRole.COMMAND)
                    add(
                        StatusChip(
                            "${lead.rallyX.toInt()}:${lead.rallyY.toInt()}",
                            commandTones.first,
                            commandTones.second
                        )
                    )
                }
            }
        }.take(6)
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
                        append("P ")
                        append(lead.activeProductionType ?: "queue")
                        if (lead.productionQueueSize > 0) append(" x${lead.productionQueueSize}")
                        if (lead.activeProductionRemainingTicks > 0) append(" ${lead.activeProductionRemainingTicks}t")
                    }
                )
            }
            if (lead.researchQueueSize > 0 || lead.activeResearchTech != null) {
                add(
                    buildString {
                        append("R ")
                        append(lead.activeResearchTech ?: "queue")
                        if (lead.researchQueueSize > 0) append(" x${lead.researchQueueSize}")
                        if (lead.activeResearchRemainingTicks > 0) append(" ${lead.activeResearchRemainingTicks}t")
                    }
                )
            }
            if (lead.underConstruction) {
                add(
                    buildString {
                        append("B")
                        lead.constructionRemainingTicks?.let { append(" ${it}t") }
                    }
                )
            }
            if (lead.supportsRally == true) {
                add(
                    if (lead.rallyX != null && lead.rallyY != null) {
                        "Rally ${lead.rallyX.toInt()},${lead.rallyY.toInt()}"
                    } else {
                        "Rally idle"
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
            lead.supportsRally == true -> "RALLY"
            else -> "QUEUE"
        }
    }

    private fun updateHealthBar() {
        val colors = assets.theme.colors
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        val takingDamage = selected.any { runtime.isDamageFlashActive(it.id) }
        val impactKind = selected.firstNotNullOfOrNull { runtime.damageImpactKind(it.id) }
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
        healthBarBack.background =
            assets.panelDrawable(
                when {
                    takingDamage && (impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE) ->
                        colors.panel.cpy().lerp(colors.build, 0.24f).also { it.a = 0.96f }
                    takingDamage -> colors.panel.cpy().lerp(colors.danger, 0.28f).also { it.a = 0.96f }
                    else -> colors.panelAlt.cpy()
                }
            )
        healthBarFill.background =
            assets.panelDrawable(
                when {
                    ratio >= 0.66f ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.10f)
                            takingDamage -> colors.build.cpy().lerp(colors.textPrimary, 0.10f)
                            else -> colors.selection.cpy()
                        }
                    ratio >= 0.33f ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> colors.warningSoft.cpy().lerp(colors.textPrimary, 0.08f)
                            takingDamage -> colors.warning.cpy().lerp(colors.textPrimary, 0.06f)
                            else -> colors.production.cpy().lerp(colors.textPrimary, 0.06f)
                        }
                    else ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> colors.warning.cpy().lerp(colors.dangerSoft, 0.28f)
                            takingDamage -> colors.dangerSoft.cpy().lerp(colors.textPrimary, 0.06f)
                            else -> colors.danger.cpy().lerp(colors.textPrimary, 0.10f)
                        }
                }
            )
        healthBarBack.add(healthBarFill).width(fillWidth).expandY().fillY().left()
        healthBarBack.add().expandX().fillX()
    }

    private fun rebuildSelectionGrid() {
        selectionGrid.clearChildren()
        val snapshot = runtime.snapshot ?: return
        val centerPanelLayout = computeCenterPanelLayout(Gdx.graphics.width)
        val selectedEntities = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val pageSize = 8
        val pageCount = ((selectedEntities.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        selectionPage = selectionPage.coerceIn(0, pageCount - 1)
        val pageStart = selectionPage * pageSize
        val selected = selectedEntities.drop(pageStart).take(pageSize)
        if (selected.isEmpty()) {
            selectionGrid.add(
                Table().apply {
                    background = assets.panelDrawable(assets.theme.colors.panelAlt.cpy().also { it.a = 0.74f })
                    pad(4f, 8f, 4f, 8f)
                    add(Label("No slots", assets.mutedLabelStyle)).left()
                }
            ).left()
            return
        }
        selectionGrid.defaults().pad(0f, 2f, 2f, 0f)
        selected.forEachIndexed { index, entity ->
            selectionGrid.add(buildSelectionSlot(entity, centerPanelLayout)).size(centerPanelLayout.rosterSlotSize.toFloat(), centerPanelLayout.rosterSlotSize.toFloat())
            if ((index + 1) % 4 == 0) {
                selectionGrid.row()
            }
        }
    }

    private fun buildSelectionSlot(entity: EntitySnapshot, layout: CenterPanelLayout): Table {
        val colors = assets.theme.colors
        val visualLayout = computeSelectionSlotVisualLayout(layout.rosterSlotSize)
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        val focused = focusedSelectionId == entity.id || (focusedSelectionId == null && runtime.session.state.selectedIds.firstOrNull() == entity.id)
        val damaged = runtime.isDamageFlashActive(entity.id)
        val impactKind = runtime.damageImpactKind(entity.id)
        val focusPulse = if (focused) uiPulse(900L) else 0f
        val clickPulse = slotClickPulse(entity.id)
        val isWorker = isWorkerEntity(entity)
        val tone = selectionSlotRoleTone(colors, entity)
        val badgeTone = selectionSlotBadgeTone(colors, entity)
        val hpColor =
            when {
                impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE ->
                    when {
                        hpRatio >= 0.66f -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.10f)
                        hpRatio >= 0.33f -> colors.warningSoft.cpy().lerp(colors.textPrimary, 0.08f)
                        else -> colors.warning.cpy().lerp(colors.dangerSoft, 0.24f)
                    }
                hpRatio >= 0.66f -> colors.selection.cpy()
                hpRatio >= 0.33f -> colors.production.cpy().lerp(colors.textPrimary, 0.06f)
                else -> colors.danger.cpy().lerp(colors.textPrimary, 0.10f)
            }
        val shortName = buildSelectionSlotCode(entity)
        return Table().apply {
            background =
                assets.panelDrawable(
                    if (focused) selectionFocusShellTone(focusPulse)
                    else if (clickPulse > 0f) selectionSlotShellTone(colors, entity, damaged, clickPulse)
                    else if (damaged) selectionSlotShellTone(colors, entity, damaged, 0f)
                    else selectionSlotShellTone(colors, entity, damaged = false, clickPulse = 0f)
                )
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            pad(1f)
            add(
                Table().apply {
                    background =
                        assets.panelDrawable(
                            if (focused) selectionFocusCardTone(focusPulse)
                            else selectionSlotCardTone(colors, entity, damaged, clickPulse)
                        )
                    pad(if (focused) 2f else 1.5f)
                    add(
                        Table().apply {
                            background =
                                assets.panelDrawable(
                                    if (focused) selectionFocusBaseTone().cpy().apply { a = 0.62f + (focusPulse * 0.18f) }
                                    else if (damaged) colors.dangerSoft.cpy().also { it.a = 0.28f }
                                    else colors.textPrimary.cpy().also { it.a = 0.08f }
                                )
                        }
                    ).height(visualLayout.topBarHeight).expandX().fillX().padBottom(2f).row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(colors.textPrimary.cpy().also { it.a = if (focused) 0.08f else 0.04f })
                            pad(2f, 1f, 0.5f, 1f)
                            add(Label(shortName, assets.titleLabelStyle)).center().expandX().fillX()
                        }
                    ).expandX().fillX().height(visualLayout.titleHeight).row()
                    add(buildSelectionSlotGlyph(entity, badgeTone, focused, damaged, visualLayout)).center().padTop(1f).row()
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background =
                                        assets.panelDrawable(
                                            if (focused) selectionFocusBaseTone().cpy().lerp(Color.WHITE, 0.12f)
                                            else colors.textPrimary.cpy().also { it.a = 0.08f }
                                        )
                                }
                            ).size(6f * visualLayout.glyphScale, visualLayout.topBarHeight)
                            add().expandX().fillX()
                            add(
                                Table().apply {
                                background =
                                    assets.panelDrawable(
                                        if (impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE) colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.10f).also { it.a = 0.96f }
                                        else if (damaged) colors.dangerSoft.cpy().also { it.a = 0.96f }
                                        else colors.textPrimary.cpy().also { it.a = 0.06f }
                                    )
                                }
                            ).size(visualLayout.markerSize, visualLayout.markerSize)
                        }
                    ).expandX().fillX().padTop(1f).row()
                    add(
                        Table().apply {
                            background =
                                assets.panelDrawable(
                                    if (focused) colors.panel.cpy().lerp(colors.warning, 0.24f).also { it.a = 1f }
                                    else if (damaged) colors.panel.cpy().lerp(colors.danger, 0.24f).also { it.a = 1f }
                                    else colors.panelAlt.cpy()
                                )
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(if (damaged) hpColor.cpy().lerp(Color.WHITE, 0.24f) else hpColor)
                                }
                            ).width(visualLayout.hpBarWidth * hpRatio.coerceIn(0f, 1f)).height(4f).left()
                            add().expandX().fillX()
                        }
                    ).width(visualLayout.hpBarWidth).height(4f).padTop(2f)
                }
            ).expand().fill()
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        markSlotPulse(entity.id)
                        if (tapCount >= 2) {
                            runtime.session.replaceSelection(intArrayOf(entity.id))
                            focusedSelectionId = entity.id
                            runtime.centerOnSelection(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
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

    private fun buildSelectionSlotCode(entity: EntitySnapshot): String {
        return formatEntityBadgeCode(entity.typeId)
    }

    private fun buildSelectionSlotGlyph(entity: EntitySnapshot, badgeTone: Color, focused: Boolean, damaged: Boolean, layout: SelectionSlotVisualLayout): Table {
        val isWorker = (entity.typeId ?: "").contains("worker", ignoreCase = true)
        val shellTone =
            if (focused) badgeTone.cpy().lerp(Color.WHITE, 0.22f)
            else if (damaged) badgeTone.cpy().lerp(Color.SCARLET, 0.25f)
            else badgeTone
        val unit = layout.glyphScale
        return Table().apply {
            pad(0f)
            when {
                entity.footprintWidth != null -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(12f * unit, 2f * unit).colspan(3).row()
                    repeat(2) { row ->
                        repeat(3) { col ->
                            add(
                                Table().apply {
                                    background =
                                        assets.panelDrawable(
                                            if (row == 0 && col == 1) shellTone.cpy().lerp(Color.WHITE, 0.18f)
                                            else shellTone.cpy().mul(0.82f, 0.82f, 0.82f, 1f)
                                        )
                                }
                            ).size(3f * unit, 3f * unit).pad(0.5f * unit)
                        }
                        row()
                    }
                }

                isWorker -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(8f * unit, 2f * unit).colspan(3).row()
                    add().size(2f * unit, 2f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.12f))
                        }
                    ).size(4f * unit, 5f * unit).pad(0.5f * unit)
                    add().size(2f * unit, 2f * unit).row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f * unit, 2f * unit).padRight(0.5f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f * unit, 2f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f * unit, 2f * unit).padLeft(0.5f * unit)
                }

                entity.weaponId != null -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f * unit, 6f * unit).padRight(0.5f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.14f))
                        }
                    ).size(6f * unit, 2f * unit).padTop(2f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(2f * unit, 2f * unit).padLeft(0.5f * unit)
                }

                else -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(4f * unit, 4f * unit).padRight(0.5f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.12f))
                        }
                    ).size(4f * unit, 6f * unit)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(4f * unit, 4f * unit).padLeft(0.5f * unit)
                }
            }
        }
    }

    private fun syncSelectionPage(snapshot: ClientSnapshot?) {
        val bannerSignature = "${buildActionBannerLine()}|${buildCommandHintLine()}"
        if (bannerSignature != lastBannerSignature) {
            lastBannerSignature = bannerSignature
            bannerPulseUntilMillis = System.currentTimeMillis() + HUD_SELECTION_PULSE_DURATION_MS
        }
        val overlaySignature = "${runtime.pauseOverlayVisible}|${runtime.helpOverlayVisible}|${runtime.attackWarningLine().orEmpty()}|${runtime.noticeLine().orEmpty()}"
        if (overlaySignature != lastOverlaySignature) {
            lastOverlaySignature = overlaySignature
            overlayHeaderPulseUntilMillis = System.currentTimeMillis() + HUD_SELECTION_PULSE_DURATION_MS
        }
        val topModeSignature = "${runtime.overlayModeLabel()}|${runtime.playControlState.paused}|${runtime.pauseOverlayVisible}"
        if (topModeSignature != lastTopModeSignature) {
            lastTopModeSignature = topModeSignature
            topModePulseUntilMillis = System.currentTimeMillis() + HUD_SELECTION_PULSE_DURATION_MS
        }
        val topStatusSignature = "${runtime.attackWarningLine().orEmpty()}|${runtime.noticeLine().orEmpty()}|${buildStatusBadgeLine()}"
        if (topStatusSignature != lastTopStatusSignature) {
            lastTopStatusSignature = topStatusSignature
            topStatusPulseUntilMillis = System.currentTimeMillis() + HUD_SELECTION_PULSE_DURATION_MS
        }
        val signature = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }?.joinToString(",") { it.id.toString() }.orEmpty()
        if (signature != lastSelectionSignature) {
            selectionPage = 0
            val selectedIds = runtime.session.state.selectedIds
            if (focusedSelectionId != null && focusedSelectionId !in selectedIds) {
                focusedSelectionId = null
            }
            lastSelectionSignature = signature
            selectionHudPulseUntilMillis = System.currentTimeMillis() + HUD_SELECTION_PULSE_DURATION_MS
        }
        if (focusedSelectionId == null) {
            focusedSelectionId = runtime.session.state.selectedIds.firstOrNull()
        }
    }

    private fun selectionHudPulse(): Float {
        val remaining = selectionHudPulseUntilMillis - System.currentTimeMillis()
        return feedbackPulseDecay(remaining, HUD_SELECTION_PULSE_DURATION_MS)
    }

    private fun topStatusPulse(): Float {
        val remaining = topStatusPulseUntilMillis - System.currentTimeMillis()
        return feedbackPulseDecay(remaining, HUD_SELECTION_PULSE_DURATION_MS)
    }

    private fun topModePulse(): Float {
        val remaining = topModePulseUntilMillis - System.currentTimeMillis()
        return feedbackPulseDecay(remaining, HUD_SELECTION_PULSE_DURATION_MS)
    }

    private fun overlayHeaderPulse(): Float {
        val remaining = overlayHeaderPulseUntilMillis - System.currentTimeMillis()
        return feedbackPulseDecay(remaining, HUD_SELECTION_PULSE_DURATION_MS)
    }

    private fun bannerPulse(): Float {
        val remaining = bannerPulseUntilMillis - System.currentTimeMillis()
        return feedbackPulseDecay(remaining, HUD_SELECTION_PULSE_DURATION_MS)
    }

    private fun markCommandPulse(actionId: String) {
        commandPulseUntilMillis[actionId] = System.currentTimeMillis() + COMMAND_CLICK_PULSE_DURATION_MS
    }

    private fun runActionWithPulse(actionId: String) {
        if (!shouldDispatchCommandUiAction(runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) {
            return
        }
        markCommandPulse(actionId)
        runtime.executeAction(actionId, Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
    }

    private fun commandClickPulse(actionId: String): Float {
        val until = commandPulseUntilMillis[actionId] ?: return 0f
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            commandPulseUntilMillis.remove(actionId)
            return 0f
        }
        return feedbackPulseDecay(remaining, COMMAND_CLICK_PULSE_DURATION_MS)
    }

    private fun markSlotPulse(entityId: Int) {
        slotPulseUntilMillis[entityId] = System.currentTimeMillis() + SLOT_CLICK_PULSE_DURATION_MS
    }

    private fun slotClickPulse(entityId: Int): Float {
        val until = slotPulseUntilMillis[entityId] ?: return 0f
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            slotPulseUntilMillis.remove(entityId)
            return 0f
        }
        return feedbackPulseDecay(remaining, SLOT_CLICK_PULSE_DURATION_MS)
    }

    private fun updateSelectionPager(snapshot: ClientSnapshot?) {
        val selectedCount = snapshot?.entities?.count { it.id in runtime.session.state.selectedIds } ?: 0
        val pageSize = 8
        val pageCount = ((selectedCount + pageSize - 1) / pageSize).coerceAtLeast(1)
        selectionPage = selectionPage.coerceIn(0, pageCount - 1)
        selectionPageLabel.setText(if (selectedCount == 0) "Pg 0/0" else "Pg ${selectionPage + 1}/$pageCount")
        controlGroupsLabel.setText(runtime.controlGroupSummaryLine()?.replace("Groups ", "Grp ") ?: "Grp idle")
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
                    background = assets.panelDrawable(assets.theme.colors.panelSoft.cpy().also { it.a = 0.90f })
                    pad(1f)
                    add(
                        makeButton("$group:$count", style = assets.subtleButtonStyle()) {
                            runtime.handleControlGroup(group, assign = false, add = false, viewWidth = Gdx.graphics.width, viewHeight = computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
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
        if (middlePanActive || minimapPanActive || selectionDragActive) return
        var deltaX = 0f
        var deltaY = 0f
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        if (isHudSurface(mouseX, mouseY)) return
        val width = Gdx.graphics.width.toFloat()
        val height = worldViewportHeight
        if (mouseX <= edgePanMargin) {
            deltaX += edgePanSpeed * edgePanIntensity((edgePanMargin - mouseX) / edgePanMargin)
        }
        if (mouseX >= width - edgePanMargin) {
            deltaX -= edgePanSpeed * edgePanIntensity((mouseX - (width - edgePanMargin)) / edgePanMargin)
        }
        if (mouseY <= edgePanMargin) {
            deltaY += edgePanSpeed * edgePanIntensity((edgePanMargin - mouseY) / edgePanMargin)
        }
        if (mouseY >= height - edgePanMargin) {
            deltaY -= edgePanSpeed * edgePanIntensity((mouseY - (height - edgePanMargin)) / edgePanMargin)
        }
        if (deltaX != 0f || deltaY != 0f) {
            runtime.nudgePanBy(deltaX, deltaY)
        }
    }

    private fun edgePanIntensity(normalized: Float): Float {
        val clamped = normalized.coerceIn(0f, 1f)
        return 0.38f + (clamped * clamped * 0.92f)
    }

    private fun isHudSurface(screenX: Float, screenY: Float): Boolean {
        if (screenY >= computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height)) return true
        if (gdxMiniMapBounds(Gdx.graphics.width, Gdx.graphics.height).contains(screenX, screenY)) return true
        return actorContains(topBar, screenX, screenY) ||
            actorContains(bottomHud, screenX, screenY) ||
            actorContains(attackWarningTable, screenX, screenY) ||
            actorContains(pauseOverlay, screenX, screenY) ||
            actorContains(helpOverlay, screenX, screenY)
    }

    private fun actorContains(actor: com.badlogic.gdx.scenes.scene2d.Actor, screenX: Float, screenY: Float): Boolean {
        val stageY = stage.viewport.screenHeight - screenY
        val local = actor.stageToLocalCoordinates(Vector2(screenX, stageY))
        return actor.hit(local.x, local.y, false) != null
    }

    private fun computeWorldViewportHeight(screenWidth: Int, screenHeight: Int): Int =
        computeWorldViewportHeightForLayout(screenWidth, screenHeight)

    private fun buildSelectionMetaLine(): String {
        val snapshot = runtime.snapshot ?: return "No live snapshot"
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val structureDeck = usesStructureCommandDeck(selected)
        return formatSelectionMetaLine(
            selected = selected,
            viewedFaction = runtime.session.state.viewedFaction,
            totalEntities = snapshot.entities.size,
            includeCapabilities = !structureDeck
        )
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
        runtime.noticeLine()?.removePrefix("notice: ")?.let(::compactNotice)?.let { return it }
        runtime.currentGroundPing()?.let { return formatGroundPingBanner(it.kind, it.queued) }
        return formatOverlayReadyBanner(
            buildModeTypeId = runtime.buildModeTypeId,
            rallyModeBuildingId = runtime.rallyModeBuildingId,
            groundMode = runtime.groundMode
        )
    }

    private fun buildAttackWarningText(): String =
        when {
            runtime.isStructureLossWarning() -> "STRUCTURE LOST"
            runtime.attackWarningLine() != null -> "UNDER ATTACK"
            else -> ""
        }

    private fun buildCommandHintLine(): String =
        formatOverlayHintLine(
            hoverHint = runtime.hoverHintLine(),
            buildModeTypeId = runtime.buildModeTypeId,
            rallyModeBuildingId = runtime.rallyModeBuildingId,
            groundMode = runtime.groundMode,
            hasSelection = runtime.session.state.selectedIds.isNotEmpty()
        )

    private fun currentActionBannerTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> panel.cpy().lerp(command, 0.18f).also { it.a = 0.76f }
                runtime.rallyModeBuildingId != null -> panel.cpy().lerp(production, 0.18f).also { it.a = 0.76f }
                runtime.groundMode != null -> panel.cpy().lerp(selection, 0.18f).also { it.a = 0.76f }
                runtime.currentGroundPing()?.kind == GroundPingKind.MOVE -> panel.cpy().lerp(selection, 0.22f).also { it.a = 0.80f }
                runtime.currentGroundPing()?.kind == GroundPingKind.ATTACK -> panel.cpy().lerp(warning, 0.26f).also { it.a = 0.82f }
                runtime.currentGroundPing()?.kind == GroundPingKind.BUILD -> panel.cpy().lerp(command, 0.22f).also { it.a = 0.80f }
                runtime.currentGroundPing()?.kind == GroundPingKind.INVALID -> panel.cpy().lerp(danger, 0.28f).also { it.a = 0.82f }
                runtime.noticeKind() == NoticeKind.TRADE -> panel.cpy().lerp(production, 0.20f).also { it.a = 0.80f }
                runtime.noticeKind() == NoticeKind.LOSS -> panel.cpy().lerp(danger, 0.24f).also { it.a = 0.80f }
                runtime.noticeKind() == NoticeKind.KILL -> panel.cpy().lerp(build, 0.18f).also { it.a = 0.80f }
                runtime.noticeLine() != null -> panel.cpy().lerp(warning, 0.18f).also { it.a = 0.76f }
                else -> panelAlt.cpy().also { it.a = 0.62f }
            }
        }

    private fun currentActionBannerPulseTone(pulse: Float): Color =
        with(assets.theme.colors) {
            currentActionBannerTone().lerp(panel.cpy().lerp(build, 0.16f).also { it.a = 0.82f }, pulse * 0.24f)
        }

    private fun currentActionBannerTextTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> production.cpy().lerp(textPrimary, 0.24f).also { it.a = 0.96f }
                runtime.rallyModeBuildingId != null -> production.cpy().lerp(textPrimary, 0.22f).also { it.a = 0.96f }
                runtime.groundMode != null -> selectionSoft.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                runtime.noticeKind() == NoticeKind.TRADE -> production.cpy().lerp(textPrimary, 0.32f).also { it.a = 0.98f }
                runtime.noticeKind() == NoticeKind.LOSS -> dangerSoft.cpy().lerp(textPrimary, 0.22f).also { it.a = 0.98f }
                runtime.noticeKind() == NoticeKind.KILL -> build.cpy().lerp(textPrimary, 0.26f).also { it.a = 0.98f }
                runtime.noticeLine() != null -> warning.cpy().lerp(textPrimary, 0.22f).also { it.a = 0.96f }
                else -> textSecondary.cpy().also { it.a = 0.94f }
            }
        }

    private fun currentTopEconomyTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.attackWarningLine() != null -> warningSoft.cpy().lerp(textPrimary, 0.28f).also { it.a = 0.98f }
                runtime.noticeLine() != null -> production.cpy().lerp(textPrimary, 0.30f).also { it.a = 0.98f }
                else -> textSecondary.cpy().lerp(commandSoft, 0.10f).also { it.a = 0.96f }
            }
        }

    private fun currentTopSelectionTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.session.state.selectedIds.isEmpty() -> textMuted.cpy().also { it.a = 0.94f }
                runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                    production.cpy().lerp(textPrimary, 0.34f).also { it.a = 0.98f }
                else -> commandSoft.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.98f }
            }
        }

    private fun currentTopModeTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> production.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.98f }
                runtime.rallyModeBuildingId != null -> production.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.98f }
                runtime.groundMode != null -> selectionSoft.cpy().lerp(textPrimary, 0.16f).also { it.a = 0.98f }
                runtime.pauseOverlayVisible || runtime.playControlState.paused -> commandSoft.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.98f }
                else -> selectionSoft.cpy().also { it.a = 0.96f }
            }
        }

    private fun currentAttackWarningCardTone(): Color =
        warningBannerCardTone(
            assets.theme.colors,
            warningBannerState(
                hasAttackWarning = runtime.attackWarningLine() != null,
                isStructureLoss = runtime.isStructureLossWarning(),
                noticeKind = runtime.noticeKind()
            )
        )

    private fun currentAttackWarningAccentTone(): Color =
        warningBannerAccentTone(
            assets.theme.colors,
            warningBannerState(
                hasAttackWarning = runtime.attackWarningLine() != null,
                isStructureLoss = runtime.isStructureLossWarning(),
                noticeKind = runtime.noticeKind()
            )
        )

    private fun currentPauseHeaderTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.attackWarningLine() != null -> panelStrong.cpy().lerp(danger, 0.32f).also { it.a = 0.92f }
                runtime.noticeLine() != null -> panelStrong.cpy().lerp(warning, 0.24f).also { it.a = 0.92f }
                else -> panelStrong.cpy().lerp(command, 0.20f).also { it.a = 0.92f }
            }
        }

    private fun currentPauseHeaderPulseTone(pulse: Float): Color =
        with(assets.theme.colors) {
            currentPauseHeaderTone().lerp(panelStrong.cpy().lerp(warning, 0.26f).also { it.a = 0.94f }, pulse * 0.26f)
        }

    private fun currentHelpHeaderTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> panelStrong.cpy().lerp(production, 0.24f).also { it.a = 0.92f }
                runtime.rallyModeBuildingId != null -> panelStrong.cpy().lerp(production, 0.22f).also { it.a = 0.92f }
                runtime.groundMode != null -> panelStrong.cpy().lerp(selection, 0.22f).also { it.a = 0.92f }
                else -> panelStrong.cpy().lerp(command, 0.20f).also { it.a = 0.92f }
            }
        }

    private fun currentHelpHeaderPulseTone(pulse: Float): Color =
        with(assets.theme.colors) {
            currentHelpHeaderTone().lerp(panelStrong.cpy().lerp(selection, 0.24f).also { it.a = 0.90f }, pulse * 0.22f)
        }

    private fun currentCommandHintCardTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> panel.cpy().lerp(production, 0.16f).also { it.a = 0.86f }
                runtime.rallyModeBuildingId != null -> panel.cpy().lerp(production, 0.14f).also { it.a = 0.86f }
                runtime.groundMode != null -> panel.cpy().lerp(selection, 0.16f).also { it.a = 0.86f }
                runtime.session.state.selectedIds.isNotEmpty() -> panel.cpy().lerp(command, 0.16f).also { it.a = 0.84f }
                else -> panelSoft.cpy().also { it.a = 0.82f }
            }
        }

    private fun currentCommandHintPulseTone(pulse: Float): Color =
        with(assets.theme.colors) {
            currentCommandHintCardTone().lerp(panel.cpy().lerp(build, 0.14f).also { it.a = 0.82f }, pulse * 0.22f)
        }

    private fun currentCommandHintTextTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> production.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.96f }
                runtime.rallyModeBuildingId != null -> production.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                runtime.groundMode != null -> selectionSoft.cpy().lerp(textPrimary, 0.16f).also { it.a = 0.96f }
                runtime.session.state.selectedIds.isNotEmpty() -> commandSoft.cpy().lerp(textPrimary, 0.14f).also { it.a = 0.96f }
                else -> textMuted.cpy().also { it.a = 0.94f }
            }
        }

    private fun currentSelectionHeadlineCardTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.session.state.selectedIds.isEmpty() -> panelSoft.cpy().also { it.a = 0.90f }
                runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                    panel.cpy().lerp(production, 0.14f).also { it.a = 0.92f }
                else -> panel.cpy().lerp(selection, 0.12f).also { it.a = 0.92f }
            }
        }

    private fun currentSelectionHeadlinePulseTone(pulse: Float): Color =
        with(assets.theme.colors) {
            currentSelectionHeadlineCardTone().lerp(panel.cpy().lerp(selectionSoft, 0.26f).also { it.a = 0.86f }, pulse * 0.34f)
        }

    private fun currentTopSelectionShellTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panelStrong.cpy().also { it.a = 0.58f }.lerp(panelStrong.cpy().lerp(selection, 0.10f).also { it.a = 0.68f }, pulse * 0.20f)
        }

    private fun currentTopSelectionCardTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panelSoft.cpy().also { it.a = 0.72f }.lerp(panel.cpy().lerp(selection, 0.18f).also { it.a = 0.82f }, pulse * 0.28f)
        }

    private fun currentTopModeShellTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panelStrong.cpy().also { it.a = 0.58f }.lerp(panelStrong.cpy().lerp(build, 0.08f).also { it.a = 0.68f }, pulse * 0.20f)
        }

    private fun currentTopModeCardTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panel.cpy().lerp(selection, 0.14f).also { it.a = 0.72f }.lerp(panel.cpy().lerp(build, 0.18f).also { it.a = 0.82f }, pulse * 0.28f)
        }

    private fun currentTopStatusShellTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panelStrong.cpy().also { it.a = 0.60f }.lerp(panelStrong.cpy().lerp(danger, 0.14f).also { it.a = 0.70f }, pulse * 0.22f)
        }

    private fun currentTopStatusCardTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panel.cpy().lerp(command, 0.18f).also { it.a = 0.74f }.lerp(panel.cpy().lerp(warning, 0.24f).also { it.a = 0.84f }, pulse * 0.30f)
        }

    private fun currentPortraitFrameTone(pulse: Float): Color =
        with(assets.theme.colors) {
            panel.cpy().lerp(build, 0.08f).also { it.a = 0.80f }.lerp(panel.cpy().lerp(selection, 0.18f).also { it.a = 0.86f }, pulse * 0.28f)
        }

    private fun currentSelectionHeadlineTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.session.state.selectedIds.isEmpty() -> textSecondary.cpy().also { it.a = 0.94f }
                runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                    production.cpy().lerp(textPrimary, 0.24f).also { it.a = 0.96f }
                else -> selectionSoft.cpy().lerp(textPrimary, 0.14f).also { it.a = 0.96f }
            }
        }

    private fun selectionFocusBaseTone(): Color = selectionFocusAccentTone(assets.theme.colors)

    private fun selectionFocusShellTone(pulse: Float): Color =
        selectionFocusShellBackgroundTone(assets.theme.colors, pulse)

    private fun selectionFocusCardTone(pulse: Float): Color =
        selectionFocusCardBackgroundTone(assets.theme.colors, pulse)

    private fun currentSelectionMetaTone(): Color =
        if (runtime.session.state.selectedIds.isEmpty()) assets.theme.colors.textMuted.cpy().also { it.a = 0.92f }
        else assets.theme.colors.textSecondary.cpy().also { it.a = 0.94f }

    private fun currentRosterTone(): Color =
        if (runtime.session.state.selectedIds.isEmpty()) assets.theme.colors.textMuted.cpy().also { it.a = 0.90f }
        else assets.theme.colors.textSecondary.cpy().also { it.a = 0.94f }

    private fun currentCenterStatusTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.session.state.selectedIds.isEmpty() -> textMuted.cpy().also { it.a = 0.92f }
                buildCenterStatusLine() == "Ready" -> commandSoft.cpy().lerp(textPrimary, 0.10f).also { it.a = 0.92f }
                else -> textSecondary.cpy().also { it.a = 0.94f }
            }
        }

    private fun currentQueueHeaderTone(): Color =
        with(assets.theme.colors) {
            when (buildQueueHeaderLine()) {
                "PRODUCTION" -> production.cpy().lerp(textPrimary, 0.24f).also { it.a = 0.96f }
                "RESEARCH" -> research.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.96f }
                "CONSTRUCT" -> build.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.96f }
                else -> textMuted.cpy().also { it.a = 0.92f }
            }
        }

    private fun compactHint(raw: String): String {
        val cleaned =
            raw
                .removePrefix("Switch to ")
                .removeSuffix(" view")
                .replace("faction ", "f")
                .replace("selection", "sel")
                .replace("current ", "")
                .replace("camera ", "")
                .replace("scenario ", "")
                .replace("preset", "pst")
                .trim()
        return if (cleaned.length <= 24) cleaned else cleaned.take(21).trimEnd() + "..."
    }

    private fun compactNotice(raw: String): String {
        val cleaned =
            raw
                .replace("Warning: ", "")
                .replace("structure lost", "STRUCT LOST")
                .replace("invalid build placement", "INVALID BUILD")
                .replace("research complete", "tech done")
                .replace(" complete", " done")
                .replace(" ready", " ready")
                .replace("view auto-switched to ", "view ")
                .replace("observer", "obs")
                .trim()
        return if (cleaned.length <= 26) cleaned else cleaned.take(23).trimEnd() + "..."
    }

    private fun uiPulse(periodMs: Long = 1200L): Float {
        val cycle = (System.currentTimeMillis() % periodMs).toFloat() / periodMs.toFloat()
        return if (cycle < 0.5f) cycle * 2f else (1f - cycle) * 2f
    }

    private fun currentQueueStatusTone(): Color =
        with(assets.theme.colors) {
            when (buildQueueHeaderLine()) {
                "PRODUCTION" -> production.cpy().lerp(textPrimary, 0.38f).also { it.a = 0.94f }
                "RESEARCH" -> research.cpy().lerp(textPrimary, 0.34f).also { it.a = 0.94f }
                "CONSTRUCT" -> build.cpy().lerp(textPrimary, 0.34f).also { it.a = 0.94f }
                else -> textMuted.cpy().also { it.a = 0.88f }
            }
        }

    private fun currentQueueCardTone(): Color =
        with(assets.theme.colors) {
            when (buildQueueHeaderLine()) {
                "PRODUCTION" -> panel.cpy().lerp(production, 0.12f).also { it.a = 0.74f }
                "RESEARCH" -> panel.cpy().lerp(research, 0.12f).also { it.a = 0.74f }
                "CONSTRUCT" -> panel.cpy().lerp(build, 0.12f).also { it.a = 0.74f }
                else -> panelAlt.cpy().also { it.a = 0.62f }
            }
        }

    private fun currentQueueHeaderBackgroundTone(): Color =
        with(assets.theme.colors) {
            when (buildQueueHeaderLine()) {
                "PRODUCTION" -> panelStrong.cpy().lerp(production, 0.22f).also { it.a = 0.84f }
                "RESEARCH" -> panelStrong.cpy().lerp(research, 0.22f).also { it.a = 0.84f }
                "CONSTRUCT" -> panelStrong.cpy().lerp(build, 0.22f).also { it.a = 0.84f }
                else -> panelStrong.cpy().lerp(command, 0.18f).also { it.a = 0.82f }
            }
        }

    private fun currentStatusBadgeTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.attackWarningLine() != null -> dangerSoft.cpy().lerp(textPrimary, 0.22f).also { it.a = 0.98f }
                runtime.noticeLine() != null -> warningSoft.cpy().lerp(textPrimary, 0.22f).also { it.a = 0.98f }
                runtime.playControlState.paused -> commandSoft.cpy().lerp(textPrimary, 0.14f).also { it.a = 0.96f }
                else -> selectionSoft.cpy().lerp(textPrimary, 0.12f).also { it.a = 0.96f }
            }
        }

    private fun currentMinimapTitleTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.attackWarningLine() != null -> dangerSoft.cpy().lerp(textPrimary, 0.20f).also { it.a = 0.98f }
                runtime.session.state.viewedFaction == null -> commandSoft.cpy().lerp(textPrimary, 0.16f).also { it.a = 0.96f }
                else -> selectionSoft.cpy().lerp(textPrimary, 0.14f).also { it.a = 0.96f }
            }
        }

    private fun currentMinimapHintTone(): Color =
        with(assets.theme.colors) {
            when {
                runtime.groundMode != null -> selectionSoft.cpy().also { it.a = 0.94f }
                runtime.rallyModeBuildingId != null -> production.cpy().also { it.a = 0.94f }
                runtime.buildModeTypeId != null -> production.cpy().lerp(textPrimary, 0.16f).also { it.a = 0.94f }
                else -> textMuted.cpy().also { it.a = 0.90f }
            }
        }

    private fun currentMinimapHeaderCardTone(): Color =
        minimapHeaderCardTone(
            assets.theme.colors,
            hasAttackWarning = runtime.attackWarningLine() != null,
            viewedFaction = runtime.session.state.viewedFaction,
            groundModeArmed = runtime.groundMode != null,
            rallyModeArmed = runtime.rallyModeBuildingId != null,
            buildModeArmed = runtime.buildModeTypeId != null
        )

    private fun currentMinimapHeaderAccentTone(): Color =
        minimapHeaderAccentTone(
            assets.theme.colors,
            hasAttackWarning = runtime.attackWarningLine() != null,
            groundModeArmed = runtime.groundMode != null,
            rallyModeArmed = runtime.rallyModeBuildingId != null,
            buildModeArmed = runtime.buildModeTypeId != null
        )

    private fun currentMinimapFooterCardTone(): Color =
        minimapFooterCardTone(
            assets.theme.colors,
            commandArmed = runtime.isGameplayCommandArmed()
        )

    private fun currentMinimapFooterAccentTone(): Color =
        minimapFooterAccentTone(
            assets.theme.colors,
            commandArmed = runtime.isGameplayCommandArmed()
        )

    private fun currentCommandHeaderTone(groups: List<Pair<String, List<ClientCommandButton>>>): Color =
        with(assets.theme.colors) {
            when {
                runtime.buildModeTypeId != null -> production.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                runtime.rallyModeBuildingId != null -> production.cpy().lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                runtime.groundMode != null -> selectionSoft.cpy().lerp(textPrimary, 0.16f).also { it.a = 0.96f }
                groups.any { it.first in setOf("Production", "Train") && it.second.isNotEmpty() } ->
                    commandGroupAccentTone("Production").lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                groups.any { it.first == "Research" && it.second.isNotEmpty() } ->
                    commandGroupAccentTone("Research").lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                groups.any { it.first == "Build" && it.second.isNotEmpty() } ->
                    commandGroupAccentTone("Build").lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                groups.any { it.first == "Orders" && it.second.isNotEmpty() } ->
                    commandGroupAccentTone("Orders").lerp(textPrimary, 0.18f).also { it.a = 0.96f }
                else -> textSecondary.cpy().also { it.a = 0.94f }
            }
        }

    private fun pingTone(kind: GroundPingKind): Color =
        when (kind) {
            GroundPingKind.MOVE -> assets.theme.colors.selection.cpy().also { it.a = 0.94f }
            GroundPingKind.ATTACK -> assets.theme.colors.warning.cpy().also { it.a = 0.96f }
            GroundPingKind.BUILD -> assets.theme.colors.commandSoft.cpy().also { it.a = 0.96f }
            GroundPingKind.INVALID -> assets.theme.colors.danger.cpy().also { it.a = 0.96f }
        }

    private fun commandGroupHeaderTone(group: String): Color =
        commandGroupHeaderBackgroundTone(assets.theme.colors, group)

    private fun commandGroupAccentTone(group: String): Color =
        commandGroupAccentBackgroundTone(assets.theme.colors, group)

    private fun commandGroupShellTone(group: String): Color =
        commandGroupShellBackgroundTone(assets.theme.colors, group)

    private fun commandButtonCardTone(actionId: String): Color =
        with(assets.theme.colors) {
            when {
                actionId.startsWith("build") -> panel.cpy().lerp(command, 0.18f).also { it.a = 0.98f }
                actionId.startsWith("train") || actionId.startsWith("research") -> panel.cpy().lerp(production, 0.20f).also { it.a = 0.98f }
                actionId == "move" || actionId == "hold" -> panel.cpy().lerp(selection, 0.16f).also { it.a = 0.98f }
                actionId == "patrol" -> panel.cpy().lerp(commandSoft, 0.18f).also { it.a = 0.98f }
                actionId == "attackMove" -> panel.cpy().lerp(danger, 0.22f).also { it.a = 0.98f }
                else -> panel.cpy().also { it.a = 0.98f }
            }
        }

    private fun commandButtonFrameTone(
        actionId: String,
        disabled: Boolean,
        checked: Boolean,
        queued: Boolean,
        activePulse: Float,
        clickPulse: Float,
        cardTone: Color
    ): Color =
        with(assets.theme.colors) {
            when {
                disabled -> panelAlt.cpy().also { it.a = 0.94f }
                checked -> panel.cpy().lerp(commandButtonCheckedBackgroundTone(actionId), 0.22f + (activePulse * 0.10f)).also { it.a = 0.92f }
                queued -> panel.cpy().lerp(commandButtonQueueTone(actionId), 0.24f).also { it.a = 0.92f }
                else -> cardTone.cpy().lerp(commandButtonPulseAccentTone(actionId), clickPulse * 0.26f)
            }
        }

    private fun commandButtonShellTone(
        actionId: String,
        disabled: Boolean,
        checked: Boolean,
        queued: Boolean,
        activePulse: Float,
        clickPulse: Float
    ): Color =
        with(assets.theme.colors) {
            when {
                disabled -> scrim.cpy().also { it.a = 0.88f }
                checked -> panelStrong.cpy().lerp(commandButtonCheckedBackgroundTone(actionId), 0.20f + (activePulse * 0.08f)).also { it.a = 0.90f }
                queued -> panelStrong.cpy().lerp(commandButtonQueueTone(actionId), 0.18f).also { it.a = 0.88f }
                else -> panelAlt.cpy().lerp(commandButtonShellAccentTone(actionId), clickPulse * 0.22f).also { it.a = 0.90f }
            }
        }

    private fun commandButtonEdgeTone(
        actionId: String,
        disabled: Boolean,
        checked: Boolean,
        queued: Boolean,
        activePulse: Float,
        clickPulse: Float
    ): Color =
        with(assets.theme.colors) {
            when {
                disabled -> textPrimary.cpy().also { it.a = 0.06f }
                checked -> commandButtonCheckedBackgroundTone(actionId).cpy().lerp(textPrimary, 0.18f).also { it.a = 0.72f }
                queued -> commandButtonQueueTone(actionId).cpy().lerp(textPrimary, 0.12f).also { it.a = 0.66f }
                else -> commandButtonAccentTone(actionId).lerp(selectionSoft.cpy().also { it.a = 0.88f }, clickPulse * 0.18f)
            }
        }

    private fun commandButtonRailTone(
        actionId: String,
        disabled: Boolean,
        checked: Boolean,
        queued: Boolean,
        activePulse: Float
    ): Color =
        with(assets.theme.colors) {
            when {
                disabled -> textMuted.cpy().also { it.a = 0.28f }
                checked -> commandButtonCheckedBackgroundTone(actionId).cpy().also { it.a = 0.70f + (activePulse * 0.08f) }
                queued -> commandButtonQueueTone(actionId).cpy().also { it.a = 0.72f }
                else -> commandButtonAccentTone(actionId).cpy()
            }
        }

    private fun commandButtonQueueTone(actionId: String): Color =
        commandButtonQueueBackgroundTone(assets.theme.colors, actionId)

    private fun commandButtonCheckedBackgroundTone(actionId: String): Color =
        commandButtonCheckedAccentTone(assets.theme.colors, actionId)

    private fun commandButtonCornerTone(disabled: Boolean, checked: Boolean): Color =
        with(assets.theme.colors) {
            when {
                disabled -> textPrimary.cpy().also { it.a = 0.08f }
                checked -> warningSoft.cpy().also { it.a = 0.66f }
                else -> textPrimary.cpy().also { it.a = 0.09f }
            }
        }

    private fun commandButtonAccentTone(actionId: String): Color =
        commandButtonAccentBackgroundTone(assets.theme.colors, actionId)

    private fun commandButtonPulseAccentTone(actionId: String): Color =
        commandButtonPulseBackgroundTone(assets.theme.colors, actionId)

    private fun commandButtonShellAccentTone(actionId: String): Color =
        commandButtonShellBackgroundTone(assets.theme.colors, actionId)

    private fun commandButtonStyle(actionId: String): TextButton.TextButtonStyle =
        when {
            actionId == "pause" || actionId == "help" || actionId == "debug" -> assets.secondaryButtonStyle()
            actionId == "rally" -> assets.primaryButtonStyle()
            actionId.startsWith("build:") || actionId.startsWith("train:") || actionId.startsWith("research:") -> assets.primaryButtonStyle()
            actionId == "move" || actionId == "attackMove" || actionId == "patrol" || actionId == "hold" -> assets.primaryButtonStyle()
            else -> assets.subtleButtonStyle()
        }

    private fun commandButtonLabel(button: ClientCommandButton, snapshot: ClientSnapshot?): String {
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        val baseLabel = formatDeckCommandButtonText(button.actionId, button.label)
        val hotkey = resolveCommandButtonHotkey(button.actionId)
        val stateSuffix = formatDeckCommandButtonStateSuffix(button.actionId, selected)
        return formatDeckCommandCaption(baseLabel, hotkey, stateSuffix)
    }

    private fun commandGroups(buttons: List<ClientCommandButton>): List<Pair<String, List<ClientCommandButton>>> {
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        return resolveCommandDeckGroups(buttons, selected, runtime.debugVisible)
    }

    private fun shiftProductionPage(direction: Int) {
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        if (usesStructureCommandDeck(selected)) {
            productionPage = 0
            return
        }
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
        disabledHint: String? = null,
        onClick: () -> Unit
    ): TextButton =
        TextButton(text, style).apply {
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        onClick()
                    }

                    override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                        runtime.setHoverHint(if (isDisabled) (disabledHint ?: "Unavailable") else (hint ?: text))
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
        private var armedLeftPress = false
        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var rightClickHandled = false

        private fun resetPointerState() {
            dragging = false
            panning = false
            minimapDragging = false
            armedLeftPress = false
            rightClickHandled = false
            middlePanActive = false
            minimapPanActive = false
            selectionDragActive = false
            dragSelection = null
        }

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (overlayBlocksWorldInput(runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) return true
            when (button) {
                Input.Buttons.MIDDLE -> {
                    panning = true
                    middlePanActive = true
                    lastX = screenX.toFloat()
                    lastY = screenY.toFloat()
                    return true
                }
                Input.Buttons.LEFT -> {
                    val clickX = screenX.toFloat()
                    val clickY = screenY.toFloat()
                    val minimapHit = gdxMiniMapBounds(Gdx.graphics.width, Gdx.graphics.height).contains(clickX, clickY)
                    val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                    if (minimapHit) {
                        if (runtime.issueMinimapLeftClick(clickX, clickY, Gdx.graphics.width, Gdx.graphics.height, queuedCommand = shiftPressed) &&
                            !runtime.isGameplayCommandArmed()
                        ) {
                            minimapDragging = true
                            minimapPanActive = true
                            dragSelection = null
                        }
                        return true
                    }
                    if (isHudSurface(clickX, clickY)) {
                        return true
                    }
                    startX = screenX.toFloat()
                    startY = screenY.toFloat()
                    lastX = startX
                    lastY = startY
                    armedLeftPress = runtime.isGameplayCommandArmed()
                    dragging = true
                    return true
                }
                Input.Buttons.RIGHT -> {
                    val clickX = screenX.toFloat()
                    val clickY = screenY.toFloat()
                    val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                    if (gdxMiniMapBounds(Gdx.graphics.width, Gdx.graphics.height).contains(clickX, clickY)) {
                        rightClickHandled =
                            runtime.issueMinimapRightClick(
                                clickX,
                                clickY,
                                Gdx.graphics.width,
                                Gdx.graphics.height,
                                Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT),
                                queuedCommand = shiftPressed
                            )
                        return true
                    }
                    if (isHudSurface(clickX, clickY)) {
                        rightClickHandled = false
                        return true
                    }
                    runtime.issueRightClick(
                        clickX,
                        clickY,
                        Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT),
                        queuedCommand = shiftPressed
                    )
                    rightClickHandled = true
                    return true
                }
            }
            return false
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (shouldAbortPointerGesture(runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) {
                resetPointerState()
                return true
            }
            if (minimapDragging) {
                runtime.dragCenterFromMinimap(screenX.toFloat(), screenY.toFloat(), Gdx.graphics.width, Gdx.graphics.height)
                return true
            }
            if (panning) {
                val deltaX = (screenX - lastX) * middlePanScale
                val deltaY = (screenY - lastY) * middlePanScale
                if (abs(deltaX) >= middlePanDeadzone || abs(deltaY) >= middlePanDeadzone) {
                    runtime.panBy(deltaX, deltaY)
                }
                lastX = screenX.toFloat()
                lastY = screenY.toFloat()
                return true
            }
            if (dragging) {
                val dragDistance = maxOf(abs(screenX.toFloat() - startX), abs(screenY.toFloat() - startY))
                if (!armedLeftPress && dragDistance >= SELECTION_DRAG_THRESHOLD_PX) {
                    selectionDragActive = true
                    dragSelection = DragSelectionBox(startX, startY, screenX.toFloat(), screenY.toFloat(), true)
                }
                lastX = screenX.toFloat()
                lastY = screenY.toFloat()
                return true
            }
            return false
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (shouldAbortPointerGesture(runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) {
                resetPointerState()
                return true
            }
            if (button == Input.Buttons.MIDDLE) {
                panning = false
                middlePanActive = false
                return true
            }
            if (button == Input.Buttons.LEFT && minimapDragging) {
                minimapDragging = false
                minimapPanActive = false
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
            val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            val additive = !armedLeftPress && shiftPressed
            if (shouldIssueSelectionBox(armedLeftPress, startX, startY, screenX.toFloat(), screenY.toFloat())) {
                runtime.issueSelectionBox(startX, startY, screenX.toFloat(), screenY.toFloat(), additive)
            } else {
                runtime.issueLeftClick(screenX.toFloat(), screenY.toFloat(), additive, queuedCommand = armedLeftPress && shiftPressed)
            }
            resetPointerState()
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            if (runtime.zoomLocked) return false
            val factor = if (amountY < 0f) 1.1f else 0.9f
            runtime.zoomAt(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), factor)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (!shouldHandleHotkeyWhileOverlayVisible(keycode, runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) {
                return true
            }
            when (keycode) {
                Input.Keys.ESCAPE -> {
                    when (
                        resolveEscapeAction(
                            pauseVisible = runtime.pauseOverlayVisible,
                            helpVisible = runtime.helpOverlayVisible,
                            hasSelection = runtime.session.state.selectedIds.isNotEmpty(),
                            hasArmedMode = runtime.isGameplayCommandArmed()
                        )
                    ) {
                        EscapeAction.CLOSE_PAUSE -> runtime.togglePauseOverlay()
                        EscapeAction.CLOSE_HELP -> runtime.toggleHelpOverlay()
                        EscapeAction.CANCEL_ARMED_MODE -> runtime.cancelArmedMode()
                        EscapeAction.CLEAR_SELECTION -> runtime.clearSelection()
                        EscapeAction.OPEN_PAUSE -> runtime.togglePauseOverlay()
                    }
                }
                Input.Keys.SPACE -> runtime.togglePlayPause()
                Input.Keys.F1 -> runtime.toggleHelpOverlay()
                Input.Keys.TAB -> runtime.toggleDebug()
                Input.Keys.LEFT -> runtime.nudgePanBy(KEYBOARD_PAN_STEP, 0f)
                Input.Keys.RIGHT -> runtime.nudgePanBy(-KEYBOARD_PAN_STEP, 0f)
                Input.Keys.UP -> runtime.nudgePanBy(0f, KEYBOARD_PAN_STEP)
                Input.Keys.DOWN -> runtime.nudgePanBy(0f, -KEYBOARD_PAN_STEP)
                Input.Keys.EQUALS, Input.Keys.PLUS -> if (!runtime.zoomLocked) runtime.zoomAt(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f, 1.1f)
                Input.Keys.MINUS -> if (!runtime.zoomLocked) runtime.zoomAt(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f, 0.9f)
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
                Input.Keys.M -> runActionWithPulse("move")
                Input.Keys.A -> runActionWithPulse("attackMove")
                Input.Keys.P -> runActionWithPulse("patrol")
                Input.Keys.S -> runActionWithPulse("rally")
                Input.Keys.H -> runActionWithPulse("hold")
                Input.Keys.B -> runActionWithPulse("build:Depot")
                Input.Keys.R -> runActionWithPulse("build:ResourceDepot")
                Input.Keys.G -> runActionWithPulse("build:GasDepot")
                Input.Keys.U -> runtime.catalog.trainOptions.getOrNull(0)?.let { runActionWithPulse("train:${it.typeId}") }
                Input.Keys.I -> runtime.catalog.trainOptions.getOrNull(1)?.let { runActionWithPulse("train:${it.typeId}") }
                Input.Keys.O -> runtime.catalog.trainOptions.getOrNull(2)?.let { runActionWithPulse("train:${it.typeId}") }
                Input.Keys.L -> runtime.catalog.researchOptions.firstOrNull()?.let { runActionWithPulse("research:${it.typeId}") }
                Input.Keys.X -> runActionWithPulse("cancelBuild")
                Input.Keys.T -> runActionWithPulse("cancelTrain")
                Input.Keys.Y -> runActionWithPulse("cancelResearch")
                Input.Keys.F2 -> runActionWithPulse("selectViewedFaction")
                Input.Keys.F3 -> runActionWithPulse("selectType")
                Input.Keys.F4 -> runActionWithPulse("selectRole")
                Input.Keys.F11 -> runActionWithPulse("selectAll")
                Input.Keys.F12 -> runActionWithPulse("selectIdleWorkers")
                Input.Keys.F5 -> runtime.restartMatch()
                Input.Keys.F6 -> runtime.cycleScenarioAndRestart(-1)
                Input.Keys.F7 -> runtime.cycleScenarioAndRestart(1)
                Input.Keys.F -> runActionWithPulse("selectDamaged")
                Input.Keys.V -> runActionWithPulse("selectCombat")
                Input.Keys.N -> runActionWithPulse("selectProducers")
                Input.Keys.Z -> runActionWithPulse("selectTrainers")
                Input.Keys.C -> runActionWithPulse("selectResearchers")
                Input.Keys.J -> runActionWithPulse("selectConstruction")
                Input.Keys.K -> runActionWithPulse("selectHarvesters")
                Input.Keys.Q -> runActionWithPulse("selectReturning")
                Input.Keys.E -> runActionWithPulse("selectCargo")
                Input.Keys.D -> runActionWithPulse("selectDropoffs")
                Input.Keys.HOME -> runtime.centerOnSelection(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
                Input.Keys.END -> runtime.centerOnViewedFaction(Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
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
            runtime.handleControlGroup(group, assign = assign, add = add, viewWidth = Gdx.graphics.width, viewHeight = computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
        }
    }
}

internal enum class EscapeAction {
    CLOSE_PAUSE,
    CLOSE_HELP,
    CANCEL_ARMED_MODE,
    CLEAR_SELECTION,
    OPEN_PAUSE
}

internal data class BottomHudLayout(
    val leftSlotWidth: Int,
    val centerWidth: Int,
    val commandWidth: Int
)

internal data class CommandDeckLayout(
    val scrollHeight: Int,
    val buttonHeight: Int,
    val groupPad: Int,
    val headerPadX: Int,
    val actorInset: Int,
    val sectionGap: Int,
    val buttonGap: Int,
    val headerPadY: Int,
    val pagerButtonWidth: Int,
    val pagerButtonHeight: Int,
    val hintWidth: Int
)

internal data class CommandDeckPresentation(
    val columns: Int,
    val buttonHeight: Int,
    val scrollHeight: Int,
    val actorInset: Int
)

internal data class CenterPanelLayout(
    val portraitSize: Int,
    val rosterSlotSize: Int,
    val pagerButtonSize: Int,
    val pagerLabelWidth: Int,
    val groupSummaryWidth: Int,
    val healthBarWidth: Int,
    val portraitGap: Int,
    val sectionGap: Int,
    val headerPadX: Int,
    val bodyPadX: Int,
    val bodyPadY: Int,
    val healthBarHeight: Int
)

internal data class TopBarLayout(
    val selectionWidth: Int,
    val modeWidth: Int,
    val statusWidth: Int
)

internal data class SelectionSlotVisualLayout(
    val topBarHeight: Float,
    val titleHeight: Float,
    val hpBarWidth: Float,
    val hpBarHeight: Float,
    val glyphScale: Float,
    val markerSize: Float
)

internal fun overlayBlocksWorldInput(pauseVisible: Boolean, helpVisible: Boolean): Boolean =
    pauseVisible || helpVisible

internal fun computeBottomHudHeight(screenWidth: Int, screenHeight: Int): Int {
    val minimapHeight = gdxMiniMapBounds(screenWidth, screenHeight).height
    val compact = screenWidth < 1440
    val centerHeight = if (compact) (screenHeight * 0.094f).coerceIn(78f, 98f) else (screenHeight * 0.098f).coerceIn(82f, 106f)
    val commandHeight = if (compact) (screenHeight * 0.052f).coerceIn(36f, 48f) else (screenHeight * 0.056f).coerceIn(40f, 54f)
    val commandShellHeight = commandHeight + if (compact) 12f else 14f
    return maxOf(minimapHeight, centerHeight, commandShellHeight).toInt()
}

internal fun computeBottomHudLayout(screenWidth: Int, screenHeight: Int): BottomHudLayout {
    val minimapWidth = gdxMiniMapBounds(screenWidth, screenHeight).width.toInt()
    val compact = screenWidth < 1440
    val leftSlotWidth = minimapWidth
    val centerWidth = if (compact) (screenWidth * 0.172f).coerceIn(226f, 286f).toInt() else (screenWidth * 0.182f).coerceIn(248f, 332f).toInt()
    val commandWidth = if (compact) (screenWidth * 0.146f).coerceIn(196f, 238f).toInt() else (screenWidth * 0.154f).coerceIn(220f, 278f).toInt()
    return BottomHudLayout(
        leftSlotWidth = leftSlotWidth,
        centerWidth = centerWidth,
        commandWidth = commandWidth
    )
}

internal fun computeCommandDeckLayout(screenWidth: Int, screenHeight: Int): CommandDeckLayout {
    val compact = screenWidth < 1440
    val scrollHeight = if (compact) (screenHeight * 0.048f).coerceIn(34f, 44f) else (screenHeight * 0.052f).coerceIn(38f, 50f)
    return CommandDeckLayout(
        scrollHeight = scrollHeight.toInt(),
        buttonHeight = if (compact) 13 else 15,
        groupPad = if (compact) 1 else 2,
        headerPadX = if (compact) 3 else 4,
        actorInset = if (compact) 8 else 10,
        sectionGap = if (compact) 3 else 4,
        buttonGap = if (compact) 2 else 3,
        headerPadY = if (compact) 2 else 3,
        pagerButtonWidth = if (compact) 20 else 22,
        pagerButtonHeight = if (compact) 16 else 18,
        hintWidth = if (compact) 156 else 172
    )
}

internal fun computeCommandDeckPresentation(
    base: CommandDeckLayout,
    structureDeck: Boolean
): CommandDeckPresentation =
    if (structureDeck) {
        CommandDeckPresentation(
            columns = 2,
            buttonHeight = base.buttonHeight + 2,
            scrollHeight = base.scrollHeight + 10,
            actorInset = (base.actorInset - 2).coerceAtLeast(6)
        )
    } else {
        CommandDeckPresentation(
            columns = 3,
            buttonHeight = base.buttonHeight,
            scrollHeight = base.scrollHeight,
            actorInset = base.actorInset
        )
    }

internal fun computeCenterPanelLayout(screenWidth: Int): CenterPanelLayout {
    val compact = screenWidth < 1440
    return CenterPanelLayout(
        portraitSize = if (compact) 38 else 46,
        rosterSlotSize = if (compact) 24 else 28,
        pagerButtonSize = if (compact) 14 else 16,
        pagerLabelWidth = if (compact) 42 else 48,
        groupSummaryWidth = if (compact) 52 else 62,
        healthBarWidth = if (compact) 104 else 120,
        portraitGap = if (compact) 4 else 5,
        sectionGap = if (compact) 2 else 3,
        headerPadX = if (compact) 3 else 4,
        bodyPadX = if (compact) 4 else 5,
        bodyPadY = if (compact) 1 else 2,
        healthBarHeight = if (compact) 6 else 7
    )
}

internal fun computeTopBarLayout(screenWidth: Int): TopBarLayout {
    val compact = screenWidth < 1360
    return TopBarLayout(
        selectionWidth = if (compact) 92 else 106,
        modeWidth = if (compact) 84 else 98,
        statusWidth = if (compact) 46 else 52
    )
}

internal fun computeSelectionSlotVisualLayout(rosterSlotSize: Int): SelectionSlotVisualLayout {
    val compact = rosterSlotSize <= 32
    return SelectionSlotVisualLayout(
        topBarHeight = if (compact) 1f else 1.35f,
        titleHeight = if (compact) 10f else 13f,
        hpBarWidth = if (compact) 15f else 18f,
        hpBarHeight = if (compact) 2.5f else 3f,
        glyphScale = if (compact) 0.66f else 0.78f,
        markerSize = if (compact) 2.8f else 3.6f
    )
}

internal fun isWorkerEntity(entity: EntitySnapshot): Boolean =
    (entity.typeId ?: "").contains("worker", ignoreCase = true) || entity.archetype.equals("worker", ignoreCase = true)

internal enum class StatusChipRole {
    READY,
    IDLE,
    COMMAND,
    PRODUCTION,
    RESEARCH,
    BUILD
}

internal enum class CommandButtonRole {
    ATTACK,
    MOVE,
    BUILD,
    TRAIN,
    RESEARCH,
    RALLY,
    SYSTEM,
    DEFAULT
}

internal enum class WarningBannerState {
    DEFAULT,
    NOTICE,
    TRADE,
    KILL,
    LOSS,
    ATTACK,
    STRUCTURE_LOSS
}

internal fun statusChipTonePair(colors: PresentationColorTokens, role: StatusChipRole): Pair<Color, Color> =
    when (role) {
        StatusChipRole.READY ->
            colors.panel.cpy().lerp(colors.command, 0.22f).also { it.a = 0.90f } to
                colors.commandSoft.cpy().also { it.a = 0.92f }
        StatusChipRole.IDLE ->
            colors.panelAlt.cpy().also { it.a = 0.86f } to
                colors.textMuted.cpy().also { it.a = 0.86f }
        StatusChipRole.COMMAND ->
            colors.panel.cpy().lerp(colors.command, 0.20f).also { it.a = 0.90f } to
                colors.commandSoft.cpy().also { it.a = 0.88f }
        StatusChipRole.PRODUCTION ->
            colors.panel.cpy().lerp(colors.production, 0.24f).also { it.a = 0.92f } to
                colors.production.cpy().also { it.a = 0.90f }
        StatusChipRole.RESEARCH ->
            colors.panel.cpy().lerp(colors.research, 0.24f).also { it.a = 0.92f } to
                colors.research.cpy().also { it.a = 0.92f }
        StatusChipRole.BUILD ->
            colors.panel.cpy().lerp(colors.build, 0.24f).also { it.a = 0.92f } to
                colors.build.cpy().also { it.a = 0.90f }
    }

internal fun warningBannerState(
    hasAttackWarning: Boolean,
    isStructureLoss: Boolean,
    noticeKind: NoticeKind?
): WarningBannerState =
    when {
        isStructureLoss -> WarningBannerState.STRUCTURE_LOSS
        hasAttackWarning -> WarningBannerState.ATTACK
        noticeKind == NoticeKind.TRADE -> WarningBannerState.TRADE
        noticeKind == NoticeKind.LOSS -> WarningBannerState.LOSS
        noticeKind == NoticeKind.KILL -> WarningBannerState.KILL
        noticeKind != null -> WarningBannerState.NOTICE
        else -> WarningBannerState.DEFAULT
    }

internal fun warningBannerCardTone(colors: PresentationColorTokens, state: WarningBannerState): Color =
    when (state) {
        WarningBannerState.STRUCTURE_LOSS -> colors.panelStrong.cpy().lerp(colors.danger, 0.42f).also { it.a = 0.96f }
        WarningBannerState.ATTACK -> colors.panelStrong.cpy().lerp(colors.danger, 0.54f).also { it.a = 0.94f }
        WarningBannerState.TRADE -> colors.panelStrong.cpy().lerp(colors.production, 0.28f).also { it.a = 0.92f }
        WarningBannerState.LOSS -> colors.panelStrong.cpy().lerp(colors.danger, 0.32f).also { it.a = 0.92f }
        WarningBannerState.KILL -> colors.panelStrong.cpy().lerp(colors.build, 0.26f).also { it.a = 0.92f }
        WarningBannerState.NOTICE -> colors.panelStrong.cpy().lerp(colors.warning, 0.30f).also { it.a = 0.92f }
        WarningBannerState.DEFAULT -> colors.panelStrong.cpy().lerp(colors.command, 0.20f).also { it.a = 0.92f }
    }

internal fun warningBannerAccentTone(colors: PresentationColorTokens, state: WarningBannerState): Color =
    when (state) {
        WarningBannerState.STRUCTURE_LOSS -> colors.warningSoft.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.82f }
        WarningBannerState.ATTACK -> colors.warningSoft.cpy().also { it.a = 0.76f }
        WarningBannerState.TRADE -> colors.production.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.78f }
        WarningBannerState.LOSS -> colors.dangerSoft.cpy().lerp(colors.textPrimary, 0.10f).also { it.a = 0.80f }
        WarningBannerState.KILL -> colors.build.cpy().lerp(colors.textPrimary, 0.10f).also { it.a = 0.78f }
        WarningBannerState.NOTICE -> colors.warningSoft.cpy().lerp(colors.textPrimary, 0.08f).also { it.a = 0.76f }
        WarningBannerState.DEFAULT -> colors.commandSoft.cpy().also { it.a = 0.74f }
    }

internal fun minimapHeaderCardTone(
    colors: PresentationColorTokens,
    hasAttackWarning: Boolean,
    viewedFaction: Int?,
    groundModeArmed: Boolean,
    rallyModeArmed: Boolean,
    buildModeArmed: Boolean
): Color =
    when {
        hasAttackWarning -> colors.panel.cpy().lerp(colors.danger, 0.18f).also { it.a = 0.72f }
        buildModeArmed || rallyModeArmed -> colors.panel.cpy().lerp(colors.production, 0.16f).also { it.a = 0.68f }
        groundModeArmed -> colors.panel.cpy().lerp(colors.selection, 0.16f).also { it.a = 0.68f }
        viewedFaction == null -> colors.panel.cpy().lerp(colors.command, 0.12f).also { it.a = 0.64f }
        else -> colors.panel.cpy().lerp(colors.selection, 0.12f).also { it.a = 0.62f }
    }

internal fun minimapHeaderAccentTone(
    colors: PresentationColorTokens,
    hasAttackWarning: Boolean,
    groundModeArmed: Boolean,
    rallyModeArmed: Boolean,
    buildModeArmed: Boolean
): Color =
    when {
        hasAttackWarning -> colors.dangerSoft.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.82f }
        buildModeArmed || rallyModeArmed -> colors.production.cpy().lerp(colors.textPrimary, 0.10f).also { it.a = 0.80f }
        groundModeArmed -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.08f).also { it.a = 0.80f }
        else -> colors.selectionSoft.cpy().also { it.a = 0.74f }
    }

internal fun minimapFooterCardTone(colors: PresentationColorTokens, commandArmed: Boolean): Color =
    if (commandArmed) colors.panelAlt.cpy().lerp(colors.selection, 0.12f).also { it.a = 0.54f }
    else colors.panelAlt.cpy().also { it.a = 0.48f }

internal fun minimapFooterAccentTone(colors: PresentationColorTokens, commandArmed: Boolean): Color =
    if (commandArmed) colors.selectionSoft.cpy().also { it.a = 0.74f }
    else colors.commandSoft.cpy().also { it.a = 0.66f }

internal fun selectionFocusAccentTone(colors: PresentationColorTokens): Color =
    colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.96f }

internal fun selectionFocusShellBackgroundTone(colors: PresentationColorTokens, pulse: Float): Color =
    colors.panelStrong.cpy().lerp(colors.selection, 0.26f + (pulse * 0.10f)).also { it.a = 0.96f }

internal fun selectionFocusCardBackgroundTone(colors: PresentationColorTokens, pulse: Float): Color =
    colors.panel.cpy().lerp(colors.selection, 0.34f + (pulse * 0.08f)).also { it.a = 0.98f }

internal fun commandGroupAccentBackgroundTone(colors: PresentationColorTokens, group: String): Color =
    when (group) {
        "Orders" -> colors.commandSoft.cpy().also { it.a = 0.82f }
        "Production", "Train" -> colors.production.cpy().also { it.a = 0.78f }
        "Research" -> colors.research.cpy().also { it.a = 0.78f }
        "Build" -> colors.build.cpy().also { it.a = 0.78f }
        "Utility", "System" -> colors.selectionSoft.cpy().also { it.a = 0.78f }
        else -> colors.commandSoft.cpy().also { it.a = 0.72f }
    }

internal fun commandGroupHeaderBackgroundTone(colors: PresentationColorTokens, group: String): Color {
    val accent = commandGroupAccentBackgroundTone(colors, group)
    val ratio =
        when (group) {
            "Utility", "System" -> 0.12f
            else -> 0.22f
        }
    val alpha =
        when (group) {
            "Orders", "Production", "Train", "Research", "Build", "Utility", "System" -> 0.86f
            else -> 0.82f
        }
    return colors.panel.cpy().lerp(accent, ratio).also { it.a = alpha }
}

internal fun commandGroupShellBackgroundTone(colors: PresentationColorTokens, group: String): Color {
    val accent = commandGroupAccentBackgroundTone(colors, group)
    return colors.panelAlt.cpy().lerp(accent, 0.08f).also { it.a = 0.98f }
}

internal fun resolveCommandButtonRole(actionId: String): CommandButtonRole =
    when {
        actionId == "attackMove" -> CommandButtonRole.ATTACK
        actionId == "move" || actionId == "hold" || actionId == "patrol" -> CommandButtonRole.MOVE
        actionId == "rally" -> CommandButtonRole.RALLY
        actionId.startsWith("build:") || actionId == "cancelBuild" -> CommandButtonRole.BUILD
        actionId.startsWith("train:") || actionId == "cancelTrain" -> CommandButtonRole.TRAIN
        actionId.startsWith("research:") || actionId == "cancelResearch" -> CommandButtonRole.RESEARCH
        actionId == "pause" || actionId == "help" || actionId == "debug" -> CommandButtonRole.SYSTEM
        else -> CommandButtonRole.DEFAULT
    }

internal fun commandButtonAccentBackgroundTone(colors: PresentationColorTokens, actionId: String): Color =
    when (resolveCommandButtonRole(actionId)) {
        CommandButtonRole.ATTACK -> colors.warning.cpy().also { it.a = 0.96f }
        CommandButtonRole.MOVE -> colors.selection.cpy().also { it.a = 0.94f }
        CommandButtonRole.BUILD -> colors.commandSoft.cpy().also { it.a = 0.96f }
        CommandButtonRole.TRAIN -> colors.production.cpy().lerp(colors.commandSoft, 0.40f).also { it.a = 0.72f }
        CommandButtonRole.RESEARCH -> colors.research.cpy().lerp(colors.commandSoft, 0.44f).also { it.a = 0.72f }
        CommandButtonRole.RALLY -> colors.build.cpy().lerp(colors.commandSoft, 0.30f).also { it.a = 0.72f }
        CommandButtonRole.SYSTEM -> colors.selectionSoft.cpy().also { it.a = 0.72f }
        CommandButtonRole.DEFAULT -> colors.commandSoft.cpy().also { it.a = 0.66f }
    }

internal fun commandButtonCheckedAccentTone(colors: PresentationColorTokens, actionId: String): Color =
    when (resolveCommandButtonRole(actionId)) {
        CommandButtonRole.ATTACK -> colors.warningSoft.cpy().lerp(colors.textPrimary, 0.10f).also { it.a = 0.94f }
        CommandButtonRole.MOVE -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.94f }
        CommandButtonRole.BUILD -> colors.command.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.94f }
        CommandButtonRole.TRAIN -> colors.production.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.94f }
        CommandButtonRole.RESEARCH -> colors.research.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.94f }
        CommandButtonRole.RALLY -> colors.build.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.94f }
        CommandButtonRole.SYSTEM -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.14f).also { it.a = 0.94f }
        CommandButtonRole.DEFAULT -> colors.commandSoft.cpy().lerp(colors.textPrimary, 0.14f).also { it.a = 0.94f }
    }

internal fun commandButtonPulseBackgroundTone(colors: PresentationColorTokens, actionId: String): Color =
    when (resolveCommandButtonRole(actionId)) {
        CommandButtonRole.ATTACK -> colors.dangerSoft.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.MOVE -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.16f).also { it.a = 0.96f }
        CommandButtonRole.BUILD -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.TRAIN -> colors.production.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.RESEARCH -> colors.research.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.RALLY -> colors.build.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.SYSTEM -> colors.selectionSoft.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
        CommandButtonRole.DEFAULT -> colors.commandSoft.cpy().lerp(colors.textPrimary, 0.18f).also { it.a = 0.96f }
    }

internal fun commandButtonShellBackgroundTone(colors: PresentationColorTokens, actionId: String): Color =
    when (resolveCommandButtonRole(actionId)) {
        CommandButtonRole.ATTACK -> colors.danger.cpy().also { it.a = 0.92f }
        CommandButtonRole.MOVE -> colors.selection.cpy().also { it.a = 0.92f }
        CommandButtonRole.BUILD -> colors.command.cpy().also { it.a = 0.92f }
        CommandButtonRole.TRAIN -> colors.production.cpy().also { it.a = 0.92f }
        CommandButtonRole.RESEARCH -> colors.research.cpy().also { it.a = 0.92f }
        CommandButtonRole.RALLY -> colors.build.cpy().also { it.a = 0.92f }
        CommandButtonRole.SYSTEM -> colors.selectionSoft.cpy().also { it.a = 0.92f }
        CommandButtonRole.DEFAULT -> colors.build.cpy().also { it.a = 0.92f }
    }

internal fun commandButtonQueueBackgroundTone(colors: PresentationColorTokens, actionId: String): Color =
    when (resolveCommandButtonRole(actionId)) {
        CommandButtonRole.TRAIN -> colors.production.cpy().lerp(colors.selectionSoft, 0.18f)
        CommandButtonRole.RESEARCH -> colors.research.cpy().lerp(colors.selectionSoft, 0.14f)
        CommandButtonRole.BUILD -> colors.build.cpy().lerp(colors.selectionSoft, 0.18f)
        CommandButtonRole.RALLY -> colors.build.cpy().lerp(colors.selectionSoft, 0.24f)
        else -> colors.selectionSoft.cpy()
    }

internal fun selectionSlotRoleTone(colors: PresentationColorTokens, entity: EntitySnapshot): Color =
    when {
        entity.weaponId != null -> colors.panel.cpy().lerp(colors.command, 0.32f).also { it.a = 0.98f }
        entity.footprintWidth != null || entity.footprintHeight != null -> colors.panel.cpy().lerp(colors.production, 0.32f).also { it.a = 0.98f }
        isWorkerEntity(entity) -> colors.panel.cpy().lerp(colors.build, 0.28f).also { it.a = 0.98f }
        else -> colors.panel.cpy().lerp(colors.textMuted, 0.24f).also { it.a = 0.98f }
    }

internal fun selectionSlotBadgeTone(colors: PresentationColorTokens, entity: EntitySnapshot): Color =
    when {
        entity.footprintWidth != null || entity.footprintHeight != null -> colors.production.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.98f }
        isWorkerEntity(entity) -> colors.build.cpy().lerp(colors.textPrimary, 0.14f).also { it.a = 0.98f }
        entity.weaponId != null -> colors.commandSoft.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.98f }
        else -> colors.textMuted.cpy().lerp(colors.textPrimary, 0.12f).also { it.a = 0.96f }
    }

internal fun selectionSlotShellTone(
    colors: PresentationColorTokens,
    entity: EntitySnapshot,
    damaged: Boolean,
    clickPulse: Float
): Color {
    val base =
        when {
            entity.weaponId != null -> colors.panelStrong.cpy().lerp(colors.command, 0.14f)
            entity.footprintWidth != null || entity.footprintHeight != null -> colors.panelStrong.cpy().lerp(colors.production, 0.16f)
            isWorkerEntity(entity) -> colors.panelStrong.cpy().lerp(colors.build, 0.14f)
            else -> colors.panelStrong.cpy().lerp(colors.textMuted, 0.10f)
        }
    return when {
        damaged -> base.lerp(colors.danger, 0.30f).also { it.a = 0.96f }
        clickPulse > 0f -> base.lerp(colors.selection, 0.18f + (clickPulse * 0.12f)).also { it.a = 0.96f }
        else -> base.also { it.a = 0.94f }
    }
}

internal fun selectionSlotCardTone(
    colors: PresentationColorTokens,
    entity: EntitySnapshot,
    damaged: Boolean,
    clickPulse: Float
): Color {
    val base = selectionSlotRoleTone(colors, entity)
    return when {
        damaged -> base.lerp(colors.dangerSoft, 0.22f).also { it.a = 0.98f }
        clickPulse > 0f -> base.lerp(colors.selectionSoft, 0.18f + (clickPulse * 0.14f)).also { it.a = 0.98f }
        else -> base
    }
}

internal fun feedbackPulseDecay(remainingMillis: Long, durationMillis: Long): Float {
    if (remainingMillis <= 0L || durationMillis <= 0L) return 0f
    val normalized = (remainingMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    return normalized * normalized
}

internal fun computeWorldViewportHeightForLayout(screenWidth: Int, screenHeight: Int): Int {
    val bottomHudTop = screenHeight - computeBottomHudHeight(screenWidth, screenHeight)
    val minimapTop = gdxMiniMapBounds(screenWidth, screenHeight).top.toInt()
    return minOf(bottomHudTop, minimapTop).coerceAtLeast((screenHeight * 0.66f).toInt())
}

internal fun shouldHandleHotkeyWhileOverlayVisible(keycode: Int, pauseVisible: Boolean, helpVisible: Boolean): Boolean {
    if (!pauseVisible && !helpVisible) return true
    if (keycode == Input.Keys.ESCAPE) return true
    if (helpVisible && keycode == Input.Keys.F1) return true
    return false
}

internal fun shouldDispatchCommandUiAction(pauseVisible: Boolean, helpVisible: Boolean): Boolean =
    !overlayBlocksWorldInput(pauseVisible, helpVisible)

internal fun shouldAbortPointerGesture(pauseVisible: Boolean, helpVisible: Boolean): Boolean =
    overlayBlocksWorldInput(pauseVisible, helpVisible)

internal fun resolveEscapeAction(
    pauseVisible: Boolean,
    helpVisible: Boolean,
    hasSelection: Boolean,
    hasArmedMode: Boolean
): EscapeAction =
    when {
        pauseVisible -> EscapeAction.CLOSE_PAUSE
        helpVisible -> EscapeAction.CLOSE_HELP
        hasArmedMode -> EscapeAction.CANCEL_ARMED_MODE
        hasSelection -> EscapeAction.CLEAR_SELECTION
        else -> EscapeAction.OPEN_PAUSE
    }

internal fun shouldIssueSelectionBox(commandArmed: Boolean, startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
    if (commandArmed) return false
    return abs(endX - startX) >= 6f || abs(endY - startY) >= 6f
}
