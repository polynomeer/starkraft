package starkraft.sim

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import kotlin.math.floor

class CornerCutTest {
    @Test
    fun avoidsDiagonalCornerCut() {
        Ids.resetForTest()
        val world = World()
        val map = MapGrid(6, 6)
        val occ = OccupancyGrid(6, 6)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(64, 32)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 500)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)

        // Block (2,1) and (1,2) so diagonal from (1,1) to (2,2) is invalid
        map.setBlocked(2, 1, true)
        map.setBlocked(1, 2, true)

        val id = world.spawn(Transform(1f, 1f), UnitTag(1, "Test"), Health(10, 10), null)
        world.orders[id]?.items?.addLast(Order.Move(3f, 3f))

        var sawDiagonal = false
        val maxTicks = 120
        for (tick in 0 until maxTicks) {
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()

            val pf = world.pathFollows[id]
            if (pf != null && pf.index < pf.length) {
                val cur = pf.nodes[pf.index]
                val nx = cur % map.width
                val ny = cur / map.width
                if (nx == 2 && ny == 2) {
                    val tr = world.transforms[id]!!
                    val cx = floor(tr.x).toInt()
                    val cy = floor(tr.y).toInt()
                    if (cx == 1 && cy == 1) {
                        sawDiagonal = true
                    }
                }
            }
        }

        assertTrue(!sawDiagonal, "Path attempted to cut blocked diagonal corner")
    }
}
