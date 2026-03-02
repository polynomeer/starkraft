package starkraft.sim.data

import kotlinx.serialization.json.Json

data class BuildSpec(
    val typeId: String,
    val archetype: String,
    val hp: Int,
    val armor: Int,
    val footprintWidth: Int,
    val footprintHeight: Int,
    val placementClearance: Int,
    val supportsTraining: Boolean,
    val supportsRally: Boolean,
    val productionQueueLimit: Int,
    val rallyOffsetX: Float,
    val rallyOffsetY: Float,
    val mineralCost: Int,
    val gasCost: Int
)

data class TrainSpec(
    val typeId: String,
    val archetype: String,
    val buildTicks: Int,
    val mineralCost: Int,
    val gasCost: Int,
    val producerTypes: List<String>
)

class DataRepo(
    private val unitsJson: String,
    private val weaponsJson: String,
    private val buildingsJson: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val units = json.decodeFromString(UnitDefs.serializer(), this.unitsJson).list.associateBy { it.id }
    private val weapons = json.decodeFromString(WeaponDefs.serializer(), this.weaponsJson).list.associateBy { it.id }
    private val buildings =
        this.buildingsJson
            ?.let { json.decodeFromString(BuildingDefs.serializer(), it).list.associateBy { def -> def.id } }
            ?: emptyMap()

    fun unit(id: String) = units.getValue(id)
    fun weapon(id: String) = weapons.getValue(id)

    fun buildSpec(id: String): BuildSpec? {
        val building = buildings[id] ?: return null
        return BuildSpec(
            typeId = building.id,
            archetype = building.archetype,
            hp = building.hp,
            armor = building.armor,
            footprintWidth = building.footprintWidth,
            footprintHeight = building.footprintHeight,
            placementClearance = building.placementClearance,
            supportsTraining = building.supportsTraining,
            supportsRally = building.supportsRally,
            productionQueueLimit = building.productionQueueLimit,
            rallyOffsetX = building.rallyOffsetX,
            rallyOffsetY = building.rallyOffsetY,
            mineralCost = building.mineralCost,
            gasCost = building.gasCost
        )
    }

    fun trainSpec(id: String): TrainSpec? {
        val unit = units[id] ?: return null
        if (unit.buildTicks <= 0) return null
        return TrainSpec(
            typeId = unit.id,
            archetype = unit.archetype,
            buildTicks = unit.buildTicks,
            mineralCost = unit.mineralCost,
            gasCost = unit.gasCost,
            producerTypes = unit.producerTypes
        )
    }
}
