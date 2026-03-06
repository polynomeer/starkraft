package starkraft.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

private val dataJson = Json { ignoreUnknownKeys = false }

internal fun validateDataDir(dir: Path): ValidationResult {
    val files =
        mapOf(
            "units" to findDataFile(dir, "units"),
            "buildings" to findDataFile(dir, "buildings"),
            "techs" to findDataFile(dir, "techs"),
            "weapons" to findDataFile(dir, "weapons")
        )
    val errors = mutableListOf<String>()
    val parsed = mutableMapOf<String, List<JsonObject>>()
    for ((name, path) in files) {
        if (path == null) {
            errors += "missing file for '$name' (expected .json/.yaml/.yml)"
            continue
        }
        parsed[name] = parseListFile(path, "$name.list", errors)
    }
    if (errors.isNotEmpty()) return ValidationResult(false, errors)

    val unitDefs = parsed.getValue("units")
    val buildingDefs = parsed.getValue("buildings")
    val techDefs = parsed.getValue("techs")
    val weaponDefs = parsed.getValue("weapons")
    val buildingIds = collectIds(buildingDefs, "buildings", errors)
    val weaponIds = collectIds(weaponDefs, "weapons", errors)
    collectIds(unitDefs, "units", errors)
    collectIds(techDefs, "techs", errors)

    for (unit in unitDefs) {
        val unitId = unit["id"]?.jsonPrimitive?.contentOrNull ?: "<unknown>"
        val weaponId = unit["weaponId"]?.jsonPrimitive?.contentOrNull
        if (weaponId != null && weaponId !in weaponIds) {
            errors += "units[$unitId].weaponId references missing weapon '$weaponId'"
        }
        validateProducerTypes(unit, "units[$unitId]", buildingIds, errors)
    }

    for (tech in techDefs) {
        val techId = tech["id"]?.jsonPrimitive?.contentOrNull ?: "<unknown>"
        validateProducerTypes(tech, "techs[$techId]", buildingIds, errors)
    }

    for (building in buildingDefs) {
        val id = building["id"]?.jsonPrimitive?.contentOrNull ?: "<unknown>"
        val supportsDropoff = building["supportsDropoff"]?.jsonPrimitive?.booleanOrNull ?: false
        val kinds = stringList(building["dropoffResourceKinds"])
        if (supportsDropoff && kinds.isEmpty()) {
            errors += "buildings[$id] supportsDropoff=true requires dropoffResourceKinds"
        }
        for (kind in kinds) {
            if (kind != "minerals" && kind != "gas") {
                errors += "buildings[$id] dropoffResourceKinds contains invalid value '$kind'"
            }
        }
    }
    return ValidationResult(errors.isEmpty(), errors)
}

private fun findDataFile(dir: Path, baseName: String): Path? {
    val candidates = listOf("$baseName.json", "$baseName.yaml", "$baseName.yml")
    for (candidate in candidates) {
        val path = dir.resolve(candidate)
        if (Files.exists(path)) return path
    }
    return null
}

private fun parseListFile(path: Path, context: String, errors: MutableList<String>): List<JsonObject> {
    return try {
        val root = readStructuredElement(path).jsonObject
        val listElement = root["list"]
        if (listElement !is JsonArray) {
            errors += "$context must be an array"
            emptyList()
        } else {
            listElement.mapNotNull {
                if (it is JsonObject) it else {
                    errors += "$context items must be objects"
                    null
                }
            }
        }
    } catch (e: Exception) {
        errors += "invalid json in $path: ${e.message}"
        emptyList()
    }
}

private fun collectIds(items: List<JsonObject>, context: String, errors: MutableList<String>): Set<String> {
    val ids = LinkedHashSet<String>(items.size)
    for (item in items) {
        val id = item["id"]?.jsonPrimitive?.contentOrNull
        if (id.isNullOrBlank()) {
            errors += "$context entry missing non-empty id"
            continue
        }
        if (!ids.add(id)) {
            errors += "$context duplicate id '$id'"
        }
    }
    return ids
}

private fun validateProducerTypes(
    item: JsonObject,
    context: String,
    buildingIds: Set<String>,
    errors: MutableList<String>
) {
    val producerTypes = stringList(item["producerTypes"])
    for (producer in producerTypes) {
        if (producer !in buildingIds) {
            errors += "$context.producerTypes references missing building '$producer'"
        }
    }
}

private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
}
