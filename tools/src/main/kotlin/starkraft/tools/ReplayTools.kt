package starkraft.tools

import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.AliveSystem
import starkraft.sim.ecs.BuildingPlacementSystem
import starkraft.sim.ecs.BuildingProductionSystem
import starkraft.sim.ecs.CombatSystem
import starkraft.sim.ecs.ConstructionSystem
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.MovementSystem
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.OccupancySystem
import starkraft.sim.ecs.ResourceHarvestSystem
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.ResearchSystem
import starkraft.sim.ecs.VictorySystem
import starkraft.sim.ecs.VisionSystem
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.issue
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayHashRecorder
import starkraft.sim.replay.ReplayIO
import java.nio.file.Path

data class ReplayFastForwardResult(
    val finalTick: Int,
    val worldHash: Long
)

data class ReplayVerifyResult(
    val recordedReplayHash: Long?,
    val computedReplayHash: Long,
    val replayHashMatches: Boolean,
    val finalTick: Int,
    val worldHash: Long
)

fun runReplay(path: Path, tickLimit: Int? = null): ReplayFastForwardResult {
    val commands = ReplayIO.load(path)
    val runner = ReplaySimRunner()
    return runner.run(commands, tickLimit)
}

fun verifyReplay(path: Path, tickLimit: Int? = null, strictHash: Boolean = false): ReplayVerifyResult {
    val meta = ReplayIO.inspect(path)
    val commands = ReplayIO.load(path, strictHash = strictHash)
    val recorder = ReplayHashRecorder()
    for (command in commands) recorder.onCommand(command)
    val computedReplayHash = recorder.value()
    val hashMatches = meta.replayHash?.let { it == computedReplayHash } ?: !strictHash
    val fastForward = ReplaySimRunner().run(commands, tickLimit)
    return ReplayVerifyResult(
        recordedReplayHash = meta.replayHash,
        computedReplayHash = computedReplayHash,
        replayHashMatches = hashMatches,
        finalTick = fastForward.finalTick,
        worldHash = fastForward.worldHash
    )
}

private class ReplaySimRunner {
    private val data: DataRepo = loadDataRepo()

    fun run(commands: List<Command>, tickLimit: Int? = null): ReplayFastForwardResult {
        val world = World()
        val map = MapGrid(32, 32)
        val occ = OccupancyGrid(32, 32)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(256, 50)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 2000)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources, data)
        val buildings = BuildingPlacementSystem(world, map, occ, resources)
        val construction = ConstructionSystem(world, data)
        val production = BuildingProductionSystem(world, map, occ, data, resources)
        val research = ResearchSystem(world, data, resources)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)
        val combat = CombatSystem(world, data)
        val victory = VictorySystem(world)
        val fog1 = FogGrid(64, 64, 0.25f)
        val fog2 = FogGrid(64, 64, 0.25f)
        val vision = VisionSystem(world, fog1, fog2)

        for (x in 6..24) map.setBlocked(x, 14, true)
        for (y in 6..14) map.setBlocked(12, y, true)
        for (x in 18..22) for (y in 18..22) map.setCost(x, y, 3f)

        val byTick = bucketByTick(commands)
        val maxTick = byTick.lastIndex
        val totalTicks = tickLimit?.coerceIn(0, maxTick + 1) ?: (maxTick + 1)
        val labelMap = HashMap<String, Int>(32)
        val labelIdMap = HashMap<Int, Int>(32)
        for (tick in 0 until totalTicks) {
            alive.tick()
            val tickCommands = byTick[tick]
            for (i in 0 until tickCommands.size) {
                issue(
                    cmd = tickCommands[i],
                    world = world,
                    recorder = starkraft.sim.replay.NullRecorder(),
                    data = data,
                    labelMap = labelMap,
                    labelIdMap = labelIdMap,
                    buildings = buildings,
                    production = production,
                    research = research
                )
            }
            harvest.tick()
            occupancy.tick()
            construction.tick()
            research.tick()
            production.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
            victory.tick()
            vision.tick()
            if (world.matchEnded) break
        }
        return ReplayFastForwardResult(
            finalTick = worldTickFromCommands(byTick, totalTicks),
            worldHash = hashWorld(world)
        )
    }

    private fun bucketByTick(commands: List<Command>): Array<ArrayList<Command>> {
        var maxTick = 0
        for (command in commands) {
            if (command.tick > maxTick) maxTick = command.tick
        }
        val byTick = Array(maxTick + 1) { ArrayList<Command>() }
        for (command in commands) {
            byTick[command.tick].add(command)
        }
        return byTick
    }

    private fun worldTickFromCommands(byTick: Array<ArrayList<Command>>, totalTicks: Int): Int {
        if (totalTicks <= 0) return 0
        if (byTick.isEmpty()) return 0
        return (totalTicks - 1).coerceAtMost(byTick.lastIndex)
    }
}

private fun loadDataRepo(): DataRepo {
    val unitsJson = ReplaySimRunner::class.java.getResource("/data/units.json")?.readText()
        ?: error("Missing /data/units.json on classpath")
    val weaponsJson = ReplaySimRunner::class.java.getResource("/data/weapons.json")?.readText()
        ?: error("Missing /data/weapons.json on classpath")
    val buildingsJson = ReplaySimRunner::class.java.getResource("/data/buildings.json")?.readText()
        ?: error("Missing /data/buildings.json on classpath")
    val techsJson = ReplaySimRunner::class.java.getResource("/data/techs.json")?.readText()
        ?: error("Missing /data/techs.json on classpath")
    return DataRepo(
        unitsJson = unitsJson,
        weaponsJson = weaponsJson,
        buildingsJson = buildingsJson,
        techsJson = techsJson
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
        val tr = world.transforms[id] ?: continue
        mix(id.toLong())
        mix((tr.x * 1000f).toInt().toLong())
        mix((tr.y * 1000f).toInt().toLong())
        val pf = world.pathFollows[id]
        mix((pf?.index ?: -1).toLong())
        mix((world.orders[id]?.items?.size ?: 0).toLong())
        val hp = world.healths[id]
        mix((hp?.hp ?: 0).toLong())
        val weapon = world.weapons[id]
        mix((weapon?.cooldownTicks ?: 0).toLong())
    }
    mix(if (world.matchEnded) 1L else 0L)
    mix((world.winnerFaction ?: -1).toLong())
    return h
}
