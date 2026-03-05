package starkraft.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

private const val MAP_SCHEMA_VERSION = 1

@Serializable
data class ToolMapFile(
    val schema: Int = MAP_SCHEMA_VERSION,
    val id: String = "generated-map",
    val width: Int,
    val height: Int,
    val blockedTiles: List<ToolTile> = emptyList(),
    val weightedTiles: List<ToolWeightedTile> = emptyList(),
    val resources: List<ToolMapResourceNode> = emptyList(),
    val spawns: List<ToolMapSpawnPoint> = emptyList()
)

@Serializable
data class ToolTile(val x: Int, val y: Int)

@Serializable
data class ToolWeightedTile(val x: Int, val y: Int, val cost: Float)

@Serializable
data class ToolMapResourceNode(
    val kind: String,
    val x: Int,
    val y: Int,
    val amount: Int,
    val yieldPerTick: Int
)

@Serializable
data class ToolMapSpawnPoint(
    val faction: Int,
    val x: Int,
    val y: Int
)

data class MapValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

private val mapJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun loadToolMap(path: Path): ToolMapFile {
    val payload = Files.readString(path)
    return if (payload.trimStart().startsWith("{")) {
        mapJson.decodeFromString(ToolMapFile.serializer(), payload)
    } else {
        error("Invalid map payload. Expected JSON object")
    }
}

fun validateToolMap(map: ToolMapFile): MapValidationResult {
    val errors = ArrayList<String>()
    if (map.schema != MAP_SCHEMA_VERSION) {
        errors.add("schema must be $MAP_SCHEMA_VERSION (actual ${map.schema})")
    }
    if (map.id.isBlank()) errors.add("id must not be blank")
    if (map.width <= 0) errors.add("width must be > 0")
    if (map.height <= 0) errors.add("height must be > 0")

    val blocked = HashSet<Pair<Int, Int>>(map.blockedTiles.size * 2 + 1)
    for (tile in map.blockedTiles) {
        if (!inside(tile.x, tile.y, map.width, map.height)) {
            errors.add("blocked tile out of bounds: (${tile.x},${tile.y})")
        }
        val key = tile.x to tile.y
        if (!blocked.add(key)) errors.add("duplicate blocked tile: (${tile.x},${tile.y})")
    }

    val weighted = HashSet<Pair<Int, Int>>(map.weightedTiles.size * 2 + 1)
    for (tile in map.weightedTiles) {
        if (!inside(tile.x, tile.y, map.width, map.height)) {
            errors.add("weighted tile out of bounds: (${tile.x},${tile.y})")
        }
        if (tile.cost < 1f) errors.add("weighted tile cost must be >= 1: (${tile.x},${tile.y})=${tile.cost}")
        val key = tile.x to tile.y
        if (!weighted.add(key)) errors.add("duplicate weighted tile: (${tile.x},${tile.y})")
    }

    for (resource in map.resources) {
        if (!inside(resource.x, resource.y, map.width, map.height)) {
            errors.add("resource out of bounds: (${resource.x},${resource.y})")
        }
        if (resource.kind.isBlank()) errors.add("resource kind must not be blank at (${resource.x},${resource.y})")
        if (resource.amount <= 0) errors.add("resource amount must be > 0 at (${resource.x},${resource.y})")
        if (resource.yieldPerTick <= 0) errors.add("resource yieldPerTick must be > 0 at (${resource.x},${resource.y})")
    }

    for (spawn in map.spawns) {
        if (!inside(spawn.x, spawn.y, map.width, map.height)) {
            errors.add("spawn out of bounds: faction=${spawn.faction} (${spawn.x},${spawn.y})")
        }
        if (spawn.faction <= 0) errors.add("spawn faction must be > 0 at (${spawn.x},${spawn.y})")
    }

    return MapValidationResult(
        valid = errors.isEmpty(),
        errors = errors
    )
}

fun generateToolMap(
    width: Int,
    height: Int,
    seed: Long,
    blockedPercent: Int,
    weightedPercent: Int,
    id: String = "generated-${width}x$height-$seed"
): ToolMapFile {
    require(width > 0) { "width must be > 0" }
    require(height > 0) { "height must be > 0" }
    require(blockedPercent in 0..50) { "blockedPercent must be in 0..50" }
    require(weightedPercent in 0..50) { "weightedPercent must be in 0..50" }
    val rng = Random(seed)
    val blockedTiles = ArrayList<ToolTile>()
    val weightedTiles = ArrayList<ToolWeightedTile>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) continue
            val roll = rng.nextInt(100)
            if (roll < blockedPercent) {
                blockedTiles.add(ToolTile(x, y))
            } else if (roll < blockedPercent + weightedPercent) {
                val cost = 2f + (rng.nextInt(4) * 0.5f)
                weightedTiles.add(ToolWeightedTile(x, y, cost))
            }
        }
    }
    val spawns =
        listOf(
            ToolMapSpawnPoint(faction = 1, x = 2, y = 2),
            ToolMapSpawnPoint(faction = 2, x = width - 3, y = height - 3)
        )
    return ToolMapFile(
        schema = MAP_SCHEMA_VERSION,
        id = id,
        width = width,
        height = height,
        blockedTiles = blockedTiles,
        weightedTiles = weightedTiles,
        resources = listOf(
            ToolMapResourceNode("MineralField", width / 2, height / 2, amount = 1500, yieldPerTick = 1)
        ),
        spawns = spawns
    )
}

fun saveToolMap(path: Path, map: ToolMapFile) {
    Files.createDirectories(path.toAbsolutePath().normalize().parent)
    Files.writeString(path, mapJson.encodeToString(ToolMapFile.serializer(), map))
}

private fun inside(x: Int, y: Int, width: Int, height: Int): Boolean =
    x >= 0 && y >= 0 && x < width && y < height
