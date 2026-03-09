package starkraft.sim.client

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

internal class GdxUiAssets : Disposable {
    val font = BitmapFont()
    val batch = SpriteBatch()
    val shapeRenderer = ShapeRenderer()
    private val whiteTexture = createWhiteTexture()
    private val baseDrawable = TextureRegionDrawable(TextureRegion(whiteTexture))

    val titleLabelStyle = Label.LabelStyle(font, Color(0.96f, 0.95f, 0.88f, 1f))
    val bodyLabelStyle = Label.LabelStyle(font, Color(0.88f, 0.92f, 0.95f, 1f))
    val mutedLabelStyle = Label.LabelStyle(font, Color(0.68f, 0.76f, 0.82f, 1f))
    val accentLabelStyle = Label.LabelStyle(font, Color(0.58f, 0.88f, 0.70f, 1f))

    fun panelDrawable(color: Color): Drawable = baseDrawable.tint(color)

    fun buttonStyle(base: Color, overColor: Color = base.cpy().lerp(Color.WHITE, 0.12f)): TextButton.TextButtonStyle =
        TextButton.TextButtonStyle().apply {
            up = panelDrawable(base)
            down = panelDrawable(base.cpy().lerp(Color.BLACK, 0.18f))
            over = panelDrawable(overColor)
            checked = panelDrawable(base.cpy().lerp(Color.CYAN, 0.2f))
            disabled = panelDrawable(base.cpy().lerp(Color.DARK_GRAY, 0.55f))
            this.font = this@GdxUiAssets.font
            fontColor = Color(0.97f, 0.98f, 0.98f, 1f)
            disabledFontColor = Color(0.55f, 0.58f, 0.60f, 1f)
        }

    override fun dispose() {
        whiteTexture.dispose()
        font.dispose()
        batch.dispose()
        shapeRenderer.dispose()
    }

    private fun createWhiteTexture(): Texture {
        val pixmap =
            Pixmap(1, 1, Pixmap.Format.RGBA8888).also {
            it.setColor(Color.WHITE)
            it.fill()
        }
        return Texture(pixmap).also { pixmap.dispose() }
    }
}
