package starkraft.tools

import starkraft.sim.ecs.AliveSystem
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.MovementSystem
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.OccupancySystem
import starkraft.sim.ecs.PathFollow
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.issue
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder
import starkraft.sim.replay.ReplayIO
import java.nio.file.Path

internal data class ReplayRunResult(
    val finalTick: Int,
    val commandCount: Int,
    val finalWorldHash: Long
)

internal fun fastForwardReplay(path: Path, tickLimit: Int? = null): ReplayRunResult {
    val commands = ReplayIO.load(path)
    val maxCommandTick = commands.maxOfOrNull { it.tick } ?: 0
    val finalTick = tickLimit?.coerceAtLeast(0)?.coerceAtMost(maxCommandTick) ?: maxCommandTick

    val world = World()
    val map = MapGrid(20, 20)
    val occupancyGrid = OccupancyGrid(20, 20)
    val pathfinder = Pathfinder(map, occupancyGrid, allowCornerCut = false)
    val pathPool = PathPool(map.width * map.height)
    val pathQueue = PathRequestQueue(256, 64)
    val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 500)
    val movement = MovementSystem(world, map, occupancyGrid, pathPool, pathQueue)
    val occupancy = OccupancySystem(world, occupancyGrid)
    val alive = AliveSystem(world)

    for (i in 0 until 8) {
        val x = 2f + i
        val y = 2f + i
        world.spawn(Transform(x, y), UnitTag(if (i < 4) 1 else 2, "Worker"), Health(40, 40), null)
    }

    val byTick = Array(finalTick + 1) { ArrayList<Command>() }
    for (command in commands) {
        if (command.tick in 0..finalTick) byTick[command.tick].add(command)
    }

    var consumed = 0
    for (tick in 0..finalTick) {
        alive.tick()
        val list = byTick[tick]
        for (i in 0 until list.size) {
            issue(list[i], world, NullRecorder())
            consumed++
        }
        occupancy.tick()
        pathing.tick()
        movement.tick()
    }
    return ReplayRunResult(finalTick, consumed, hashWorld(world))
}

private fun hashWorld(world: World): Long {
    val ids = world.transforms.keys.sorted()
    var h = 1469598103934665603L
    fun mix(v: Long) {
        h = h xor v
        h *= 1099511628211L
    }
    for (id in ids) {
        val tr = world.transforms[id] ?: continue
        mix(id.toLong())
        mix((tr.x * 1000f).toInt().toLong())
        mix((tr.y * 1000f).toInt().toLong())
        val follow: PathFollow? = world.pathFollows[id]
        mix((follow?.index ?: -1).toLong())
        mix((world.orders[id]?.items?.size ?: 0).toLong())
    }
    return h
}
