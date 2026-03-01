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
import starkraft.sim.replay.ReplayMetadata
import starkraft.sim.replay.ReplayRecorder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val scriptDryRun = hasFlag(args, "--scriptDryRun")
    val replayValidateOnly = hasFlag(args, "--replayValidateOnly")
    val dumpWorldHash = hasFlag(args, "--dumpWorldHash")
    val strictReplayHash = hasFlag(args, "--strictReplayHash")
    val printEntities = hasFlag(args, "--printEntities")
    val printOrders = hasFlag(args, "--printOrders")
    val labelDump = hasFlag(args, "--labelDump")
    val replayStats = hasFlag(args, "--replayStats")
    val replayStatsJson = hasFlag(args, "--replayStatsJson")
    val replayDumpPath = parseReplayDumpPath(args)
    val baseCommands: Array<ArrayList<Command>> = when {
        replayPath != null -> loadReplayCommands(replayPath, strictReplayHash)
        scriptPath != null -> loadScriptCommands(scriptPath)
        else -> arrayOf()
    }
    val spawnCommands: Array<ArrayList<Command>> =
        if (spawnScriptPath != null) loadSpawnScriptCommands(spawnScriptPath) else arrayOf()
    val commandsByTick = mergeCommands(spawnCommands, baseCommands)
    val labelMap = HashMap<String, Int>()
    val labelIdMap = HashMap<Int, Int>()
    val replayMeta =
        if (replayPath != null) ReplayIO.inspect(resolvePath(replayPath)) else null

    if ((scriptValidate || scriptDryRun) && (scriptPath != null || spawnScriptPath != null)) {
        validateSpawnTypes(commandsByTick, data)
        if (scriptPath != null) {
            validateCommandUnitIds(commandsByTick, world)
            validateLabelUsage(commandsByTick)
        }
        if (scriptDryRun) {
            println("script dry run ok")
        }
        printScriptCommands(commandsByTick)
        return
    }
    if (replayValidateOnly && replayPath != null) {
        println(
            "replay validation ok: $replayPath schema=${replayMeta?.schema} " +
                "seed=${replayMeta?.seed} replayHash=${replayMeta?.replayHash}"
        )
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
                issue(cmds[i], world, recorder, data, labelMap, labelIdMap)
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
        ReplayIO.save(Paths.get(recordPath), recorded, seed)
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

    if (printEntities) {
        printAliveEntities(world)
    }

    if (printOrders) {
        printPendingOrders(world)
    }

    if (labelDump) {
        printLabelMappings(labelMap, labelIdMap)
    }

    if ((replayStats || replayStatsJson) && commandsByTick.isNotEmpty()) {
        val stats = buildCommandStats(commandsByTick, replayMeta)
        if (replayStats) {
            printCommandStats(stats)
        }
        if (replayStatsJson) {
            println(statsJson.encodeToString(stats))
        }
    }

    if (replayOutPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(replayOutPath), recorded, seed)
        println("replay out saved: $replayOutPath")
    }

    if (replayDumpPath != null && scriptPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(replayDumpPath), recorded, seed)
        println("replay dump saved: $replayDumpPath")
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

private fun parseReplayDumpPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--replayDump" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--replayDump=")) return a.substringAfter("=")
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

private fun loadReplayCommands(pathStr: String, strictHash: Boolean): Array<ArrayList<Command>> {
    val path = resolvePath(pathStr)
    if (!Files.exists(path)) error("Replay file not found: $pathStr")
    val cmds = ReplayIO.load(path, strictHash)
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
                is Command.MoveFaction -> {
                    println("tick=$tick moveFaction faction=${c.faction} x=${c.x} y=${c.y}")
                }
                is Command.MoveType -> {
                    println("tick=$tick moveType type=${c.typeId} x=${c.x} y=${c.y}")
                }
                is Command.Attack -> {
                    println("tick=$tick attack units=${c.units.joinToString(",")} target=${c.target}")
                }
                is Command.AttackFaction -> {
                    println("tick=$tick attackFaction faction=${c.faction} target=${c.target}")
                }
                is Command.AttackType -> {
                    println("tick=$tick attackType type=${c.typeId} target=${c.target}")
                }
                is Command.Spawn -> {
                    val label = c.label?.let { "@$it " } ?: ""
                    println("tick=$tick spawn ${label}faction=${c.faction} type=${c.typeId} x=${c.x} y=${c.y} vision=${c.vision}")
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

private fun validateCommandUnitIds(commandsByTick: Array<ArrayList<Command>>, world: World) {
    val existing = world.transforms.keys
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Move -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in move at tick $tick")
                        }
                    }
                }
                is Command.Attack -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in attack at tick $tick")
                        }
                    }
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in attack at tick $tick")
                    }
                }
                is Command.MoveFaction -> Unit
                is Command.MoveType -> Unit
                is Command.AttackFaction -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in attackFaction at tick $tick")
                    }
                }
                is Command.AttackType -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in attackType at tick $tick")
                    }
                }
                is Command.Spawn -> {
                    // Spawn adds new ids; no validation needed here.
                }
            }
        }
    }
}

private fun validateLabelUsage(commandsByTick: Array<ArrayList<Command>>) {
    val defined = HashSet<Int>()
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Spawn -> {
                    val labelId = c.labelId
                    if (labelId != null) defined.add(labelId)
                }
                is Command.Move -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in move at tick $tick (spawn first)")
                    }
                }
                is Command.Attack -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in attack at tick $tick (spawn first)")
                    }
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in attack at tick $tick (spawn first)")
                    }
                }
                is Command.MoveFaction -> Unit
                is Command.MoveType -> Unit
                is Command.AttackFaction -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in attackFaction at tick $tick (spawn first)")
                    }
                }
                is Command.AttackType -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in attackType at tick $tick (spawn first)")
                    }
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

private fun printAliveEntities(world: World) {
    println("alive entities:")
    val ids = world.transforms.keys.sorted()
    for (id in ids) {
        val hp = world.healths[id]?.hp ?: 0
        if (hp <= 0) continue
        val tr = world.transforms[id]!!
        val tag = world.tags[id]
        println(
            "id=$id type=${tag?.typeId} faction=${tag?.faction} " +
                "x=${"%.2f".format(tr.x)} y=${"%.2f".format(tr.y)} hp=$hp"
        )
    }
}

private fun printPendingOrders(world: World) {
    println("pending orders:")
    val ids = world.orders.keys.sorted()
    for (id in ids) {
        val q = world.orders[id]?.items ?: continue
        if (q.isEmpty()) continue
        val items = q.joinToString(",") { o ->
            when (o) {
                is Order.Move -> "move(${String.format("%.2f", o.tx)},${String.format("%.2f", o.ty)})"
                is Order.Attack -> "attack(${o.target})"
            }
        }
        println("id=$id orders=[$items]")
    }
}

private fun printLabelMappings(labelMap: Map<String, Int>, labelIdMap: Map<Int, Int>) {
    println("label mappings:")
    if (labelMap.isEmpty() && labelIdMap.isEmpty()) {
        println("(none)")
        return
    }
    for ((label, id) in labelMap.entries.sortedBy { it.key }) {
        println("@$label -> $id")
    }
    val remaining = labelIdMap.filter { (_, id) ->
        labelMap.values.none { it == id }
    }
    for ((labelId, id) in remaining.entries.sortedBy { it.key }) {
        println("labelId=$labelId -> $id")
    }
}

private val statsJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
private data class CommandStats(
    val metadata: CommandStatsMetadata? = null,
    val ticks: List<CommandTickCount>,
    val totals: CommandTotals
)

@Serializable
private data class CommandStatsMetadata(
    val schema: Int,
    val replayHash: Long? = null,
    val seed: Long? = null,
    val legacy: Boolean
)

@Serializable
private data class CommandTickCount(
    val tick: Int,
    val commands: Int
)

@Serializable
private data class CommandTotals(
    val total: Int,
    val spawns: Int,
    val moves: Int,
    val attacks: Int
)

private fun buildCommandStats(
    commandsByTick: Array<ArrayList<Command>>,
    replayMeta: ReplayMetadata?
): CommandStats {
    var total = 0
    var spawns = 0
    var moves = 0
    var attacks = 0
    val ticks = ArrayList<CommandTickCount>()
    for (tick in commandsByTick.indices) {
        val count = commandsByTick[tick].size
        if (count > 0) {
            ticks.add(CommandTickCount(tick, count))
            total += count
        }
        for (cmd in commandsByTick[tick]) {
            when (cmd) {
                is Command.Move, is Command.MoveFaction, is Command.MoveType -> moves++
                is Command.Attack, is Command.AttackFaction, is Command.AttackType -> attacks++
                is Command.Spawn -> spawns++
            }
        }
    }
    return CommandStats(
        metadata =
            replayMeta?.let {
                CommandStatsMetadata(
                    schema = it.schema,
                    replayHash = it.replayHash,
                    seed = it.seed,
                    legacy = it.legacy
                )
            },
        ticks = ticks,
        totals = CommandTotals(total, spawns, moves, attacks)
    )
}

private fun printCommandStats(stats: CommandStats) {
    println("command stats:")
    val meta = stats.metadata
    if (meta != null) {
        println("replay metadata: schema=${meta.schema} seed=${meta.seed} replayHash=${meta.replayHash}")
    }
    for (tick in stats.ticks) {
        println("tick=${tick.tick} commands=${tick.commands}")
    }
    println(
        "total=${stats.totals.total} spawns=${stats.totals.spawns} " +
            "moves=${stats.totals.moves} attacks=${stats.totals.attacks}"
    )
}

fun issue(
    cmd: Command,
    world: World,
    recorder: starkraft.sim.replay.Recorder,
    data: DataRepo? = null,
    labelMap: MutableMap<String, Int> = mutableMapOf(),
    labelIdMap: MutableMap<Int, Int> = mutableMapOf()
) {
    recorder.onCommand(cmd)
    when (cmd) {
        is Command.Move -> {
            if (cmd.units.size == 1 && cmd.units[0] == 0) {
                for (id in world.orders.keys) {
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            } else {
                cmd.units.forEach { id ->
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
        }
        is Command.MoveFaction -> {
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
        }
        is Command.MoveType -> {
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
        }

        is Command.Attack -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            if (cmd.units.size == 1 && cmd.units[0] == 0) {
                for (id in world.orders.keys) {
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Attack(target))
                }
            } else {
                cmd.units.forEach { id ->
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Attack(target))
                }
            }
        }
        is Command.AttackFaction -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.Attack(target))
                }
            }
        }
        is Command.AttackType -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.Attack(target))
                }
            }
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
            val id = world.spawn(
                Transform(cmd.x, cmd.y),
                UnitTag(cmd.faction, cmd.typeId),
                Health(def.hp, def.hp, def.armor),
                weapon,
                vision
            )
            if (cmd.label != null) {
                labelMap[cmd.label] = id
            }
            if (cmd.labelId != null) {
                labelIdMap[cmd.labelId] = id
            }
        }
    }
}

private fun resolveLabelId(id: Int, labelIdMap: Map<Int, Int>): Int {
    if (id >= 0) return id
    return labelIdMap[id] ?: error("Unknown label id '$id' (spawn missing?)")
}
