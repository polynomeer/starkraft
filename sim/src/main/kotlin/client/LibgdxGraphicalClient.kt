package starkraft.sim.client

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import java.io.File
import kotlin.system.exitProcess

internal fun launchLibgdxClient(
    session: ClientSession,
    config: GraphicalClientLaunchConfig,
    onRestartRequested: () -> Unit
) {
    val game = StarkraftGdxGame(session, config, onRestartRequested)
    val applicationConfig =
        Lwjgl3ApplicationConfiguration().apply {
            setTitle("Starkraft")
            setWindowedMode(1440, 900)
            useVsync(true)
            setForegroundFPS(60)
        }
    Lwjgl3Application(game, applicationConfig)
}

internal object Lwjgl3StartupHelper {
    private const val RESTARTED_PROPERTY = "starkraft.lwjgl3.restarted"

    fun startNewJvmIfRequired(mainClass: String, args: Array<String>) {
        if (!shouldRelaunchOnFirstThread()) return
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val classpath = System.getProperty("java.class.path")
        val command =
            buildList {
                add(javaBin)
                add("-XstartOnFirstThread")
                add("-D$RESTARTED_PROPERTY=true")
                add("-cp")
                add(classpath)
                add(mainClass)
                addAll(args)
            }
        val exitCode = ProcessBuilder(command).inheritIO().start().waitFor()
        exitProcess(exitCode)
    }

    private fun shouldRelaunchOnFirstThread(): Boolean {
        if (!System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)) return false
        return System.getProperty(RESTARTED_PROPERTY) != "true"
    }
}
