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
import java.util.LinkedHashMap

internal data class PresentationColorTokens(
    val appBackground: Color,
    val worldFrame: Color,
    val panel: Color,
    val panelAlt: Color,
    val panelStrong: Color,
    val panelSoft: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val selection: Color,
    val selectionSoft: Color,
    val command: Color,
    val commandSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val research: Color,
    val production: Color,
    val build: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderActive: Color,
    val borderWarning: Color,
    val glowSelection: Color,
    val glowCommand: Color,
    val glowAlert: Color,
    val terrainA: Color,
    val terrainB: Color,
    val terrainRidge: Color,
    val terrainDust: Color,
    val terrainMetal: Color,
    val fog: Color,
    val shroud: Color,
    val friendlyFaction: Color,
    val enemyFaction: Color,
    val neutralFaction: Color
)

internal data class PresentationMotionTokens(
    val fastMs: Long,
    val uiMs: Long,
    val confirmMs: Long,
    val alertMs: Long,
    val effectMs: Long
)

internal data class PresentationAudioTokens(
    val uiGain: Float,
    val commandGain: Float,
    val combatGain: Float,
    val alertGain: Float,
    val structureDeathGain: Float
)

internal data class PresentationTheme(
    val colors: PresentationColorTokens,
    val motion: PresentationMotionTokens,
    val audio: PresentationAudioTokens
)

internal fun defaultPresentationTheme(): PresentationTheme =
    PresentationTheme(
        colors =
            PresentationColorTokens(
                appBackground = Color(0.043f, 0.075f, 0.106f, 1f),
                worldFrame = Color.valueOf("0F172A"),
                panel = Color.valueOf("192134"),
                panelAlt = Color.valueOf("0F1F2B"),
                panelStrong = Color.valueOf("102430"),
                panelSoft = Color(0.10f, 0.16f, 0.20f, 0.72f),
                scrim = Color(0.008f, 0.031f, 0.055f, 0.72f),
                textPrimary = Color.WHITE.cpy(),
                textSecondary = Color.valueOf("D5DEE8"),
                textMuted = Color.valueOf("94A3B8"),
                textDisabled = Color.valueOf("64748B"),
                textInverse = Color.valueOf("0F172A"),
                selection = Color.valueOf("22C55E"),
                selectionSoft = Color.valueOf("86EFAC"),
                command = Color.valueOf("38BDF8"),
                commandSoft = Color.valueOf("7DD3FC"),
                warning = Color.valueOf("F59E0B"),
                warningSoft = Color.valueOf("FCD34D"),
                danger = Color.valueOf("DC2626"),
                dangerSoft = Color.valueOf("F87171"),
                research = Color.valueOf("22D3EE"),
                production = Color.valueOf("FBBF24"),
                build = Color.valueOf("34D399"),
                borderSubtle = Color(1f, 1f, 1f, 0.08f),
                borderDefault = Color(0.49f, 0.83f, 0.99f, 0.28f),
                borderActive = Color(0.13f, 0.77f, 0.37f, 0.56f),
                borderWarning = Color(0.96f, 0.62f, 0.04f, 0.42f),
                glowSelection = Color(0.13f, 0.77f, 0.37f, 0.20f),
                glowCommand = Color(0.22f, 0.74f, 0.97f, 0.16f),
                glowAlert = Color(0.96f, 0.62f, 0.04f, 0.18f),
                terrainA = Color(0.10f, 0.15f, 0.11f, 1f),
                terrainB = Color(0.12f, 0.18f, 0.13f, 1f),
                terrainRidge = Color(0.18f, 0.22f, 0.15f, 1f),
                terrainDust = Color(0.22f, 0.19f, 0.13f, 1f),
                terrainMetal = Color(0.15f, 0.18f, 0.21f, 1f),
                fog = Color(0.07f, 0.12f, 0.14f, 0.18f),
                shroud = Color(0.05f, 0.09f, 0.11f, 0.32f),
                friendlyFaction = Color(0.30f, 0.60f, 1.00f, 1f),
                enemyFaction = Color(0.90f, 0.36f, 0.28f, 1f),
                neutralFaction = Color(0.78f, 0.69f, 0.42f, 1f)
            ),
        motion =
            PresentationMotionTokens(
                fastMs = 120L,
                uiMs = 180L,
                confirmMs = 240L,
                alertMs = 320L,
                effectMs = 420L
            ),
        audio =
            PresentationAudioTokens(
                uiGain = 0.42f,
                commandGain = 0.48f,
                combatGain = 0.46f,
                alertGain = 0.60f,
                structureDeathGain = 0.60f
            )
    )

internal class GdxUiAssets : Disposable {
    val font = BitmapFont()
    val batch = SpriteBatch()
    val shapeRenderer = ShapeRenderer()
    val visualCatalog = defaultVisualAssetCatalog()
    val spriteAssets = GdxSpriteAssets(visualCatalog)
    val theme = defaultPresentationTheme()
    private var alertSoundPath: Path? = null
    private var attackSoundPath: Path? = null
    private var moveSoundPath: Path? = null
    private var buildSoundPath: Path? = null
    private var invalidSoundPath: Path? = null
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
    val moveSound: Sound = createMoveSound()
    val buildSound: Sound = createBuildSound()
    val invalidSound: Sound = createInvalidSound()
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
    val paper = theme.colors.textPrimary.cpy()
    val ink = theme.colors.textSecondary.cpy()
    val muted = theme.colors.textMuted.cpy()
    val accent = theme.colors.selection.cpy()
    val alert = theme.colors.warning.cpy()
    val panel = theme.colors.panel.cpy().also { it.a = 0.88f }
    val panelStrong = theme.colors.panelStrong.cpy().also { it.a = 0.94f }
    val panelSoft = theme.colors.panelSoft.cpy()
    val chrome = Color(0.16f, 0.29f, 0.35f, 1f)
    val chromeBright = theme.colors.command.cpy().lerp(theme.colors.panelStrong, 0.42f)
    val chromeMuted = theme.colors.panelAlt.cpy().lerp(theme.colors.textMuted, 0.12f)
    private val panelDrawableCache = LinkedHashMap<Int, Drawable>(256)

    val titleLabelStyle = Label.LabelStyle(font, paper)
    val bodyLabelStyle = Label.LabelStyle(font, ink)
    val mutedLabelStyle = Label.LabelStyle(font, muted)
    val accentLabelStyle = Label.LabelStyle(font, accent)
    val alertLabelStyle = Label.LabelStyle(font, alert)

    fun panelDrawable(color: Color): Drawable =
        panelDrawableCache.getOrPut(color.toIntBits()) { baseDrawable.tint(Color(color)) }

    fun buttonStyle(base: Color, overColor: Color = base.cpy().lerp(Color.WHITE, 0.12f)): TextButton.TextButtonStyle =
        TextButton.TextButtonStyle().apply {
            up = panelDrawable(base)
            down = panelDrawable(base.cpy().lerp(Color.BLACK, 0.18f))
            over = panelDrawable(overColor)
            checked = panelDrawable(base.cpy().lerp(theme.colors.command, 0.2f))
            disabled = panelDrawable(base.cpy().lerp(Color.DARK_GRAY, 0.55f))
            this.font = this@GdxUiAssets.font
            fontColor = paper
            disabledFontColor = theme.colors.textDisabled.cpy()
        }

    fun primaryButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chromeBright, chromeBright.cpy().lerp(accent, 0.10f))

    fun secondaryButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chrome, chrome.cpy().lerp(Color.WHITE, 0.05f))

    fun subtleButtonStyle(): TextButton.TextButtonStyle = buttonStyle(chromeMuted, chromeMuted.cpy().lerp(Color.WHITE, 0.04f))

    override fun dispose() {
        panelDrawableCache.clear()
        whiteTexture.dispose()
        font.dispose()
        batch.dispose()
        shapeRenderer.dispose()
        spriteAssets.dispose()
        alertSound.dispose()
        attackSound.dispose()
        moveSound.dispose()
        buildSound.dispose()
        invalidSound.dispose()
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
        moveSoundPath?.let(Files::deleteIfExists)
        buildSoundPath?.let(Files::deleteIfExists)
        invalidSoundPath?.let(Files::deleteIfExists)
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

    private fun createMoveSound(): Sound {
        val bytes = renderToneWav(primaryHz = 180.0, secondaryHz = 360.0, durationSeconds = 0.08f, primaryMix = 0.46, secondaryMix = 0.18)
        val tempPath = Files.createTempFile("starkraft-move-", ".wav")
        moveSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createBuildSound(): Sound {
        val bytes = renderToneWav(primaryHz = 260.0, secondaryHz = 520.0, durationSeconds = 0.10f, primaryMix = 0.42, secondaryMix = 0.18)
        val tempPath = Files.createTempFile("starkraft-build-", ".wav")
        buildSoundPath = tempPath
        tempPath.toFile().deleteOnExit()
        val handle = Gdx.files.absolute(tempPath.toString())
        handle.writeBytes(bytes, false)
        return Gdx.audio.newSound(handle)
    }

    private fun createInvalidSound(): Sound {
        val bytes =
            renderSweepWav(
                startHz = 240.0,
                endHz = 110.0,
                accentHz = 280.0,
                durationSeconds = 0.09f,
                sweepMix = 0.34,
                accentMix = 0.16
            )
        val tempPath = Files.createTempFile("starkraft-invalid-", ".wav")
        invalidSoundPath = tempPath
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
