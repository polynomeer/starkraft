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
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport

internal class MainMenuScreen(
    private val game: StarkraftGdxGame,
    private val assets: GdxUiAssets,
    private val runtime: GdxClientRuntime
) : ScreenAdapter() {
    private val stage = Stage(ScreenViewport())
    private val scenarioLabel = Label("", assets.accentLabelStyle)
    private val summaryLabel = Label("", assets.mutedLabelStyle)
    private val controlsLabel = Label("", assets.mutedLabelStyle)
    private val statusLabel = Label("ready", assets.bodyLabelStyle)
    private val statusCard = Table()
    private val scenarioHeader = Label("Scenario", assets.titleLabelStyle)
    private val presetHeader = Label("Presets", assets.titleLabelStyle)
    private val sessionHeader = Label("Session", assets.titleLabelStyle)
    private val enterMatchButton = makeButton("Enter Match") { runtime.enterMatch(game::openGameScreen) }
    private val screenFade = Table()
    private var screenFadeAlpha = 1f

    init {
        scenarioHeader.setFontScale(0.94f)
        presetHeader.setFontScale(0.94f)
        sessionHeader.setFontScale(0.94f)
        scenarioLabel.setFontScale(0.90f)
        summaryLabel.setFontScale(0.86f)
        controlsLabel.setFontScale(0.82f)
        statusLabel.setFontScale(0.88f)
        val root =
            Table().apply {
                setFillParent(true)
                background = assets.panelDrawable(Color(0.03f, 0.06f, 0.09f, 0.96f))
                pad(24f)
            }

        val hero =
            Table().apply {
                background = null
                pad(12f)
                defaults().left().padBottom(8f)
                add(
                    Table().apply {
                        background = assets.panelDrawable(Color(0.08f, 0.13f, 0.18f, 0.90f))
                        pad(1f)
                        add(
                            Table().apply {
                                background = assets.panelDrawable(Color(0.16f, 0.28f, 0.34f, 0.92f))
                                pad(4f, 10f, 4f, 10f)
                                add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.82f)) }).width(3f).expandY().fillY().padRight(6f)
                                add(Label("STARKRAFT", assets.titleLabelStyle)).left()
                            }
                        )
                    }
                ).left().row()
                add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.82f)) }).width(420f).height(2f).left().padBottom(12f).row()
                add(Label("Deterministic RTS sandbox with a live libGDX command deck.", assets.bodyLabelStyle)).width(520f).left().row()
                add(
                    statusCard.apply {
                        background = assets.panelDrawable(Color(0.08f, 0.13f, 0.18f, 0.68f))
                        pad(1f)
                        add(
                            Table().apply {
                                background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.92f))
                                pad(7f, 9f, 7f, 9f)
                                add(
                                    Table().apply {
                                        background = assets.panelDrawable(Color(0.56f, 0.88f, 0.96f, 0.82f))
                                    }
                                ).width(3f).expandY().fillY().padRight(6f)
                                add(statusLabel).left().expandX().fillX()
                            }
                        ).expandX().fillX()
                    }
                ).width(520f).left().padTop(8f).row()
            }

        val controlsPanel =
            Table().apply {
                background = assets.panelDrawable(Color(0.05f, 0.09f, 0.13f, 0.92f))
                pad(1f)
                defaults().left().pad(6f)
            }
        controlsPanel.add(
            Table().apply {
                background = assets.panelDrawable(Color(0.08f, 0.13f, 0.18f, 0.92f))
                pad(15f)
                defaults().left().pad(6f)
                add(
                    Table().apply {
                        background = assets.panelDrawable(Color(0.14f, 0.22f, 0.27f, 0.94f))
                        pad(4f, 8f, 4f, 8f)
                        add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.80f)) }).width(2f).expandY().fillY().padRight(5f)
                        add(Label("TACTICAL CONSOLE", assets.titleLabelStyle)).left()
                    }
                ).left().expandX().fillX().row()
                add(Table().apply { background = assets.panelDrawable(Color(0.22f, 0.42f, 0.48f, 0.82f)) }).height(2f).expandX().fillX().padBottom(8f).row()
                add(
                    Table().apply {
                        add(Table().apply { background = assets.panelDrawable(Color(0.58f, 0.88f, 0.96f, 0.80f)) }).width(2f).expandY().fillY().padRight(5f)
                        add(scenarioHeader).left()
                    }
                ).left().row()
                add(scenarioLabel).left().row()
                add(summaryLabel).width(340f).left().padBottom(10f).row()
                controlsLabel.setText("keys  <- -> scenario  enter match  s/l quick  a/k alt  f5 restart  esc quit")
                add(
                    Table().apply {
                        background = assets.panelDrawable(Color(0.10f, 0.16f, 0.20f, 0.88f))
                        pad(3f, 6f, 3f, 6f)
                        add(controlsLabel).width(328f).left()
                    }
                ).left().padBottom(12f).row()

                val scenarioButtons = Table()
                scenarioButtons.defaults().pad(3f)
                scenarioButtons.add(makeButton("Previous Scenario", style = assets.subtleButtonStyle()) { runtime.cycleScenario(-1); refresh() }).width(164f)
                scenarioButtons.add(makeButton("Next Scenario", style = assets.subtleButtonStyle()) { runtime.cycleScenario(1); refresh() }).width(164f)
                add(scenarioButtons).left().row()

                add(Table().apply { background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f)) }).height(1f).expandX().fillX().padTop(6f).padBottom(4f).row()
                add(
                    Table().apply {
                        add(Table().apply { background = assets.panelDrawable(Color(0.98f, 0.90f, 0.52f, 0.76f)) }).width(2f).expandY().fillY().padRight(5f)
                        add(presetHeader).left()
                    }
                ).left().row()

                val presetButtons = Table()
                presetButtons.defaults().pad(3f)
                presetButtons.add(makeButton("Save Q", style = assets.secondaryButtonStyle()) { runtime.savePreset("quick"); refresh() }).width(164f)
                presetButtons.add(makeButton("Load Q", style = assets.secondaryButtonStyle()) { runtime.loadPreset("quick"); refresh() }).width(164f).row()
                presetButtons.add(makeButton("Save A", style = assets.secondaryButtonStyle()) { runtime.savePreset("alt"); refresh() }).width(164f)
                presetButtons.add(makeButton("Load A", style = assets.secondaryButtonStyle()) { runtime.loadPreset("alt"); refresh() }).width(164f)
                add(presetButtons).left().padBottom(8f).row()

                add(Table().apply { background = assets.panelDrawable(Color(0.12f, 0.18f, 0.22f, 0.82f)) }).height(1f).expandX().fillX().padTop(4f).padBottom(4f).row()
                add(
                    Table().apply {
                        add(Table().apply { background = assets.panelDrawable(Color(0.70f, 0.98f, 0.78f, 0.76f)) }).width(2f).expandY().fillY().padRight(5f)
                        add(sessionHeader).left()
                    }
                ).left().row()
                add(enterMatchButton).width(336f).height(38f).padTop(6f).row()
                add(makeButton("Restart", style = assets.secondaryButtonStyle()) { runtime.applyScenarioAndRestart() }).width(336f).height(34f).row()
                add(makeButton("Quit", style = assets.buttonStyle(Color(0.32f, 0.16f, 0.16f, 0.98f), Color(0.40f, 0.18f, 0.18f, 0.98f))) { Gdx.app.exit() }).width(336f).height(34f).row()
            }
        ).expandX().fillX().row()

        root.add(hero).expand().fill().left().top().padRight(20f)
        root.add(controlsPanel).width(380f).right().top()
        stage.addActor(root)

        screenFade.apply {
            setFillParent(true)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            background = assets.panelDrawable(Color(0.02f, 0.03f, 0.05f, 1f))
            color.a = screenFadeAlpha
        }
        stage.addActor(screenFade)
        refresh()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, MenuInputController())
    }

    override fun render(delta: Float) {
        runtime.tick()
        refresh()
        updateScreenFade(delta)
        ScreenUtils.clear(0.03f, 0.05f, 0.07f, 1f)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }

    private fun refresh() {
        scenarioHeader.color = currentScenarioTone()
        presetHeader.color = currentPresetTone()
        sessionHeader.color = currentSessionTone()
        statusCard.background = assets.panelDrawable(currentMenuStatusCardTone())
        scenarioLabel.setText(
            if (runtime.scenarioRestartRequired()) {
                "Scenario: ${runtime.playScenario.id} (restart required)"
            } else {
                "Scenario: ${runtime.playScenario.id}"
            }
        )
        summaryLabel.setText(runtime.mainMenuSummaryLines().joinToString("\n"))
        summaryLabel.setWrap(true)
        statusLabel.setText(
            if (runtime.scenarioRestartRequired()) {
                "Scenario switch pending. Entering restarts play."
            } else {
                "Scenario live. Enter to attach."
            }
        )
        statusLabel.color = if (runtime.scenarioRestartRequired()) assets.alert else assets.ink
        enterMatchButton.setText(
            if (runtime.scenarioRestartRequired()) {
                "Restart + Enter"
            } else {
                "Enter Match"
            }
        )
    }

    private fun currentMenuStatusCardTone(): Color =
        if (runtime.scenarioRestartRequired()) {
            Color(0.22f, 0.18f, 0.08f, 0.78f)
        } else {
            Color(0.08f, 0.13f, 0.18f, 0.68f)
        }

    private fun currentScenarioTone(): Color =
        if (runtime.scenarioRestartRequired()) Color(1.00f, 0.92f, 0.62f, 0.96f) else Color(0.78f, 0.94f, 0.98f, 0.96f)

    private fun currentPresetTone(): Color = Color(1.00f, 0.92f, 0.62f, 0.96f)

    private fun currentSessionTone(): Color =
        if (runtime.scenarioRestartRequired()) Color(0.88f, 0.92f, 0.98f, 0.96f) else Color(0.74f, 0.96f, 0.84f, 0.96f)

    private fun updateScreenFade(delta: Float) {
        if (screenFadeAlpha <= 0f) {
            screenFade.isVisible = false
            return
        }
        screenFadeAlpha = (screenFadeAlpha - (delta * 1.8f)).coerceAtLeast(0f)
        screenFade.isVisible = screenFadeAlpha > 0f
        screenFade.color.a = screenFadeAlpha
    }

    private fun makeButton(
        text: String,
        style: TextButton.TextButtonStyle = assets.primaryButtonStyle(),
        onClick: () -> Unit
    ): TextButton =
        TextButton(text, style).apply {
            addListener(
                object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        onClick()
                    }
                }
            )
        }

    private inner class MenuInputController : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.LEFT -> runtime.cycleScenario(-1)
                Input.Keys.RIGHT -> runtime.cycleScenario(1)
                Input.Keys.ENTER, Input.Keys.SPACE -> runtime.enterMatch(game::openGameScreen)
                Input.Keys.S -> runtime.savePreset("quick")
                Input.Keys.L -> runtime.loadPreset("quick")
                Input.Keys.A -> runtime.savePreset("alt")
                Input.Keys.K -> runtime.loadPreset("alt")
                Input.Keys.F5 -> runtime.applyScenarioAndRestart()
                Input.Keys.ESCAPE -> Gdx.app.exit()
                else -> return false
            }
            refresh()
            return true
        }
    }
}
