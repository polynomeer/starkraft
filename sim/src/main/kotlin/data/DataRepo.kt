package starkraft.sim.data

import kotlinx.serialization.json.Json

class DataRepo(unitsJson: String, weaponsJson: String) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val units = json.decodeFromString(UnitDefs.serializer(), unitsJson).list.associateBy { it.id }
    private val weapons = json.decodeFromString(WeaponDefs.serializer(), weaponsJson).list.associateBy { it.id }

    fun unit(id: String) = units.getValue(id)
    fun weapon(id: String) = weapons.getValue(id)
}
