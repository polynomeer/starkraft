package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.AliveSystem
import starkraft.sim.ecs.CombatSystem
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.MovementSystem
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.OccupancySystem
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.PathFollow
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.WeaponRef
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem

class CombatBehaviorTest {
    private fun combatData(): DataRepo =
        DataRepo(
            """{"list":[]}""",
            """{"list":[{"id":"Gauss","damage":8,"range":4.0,"cooldownTicks":1}]}""",
            """{"list":[]}"""
        )

    @Test
    fun `attack order chases targets that start out of range`() {
        val data = combatData()
        val world = World()
        val map = MapGrid(24, 24)
        val occ = OccupancyGrid(24, 24)
        val alive = AliveSystem(world)
        val occupancy = OccupancySystem(world, occ)
        val pool = PathPool(map.width * map.height)
        val queue = PathRequestQueue(64, 64)
        val pathfinder = Pathfinder(map, occ)
        val pathing = PathfindingSystem(world, pathfinder, pool, queue, 1024)
        val movement = MovementSystem(world, map, occ, pool, queue, data)
        val combat = CombatSystem(world, data)

        val attacker =
            world.spawn(
                Transform(2f, 2f),
                UnitTag(1, "Marine"),
                Health(45, 45),
                WeaponRef("Gauss")
            )
        val target =
            world.spawn(
                Transform(12f, 2f),
                UnitTag(2, "Zergling"),
                Health(35, 35),
                w = null
            )
        world.orders[attacker]?.items?.addLast(Order.Attack(target))

        repeat(140) {
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
        }

        assertTrue(world.transforms[attacker]!!.x > 2.5f)
        assertEquals(Order.Attack(target), world.orders[attacker]?.items?.firstOrNull())
    }

    @Test
    fun `attack order clears after target death`() {
        val data = combatData()
        val world = World()
        val map = MapGrid(16, 16)
        val occ = OccupancyGrid(16, 16)
        val alive = AliveSystem(world)
        val occupancy = OccupancySystem(world, occ)
        val pool = PathPool(map.width * map.height)
        val queue = PathRequestQueue(32, 32)
        val pathfinder = Pathfinder(map, occ)
        val pathing = PathfindingSystem(world, pathfinder, pool, queue, 512)
        val movement = MovementSystem(world, map, occ, pool, queue, data)
        val combat = CombatSystem(world, data)

        val attacker =
            world.spawn(
                Transform(2f, 2f),
                UnitTag(1, "Marine"),
                Health(45, 45),
                WeaponRef("Gauss")
            )
        val target =
            world.spawn(
                Transform(4f, 2f),
                UnitTag(2, "Zergling"),
                Health(6, 6),
                w = null
            )
        world.orders[attacker]?.items?.addLast(Order.Attack(target))

        repeat(3) {
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
        }

        assertEquals(null, world.healths[target])
        assertEquals(null, world.orders[attacker]?.items?.firstOrNull())
        assertEquals(null, world.pathFollows[attacker])
    }

    @Test
    fun `attack move pauses to fight and then continues`() {
        val data = combatData()
        val world = World()
        val map = MapGrid(24, 24)
        val occ = OccupancyGrid(24, 24)
        val alive = AliveSystem(world)
        val occupancy = OccupancySystem(world, occ)
        val pool = PathPool(map.width * map.height)
        val queue = PathRequestQueue(64, 64)
        val pathfinder = Pathfinder(map, occ)
        val pathing = PathfindingSystem(world, pathfinder, pool, queue, 1024)
        val movement = MovementSystem(world, map, occ, pool, queue, data)
        val combat = CombatSystem(world, data)

        val attacker =
            world.spawn(
                Transform(2f, 2f),
                UnitTag(1, "Marine"),
                Health(45, 45),
                WeaponRef("Gauss")
            )
        val blocker =
            world.spawn(
                Transform(6f, 2f),
                UnitTag(2, "Zergling"),
                Health(8, 8),
                w = null
            )
        world.orders[attacker]?.items?.addLast(Order.AttackMove(12f, 2f))

        repeat(160) {
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
        }

        assertEquals(null, world.healths[blocker])
        assertTrue(world.transforms[attacker]!!.x > 9f)
        assertEquals(Order.AttackMove(12f, 2f), world.orders[attacker]?.items?.firstOrNull())
    }

    @Test
    fun `auto targeting avoids wasting lethal follow up shots when another target is available`() {
        val data = combatData()
        val world = World()
        val alive = AliveSystem(world)
        val combat = CombatSystem(world, data)

        world.spawn(
            Transform(2f, 2f),
            UnitTag(1, "Marine"),
            Health(45, 45),
            WeaponRef("Gauss")
        )
        world.spawn(
            Transform(2f, 2.5f),
            UnitTag(1, "Marine"),
            Health(45, 45),
            WeaponRef("Gauss")
        )
        val nearTarget =
            world.spawn(
                Transform(5f, 2f),
                UnitTag(2, "Zergling"),
                Health(12, 12),
                w = null
            )
        val farTarget =
            world.spawn(
                Transform(5f, 4f),
                UnitTag(2, "Zergling"),
                Health(35, 35),
                w = null
            )

        alive.tick()
        combat.tick()

        assertEquals(4, world.healths[nearTarget]?.hp)
        assertEquals(27, world.healths[farTarget]?.hp)
        assertEquals(2, combat.lastTickAttacks)
    }

    @Test
    fun `hold order fires in range but does not chase out of range targets`() {
        val data = combatData()
        val world = World()
        val map = MapGrid(24, 24)
        val occ = OccupancyGrid(24, 24)
        val alive = AliveSystem(world)
        val occupancy = OccupancySystem(world, occ)
        val pool = PathPool(map.width * map.height)
        val queue = PathRequestQueue(64, 64)
        val pathfinder = Pathfinder(map, occ)
        val pathing = PathfindingSystem(world, pathfinder, pool, queue, 1024)
        val movement = MovementSystem(world, map, occ, pool, queue, data)
        val combat = CombatSystem(world, data)

        val attacker =
            world.spawn(
                Transform(2f, 2f),
                UnitTag(1, "Marine"),
                Health(45, 45),
                WeaponRef("Gauss")
            )
        val nearTarget =
            world.spawn(
                Transform(5f, 2f),
                UnitTag(2, "Zergling"),
                Health(6, 6),
                w = null
            )
        world.spawn(
            Transform(10f, 2f),
            UnitTag(2, "Zergling"),
            Health(35, 35),
            w = null
        )
        world.orders[attacker]?.items?.addLast(Order.Hold)

        repeat(40) {
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
        }

        assertEquals(null, world.healths[nearTarget])
        assertTrue(world.transforms[attacker]!!.x < 2.2f)
        assertEquals(Order.Hold, world.orders[attacker]?.items?.firstOrNull())
        assertEquals(null, world.pathFollows[attacker])
    }

    @Test
    fun `auto targeting stays sticky while target remains in range`() {
        val data = combatData()
        val world = World()
        val alive = AliveSystem(world)
        val combat = CombatSystem(world, data)

        world.spawn(
            Transform(2f, 2f),
            UnitTag(1, "Marine"),
            Health(45, 45),
            WeaponRef("Gauss")
        )
        val nearTarget =
            world.spawn(
                Transform(5f, 2f),
                UnitTag(2, "Zergling"),
                Health(35, 35),
                w = null
            )
        world.spawn(
            Transform(5f, 2.6f),
            UnitTag(2, "Zergling"),
            Health(35, 35),
            w = null
        )

        alive.tick()
        combat.tick()
        val stickyBefore = world.autoAttackTargets.values.firstOrNull()
        alive.tick()
        combat.tick()
        val stickyAfter = world.autoAttackTargets.values.firstOrNull()

        assertEquals(nearTarget, stickyBefore)
        assertEquals(nearTarget, stickyAfter)
    }
}
