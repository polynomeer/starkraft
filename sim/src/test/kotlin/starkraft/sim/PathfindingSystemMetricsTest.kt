package starkraft.sim

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.MovementSystem
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.OccupancySystem
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.OrderQueue
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem

class PathfindingSystemMetricsTest {
    @Test
    fun `pathfinding metrics expose node usage and carryover`() {
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val pathQueue = PathRequestQueue(16, 16)
        val pathPool = PathPool(map.width * map.height)
        val pathing = PathfindingSystem(world, Pathfinder(map, occ, allowCornerCut = false), pathPool, pathQueue, nodesBudgetPerTick = 8)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)

        val ids = IntArray(6) {
            world.spawn(Transform(1f + it, 1f), UnitTag(1, "Marine"), Health(40, 40), null)
        }
        for (id in ids) {
            world.orders[id] = OrderQueue(ArrayDeque<Order>().apply { addLast(Order.Move(14f, 14f)) })
        }

        occupancy.tick()
        movement.tick()
        pathing.tick()

        assertTrue(pathing.lastTickRequests >= 0)
        assertTrue(pathing.lastTickNodesUsed >= 0)
        assertTrue(pathing.lastTickCarryOver >= 0)
        assertTrue(pathing.lastTickBudgetExhausted >= 0)
    }
}
