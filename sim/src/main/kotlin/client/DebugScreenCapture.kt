package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import java.io.File

/**
 * Saves one frame of the running game as a PNG, for checking art changes without a person at the screen.
 *
 * Set STARKRAFT_CAPTURE_PATH to enable. The game screen opens right away, skipping the menu, and the frame
 * is saved after STARKRAFT_CAPTURE_AFTER_MS (default 8000). The client exits after saving.
 */
internal class DebugScreenCapture(
    private val path: String,
    private val afterMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val startedAt = nowMillis()
    private var done = false

    fun shouldOpenGame(): Boolean = !done

    internal fun isDue(): Boolean = !done && nowMillis() - startedAt >= afterMillis

    fun captureIfDue() {
        if (!isDue()) return
        done = true
        val width = Gdx.graphics.backBufferWidth
        val height = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true)
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        try {
            pixmap.pixels.clear()
            pixmap.pixels.put(pixels)
            pixmap.pixels.clear()
            PixmapIO.writePNG(FileHandle(File(path)), pixmap)
        } finally {
            pixmap.dispose()
        }
        Gdx.app.exit()
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): DebugScreenCapture? {
            val path = env["STARKRAFT_CAPTURE_PATH"]?.takeIf { it.isNotBlank() } ?: return null
            val after = env["STARKRAFT_CAPTURE_AFTER_MS"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 8000L
            return DebugScreenCapture(path, after)
        }
    }
}
