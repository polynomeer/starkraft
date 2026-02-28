package starkraft.sim

import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder
import starkraft.sim.replay.ReplayIO
import starkraft.sim.replay.ReplayRecorder
import java.nio.file.Files
import java.nio.file.Paths

object Time {
    const val TICK_MS = 20
}

fun main(args: Array<String>) {
    // Load data resources
    val unitsResource = object {}.javaClass.getResource("/data/units.json")
        ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
    val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
        ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")

    val unitsJson = unitsResource.readText()
    val weaponsJson = weaponsResource.readText()
    val data = DataRepo(unitsJson, weaponsJson)

    val world = World()
    val map = MapGrid(32, 32)
    val occ = OccupancyGrid(32, 32)
    val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
    val pathPool = PathPool(map.width * map.height)
    val pathQueue = PathRequestQueue(256, 50)
    val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 2000)
    val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
    val occupancy = OccupancySystem(world, occ)
    val alive = AliveSystem(world)
    val combat = CombatSystem(world, data)
    val fog1 = FogGrid(64, 64, 0.25f) // tileSize=0.25이면 대략 16x16 월드
    val fog2 = FogGrid(64, 64, 0.25f)
    val visionSys = VisionSystem(world, fog1, fog2)
    val recorder = ReplayRecorder()

    // Simple obstacles + rough terrain
    for (x in 6..24) map.setBlocked(x, 14, true)
    for (y in 6..14) map.setBlocked(12, y, true)
    for (x in 18..22) for (y in 18..22) map.setCost(x, y, 3f)

    // Spawn with vision component
    val team1 = mutableListOf<Int>()
    val team2 = mutableListOf<Int>()
    repeat(5) {
        // Marines (team1)
        val idA = world.spawn(Transform(2f + it * 0.2f, 2f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))
        world.visions[idA] = Vision(7f)
        team1.add(idA)

        // Zerglings (team2)
        val idB =
            world.spawn(Transform(10f - it * 0.2f, 10f), UnitTag(2, "Zergling"), Health(35, 35), WeaponRef("Claw"))
        world.visions[idB] = Vision(6f)
        team2.add(idB)
    }

    val replayPath = parseReplayPath(args)
    val recordPath = parseRecordPath(args)
    val commandsByTick: Array<ArrayList<Command>> =
        if (replayPath != null) loadReplayCommands(replayPath) else arrayOf()

    if (replayPath == null) {
        team1.forEach { id -> world.orders[id]?.items?.addLast(Order.Move(28f, 28f)) }
        team2.forEach { id -> world.orders[id]?.items?.addLast(Order.Move(2f, 2f)) }
    }

    val totalTicks = if (commandsByTick.isNotEmpty()) commandsByTick.size else 1500
    var tick = 0
    while (tick < totalTicks) {
        alive.tick()
        if (tick == 200) {
            for (x in 14..20) occ.addStatic(x, 10)
            println("tick=$tick  add static blockers at y=10 (x=14..20)")
        }
        if (tick == 500) {
            for (x in 14..20) occ.removeStatic(x, 10)
            println("tick=$tick  remove static blockers at y=10 (x=14..20)")
        }
        if (tick < commandsByTick.size) {
            val cmds = commandsByTick[tick]
            for (i in 0 until cmds.size) {
                issue(cmds[i], world, recorder)
            }
        }

        occupancy.tick()
        pathing.tick()
        movement.tick()
        combat.tick()
        visionSys.tick()

        if (tick % 25 == 0) {
            val m1 = world.tags.filter { it.value.faction == 1 }.keys.size
            val m2 = world.tags.filter { it.value.faction == 2 }.keys.size
            println(
                "tick=$tick  alive: team1=$m1 team2=$m2  visibleTiles: t1=${fog1.visibleCount()} t2=${fog2.visibleCount()} " +
                    "pathReq=${pathing.lastTickRequests} pathSolved=${pathing.lastTickSolved} queue=${pathQueue.size} avgLen=${"%.2f".format(pathing.lastTickAvgPathLen)} " +
                    "replans=${movement.lastTickReplans} " +
                    "blocked=${movement.lastTickReplansBlocked} stuck=${movement.lastTickReplansStuck}"
            )
        }
        tick++
        Thread.sleep(Time.TICK_MS.toLong())
    }

    if (recordPath != null) {
        ReplayIO.save(Paths.get(recordPath), recorder.snapshot())
        println("replay saved: $recordPath")
    }
}

private fun parseReplayPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--replay" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--replay=")) return a.substringAfter("=")
        i++
    }
    return null
}

private fun parseRecordPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--record" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--record=")) return a.substringAfter("=")
        i++
    }
    return null
}

private fun loadReplayCommands(pathStr: String): Array<ArrayList<Command>> {
    val path = Paths.get(pathStr)
    if (!Files.exists(path)) error("Replay file not found: $pathStr")
    val cmds = ReplayIO.load(path)
    if (cmds.isEmpty()) return arrayOf()
    var maxTick = 0
    for (c in cmds) if (c.tick > maxTick) maxTick = c.tick
    val byTick = Array(maxTick + 1) { ArrayList<Command>() }
    for (c in cmds) {
        byTick[c.tick].add(c)
    }
    return byTick
}

fun issue(cmd: Command, world: World, recorder: starkraft.sim.replay.Recorder) {
    recorder.onCommand(cmd)
    when (cmd) {
        is Command.Move -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y)) }
        }

        is Command.Attack -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Attack(cmd.target)) }
        }
    }
}
