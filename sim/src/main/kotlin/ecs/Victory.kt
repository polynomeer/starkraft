package starkraft.sim.ecs

class VictorySystem(private val world: World) {
    private val nonCombatTypeIds = setOf("MineralField", "GasGeyser")

    fun tick() {
        if (world.matchEnded) return
        var survivorFaction = -1
        var aliveFactions = 0
        for ((id, tr) in world.transforms) {
            val hp = world.healths[id]?.hp ?: 0
            if (hp <= 0) continue
            val tag = world.tags[id] ?: continue
            if (tag.faction <= 0) continue
            if (tag.typeId in nonCombatTypeIds) continue
            if (survivorFaction == -1) {
                survivorFaction = tag.faction
                aliveFactions = 1
            } else if (survivorFaction != tag.faction) {
                aliveFactions = 2
                break
            }
            if (tr.x.isNaN() || tr.y.isNaN()) {
                // Guard against corrupted state producing invalid movement values.
                world.matchEnded = true
                world.winnerFaction = null
                return
            }
        }

        if (aliveFactions <= 1) {
            world.matchEnded = true
            world.winnerFaction = survivorFaction.takeIf { it > 0 }
        }
    }
}
