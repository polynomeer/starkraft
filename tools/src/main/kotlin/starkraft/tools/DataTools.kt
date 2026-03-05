package starkraft.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import starkraft.sim.data.BuildingDefs
import starkraft.sim.data.TechDefs
import starkraft.sim.data.UnitDefs
import starkraft.sim.data.WeaponDefs
import java.nio.file.Files
import java.nio.file.Path

data class DataValidationInput(
    val unitsPath: Path,
    val weaponsPath: Path,
    val buildingsPath: Path,
    val techsPath: Path
)

data class DataValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

private val dataJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

fun validateGameData(input: DataValidationInput): DataValidationResult {
    val units = dataJson.decodeFromString(UnitDefs.serializer(), loadJsonOrYaml(input.unitsPath)).list
    val weapons = dataJson.decodeFromString(WeaponDefs.serializer(), loadJsonOrYaml(input.weaponsPath)).list
    val buildings = dataJson.decodeFromString(BuildingDefs.serializer(), loadJsonOrYaml(input.buildingsPath)).list
    val techs = dataJson.decodeFromString(TechDefs.serializer(), loadJsonOrYaml(input.techsPath)).list

    val errors = ArrayList<String>()
    validateDuplicates(units.map { it.id }, "unit", errors)
    validateDuplicates(weapons.map { it.id }, "weapon", errors)
    validateDuplicates(buildings.map { it.id }, "building", errors)
    validateDuplicates(techs.map { it.id }, "tech", errors)

    val weaponIds = weapons.mapTo(HashSet(weapons.size * 2 + 1)) { it.id }
    val buildingById = buildings.associateBy { it.id }
    val techIds = techs.mapTo(HashSet(techs.size * 2 + 1)) { it.id }

    for (unit in units) {
        if (unit.id.isBlank()) errors.add("unit id must not be blank")
        if (unit.hp <= 0) errors.add("unit '${unit.id}' hp must be > 0")
        if (unit.speed < 0f) errors.add("unit '${unit.id}' speed must be >= 0")
        if (unit.weaponId != null && unit.weaponId !in weaponIds) {
            errors.add("unit '${unit.id}' references missing weapon '${unit.weaponId}'")
        }
        validateRequiredRefs("unit '${unit.id}'", unit.requiredBuildingTypes, unit.requiredResearchIds, buildingById, techIds, errors)
        for (producerType in unit.producerTypes) {
            val building = buildingById[producerType]
            if (building == null) {
                errors.add("unit '${unit.id}' producer building '$producerType' is missing")
            } else if (!building.supportsTraining) {
                errors.add("unit '${unit.id}' producer building '$producerType' does not support training")
            }
        }
    }

    for (building in buildings) {
        if (building.id.isBlank()) errors.add("building id must not be blank")
        if (building.hp <= 0) errors.add("building '${building.id}' hp must be > 0")
        if (building.footprintWidth <= 0 || building.footprintHeight <= 0) {
            errors.add("building '${building.id}' footprint must be > 0")
        }
        if (building.productionQueueLimit < 0) errors.add("building '${building.id}' productionQueueLimit must be >= 0")
        validateRequiredRefs("building '${building.id}'", building.requiredBuildingTypes, building.requiredResearchIds, buildingById, techIds, errors)
    }

    for (tech in techs) {
        if (tech.id.isBlank()) errors.add("tech id must not be blank")
        if (tech.buildTicks <= 0) errors.add("tech '${tech.id}' buildTicks must be > 0")
        validateRequiredRefs("tech '${tech.id}'", tech.requiredBuildingTypes, tech.requiredResearchIds, buildingById, techIds, errors)
        for (producerType in tech.producerTypes) {
            val building = buildingById[producerType]
            if (building == null) {
                errors.add("tech '${tech.id}' producer building '$producerType' is missing")
            } else if (!building.supportsResearch) {
                errors.add("tech '${tech.id}' producer building '$producerType' does not support research")
            }
        }
    }

    return DataValidationResult(valid = errors.isEmpty(), errors = errors)
}

private fun validateRequiredRefs(
    owner: String,
    requiredBuildingTypes: List<String>,
    requiredResearchIds: List<String>,
    buildingById: Map<String, *>,
    techIds: Set<String>,
    errors: MutableList<String>
) {
    for (buildingType in requiredBuildingTypes) {
        if (buildingType !in buildingById) {
            errors.add("$owner requires missing building '$buildingType'")
        }
    }
    for (researchId in requiredResearchIds) {
        if (researchId !in techIds) {
            errors.add("$owner requires missing tech '$researchId'")
        }
    }
}

private fun validateDuplicates(ids: List<String>, kind: String, errors: MutableList<String>) {
    val seen = HashSet<String>(ids.size * 2 + 1)
    for (id in ids) {
        if (!seen.add(id)) errors.add("duplicate $kind id '$id'")
    }
}

private fun loadJsonOrYaml(path: Path): String {
    val payload = Files.readString(path)
    val lower = path.fileName.toString().lowercase()
    if (lower.endsWith(".json")) return payload
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
        val yaml = Load(LoadSettings.builder().build()).loadFromString(payload)
        val element = toJsonElement(yaml)
        return dataJson.encodeToString(JsonElement.serializer(), element)
    }
    error("Unsupported data file extension for '$path' (expected .json/.yaml/.yml)")
}

private fun toJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toDouble())
        is Map<*, *> -> {
            val mapped = LinkedHashMap<String, JsonElement>(value.size)
            for ((k, v) in value) {
                mapped[k?.toString() ?: "null"] = toJsonElement(v)
            }
            JsonObject(mapped)
        }
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
}
