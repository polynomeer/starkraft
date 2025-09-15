package starkraft.sim.ecs

class FactionIndex(private val world: World) {
    private val byFaction = mutableMapOf<Int, MutableSet<EntityId>>()

    fun add(id: EntityId, faction: Int) {
        byFaction.getOrPut(faction) { mutableSetOf() }.add(id)
    }

    fun remove(id: EntityId, faction: Int) {
        byFaction[faction]?.remove(id)
    }

    /** All enemies for a given faction */
    fun enemiesOf(faction: Int): Sequence<EntityId> =
        byFaction.asSequence()
            .filter { (f, _) -> f != faction }
            .flatMap { (_, ids) -> ids.asSequence() }
}
