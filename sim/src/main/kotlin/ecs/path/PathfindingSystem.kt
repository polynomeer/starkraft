package starkraft.sim.ecs.path

import kotlin.math.floor
import starkraft.sim.ecs.Order
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
    var lastTickRequests: Int = 0
        private set
    var lastTickSolved: Int = 0
        private set

    fun tick() {
        var remainingBudget = nodesBudgetPerTick
        var newCount = 0
        var solved = 0
        lastTickRequests = queue.count

        for (i in 0 until queue.count) {
            val id = queue.idAt(i)
            val orders = world.orders[id]?.items ?: continue
            val move = orders.firstOrNull() as? Order.Move ?: continue
            if (remainingBudget <= 0) {
                queue.setId(newCount++, id)
                continue
            }

            val tr = world.transforms[id] ?: continue
            val sx = floor(tr.x).toInt()
            val sy = floor(tr.y).toInt()
            val gx = floor(move.tx).toInt()
            val gy = floor(move.ty).toInt()
            val out = pool.obtain()
            val len = pathfinder.findPath(sx, sy, gx, gy, remainingBudget, out)
            remainingBudget -= pathfinder.lastNodesUsed

            if (len > 0) {
                val old = world.pathFollows[id]
                if (old != null) pool.recycle(old.nodes)
                world.pathFollows[id] = PathFollow(out, len, 0)
                solved++
            } else {
                pool.recycle(out)
                if (pathfinder.lastBudgetExhausted) {
                    queue.setId(newCount++, id)
                }
            }
        }
        queue.reset(newCount)
        lastTickSolved = solved
    }
}
