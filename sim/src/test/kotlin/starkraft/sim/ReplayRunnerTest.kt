package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files

class ReplayRunnerTest {
    @Test
    fun replayRunMatchesGoldenHash() {
        Ids.resetForTest()
        val replayPath = Files.createTempFile("starkraft-replay-run", ".json")
        val cmds = sampleReplayCommands()
        ReplayIO.save(replayPath, cmds)

        val worldHash = runReplay(replayPath.toString())
        assertEquals(GOLDEN_WORLD_HASH, worldHash)
    }
}

private const val GOLDEN_WORLD_HASH = 3423039781053103764L

private fun runReplay(path: String): Long {
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

    val ids = IntArray(6)
    for (i in ids.indices) {
        val x = 2f + i
        val y = 2f + i
        ids[i] = world.spawn(Transform(x, y), UnitTag(1, "Test"), Health(10, 10), null)
    }

    val commands = ReplayIO.load(java.nio.file.Paths.get(path))
    var maxTick = 0
    for (c in commands) if (c.tick > maxTick) maxTick = c.tick
    val byTick = Array(maxTick + 1) { ArrayList<starkraft.sim.net.Command>() }
    for (c in commands) byTick[c.tick].add(c)

    for (tick in 0..maxTick) {
        alive.tick()
        val cmds = byTick[tick]
        for (i in 0 until cmds.size) {
            issue(cmds[i], world, starkraft.sim.replay.NullRecorder())
        }
        occupancy.tick()
        pathing.tick()
        movement.tick()
    }

    return hashWorld(world)
}

private fun sampleReplayCommands(): List<starkraft.sim.net.Command> {
    return listOf(
        starkraft.sim.net.Command.Move(0, intArrayOf(1, 2, 3), 10f, 10f),
        starkraft.sim.net.Command.Move(5, intArrayOf(4, 5, 6), 15f, 15f),
        starkraft.sim.net.Command.Move(20, intArrayOf(1, 6), 3f, 17f)
    )
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
