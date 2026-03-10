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

    init {
        val root =
            Table().apply {
                setFillParent(true)
                background = assets.panelDrawable(Color(0.05f, 0.10f, 0.14f, 0.95f))
                defaults().pad(8f)
            }
        root.add(Label("STARKRAFT", assets.titleLabelStyle)).padBottom(16f).row()
        root.add(Label("libGDX client on top of the existing deterministic sim", assets.mutedLabelStyle)).padBottom(24f).row()
        root.add(scenarioLabel).row()
        root.add(summaryLabel).padBottom(12f).row()
        controlsLabel.setText("keys: left/right scenario  enter play  s/l quick  a/k alt  esc quit")
        root.add(controlsLabel).padBottom(12f).row()
        root.add(makeButton("Previous Scenario") { runtime.cycleScenario(-1); refresh() }).width(280f).row()
        root.add(makeButton("Next Scenario") { runtime.cycleScenario(1); refresh() }).width(280f).row()
        root.add(makeButton("Save Quick Preset") { runtime.savePreset("quick"); refresh() }).width(280f).row()
        root.add(makeButton("Load Quick Preset") { runtime.loadPreset("quick"); refresh() }).width(280f).row()
        root.add(makeButton("Save Alt Preset") { runtime.savePreset("alt"); refresh() }).width(280f).row()
        root.add(makeButton("Load Alt Preset") { runtime.loadPreset("alt"); refresh() }).width(280f).row()
        root.add(makeButton("Enter Match") { runtime.enterMatch(game::openGameScreen) }).width(280f).padTop(16f).row()
        root.add(makeButton("Restart Match") { runtime.applyScenarioAndRestart() }).width(280f).row()
        root.add(makeButton("Quit") { Gdx.app.exit() }).width(280f).row()
        stage.addActor(root)
        refresh()
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, MenuInputController())
    }

    override fun render(delta: Float) {
        runtime.tick()
        refresh()
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
        scenarioLabel.setText("Scenario: ${runtime.playScenario.id}")
        summaryLabel.setText(runtime.mainMenuSummaryLines().joinToString("\n"))
    }

    private fun makeButton(text: String, onClick: () -> Unit): TextButton =
        TextButton(text, assets.buttonStyle(Color(0.17f, 0.30f, 0.38f, 1f))).apply {
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
