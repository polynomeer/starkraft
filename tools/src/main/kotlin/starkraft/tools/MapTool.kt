package starkraft.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private val mapJson = Json { ignoreUnknownKeys = false }

@Serializable
internal data class MapPayload(
    val schema: Int,
    val id: String,
    val width: Int,
    val height: Int,
    val blockedTiles: List<MapTile> = emptyList(),
    val weightedTiles: List<WeightedTile> = emptyList(),
    val resources: List<MapResource> = emptyList(),
    val spawns: List<MapSpawn> = emptyList()
)

@Serializable
internal data class MapTile(val x: Int, val y: Int)

@Serializable
internal data class WeightedTile(val x: Int, val y: Int, val cost: Float)

@Serializable
internal data class MapResource(
    val kind: String,
    val x: Int,
    val y: Int,
    val amount: Int,
    @SerialName("yieldPerTick") val yieldPerTick: Int
)

@Serializable
internal data class MapSpawn(val faction: Int, val x: Int, val y: Int)

internal data class ValidationResult(val ok: Boolean, val errors: List<String>)

internal fun validateMap(path: Path): ValidationResult {
    val raw = Files.readString(path)
    val payload = try {
        mapJson.decodeFromString<MapPayload>(raw)
    } catch (e: Exception) {
        return ValidationResult(false, listOf("invalid json: ${e.message}"))
    }
    val errors = mutableListOf<String>()
    if (payload.schema != 1) errors += "schema must be 1"
    if (payload.id.isBlank()) errors += "id must not be blank"
    if (payload.width <= 0 || payload.height <= 0) errors += "width and height must be > 0"
    for (tile in payload.blockedTiles) {
        if (!inBounds(tile.x, tile.y, payload.width, payload.height)) {
            errors += "blocked tile out of bounds: (${tile.x},${tile.y})"
        }
    }
    for (tile in payload.weightedTiles) {
        if (!inBounds(tile.x, tile.y, payload.width, payload.height)) {
            errors += "weighted tile out of bounds: (${tile.x},${tile.y})"
        }
        if (tile.cost < 1f) {
            errors += "weighted tile cost must be >= 1: (${tile.x},${tile.y})"
        }
    }
    for (resource in payload.resources) {
        if (!inBounds(resource.x, resource.y, payload.width, payload.height)) {
            errors += "resource out of bounds: (${resource.x},${resource.y})"
        }
        if (resource.kind.isBlank()) errors += "resource kind must not be blank"
        if (resource.amount <= 0) errors += "resource amount must be > 0 at (${resource.x},${resource.y})"
        if (resource.yieldPerTick <= 0) errors += "resource yieldPerTick must be > 0 at (${resource.x},${resource.y})"
    }
    for (spawn in payload.spawns) {
        if (!inBounds(spawn.x, spawn.y, payload.width, payload.height)) {
            errors += "spawn out of bounds: faction=${spawn.faction} pos=(${spawn.x},${spawn.y})"
        }
        if (spawn.faction <= 0) errors += "spawn faction must be >= 1 at (${spawn.x},${spawn.y})"
    }
    return ValidationResult(errors.isEmpty(), errors)
}

private fun inBounds(x: Int, y: Int, width: Int, height: Int): Boolean =
    x >= 0 && y >= 0 && x < width && y < height
