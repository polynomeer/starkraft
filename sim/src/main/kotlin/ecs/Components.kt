package starkraft.sim.ecs

data class Transform(var x: Float, var y: Float, var dir: Float = 0f)

data class Motion(var vx: Float = 0f, var vy: Float = 0f)

data class UnitTag(val faction: Int, val typeId: String)

data class Health(var hp: Int, var maxHp: Int, var armor: Int = 0)

data class WeaponRef(val id: String, var cooldownTicks: Int = 0)

data class OrderQueue(val items: ArrayDeque<Order> = ArrayDeque())

sealed interface Order {
    data class Move(val tx: Float, val ty: Float) : Order;
    data class AttackMove(val tx: Float, val ty: Float) : Order;
    data class Patrol(val ax: Float, val ay: Float, val bx: Float, val by: Float, var toB: Boolean = true) : Order
    data object Hold : Order
    data class Attack(val target: EntityId) : Order
}

data class Vision(var range: Float)

data class PathFollow(var nodes: IntArray, var length: Int, var index: Int = 0, var goalX: Int = -1, var goalY: Int = -1)

data class RepathCooldown(var ticks: Int = 0)

data class BuildingFootprint(
    val tileX: Int,
    val tileY: Int,
    val width: Int,
    val height: Int,
    val clearance: Int = 0
)

data class ConstructionSite(
    var remainingTicks: Int,
    val totalTicks: Int,
    val maxHp: Int
)

data class RallyPoint(val x: Float, val y: Float)

data class ResourceStockpile(var minerals: Int = 0, var gas: Int = 0)

data class ResourceNode(
    val kind: String = KIND_MINERALS,
    var remaining: Int,
    val yieldPerTick: Int = 0
) {
    companion object {
        const val KIND_MINERALS = "minerals"
        const val KIND_GAS = "gas"
    }
}

data class Harvester(
    var targetNodeId: EntityId,
    val harvestPerTick: Int = 1,
    val range: Float = 1.25f,
    val cargoCapacity: Int = Int.MAX_VALUE,
    val dropoffRange: Float = 1.5f,
    var cargoKind: String? = null,
    var cargoAmount: Int = 0,
    var returnTargetId: EntityId = -1
)

data class ProductionJob(
    val typeId: String,
    var remainingTicks: Int,
    val mineralCost: Int = 0,
    val gasCost: Int = 0
)

data class ProductionQueue(val items: ArrayDeque<ProductionJob> = ArrayDeque())

data class ResearchJob(
    val techId: String,
    var remainingTicks: Int,
    val mineralCost: Int = 0,
    val gasCost: Int = 0
)

data class ResearchQueue(val items: ArrayDeque<ResearchJob> = ArrayDeque())

data class StuckTracker(
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var ticks: Int = 0
)
