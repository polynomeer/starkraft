package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

internal class GdxSpriteAssets(
    val catalog: VisualAssetCatalog
) : Disposable {
    private val texturesByPath = LinkedHashMap<String, Texture>()
    private val missingPaths = HashSet<String>()
    private val loadedByRef = HashMap<SpriteAssetRef, LoadedSpriteAsset?>()

    fun entity(entity: EntitySnapshot): LoadedSpriteAsset? =
        catalog.entity(
            typeId = entity.typeId,
            archetype = entity.archetype,
            isStructure = entity.footprintWidth != null && entity.footprintHeight != null
        )?.let(::resolve)

    fun resource(kind: String): LoadedSpriteAsset? = catalog.resource(kind)?.let(::resolve)

    fun terrainBase(): LoadedSpriteAsset? = catalog.terrainBase()?.let(::resolve)

    fun blockedTile(): LoadedSpriteAsset? = catalog.blockedTile()?.let(::resolve)

    fun occupiedTile(): LoadedSpriteAsset? = catalog.occupiedTile()?.let(::resolve)

    private fun resolve(ref: SpriteAssetRef): LoadedSpriteAsset? {
        if (ref in loadedByRef) return loadedByRef[ref]
        return load(ref).also { loadedByRef[ref] = it }
    }

    private fun load(ref: SpriteAssetRef): LoadedSpriteAsset? {
        val texture = texture(ref.asset) ?: return null
        // An anchor is measured on the whole image, so anchored sprites keep their transparent margin.
        val anchored = ref.anchorX != null && ref.anchorY != null
        val region = if (anchored) TextureRegion(texture) else region(ref.asset, texture)
        return LoadedSpriteAsset(
            region = region,
            scale = ref.scale.coerceAtLeast(0.01f),
            tint = ref.tint?.let(::parseTint),
            teamTintStrength = ref.teamTintStrength.coerceIn(0f, 1f),
            anchorX = ref.anchorX ?: 0.5f,
            anchorY = ref.anchorY ?: 1f,
            widthTiles = ref.widthTiles ?: ref.scale.coerceAtLeast(0.01f)
        )
    }

    private fun texture(path: String): Texture? {
        texturesByPath[path]?.let { return it }
        if (path in missingPaths) return null
        val handle = Gdx.files.classpath(path)
        if (!handle.exists()) {
            missingPaths += path
            return null
        }
        return Texture(handle, true).also {
            // Sprites are drawn far below their source size, so they need mipmaps to stay clean.
            it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            texturesByPath[path] = it
        }
    }

    private fun region(path: String, texture: Texture): TextureRegion {
        val handle = Gdx.files.classpath(path)
        val pixmap = Pixmap(handle)
        return try {
            val bounds = opaqueBounds(pixmap)
            if (bounds == null) {
                TextureRegion(texture)
            } else {
                TextureRegion(texture, bounds.left, bounds.top, bounds.width, bounds.height)
            }
        } finally {
            pixmap.dispose()
        }
    }

    private fun opaqueBounds(pixmap: Pixmap): SpriteBounds? {
        var minX = pixmap.width
        var minY = pixmap.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until pixmap.height) {
            for (x in 0 until pixmap.width) {
                val alpha = pixmap.getPixel(x, y) and 0xff
                if (alpha <= 8) continue
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) return null
        return SpriteBounds(
            left = minX,
            top = minY,
            width = (maxX - minX + 1).coerceAtLeast(1),
            height = (maxY - minY + 1).coerceAtLeast(1)
        )
    }

    private fun parseTint(raw: String): Color {
        val normalized =
            if (raw.startsWith("#")) {
                raw.substring(1)
            } else {
                raw
            }
        return Color.valueOf(normalized)
    }

    override fun dispose() {
        texturesByPath.values.forEach(Texture::dispose)
        texturesByPath.clear()
        loadedByRef.clear()
        missingPaths.clear()
    }
}

internal data class LoadedSpriteAsset(
    val region: TextureRegion,
    val scale: Float,
    val tint: Color?,
    val teamTintStrength: Float,
    val anchorX: Float,
    val anchorY: Float,
    val widthTiles: Float
)

private data class SpriteBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)
