package starkraft.sim.net

import starkraft.sim.ecs.EntityId

sealed interface Command {
    val tick: Int

    data class Move(override val tick: Int, val units: IntArray, val x: Float, val y: Float) : Command
    data class Attack(override val tick: Int, val units: IntArray, val target: EntityId) : Command
    data class Spawn(
        override val tick: Int,
        val faction: Int,
        val typeId: String,
        val x: Float,
        val y: Float,
        val vision: Float? = null,
        val label: String? = null,
        val labelId: Int? = null
    ) : Command
}
