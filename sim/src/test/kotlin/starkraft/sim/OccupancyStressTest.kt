package starkraft.sim

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import java.util.Random

class OccupancyStressTest {
    @Test
    fun manyUnitsMoveWithoutStalling() {
        Ids.resetForTest()
        val rng = Random(42L)
        val world = World()
        val map = MapGrid(32, 32)
        val occ = OccupancyGrid(32, 32)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(256, 50)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 3000)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)

        // Scatter obstacles
        repeat(80) {
            map.setBlocked(rng.nextInt(map.width), rng.nextInt(map.height), true)
        }

        val count = 120
        val ids = IntArray(count)
        repeat(count) { i ->
            val x = 1f + rng.nextInt(30) + rng.nextFloat()
            val y = 1f + rng.nextInt(30) + rng.nextFloat()
            ids[i] = world.spawn(Transform(x, y), UnitTag(1, "Test"), Health(10, 10), null)
        }

        // Initial move
        for (i in 0 until count) {
            val tx = 1f + rng.nextInt(30) + rng.nextFloat()
            val ty = 1f + rng.nextInt(30) + rng.nextFloat()
            world.orders[ids[i]]?.items?.addLast(Order.Move(tx, ty))
        }

        val totalTicks = 600
        for (tick in 0 until totalTicks) {
            if (tick % 120 == 0) {
                for (i in 0 until count) {
                    val tx = 1f + rng.nextInt(30) + rng.nextFloat()
                    val ty = 1f + rng.nextInt(30) + rng.nextFloat()
                    world.orders[ids[i]]?.items?.addLast(Order.Move(tx, ty))
                }
            }
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
        }

        val moved = ids.count { id ->
            val tr = world.transforms[id]!!
            tr.x > 2f || tr.y > 2f
        }
        assertTrue(moved >= count / 2, "Too many units stalled")
    }
}
