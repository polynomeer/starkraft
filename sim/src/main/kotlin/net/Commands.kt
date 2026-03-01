package starkraft.sim.net

import starkraft.sim.ecs.EntityId

sealed interface Command {
    val tick: Int

    data class Move(override val tick: Int, val units: IntArray, val x: Float, val y: Float) : Command
    data class MoveFaction(override val tick: Int, val faction: Int, val x: Float, val y: Float) : Command
    data class MoveType(override val tick: Int, val typeId: String, val x: Float, val y: Float) : Command
    data class Attack(override val tick: Int, val units: IntArray, val target: EntityId) : Command
    data class AttackFaction(override val tick: Int, val faction: Int, val target: EntityId) : Command
    data class AttackType(override val tick: Int, val typeId: String, val target: EntityId) : Command
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
    data class Build(
        override val tick: Int,
        val faction: Int,
        val typeId: String,
        val tileX: Int,
        val tileY: Int,
        val width: Int,
        val height: Int,
        val hp: Int,
        val armor: Int = 0,
        val mineralCost: Int = 0,
        val gasCost: Int = 0,
        val label: String? = null,
        val labelId: Int? = null
    ) : Command
    data class Train(
        override val tick: Int,
        val buildingId: Int,
        val typeId: String,
        val buildTicks: Int,
        val mineralCost: Int = 0,
        val gasCost: Int = 0
    ) : Command
    data class CancelTrain(
        override val tick: Int,
        val buildingId: Int
    ) : Command
}
