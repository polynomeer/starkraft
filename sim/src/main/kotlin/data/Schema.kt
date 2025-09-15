package starkraft.sim.data

import kotlinx.serialization.Serializable

@Serializable
data class UnitDef(
    val id: String,
    val hp: Int,
    val armor: Int = 0,
    val speed: Float = 0.06f,
    val weaponId: String? = null,
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
