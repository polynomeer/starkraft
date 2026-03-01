package starkraft.sim.data

import kotlinx.serialization.Serializable

@Serializable
data class UnitDef(
    val id: String,
    val hp: Int,
    val armor: Int = 0,
    val speed: Float = 0.06f,
    val weaponId: String? = null,
    val mineralCost: Int = 0,
    val gasCost: Int = 0,
    val buildTicks: Int = 0,
    val footprintWidth: Int = 0,
    val footprintHeight: Int = 0,
    val producerTypes: List<String> = emptyList(),
)

@Serializable
data class WeaponDef(
    val id: String,
    val damage: Int,
    val range: Float,
    val cooldownTicks: Int,
)

@Serializable
data class UnitDefs(val list: List<UnitDef>)

@Serializable
data class WeaponDefs(val list: List<WeaponDef>)
