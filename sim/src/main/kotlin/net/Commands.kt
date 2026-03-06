package starkraft.sim.net

import starkraft.sim.ecs.EntityId

sealed interface Command {
    val tick: Int

    data class Move(override val tick: Int, val units: IntArray, val x: Float, val y: Float) : Command
    data class MoveFaction(override val tick: Int, val faction: Int, val x: Float, val y: Float) : Command
    data class MoveType(override val tick: Int, val typeId: String, val x: Float, val y: Float) : Command
    data class MoveArchetype(override val tick: Int, val archetype: String, val x: Float, val y: Float) : Command
    data class Patrol(override val tick: Int, val units: IntArray, val x: Float, val y: Float) : Command
    data class PatrolFaction(override val tick: Int, val faction: Int, val x: Float, val y: Float) : Command
    data class PatrolType(override val tick: Int, val typeId: String, val x: Float, val y: Float) : Command
    data class PatrolArchetype(override val tick: Int, val archetype: String, val x: Float, val y: Float) : Command
    data class AttackMove(override val tick: Int, val units: IntArray, val x: Float, val y: Float) : Command
    data class AttackMoveFaction(override val tick: Int, val faction: Int, val x: Float, val y: Float) : Command
    data class AttackMoveType(override val tick: Int, val typeId: String, val x: Float, val y: Float) : Command
    data class AttackMoveArchetype(override val tick: Int, val archetype: String, val x: Float, val y: Float) : Command
    data class Hold(override val tick: Int, val units: IntArray) : Command
    data class HoldFaction(override val tick: Int, val faction: Int) : Command
    data class HoldType(override val tick: Int, val typeId: String) : Command
    data class HoldArchetype(override val tick: Int, val archetype: String) : Command
    data class Attack(override val tick: Int, val units: IntArray, val target: EntityId) : Command
    data class AttackFaction(override val tick: Int, val faction: Int, val target: EntityId) : Command
    data class AttackType(override val tick: Int, val typeId: String, val target: EntityId) : Command
    data class AttackArchetype(override val tick: Int, val archetype: String, val target: EntityId) : Command
    data class Harvest(override val tick: Int, val units: IntArray, val target: EntityId) : Command
    data class HarvestFaction(override val tick: Int, val faction: Int, val target: EntityId) : Command
    data class HarvestType(override val tick: Int, val typeId: String, val target: EntityId) : Command
    data class HarvestArchetype(override val tick: Int, val archetype: String, val target: EntityId) : Command
    data class Construct(override val tick: Int, val units: IntArray, val target: EntityId) : Command
    data class ConstructFaction(override val tick: Int, val faction: Int, val target: EntityId) : Command
    data class ConstructType(override val tick: Int, val typeId: String, val target: EntityId) : Command
    data class ConstructArchetype(override val tick: Int, val archetype: String, val target: EntityId) : Command
    data class SpawnNode(
        override val tick: Int,
        val kind: String,
        val x: Float,
        val y: Float,
        val amount: Int,
        val yieldPerTick: Int = 0,
        val label: String? = null,
        val labelId: Int? = null
    ) : Command
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
    data class CancelBuild(
        override val tick: Int,
        val buildingId: Int
    ) : Command
    data class Research(
        override val tick: Int,
        val buildingId: Int,
        val techId: String,
        val buildTicks: Int,
        val mineralCost: Int = 0,
        val gasCost: Int = 0
    ) : Command
    data class CancelResearch(
        override val tick: Int,
        val buildingId: Int
    ) : Command
    data class Rally(
        override val tick: Int,
        val buildingId: Int,
        val x: Float,
        val y: Float
    ) : Command
    data class SurrenderFaction(
        override val tick: Int,
        val faction: Int
    ) : Command
}
