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
    private var attackSoundPath: Path? = null
    private var rangedCombatSoundPath: Path? = null
    private var meleeCombatSoundPath: Path? = null
    private var marineCombatSoundPath: Path? = null
    private var zerglingCombatSoundPath: Path? = null
    private var deathSoundPath: Path? = null
    private var marineDeathSoundPath: Path? = null
    private var zerglingDeathSoundPath: Path? = null
    private var structureDeathSoundPath: Path? = null
    private var structureDeathTailSoundPath: Path? = null
    private var completeSoundPath: Path? = null
    val alertSound: Sound = createAlertSound()
    val attackSound: Sound = createAttackSound()
    val rangedCombatSound: Sound = createRangedCombatSound()
    val meleeCombatSound: Sound = createMeleeCombatSound()
    val marineCombatSound: Sound = createMarineCombatSound()
    val zerglingCombatSound: Sound = createZerglingCombatSound()
    val deathSound: Sound = createDeathSound()
    val marineDeathSound: Sound = createMarineDeathSound()
    val zerglingDeathSound: Sound = createZerglingDeathSound()
    val structureDeathSound: Sound = createStructureDeathSound()
    val structureDeathTailSound: Sound = createStructureDeathTailSound()
    val completeSound: Sound = createCompleteSound()
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
        attackSound.dispose()
        rangedCombatSound.dispose()
        meleeCombatSound.dispose()
        marineCombatSound.dispose()
        zerglingCombatSound.dispose()
        deathSound.dispose()
        marineDeathSound.dispose()
        zerglingDeathSound.dispose()
        structureDeathSound.dispose()
        structureDeathTailSound.dispose()
        completeSound.dispose()
        alertSoundPath?.let(Files::deleteIfExists)
        attackSoundPath?.let(Files::deleteIfExists)
        rangedCombatSoundPath?.let(Files::deleteIfExists)
        meleeCombatSoundPath?.let(Files::deleteIfExists)
        marineCombatSoundPath?.let(Files::deleteIfExists)
        zerglingCombatSoundPath?.let(Files::deleteIfExists)
        deathSoundPath?.let(Files::deleteIfExists)
        marineDeathSoundPath?.let(Files::deleteIfExists)
        zerglingDeathSoundPath?.let(Files::deleteIfExists)
        structureDeathSoundPath?.let(Files::deleteIfExists)
        structureDeathTailSoundPath?.let(Files::deleteIfExists)
        completeSoundPath?.let(Files::deleteIfExists)
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
        val bytes = renderToneWav(primaryHz = 880.0, secondaryHz = 1320.0, durationSeconds = 0.18f, primaryMix = 0.55, secondaryMix = 0.20)
        val tempPath = Files.createTempFile("starkraft-alert-", ".wav")
        alertSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createCompleteSound(): Sound {
        val bytes = renderToneWav(primaryHz = 660.0, secondaryHz = 990.0, durationSeconds = 0.22f, primaryMix = 0.42, secondaryMix = 0.18)
        val tempPath = Files.createTempFile("starkraft-complete-", ".wav")
        completeSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createAttackSound(): Sound {
        val bytes = renderToneWav(primaryHz = 220.0, secondaryHz = 440.0, durationSeconds = 0.10f, primaryMix = 0.58, secondaryMix = 0.22)
        val tempPath = Files.createTempFile("starkraft-attack-", ".wav")
        attackSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createRangedCombatSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 520.0,
                endHz = 220.0,
                accentHz = 1240.0,
                durationSeconds = 0.12f,
                sweepMix = 0.46,
                accentMix = 0.18
            )
        val tempPath = Files.createTempFile("starkraft-combat-ranged-", ".wav")
        rangedCombatSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createMeleeCombatSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 760.0,
                endHz = 180.0,
                accentHz = 1660.0,
                durationSeconds = 0.10f,
                sweepMix = 0.36,
                accentMix = 0.24
            )
        val tempPath = Files.createTempFile("starkraft-combat-melee-", ".wav")
        meleeCombatSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createMarineCombatSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 880.0,
                endHz = 260.0,
                accentHz = 1820.0,
                durationSeconds = 0.08f,
                sweepMix = 0.30,
                accentMix = 0.30
            )
        val tempPath = Files.createTempFile("starkraft-combat-marine-", ".wav")
        marineCombatSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createZerglingCombatSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 1180.0,
                endHz = 140.0,
                accentHz = 740.0,
                durationSeconds = 0.09f,
                sweepMix = 0.34,
                accentMix = 0.18
            )
        val tempPath = Files.createTempFile("starkraft-combat-zergling-", ".wav")
        zerglingCombatSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createDeathSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 240.0,
                endHz = 72.0,
                accentHz = 480.0,
                durationSeconds = 0.24f,
                sweepMix = 0.50,
                accentMix = 0.14
            )
        val tempPath = Files.createTempFile("starkraft-death-", ".wav")
        deathSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createMarineDeathSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 320.0,
                endHz = 90.0,
                accentHz = 620.0,
                durationSeconds = 0.18f,
                sweepMix = 0.42,
                accentMix = 0.16
            )
        val tempPath = Files.createTempFile("starkraft-death-marine-", ".wav")
        marineDeathSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createZerglingDeathSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 540.0,
                endHz = 120.0,
                accentHz = 840.0,
                durationSeconds = 0.16f,
                sweepMix = 0.34,
                accentMix = 0.24
            )
        val tempPath = Files.createTempFile("starkraft-death-zergling-", ".wav")
        zerglingDeathSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createStructureDeathSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 180.0,
                endHz = 48.0,
                accentHz = 92.0,
                durationSeconds = 0.34f,
                sweepMix = 0.54,
                accentMix = 0.18
            )
        val tempPath = Files.createTempFile("starkraft-death-structure-", ".wav")
        structureDeathSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createStructureDeathTailSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 120.0,
                endHz = 34.0,
                accentHz = 58.0,
                durationSeconds = 0.42f,
                sweepMix = 0.48,
                accentMix = 0.12
            )
        val tempPath = Files.createTempFile("starkraft-death-structure-tail-", ".wav")
        structureDeathTailSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun renderToneWav(primaryHz: Double, secondaryHz: Double, durationSeconds: Float, primaryMix: Double, secondaryMix: Double): ByteArray {
        val sampleRate = 22050
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
                    kotlin.math.sin(2.0 * Math.PI * primaryHz * t) * primaryMix +
                        kotlin.math.sin(2.0 * Math.PI * secondaryHz * t) * secondaryMix
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

    private fun renderSweepWav(
        startHz: Double,
        endHz: Double,
        accentHz: Double,
        durationSeconds: Float,
        sweepMix: Double,
        accentMix: Double
    ): ByteArray {
        val sampleRate = 22050
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val pcm = ByteArrayOutputStream(sampleCount * 2)
        var phase = 0.0
        var accentPhase = 0.0
        for (i in 0 until sampleCount) {
            val t = i / sampleRate.toFloat()
            val progress = (t / durationSeconds).coerceIn(0f, 1f)
            val hz = startHz + ((endHz - startHz) * progress)
            phase += (2.0 * Math.PI * hz) / sampleRate
            accentPhase += (2.0 * Math.PI * accentHz) / sampleRate
            val envelope =
                when {
                    t < 0.01f -> t / 0.01f
                    t > durationSeconds - 0.05f -> (durationSeconds - t) / 0.05f
                    else -> 1f
                }.coerceIn(0f, 1f)
            val grit = kotlin.math.sin(accentPhase) * kotlin.math.sin(accentPhase * 0.31)
            val sample =
                (
                    kotlin.math.sin(phase) * sweepMix +
                        kotlin.math.sin(phase * 1.7) * (sweepMix * 0.38) +
                        grit * accentMix
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
