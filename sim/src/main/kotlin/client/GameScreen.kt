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
    private val statusHeader = Label("Battlefield", assets.titleLabelStyle)
    private val centerHeaderLabel = Label("Selected", assets.titleLabelStyle)
    private val selectionMetaLabel = Label("", assets.mutedLabelStyle)
    private val factionOverviewLabel = Label("", assets.mutedLabelStyle)
    private val hudLinesLabel = Label("", assets.bodyLabelStyle)
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
    private val attackWarningLabel = Label("", assets.alertLabelStyle)
    private val minimapFrame = Table()
    private val minimapTitle = Label("Tac Map", assets.titleLabelStyle)
    private val minimapHint = Label("map drag", assets.mutedLabelStyle)
    private val bottomHud = Table()
    private val leftHudColumn = Table()
    private val statusCard = Table()
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
    private val commandPulseUntilMillis = HashMap<String, Long>()
    private val slotPulseUntilMillis = HashMap<Int, Long>()
    private var screenFadeAlpha = 1f
    private var soundVariantTick = 0
    private val soundCooldownUntilMillis = HashMap<String, Long>()

    init {
        statusHeader.setFontScale(0.94f)
        centerHeaderLabel.setFontScale(0.94f)
        commandHeaderLabel.setFontScale(0.94f)
        queueHeaderLabel.setFontScale(0.86f)
        economyLabel.setFontScale(0.92f)
        topSelectionLabel.setFontScale(0.88f)
        modeLabel.setFontScale(0.88f)
        statusBadgeLabel.setFontScale(0.88f)
        commandHintLabel.setFontScale(0.84f)
        selectionMetaLabel.setFontScale(0.84f)
        factionOverviewLabel.setFontScale(0.84f)
        selectionRosterLabel.setFontScale(0.84f)
        footerLabel.setFontScale(0.84f)
        centerFooterLabel.setFontScale(0.84f)
        minimapHint.setFontScale(0.82f)
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
        val root =
            Table().apply {
                setFillParent(true)
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
            }

        topBar.apply {
            background = null
            pad(0f, 3f, 0f, 3f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.60f))
                    pad(1f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.74f))
                            pad(1f, 3f, 1f, 3f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.78f)) }).width(2f).expandY().fillY().padRight(4f)
                            add(economyLabel).left()
                        }
                    ).left().expandX().fillX()
                }
            ).left().expandX().fillX()
            add(
                topSelectionShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f))
                    pad(1f)
                    add(
                        topSelectionCard.apply {
                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.72f))
                            pad(1f, 3f, 1f, 3f)
                            add(topSelectionLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(118f).center().padLeft(3f).padRight(3f)
            add(
                topModeShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f))
                    pad(1f)
                    add(
                        topModeCard.apply {
                            background = assets.panelDrawable(Color(0.10f, 0.18f, 0.16f, 0.72f))
                            pad(1f, 3f, 1f, 3f)
                            add(modeLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(110f).center().padRight(3f)
            add(
                topStatusShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.60f))
                    pad(1f)
                    add(
                        topStatusCard.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.74f))
                            pad(1f, 3f, 1f, 3f)
                            add(statusBadgeLabel).right()
                        }
                    ).expandX().fillX()
                }
            ).width(60f).right()
        }

        minimapFrame.apply {
            background = null
            pad(0f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
        }

        statusCard.apply {
            background = assets.panelDrawable(Color(0.04f, 0.09f, 0.12f, 0.72f))
            pad(10f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(2f).expandY().fillY().padRight(5f)
                    add(statusHeader).left()
                }
            ).left().row()
            add(Table().apply { background = assets.panelDrawable(Color(0.20f, 0.44f, 0.50f, 0.62f)) }).height(1f).expandX().fillX().padTop(5f).row()
            add(factionOverviewLabel).left().expandX().fillX().padTop(6f).row()
            add(hudLinesLabel).left().expandX().fillX().padTop(8f)
        }

        commandCard.apply {
            background = null
            pad(3f)
            top()
            add(
                Table().apply {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.76f))
                            pad(1f)
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.78f))
                                    pad(2f, 6f, 2f, 6f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(2f).expandY().fillY().padRight(4f)
                                    add(commandHeaderLabel).left()
                                }
                            )
                        }
                    ).left().padRight(4f)
                    add().expandX().fillX()
                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftProductionPage(-1) }).width(22f).height(18f).padRight(3f)
                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftProductionPage(1) }).width(22f).height(18f)
                }
            ).expandX().fillX().row()
            add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.50f, 0.62f)) }).height(1f).expandX().fillX().padTop(5f).row()
            add(
                commandHintCard.apply {
                    pad(2f, 2f, 0f, 2f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.68f))
                            pad(1f)
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.14f, 0.20f, 0.24f, 0.72f))
                                    pad(2f, 4f, 2f, 4f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(2f).expandY().fillY().padRight(4f)
                                    add(commandHintLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX()
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
            pad(0f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.12f, 0.16f, 0.48f))
                    pad(1f, 5f, 1f, 5f)
                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.78f)) }).width(2f).expandY().fillY().padRight(4f)
                    add(actionBannerLabel).center()
                }
            ).center()
        }

        centerCard.apply {
            background = null
            pad(1f)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(
                Table().apply {
                    add(centerHeaderLabel).left()
                    add().expandX().fillX()
                    add(healthLabel).right()
                }
            ).expandX().fillX().padBottom(1f).row()
            add(Table().apply { background = assets.panelDrawable(Color(0.20f, 0.44f, 0.50f, 0.62f)) }).height(1f).expandX().fillX().padTop(2f).row()
            add(
                selectionHeadlineCard.apply {
                    background = assets.panelDrawable(Color(0.14f, 0.20f, 0.24f, 0.70f))
                    pad(1f, 4f, 1f, 4f)
                    add(selectionLabel).left().expandX().fillX()
                }
            ).left().expandX().fillX().padTop(2f).row()
            add(Table().apply { background = assets.panelDrawable(Color(0.09f, 0.15f, 0.19f, 0.60f)) }).height(1f).expandX().fillX().padTop(1f).row()
            add(
                Table().apply {
                    add(
                        portraitFrame.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.20f, 0.18f, 0.80f))
                            pad(2f)
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.34f, 0.40f, 0.16f, 0.76f))
                                }
                            ).height(1f).expandX().fillX().row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.08f, 0.11f, 0.09f, 0.76f))
                                    pad(5f, 4f, 3f, 4f)
                                    add(portraitLabel).center().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.16f, 0.24f, 0.28f, 0.58f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(healthLabel).center()
                                        }
                                    ).padTop(3f)
                                }
                            ).expand().fill().padTop(3f)
                        }
                    ).size(62f, 62f).top().left().padRight(4f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.62f))
                                    pad(1f, 4f, 1f, 4f)
                                    add(selectionMetaLabel).left().expandX().fillX()
                                }
                            ).expandX().fillX().row()
                            add(Table().apply { background = assets.panelDrawable(Color(0.10f, 0.15f, 0.19f, 0.58f)) }).height(1f).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.16f, 0.24f, 0.28f, 0.30f))
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
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.62f))
                                    pad(1f, 5f, 1f, 5f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.68f)) }).width(1f).expandY().fillY().padRight(4f)
                                    add(Label("STATUS", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.06f, 0.10f, 0.14f, 0.38f))
                                    pad(2f, 3f, 2f, 3f)
                                    add(centerStatusStrip).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(currentQueueCardTone())
                                    pad(2f, 3f, 2f, 3f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(currentQueueHeaderBackgroundTone())
                                            pad(1f, 4f, 1f, 4f)
                                            add(queueHeaderLabel).left()
                                        }
                                    ).left().padRight(4f)
                                    add(queueStatusStrip).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.62f))
                                    pad(1f, 4f, 1f, 4f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.90f, 0.52f, 0.64f)) }).width(1f).expandY().fillY().padRight(4f)
                                    add(Label("ROSTER", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(selectionGrid).left().expandX().fillX().padTop(2f).row()
                            add(
                                selectionPager.apply {
                                    clearChildren()
                                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftSelectionPage(-1) }).width(18f).height(18f).padRight(2f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.68f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(selectionPageLabel).left()
                                        }
                                    ).width(54f).left().padRight(2f)
                                    add(controlGroupButtons).minWidth(56f).right().padRight(2f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.68f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(controlGroupsLabel).right()
                                        }
                                    ).width(66f).right().padRight(2f)
                                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftSelectionPage(1) }).width(18f).height(18f)
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
        root.add(wrapTopStrip(topBar)).expandX().fillX().pad(6f, 18f, 0f, 18f).row()
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
                    background = assets.panelDrawable(Color(0.22f, 0.05f, 0.05f, 0.56f))
                    pad(1f)
                    add(
                        attackWarningCard.apply {
                            background = assets.panelDrawable(Color(0.66f, 0.18f, 0.14f, 0.76f))
                            pad(6f, 14f, 6f, 14f)
                            add(Table().apply { background = assets.panelDrawable(Color(1.00f, 0.84f, 0.70f, 0.72f)) }).width(2f).expandY().fillY().padRight(6f)
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
            background = assets.panelDrawable(Color(0.03f, 0.04f, 0.06f, 0.62f))
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.05f, 0.09f, 0.13f, 0.76f))
                    pad(10f)
                    defaults().pad(4f)
                    add(
                        pauseHeaderCard.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.28f, 0.34f, 0.72f))
                            pad(3f, 8f, 3f, 8f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.72f)) }).width(2f).expandY().fillY().padRight(5f)
                            add(Label("PAUSED", assets.titleLabelStyle)).left()
                        }
                    ).width(248f).left().row()
                    add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.58f)) }).height(1f).width(248f).padBottom(6f).row()
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
            background = assets.panelDrawable(Color(0.03f, 0.05f, 0.08f, 0.56f))
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.05f, 0.09f, 0.13f, 0.74f))
                    pad(8f, 10f, 8f, 10f)
                    add(
                        helpHeaderCard.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.28f, 0.34f, 0.72f))
                            pad(3f, 8f, 3f, 8f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.72f)) }).width(2f).expandY().fillY().padRight(5f)
                            add(Label("HELP", assets.titleLabelStyle)).left()
                        }
                    ).left().row()
                    add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.58f)) }).height(1f).expandX().fillX().padTop(5f).padBottom(6f).row()
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
        val commandButtonHeight = if (width >= 1440) 21f else 19f
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
        queueStatusLabel.setWrap(true)
        selectionRosterLabel.setWrap(true)
        hudLinesLabel.setWrap(true)
        footerLabel.setWrap(true)
        centerFooterLabel.setWrap(true)
        minimapTitle.setText("Tac Map  ${runtime.session.state.viewedFaction?.let { "F$it" } ?: "Obs"}")
        minimapTitle.color = currentMinimapTitleTone()
        minimapHint.color = currentMinimapHintTone()
        selectionLabel.setWidth(centerWidth)
        selectionMetaLabel.setWidth(centerWidth)
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
        buttonTable.defaults().pad(0f, 0f, 3f, 3f)
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
        commandHintLabel.setText(buildCommandHintLine())
        commandHintLabel.color = currentCommandHintTextTone()
        commandHintCard.background = assets.panelDrawable(currentCommandHintCardTone())
        val selectionPulse = selectionHudPulse()
        topSelectionShell.background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f).lerp(Color(0.18f, 0.24f, 0.18f, 0.68f), selectionPulse * 0.20f))
        topSelectionCard.background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.72f).lerp(Color(0.26f, 0.34f, 0.22f, 0.82f), selectionPulse * 0.28f))
        val topModePulse = topModePulse()
        topModeShell.background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f).lerp(Color(0.16f, 0.22f, 0.18f, 0.68f), topModePulse * 0.20f))
        topModeCard.background = assets.panelDrawable(Color(0.10f, 0.18f, 0.16f, 0.72f).lerp(Color(0.24f, 0.34f, 0.22f, 0.82f), topModePulse * 0.28f))
        val topStatusPulse = topStatusPulse()
        topStatusShell.background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.60f).lerp(Color(0.24f, 0.18f, 0.14f, 0.70f), topStatusPulse * 0.22f))
        topStatusCard.background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.74f).lerp(Color(0.38f, 0.26f, 0.18f, 0.84f), topStatusPulse * 0.30f))
        selectionHeadlineCard.background = assets.panelDrawable(currentSelectionHeadlineCardTone().cpy().lerp(Color(0.74f, 1.00f, 0.82f, 0.86f), selectionPulse * 0.34f))
        portraitFrame.background = assets.panelDrawable(Color(0.16f, 0.20f, 0.18f, 0.80f).lerp(Color(0.28f, 0.36f, 0.22f, 0.86f), selectionPulse * 0.28f))
        attackWarningLabel.setText(buildAttackWarningText())
        attackWarningCard.background = assets.panelDrawable(currentAttackWarningCardTone())
        attackWarningTable.isVisible = runtime.attackWarningLine() != null
        centerFooterLabel.setText(buildCenterFooterLine())
        syncSelectionPage(snapshot)
        updateSelectionPager(snapshot)
        pauseOverlay.isVisible = runtime.pauseOverlayVisible
        helpOverlay.isVisible = runtime.helpOverlayVisible
        pauseHeaderCard.background = assets.panelDrawable(currentPauseHeaderTone())
        helpHeaderCard.background = assets.panelDrawable(currentHelpHeaderTone())
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
                        val activePulse = uiPulse()
                        val actor = makeButton(
                            commandButtonLabel(button),
                            runtime.actionHint(button.actionId),
                            commandButtonStyle(button.actionId)
                        ) {
                            runActionWithPulse(button.actionId)
                        }
                        actor.isDisabled = !runtime.isActionEnabled(button.actionId)
                        actor.isChecked = runtime.isActionActive(button.actionId)
                        actor.color =
                            when {
                                actor.isDisabled -> Color(0.66f, 0.70f, 0.74f, 0.55f)
                                actor.isChecked -> Color(1.00f, 0.98f, 0.86f, 0.94f)
                                else -> Color.WHITE
                            }
                        val cardTone =
                            when {
                                button.actionId.startsWith("build") ->
                                    Color(0.20f, 0.24f, 0.32f, 0.98f)
                                button.actionId.startsWith("train") || button.actionId.startsWith("research") ->
                                    Color(0.22f, 0.20f, 0.10f, 0.98f)
                                button.actionId == "move" || button.actionId == "hold" ->
                                    Color(0.10f, 0.24f, 0.16f, 0.98f)
                                button.actionId == "patrol" ->
                                    Color(0.10f, 0.20f, 0.30f, 0.98f)
                                button.actionId == "attackMove" ->
                                    Color(0.36f, 0.14f, 0.10f, 0.98f)
                                else -> Color(0.16f, 0.18f, 0.22f, 0.98f)
                            }
                        val clickPulse = commandClickPulse(button.actionId)
                        val frameTone =
                            when {
                                actor.isDisabled -> Color(0.05f, 0.06f, 0.08f, 0.94f)
                                actor.isChecked -> Color(0.28f + (activePulse * 0.06f), 0.32f + (activePulse * 0.05f), 0.14f, 0.90f)
                                else -> cardTone.cpy().lerp(Color(0.34f, 0.44f, 0.30f, 0.96f), clickPulse * 0.26f)
                            }
                        val shellTone =
                            when {
                                actor.isDisabled -> Color(0.01f, 0.03f, 0.05f, 0.88f)
                                actor.isChecked -> Color(0.30f + (activePulse * 0.04f), 0.28f + (activePulse * 0.03f), 0.10f, 0.86f)
                                else -> Color(0.01f, 0.03f, 0.05f, 0.90f).lerp(Color(0.12f, 0.20f, 0.14f, 0.92f), clickPulse * 0.22f)
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
                                                            actor.isDisabled -> Color(1f, 1f, 1f, 0.06f)
                                                            actor.isChecked -> Color(1.00f, 0.92f, 0.46f, 0.72f)
                                                            button.actionId == "attackMove" -> pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.84f)
                                                            button.actionId.startsWith("build:") -> pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.82f)
                                                            button.actionId.startsWith("train:") || button.actionId.startsWith("research:") -> Color(0.72f, 0.84f, 1.00f, 0.82f)
                                                            button.actionId == "move" || button.actionId == "hold" -> pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.80f)
                                                            else -> Color(0.56f, 0.88f, 0.96f, 0.74f)
                                                        }
                                                            .lerp(Color(0.78f, 1.00f, 0.86f, 0.88f), clickPulse * 0.18f)
                                                    )
                                            }
                                        ).height(1.5f).expandX().fillX().colspan(4).padBottom(1f).row()
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                            when {
                                                                actor.isDisabled -> Color(0.16f, 0.18f, 0.20f, 0.28f)
                                                            actor.isChecked -> Color(1.00f, 0.92f, 0.46f, 0.62f + (activePulse * 0.06f))
                                                            button.actionId == "attackMove" -> pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.76f)
                                                            button.actionId.startsWith("build:") -> pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.74f)
                                                            button.actionId.startsWith("train:") || button.actionId.startsWith("research:") -> Color(0.64f, 0.78f, 1.00f, 0.72f)
                                                            button.actionId == "move" || button.actionId == "hold" -> pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.70f)
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
                                        add(actor).width(commandActorWidth).height(commandButtonHeight).left().expandX().fillX()
                                        add(
                                            Table().apply {
                                                background =
                                                    assets.panelDrawable(
                                                        when {
                                                            actor.isDisabled -> Color(1f, 1f, 1f, 0.08f)
                                                            actor.isChecked -> Color(1.00f, 0.94f, 0.60f, 0.74f)
                                                            else -> Color(1f, 1f, 1f, 0.12f)
                                                        }
                                                    )
                                            }
                                        ).size(4f, 4f).padLeft(3f)
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
            picked.add(0, "warning: no vision, press 1/2/3")
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
        val snapshot = runtime.snapshot ?: return "Awaiting"
        return if (runtime.session.state.selectedIds.isEmpty()) {
            val faction = runtime.session.state.viewedFaction?.let { "f$it" } ?: "observer"
            "$faction · ${snapshot.entities.size} up"
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
        val snapshot = runtime.snapshot ?: return "No data"
        if (snapshot.factions.isEmpty()) return "No factions"
        return snapshot.factions.joinToString("\n") { faction ->
            val viewed = if (runtime.session.state.viewedFaction == faction.faction) " <" else ""
            "F${faction.faction}  M${faction.minerals}  G${faction.gas}  V${faction.visibleTiles}$viewed"
        }
    }

    private fun wrapHudPanel(content: Table, tone: Color): Table =
        Table().apply {
            background = null
            add(Table().apply { background = assets.panelDrawable(tone.cpy().apply { a *= 0.76f }) }).height(1f).expandX().fillX().row()
            add(
                Table().apply {
                    add(Table().apply { background = assets.panelDrawable(tone.cpy().lerp(Color.WHITE, 0.08f).apply { a *= 0.72f }) }).width(1f).expandY().fillY()
                    add(
                        Table().apply {
                            background = null
                            pad(3f, 5f, 5f, 5f)
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
            add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.54f)) }).height(1f).expandX().fillX().padTop(2f)
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
            background = assets.panelDrawable(Color(0.01f, 0.03f, 0.05f, 0.68f))
            pad(1f, 1f, 2f, 1f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.22f))
                    pad(1f, 1f, 2f, 1f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.66f))
                                }
                            ).size(12f, 3f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.78f))
                                }
                            ).width(6f).height(2f).left().padBottom(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.18f, 0.24f, 0.28f, 0.78f))
                                }
                            ).width(3f).height(6f).left().padBottom(1f).row()
                            add(content).expand().fill().row()
                            add(
                                Table().apply {
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.08f, 0.12f, 0.15f, 0.78f))
                                        }
                                    ).width(20f).height(2f).right().row()
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.20f, 0.28f, 0.32f, 0.78f))
                                        }
                                    ).width(8f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.15f, 0.18f, 0.78f))
                                        }
                                    ).width(10f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.24f, 0.30f, 0.34f, 0.78f))
                                        }
                                    ).width(4f).height(4f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.12f, 0.18f, 0.21f, 0.78f))
                                        }
                                    ).width(9f).height(2f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.18f, 0.24f, 0.28f, 0.78f))
                                        }
                                    ).width(3f).height(5f).right().padTop(1f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.08f, 0.12f, 0.15f, 0.78f))
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
            centerStatusStrip.add(buildStatusChip("RDY", Color(0.20f, 0.40f, 0.46f, 0.90f), Color(0.62f, 0.88f, 0.96f, 0.92f)))
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
        return buildList {
            lead.activeOrder?.takeIf { it.isNotBlank() }?.let {
                add(StatusChip("O ${it.lowercase().take(4)}", Color(0.12f, 0.24f, 0.30f, 0.90f), Color(0.62f, 0.88f, 0.96f, 0.88f)))
            }
            if (lead.orderQueueSize > 0) add(StatusChip("Q${lead.orderQueueSize}", Color(0.16f, 0.21f, 0.11f, 0.90f), Color(0.92f, 0.88f, 0.48f, 0.90f)))
            if (lead.pathRemainingNodes > 0) add(StatusChip("P${lead.pathRemainingNodes}", Color(0.12f, 0.24f, 0.30f, 0.90f), Color(0.62f, 0.88f, 0.96f, 0.88f)))
            lead.activeProductionType?.let {
                add(StatusChip("P ${it.take(4)}", Color(0.20f, 0.26f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
            }
            lead.activeResearchTech?.let {
                add(StatusChip("R ${it.take(4)}", Color(0.16f, 0.18f, 0.30f, 0.92f), Color(0.78f, 0.84f, 1.00f, 0.92f)))
            }
            if (lead.underConstruction) add(StatusChip("BLD", Color(0.20f, 0.24f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
            lead.harvestPhase?.let {
                add(StatusChip("H ${it.lowercase().take(3)}", Color(0.10f, 0.26f, 0.20f, 0.92f), Color(0.58f, 0.92f, 0.72f, 0.90f)))
            }
            if (lead.harvestCargoAmount != null && lead.harvestCargoAmount > 0) {
                add(
                    StatusChip(
                        "C ${(lead.harvestCargoKind ?: "res").take(1).uppercase()}${lead.harvestCargoAmount}",
                        Color(0.10f, 0.26f, 0.20f, 0.92f),
                        Color(0.58f, 0.92f, 0.72f, 0.90f)
                    )
                )
            }
        }.take(6)
    }

    private fun buildStatusChip(text: String, backgroundTone: Color, accentTone: Color): Table =
        Table().apply {
            background = assets.panelDrawable(backgroundTone)
            pad(1f, 2f, 1f, 2f)
            add(Table().apply { background = assets.panelDrawable(accentTone) }).width(2f).height(8f).padRight(3f)
            add(Label(text.uppercase(), assets.mutedLabelStyle).apply { color = Color.WHITE }).left()
        }

    private fun buildCenterFooterLine(): String =
        when {
            runtime.buildModeTypeId != null -> "LMB/RMB place  Esc cancel"
            runtime.groundMode != null -> "LMB/RMB confirm  Esc cancel"
            runtime.session.state.selectedIds.isNotEmpty() -> "Home center  Esc clear  Shift add"
            else -> "Drag select  RMB order  MMB pan"
        }

    private fun rebuildQueueStatusStrip() {
        queueStatusStrip.clearChildren()
        queueStatusStrip.defaults().left().pad(0f, 0f, 0f, 3f)
        val bits = buildQueueStatusBits()
        if (bits.isEmpty()) {
            queueStatusStrip.add(buildStatusChip("IDLE", Color(0.10f, 0.14f, 0.18f, 0.86f), Color(0.50f, 0.58f, 0.66f, 0.86f)))
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
                add(
                    StatusChip(
                        "P ${(lead.activeProductionType ?: "Q").take(4)}",
                        Color(0.20f, 0.26f, 0.12f, 0.92f),
                        Color(0.98f, 0.84f, 0.46f, 0.90f)
                    )
                )
                if (lead.productionQueueSize > 0) {
                    add(StatusChip("x${lead.productionQueueSize}", Color(0.20f, 0.26f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
                }
                if (lead.activeProductionRemainingTicks > 0) {
                    add(StatusChip("${lead.activeProductionRemainingTicks}t", Color(0.20f, 0.26f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
                }
            }
            if (lead.researchQueueSize > 0 || lead.activeResearchTech != null) {
                add(
                    StatusChip(
                        "R ${(lead.activeResearchTech ?: "Q").take(4)}",
                        Color(0.16f, 0.18f, 0.30f, 0.92f),
                        Color(0.78f, 0.84f, 1.00f, 0.92f)
                    )
                )
                if (lead.researchQueueSize > 0) {
                    add(StatusChip("x${lead.researchQueueSize}", Color(0.16f, 0.18f, 0.30f, 0.92f), Color(0.78f, 0.84f, 1.00f, 0.92f)))
                }
                if (lead.activeResearchRemainingTicks > 0) {
                    add(StatusChip("${lead.activeResearchRemainingTicks}t", Color(0.16f, 0.18f, 0.30f, 0.92f), Color(0.78f, 0.84f, 1.00f, 0.92f)))
                }
            }
            if (lead.underConstruction) {
                add(StatusChip("BLD", Color(0.20f, 0.24f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
                lead.constructionRemainingTicks?.takeIf { it > 0 }?.let {
                    add(StatusChip("${it}t", Color(0.20f, 0.24f, 0.12f, 0.92f), Color(0.98f, 0.84f, 0.46f, 0.90f)))
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
                        Color(0.12f, 0.20f, 0.10f, 0.96f)
                    takingDamage -> Color(0.20f, 0.08f, 0.08f, 0.96f)
                    else -> Color(0.12f, 0.14f, 0.16f, 1f)
                }
            )
        healthBarFill.background =
            assets.panelDrawable(
                when {
                    ratio >= 0.66f ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> Color(0.68f, 1.00f, 0.60f, 1f)
                            takingDamage -> Color(0.44f, 0.92f, 0.56f, 1f)
                            else -> Color(0.22f, 0.78f, 0.42f, 1f)
                        }
                    ratio >= 0.33f ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> Color(0.92f, 1.00f, 0.42f, 1f)
                            takingDamage -> Color(1.00f, 0.82f, 0.34f, 1f)
                            else -> Color(0.87f, 0.73f, 0.20f, 1f)
                        }
                    else ->
                        when {
                            impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE -> Color(1.00f, 0.58f, 0.34f, 1f)
                            takingDamage -> Color(1.00f, 0.42f, 0.34f, 1f)
                            else -> Color(0.84f, 0.30f, 0.25f, 1f)
                        }
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
            selectionGrid.add(buildSelectionSlot(entity)).size(40f, 40f)
            if ((index + 1) % 4 == 0) {
                selectionGrid.row()
            }
        }
    }

    private fun buildSelectionSlot(entity: EntitySnapshot): Table {
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        val focused = focusedSelectionId == entity.id || (focusedSelectionId == null && runtime.session.state.selectedIds.firstOrNull() == entity.id)
        val damaged = runtime.isDamageFlashActive(entity.id)
        val impactKind = runtime.damageImpactKind(entity.id)
        val focusPulse = if (focused) uiPulse(900L) else 0f
        val clickPulse = slotClickPulse(entity.id)
        val isWorker = (entity.typeId ?: "").contains("worker", ignoreCase = true)
        val tone =
            when {
                entity.weaponId != null -> Color(0.17f, 0.31f, 0.39f, 0.96f)
                entity.footprintWidth != null -> Color(0.28f, 0.24f, 0.15f, 0.96f)
                else -> Color(0.16f, 0.25f, 0.18f, 0.96f)
            }
        val badgeTone =
            when {
                entity.footprintWidth != null -> Color(0.44f, 0.34f, 0.16f, 0.96f)
                isWorker -> Color(0.20f, 0.44f, 0.28f, 0.96f)
                entity.weaponId != null -> Color(0.18f, 0.46f, 0.48f, 0.96f)
                else -> Color(0.28f, 0.34f, 0.40f, 0.96f)
            }
        val hpColor =
            when {
                impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE ->
                    when {
                        hpRatio >= 0.66f -> Color(0.68f, 1.00f, 0.60f, 1f)
                        hpRatio >= 0.33f -> Color(0.92f, 1.00f, 0.42f, 1f)
                        else -> Color(1.00f, 0.58f, 0.34f, 1f)
                    }
                hpRatio >= 0.66f -> Color(0.22f, 0.78f, 0.42f, 1f)
                hpRatio >= 0.33f -> Color(0.87f, 0.73f, 0.20f, 1f)
                else -> Color(0.84f, 0.30f, 0.25f, 1f)
            }
        val shortName = buildSelectionSlotCode(entity)
        return Table().apply {
            background =
                assets.panelDrawable(
                    if (focused) selectionFocusShellTone(focusPulse)
                    else if (clickPulse > 0f) Color(0.14f, 0.20f, 0.16f, 0.94f).lerp(Color(0.40f, 0.56f, 0.32f, 0.96f), clickPulse * 0.34f)
                    else if (damaged) Color(0.22f, 0.10f, 0.10f, 0.94f)
                    else Color(0.08f, 0.12f, 0.16f, 0.92f)
                )
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            pad(1f)
            add(
                Table().apply {
                    background =
                        assets.panelDrawable(
                            if (focused) selectionFocusCardTone(focusPulse)
                            else if (clickPulse > 0f) tone.cpy().lerp(Color(0.40f, 0.54f, 0.30f, 0.96f), clickPulse * 0.28f)
                            else if (damaged) tone.cpy().lerp(Color.SCARLET, 0.28f)
                            else tone
                        )
                    pad(if (focused) 2f else 1.5f)
                    add(
                        Table().apply {
                            background =
                                assets.panelDrawable(
                                    if (focused) selectionFocusBaseTone().cpy().apply { a = 0.62f + (focusPulse * 0.18f) }
                                    else if (damaged) Color(1.00f, 0.74f, 0.64f, 0.28f)
                                    else Color(1f, 1f, 1f, 0.08f)
                                )
                        }
                    ).height(2f).expandX().fillX().padBottom(2f).row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(1f, 1f, 1f, if (focused) 0.08f else 0.04f))
                            pad(2f, 1f, 0.5f, 1f)
                            add(Label(shortName, assets.titleLabelStyle)).center().expandX().fillX()
                        }
                    ).expandX().fillX().height(18f).row()
                    add(buildSelectionSlotGlyph(entity, badgeTone, focused, damaged)).center().padTop(1f).row()
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background =
                                        assets.panelDrawable(
                                            if (focused) selectionFocusBaseTone().cpy().lerp(Color.WHITE, 0.12f)
                                            else Color(1f, 1f, 1f, 0.08f)
                                        )
                                }
                            ).size(7f, 1.5f)
                            add().expandX().fillX()
                            add(
                                Table().apply {
                                background =
                                    assets.panelDrawable(
                                        if (impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE) Color(0.82f, 1.00f, 0.58f, 0.96f)
                                        else if (damaged) Color(0.92f, 0.34f, 0.28f, 0.96f)
                                        else Color(1f, 1f, 1f, 0.06f)
                                    )
                                }
                            ).size(5f, 5f)
                        }
                    ).expandX().fillX().padTop(1f).row()
                    add(
                        Table().apply {
                            background =
                                assets.panelDrawable(
                                    if (focused) Color(0.16f, 0.20f, 0.08f, 1f)
                                    else if (damaged) Color(0.18f, 0.08f, 0.08f, 1f)
                                    else Color(0.09f, 0.11f, 0.13f, 1f)
                                )
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(if (damaged) hpColor.cpy().lerp(Color.WHITE, 0.24f) else hpColor)
                                }
                            ).width(24f * hpRatio.coerceIn(0f, 1f)).height(4f).left()
                            add().expandX().fillX()
                        }
                    ).width(24f).height(4f).padTop(2f)
                }
            ).expand().fill()
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        markSlotPulse(entity.id)
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

    private fun buildSelectionSlotCode(entity: EntitySnapshot): String {
        val type = (entity.typeId ?: "?").uppercase()
        return when {
            type.length <= 2 -> type
            "_" in type -> type.split("_").mapNotNull { it.firstOrNull()?.toString() }.joinToString("").take(2)
            else -> type.take(2)
        }
    }

    private fun buildSelectionSlotGlyph(entity: EntitySnapshot, badgeTone: Color, focused: Boolean, damaged: Boolean): Table {
        val isWorker = (entity.typeId ?: "").contains("worker", ignoreCase = true)
        val shellTone =
            if (focused) badgeTone.cpy().lerp(Color.WHITE, 0.22f)
            else if (damaged) badgeTone.cpy().lerp(Color.SCARLET, 0.25f)
            else badgeTone
        return Table().apply {
            pad(0f)
            when {
                entity.footprintWidth != null -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(12f, 2f).colspan(3).row()
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
                            ).size(3f, 3f).pad(0.5f)
                        }
                        row()
                    }
                }

                isWorker -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(8f, 2f).colspan(3).row()
                    add().size(2f, 2f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.12f))
                        }
                    ).size(4f, 5f).pad(0.5f)
                    add().size(2f, 2f).row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f, 2f).padRight(0.5f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f, 2f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f, 2f).padLeft(0.5f)
                }

                entity.weaponId != null -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(3f, 6f).padRight(0.5f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.14f))
                        }
                    ).size(6f, 2f).padTop(2f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(2f, 2f).padLeft(0.5f)
                }

                else -> {
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(4f, 4f).padRight(0.5f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone.cpy().lerp(Color.WHITE, 0.12f))
                        }
                    ).size(4f, 6f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(shellTone)
                        }
                    ).size(4f, 4f).padLeft(0.5f)
                }
            }
        }
    }

    private fun syncSelectionPage(snapshot: ClientSnapshot?) {
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
        if (remaining <= 0L) return 0f
        val normalized = remaining.toFloat() / HUD_SELECTION_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
    }

    private fun topStatusPulse(): Float {
        val remaining = topStatusPulseUntilMillis - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        val normalized = remaining.toFloat() / HUD_SELECTION_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
    }

    private fun topModePulse(): Float {
        val remaining = topModePulseUntilMillis - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        val normalized = remaining.toFloat() / HUD_SELECTION_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
    }

    private fun markCommandPulse(actionId: String) {
        commandPulseUntilMillis[actionId] = System.currentTimeMillis() + COMMAND_CLICK_PULSE_DURATION_MS
    }

    private fun runActionWithPulse(actionId: String) {
        markCommandPulse(actionId)
        runtime.executeAction(actionId, Gdx.graphics.width, computeWorldViewportHeight(Gdx.graphics.height))
    }

    private fun commandClickPulse(actionId: String): Float {
        val until = commandPulseUntilMillis[actionId] ?: return 0f
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            commandPulseUntilMillis.remove(actionId)
            return 0f
        }
        val normalized = remaining.toFloat() / COMMAND_CLICK_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
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
        val normalized = remaining.toFloat() / SLOT_CLICK_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
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
                    background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.90f))
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
        runtime.noticeLine()?.removePrefix("notice: ")?.let(::compactNotice)?.let { return it }
        val selectionCount = runtime.session.state.selectedIds.size
        return when {
            runtime.buildModeTypeId != null -> "Place ${runtime.buildModeTypeId}"
            runtime.groundMode != null -> "${runtime.overlayModeLabel().uppercase()} ready"
            selectionCount > 0 -> ""
            else -> ""
        }
    }

    private fun buildAttackWarningText(): String =
        when {
            runtime.isStructureLossWarning() -> "STRUCTURE LOST"
            runtime.attackWarningLine() != null -> "UNDER ATTACK"
            else -> ""
        }

    private fun buildCommandHintLine(): String =
        runtime.hoverHintLine()?.let(::compactHint)
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
            runtime.noticeKind() == NoticeKind.TRADE -> Color(0.20f, 0.18f, 0.12f, 0.80f)
            runtime.noticeKind() == NoticeKind.LOSS -> Color(0.26f, 0.14f, 0.12f, 0.80f)
            runtime.noticeKind() == NoticeKind.KILL -> Color(0.12f, 0.20f, 0.12f, 0.80f)
            runtime.noticeLine() != null -> Color(0.18f, 0.16f, 0.10f, 0.76f)
            else -> Color(0.08f, 0.14f, 0.18f, 0.62f)
        }

    private fun currentActionBannerTextTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.98f, 0.90f, 0.62f, 0.96f)
            runtime.groundMode != null -> Color(0.72f, 0.96f, 0.82f, 0.96f)
            runtime.noticeKind() == NoticeKind.TRADE -> Color(1.00f, 0.92f, 0.68f, 0.98f)
            runtime.noticeKind() == NoticeKind.LOSS -> Color(1.00f, 0.78f, 0.72f, 0.98f)
            runtime.noticeKind() == NoticeKind.KILL -> Color(0.84f, 1.00f, 0.78f, 0.98f)
            runtime.noticeLine() != null -> Color(1.00f, 0.88f, 0.58f, 0.96f)
            else -> Color(0.86f, 0.94f, 0.98f, 0.94f)
        }

    private fun currentTopEconomyTone(): Color =
        when {
            runtime.attackWarningLine() != null -> Color(1.00f, 0.84f, 0.72f, 0.96f)
            runtime.noticeLine() != null -> Color(1.00f, 0.92f, 0.68f, 0.96f)
            else -> Color(0.86f, 0.94f, 0.98f, 0.94f)
        }

    private fun currentTopSelectionTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.72f, 0.78f, 0.82f, 0.92f)
            runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                Color(0.98f, 0.92f, 0.62f, 0.96f)
            else -> Color(0.78f, 0.94f, 0.98f, 0.96f)
        }

    private fun currentTopModeTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.98f, 0.90f, 0.58f, 0.96f)
            runtime.groundMode != null -> Color(0.72f, 0.96f, 0.84f, 0.96f)
            runtime.pauseOverlayVisible || runtime.playControlState.paused -> Color(0.84f, 0.90f, 0.98f, 0.96f)
            else -> Color(0.72f, 0.96f, 0.84f, 0.94f)
        }

    private fun currentAttackWarningCardTone(): Color =
        when {
            runtime.isStructureLossWarning() -> Color(0.48f, 0.12f, 0.10f, 0.96f)
            runtime.attackWarningLine() != null -> Color(0.66f, 0.18f, 0.14f, 0.94f)
            runtime.noticeKind() == NoticeKind.TRADE -> Color(0.36f, 0.28f, 0.10f, 0.92f)
            runtime.noticeKind() == NoticeKind.LOSS -> Color(0.34f, 0.18f, 0.16f, 0.92f)
            runtime.noticeKind() == NoticeKind.KILL -> Color(0.16f, 0.28f, 0.18f, 0.92f)
            runtime.noticeLine() != null -> Color(0.38f, 0.28f, 0.10f, 0.92f)
            else -> Color(0.16f, 0.28f, 0.34f, 0.92f)
        }

    private fun currentPauseHeaderTone(): Color =
        when {
            runtime.attackWarningLine() != null -> Color(0.34f, 0.18f, 0.16f, 0.92f)
            runtime.noticeLine() != null -> Color(0.30f, 0.24f, 0.10f, 0.92f)
            else -> Color(0.16f, 0.28f, 0.34f, 0.92f)
        }

    private fun currentHelpHeaderTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.30f, 0.24f, 0.10f, 0.92f)
            runtime.groundMode != null -> Color(0.16f, 0.28f, 0.20f, 0.92f)
            else -> Color(0.16f, 0.28f, 0.34f, 0.92f)
        }

    private fun currentCommandHintCardTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.18f, 0.18f, 0.08f, 0.86f)
            runtime.groundMode != null -> Color(0.10f, 0.20f, 0.16f, 0.86f)
            runtime.session.state.selectedIds.isNotEmpty() -> Color(0.08f, 0.16f, 0.20f, 0.84f)
            else -> Color(0.12f, 0.16f, 0.18f, 0.82f)
        }

    private fun currentCommandHintTextTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.98f, 0.90f, 0.58f, 0.96f)
            runtime.groundMode != null -> Color(0.72f, 0.96f, 0.84f, 0.96f)
            runtime.session.state.selectedIds.isNotEmpty() -> Color(0.76f, 0.92f, 0.98f, 0.96f)
            else -> Color(0.72f, 0.78f, 0.82f, 0.94f)
        }

    private fun currentSelectionHeadlineCardTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.12f, 0.18f, 0.22f, 0.90f)
            runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                Color(0.18f, 0.24f, 0.12f, 0.92f)
            else -> Color(0.12f, 0.20f, 0.24f, 0.92f)
        }

    private fun currentSelectionHeadlineTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.76f, 0.84f, 0.88f, 0.94f)
            runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                Color(0.98f, 0.92f, 0.62f, 0.96f)
            else -> Color(0.74f, 0.94f, 0.98f, 0.96f)
        }

    private fun selectionFocusBaseTone(): Color = Color(0.98f, 0.92f, 0.56f, 0.96f)

    private fun selectionFocusShellTone(pulse: Float): Color =
        Color(0.26f + (pulse * 0.10f), 0.22f + (pulse * 0.06f), 0.08f, 0.96f)

    private fun selectionFocusCardTone(pulse: Float): Color =
        Color(0.38f + (pulse * 0.08f), 0.30f + (pulse * 0.06f), 0.08f, 0.98f)

    private fun currentSelectionMetaTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.66f, 0.72f, 0.76f, 0.92f)
            else -> Color(0.80f, 0.88f, 0.92f, 0.94f)
        }

    private fun currentRosterTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.64f, 0.70f, 0.74f, 0.90f)
            else -> Color(0.84f, 0.88f, 0.92f, 0.94f)
        }

    private fun currentCenterStatusTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.60f, 0.70f, 0.76f, 0.92f)
            buildCenterStatusLine() == "Ready" -> Color(0.62f, 0.88f, 0.96f, 0.92f)
            else -> Color(0.90f, 0.94f, 0.98f, 0.94f)
        }

    private fun currentQueueHeaderTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(1.00f, 0.92f, 0.62f, 0.96f)
            "RESEARCH" -> Color(0.78f, 0.96f, 1.00f, 0.96f)
            "CONSTRUCT" -> Color(0.74f, 1.00f, 0.82f, 0.96f)
            else -> Color(0.62f, 0.72f, 0.78f, 0.92f)
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
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(1.00f, 0.94f, 0.72f, 0.94f)
            "RESEARCH" -> Color(0.86f, 0.96f, 1.00f, 0.94f)
            "CONSTRUCT" -> Color(0.84f, 1.00f, 0.90f, 0.94f)
            else -> Color(0.66f, 0.74f, 0.80f, 0.88f)
        }

    private fun currentQueueCardTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(0.18f, 0.14f, 0.08f, 0.74f)
            "RESEARCH" -> Color(0.10f, 0.14f, 0.20f, 0.74f)
            "CONSTRUCT" -> Color(0.10f, 0.17f, 0.12f, 0.74f)
            else -> Color(0.08f, 0.12f, 0.16f, 0.62f)
        }

    private fun currentQueueHeaderBackgroundTone(): Color =
        when (buildQueueHeaderLine()) {
            "PRODUCTION" -> Color(0.40f, 0.30f, 0.10f, 0.84f)
            "RESEARCH" -> Color(0.20f, 0.30f, 0.42f, 0.84f)
            "CONSTRUCT" -> Color(0.20f, 0.34f, 0.22f, 0.84f)
            else -> Color(0.18f, 0.28f, 0.34f, 0.82f)
        }

    private fun currentStatusBadgeTone(): Color =
        when {
            runtime.attackWarningLine() != null -> Color(1.00f, 0.70f, 0.58f, 0.98f)
            runtime.noticeLine() != null -> Color(1.00f, 0.90f, 0.62f, 0.98f)
            runtime.playControlState.paused -> Color(0.82f, 0.88f, 0.96f, 0.96f)
            else -> Color(0.62f, 0.96f, 0.80f, 0.96f)
        }

    private fun currentMinimapTitleTone(): Color =
        when {
            runtime.attackWarningLine() != null -> Color(1.00f, 0.78f, 0.64f, 0.98f)
            runtime.session.state.viewedFaction == null -> Color(0.82f, 0.90f, 0.98f, 0.96f)
            else -> Color(0.72f, 0.96f, 0.84f, 0.96f)
        }

    private fun currentMinimapHintTone(): Color =
        when {
            runtime.groundMode != null -> Color(0.74f, 0.96f, 0.84f, 0.94f)
            runtime.buildModeTypeId != null -> Color(0.98f, 0.90f, 0.58f, 0.94f)
            else -> Color(0.66f, 0.74f, 0.80f, 0.90f)
        }

    private fun currentCommandHeaderTone(groups: List<Pair<String, List<ClientCommandButton>>>): Color =
        when {
            runtime.buildModeTypeId != null -> Color(0.98f, 0.90f, 0.58f, 0.96f)
            runtime.groundMode != null -> Color(0.74f, 0.96f, 0.84f, 0.96f)
            groups.any { it.first == "Production" && it.second.isNotEmpty() } -> Color(0.88f, 0.92f, 0.98f, 0.96f)
            else -> Color(0.78f, 0.86f, 0.92f, 0.94f)
        }

    private fun pingTone(kind: GroundPingKind): Color =
        when (kind) {
            GroundPingKind.MOVE -> Color(0.34f, 0.98f, 0.58f, 0.94f)
            GroundPingKind.ATTACK -> Color(1.00f, 0.58f, 0.30f, 0.96f)
            GroundPingKind.BUILD -> Color(0.64f, 0.84f, 1.00f, 0.96f)
            GroundPingKind.INVALID -> Color(0.96f, 0.30f, 0.30f, 0.96f)
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
                    if (runtime.dragCenterFromMinimap(screenX.toFloat(), screenY.toFloat(), Gdx.graphics.width, Gdx.graphics.height)) {
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
                Input.Keys.LEFT -> runtime.nudgePanBy(28f, 0f)
                Input.Keys.RIGHT -> runtime.nudgePanBy(-28f, 0f)
                Input.Keys.UP -> runtime.nudgePanBy(0f, 28f)
                Input.Keys.DOWN -> runtime.nudgePanBy(0f, -28f)
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
                Input.Keys.M -> runActionWithPulse("move")
                Input.Keys.A -> runActionWithPulse("attackMove")
                Input.Keys.P -> runActionWithPulse("patrol")
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
