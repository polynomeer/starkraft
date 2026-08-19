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
import com.badlogic.gdx.scenes.scene2d.Actor
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
import kotlin.math.roundToInt

internal class GameScreen(
    private val game: StarkraftGdxGame,
    private val assets: GdxUiAssets,
    private val runtime: GdxClientRuntime
) : ScreenAdapter() {
    private companion object {
        const val HUD_SELECTION_PULSE_DURATION_MS = 260L
        const val COMMAND_CLICK_PULSE_DURATION_MS = 180L
        const val SLOT_CLICK_PULSE_DURATION_MS = 180L
        const val IMPACT_FLASH_DECAY_PER_SECOND = 2.8f
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
    private val impactFlash = Table()
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
    private var lastCenterStatusStripSignature = ""
    private var lastQueueStatusStripSignature = ""
    private var lastHealthBarSignature = ""
    private var lastSelectionGridSignature = ""
    private var lastSelectionPagerSignature = ""
    private var lastControlGroupButtonsSignature = ""
    private var lastHudChromeSignature = ""
    private var lastHudLayoutSignature = ""
    private val commandPulseUntilMillis = HashMap<String, Long>()
    private val slotPulseUntilMillis = HashMap<Int, Long>()
    private var screenFadeAlpha = 1f
    private var impactFlashAlpha = 0f
    private val impactFlashTone = Color(1f, 1f, 1f, 0f)
    private var soundVariantTick = 0
    private val soundCooldownUntilMillis = HashMap<String, Long>()
    private lateinit var topSelectionCell: Cell<*>
    private lateinit var topModeCell: Cell<*>
    private lateinit var topStatusCell: Cell<*>
    private lateinit var leftHudSpacerCell: Cell<*>
    private lateinit var centerHudCell: Cell<*>
    private lateinit var commandHudCell: Cell<*>

    private data class SelectionFrameContext(
        val snapshot: ClientSnapshot,
        val selected: List<EntitySnapshot>,
        val lead: EntitySnapshot?
    )

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
        topSelectionLabel.setEllipsis(true)
        modeLabel.setEllipsis(true)
        statusBadgeLabel.setEllipsis(true)
        commandHintLabel.setEllipsis(true)
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
        val worldViewportHeight = computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height)
        runtime.tick()
        runtime.ensurePlayableView(Gdx.graphics.width, worldViewportHeight)
        runtime.ensureInitialCamera(Gdx.graphics.width, worldViewportHeight)
        applyEdgePan(worldViewportHeight.toFloat())
        runtime.constrainCamera(Gdx.graphics.width, worldViewportHeight)
        refreshHud()
        if (runtime.consumeAttackAlertSound()) {
            playSoundVariant(assets.alertSound, 0.7f, 0.98f, 1.04f)
            triggerImpactFlash(Color(1.00f, 0.42f, 0.22f, 1f), 0.13f)
        }
        when (runtime.consumeCommandSoundKind()) {
            CommandSoundKind.MOVE -> playSoundVariant("cmd-move", assets.moveSound, 0.48f, 0.98f, 1.05f, 30L)
            CommandSoundKind.ATTACK -> playSoundVariant("cmd-attack", assets.attackSound, 0.55f, 0.97f, 1.05f, 30L)
            CommandSoundKind.BUILD -> playSoundVariant("cmd-build", assets.buildSound, 0.52f, 0.98f, 1.04f, 45L)
            CommandSoundKind.INVALID -> playSoundVariant("cmd-invalid", assets.invalidSound, 0.48f, 0.94f, 1.00f, 45L)
            null -> Unit
        }
        when (runtime.consumeCombatSoundKind()) {
            CombatSoundKind.MARINE_RANGED -> {
                playSoundVariant(assets.marineCombatSound, 0.46f, 0.96f, 1.06f)
                triggerImpactFlash(Color(0.98f, 0.80f, 0.42f, 1f), 0.045f)
            }
            CombatSoundKind.ZERGLING_MELEE -> {
                playSoundVariant(assets.zerglingCombatSound, 0.46f, 0.93f, 1.03f)
                triggerImpactFlash(Color(0.62f, 1.00f, 0.54f, 1f), 0.050f)
            }
            CombatSoundKind.MELEE -> {
                playSoundVariant(assets.meleeCombatSound, 0.44f, 0.95f, 1.04f)
                triggerImpactFlash(Color(1.00f, 0.72f, 0.48f, 1f), 0.042f)
            }
            CombatSoundKind.RANGED -> {
                playSoundVariant(assets.rangedCombatSound, 0.42f, 0.97f, 1.05f)
                triggerImpactFlash(Color(0.86f, 0.92f, 1.00f, 1f), 0.036f)
            }
            null -> Unit
        }
        when (runtime.consumeDeathSoundKind()) {
            DeathSoundKind.UNIT -> {
                playSoundVariant("death-unit", assets.deathSound, 0.56f, 0.94f, 1.03f, 55L)
                triggerImpactFlash(Color(1.00f, 0.52f, 0.42f, 1f), 0.070f)
            }
            DeathSoundKind.MARINE -> {
                playSoundVariant("death-marine", assets.marineDeathSound, 0.58f, 0.95f, 1.04f, 55L)
                triggerImpactFlash(Color(1.00f, 0.56f, 0.46f, 1f), 0.076f)
            }
            DeathSoundKind.ZERGLING -> {
                playSoundVariant("death-zergling", assets.zerglingDeathSound, 0.56f, 0.92f, 1.01f, 45L)
                triggerImpactFlash(Color(0.68f, 1.00f, 0.56f, 1f), 0.076f)
            }
            DeathSoundKind.STRUCTURE -> {
                playSoundVariant("death-structure-main", assets.structureDeathSound, 0.60f, 0.92f, 1.00f, 80L)
                playSoundVariant("death-structure-tail", assets.structureDeathTailSound, 0.38f, 0.88f, 0.96f, 120L)
                triggerImpactFlash(Color(1.00f, 0.66f, 0.34f, 1f), 0.110f)
            }
            null -> Unit
        }
        if (runtime.consumeCompletionAlertSound()) {
            playSoundVariant(assets.completeSound, 0.55f, 0.98f, 1.04f)
            triggerImpactFlash(Color(0.60f, 0.98f, 0.76f, 1f), 0.060f)
        }
        worldRenderer.render(runtime, Gdx.graphics.width, Gdx.graphics.height, worldViewportHeight, dragSelection)
        updateImpactFlash(delta)
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
        val initialTopBarLayout = computeTopBarLayout(1280)
        val root =
            Table().apply {
                setFillParent(true)
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
            }

        topBar.apply {
            background = null
            pad(0f, 2f, 0f, 2f)
            add(
                Table().apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.60f))
                    pad(1f)
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.74f))
                            pad(1f, 2f, 1f, 2f)
                            add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.78f)) }).width(2f).expandY().fillY().padRight(3f)
                            add(economyLabel).left()
                        }
                    ).left().expandX().fillX()
                }
            ).left().expandX().fillX()
            topSelectionCell = add(
                topSelectionShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f))
                    pad(1f)
                    add(
                        topSelectionCard.apply {
                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.72f))
                            pad(1f, 2f, 1f, 2f)
                            add(topSelectionLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(initialTopBarLayout.selectionWidth.toFloat()).center().padLeft(2f).padRight(2f)
            topModeCell = add(
                topModeShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.58f))
                    pad(1f)
                    add(
                        topModeCard.apply {
                            background = assets.panelDrawable(Color(0.10f, 0.18f, 0.16f, 0.72f))
                            pad(1f, 2f, 1f, 2f)
                            add(modeLabel).center()
                        }
                    ).expandX().fillX()
                }
            ).width(initialTopBarLayout.modeWidth.toFloat()).center().padRight(2f)
            topStatusCell = add(
                topStatusShell.apply {
                    background = assets.panelDrawable(Color(0.08f, 0.13f, 0.17f, 0.60f))
                    pad(1f)
                    add(
                        topStatusCard.apply {
                            background = assets.panelDrawable(Color(0.16f, 0.23f, 0.29f, 0.74f))
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
            val initialCenterLayout = computeCenterPanelLayout(1280)
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
                    ).size(initialCenterLayout.portraitSize.toFloat(), initialCenterLayout.portraitSize.toFloat()).top().left().padRight(4f)
                    add(
                        Table().apply {
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.62f))
                                    pad(1f, 3f, 1f, 3f)
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
                            ).width(initialCenterLayout.healthBarWidth.toFloat()).height(7f).left().padTop(1f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.62f))
                                    pad(1f, 4f, 1f, 4f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.68f)) }).width(1f).expandY().fillY().padRight(4f)
                                    add(Label("STATUS", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(Color(0.06f, 0.10f, 0.14f, 0.38f))
                                    pad(1f, 3f, 1f, 3f)
                                    add(centerStatusStrip).left().expandX().fillX()
                                }
                            ).expandX().fillX().padTop(2f).row()
                            add(
                                Table().apply {
                                    background = assets.panelDrawable(currentQueueCardTone())
                                    pad(1f, 3f, 1f, 3f)
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
                                    pad(1f, 3f, 1f, 3f)
                                    add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.90f, 0.52f, 0.64f)) }).width(1f).expandY().fillY().padRight(4f)
                                    add(Label("ROSTER", assets.mutedLabelStyle)).left()
                                }
                            ).left().expandX().fillX().padTop(2f).row()
                            add(selectionGrid).left().expandX().fillX().padTop(2f).row()
                            add(
                                selectionPager.apply {
                                    clearChildren()
                                    add(makeButton("<", style = assets.subtleButtonStyle()) { shiftSelectionPage(-1) }).width(initialCenterLayout.pagerButtonSize.toFloat()).height(initialCenterLayout.pagerButtonSize.toFloat()).padRight(2f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.68f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(selectionPageLabel).left()
                                        }
                                    ).width(initialCenterLayout.pagerLabelWidth.toFloat()).left().padRight(2f)
                                    add(controlGroupButtons).minWidth(56f).right().padRight(2f)
                                    add(
                                        Table().apply {
                                            background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.68f))
                                            pad(1f, 4f, 1f, 4f)
                                            add(controlGroupsLabel).right()
                                        }
                                    ).width(initialCenterLayout.groupSummaryWidth.toFloat()).right().padRight(2f)
                                    add(makeButton(">", style = assets.subtleButtonStyle()) { shiftSelectionPage(1) }).width(initialCenterLayout.pagerButtonSize.toFloat()).height(initialCenterLayout.pagerButtonSize.toFloat())
                                }
                            ).expandX().fillX()
                        }
                    ).expandX().fillX().top()
                }
            ).expandX().fillX().padTop(2f).row()
        }

        bottomHud.apply {
            background = null
            pad(0f, 14f, 6f, 14f)
            leftHudSpacerCell = add().width(208f).bottom()
            centerHudCell = add(wrapHudPanel(centerCard, Color(0.20f, 0.44f, 0.50f, 0.92f))).width(266f).bottom().padRight(8f)
            add().expandX().fillX()
            commandHudCell = add(wrapHudPanel(commandCard, Color(0.22f, 0.38f, 0.46f, 0.92f))).width(278f).right().bottom()
        }

        impactFlash.apply {
            setFillParent(true)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            isVisible = false
            background = assets.panelDrawable(Color.WHITE)
            color.set(impactFlashTone)
        }
        stage.addActor(impactFlash)

        root.top()
        root.add(wrapTopStrip(topBar)).expandX().fillX().pad(6f, 18f, 0f, 18f).row()
        root.add().expand().fill().row()
        root.add(bottomHud).expandX().fillX().bottom()
        stage.addActor(root)

        leftHudColumn.apply {
            setFillParent(true)
            bottom().left()
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            add(minimapFrame).padLeft(16f).padBottom(10f)
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
        val bottomHudLayout = computeBottomHudLayout(width, height)
        val commandDeckLayout = computeCommandDeckLayout(width, height)
        val centerPanelLayout = computeCenterPanelLayout(width)
        val topBarLayout = computeTopBarLayout(width)
        val minimapWidth = minimapBounds.width
        val minimapHeight = minimapBounds.height
        val centerWidth = bottomHudLayout.centerWidth.toFloat()
        val commandWidth = bottomHudLayout.commandWidth.toFloat()
        val commandHeight = commandDeckLayout.scrollHeight.toFloat()
        val commandButtonHeight = commandDeckLayout.buttonHeight.toFloat()
        val commandColumns = 3
        val commandCellWidth = (commandWidth / commandColumns) - 2f
        val commandActorWidth = commandCellWidth - commandDeckLayout.actorInset.toFloat()
        val hudShellHeight = computeBottomHudHeight(width, height).toFloat()
        val unifiedPanelHeight = hudShellHeight
        val hudLayoutSignature = buildHudLayoutSignature(width, height, topBarLayout, bottomHudLayout, commandDeckLayout, centerPanelLayout, minimapWidth, minimapHeight)
        if (hudLayoutSignature != lastHudLayoutSignature) {
            lastHudLayoutSignature = hudLayoutSignature
            applyHudLayout(topBarLayout, bottomHudLayout, commandDeckLayout, centerPanelLayout, minimapWidth, minimapHeight, centerWidth, commandWidth, commandHeight, unifiedPanelHeight, hudShellHeight)
        }
        setLabelTextIfChanged(minimapTitle, "Tac Map  ${runtime.session.state.viewedFaction?.let { "F$it" } ?: "Obs"}")
        setActorColorIfChanged(minimapTitle, currentMinimapTitleTone())
        setLabelTextIfChanged(minimapHint, buildMinimapHintLine(runtime.isGameplayCommandArmed()))
        setActorColorIfChanged(minimapHint, currentMinimapHintTone())
        buttonTable.defaults().pad(0f, 0f, 3f, 3f)
        val selectionHeadline = buildSelectionHeadline()
        val selectionFrame = snapshot?.let(::buildSelectionFrameContext)
        val selectionMetaLine = buildSelectionMetaLine(selectionFrame)
        val centerStatusLine = buildCenterStatusLine(selectionFrame)
        val queueStatusLine = buildQueueStatusLine(selectionFrame)
        val queueHeaderLine = buildQueueHeaderLine(selectionFrame)
        val selectionRosterLine = buildSelectionRosterLine(selectionFrame)
        val factionOverviewLine = buildFactionOverviewLine()
        val portraitText = buildPortraitText(selectionFrame)
        val healthLine = buildHealthLine(selectionFrame)
        val topEconomyLine = buildTopEconomyLine()
        val topSelectionLine = buildTopSelectionLine(selectionFrame)
        val topModeLine = buildTopModeLine()
        val statusBadgeLine = buildStatusBadgeLine()
        val actionBannerText = buildActionBannerLine()
        val commandHintText = buildCommandHintLine()
        val centerFooterLine = buildCenterFooterLine()
        val statusSummaryText = buildStatusSummaryLines(topEconomyLine).joinToString("\n")
        setLabelTextIfChanged(selectionLabel, selectionHeadline)
        setLabelTextIfChanged(selectionMetaLabel, selectionMetaLine)
        setLabelTextIfChanged(centerStatusLabel, centerStatusLine)
        val centerStatusBits = buildCenterStatusBits(selectionFrame)
        val centerStatusStripSignature = buildStatusStripSignature(centerStatusBits, "RDY")
        if (centerStatusStripSignature != lastCenterStatusStripSignature) {
            lastCenterStatusStripSignature = centerStatusStripSignature
            rebuildCenterStatusStrip(centerStatusBits)
        }
        setLabelTextIfChanged(queueStatusLabel, queueStatusLine)
        val queueStatusBits = buildQueueStatusBits(selectionFrame)
        val queueStatusStripSignature = buildStatusStripSignature(queueStatusBits, "IDLE")
        if (queueStatusStripSignature != lastQueueStatusStripSignature) {
            lastQueueStatusStripSignature = queueStatusStripSignature
            rebuildQueueStatusStrip(queueStatusBits)
        }
        setLabelTextIfChanged(queueHeaderLabel, queueHeaderLine)
        setActorColorIfChanged(selectionLabel, currentSelectionHeadlineTone())
        setActorColorIfChanged(selectionMetaLabel, currentSelectionMetaTone())
        setActorColorIfChanged(centerStatusLabel, currentCenterStatusTone(centerStatusLine))
        setActorColorIfChanged(queueHeaderLabel, currentQueueHeaderTone(queueHeaderLine))
        setActorColorIfChanged(queueStatusLabel, currentQueueStatusTone(queueHeaderLine))
        setLabelTextIfChanged(selectionRosterLabel, selectionRosterLine)
        setActorColorIfChanged(selectionRosterLabel, currentRosterTone())
        setLabelTextIfChanged(factionOverviewLabel, factionOverviewLine)
        setLabelTextIfChanged(portraitLabel, portraitText)
        setLabelTextIfChanged(healthLabel, healthLine)
        val healthBarSignature = buildHealthBarSignature()
        if (healthBarSignature != lastHealthBarSignature) {
            lastHealthBarSignature = healthBarSignature
            updateHealthBar()
        }
        val selectionGridSignature = buildSelectionGridSignature(snapshot, centerPanelLayout)
        if (selectionGridSignature != lastSelectionGridSignature) {
            lastSelectionGridSignature = selectionGridSignature
            rebuildSelectionGrid()
        }
        setLabelTextIfChanged(hudLinesLabel, statusSummaryText)
        setLabelTextIfChanged(
            footerLabel,
            buildHudFooterLine(
                hasSelection = runtime.session.state.selectedIds.isNotEmpty(),
                commandArmed = runtime.isGameplayCommandArmed()
            )
        )
        setLabelTextIfChanged(statusHeader, "Battlefield")
        setLabelTextIfChanged(centerHeaderLabel, buildCenterHeaderLine(selectionFrame))
        val groupedButtons = commandGroups(runtime.buttonModels())
        setLabelTextIfChanged(commandHeaderLabel, buildCommandHeader(groupedButtons))
        setActorColorIfChanged(commandHeaderLabel, currentCommandHeaderTone(groupedButtons))
        setLabelTextIfChanged(economyLabel, topEconomyLine)
        setActorColorIfChanged(economyLabel, currentTopEconomyTone())
        setLabelTextIfChanged(topSelectionLabel, topSelectionLine)
        setActorColorIfChanged(topSelectionLabel, currentTopSelectionTone())
        setLabelTextIfChanged(modeLabel, topModeLine)
        setActorColorIfChanged(modeLabel, currentTopModeTone())
        setLabelTextIfChanged(statusBadgeLabel, statusBadgeLine)
        setActorColorIfChanged(statusBadgeLabel, currentStatusBadgeTone())
        setLabelTextIfChanged(actionBannerLabel, actionBannerText)
        setActorColorIfChanged(actionBannerLabel, currentActionBannerTextTone())
        setLabelTextIfChanged(commandHintLabel, commandHintText)
        setActorColorIfChanged(commandHintLabel, currentCommandHintTextTone())
        setLabelTextIfChanged(attackWarningLabel, buildAttackWarningText())
        val showAttackWarning = runtime.attackWarningLine() != null
        setActorVisibleIfChanged(attackWarningTable, showAttackWarning)
        setLabelTextIfChanged(centerFooterLabel, centerFooterLine)
        syncSelectionPage(snapshot, actionBannerText, commandHintText, statusBadgeLine)
        val selectionPagerSignature = buildSelectionPagerSignature(snapshot)
        if (selectionPagerSignature != lastSelectionPagerSignature) {
            lastSelectionPagerSignature = selectionPagerSignature
            updateSelectionPager(snapshot)
        }
        setActorVisibleIfChanged(pauseOverlay, runtime.pauseOverlayVisible)
        setActorVisibleIfChanged(helpOverlay, runtime.helpOverlayVisible)
        setLabelTextIfChanged(helpLabel, buildHelpOverlayLines(runtime.helpOverlayVisible).joinToString("\n"))
        val showActionBanner = actionBannerText.isNotBlank()
        setActorVisibleIfChanged(actionBanner, showActionBanner)
        val hudChromeSignature = buildHudChromeSignature(showActionBanner, showAttackWarning)
        if (hudChromeSignature != lastHudChromeSignature) {
            lastHudChromeSignature = hudChromeSignature
            applyHudChrome(showActionBanner, showAttackWarning)
        }
        val commandPanelSignature = buildCommandPanelSignature(groupedButtons, snapshot, commandDeckLayout, commandWidth, commandButtonHeight)
        if (commandPanelSignature != lastCommandPanelSignature) {
            lastCommandPanelSignature = commandPanelSignature
            rebuildCommandPanel(groupedButtons, snapshot, commandDeckLayout, commandColumns, commandCellWidth, commandActorWidth, commandButtonHeight)
            bottomHud.invalidateHierarchy()
        }
    }

    private fun buildHudChromeSignature(showActionBanner: Boolean, showAttackWarning: Boolean): String {
        val selectionPulse = quantizePulse(selectionHudPulse())
        val topModePulse = quantizePulse(topModePulse())
        val topStatusPulse = quantizePulse(topStatusPulse())
        val overlayPulse = quantizePulse(overlayHeaderPulse())
        val bannerPulse = quantizePulse(bannerPulse())
        return listOf(
            commandHintCardColor(bannerPulse).toIntBits(),
            topSelectionShellColor(selectionPulse).toIntBits(),
            topSelectionCardColor(selectionPulse).toIntBits(),
            topModeShellColor(topModePulse).toIntBits(),
            topModeCardColor(topModePulse).toIntBits(),
            topStatusShellColor(topStatusPulse).toIntBits(),
            topStatusCardColor(topStatusPulse).toIntBits(),
            selectionHeadlineCardColor(selectionPulse).toIntBits(),
            portraitFrameColor(selectionPulse).toIntBits(),
            currentAttackWarningCardTone().toIntBits(),
            pauseHeaderCardColor(overlayPulse).toIntBits(),
            helpHeaderCardColor(overlayPulse).toIntBits(),
            if (showActionBanner) actionBannerColor(bannerPulse).toIntBits() else "none",
            showActionBanner,
            showAttackWarning
        ).joinToString("|")
    }

    private fun applyHudChrome(showActionBanner: Boolean, showAttackWarning: Boolean) {
        val selectionPulse = quantizePulse(selectionHudPulse())
        val topModePulse = quantizePulse(topModePulse())
        val topStatusPulse = quantizePulse(topStatusPulse())
        val overlayPulse = quantizePulse(overlayHeaderPulse())
        val bannerPulse = quantizePulse(bannerPulse())
        commandHintCard.background = assets.panelDrawable(commandHintCardColor(bannerPulse))
        topSelectionShell.background = assets.panelDrawable(topSelectionShellColor(selectionPulse))
        topSelectionCard.background = assets.panelDrawable(topSelectionCardColor(selectionPulse))
        topModeShell.background = assets.panelDrawable(topModeShellColor(topModePulse))
        topModeCard.background = assets.panelDrawable(topModeCardColor(topModePulse))
        topStatusShell.background = assets.panelDrawable(topStatusShellColor(topStatusPulse))
        topStatusCard.background = assets.panelDrawable(topStatusCardColor(topStatusPulse))
        selectionHeadlineCard.background = assets.panelDrawable(selectionHeadlineCardColor(selectionPulse))
        portraitFrame.background = assets.panelDrawable(portraitFrameColor(selectionPulse))
        attackWarningCard.background = assets.panelDrawable(currentAttackWarningCardTone())
        pauseHeaderCard.background = assets.panelDrawable(pauseHeaderCardColor(overlayPulse))
        helpHeaderCard.background = assets.panelDrawable(helpHeaderCardColor(overlayPulse))
        actionBanner.background = if (showActionBanner) assets.panelDrawable(actionBannerColor(bannerPulse)) else null
        actionBanner.pad(if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f, if (showActionBanner) 3f else 0f, if (showActionBanner) 6f else 0f)
        setActorVisibleIfChanged(attackWarningTable, showAttackWarning)
    }

    private fun quantizePulse(value: Float, steps: Int = 6): Float {
        if (value <= 0f) return 0f
        val clamped = value.coerceIn(0f, 1f)
        return ((clamped * steps).roundToInt() / steps.toFloat()).coerceIn(0f, 1f)
    }

    private fun buildHudLayoutSignature(
        width: Int,
        height: Int,
        topBarLayout: TopBarLayout,
        bottomHudLayout: BottomHudLayout,
        commandDeckLayout: CommandDeckLayout,
        centerPanelLayout: CenterPanelLayout,
        minimapWidth: Float,
        minimapHeight: Float
    ): String =
        listOf(
            width,
            height,
            topBarLayout.selectionWidth,
            topBarLayout.modeWidth,
            topBarLayout.statusWidth,
            bottomHudLayout.leftSlotWidth,
            bottomHudLayout.centerWidth,
            bottomHudLayout.commandWidth,
            commandDeckLayout.scrollHeight,
            centerPanelLayout.portraitSize,
            minimapWidth.toInt(),
            minimapHeight.toInt(),
            computeBottomHudHeight(width, height)
        ).joinToString("|")

    private fun applyHudLayout(
        topBarLayout: TopBarLayout,
        bottomHudLayout: BottomHudLayout,
        commandDeckLayout: CommandDeckLayout,
        centerPanelLayout: CenterPanelLayout,
        minimapWidth: Float,
        minimapHeight: Float,
        centerWidth: Float,
        commandWidth: Float,
        commandHeight: Float,
        unifiedPanelHeight: Float,
        hudShellHeight: Float
    ) {
        topSelectionCell.width(topBarLayout.selectionWidth.toFloat())
        topModeCell.width(topBarLayout.modeWidth.toFloat())
        topStatusCell.width(topBarLayout.statusWidth.toFloat())
        leftHudSpacerCell.width(bottomHudLayout.leftSlotWidth.toFloat())
        centerHudCell.width(centerWidth)
        commandHudCell.width(commandWidth)
        selectionLabel.setWrap(true)
        selectionMetaLabel.setWrap(true)
        factionOverviewLabel.setWrap(true)
        queueStatusLabel.setWrap(true)
        selectionRosterLabel.setWrap(true)
        hudLinesLabel.setWrap(true)
        footerLabel.setWrap(true)
        centerFooterLabel.setWrap(true)
        topSelectionLabel.setWrap(false)
        modeLabel.setWrap(false)
        statusBadgeLabel.setWrap(false)
        commandHintLabel.setWrap(false)
        selectionLabel.setWidth(centerWidth)
        selectionMetaLabel.setWidth(centerWidth)
        queueStatusLabel.setWidth(centerWidth)
        selectionRosterLabel.setWidth(centerWidth)
        hudLinesLabel.setWidth(minimapWidth)
        factionOverviewLabel.setWidth(minimapWidth)
        footerLabel.setWidth(minimapWidth)
        centerFooterLabel.setWidth(centerWidth)
        topSelectionLabel.setWidth(topBarLayout.selectionWidth.toFloat() - 8f)
        modeLabel.setWidth(topBarLayout.modeWidth.toFloat() - 8f)
        statusBadgeLabel.setWidth(topBarLayout.statusWidth.toFloat() - 8f)
        commandHintLabel.setWidth(commandWidth - 38f)
        minimapHint.setWidth(minimapWidth - 20f)
        minimapFrame.setSize(minimapWidth, minimapHeight)
        centerCard.setSize(centerWidth, unifiedPanelHeight - 6f)
        commandCard.setSize(commandWidth, unifiedPanelHeight - 6f)
        commandScroll.setSize(commandWidth - 8f, commandHeight)
        portraitFrame.setSize(centerPanelLayout.portraitSize.toFloat(), centerPanelLayout.portraitSize.toFloat())
        bottomHud.setHeight(hudShellHeight)
    }

    private fun rebuildCommandPanel(
        groupedButtons: List<Pair<String, List<ClientCommandButton>>>,
        snapshot: ClientSnapshot?,
        commandDeckLayout: CommandDeckLayout,
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
                    background = assets.panelDrawable(Color(0.02f, 0.05f, 0.08f, 0.98f))
                    pad(commandDeckLayout.groupPad.toFloat())
                    add(
                        Table().apply {
                            background = assets.panelDrawable(commandGroupHeaderTone(group.first))
                            pad(2f, commandDeckLayout.headerPadX.toFloat(), 2f, commandDeckLayout.headerPadX.toFloat())
                            add(Table().apply { background = assets.panelDrawable(commandGroupAccentTone(group.first)) }).width(2f).expandY().fillY().padRight(4f)
                            add(Label(group.first.uppercase(), assets.accentLabelStyle)).left()
                        }
                    ).colspan(commandColumns).left().padBottom(4f).row()
                    group.second.forEachIndexed { index, button ->
                        val activePulse = uiPulse()
                        val buttonLabel = commandButtonText(button)
                        val hotkeyLabel = commandButtonHotkey(button)
                        val actor = makeButton(
                            buttonLabel,
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
                                                            actor.isChecked -> Color(1.00f, 0.92f, 0.46f, 0.64f)
                                                            button.actionId == "attackMove" -> pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.74f)
                                                            button.actionId.startsWith("build:") -> pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.72f)
                                                            button.actionId.startsWith("train:") || button.actionId.startsWith("research:") -> Color(0.72f, 0.84f, 1.00f, 0.72f)
                                                            button.actionId == "move" || button.actionId == "hold" -> pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.70f)
                                                            else -> Color(0.56f, 0.88f, 0.96f, 0.66f)
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
                                        ).size(if (commandDeckLayout.buttonHeight <= 17) 9f else 10f, if (commandDeckLayout.buttonHeight <= 17) 9f else 10f).left().padRight(3f)
                                        add(actor).width(commandActorWidth - if (hotkeyLabel != null) 18f else 8f).height(commandButtonHeight).left().expandX().fillX()
                                        if (hotkeyLabel != null) {
                                            add(
                                                Table().apply {
                                                    background =
                                                        assets.panelDrawable(
                                                            when {
                                                                actor.isDisabled -> Color(0.16f, 0.18f, 0.20f, 0.34f)
                                                                actor.isChecked -> Color(1.00f, 0.94f, 0.60f, 0.76f)
                                                                else -> commandHotkeyTone(button.actionId)
                                                            }
                                                        )
                                                    pad(1f, 3f, 1f, 3f)
                                                    add(
                                                        Label(hotkeyLabel, assets.mutedLabelStyle).apply {
                                                            setFontScale(0.76f)
                                                            color =
                                                                when {
                                                                    actor.isDisabled -> Color(0.72f, 0.76f, 0.80f, 0.62f)
                                                                    actor.isChecked -> Color(0.14f, 0.12f, 0.08f, 0.94f)
                                                                    else -> Color(0.92f, 0.96f, 1.00f, 0.96f)
                                                                }
                                                        }
                                                    ).center()
                                                }
                                            ).minWidth(15f).height(commandButtonHeight - 2f).padLeft(3f)
                                        } else {
                                            add(
                                                Table().apply {
                                                    background =
                                                        assets.panelDrawable(
                                                            when {
                                                                actor.isDisabled -> Color(1f, 1f, 1f, 0.06f)
                                                                actor.isChecked -> Color(1.00f, 0.94f, 0.60f, 0.44f)
                                                                else -> Color(1f, 1f, 1f, 0.08f)
                                                            }
                                                        )
                                                }
                                            ).size(3f, 3f).padLeft(3f)
                                        }
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
            append(commandWidth.toInt())
            append('|')
            append(commandButtonHeight.toInt())
            append('|')
            append(snapshot?.entities?.size ?: -1)
            append('|')
            append(snapshot?.resourceNodes?.size ?: -1)
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
                }
            }
        }

    private fun buildStatusSummaryLines(topEconomyLine: String = buildTopEconomyLine()): List<String> {
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
        if (runtime.session.state.viewedFaction != null && topEconomyLine.contains("vis 0")) {
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
            "${faction.uppercase()} READY · ${snapshot.entities.size} UNITS"
        } else {
            val lead = snapshot.entities.firstOrNull { it.id == runtime.session.state.selectedIds.first() }
            if (runtime.session.state.selectedIds.size == 1 && lead != null) {
                "${lead.typeId ?: "Unit"} #${lead.id}"
            } else {
                "${runtime.session.state.selectedIds.size} UNIT GROUP"
            }
        }
    }

    private fun buildSelectionFrameContext(snapshot: ClientSnapshot): SelectionFrameContext {
        val selected = snapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val lead = if (selected.isEmpty()) null else resolveFocusedEntity(snapshot, selected) ?: selected.first()
        return SelectionFrameContext(snapshot, selected, lead)
    }

    private fun buildPortraitText(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val selectionContext = context ?: return "NO\nDATA"
        if (selectionContext.selected.isEmpty()) {
            return runtime.session.state.viewedFaction?.let { "F$it\nVIEW" } ?: "OBS\nVIEW"
        }
        val lead = selectionContext.lead ?: return "NO\nDATA"
        return if (selectionContext.selected.size == 1) {
            "${compactPortraitType(lead.typeId)}\n${compactPortraitRole(lead.archetype)}"
        } else {
            "${selectionContext.selected.size} UN\n${compactPortraitType(lead.typeId)}"
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

    private fun buildHealthLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val selected = context?.selected.orEmpty()
        if (selected.isEmpty()) return "HP -"
        val hp = selected.sumOf { it.hp }
        val maxHp = selected.sumOf { it.maxHp.coerceAtLeast(1) }
        return "HP $hp/$maxHp"
    }

    private fun buildSelectionRosterLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val selected = context?.selected.orEmpty()
        if (selected.isEmpty()) {
            return "No active roster"
        }
        val counts =
            selected
                .groupingBy { it.typeId ?: "Unknown" }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(4)
                .joinToString("  ") { "${it.key} x${it.value}" }
        return counts
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
        val productionCount =
            runtime.buttonModels().count {
                it.actionId.startsWith("build:") ||
                    it.actionId.startsWith("train:") ||
                    it.actionId.startsWith("research:") ||
                    it.actionId.startsWith("cancel")
            }
        val hasProduction = groups.any { it.first == "Production" && it.second.isNotEmpty() }
        return formatCommandHeaderLine(
            overlayModeLabel = runtime.overlayModeLabel(),
            productionCount = if (hasProduction) productionCount else 0,
            productionPage = productionPage
        )
    }

    private fun buildCenterHeaderLine(context: SelectionFrameContext?): String =
        when {
            context == null -> "Selected"
            context.selected.isEmpty() -> "Selected"
            context.selected.size == 1 -> "Focus"
            else -> "Squad"
        }

    private fun buildCenterStatusLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val lead = context?.lead ?: return "No status"
        val statusBits = buildList {
            lead.activeOrder?.takeIf { it.isNotBlank() }?.let { add(it.lowercase().take(8)) }
            if (lead.orderQueueSize > 0) add("queue ${lead.orderQueueSize}")
            if (lead.pathRemainingNodes > 0) add("path ${lead.pathRemainingNodes}")
            lead.activeProductionType?.let { add("train ${it.take(8)}") }
            lead.activeResearchTech?.let { add("tech ${it.take(8)}") }
            if (lead.underConstruction) add("construct")
            lead.harvestPhase?.let { add("harvest ${it.lowercase().take(8)}") }
            if (lead.harvestCargoAmount != null && lead.harvestCargoAmount > 0) {
                add("${(lead.harvestCargoKind ?: "res").take(3)} ${lead.harvestCargoAmount}")
            }
        }
        return if (statusBits.isEmpty()) "Ready" else statusBits.joinToString(" · ")
    }

    private fun rebuildCenterStatusStrip(bits: List<StatusChip>) {
        centerStatusStrip.clearChildren()
        centerStatusStrip.defaults().left().pad(0f, 0f, 0f, 3f)
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

    private fun buildStatusStripSignature(bits: List<StatusChip>, emptyLabel: String): String =
        if (bits.isEmpty()) {
            emptyLabel
        } else {
            bits.joinToString("|") { chip ->
                buildString {
                    append(chip.label)
                    append('@')
                    append(chip.background.toIntBits())
                    append(':')
                    append(chip.accent.toIntBits())
                }
            }
        }

    private fun buildCenterStatusBits(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): List<StatusChip> {
        val lead = context?.lead ?: return emptyList()
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
            runtime.groundMode == ClientGroundCommandMode.MOVE -> "LMB/RMB move  Esc cancel"
            runtime.groundMode == ClientGroundCommandMode.ATTACK_MOVE -> "LMB/RMB attack  Esc cancel"
            runtime.groundMode == ClientGroundCommandMode.PATROL -> "LMB/RMB patrol  Esc cancel"
            runtime.session.state.selectedIds.isNotEmpty() -> "RMB move  A+LMB attack  Shift add"
            else -> "Drag select  RMB order  MMB pan"
        }

    private fun rebuildQueueStatusStrip(bits: List<StatusChip>) {
        queueStatusStrip.clearChildren()
        queueStatusStrip.defaults().left().pad(0f, 0f, 0f, 3f)
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

    private fun buildQueueStatusBits(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): List<StatusChip> {
        val lead = context?.lead ?: return emptyList()
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

    private fun buildQueueStatusLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val lead = context?.lead ?: return "Idle"
        return formatQueueStatusLine(
            productionType = lead.activeProductionType,
            productionQueueSize = lead.productionQueueSize,
            productionRemainingTicks = lead.activeProductionRemainingTicks,
            researchTech = lead.activeResearchTech,
            researchQueueSize = lead.researchQueueSize,
            researchRemainingTicks = lead.activeResearchRemainingTicks,
            underConstruction = lead.underConstruction,
            constructionRemainingTicks = lead.constructionRemainingTicks
        )
    }

    private fun buildQueueHeaderLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val lead = context?.lead ?: return "QUEUE"
        return resolveQueueHeaderLine(
            hasProduction = lead.activeProductionType != null || lead.productionQueueSize > 0,
            hasResearch = lead.activeResearchTech != null || lead.researchQueueSize > 0,
            underConstruction = lead.underConstruction
        )
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

    private fun buildHealthBarSignature(): String {
        val snapshot = runtime.snapshot
        val selected = snapshot?.entities?.filter { it.id in runtime.session.state.selectedIds }.orEmpty()
        val takingDamage = selected.any { runtime.isDamageFlashActive(it.id) }
        val impactKind = selected.firstNotNullOfOrNull { runtime.damageImpactKind(it.id) }
        val totalHp = selected.sumOf { it.hp }
        val totalMaxHp = selected.sumOf { it.maxHp.coerceAtLeast(1) }
        return listOf(
            selected.size,
            totalHp,
            totalMaxHp,
            takingDamage,
            impactKind?.name ?: "-"
        ).joinToString("|")
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
                    background = assets.panelDrawable(Color(0.08f, 0.12f, 0.16f, 0.74f))
                    pad(3f, 6f, 3f, 6f)
                    add(Label("No slots", assets.mutedLabelStyle)).left()
                }
            ).left()
            return
        }
        selectionGrid.defaults().pad(0f, 1.5f, 1.5f, 0f)
        selected.forEachIndexed { index, entity ->
            selectionGrid.add(buildSelectionSlot(entity, centerPanelLayout)).size(centerPanelLayout.rosterSlotSize.toFloat(), centerPanelLayout.rosterSlotSize.toFloat())
            if ((index + 1) % 4 == 0) {
                selectionGrid.row()
            }
        }
    }

    private fun buildSelectionGridSignature(snapshot: ClientSnapshot?, centerPanelLayout: CenterPanelLayout): String {
        val safeSnapshot = snapshot ?: return "empty"
        val selectedEntities = safeSnapshot.entities.filter { it.id in runtime.session.state.selectedIds }
        val pageSize = 8
        val pageCount = ((selectedEntities.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = selectionPage.coerceIn(0, pageCount - 1)
        val pageStart = page * pageSize
        val selected = selectedEntities.drop(pageStart).take(pageSize)
        return buildString {
            append(page)
            append('|')
            append(centerPanelLayout.rosterSlotSize)
            append('|')
            append(focusedSelectionId ?: -1)
            append('|')
            selected.forEach { entity ->
                append(entity.id)
                append(':')
                append(entity.hp)
                append('/')
                append(entity.maxHp)
                append(':')
                append(entity.weaponId ?: "-")
                append(':')
                append(entity.footprintWidth ?: -1)
                append(':')
                append(runtime.isDamageFlashActive(entity.id))
                append(':')
                append(runtime.damageImpactKind(entity.id)?.name ?: "-")
                append(':')
                append(slotPulseUntilMillis[entity.id] ?: 0L)
                append(';')
            }
        }
    }

    private fun buildSelectionSlot(entity: EntitySnapshot, layout: CenterPanelLayout): Table {
        val visualLayout = computeSelectionSlotVisualLayout(layout.rosterSlotSize)
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
        val titleLabel =
            Label(shortName, assets.titleLabelStyle).apply {
                setFontScale(if (layout.rosterSlotSize <= 32) 0.78f else 0.86f)
                setAlignment(Align.center)
            }
        return Table().apply {
            background =
                assets.panelDrawable(
                    if (focused) selectionFocusShellTone(focusPulse)
                    else if (clickPulse > 0f) Color(0.14f, 0.20f, 0.16f, 0.94f).lerp(Color(0.40f, 0.56f, 0.32f, 0.96f), clickPulse * 0.34f)
                    else if (damaged) Color(0.22f, 0.10f, 0.10f, 0.94f)
                    else Color(0.08f, 0.12f, 0.16f, 0.92f)
                )
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            pad(if (layout.rosterSlotSize <= 32) 0.5f else 1f)
            add(
                Table().apply {
                    background =
                        assets.panelDrawable(
                            if (focused) selectionFocusCardTone(focusPulse)
                            else if (clickPulse > 0f) tone.cpy().lerp(Color(0.40f, 0.54f, 0.30f, 0.96f), clickPulse * 0.28f)
                            else if (damaged) tone.cpy().lerp(Color.SCARLET, 0.28f)
                            else tone
                        )
                    pad(if (layout.rosterSlotSize <= 32) {
                        if (focused) 1.5f else 1f
                    } else {
                        if (focused) 2f else 1.5f
                    })
                    add(
                        Table().apply {
                            background =
                                assets.panelDrawable(
                                    if (focused) selectionFocusBaseTone().cpy().apply { a = 0.62f + (focusPulse * 0.18f) }
                                    else if (damaged) Color(1.00f, 0.74f, 0.64f, 0.28f)
                                    else Color(1f, 1f, 1f, 0.08f)
                                )
                        }
                    ).height(visualLayout.topBarHeight).expandX().fillX().padBottom(if (layout.rosterSlotSize <= 32) 1f else 2f).row()
                    add(
                        Table().apply {
                            background = assets.panelDrawable(Color(1f, 1f, 1f, if (focused) 0.08f else 0.04f))
                            pad(if (layout.rosterSlotSize <= 32) 1f else 2f, 1f, 0.5f, 1f)
                            add(titleLabel).center().expandX().fillX()
                        }
                    ).expandX().fillX().height(visualLayout.titleHeight).row()
                    add(buildSelectionSlotGlyph(entity, badgeTone, focused, damaged, visualLayout)).center().padTop(if (layout.rosterSlotSize <= 32) 0.5f else 1f).row()
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
                            ).size(6f * visualLayout.glyphScale, visualLayout.topBarHeight)
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
                            ).size(visualLayout.markerSize, visualLayout.markerSize)
                        }
                    ).expandX().fillX().padTop(if (layout.rosterSlotSize <= 32) 0.5f else 1f).row()
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
                            ).width(visualLayout.hpBarWidth * hpRatio.coerceIn(0f, 1f)).height(visualLayout.hpBarHeight).left()
                            add().expandX().fillX()
                        }
                    ).width(visualLayout.hpBarWidth).height(visualLayout.hpBarHeight).padTop(if (layout.rosterSlotSize <= 32) 1f else 2f)
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
        val type = (entity.typeId ?: "?").uppercase()
        return when {
            type.length <= 2 -> type
            "_" in type -> type.split("_").mapNotNull { it.firstOrNull()?.toString() }.joinToString("").take(2)
            else -> type.take(2)
        }
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

    private fun syncSelectionPage(snapshot: ClientSnapshot?, actionBannerText: String, commandHintText: String, statusBadgeLine: String) {
        val bannerSignature = "$actionBannerText|$commandHintText"
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
        val topStatusSignature = "${runtime.attackWarningLine().orEmpty()}|${runtime.noticeLine().orEmpty()}|$statusBadgeLine"
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

    private fun overlayHeaderPulse(): Float {
        val remaining = overlayHeaderPulseUntilMillis - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        val normalized = remaining.toFloat() / HUD_SELECTION_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
    }

    private fun bannerPulse(): Float {
        val remaining = bannerPulseUntilMillis - System.currentTimeMillis()
        if (remaining <= 0L) return 0f
        val normalized = remaining.toFloat() / HUD_SELECTION_PULSE_DURATION_MS.toFloat()
        return normalized * normalized
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
        setLabelTextIfChanged(selectionPageLabel, formatSelectionPageLabel(selectedCount, selectionPage, pageCount))
        setLabelTextIfChanged(controlGroupsLabel, formatControlGroupDeckLine(runtime.controlGroupSizes()))
        val controlGroupButtonsSignature = buildControlGroupButtonsSignature()
        if (controlGroupButtonsSignature != lastControlGroupButtonsSignature) {
            lastControlGroupButtonsSignature = controlGroupButtonsSignature
            rebuildControlGroupButtons()
        }
    }

    private fun setLabelTextIfChanged(label: Label, text: String) {
        if (!label.textEquals(text)) {
            label.setText(text)
        }
    }

    private fun setActorColorIfChanged(actor: Actor, color: Color) {
        if (actor.color.toIntBits() != color.toIntBits()) {
            actor.color = color
        }
    }

    private fun setActorVisibleIfChanged(actor: Actor, visible: Boolean) {
        if (actor.isVisible != visible) {
            actor.isVisible = visible
        }
    }

    private fun buildSelectionPagerSignature(snapshot: ClientSnapshot?): String {
        val selectedCount = snapshot?.entities?.count { it.id in runtime.session.state.selectedIds } ?: 0
        val pageSize = 8
        val pageCount = ((selectedCount + pageSize - 1) / pageSize).coerceAtLeast(1)
        return listOf(
            selectedCount,
            selectionPage.coerceIn(0, pageCount - 1),
            pageCount,
            formatControlGroupDeckLine(runtime.controlGroupSizes())
        ).joinToString("|")
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
                            runtime.handleControlGroup(group, assign = false, add = false, viewWidth = Gdx.graphics.width, viewHeight = computeWorldViewportHeight(Gdx.graphics.width, Gdx.graphics.height))
                        }
                    ).height(18f)
                }
            ).height(18f)
        }
    }

    private fun buildControlGroupButtonsSignature(): String =
        runtime.controlGroupSizes().joinToString("|") { (group, count) -> "$group:$count" }

    private fun updateScreenFade(delta: Float) {
        if (screenFadeAlpha <= 0f) {
            screenFade.isVisible = false
            return
        }
        screenFadeAlpha = (screenFadeAlpha - (delta * 1.8f)).coerceAtLeast(0f)
        screenFade.isVisible = screenFadeAlpha > 0f
        screenFade.color.a = screenFadeAlpha
    }

    private fun triggerImpactFlash(tone: Color, alpha: Float) {
        if (alpha <= 0f) return
        impactFlashTone.set(tone.r, tone.g, tone.b, 1f)
        impactFlash.color.set(impactFlashTone)
        impactFlashAlpha = maxOf(impactFlashAlpha, alpha.coerceIn(0f, 0.18f))
        impactFlash.isVisible = impactFlashAlpha > 0f
        impactFlash.color.a = impactFlashAlpha
    }

    private fun updateImpactFlash(delta: Float) {
        if (impactFlashAlpha <= 0f) {
            impactFlash.isVisible = false
            return
        }
        impactFlashAlpha = (impactFlashAlpha - (delta * IMPACT_FLASH_DECAY_PER_SECOND)).coerceAtLeast(0f)
        impactFlash.isVisible = impactFlashAlpha > 0f
        impactFlash.color.set(impactFlashTone.r, impactFlashTone.g, impactFlashTone.b, impactFlashAlpha)
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

    private fun buildSelectionMetaLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val selectionContext = context ?: return "No live snapshot"
        if (selectionContext.selected.isEmpty()) {
            return "${runtime.session.state.viewedFaction?.let { "f$it" } ?: "obs"} · ${selectionContext.snapshot.entities.size}"
        }
        val combat = selectionContext.selected.count { it.weaponId != null }
        val workers = selectionContext.selected.count { it.archetype == "worker" }
        val structures = selectionContext.selected.count { it.footprintWidth != null && it.footprintHeight != null }
        return "${selectionContext.selected.size} units · $combat combat · $workers workers · $structures structures"
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

    private fun buildTopSelectionLine(context: SelectionFrameContext? = runtime.snapshot?.let(::buildSelectionFrameContext)): String {
        val selectionContext = context ?: return "No sel"
        val lead = selectionContext.lead ?: return "No sel"
        return if (selectionContext.selected.size == 1) {
            "${lead.typeId} ${lead.hp}/${lead.maxHp}"
        } else {
            "${selectionContext.selected.size}x ${lead.typeId}"
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
                runtime.buildModeTypeId != null -> "Place ${runtime.buildModeTypeId}  LMB/RMB confirm"
                runtime.groundMode == ClientGroundCommandMode.MOVE -> "Move order  LMB/RMB confirm"
                runtime.groundMode == ClientGroundCommandMode.ATTACK_MOVE -> "Attack order  LMB/RMB confirm"
                runtime.groundMode == ClientGroundCommandMode.PATROL -> "Patrol route  LMB/RMB confirm"
                runtime.session.state.selectedIds.isNotEmpty() -> "RMB move  A+LMB atk  M move"
                else -> "Select units for orders"
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
            runtime.attackWarningLine() != null -> Color(1.00f, 0.88f, 0.78f, 0.98f)
            runtime.noticeLine() != null -> Color(1.00f, 0.94f, 0.74f, 0.98f)
            else -> Color(0.90f, 0.96f, 1.00f, 0.96f)
        }

    private fun currentTopSelectionTone(): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.76f, 0.82f, 0.86f, 0.94f)
            runtime.snapshot?.entities?.any { it.id in runtime.session.state.selectedIds && it.weaponId != null } == true ->
                Color(1.00f, 0.94f, 0.68f, 0.98f)
            else -> Color(0.82f, 0.96f, 1.00f, 0.98f)
        }

    private fun currentTopModeTone(): Color =
        when {
            runtime.buildModeTypeId != null -> Color(1.00f, 0.92f, 0.64f, 0.98f)
            runtime.groundMode != null -> Color(0.78f, 0.98f, 0.88f, 0.98f)
            runtime.pauseOverlayVisible || runtime.playControlState.paused -> Color(0.88f, 0.92f, 1.00f, 0.98f)
            else -> Color(0.76f, 0.98f, 0.88f, 0.96f)
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

    private fun commandHintCardColor(pulse: Float): Color =
        currentCommandHintCardTone().cpy().lerp(Color(0.22f, 0.30f, 0.22f, 0.82f), pulse * 0.22f)

    private fun topSelectionShellColor(pulse: Float): Color =
        Color(0.08f, 0.13f, 0.17f, 0.58f).lerp(Color(0.18f, 0.24f, 0.18f, 0.68f), pulse * 0.20f)

    private fun topSelectionCardColor(pulse: Float): Color =
        Color(0.10f, 0.16f, 0.20f, 0.72f).lerp(Color(0.26f, 0.34f, 0.22f, 0.82f), pulse * 0.28f)

    private fun topModeShellColor(pulse: Float): Color =
        Color(0.08f, 0.13f, 0.17f, 0.58f).lerp(Color(0.16f, 0.22f, 0.18f, 0.68f), pulse * 0.20f)

    private fun topModeCardColor(pulse: Float): Color =
        Color(0.10f, 0.18f, 0.16f, 0.72f).lerp(Color(0.24f, 0.34f, 0.22f, 0.82f), pulse * 0.28f)

    private fun topStatusShellColor(pulse: Float): Color =
        Color(0.08f, 0.13f, 0.17f, 0.60f).lerp(Color(0.24f, 0.18f, 0.14f, 0.70f), pulse * 0.22f)

    private fun topStatusCardColor(pulse: Float): Color =
        Color(0.16f, 0.23f, 0.29f, 0.74f).lerp(Color(0.38f, 0.26f, 0.18f, 0.84f), pulse * 0.30f)

    private fun selectionHeadlineCardColor(pulse: Float): Color =
        currentSelectionHeadlineCardTone().cpy().lerp(Color(0.74f, 1.00f, 0.82f, 0.86f), pulse * 0.34f)

    private fun portraitFrameColor(pulse: Float): Color =
        Color(0.16f, 0.20f, 0.18f, 0.80f).lerp(Color(0.28f, 0.36f, 0.22f, 0.86f), pulse * 0.28f)

    private fun pauseHeaderCardColor(pulse: Float): Color =
        currentPauseHeaderTone().cpy().lerp(Color(0.38f, 0.30f, 0.20f, 0.94f), pulse * 0.26f)

    private fun helpHeaderCardColor(pulse: Float): Color =
        currentHelpHeaderTone().cpy().lerp(Color(0.26f, 0.34f, 0.22f, 0.90f), pulse * 0.22f)

    private fun actionBannerColor(pulse: Float): Color =
        currentActionBannerTone().cpy().lerp(Color(0.24f, 0.30f, 0.18f, 0.82f), pulse * 0.24f)

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

    private fun currentCenterStatusTone(centerStatusLine: String = buildCenterStatusLine()): Color =
        when {
            runtime.session.state.selectedIds.isEmpty() -> Color(0.60f, 0.70f, 0.76f, 0.92f)
            centerStatusLine == "Ready" -> Color(0.62f, 0.88f, 0.96f, 0.92f)
            else -> Color(0.90f, 0.94f, 0.98f, 0.94f)
        }

    private fun currentQueueHeaderTone(queueHeaderLine: String = buildQueueHeaderLine()): Color =
        when (queueHeaderLine) {
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
                .replace("attack", "atk")
                .replace("command", "cmd")
                .replace("current ", "")
                .replace("camera ", "")
                .replace("scenario ", "")
                .replace("preset", "pst")
                .trim()
        return if (cleaned.length <= 20) cleaned else cleaned.take(17).trimEnd() + "..."
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

    private fun currentQueueStatusTone(queueHeaderLine: String = buildQueueHeaderLine()): Color =
        when (queueHeaderLine) {
            "PRODUCTION" -> Color(1.00f, 0.94f, 0.72f, 0.94f)
            "RESEARCH" -> Color(0.86f, 0.96f, 1.00f, 0.94f)
            "CONSTRUCT" -> Color(0.84f, 1.00f, 0.90f, 0.94f)
            else -> Color(0.66f, 0.74f, 0.80f, 0.88f)
        }

    private fun currentQueueCardTone(queueHeaderLine: String = buildQueueHeaderLine()): Color =
        when (queueHeaderLine) {
            "PRODUCTION" -> Color(0.18f, 0.14f, 0.08f, 0.74f)
            "RESEARCH" -> Color(0.10f, 0.14f, 0.20f, 0.74f)
            "CONSTRUCT" -> Color(0.10f, 0.17f, 0.12f, 0.74f)
            else -> Color(0.08f, 0.12f, 0.16f, 0.62f)
        }

    private fun currentQueueHeaderBackgroundTone(queueHeaderLine: String = buildQueueHeaderLine()): Color =
        when (queueHeaderLine) {
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

    private fun commandButtonText(button: ClientCommandButton): String =
        formatCommandButtonText(button.actionId, button.label)

    private fun commandButtonHotkey(button: ClientCommandButton): String? =
        resolveCommandButtonHotkey(button.actionId)

    private fun commandHotkeyTone(actionId: String): Color =
        when {
            actionId == "attackMove" -> pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.72f)
            actionId.startsWith("build:") -> pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.72f)
            actionId == "move" || actionId == "hold" || actionId == "patrol" -> pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.68f)
            actionId.startsWith("train:") || actionId.startsWith("research:") -> Color(0.66f, 0.80f, 1.00f, 0.68f)
            else -> Color(0.34f, 0.42f, 0.48f, 0.68f)
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
            dragSelection = null
        }

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (overlayBlocksWorldInput(runtime.pauseOverlayVisible, runtime.helpOverlayVisible)) return true
            when (button) {
                Input.Buttons.MIDDLE -> {
                    panning = true
                    lastX = screenX.toFloat()
                    lastY = screenY.toFloat()
                    return true
                }
                Input.Buttons.LEFT -> {
                    val clickX = screenX.toFloat()
                    val clickY = screenY.toFloat()
                    if (isHudSurface(clickX, clickY)) {
                        if (!runtime.isGameplayCommandArmed() &&
                            gdxMiniMapBounds(Gdx.graphics.width, Gdx.graphics.height).contains(clickX, clickY) &&
                            runtime.dragCenterFromMinimap(clickX, clickY, Gdx.graphics.width, Gdx.graphics.height)
                        ) {
                            minimapDragging = true
                            dragSelection = null
                        }
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
                    if (isHudSurface(screenX.toFloat(), screenY.toFloat())) {
                        rightClickHandled = false
                        return true
                    }
                    runtime.issueRightClick(screenX.toFloat(), screenY.toFloat(), Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
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
                if (!armedLeftPress) {
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
            if (shouldIssueSelectionBox(armedLeftPress, startX, startY, screenX.toFloat(), screenY.toFloat())) {
                runtime.issueSelectionBox(startX, startY, screenX.toFloat(), screenY.toFloat(), additive)
            } else {
                runtime.issueLeftClick(screenX.toFloat(), screenY.toFloat(), additive)
            }
            resetPointerState()
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
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
                            hasArmedMode = runtime.groundMode != null || runtime.buildModeTypeId != null
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
    val actorInset: Int
)

internal data class CenterPanelLayout(
    val portraitSize: Int,
    val rosterSlotSize: Int,
    val pagerButtonSize: Int,
    val pagerLabelWidth: Int,
    val groupSummaryWidth: Int,
    val healthBarWidth: Int
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
    val centerHeight = (screenHeight * 0.132f).coerceIn(108f, 140f)
    val commandHeight = (screenHeight * 0.082f).coerceIn(68f, 90f)
    val commandShellHeight = (commandHeight + 24f).coerceIn(94f, 114f)
    return maxOf(minimapHeight, centerHeight, commandShellHeight).toInt()
}

internal fun computeBottomHudLayout(screenWidth: Int, screenHeight: Int): BottomHudLayout {
    val minimapWidth = gdxMiniMapBounds(screenWidth, screenHeight).width.toInt()
    val leftSlotWidth = (minimapWidth + 6).coerceAtLeast(146)
    val centerWidth = (screenWidth * 0.152f).coerceIn(196f, 248f).toInt()
    val commandWidth = (screenWidth * 0.158f).coerceIn(208f, 264f).toInt()
    return BottomHudLayout(
        leftSlotWidth = leftSlotWidth,
        centerWidth = centerWidth,
        commandWidth = commandWidth
    )
}

internal fun computeCommandDeckLayout(screenWidth: Int, screenHeight: Int): CommandDeckLayout {
    val compact = screenWidth < 1360
    val scrollHeight = if (compact) (screenHeight * 0.076f).coerceIn(62f, 78f) else (screenHeight * 0.084f).coerceIn(68f, 90f)
    return CommandDeckLayout(
        scrollHeight = scrollHeight.toInt(),
        buttonHeight = if (compact) 16 else 18,
        groupPad = if (compact) 2 else 3,
        headerPadX = if (compact) 4 else 6,
        actorInset = if (compact) 12 else 14
    )
}

internal fun computeCenterPanelLayout(screenWidth: Int): CenterPanelLayout {
    val compact = screenWidth < 1360
    return CenterPanelLayout(
        portraitSize = if (compact) 46 else 52,
        rosterSlotSize = if (compact) 30 else 34,
        pagerButtonSize = if (compact) 16 else 18,
        pagerLabelWidth = if (compact) 46 else 50,
        groupSummaryWidth = if (compact) 50 else 58,
        healthBarWidth = if (compact) 90 else 100
    )
}

internal fun computeTopBarLayout(screenWidth: Int): TopBarLayout {
    val compact = screenWidth < 1360
    return TopBarLayout(
        selectionWidth = if (compact) 96 else 112,
        modeWidth = if (compact) 90 else 104,
        statusWidth = if (compact) 50 else 56
    )
}

internal fun computeSelectionSlotVisualLayout(rosterSlotSize: Int): SelectionSlotVisualLayout {
    val compact = rosterSlotSize <= 32
    return SelectionSlotVisualLayout(
        topBarHeight = if (compact) 1.25f else 1.75f,
        titleHeight = if (compact) 13f else 16f,
        hpBarWidth = if (compact) 18f else 22f,
        hpBarHeight = if (compact) 3f else 4f,
        glyphScale = if (compact) 0.78f else 0.92f,
        markerSize = if (compact) 3.5f else 4.5f
    )
}

internal fun computeWorldViewportHeightForLayout(screenWidth: Int, screenHeight: Int): Int {
    val bottomHudTop = screenHeight - computeBottomHudHeight(screenWidth, screenHeight)
    val minimapTop = gdxMiniMapBounds(screenWidth, screenHeight).top.toInt()
    return minOf(bottomHudTop, minimapTop).coerceAtLeast(240)
}

internal fun formatCommandHeaderLine(
    overlayModeLabel: String,
    productionCount: Int,
    productionPage: Int,
    productionPageSize: Int = 6
): String {
    val pageCount = ((productionCount + productionPageSize - 1) / productionPageSize).coerceAtLeast(1)
    val normalizedPage = productionPage.coerceIn(0, pageCount - 1)
    val base = "Orders · ${overlayModeLabel.uppercase()}"
    return if (productionCount > productionPageSize) {
        "$base · ${normalizedPage + 1}/$pageCount"
    } else {
        base
    }
}

internal fun formatCommandButtonText(actionId: String, fallbackLabel: String): String =
    when (actionId) {
        "attackMove" -> "Attack"
        "centerSelection" -> "Center"
        "viewF1" -> "F1 View"
        "viewF2" -> "F2 View"
        "observer" -> "Observer"
        "pause" -> "Pause"
        "help" -> "Help"
        "debug" -> "Debug"
        "build:Depot" -> "Depot"
        "build:ResourceDepot" -> "Expand"
        "build:GasDepot" -> "Refine"
        "cancelBuild" -> "Cancel B"
        "cancelTrain" -> "Cancel T"
        "cancelResearch" -> "Cancel R"
        "patrol" -> "Patrol"
        "move" -> "Move"
        "hold" -> "Hold"
        "clear" -> "Clear"
        else -> fallbackLabel
    }

internal fun resolveCommandButtonHotkey(actionId: String): String? =
    when (actionId) {
        "move" -> "M"
        "attackMove" -> "A"
        "patrol" -> "P"
        "hold" -> "H"
        "clear" -> "Esc"
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

internal fun buildHudFooterLine(hasSelection: Boolean, commandArmed: Boolean): String =
    when {
        commandArmed -> "LMB/RMB confirm  Esc cancel  MMB pan"
        hasSelection -> "LMB reselect  RMB order  drag box add"
        else -> "LMB select  RMB move  drag box select"
    }

internal fun buildMinimapHintLine(commandArmed: Boolean): String =
    if (commandArmed) {
        "armed: world click confirm"
    } else {
        "minimap drag camera"
    }

internal fun formatSelectionPageLabel(selectedCount: Int, pageIndex: Int, pageCount: Int): String =
    if (selectedCount <= 0) {
        "Pg 0/0 · 0"
    } else {
        "Pg ${pageIndex + 1}/$pageCount · $selectedCount"
    }

internal fun formatControlGroupDeckLine(groups: List<Pair<Int, Int>>): String =
    if (groups.isEmpty()) {
        "Groups idle"
    } else {
        groups.take(3).joinToString("  ") { (group, count) -> "$group:$count" }
    }

internal fun resolveQueueHeaderLine(hasProduction: Boolean, hasResearch: Boolean, underConstruction: Boolean): String =
    when {
        hasProduction && hasResearch -> "PIPELINE"
        hasResearch -> "RESEARCH"
        hasProduction -> "PRODUCTION"
        underConstruction -> "CONSTRUCT"
        else -> "QUEUE"
    }

internal fun formatQueueStatusLine(
    productionType: String?,
    productionQueueSize: Int,
    productionRemainingTicks: Int,
    researchTech: String?,
    researchQueueSize: Int,
    researchRemainingTicks: Int,
    underConstruction: Boolean,
    constructionRemainingTicks: Int?
): String {
    val parts = buildList {
        if (productionQueueSize > 0 || productionType != null) {
            add(
                buildString {
                    append("Train ")
                    append(productionType ?: "queue")
                    if (productionQueueSize > 0) append(" x$productionQueueSize")
                    if (productionRemainingTicks > 0) append(" · ${productionRemainingTicks}t")
                }
            )
        }
        if (researchQueueSize > 0 || researchTech != null) {
            add(
                buildString {
                    append("Tech ")
                    append(researchTech ?: "queue")
                    if (researchQueueSize > 0) append(" x$researchQueueSize")
                    if (researchRemainingTicks > 0) append(" · ${researchRemainingTicks}t")
                }
            )
        }
        if (underConstruction) {
            add(
                buildString {
                    append("Build")
                    constructionRemainingTicks?.takeIf { it > 0 }?.let { append(" · ${it}t") }
                }
            )
        }
    }
    return if (parts.isEmpty()) "Idle" else parts.joinToString("  |  ")
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
