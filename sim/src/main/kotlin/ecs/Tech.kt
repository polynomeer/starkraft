package starkraft.sim.ecs

internal fun missingRequiredBuildings(world: World, faction: Int, requiredBuildingTypes: List<String>): List<String> {
    if (requiredBuildingTypes.isEmpty()) return emptyList()
    val present = HashSet<String>()
    for (id in world.footprints.keys) {
        if (world.constructionSites.containsKey(id)) continue
        val tag = world.tags[id] ?: continue
        if (tag.faction != faction) continue
        present.add(tag.typeId)
    }
    if (present.isEmpty()) return requiredBuildingTypes
    return requiredBuildingTypes.filterNot(present::contains)
}

internal fun missingRequiredResearch(world: World, faction: Int, requiredResearchIds: List<String>): List<String> {
    if (requiredResearchIds.isEmpty()) return emptyList()
    val unlocked = world.unlockedTechs(faction)
    if (unlocked.isEmpty()) return requiredResearchIds
    return requiredResearchIds.filterNot(unlocked::contains)
}
