package starkraft.sim.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SpriteAssetRef(
    val asset: String,
    val scale: Float = 1f,
    val tint: String? = null,
    val teamTintStrength: Float = 0f
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
