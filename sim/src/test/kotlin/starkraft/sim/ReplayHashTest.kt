package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayHashRecorder
import java.util.Random

class ReplayHashTest {
    @Test
    fun replayHashIsDeterministic() {
        val seed = testSeed()
        val h1 = runReplayScenario(seed)
        val h2 = runReplayScenario(seed)
        assertEquals(h1.replayHash, h2.replayHash)
        assertEquals(h1.worldHash, h2.worldHash)
        assertEquals(6762632104746809539L, h1.replayHash)
        assertEquals(6018382555110414077L, h1.worldHash)
    }
}

private fun testSeed(): Long =
    System.getProperty("seed")?.toLong() ?: 1234L

private data class ReplayResult(val replayHash: Long, val worldHash: Long)

private fun runReplayScenario(seed: Long): ReplayResult {
    Ids.resetForTest()
    val rng = Random(seed)
    val recorder = ReplayHashRecorder()

    val world = World()
    val map = MapGrid(20, 20)
    val occ = OccupancyGrid(20, 20)
    val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
    val pathPool = PathPool(map.width * map.height)
    val pathQueue = PathRequestQueue(128, 32)
    val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 500)
    val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
    val occupancy = OccupancySystem(world, occ)
    val alive = AliveSystem(world)

    val ids = IntArray(10)
    for (i in ids.indices) {
        val x = 2f + rng.nextInt(16) + rng.nextFloat()
        val y = 2f + rng.nextInt(16) + rng.nextFloat()
        ids[i] = world.spawn(Transform(x, y), UnitTag(1, "Test"), Health(10, 10), null)
    }

    val totalTicks = 300
    for (tick in 0 until totalTicks) {
        if (tick % 60 == 0) {
            for (i in ids.indices) {
                val tx = 1f + rng.nextInt(18) + rng.nextFloat()
                val ty = 1f + rng.nextInt(18) + rng.nextFloat()
                val cmd = Command.Move(tick, intArrayOf(ids[i]), tx, ty)
                recorder.onCommand(cmd)
                world.orders[ids[i]]?.items?.addLast(Order.Move(tx, ty))
            }
        }
        alive.tick()
        occupancy.tick()
        pathing.tick()
        movement.tick()
    }

    return ReplayResult(recorder.value(), hashWorld(world))
}

private fun hashWorld(world: World): Long {
    val ids = world.transforms.keys.sorted()
    var h = 1469598103934665603L
    fun mix(v: Long) {
        h = h xor v
        h *= 1099511628211L
    }
    for (id in ids) {
        val tr = world.transforms[id]!!
        mix(id.toLong())
        mix((tr.x * 1000f).toInt().toLong())
        mix((tr.y * 1000f).toInt().toLong())
        val pf = world.pathFollows[id]
        mix((pf?.index ?: -1).toLong())
        mix((world.orders[id]?.items?.size ?: 0).toLong())
    }
    return h
}
