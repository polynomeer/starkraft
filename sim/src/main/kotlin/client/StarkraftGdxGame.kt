package starkraft.sim.client

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.utils.Disposable

internal class StarkraftGdxGame(
    private val session: ClientSession,
    private val launchConfig: GraphicalClientLaunchConfig,
    private val onRestartRequested: () -> Unit
) : Game(), Disposable {
    private val assets = GdxUiAssets()
    private val runtime =
        GdxClientRuntime(
            session = session,
            controlPath = launchConfig.controlPath,
            scenarioPath = launchConfig.scenarioPath,
            playRoot = launchConfig.playRoot,
            requestRestart = {
                onRestartRequested()
                Gdx.app.exit()
            }
        )
    private var gameScreen: Screen? = null

    override fun create() {
        setScreen(MainMenuScreen(this, assets, runtime))
    }

    fun openGameScreen() {
        val existing = gameScreen
        if (existing != null) {
            setScreen(existing)
            return
        }
        val created = GameScreen(this, assets, runtime)
        gameScreen = created
        setScreen(created)
    }

    fun openMainMenu() {
        setScreen(MainMenuScreen(this, assets, runtime))
    }

    override fun dispose() {
        screen?.dispose()
        gameScreen?.takeIf { it !== screen }?.dispose()
        assets.dispose()
        session.close()
    }
}
