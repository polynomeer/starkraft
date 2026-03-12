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
    private val enterMatchButton = makeButton("Enter Match") { runtime.enterMatch(game::openGameScreen) }
    private val screenFade = Table()
    private var screenFadeAlpha = 1f

    init {
        val root =
            Table().apply {
                setFillParent(true)
                background = assets.panelDrawable(Color(0.03f, 0.06f, 0.09f, 0.96f))
                pad(28f)
            }

        val hero =
            Table().apply {
                background = assets.panelDrawable(Color(0.08f, 0.13f, 0.18f, 0.94f))
                pad(24f)
                defaults().left().padBottom(10f)
                add(Label("STARKRAFT", assets.titleLabelStyle)).left().row()
                add(Label("Deterministic RTS sandbox with a live libGDX command deck.", assets.bodyLabelStyle)).width(480f).left().row()
                add(statusLabel).left().row()
            }

        val controlsPanel =
            Table().apply {
                background = assets.panelDrawable(Color(0.08f, 0.11f, 0.15f, 0.90f))
                pad(18f)
                defaults().left().pad(6f)
            }
        controlsPanel.add(Label("Scenario", assets.titleLabelStyle)).left().row()
        controlsPanel.add(scenarioLabel).left().row()
        controlsPanel.add(summaryLabel).width(340f).left().padBottom(10f).row()
        controlsLabel.setText("keys: left/right scenario  enter match  s/l quick  a/k alt  f5 restart  esc quit")
        controlsPanel.add(controlsLabel).width(340f).left().padBottom(12f).row()

        val scenarioButtons = Table()
        scenarioButtons.defaults().pad(4f)
        scenarioButtons.add(makeButton("Previous Scenario", style = assets.subtleButtonStyle()) { runtime.cycleScenario(-1); refresh() }).width(164f)
        scenarioButtons.add(makeButton("Next Scenario", style = assets.subtleButtonStyle()) { runtime.cycleScenario(1); refresh() }).width(164f)
        controlsPanel.add(scenarioButtons).left().row()

        val presetButtons = Table()
        presetButtons.defaults().pad(4f)
        presetButtons.add(makeButton("Save Quick", style = assets.secondaryButtonStyle()) { runtime.savePreset("quick"); refresh() }).width(164f)
        presetButtons.add(makeButton("Load Quick", style = assets.secondaryButtonStyle()) { runtime.loadPreset("quick"); refresh() }).width(164f).row()
        presetButtons.add(makeButton("Save Alt", style = assets.secondaryButtonStyle()) { runtime.savePreset("alt"); refresh() }).width(164f)
        presetButtons.add(makeButton("Load Alt", style = assets.secondaryButtonStyle()) { runtime.loadPreset("alt"); refresh() }).width(164f)
        controlsPanel.add(presetButtons).left().padBottom(8f).row()

        controlsPanel.add(enterMatchButton).width(336f).height(42f).padTop(8f).row()
        controlsPanel.add(makeButton("Restart Match", style = assets.subtleButtonStyle()) { runtime.applyScenarioAndRestart() }).width(336f).row()
        controlsPanel.add(makeButton("Quit", style = assets.subtleButtonStyle()) { Gdx.app.exit() }).width(336f).row()

        root.add(hero).expand().fill().left().top().padRight(16f)
        root.add(controlsPanel).width(380f).right().top()
        stage.addActor(root)

        screenFade.apply {
            setFillParent(true)
            touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
            background = assets.panelDrawable(Color(0f, 0f, 0f, 1f))
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
                "Pending scenario switch. Entering the match will restart the play session."
            } else {
                "Live scenario ready. Enter match to attach to the current play session."
            }
        )
        enterMatchButton.setText(
            if (runtime.scenarioRestartRequired()) {
                "Restart And Enter Match"
            } else {
                "Enter Match"
            }
        )
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
