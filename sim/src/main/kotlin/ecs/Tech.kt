package starkraft.sim.ecs

internal fun missingRequiredBuildings(world: World, faction: Int, requiredBuildingTypes: List<String>): List<String> {
    if (requiredBuildingTypes.isEmpty()) return emptyList()
    val present = HashSet<String>()
    for (id in world.footprints.keys) {
        val tag = world.tags[id] ?: continue
        if (tag.faction != faction) continue
        present.add(tag.typeId)
    }
    if (present.isEmpty()) return requiredBuildingTypes
    return requiredBuildingTypes.filterNot(present::contains)
}

