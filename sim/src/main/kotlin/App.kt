package starkraft.sim

import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.net.Command
import starkraft.sim.net.ScriptRunner
import starkraft.sim.replay.ReplayHashRecorder
import starkraft.sim.replay.ReplayIO
import starkraft.sim.replay.ReplayRecorder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Random

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

    val seed = parseSeed(args)
    val rng = if (seed != null) Random(seed) else null

    // Spawn with vision component
    val team1 = mutableListOf<Int>()
    val team2 = mutableListOf<Int>()
    repeat(5) {
        // Marines (team1)
        val jitterX = rng?.let { (it.nextFloat() - 0.5f) * 0.4f } ?: 0f
        val jitterY = rng?.let { (it.nextFloat() - 0.5f) * 0.4f } ?: 0f
        val idA = world.spawn(
            Transform(2f + it * 0.2f + jitterX, 2f + jitterY),
            UnitTag(1, "Marine"),
            Health(45, 45),
            WeaponRef("Gauss")
        )
        world.visions[idA] = Vision(7f)
        team1.add(idA)

        // Zerglings (team2)
        val idB =
            world.spawn(
                Transform(10f - it * 0.2f - jitterX, 10f - jitterY),
                UnitTag(2, "Zergling"),
                Health(35, 35),
                WeaponRef("Claw")
            )
        world.visions[idB] = Vision(6f)
        team2.add(idB)
    }

    val replayPath = parseReplayPath(args)
    val scriptPath = parseScriptPath(args)
    val spawnScriptPath = parseSpawnScriptPath(args)
    val recordPath = parseRecordPath(args)
    val replayOutPath = parseReplayOutPath(args)
    val tickLimit = parseTickLimit(args)
    val replayTicks = parseReplayTicks(args)
    val noSleep = hasFlag(args, "--noSleep")
    val scriptValidate = hasFlag(args, "--scriptValidate")
    val replayValidateOnly = hasFlag(args, "--replayValidateOnly")
    val dumpWorldHash = hasFlag(args, "--dumpWorldHash")
    val baseCommands: Array<ArrayList<Command>> = when {
        replayPath != null -> loadReplayCommands(replayPath)
        scriptPath != null -> loadScriptCommands(scriptPath)
        else -> arrayOf()
    }
    val spawnCommands: Array<ArrayList<Command>> =
        if (spawnScriptPath != null) loadSpawnScriptCommands(spawnScriptPath) else arrayOf()
    val commandsByTick = mergeCommands(spawnCommands, baseCommands)
    if (scriptValidate && (scriptPath != null || spawnScriptPath != null)) {
        validateSpawnTypes(commandsByTick, data)
        printScriptCommands(commandsByTick)
        return
    }
    if (replayValidateOnly && replayPath != null) {
        println("replay validation ok: $replayPath")
        return
    }

    if (replayPath == null) {
        if (team1.isNotEmpty()) {
            issue(Command.Move(0, team1.toIntArray(), 28f, 28f), world, recorder, data)
        }
        if (team2.isNotEmpty()) {
            issue(Command.Move(0, team2.toIntArray(), 2f, 2f), world, recorder, data)
        }
    }

    val defaultTicks = if (commandsByTick.isNotEmpty()) commandsByTick.size else 1500
    val totalTicks = when {
        replayTicks != null -> minOf(defaultTicks, replayTicks.coerceAtLeast(0))
        tickLimit != null -> tickLimit.coerceAtLeast(0)
        else -> defaultTicks
    }
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
                issue(cmds[i], world, recorder, data)
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
        if (!noSleep) Thread.sleep(Time.TICK_MS.toLong())
    }

    if (recordPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(recordPath), recorded)
        println("replay saved: $recordPath")
    }

    if (replayPath != null || scriptPath != null) {
        val worldHash = hashWorldForReplay(world)
        val replayHash = ReplayHashRecorder().also { r ->
            val end = minOf(totalTicks, commandsByTick.size)
            for (idx in 0 until end) {
                val tickCmds = commandsByTick[idx]
                for (i in 0 until tickCmds.size) {
                    r.onCommand(tickCmds[i])
                }
            }
        }.value()
        val source = if (replayPath != null) "replay" else "script"
        println("$source hash=$replayHash world hash=$worldHash")
    }

    if (dumpWorldHash && replayPath == null && scriptPath == null) {
        val worldHash = hashWorldForReplay(world)
        println("world hash=$worldHash")
    }

    if (replayOutPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(replayOutPath), recorded)
        println("replay out saved: $replayOutPath")
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

private fun parseReplayOutPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--replayOut" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--replayOut=")) return a.substringAfter("=")
        i++
    }
    return null
}

private fun parseScriptPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--script" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--script=")) return a.substringAfter("=")
        i++
    }
    return null
}

private fun parseSpawnScriptPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--spawnScript" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--spawnScript=")) return a.substringAfter("=")
        i++
    }
    return null
}

private fun parseTickLimit(args: Array<String>): Int? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--ticks" && i + 1 < args.size) return args[i + 1].toInt()
        if (a.startsWith("--ticks=")) return a.substringAfter("=").toInt()
        i++
    }
    return null
}

private fun hasFlag(args: Array<String>, flag: String): Boolean =
    args.any { it == flag }

private fun parseReplayTicks(args: Array<String>): Int? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--replayTicks" && i + 1 < args.size) return args[i + 1].toInt()
        if (a.startsWith("--replayTicks=")) return a.substringAfter("=").toInt()
        i++
    }
    return null
}

private fun parseSeed(args: Array<String>): Long? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--seed" && i + 1 < args.size) return args[i + 1].toLong()
        if (a.startsWith("--seed=")) return a.substringAfter("=").toLong()
        i++
    }
    return null
}

private fun loadReplayCommands(pathStr: String): Array<ArrayList<Command>> {
    val path = resolvePath(pathStr)
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

private fun loadScriptCommands(pathStr: String): Array<ArrayList<Command>> {
    val path = resolvePath(pathStr)
    if (!Files.exists(path)) error("Script file not found: $pathStr")
    val cmds = ScriptRunner.load(path)
    if (cmds.isEmpty()) return arrayOf()
    var maxTick = 0
    for (c in cmds) if (c.tick > maxTick) maxTick = c.tick
    val byTick = Array(maxTick + 1) { ArrayList<Command>() }
    for (c in cmds) {
        byTick[c.tick].add(c)
    }
    return byTick
}

private fun resolvePath(pathStr: String): java.nio.file.Path {
    val p = Paths.get(pathStr)
    if (p.isAbsolute) return p
    val base = Paths.get("").toAbsolutePath()
    val candidate = base.resolve(pathStr)
    return if (Files.exists(candidate)) candidate else p
}

private fun loadSpawnScriptCommands(pathStr: String): Array<ArrayList<Command>> {
    val all = loadScriptCommands(pathStr)
    for (tick in all.indices) {
        val cmds = all[tick]
        val it = cmds.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c !is Command.Spawn) it.remove()
        }
    }
    return all
}

private fun mergeCommands(
    first: Array<ArrayList<Command>>,
    second: Array<ArrayList<Command>>
): Array<ArrayList<Command>> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first
    val size = maxOf(first.size, second.size)
    val merged = Array(size) { ArrayList<Command>() }
    for (i in 0 until size) {
        if (i < first.size) merged[i].addAll(first[i])
        if (i < second.size) merged[i].addAll(second[i])
    }
    return merged
}

private fun printScriptCommands(commandsByTick: Array<ArrayList<Command>>) {
    println("script commands:")
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Move -> {
                    println("tick=$tick move units=${c.units.joinToString(",")} x=${c.x} y=${c.y}")
                }
                is Command.Attack -> {
                    println("tick=$tick attack units=${c.units.joinToString(",")} target=${c.target}")
                }
                is Command.Spawn -> {
                    println("tick=$tick spawn faction=${c.faction} type=${c.typeId} x=${c.x} y=${c.y} vision=${c.vision}")
                }
            }
        }
    }
}

private fun validateSpawnTypes(commandsByTick: Array<ArrayList<Command>>, data: DataRepo) {
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            if (c is Command.Spawn) {
                try {
                    data.unit(c.typeId)
                } catch (_: NoSuchElementException) {
                    error("Unknown unit typeId '${c.typeId}' in spawn at tick $tick")
                }
            }
        }
    }
}

private fun hashWorldForReplay(world: World): Long {
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
        val hp = world.healths[id]
        mix((hp?.hp ?: 0).toLong())
        val w = world.weapons[id]
        mix((w?.cooldownTicks ?: 0).toLong())
    }
    return h
}

fun issue(cmd: Command, world: World, recorder: starkraft.sim.replay.Recorder, data: DataRepo? = null) {
    recorder.onCommand(cmd)
    when (cmd) {
        is Command.Move -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y)) }
        }

        is Command.Attack -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Attack(cmd.target)) }
        }

        is Command.Spawn -> {
            val repo = data ?: error("Spawn requires DataRepo")
            val def = try {
                repo.unit(cmd.typeId)
            } catch (_: NoSuchElementException) {
                error("Unknown unit typeId '${cmd.typeId}' in spawn command")
            }
            val weapon = def.weaponId?.let { WeaponRef(it) }
            val vision = cmd.vision?.let { Vision(it) }
            world.spawn(
                Transform(cmd.x, cmd.y),
                UnitTag(cmd.faction, cmd.typeId),
                Health(def.hp, def.hp, def.armor),
                weapon,
                vision
            )
        }
    }
}
