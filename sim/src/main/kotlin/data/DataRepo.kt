package starkraft.sim.data

import kotlinx.serialization.json.Json

data class BuildSpec(
    val typeId: String,
    val hp: Int,
    val armor: Int,
    val footprintWidth: Int,
    val footprintHeight: Int,
    val mineralCost: Int,
    val gasCost: Int
)

data class TrainSpec(
    val typeId: String,
    val buildTicks: Int,
    val mineralCost: Int,
    val gasCost: Int,
    val producerTypes: List<String>
)

class DataRepo(unitsJson: String, weaponsJson: String) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val units = json.decodeFromString(UnitDefs.serializer(), unitsJson).list.associateBy { it.id }
    private val weapons = json.decodeFromString(WeaponDefs.serializer(), weaponsJson).list.associateBy { it.id }

    fun unit(id: String) = units.getValue(id)
    fun weapon(id: String) = weapons.getValue(id)

    fun buildSpec(id: String): BuildSpec? {
        val unit = units[id] ?: return null
        if (unit.footprintWidth <= 0 || unit.footprintHeight <= 0) return null
        return BuildSpec(
            typeId = unit.id,
            hp = unit.hp,
            armor = unit.armor,
            footprintWidth = unit.footprintWidth,
            footprintHeight = unit.footprintHeight,
            mineralCost = unit.mineralCost,
            gasCost = unit.gasCost
        )
    }

    fun trainSpec(id: String): TrainSpec? {
        val unit = units[id] ?: return null
        if (unit.buildTicks <= 0) return null
        return TrainSpec(
            typeId = unit.id,
            buildTicks = unit.buildTicks,
            mineralCost = unit.mineralCost,
            gasCost = unit.gasCost,
            producerTypes = unit.producerTypes
        )
    }
}
