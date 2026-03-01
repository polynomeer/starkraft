package starkraft.sim.ecs

data class Transform(var x: Float, var y: Float, var dir: Float = 0f)

data class Motion(var vx: Float = 0f, var vy: Float = 0f)

data class UnitTag(val faction: Int, val typeId: String)

data class Health(var hp: Int, var maxHp: Int, var armor: Int = 0)

data class WeaponRef(val id: String, var cooldownTicks: Int = 0)

data class OrderQueue(val items: ArrayDeque<Order> = ArrayDeque())

sealed interface Order {
    data class Move(val tx: Float, val ty: Float) : Order;
    data class Attack(val target: EntityId) : Order
}

data class Vision(var range: Float)

data class PathFollow(var nodes: IntArray, var length: Int, var index: Int = 0)

data class RepathCooldown(var ticks: Int = 0)

data class BuildingFootprint(val tileX: Int, val tileY: Int, val width: Int, val height: Int)

data class RallyPoint(val x: Float, val y: Float)

data class ResourceStockpile(var minerals: Int = 0, var gas: Int = 0)

data class ProductionJob(
    val typeId: String,
    var remainingTicks: Int,
    val mineralCost: Int = 0,
    val gasCost: Int = 0
)

data class ProductionQueue(val items: ArrayDeque<ProductionJob> = ArrayDeque())

data class StuckTracker(
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var ticks: Int = 0
)
