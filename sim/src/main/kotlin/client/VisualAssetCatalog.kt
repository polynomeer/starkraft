package starkraft.sim.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

@Serializable
internal data class SpriteAssetRef(
    val asset: String,
    val scale: Float = 1f,
    val tint: String? = null,
    val teamTintStrength: Float = 0f,
    val anchorX: Float? = null,
    val anchorY: Float? = null,
    val widthTiles: Float? = null
)

@Serializable
internal data class TerrainVisualDefs(
    val base: SpriteAssetRef? = null,
    val blocked: SpriteAssetRef? = null,
    val occupied: SpriteAssetRef? = null
)

@Serializable
internal data class VisualAssetManifest(
    val units: Map<String, SpriteAssetRef> = emptyMap(),
    val unitArchetypes: Map<String, SpriteAssetRef> = emptyMap(),
    val buildings: Map<String, SpriteAssetRef> = emptyMap(),
    val buildingArchetypes: Map<String, SpriteAssetRef> = emptyMap(),
    val resources: Map<String, SpriteAssetRef> = emptyMap(),
    val terrain: TerrainVisualDefs = TerrainVisualDefs()
)

/** A structure's position is its footprint's centre; this returns the footprint's first tile on one axis. */
internal fun structureOriginTile(center: Float, footprintSize: Int): Int = floor(center - (footprintSize * 0.5f)).toInt()

/** Where a sprite lands on screen. Coordinates are y-down, matching the world camera. */
internal data class SpriteScreenRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Places the sprite so its anchor (fractions of the image, from the top-left) sits on the given screen point. */
internal fun spriteScreenRect(
    screenX: Float,
    screenY: Float,
    tileSize: Float,
    ref: LoadedSpriteAsset
): SpriteScreenRect = spriteScreenRect(
    screenX, screenY, tileSize, ref.anchorX, ref.anchorY, ref.widthTiles, ref.region.regionWidth, ref.region.regionHeight
)

internal fun spriteScreenRect(
    screenX: Float,
    screenY: Float,
    tileSize: Float,
    anchorX: Float,
    anchorY: Float,
    widthTiles: Float,
    imageWidth: Int,
    imageHeight: Int
): SpriteScreenRect {
    val width = widthTiles * tileSize
    val height = width * imageHeight / imageWidth.coerceAtLeast(1)
    return SpriteScreenRect(screenX - (width * anchorX), screenY - (height * anchorY), width, height)
}

internal class VisualAssetCatalog(
    private val manifest: VisualAssetManifest
) {
    fun entity(typeId: String, archetype: String?, isStructure: Boolean): SpriteAssetRef? =
        if (isStructure) {
            manifest.buildings[typeId] ?: archetype?.let { manifest.buildingArchetypes[it] }
        } else {
            manifest.units[typeId] ?: archetype?.let { manifest.unitArchetypes[it] }
        }

    fun resource(kind: String): SpriteAssetRef? = manifest.resources[kind]

    fun terrainBase(): SpriteAssetRef? = manifest.terrain.base

    fun blockedTile(): SpriteAssetRef? = manifest.terrain.blocked

    fun occupiedTile(): SpriteAssetRef? = manifest.terrain.occupied
}

private val visualAssetsJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

internal fun parseVisualAssetCatalog(payload: String): VisualAssetCatalog =
    VisualAssetCatalog(visualAssetsJson.decodeFromString(VisualAssetManifest.serializer(), payload))

private val defaultVisualAssetCatalogValue: VisualAssetCatalog by lazy {
    val visualsResource = object {}.javaClass.getResource("/data/visuals.json")
    if (visualsResource == null) {
        VisualAssetCatalog(VisualAssetManifest())
    } else {
        parseVisualAssetCatalog(visualsResource.readText())
    }
}

internal fun defaultVisualAssetCatalog(): VisualAssetCatalog = defaultVisualAssetCatalogValue
