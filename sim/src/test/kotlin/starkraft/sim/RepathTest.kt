package starkraft.sim

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import kotlin.math.abs
import kotlin.math.floor

class RepathTest {
    @Test
    fun replansWhenBlockedMidPath() {
        Ids.resetForTest()
        val world = World()
        val map = MapGrid(10, 10)
        val occ = OccupancyGrid(10, 10)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(64, 32)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 500)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)

        // Wall at x=5 with two gaps at y=2 and y=7
        for (y in 0 until 10) {
            if (y == 2 || y == 7) continue
            map.setBlocked(5, y, true)
        }

        val id = world.spawn(Transform(2f, 2f), UnitTag(1, "Test"), Health(10, 10), null)
        world.orders[id]?.items?.addLast(Order.Move(8f, 2f))

        var blocked = false
        var safePathObserved = false
        val maxTicks = 400
        for (tick in 0 until maxTicks) {
            alive.tick()
            if (tick == 20) {
                occ.addStatic(5, 2)
                blocked = true
            }
            occupancy.tick()
            pathing.tick()
            movement.tick()

            if (blocked && tick > 40) {
                val pf = world.pathFollows[id]
                if (pf != null) {
                    safePathObserved = !pathContains(pf, map.width, 5, 2)
                }
            }
        }

        val tr = world.transforms[id]!!
        assertTrue(isNear(tr.x, 8f) && isNear(tr.y, 2f), "Unit did not reach goal")
        assertTrue(safePathObserved, "Replan did not avoid blocked gap")
    }
}

private fun pathContains(pf: PathFollow, width: Int, x: Int, y: Int): Boolean {
    val target = y * width + x
    for (i in pf.index until pf.length) {
        if (pf.nodes[i] == target) return true
    }
    return false
}

private fun isNear(v: Float, target: Float): Boolean = abs(v - target) < 0.2f
