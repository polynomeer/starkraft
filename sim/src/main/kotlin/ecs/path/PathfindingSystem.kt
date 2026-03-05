package starkraft.sim.ecs.path

import kotlin.math.floor
import kotlin.math.abs
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.BuildingFootprint
import starkraft.sim.ecs.PathFollow
import starkraft.sim.ecs.World

class PathPool(private val maxSize: Int, private val capacity: Int = 64) {
    private val pool = ArrayDeque<IntArray>(capacity)

    fun obtain(): IntArray {
        return if (pool.isEmpty()) IntArray(maxSize) else pool.removeFirst()
    }

    fun recycle(nodes: IntArray) {
        if (pool.size < capacity && nodes.size == maxSize) {
            pool.addLast(nodes)
        }
    }
}

class PathRequestQueue(private val maxSize: Int, private val maxEnqueuesPerTick: Int) {
    private val ids = IntArray(maxSize)
    var count: Int = 0
        private set
    private var enqueuedThisTick: Int = 0

    val size: Int
        get() = count

    fun enqueue(id: Int): Boolean {
        if (enqueuedThisTick >= maxEnqueuesPerTick) return false
        if (count >= maxSize) return false
        ids[count++] = id
        enqueuedThisTick++
        return true
    }

    fun idAt(i: Int): Int = ids[i]

    fun setId(i: Int, id: Int) {
        ids[i] = id
    }

    fun reset(newCount: Int) {
        count = newCount
    }

    fun beginTick() {
        enqueuedThisTick = 0
    }
}

class PathfindingSystem(
    private val world: World,
    private val pathfinder: Pathfinder,
    private val pool: PathPool,
    private val queue: PathRequestQueue,
    private val nodesBudgetPerTick: Int
) {
    private var assignedEntityIds = IntArray(16)
    private var assignedLengths = IntArray(16)
    private var assignedGoalXs = IntArray(16)
    private var assignedGoalYs = IntArray(16)
    var lastTickRequests: Int = 0
        private set
    var lastTickSolved: Int = 0
        private set
    var lastTickAvgPathLen: Float = 0f
        private set
    var lastTickAssignedCount: Int = 0
        private set
    var lastTickNodesUsed: Int = 0
        private set
    var lastTickBudgetExhausted: Int = 0
        private set
    var lastTickCarryOver: Int = 0
        private set
    val nodeBudgetPerTick: Int
        get() = nodesBudgetPerTick

    fun tick() {
        var remainingBudget = nodesBudgetPerTick
        var nodesUsed = 0
        var budgetExhausted = 0
        var newCount = 0
        var solved = 0
        var totalLen = 0
        lastTickAssignedCount = 0
        lastTickRequests = queue.count

        for (i in 0 until queue.count) {
            val id = queue.idAt(i)
            val orders = world.orders[id]?.items ?: continue
            val first = orders.firstOrNull() ?: continue
            if (remainingBudget <= 0) {
                queue.setId(newCount++, id)
                continue
            }

            val tr = world.transforms[id] ?: continue
            val sx = floor(tr.x).toInt()
            val sy = floor(tr.y).toInt()
            val (gx, gy) =
                when (first) {
                    is Order.Move -> floor(first.tx).toInt() to floor(first.ty).toInt()
                    is Order.Patrol -> {
                        val tx = if (first.toB) first.bx else first.ax
                        val ty = if (first.toB) first.by else first.ay
                        floor(tx).toInt() to floor(ty).toInt()
                    }
                    is Order.AttackMove -> floor(first.tx).toInt() to floor(first.ty).toInt()
                    is Order.Hold -> continue
                    is Order.Construct -> {
                        val footprint = world.footprints[first.target] ?: continue
                        nearestFootprintApproachTile(sx, sy, footprint)
                    }
                    is Order.Attack -> {
                        val targetTransform = world.transforms[first.target] ?: continue
                        floor(targetTransform.x).toInt() to floor(targetTransform.y).toInt()
                    }
                }
            val out = pool.obtain()
            val len = pathfinder.findPath(sx, sy, gx, gy, remainingBudget, out, allowOccupiedGoal = first is Order.Attack)
            remainingBudget -= pathfinder.lastNodesUsed
            nodesUsed += pathfinder.lastNodesUsed

            if (len > 0) {
                val old = world.pathFollows[id]
                if (old != null) pool.recycle(old.nodes)
                world.pathFollows[id] = PathFollow(out, len, 0, gx, gy)
                recordAssigned(id, len, gx, gy)
                solved++
                totalLen += len
            } else {
                pool.recycle(out)
                if (pathfinder.lastBudgetExhausted) {
                    budgetExhausted++
                    queue.setId(newCount++, id)
                }
            }
        }
        queue.reset(newCount)
        lastTickSolved = solved
        lastTickAvgPathLen = if (solved > 0) totalLen.toFloat() / solved.toFloat() else 0f
        lastTickNodesUsed = nodesUsed
        lastTickBudgetExhausted = budgetExhausted
        lastTickCarryOver = newCount
    }

    fun assignedEntityId(index: Int): Int = assignedEntityIds[index]

    fun assignedLength(index: Int): Int = assignedLengths[index]

    fun assignedGoalX(index: Int): Int = assignedGoalXs[index]

    fun assignedGoalY(index: Int): Int = assignedGoalYs[index]

    private fun recordAssigned(entityId: Int, length: Int, goalX: Int, goalY: Int) {
        val index = lastTickAssignedCount
        if (index >= assignedEntityIds.size) {
            val nextSize = assignedEntityIds.size * 2
            assignedEntityIds = assignedEntityIds.copyOf(nextSize)
            assignedLengths = assignedLengths.copyOf(nextSize)
            assignedGoalXs = assignedGoalXs.copyOf(nextSize)
            assignedGoalYs = assignedGoalYs.copyOf(nextSize)
        }
        assignedEntityIds[index] = entityId
        assignedLengths[index] = length
        assignedGoalXs[index] = goalX
        assignedGoalYs[index] = goalY
        lastTickAssignedCount = index + 1
    }

    private fun nearestFootprintApproachTile(sx: Int, sy: Int, footprint: BuildingFootprint): Pair<Int, Int> {
        var bestX = footprint.tileX - 1
        var bestY = footprint.tileY
        var bestDist = Int.MAX_VALUE
        val minX = footprint.tileX - 1
        val minY = footprint.tileY - 1
        val maxX = footprint.tileX + footprint.width
        val maxY = footprint.tileY + footprint.height
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val onBorder = x == minX || x == maxX || y == minY || y == maxY
                if (!onBorder) continue
                val dist = abs(sx - x) + abs(sy - y)
                if (dist < bestDist) {
                    bestDist = dist
                    bestX = x
                    bestY = y
                }
            }
        }
        return bestX to bestY
    }
}
