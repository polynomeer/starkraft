package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.ecs.services.FogGrid
import kotlin.math.roundToInt
import java.util.Random

class DeterminismTest {
    @Test
    fun deterministicSimulationHash() {
        val h1 = runSimAndHash(1234L)
        val h2 = runSimAndHash(1234L)
        assertEquals(h1, h2)
    }
}

private fun runSimAndHash(seed: Long): Long {
    Ids.resetForTest()
    val rng = Random(seed)
    val unitsResource = object {}.javaClass.getResource("/data/units.json")
        ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
    val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
        ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")
    val data = DataRepo(unitsResource.readText(), weaponsResource.readText())

    val world = World()
    val map = MapGrid(32, 32)
    val occ = OccupancyGrid(32, 32)
    val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
    val pathPool = PathPool(map.width * map.height)
    val pathQueue = PathRequestQueue(256, 50)
    val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 2000)
    val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
    val occupancy = OccupancySystem(world, occ)
    val combat = CombatSystem(world, data)
    val fog1 = FogGrid(64, 64, 0.25f)
    val fog2 = FogGrid(64, 64, 0.25f)
    val vision = VisionSystem(world, fog1, fog2)

    // Obstacles and rough terrain (matches demo)
    for (x in 6..24) map.setBlocked(x, 14, true)
    for (y in 6..14) map.setBlocked(12, y, true)
    for (x in 18..22) for (y in 18..22) map.setCost(x, y, 3f)

    val ids = IntArray(20)
    for (i in 0 until 20) {
        val x = 2f + rng.nextInt(28) + rng.nextFloat()
        val y = 2f + rng.nextInt(28) + rng.nextFloat()
        val faction = if (i % 2 == 0) 1 else 2
        val type = if (faction == 1) "Marine" else "Zergling"
        val weapon = if (faction == 1) "Gauss" else "Claw"
        ids[i] = world.spawn(Transform(x, y), UnitTag(faction, type), Health(40, 40), WeaponRef(weapon))
        world.visions[ids[i]] = Vision(if (faction == 1) 7f else 6f)
    }

    val phases = 5
    val tx = Array(phases) { FloatArray(ids.size) }
    val ty = Array(phases) { FloatArray(ids.size) }
    for (p in 0 until phases) {
        for (i in ids.indices) {
            tx[p][i] = 1f + rng.nextInt(30) + rng.nextFloat()
            ty[p][i] = 1f + rng.nextInt(30) + rng.nextFloat()
        }
    }

    val totalTicks = 500
    for (tick in 0 until totalTicks) {
        if (tick % 100 == 0) {
            val phase = tick / 100
            for (i in ids.indices) {
                world.orders[ids[i]]?.items?.addLast(Order.Move(tx[phase][i], ty[phase][i]))
            }
        }
        occupancy.tick()
        pathing.tick()
        movement.tick()
        combat.tick()
        vision.tick()
    }

    return hashWorld(world, fog1, fog2)
}

private fun hashWorld(world: World, fog1: FogGrid, fog2: FogGrid): Long {
    val ids = world.transforms.keys.sorted()
    var h = 1469598103934665603L
    fun mix(v: Long) {
        h = h xor v
        h *= 1099511628211L
    }
    for (id in ids) {
        val tr = world.transforms[id]!!
        mix(id.toLong())
        mix((tr.x * 1000f).roundToInt().toLong())
        mix((tr.y * 1000f).roundToInt().toLong())
        val pf = world.pathFollows[id]
        mix((pf?.index ?: -1).toLong())
        mix((world.orders[id]?.items?.size ?: 0).toLong())
        val hp = world.healths[id]
        mix((hp?.hp ?: 0).toLong())
        val w = world.weapons[id]
        mix((w?.cooldownTicks ?: 0).toLong())
    }
    mix(fog1.visibleCount().toLong())
    mix(fog2.visibleCount().toLong())
    return h
}
