package starkraft.sim.client

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

internal class GdxUiAssets : Disposable {
    val font = BitmapFont()
    val batch = SpriteBatch()
    val shapeRenderer = ShapeRenderer()
    private var alertSoundPath: Path? = null
    val alertSound: Sound = createAlertSound()
    private val whiteTexture = createWhiteTexture()
    private val baseDrawable = TextureRegionDrawable(TextureRegion(whiteTexture))
    val paper = Color(0.95f, 0.94f, 0.89f, 1f)
    val ink = Color(0.84f, 0.90f, 0.94f, 1f)
    val muted = Color(0.61f, 0.72f, 0.78f, 1f)
    val accent = Color(0.54f, 0.88f, 0.68f, 1f)
    val alert = Color(0.93f, 0.54f, 0.34f, 1f)
    val panel = Color(0.06f, 0.10f, 0.14f, 0.88f)
    val panelStrong = Color(0.08f, 0.13f, 0.18f, 0.94f)
    val panelSoft = Color(0.10f, 0.16f, 0.20f, 0.72f)
    val chrome = Color(0.16f, 0.29f, 0.35f, 1f)
    val chromeBright = Color(0.22f, 0.42f, 0.48f, 1f)
    val chromeMuted = Color(0.18f, 0.24f, 0.28f, 1f)

    val titleLabelStyle = Label.LabelStyle(font, paper)
    val bodyLabelStyle = Label.LabelStyle(font, ink)
    val mutedLabelStyle = Label.LabelStyle(font, muted)
    val accentLabelStyle = Label.LabelStyle(font, accent)
    val alertLabelStyle = Label.LabelStyle(font, alert)

    fun panelDrawable(color: Color): Drawable = baseDrawable.tint(color)

    fun buttonStyle(base: Color, overColor: Color = base.cpy().lerp(Color.WHITE, 0.12f)): TextButton.TextButtonStyle =
        TextButton.TextButtonStyle().apply {
            up = panelDrawable(base)
            down = panelDrawable(base.cpy().lerp(Color.BLACK, 0.18f))
            over = panelDrawable(overColor)
            checked = panelDrawable(base.cpy().lerp(Color.CYAN, 0.2f))
            disabled = panelDrawable(base.cpy().lerp(Color.DARK_GRAY, 0.55f))
            this.font = this@GdxUiAssets.font
            fontColor = paper
            disabledFontColor = Color(0.55f, 0.58f, 0.60f, 1f)
        }

    fun primaryButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chromeBright, chromeBright.cpy().lerp(accent, 0.15f))

    fun secondaryButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chrome, chrome.cpy().lerp(Color.WHITE, 0.08f))

    fun subtleButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chromeMuted, chromeMuted.cpy().lerp(Color.WHITE, 0.06f))

    override fun dispose() {
        whiteTexture.dispose()
        font.dispose()
        batch.dispose()
        shapeRenderer.dispose()
        alertSound.dispose()
        alertSoundPath?.let(Files::deleteIfExists)
    }

    private fun createWhiteTexture(): Texture {
        val pixmap =
            Pixmap(1, 1, Pixmap.Format.RGBA8888).also {
            it.setColor(Color.WHITE)
            it.fill()
        }
        return Texture(pixmap).also { pixmap.dispose() }
    }

    private fun createAlertSound(): Sound {
        val bytes = renderAlertWav()
        val tempPath = Files.createTempFile("starkraft-alert-", ".wav")
        alertSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun renderAlertWav(): ByteArray {
        val sampleRate = 22050
        val durationSeconds = 0.18f
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val pcm = ByteArrayOutputStream(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val t = i / sampleRate.toFloat()
            val envelope =
                when {
                    t < 0.02f -> t / 0.02f
                    t > durationSeconds - 0.03f -> (durationSeconds - t) / 0.03f
                    else -> 1f
                }.coerceIn(0f, 1f)
            val sample =
                (
                    kotlin.math.sin(2.0 * Math.PI * 880.0 * t) * 0.55 +
                        kotlin.math.sin(2.0 * Math.PI * 1320.0 * t) * 0.20
                ) * envelope
            val shortValue = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pcm.write(shortValue.toInt() and 0xff)
            pcm.write((shortValue.toInt() shr 8) and 0xff)
        }
        val pcmBytes = pcm.toByteArray()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcmBytes.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(pcmBytes.size)
        return header.array() + pcmBytes
    }
}
