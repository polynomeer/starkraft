package starkraft.sim

import starkraft.sim.client.buildClientSnapshot
import starkraft.sim.client.BuildFailureCounts
import starkraft.sim.client.CombatEventRecord
import starkraft.sim.client.DamageEventRecord
import starkraft.sim.client.DespawnEventRecord
import starkraft.sim.client.OrderQueueEntityRecord
import starkraft.sim.client.OccupancyChangeEventRecord
import starkraft.sim.client.MapBlockedTileRecord
import starkraft.sim.client.MapCostTileRecord
import starkraft.sim.client.PathAssignedEventRecord
import starkraft.sim.client.PathProgressEventRecord
import starkraft.sim.client.VisionChangeEventRecord
import starkraft.sim.client.renderCombatStreamRecordJson
import starkraft.sim.client.renderClientSnapshotJson
import starkraft.sim.client.renderCommandStreamRecordJson
import starkraft.sim.client.renderCommandFailureStreamRecordJson
import starkraft.sim.client.renderDamageStreamRecordJson
import starkraft.sim.client.renderDespawnStreamRecordJson
import starkraft.sim.client.renderMetricsStreamRecordJson
import starkraft.sim.client.renderMapStateStreamRecordJson
import starkraft.sim.client.renderOrderAppliedStreamRecordJson
import starkraft.sim.client.renderOrderQueueStreamRecordJson
import starkraft.sim.client.renderOccupancyChangeStreamRecordJson
import starkraft.sim.client.renderPathAssignedStreamRecordJson
import starkraft.sim.client.renderPathProgressStreamRecordJson
import starkraft.sim.client.renderSnapshotSessionEndJson
import starkraft.sim.client.renderSnapshotSessionStartJson
import starkraft.sim.client.renderSnapshotStreamRecordJson
import starkraft.sim.client.renderSelectionStreamRecordJson
import starkraft.sim.client.renderSessionStatsStreamRecordJson
import starkraft.sim.client.renderSpawnStreamRecordJson
import starkraft.sim.client.renderTickSummaryStreamRecordJson
import starkraft.sim.client.renderVisionStreamRecordJson
import starkraft.sim.client.ProductionEventRecord
import starkraft.sim.client.TrainFailureCounts
import starkraft.sim.client.renderProductionStreamRecordJson
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

private const val DEMO_MAP_ID = "demo-32x32-obstacles"
private const val BUILD_VERSION = "1.0-SNAPSHOT"

fun main(args: Array<String>) {
    // Load data resources
    val unitsResource = object {}.javaClass.getResource("/data/units.json")
        ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
    val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
        ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")
    val buildingsResource = object {}.javaClass.getResource("/data/buildings.json")
        ?: error("Resource '/data/buildings.json' not found. Ensure it exists in the resources directory.")

    val unitsJson = unitsResource.readText()
    val weaponsJson = weaponsResource.readText()
    val buildingsJson = buildingsResource.readText()
    val data = DataRepo(unitsJson, weaponsJson, buildingsJson)

    val world = World()
    val map = MapGrid(32, 32)
    val occ = OccupancyGrid(32, 32)
    val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
    val pathPool = PathPool(map.width * map.height)
    val pathQueue = PathRequestQueue(256, 50)
    val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 2000)
    val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
    val resources = ResourceSystem(world)
    val buildings = BuildingPlacementSystem(world, map, occ, resources)
    val production = BuildingProductionSystem(world, map, occ, data, resources)
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
    resources.set(faction = 1, minerals = 500, gas = 0)
    resources.set(faction = 2, minerals = 500, gas = 0)
    val depotBuild = data.buildSpec("Depot")
    val depotId =
        if (depotBuild != null) {
            buildings.place(
                faction = 1,
                typeId = depotBuild.typeId,
                tileX = 24,
                tileY = 4,
                width = depotBuild.footprintWidth,
                height = depotBuild.footprintHeight,
                hp = depotBuild.hp,
                clearance = depotBuild.placementClearance,
                armor = depotBuild.armor,
                mineralCost = depotBuild.mineralCost,
                gasCost = depotBuild.gasCost
            )
        } else {
            null
        }
    if (depotId != null) {
        val marineTrain = data.trainSpec("Marine")
        production.enqueue(
            depotId,
            typeId = "Marine",
            buildTicks = marineTrain?.buildTicks ?: 75,
            mineralCost = marineTrain?.mineralCost ?: 50,
            gasCost = marineTrain?.gasCost ?: 0
        )
    }

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
    val snapshotOutPath = parseSnapshotOutPath(args)
    val tickLimit = parseTickLimit(args)
    val replayTicks = parseReplayTicks(args)
    val snapshotEvery = parseSnapshotEvery(args)
    val noSleep = hasFlag(args, "--noSleep")
    val scriptValidate = hasFlag(args, "--scriptValidate")
    val scriptDryRun = hasFlag(args, "--scriptDryRun")
    val replayValidateOnly = hasFlag(args, "--replayValidateOnly")
    val dumpWorldHash = hasFlag(args, "--dumpWorldHash")
    val strictReplayHash = hasFlag(args, "--strictReplayHash")
    val strictReplayMeta = hasFlag(args, "--strictReplayMeta")
    val printEntities = hasFlag(args, "--printEntities")
    val printOrders = hasFlag(args, "--printOrders")
    val labelDump = hasFlag(args, "--labelDump")
    val replayStats = hasFlag(args, "--replayStats")
    val replayStatsJson = hasFlag(args, "--replayStatsJson")
    val replayMetaJson = hasFlag(args, "--replayMetaJson")
    val snapshotJson = hasFlag(args, "--snapshotJson")
    val compactJson = hasFlag(args, "--compactJson")
    val replayDumpPath = parseReplayDumpPath(args)
    val resolvedReplayPath = replayPath?.let(::resolvePath)
    val resolvedSnapshotOutPath = snapshotOutPath?.let(::resolvePath)
    val replayMeta =
        if (resolvedReplayPath != null) ReplayIO.inspect(resolvedReplayPath) else null
    val streamSequence = longArrayOf(0L)
    val visionPrevTeam1 = BooleanArray(fog1.width * fog1.height)
    val visionPrevTeam2 = BooleanArray(fog2.width * fog2.height)
    if (resolvedSnapshotOutPath != null) {
        Files.deleteIfExists(resolvedSnapshotOutPath)
    }
    if (resolvedSnapshotOutPath != null && (snapshotJson || snapshotEvery != null)) {
        emitSnapshotLine(
            renderSnapshotSessionStartJson(
                sequence = nextStreamSequence(streamSequence),
                mapId = DEMO_MAP_ID,
                buildVersion = BUILD_VERSION,
                seed = seed,
                pretty = false
            ),
            resolvedSnapshotOutPath
        )
        emitMapStateRecord(map, occ, resolvedSnapshotOutPath, streamSequence)
    }
    requireReplayCompatibility(replayMeta, strictReplayMeta)
    val baseProgram: LoadedProgram = when {
        replayPath != null -> LoadedProgram(loadReplayCommands(replayPath, strictReplayHash), emptyArray())
        scriptPath != null -> loadScriptProgram(scriptPath)
        else -> LoadedProgram(arrayOf(), emptyArray())
    }
    val spawnProgram: LoadedProgram =
        if (spawnScriptPath != null) loadSpawnScriptProgram(spawnScriptPath) else LoadedProgram(arrayOf(), emptyArray())
    val baseCommands = baseProgram.commandsByTick
    val spawnCommands = spawnProgram.commandsByTick
    val commandsByTick = mergeCommands(spawnCommands, baseCommands)
    val selectionEventsByTick = mergeSelectionEvents(spawnProgram.selectionEventsByTick, baseProgram.selectionEventsByTick)
    val labelMap = HashMap<String, Int>()
    val labelIdMap = HashMap<Int, Int>()
    var totalPathRequests = 0
    var totalPathSolved = 0
    var totalReplans = 0
    var totalReplansBlocked = 0
    var totalReplansStuck = 0
    var totalAttacks = 0
    var totalKills = 0
    var totalDespawns = 0
    var totalBuilds = 0
    var totalBuildFailures = 0
    val totalBuildFailureReasons = BuildFailureCounterSet()
    var totalTrainsQueued = 0
    var totalTrainsCompleted = 0
    var totalTrainsCancelled = 0
    var totalTrainFailures = 0
    val totalTrainFailureReasons = TrainFailureCounterSet()

    if ((scriptValidate || scriptDryRun) && (scriptPath != null || spawnScriptPath != null)) {
        validateSpawnTypes(commandsByTick, data)
        validateBuildCommands(commandsByTick, data)
        validateTrainCommands(commandsByTick, data)
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
        printReplayCompatibilityWarnings(replayMeta)
        println(
            "replay validation ok: $replayPath schema=${replayMeta?.schema} " +
                "seed=${replayMeta?.seed} mapId=${replayMeta?.mapId} " +
                "buildVersion=${replayMeta?.buildVersion} replayHash=${replayMeta?.replayHash}"
        )
        return
    }
    if (replayMetaJson && replayPath != null) {
        println(
            renderReplayMetaJson(
                buildReplayMetaReport(
                    replayMeta = replayMeta,
                    replayPath = resolvedReplayPath?.toAbsolutePath()?.normalize()?.toString(),
                    currentMapId = DEMO_MAP_ID,
                    currentBuildVersion = BUILD_VERSION,
                    currentSeed = seed,
                    strictReplayMeta = strictReplayMeta,
                    strictReplayHash = strictReplayHash
                ),
                pretty = !compactJson
            )
        )
        return
    }

    if (replayPath == null) {
        if (team1.isNotEmpty()) {
            issue(Command.Move(0, team1.toIntArray(), 28f, 28f), world, recorder, data, snapshotOutPath = resolvedSnapshotOutPath, streamSequence = streamSequence)
        }
        if (team2.isNotEmpty()) {
            issue(Command.Move(0, team2.toIntArray(), 2f, 2f), world, recorder, data, snapshotOutPath = resolvedSnapshotOutPath, streamSequence = streamSequence)
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
        val commandOutcomeCounters = CommandOutcomeCounters()
        var tickTrainsCompleted = 0
        world.clearRemovedEvents()
        production.clearTickEvents()
        alive.tick()
        if (tick == 200) {
            val changes = ArrayList<OccupancyChangeEventRecord>(7)
            for (x in 14..20) occ.addStatic(x, 10)
            for (x in 14..20) changes.add(OccupancyChangeEventRecord(x, 10, blocked = true))
            emitOccupancyChangeRecord(tick, changes, resolvedSnapshotOutPath, streamSequence)
            println("tick=$tick  add static blockers at y=10 (x=14..20)")
        }
        if (tick == 500) {
            val changes = ArrayList<OccupancyChangeEventRecord>(7)
            for (x in 14..20) occ.removeStatic(x, 10)
            for (x in 14..20) changes.add(OccupancyChangeEventRecord(x, 10, blocked = false))
            emitOccupancyChangeRecord(tick, changes, resolvedSnapshotOutPath, streamSequence)
            println("tick=$tick  remove static blockers at y=10 (x=14..20)")
        }
        if (tick < commandsByTick.size) {
            if (tick < selectionEventsByTick.size) {
                val selections = selectionEventsByTick[tick]
                for (i in 0 until selections.size) {
                    emitSelectionRecord(selections[i], tick, resolvedSnapshotOutPath, streamSequence)
                }
            }
            val cmds = commandsByTick[tick]
            for (i in 0 until cmds.size) {
                issue(
                    cmds[i],
                    world,
                    recorder,
                    data,
                    labelMap,
                    labelIdMap,
                    resolvedSnapshotOutPath,
                    streamSequence,
                    buildings,
                    production,
                    commandOutcomeCounters
                )
            }
        }

        occupancy.tick()
        production.tick()
        for (i in 0 until production.lastTickEventCount) {
            if (production.eventKind(i) == BuildingProductionSystem.EVENT_COMPLETE) tickTrainsCompleted++
        }
        emitProductionRecord(production, tick, resolvedSnapshotOutPath, streamSequence)
        pathing.tick()
        emitPathAssignedRecord(pathing, tick, resolvedSnapshotOutPath, streamSequence)
        movement.tick()
        emitPathProgressRecord(movement, tick, resolvedSnapshotOutPath, streamSequence)
        combat.tick()
        emitDamageRecord(combat, tick, resolvedSnapshotOutPath, streamSequence)
        emitCombatRecord(combat, tick, resolvedSnapshotOutPath, streamSequence)
        emitDespawnRecord(world, tick, resolvedSnapshotOutPath, streamSequence)
        visionSys.tick()
        totalPathRequests += pathing.lastTickRequests
        totalPathSolved += pathing.lastTickSolved
        totalReplans += movement.lastTickReplans
        totalReplansBlocked += movement.lastTickReplansBlocked
        totalReplansStuck += movement.lastTickReplansStuck
        totalAttacks += combat.lastTickAttacks
        totalKills += combat.lastTickKills
        totalDespawns += world.removedEventCount
        totalBuilds += commandOutcomeCounters.builds
        totalBuildFailures += commandOutcomeCounters.buildFailures
        totalBuildFailureReasons.add(commandOutcomeCounters.buildFailureReasons)
        totalTrainsQueued += commandOutcomeCounters.trainsQueued
        totalTrainsCompleted += tickTrainsCompleted
        totalTrainsCancelled += commandOutcomeCounters.trainsCancelled
        totalTrainFailures += commandOutcomeCounters.trainFailures
        totalTrainFailureReasons.add(commandOutcomeCounters.trainFailureReasons)

        if (snapshotEvery != null && shouldEmitSnapshotAtTick(tick, snapshotEvery)) {
            emitVisionRecord(fog1, fog2, tick, visionPrevTeam1, visionPrevTeam2, resolvedSnapshotOutPath, streamSequence)
            emitMetricsRecord(world, fog1, fog2, tick, pathing, pathQueue, movement, resolvedSnapshotOutPath, streamSequence)
            emitTickSummaryRecord(
                world,
                fog1,
                fog2,
                tick,
                pathing,
                pathQueue,
                movement,
                combat,
                commandOutcomeCounters,
                tickTrainsCompleted,
                resolvedSnapshotOutPath,
                streamSequence
            )
            emitClientSnapshot(world, map, fog1, fog2, tick, seed, compactJson, resolvedSnapshotOutPath, streamSequence)
        }

        if (tick % 25 == 0) {
            val m1 = world.tags.filter { it.value.faction == 1 }.keys.size
            val m2 = world.tags.filter { it.value.faction == 2 }.keys.size
            val outcomeSuffix = renderCommandOutcomeLogSuffix(commandOutcomeCounters, tickTrainsCompleted)
            println(
                "tick=$tick  alive: team1=$m1 team2=$m2  visibleTiles: t1=${fog1.visibleCount()} t2=${fog2.visibleCount()} " +
                    "buildings=${world.footprints.size} prodQueues=${world.productionQueues.size} " +
                    "minerals: t1=${world.stockpiles[1]?.minerals ?: 0} t2=${world.stockpiles[2]?.minerals ?: 0} " +
                    "pathReq=${pathing.lastTickRequests} pathSolved=${pathing.lastTickSolved} queue=${pathQueue.size} avgLen=${"%.2f".format(pathing.lastTickAvgPathLen)} " +
                    "replans=${movement.lastTickReplans} " +
                    "blocked=${movement.lastTickReplansBlocked} stuck=${movement.lastTickReplansStuck}$outcomeSuffix"
            )
        }
        tick++
        if (!noSleep) Thread.sleep(Time.TICK_MS.toLong())
    }

    if (recordPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(recordPath), recorded, seed, DEMO_MAP_ID, BUILD_VERSION)
        println("replay saved: $recordPath")
    }

    val finalWorldHash = hashWorldForReplay(world)
    val finalReplayHash =
        if (replayPath != null || scriptPath != null) {
            ReplayHashRecorder().also { r ->
                val end = minOf(totalTicks, commandsByTick.size)
                for (idx in 0 until end) {
                    val tickCmds = commandsByTick[idx]
                    for (i in 0 until tickCmds.size) {
                        r.onCommand(tickCmds[i])
                    }
                }
            }.value()
        } else {
            null
        }

    if (replayPath != null || scriptPath != null) {
        val source = if (replayPath != null) "replay" else "script"
        println("$source hash=$finalReplayHash world hash=$finalWorldHash")
        println(currentRuntimeMetadataLine(seed))
    }
    val finalOutcomeSummary =
        renderAggregateOutcomeSummary(
            totalBuilds,
            totalBuildFailures,
            totalBuildFailureReasons,
            totalTrainsQueued,
            totalTrainsCompleted,
            totalTrainsCancelled,
            totalTrainFailures,
            totalTrainFailureReasons
        )
    if (finalOutcomeSummary != null) {
        println(finalOutcomeSummary)
    }

    if (dumpWorldHash && replayPath == null && scriptPath == null) {
        println("world hash=$finalWorldHash")
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
            println(renderCommandStatsJson(stats, pretty = !compactJson))
        }
    }

    if (snapshotJson) {
        emitClientSnapshot(world, map, fog1, fog2, tick, seed, compactJson, resolvedSnapshotOutPath, streamSequence)
    }
    if (resolvedSnapshotOutPath != null && (snapshotJson || snapshotEvery != null)) {
        emitSnapshotLine(
            renderSessionStatsStreamRecordJson(
                sequence = nextStreamSequence(streamSequence),
                ticks = tick,
                pathRequests = totalPathRequests,
                pathSolved = totalPathSolved,
                replans = totalReplans,
                replansBlocked = totalReplansBlocked,
                replansStuck = totalReplansStuck,
                attacks = totalAttacks,
                kills = totalKills,
                despawns = totalDespawns,
                builds = totalBuilds,
                buildFailures = totalBuildFailures,
                buildFailureReasons = totalBuildFailureReasons.toRecord(),
                trainsQueued = totalTrainsQueued,
                trainsCompleted = totalTrainsCompleted,
                trainsCancelled = totalTrainsCancelled,
                trainFailures = totalTrainFailures,
                trainFailureReasons = totalTrainFailureReasons.toRecord(),
                finalVisibleTilesFaction1 = fog1.visibleCount(),
                finalVisibleTilesFaction2 = fog2.visibleCount(),
                finalWorldHash = finalWorldHash,
                finalReplayHash = finalReplayHash,
                pretty = false
            ),
            resolvedSnapshotOutPath
        )
        emitSnapshotLine(
            renderSnapshotSessionEndJson(
                sequence = nextStreamSequence(streamSequence),
                tick = tick,
                worldHash = finalWorldHash,
                replayHash = finalReplayHash,
                pretty = false
            ),
            resolvedSnapshotOutPath
        )
    }

    if (replayOutPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(replayOutPath), recorded, seed, DEMO_MAP_ID, BUILD_VERSION)
        println("replay out saved: $replayOutPath")
    }

    if (replayDumpPath != null && scriptPath != null) {
        val recorded = recorder.snapshot()
        ReplayIO.save(Paths.get(replayDumpPath), recorded, seed, DEMO_MAP_ID, BUILD_VERSION)
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

private fun parseSnapshotOutPath(args: Array<String>): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--snapshotOut" && i + 1 < args.size) return args[i + 1]
        if (a.startsWith("--snapshotOut=")) return a.substringAfter("=")
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

private fun parseSnapshotEvery(args: Array<String>): Int? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--snapshotEvery" && i + 1 < args.size) return args[i + 1].toInt()
        if (a.startsWith("--snapshotEvery=")) return a.substringAfter("=").toInt()
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

private fun emitClientSnapshot(
    world: World,
    map: MapGrid,
    fog1: FogGrid,
    fog2: FogGrid,
    tick: Int,
    seed: Long?,
    compactJson: Boolean,
    snapshotOutPath: java.nio.file.Path? = null,
    streamSequence: LongArray? = null
) {
    val snapshot = buildClientSnapshot(
        world = world,
        map = map,
        tick = tick,
        mapId = DEMO_MAP_ID,
        buildVersion = BUILD_VERSION,
        seed = seed,
        fogByFaction = mapOf(1 to fog1, 2 to fog2)
    )
    if (snapshotOutPath == null) {
        emitSnapshotLine(renderClientSnapshotJson(snapshot, pretty = !compactJson), null)
    } else {
        val sequence = streamSequence?.let(::nextStreamSequence) ?: 0L
        emitSnapshotLine(renderSnapshotStreamRecordJson(snapshot, sequence = sequence, pretty = false), snapshotOutPath)
    }
}

internal fun shouldEmitSnapshotAtTick(tick: Int, every: Int): Boolean {
    if (every <= 0) return false
    return tick % every == 0
}

internal fun nextStreamSequence(state: LongArray): Long {
    val value = state[0]
    state[0] = value + 1L
    return value
}

private fun emitMetricsRecord(
    world: World,
    fog1: FogGrid,
    fog2: FogGrid,
    tick: Int,
    pathing: PathfindingSystem,
    pathQueue: PathRequestQueue,
    movement: MovementSystem,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    var alive1 = 0
    var alive2 = 0
    val alive = world.aliveSnapshot
    for (i in 0 until alive.count) {
        when (world.tags[alive.ids[i]]?.faction) {
            1 -> alive1++
            2 -> alive2++
        }
    }
    emitSnapshotLine(
        renderMetricsStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            factions =
                listOf(
                    starkraft.sim.client.MetricsFactionRecord(1, alive1, fog1.visibleCount()),
                    starkraft.sim.client.MetricsFactionRecord(2, alive2, fog2.visibleCount())
                ),
            pathRequests = pathing.lastTickRequests,
            pathSolved = pathing.lastTickSolved,
            pathQueueSize = pathQueue.size,
            avgPathLength = pathing.lastTickAvgPathLen,
            replans = movement.lastTickReplans,
            replansBlocked = movement.lastTickReplansBlocked,
            replansStuck = movement.lastTickReplansStuck,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitDamageRecord(
    combat: CombatSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || combat.lastTickEventCount == 0) return
    val events = ArrayList<DamageEventRecord>(combat.lastTickEventCount)
    for (i in 0 until combat.lastTickEventCount) {
        events.add(
            DamageEventRecord(
                attackerId = combat.eventAttacker(i),
                targetId = combat.eventTarget(i),
                damage = combat.eventDamage(i),
                targetHp = combat.eventTargetHp(i),
                killed = combat.eventKilled(i)
            )
        )
    }
    emitSnapshotLine(
        renderDamageStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            events = events,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitProductionRecord(
    production: BuildingProductionSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || production.lastTickEventCount == 0) return
    val events = ArrayList<ProductionEventRecord>(production.lastTickEventCount)
    for (i in 0 until production.lastTickEventCount) {
        val kind =
            when (production.eventKind(i)) {
                BuildingProductionSystem.EVENT_ENQUEUE -> "enqueue"
                BuildingProductionSystem.EVENT_COMPLETE -> "complete"
                BuildingProductionSystem.EVENT_CANCEL -> "cancel"
                else -> "progress"
            }
        val spawnedId = production.eventSpawnedId(i).takeIf { it != 0 }
        events.add(
            ProductionEventRecord(
                kind = kind,
                buildingId = production.eventBuildingId(i),
                typeId = production.eventTypeId(i) ?: "",
                remainingTicks = production.eventRemainingTicks(i),
                spawnedEntityId = spawnedId
            )
        )
    }
    emitSnapshotLine(
        renderProductionStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            events = events,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitCommandFailureRecord(
    tick: Int,
    commandType: String,
    reason: String,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?,
    faction: Int? = null,
    buildingId: Int? = null,
    typeId: String? = null,
    tileX: Int? = null,
    tileY: Int? = null
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderCommandFailureStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            commandType = commandType,
            reason = reason,
            faction = faction,
            buildingId = buildingId,
            typeId = typeId,
            tileX = tileX,
            tileY = tileY,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitCombatRecord(
    combat: CombatSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || combat.lastTickEventCount == 0) return
    val events = ArrayList<CombatEventRecord>(combat.lastTickEventCount)
    for (i in 0 until combat.lastTickEventCount) {
        events.add(
            CombatEventRecord(
                attackerId = combat.eventAttacker(i),
                targetId = combat.eventTarget(i),
                damage = combat.eventDamage(i),
                targetHp = combat.eventTargetHp(i),
                killed = combat.eventKilled(i)
            )
        )
    }
    emitSnapshotLine(
        renderCombatStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            attacks = combat.lastTickAttacks,
            kills = combat.lastTickKills,
            events = events,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitDespawnRecord(
    world: World,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || world.removedEventCount == 0) return
    val entities = ArrayList<DespawnEventRecord>(world.removedEventCount)
    for (i in 0 until world.removedEventCount) {
        entities.add(
            DespawnEventRecord(
                entityId = world.removedEntityId(i),
                faction = world.removedFaction(i),
                typeId = world.removedTypeId(i),
                reason = world.removedReason(i)
            )
        )
    }
    emitSnapshotLine(
        renderDespawnStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitTickSummaryRecord(
    world: World,
    fog1: FogGrid,
    fog2: FogGrid,
    tick: Int,
    pathing: PathfindingSystem,
    pathQueue: PathRequestQueue,
    movement: MovementSystem,
    combat: CombatSystem,
    commandOutcomeCounters: CommandOutcomeCounters,
    tickTrainsCompleted: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    var aliveTotal = 0
    val alive = world.aliveSnapshot
    for (i in 0 until alive.count) {
        val id = alive.ids[i]
        if ((world.healths[id]?.hp ?: 0) > 0) aliveTotal++
    }
    emitSnapshotLine(
        renderTickSummaryStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            aliveTotal = aliveTotal,
            visibleTilesFaction1 = fog1.visibleCount(),
            visibleTilesFaction2 = fog2.visibleCount(),
            pathRequests = pathing.lastTickRequests,
            pathSolved = pathing.lastTickSolved,
            pathQueueSize = pathQueue.size,
            avgPathLength = pathing.lastTickAvgPathLen,
            replans = movement.lastTickReplans,
            replansBlocked = movement.lastTickReplansBlocked,
            replansStuck = movement.lastTickReplansStuck,
            attacks = combat.lastTickAttacks,
            kills = combat.lastTickKills,
            despawns = world.removedEventCount,
            builds = commandOutcomeCounters.builds,
            buildFailures = commandOutcomeCounters.buildFailures,
            buildFailureReasons = commandOutcomeCounters.buildFailureReasons.toRecord(),
            trainsQueued = commandOutcomeCounters.trainsQueued,
            trainsCompleted = tickTrainsCompleted,
            trainsCancelled = commandOutcomeCounters.trainsCancelled,
            trainFailures = commandOutcomeCounters.trainFailures,
            trainFailureReasons = commandOutcomeCounters.trainFailureReasons.toRecord(),
            pretty = false
        ),
        snapshotOutPath
    )
}

internal fun emitSnapshotLine(snapshotJson: String, snapshotOutPath: java.nio.file.Path?) {
    if (snapshotOutPath == null) {
        println(snapshotJson)
        return
    }
    val parent = snapshotOutPath.parent
    if (parent != null) Files.createDirectories(parent)
    Files.writeString(
        snapshotOutPath,
        snapshotJson + "\n",
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND
    )
}

private fun loadScriptCommands(pathStr: String): Array<ArrayList<Command>> {
    return loadScriptProgram(pathStr).commandsByTick
}

private data class LoadedProgram(
    val commandsByTick: Array<ArrayList<Command>>,
    val selectionEventsByTick: Array<ArrayList<ScriptRunner.SelectionEvent>>
)

private fun loadScriptProgram(pathStr: String): LoadedProgram {
    val path = resolvePath(pathStr)
    if (!Files.exists(path)) error("Script file not found: $pathStr")
    val program = ScriptRunner.loadProgram(path)
    val cmds = program.commands
    val selectionEvents = program.selections
    if (cmds.isEmpty() && selectionEvents.isEmpty()) return LoadedProgram(arrayOf(), emptyArray())
    var maxTick = 0
    for (c in cmds) if (c.tick > maxTick) maxTick = c.tick
    for (event in selectionEvents) if (event.tick > maxTick) maxTick = event.tick
    val byTick = Array(maxTick + 1) { ArrayList<Command>() }
    val selectionByTick = Array(maxTick + 1) { ArrayList<ScriptRunner.SelectionEvent>() }
    for (c in cmds) {
        byTick[c.tick].add(c)
    }
    for (event in selectionEvents) {
        selectionByTick[event.tick].add(event)
    }
    return LoadedProgram(byTick, selectionByTick)
}

private fun resolvePath(pathStr: String): java.nio.file.Path {
    return resolvePathFromBase(pathStr, Paths.get("").toAbsolutePath().normalize())
}

internal fun resolvePathFromBase(pathStr: String, base: java.nio.file.Path): java.nio.file.Path {
    val p = Paths.get(pathStr)
    if (p.isAbsolute) return p.normalize()
    val direct = base.resolve(pathStr).normalize()
    if (Files.exists(direct)) return direct
    val root = findProjectRoot(base)
    if (root != null) {
        val rooted = root.resolve(pathStr).normalize()
        if (Files.exists(rooted)) return rooted
    }
    return direct
}

internal fun findProjectRoot(start: java.nio.file.Path): java.nio.file.Path? {
    var current: java.nio.file.Path? = start
    while (current != null) {
        if (Files.exists(current.resolve("settings.gradle.kts")) || Files.exists(current.resolve("gradlew"))) {
            return current
        }
        current = current.parent
    }
    return null
}

private fun loadSpawnScriptCommands(pathStr: String): Array<ArrayList<Command>> {
    return loadSpawnScriptProgram(pathStr).commandsByTick
}

private fun loadSpawnScriptProgram(pathStr: String): LoadedProgram {
    val program = loadScriptProgram(pathStr)
    val all = program.commandsByTick
    val selections = program.selectionEventsByTick
    for (tick in all.indices) {
        val cmds = all[tick]
        val it = cmds.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c !is Command.Spawn) it.remove()
        }
    }
    return LoadedProgram(all, selections)
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

private fun mergeSelectionEvents(
    first: Array<ArrayList<ScriptRunner.SelectionEvent>>,
    second: Array<ArrayList<ScriptRunner.SelectionEvent>>
): Array<ArrayList<ScriptRunner.SelectionEvent>> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first
    val size = maxOf(first.size, second.size)
    val merged = Array(size) { ArrayList<ScriptRunner.SelectionEvent>() }
    for (i in 0 until size) {
        if (i < first.size) merged[i].addAll(first[i])
        if (i < second.size) merged[i].addAll(second[i])
    }
    return merged
}

private fun emitSelectionRecord(
    event: ScriptRunner.SelectionEvent,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val json =
        when (val selection = event.selection) {
            is ScriptRunner.Selection.Units ->
                renderSelectionStreamRecordJson(
                    sequence = nextStreamSequence(streamSequence),
                    tick = tick,
                    selectionType = "units",
                    units = selection.ids,
                    pretty = false
                )
            is ScriptRunner.Selection.All ->
                renderSelectionStreamRecordJson(
                    sequence = nextStreamSequence(streamSequence),
                    tick = tick,
                    selectionType = "all",
                    pretty = false
                )
            is ScriptRunner.Selection.Faction ->
                renderSelectionStreamRecordJson(
                    sequence = nextStreamSequence(streamSequence),
                    tick = tick,
                    selectionType = "faction",
                    faction = selection.id,
                    pretty = false
                )
            is ScriptRunner.Selection.Type ->
                renderSelectionStreamRecordJson(
                    sequence = nextStreamSequence(streamSequence),
                    tick = tick,
                    selectionType = "type",
                    typeId = selection.typeId,
                    pretty = false
                )
        }
    emitSnapshotLine(json, snapshotOutPath)
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
                is Command.Build -> {
                    val label = c.label?.let { "@$it " } ?: ""
                    println(
                        "tick=$tick build ${label}faction=${c.faction} type=${c.typeId} " +
                            "tileX=${c.tileX} tileY=${c.tileY} width=${c.width} height=${c.height} " +
                            "hp=${c.hp} armor=${c.armor} minerals=${c.mineralCost} gas=${c.gasCost}"
                    )
                }
                is Command.Train -> {
                    println(
                        "tick=$tick train building=${c.buildingId} type=${c.typeId} " +
                            "buildTicks=${c.buildTicks} minerals=${c.mineralCost} gas=${c.gasCost}"
                    )
                }
                is Command.CancelTrain -> {
                    println("tick=$tick cancelTrain building=${c.buildingId}")
                }
                is Command.Rally -> {
                    println("tick=$tick rally building=${c.buildingId} x=${c.x} y=${c.y}")
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

internal fun validateBuildCommands(commandsByTick: Array<ArrayList<Command>>, data: DataRepo) {
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            if (c !is Command.Build) continue
            val spec = data.buildSpec(c.typeId)
            val width = if (c.width > 0) c.width else (spec?.footprintWidth ?: 0)
            val height = if (c.height > 0) c.height else (spec?.footprintHeight ?: 0)
            val hp = if (c.hp > 0) c.hp else (spec?.hp ?: 0)
            if (spec == null && (c.width <= 0 || c.height <= 0 || c.hp <= 0)) {
                error(
                    "Unknown building typeId '${c.typeId}' in build at tick $tick " +
                        "(missing defaults for width/height/hp)"
                )
            }
            if (width <= 0 || height <= 0 || hp <= 0) {
                error(
                    "Invalid build definition for '${c.typeId}' at tick $tick " +
                        "(resolved width=$width height=$height hp=$hp)"
                )
            }
        }
    }
}

internal fun validateTrainCommands(commandsByTick: Array<ArrayList<Command>>, data: DataRepo) {
    val labeledBuildingTypes = HashMap<Int, String>()
    val labeledQueueState = HashMap<Int, ValidationQueueState>()
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Build -> {
                    val labelId = c.labelId
                    if (labelId != null) {
                        labeledBuildingTypes[labelId] = c.typeId
                        val queueLimit = data.buildSpec(c.typeId)?.productionQueueLimit ?: 5
                        labeledQueueState[labelId] = ValidationQueueState(queueLimit)
                    }
                }
                is Command.Train -> {
                    val spec = data.trainSpec(c.typeId)
                    val buildTicks = if (c.buildTicks > 0) c.buildTicks else (spec?.buildTicks ?: 0)
                    if (spec == null && c.buildTicks <= 0) {
                        error("Unknown unit typeId '${c.typeId}' in train at tick $tick (missing default buildTicks)")
                    }
                    if (buildTicks <= 0) {
                        error("Invalid train definition for '${c.typeId}' at tick $tick (resolved buildTicks=$buildTicks)")
                    }
                    if (c.buildingId < 0) {
                        val buildingType = labeledBuildingTypes[c.buildingId]
                        if (buildingType != null && spec != null && spec.producerTypes.isNotEmpty() && !spec.producerTypes.contains(buildingType)) {
                            error(
                                "Incompatible producer '$buildingType' for '${c.typeId}' in train at tick $tick " +
                                    "(allowed=${spec.producerTypes.joinToString(",")})"
                            )
                        }
                        val queueState = labeledQueueState[c.buildingId]
                        if (queueState != null) {
                            queueState.discardCompleted(tick)
                            if (queueState.size >= queueState.limit) {
                                error(
                                    "Queue limit exceeded for '$buildingType' in train at tick $tick " +
                                        "(limit=${queueState.limit}, queued=${queueState.size})"
                                )
                            }
                            queueState.enqueue(tick, buildTicks)
                        }
                    }
                }
                is Command.CancelTrain -> {
                    if (c.buildingId < 0) {
                        labeledQueueState[c.buildingId]?.discardCompleted(tick)
                        labeledQueueState[c.buildingId]?.cancelLast()
                    }
                }
                else -> Unit
            }
        }
    }
}

private class ValidationQueueState(val limit: Int) {
    private val completionTicks = ArrayDeque<Int>()

    val size: Int
        get() = completionTicks.size

    fun discardCompleted(currentTick: Int) {
        while (completionTicks.isNotEmpty() && completionTicks.first() <= currentTick) {
            completionTicks.removeFirst()
        }
    }

    fun enqueue(currentTick: Int, buildTicks: Int) {
        val startTick = completionTicks.lastOrNull() ?: currentTick
        completionTicks.addLast(startTick + buildTicks)
    }

    fun cancelLast() {
        completionTicks.removeLastOrNull()
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
                is Command.Build -> Unit
                is Command.Train -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in train at tick $tick")
                    }
                }
                is Command.CancelTrain -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in cancelTrain at tick $tick")
                    }
                }
                is Command.Rally -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in rally at tick $tick")
                    }
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
                is Command.Build -> {
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
                is Command.Train -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in train at tick $tick (spawn/build first)")
                    }
                }
                is Command.CancelTrain -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in cancelTrain at tick $tick (spawn/build first)")
                    }
                }
                is Command.Rally -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in rally at tick $tick (spawn/build first)")
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

private val compactStatsJson = Json {
    prettyPrint = false
    encodeDefaults = true
}

@Serializable
internal data class CommandStats(
    val metadata: CommandStatsMetadata? = null,
    val warnings: List<String> = emptyList(),
    val ticks: List<CommandTickCount>,
    val totals: CommandTotals
)

@Serializable
internal data class ReplayMetaReport(
    val replayPath: String? = null,
    val currentMapId: String? = null,
    val currentBuildVersion: String? = null,
    val currentSeed: Long? = null,
    val strictReplayMeta: Boolean = false,
    val strictReplayHash: Boolean = false,
    val metadata: CommandStatsMetadata? = null,
    val warnings: List<String> = emptyList()
)

@Serializable
internal data class CommandStatsMetadata(
    val schema: Int,
    val replayHash: Long? = null,
    val seed: Long? = null,
    val mapId: String? = null,
    val buildVersion: String? = null,
    val eventCount: Int = 0,
    val fileSizeBytes: Long = 0,
    val legacy: Boolean
)

@Serializable
internal data class CommandTickCount(
    val tick: Int,
    val commands: Int,
    val spawns: Int,
    val moves: Int,
    val attacks: Int,
    val selectors: CommandSelectorTotals,
    val breakdown: CommandActionBreakdown
)

@Serializable
internal data class CommandTotals(
    val total: Int,
    val spawns: Int,
    val moves: Int,
    val attacks: Int,
    val selectors: CommandSelectorTotals,
    val breakdown: CommandActionBreakdown
)

@Serializable
internal data class CommandSelectorTotals(
    val direct: Int,
    val faction: Int,
    val type: Int
)

@Serializable
internal data class CommandActionBreakdown(
    val move: CommandSelectorTotals,
    val attack: CommandSelectorTotals
)

internal fun buildCommandStats(
    commandsByTick: Array<ArrayList<Command>>,
    replayMeta: ReplayMetadata?
): CommandStats {
    var total = 0
    var spawns = 0
    var moves = 0
    var attacks = 0
    var direct = 0
    var faction = 0
    var type = 0
    var moveDirect = 0
    var moveFaction = 0
    var moveType = 0
    var attackDirect = 0
    var attackFaction = 0
    var attackType = 0
    val ticks = ArrayList<CommandTickCount>()
    for (tick in commandsByTick.indices) {
        val tickCommands = commandsByTick[tick]
        val count = tickCommands.size
        var tickSpawns = 0
        var tickMoves = 0
        var tickAttacks = 0
        var tickDirect = 0
        var tickFaction = 0
        var tickType = 0
        var tickMoveDirect = 0
        var tickMoveFaction = 0
        var tickMoveType = 0
        var tickAttackDirect = 0
        var tickAttackFaction = 0
        var tickAttackType = 0
        for (cmd in tickCommands) {
            when (cmd) {
                is Command.Move -> {
                    tickMoves++
                    tickDirect++
                    tickMoveDirect++
                }
                is Command.MoveFaction -> {
                    tickMoves++
                    tickFaction++
                    tickMoveFaction++
                }
                is Command.MoveType -> {
                    tickMoves++
                    tickType++
                    tickMoveType++
                }
                is Command.Attack -> {
                    tickAttacks++
                    tickDirect++
                    tickAttackDirect++
                }
                is Command.AttackFaction -> {
                    tickAttacks++
                    tickFaction++
                    tickAttackFaction++
                }
                is Command.AttackType -> {
                    tickAttacks++
                    tickType++
                    tickAttackType++
                }
                is Command.Spawn -> tickSpawns++
                is Command.Build -> Unit
                is Command.Train -> Unit
                is Command.CancelTrain -> Unit
                is Command.Rally -> Unit
            }
        }
        if (count > 0) {
            ticks.add(
                CommandTickCount(
                    tick = tick,
                    commands = count,
                    spawns = tickSpawns,
                    moves = tickMoves,
                    attacks = tickAttacks,
                    selectors = CommandSelectorTotals(direct = tickDirect, faction = tickFaction, type = tickType),
                    breakdown =
                        CommandActionBreakdown(
                            move =
                                CommandSelectorTotals(
                                    direct = tickMoveDirect,
                                    faction = tickMoveFaction,
                                    type = tickMoveType
                                ),
                            attack =
                                CommandSelectorTotals(
                                    direct = tickAttackDirect,
                                    faction = tickAttackFaction,
                                    type = tickAttackType
                                )
                        )
                )
            )
            total += count
        }
        for (cmd in tickCommands) {
            when (cmd) {
                is Command.Move -> {
                    moves++
                    direct++
                    moveDirect++
                }
                is Command.MoveFaction -> {
                    moves++
                    faction++
                    moveFaction++
                }
                is Command.MoveType -> {
                    moves++
                    type++
                    moveType++
                }
                is Command.Attack -> {
                    attacks++
                    direct++
                    attackDirect++
                }
                is Command.AttackFaction -> {
                    attacks++
                    faction++
                    attackFaction++
                }
                is Command.AttackType -> {
                    attacks++
                    type++
                    attackType++
                }
                is Command.Spawn -> spawns++
                is Command.Build -> Unit
                is Command.Train -> Unit
                is Command.CancelTrain -> Unit
                is Command.Rally -> Unit
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
                    mapId = it.mapId,
                    buildVersion = it.buildVersion,
                    eventCount = it.eventCount,
                    fileSizeBytes = it.fileSizeBytes,
                    legacy = it.legacy
                )
            },
        warnings = replayCompatibilityWarnings(replayMeta),
        ticks = ticks,
        totals = CommandTotals(
            total = total,
            spawns = spawns,
            moves = moves,
            attacks = attacks,
            selectors = CommandSelectorTotals(direct = direct, faction = faction, type = type),
            breakdown =
                CommandActionBreakdown(
                    move = CommandSelectorTotals(direct = moveDirect, faction = moveFaction, type = moveType),
                    attack =
                        CommandSelectorTotals(
                            direct = attackDirect,
                            faction = attackFaction,
                            type = attackType
                        )
                )
        )
    )
}

internal fun buildReplayMetaReport(
    replayMeta: ReplayMetadata?,
    replayPath: String? = null,
    currentMapId: String? = null,
    currentBuildVersion: String? = null,
    currentSeed: Long? = null,
    strictReplayMeta: Boolean = false,
    strictReplayHash: Boolean = false
): ReplayMetaReport {
    return ReplayMetaReport(
        replayPath = replayPath,
        currentMapId = currentMapId,
        currentBuildVersion = currentBuildVersion,
        currentSeed = currentSeed,
        strictReplayMeta = strictReplayMeta,
        strictReplayHash = strictReplayHash,
        metadata =
            replayMeta?.let {
                CommandStatsMetadata(
                    schema = it.schema,
                    replayHash = it.replayHash,
                    seed = it.seed,
                    mapId = it.mapId,
                    buildVersion = it.buildVersion,
                    eventCount = it.eventCount,
                    fileSizeBytes = it.fileSizeBytes,
                    legacy = it.legacy
                )
            },
        warnings = replayCompatibilityWarnings(replayMeta)
    )
}

internal fun renderCommandStatsJson(stats: CommandStats, pretty: Boolean = true): String {
    return if (pretty) statsJson.encodeToString(stats) else compactStatsJson.encodeToString(stats)
}

internal fun renderReplayMetaJson(report: ReplayMetaReport, pretty: Boolean = true): String {
    return if (pretty) statsJson.encodeToString(report) else compactStatsJson.encodeToString(report)
}

private const val COMPACT_TICK_PREVIEW_COUNT = 5
private const val COMPACT_TICK_THRESHOLD = 12

internal fun renderCommandStatsText(stats: CommandStats): String {
    val lines = ArrayList<String>(stats.ticks.size + 8)
    lines.add("command stats:")
    for (warning in stats.warnings) {
        lines.add(warning)
    }
    val meta = stats.metadata
    if (meta != null) {
        lines.add(
            "replay metadata: schema=${meta.schema} seed=${meta.seed} " +
                "mapId=${meta.mapId} buildVersion=${meta.buildVersion} " +
                "replayHash=${meta.replayHash} eventCount=${meta.eventCount} fileSizeBytes=${meta.fileSizeBytes}"
        )
    }
    if (stats.ticks.size <= COMPACT_TICK_THRESHOLD) {
        for (tick in stats.ticks) {
            lines.add(formatTickStatsLine(tick))
        }
    } else {
        for (i in 0 until COMPACT_TICK_PREVIEW_COUNT) {
            lines.add(formatTickStatsLine(stats.ticks[i]))
        }
        val omitted = stats.ticks.size - (COMPACT_TICK_PREVIEW_COUNT * 2)
        lines.add("... $omitted ticks omitted ...")
        for (i in stats.ticks.size - COMPACT_TICK_PREVIEW_COUNT until stats.ticks.size) {
            lines.add(formatTickStatsLine(stats.ticks[i]))
        }
    }
    lines.add(
        "total=${stats.totals.total} spawns=${stats.totals.spawns} " +
            "moves=${stats.totals.moves} attacks=${stats.totals.attacks}"
    )
    lines.add(
        "selectors: direct=${stats.totals.selectors.direct} " +
            "faction=${stats.totals.selectors.faction} type=${stats.totals.selectors.type}"
    )
    lines.add(
        "move selectors: direct=${stats.totals.breakdown.move.direct} " +
            "faction=${stats.totals.breakdown.move.faction} type=${stats.totals.breakdown.move.type}"
    )
    lines.add(
        "attack selectors: direct=${stats.totals.breakdown.attack.direct} " +
            "faction=${stats.totals.breakdown.attack.faction} type=${stats.totals.breakdown.attack.type}"
    )
    return lines.joinToString(separator = "\n")
}

private fun formatTickStatsLine(tick: CommandTickCount): String {
    return "tick=${tick.tick} commands=${tick.commands} spawns=${tick.spawns} " +
        "moves=${tick.moves} attacks=${tick.attacks} " +
        "selectors=${tick.selectors.direct}/${tick.selectors.faction}/${tick.selectors.type} " +
        "move=${tick.breakdown.move.direct}/${tick.breakdown.move.faction}/${tick.breakdown.move.type} " +
        "attack=${tick.breakdown.attack.direct}/${tick.breakdown.attack.faction}/${tick.breakdown.attack.type}"
}

private fun printCommandStats(stats: CommandStats) {
    println(renderCommandStatsText(stats))
}

private fun printReplayCompatibilityWarnings(meta: ReplayMetadata?) {
    for (warning in replayCompatibilityWarnings(meta)) {
        println(warning)
    }
}

internal fun replayCompatibilityWarnings(meta: ReplayMetadata?): List<String> {
    if (meta == null || meta.legacy) return emptyList()
    val warnings = ArrayList<String>(2)
    val replayMap = meta.mapId
    if (replayMap != null && replayMap != DEMO_MAP_ID) {
        warnings.add("replay warning: mapId=$replayMap current=$DEMO_MAP_ID")
    }
    val replayBuild = meta.buildVersion
    if (replayBuild != null && replayBuild != BUILD_VERSION) {
        warnings.add("replay warning: buildVersion=$replayBuild current=$BUILD_VERSION")
    }
    return warnings
}

internal fun requireReplayCompatibility(meta: ReplayMetadata?, strict: Boolean) {
    if (!strict) return
    val warnings = replayCompatibilityWarnings(meta)
    if (warnings.isNotEmpty()) {
        error(warnings.joinToString(separator = "\n"))
    }
}

internal fun currentRuntimeMetadataLine(seed: Long?): String {
    return "runtime metadata: mapId=$DEMO_MAP_ID buildVersion=$BUILD_VERSION seed=$seed"
}

internal fun renderCommandOutcomeLogSuffix(
    counters: CommandOutcomeCounters,
    trainsCompleted: Int
): String {
    val parts = ArrayList<String>(4)
    if (counters.builds > 0) parts.add("builds=${counters.builds}")
    if (counters.buildFailures > 0) {
        parts.add("buildFails=${counters.buildFailures}[${formatBuildFailureReasons(counters.buildFailureReasons)}]")
    }
    if (counters.trainsQueued > 0 || trainsCompleted > 0 || counters.trainsCancelled > 0) {
        parts.add("train=q${counters.trainsQueued}/c$trainsCompleted/x${counters.trainsCancelled}")
    }
    if (counters.trainFailures > 0) {
        parts.add("trainFails=${counters.trainFailures}[${formatTrainFailureReasons(counters.trainFailureReasons)}]")
    }
    return if (parts.isEmpty()) "" else "  " + parts.joinToString(" ")
}

internal fun renderAggregateOutcomeSummary(
    totalBuilds: Int,
    totalBuildFailures: Int,
    totalBuildFailureReasons: BuildFailureCounterSet,
    totalTrainsQueued: Int,
    totalTrainsCompleted: Int,
    totalTrainsCancelled: Int,
    totalTrainFailures: Int,
    totalTrainFailureReasons: TrainFailureCounterSet
): String? {
    if (
        totalBuilds == 0 &&
        totalBuildFailures == 0 &&
        totalTrainsQueued == 0 &&
        totalTrainsCompleted == 0 &&
        totalTrainsCancelled == 0 &&
        totalTrainFailures == 0
    ) {
        return null
    }
    val parts = ArrayList<String>(4)
    parts.add("builds=$totalBuilds")
    if (totalBuildFailures > 0) {
        parts.add("buildFails=$totalBuildFailures[${formatBuildFailureReasons(totalBuildFailureReasons)}]")
    }
    parts.add("train=q$totalTrainsQueued/c$totalTrainsCompleted/x$totalTrainsCancelled")
    if (totalTrainFailures > 0) {
        parts.add("trainFails=$totalTrainFailures[${formatTrainFailureReasons(totalTrainFailureReasons)}]")
    }
    return "command outcomes: " + parts.joinToString(" ")
}

internal fun formatBuildFailureReasons(reasons: BuildFailureCounterSet): String =
    listOfNotNull(
        reasons.invalidDefinition.takeIf { it > 0 }?.let { "invalidDefinition=$it" },
        reasons.invalidFootprint.takeIf { it > 0 }?.let { "invalidFootprint=$it" },
        reasons.invalidPlacement.takeIf { it > 0 }?.let { "invalidPlacement=$it" },
        reasons.insufficientResources.takeIf { it > 0 }?.let { "insufficientResources=$it" }
    ).joinToString(",")

internal fun formatTrainFailureReasons(reasons: TrainFailureCounterSet): String =
    listOfNotNull(
        reasons.missingBuilding.takeIf { it > 0 }?.let { "missingBuilding=$it" },
        reasons.invalidUnit.takeIf { it > 0 }?.let { "invalidUnit=$it" },
        reasons.invalidBuildTime.takeIf { it > 0 }?.let { "invalidBuildTime=$it" },
        reasons.incompatibleProducer.takeIf { it > 0 }?.let { "incompatibleProducer=$it" },
        reasons.insufficientResources.takeIf { it > 0 }?.let { "insufficientResources=$it" },
        reasons.queueFull.takeIf { it > 0 }?.let { "queueFull=$it" },
        reasons.nothingToCancel.takeIf { it > 0 }?.let { "nothingToCancel=$it" }
    ).joinToString(",")

data class CommandOutcomeCounters(
    var builds: Int = 0,
    var buildFailures: Int = 0,
    val buildFailureReasons: BuildFailureCounterSet = BuildFailureCounterSet(),
    var trainsQueued: Int = 0,
    var trainsCancelled: Int = 0,
    var trainFailures: Int = 0,
    val trainFailureReasons: TrainFailureCounterSet = TrainFailureCounterSet()
)

data class BuildFailureCounterSet(
    var invalidDefinition: Int = 0,
    var invalidFootprint: Int = 0,
    var invalidPlacement: Int = 0,
    var insufficientResources: Int = 0
) {
    fun add(other: BuildFailureCounterSet) {
        invalidDefinition += other.invalidDefinition
        invalidFootprint += other.invalidFootprint
        invalidPlacement += other.invalidPlacement
        insufficientResources += other.insufficientResources
    }

    fun toRecord(): BuildFailureCounts =
        BuildFailureCounts(
            invalidDefinition = invalidDefinition,
            invalidFootprint = invalidFootprint,
            invalidPlacement = invalidPlacement,
            insufficientResources = insufficientResources
        )
}

data class TrainFailureCounterSet(
    var missingBuilding: Int = 0,
    var invalidUnit: Int = 0,
    var invalidBuildTime: Int = 0,
    var incompatibleProducer: Int = 0,
    var insufficientResources: Int = 0,
    var queueFull: Int = 0,
    var nothingToCancel: Int = 0
) {
    fun add(other: TrainFailureCounterSet) {
        missingBuilding += other.missingBuilding
        invalidUnit += other.invalidUnit
        invalidBuildTime += other.invalidBuildTime
        incompatibleProducer += other.incompatibleProducer
        insufficientResources += other.insufficientResources
        queueFull += other.queueFull
        nothingToCancel += other.nothingToCancel
    }

    fun toRecord(): TrainFailureCounts =
        TrainFailureCounts(
            missingBuilding = missingBuilding,
            invalidUnit = invalidUnit,
            invalidBuildTime = invalidBuildTime,
            incompatibleProducer = incompatibleProducer,
            insufficientResources = insufficientResources,
            queueFull = queueFull,
            nothingToCancel = nothingToCancel
        )
}

fun issue(
    cmd: Command,
    world: World,
    recorder: starkraft.sim.replay.Recorder,
    data: DataRepo? = null,
    labelMap: MutableMap<String, Int> = mutableMapOf(),
    labelIdMap: MutableMap<Int, Int> = mutableMapOf(),
    snapshotOutPath: java.nio.file.Path? = null,
    streamSequence: LongArray? = null,
    buildings: BuildingPlacementSystem? = null,
    production: BuildingProductionSystem? = null,
    outcomeCounters: CommandOutcomeCounters? = null
) {
    recorder.onCommand(cmd)
    if (snapshotOutPath != null && streamSequence != null) {
        emitSnapshotLine(
            renderCommandStreamRecordJson(cmd, sequence = nextStreamSequence(streamSequence), pretty = false),
            snapshotOutPath
        )
    }
    when (cmd) {
        is Command.Move -> {
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "move", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
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
            emitOrderQueueRecord(cmd.tick, "move", applied, world, snapshotOutPath, streamSequence)
        }
        is Command.MoveFaction -> {
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "move", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "move", applied, world, snapshotOutPath, streamSequence)
        }
        is Command.MoveType -> {
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "move", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "move", applied, world, snapshotOutPath, streamSequence)
        }

        is Command.Attack -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "attack", applied, target, null, null, snapshotOutPath, streamSequence)
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
            emitOrderQueueRecord(cmd.tick, "attack", applied, world, snapshotOutPath, streamSequence)
        }
        is Command.AttackFaction -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "attack", applied, target, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.Attack(target))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attack", applied, world, snapshotOutPath, streamSequence)
        }
        is Command.AttackType -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "attack", applied, target, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.Attack(target))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attack", applied, world, snapshotOutPath, streamSequence)
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
            if (snapshotOutPath != null && streamSequence != null) {
                emitSnapshotLine(
                    renderSpawnStreamRecordJson(
                        sequence = nextStreamSequence(streamSequence),
                        tick = cmd.tick,
                        entityId = id,
                        faction = cmd.faction,
                        typeId = cmd.typeId,
                        x = cmd.x,
                        y = cmd.y,
                        vision = cmd.vision,
                        label = cmd.label,
                        labelId = cmd.labelId,
                        pretty = false
                    ),
                    snapshotOutPath
                )
            }
        }
        is Command.Build -> {
            val placement = buildings ?: error("Build requires BuildingPlacementSystem")
            val repo = data ?: error("Build requires DataRepo")
            val spec = repo.buildSpec(cmd.typeId)
            val width = if (cmd.width > 0) cmd.width else (spec?.footprintWidth ?: 0)
            val height = if (cmd.height > 0) cmd.height else (spec?.footprintHeight ?: 0)
            val hp = if (cmd.hp > 0) cmd.hp else (spec?.hp ?: 0)
            val armor = if (cmd.armor > 0) cmd.armor else (spec?.armor ?: 0)
            val mineralCost = if (cmd.mineralCost > 0) cmd.mineralCost else (spec?.mineralCost ?: 0)
            val gasCost = if (cmd.gasCost > 0) cmd.gasCost else (spec?.gasCost ?: 0)
            val clearance = spec?.placementClearance ?: 0
            if (width <= 0 || height <= 0 || hp <= 0) {
                if (outcomeCounters != null) {
                    outcomeCounters.buildFailures++
                    outcomeCounters.buildFailureReasons.invalidDefinition++
                }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "build",
                    reason = "invalidDefinition",
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    tileX = cmd.tileX,
                    tileY = cmd.tileY
                )
                return
            }
            val result =
                placement.placeResult(
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    tileX = cmd.tileX,
                    tileY = cmd.tileY,
                    width = width,
                    height = height,
                    hp = hp,
                    clearance = clearance,
                    armor = armor,
                    mineralCost = mineralCost,
                    gasCost = gasCost
                )
            val id = result.entityId
            if (id == null) {
                if (outcomeCounters != null) {
                    outcomeCounters.buildFailures++
                    when (result.failure) {
                        BuildFailureReason.INSUFFICIENT_RESOURCES -> outcomeCounters.buildFailureReasons.insufficientResources++
                        BuildFailureReason.INVALID_FOOTPRINT -> outcomeCounters.buildFailureReasons.invalidFootprint++
                        else -> outcomeCounters.buildFailureReasons.invalidPlacement++
                    }
                }
                val reason =
                    when (result.failure) {
                        BuildFailureReason.INSUFFICIENT_RESOURCES -> "insufficientResources"
                        BuildFailureReason.INVALID_FOOTPRINT -> "invalidFootprint"
                        else -> "invalidPlacement"
                    }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "build",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    tileX = cmd.tileX,
                    tileY = cmd.tileY
                )
                return
            }
            if (outcomeCounters != null) outcomeCounters.builds++
            if (cmd.label != null) {
                labelMap[cmd.label] = id
            }
            if (cmd.labelId != null) {
                labelIdMap[cmd.labelId] = id
            }
            if (snapshotOutPath != null && streamSequence != null) {
                emitSnapshotLine(
                    renderSpawnStreamRecordJson(
                        sequence = nextStreamSequence(streamSequence),
                        tick = cmd.tick,
                        entityId = id,
                        faction = cmd.faction,
                        typeId = cmd.typeId,
                        x = cmd.tileX.toFloat() + width.toFloat() * 0.5f,
                        y = cmd.tileY.toFloat() + height.toFloat() * 0.5f,
                        vision = null,
                        label = null,
                        labelId = null,
                        pretty = false
                    ),
                    snapshotOutPath
                )
            }
        }
        is Command.Train -> {
            val productionSystem = production ?: error("Train requires BuildingProductionSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            val repo = data ?: error("Train requires DataRepo")
            val spec = repo.trainSpec(cmd.typeId)
            val failure = productionSystem.enqueueResult(
                buildingId = buildingId,
                typeId = cmd.typeId,
                buildTicks = if (cmd.buildTicks > 0) cmd.buildTicks else (spec?.buildTicks ?: 0),
                mineralCost = if (cmd.mineralCost > 0) cmd.mineralCost else (spec?.mineralCost ?: 0),
                gasCost = if (cmd.gasCost > 0) cmd.gasCost else (spec?.gasCost ?: 0)
            )
            if (failure != null) {
                if (outcomeCounters != null) {
                    outcomeCounters.trainFailures++
                    when (failure) {
                        TrainFailureReason.MISSING_BUILDING -> outcomeCounters.trainFailureReasons.missingBuilding++
                        TrainFailureReason.INVALID_UNIT -> outcomeCounters.trainFailureReasons.invalidUnit++
                        TrainFailureReason.INVALID_BUILD_TIME -> outcomeCounters.trainFailureReasons.invalidBuildTime++
                        TrainFailureReason.INCOMPATIBLE_PRODUCER -> outcomeCounters.trainFailureReasons.incompatibleProducer++
                        TrainFailureReason.INSUFFICIENT_RESOURCES -> outcomeCounters.trainFailureReasons.insufficientResources++
                        TrainFailureReason.QUEUE_FULL -> outcomeCounters.trainFailureReasons.queueFull++
                    }
                }
                val reason =
                    when (failure) {
                        TrainFailureReason.MISSING_BUILDING -> "missingBuilding"
                        TrainFailureReason.INVALID_UNIT -> "invalidUnit"
                        TrainFailureReason.INVALID_BUILD_TIME -> "invalidBuildTime"
                        TrainFailureReason.INCOMPATIBLE_PRODUCER -> "incompatibleProducer"
                        TrainFailureReason.INSUFFICIENT_RESOURCES -> "insufficientResources"
                        TrainFailureReason.QUEUE_FULL -> "queueFull"
                    }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "train",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId,
                    typeId = cmd.typeId
                )
            } else {
                if (outcomeCounters != null) outcomeCounters.trainsQueued++
            }
        }
        is Command.CancelTrain -> {
            val productionSystem = production ?: error("CancelTrain requires BuildingProductionSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            if (productionSystem.cancelLast(buildingId)) {
                if (outcomeCounters != null) outcomeCounters.trainsCancelled++
            } else {
                if (outcomeCounters != null) {
                    outcomeCounters.trainFailures++
                    outcomeCounters.trainFailureReasons.nothingToCancel++
                }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "cancelTrain",
                    reason = "nothingToCancel",
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId
                )
            }
        }
        is Command.Rally -> {
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            if (!world.footprints.containsKey(buildingId)) {
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "rally",
                    reason = "missingBuilding",
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId
                )
                return
            }
            world.rallyPoints[buildingId] = RallyPoint(cmd.x, cmd.y)
        }
    }
}

private fun resolveLabelId(id: Int, labelIdMap: Map<Int, Int>): Int {
    if (id >= 0) return id
    return labelIdMap[id] ?: error("Unknown label id '$id' (spawn missing?)")
}

private fun collectDirectTargets(cmdUnits: IntArray, world: World, labelIdMap: Map<Int, Int>): IntArray {
    if (cmdUnits.size == 1 && cmdUnits[0] == 0) {
        val ids = IntArray(world.orders.size)
        var count = 0
        for (id in world.orders.keys) {
            ids[count++] = resolveLabelId(id, labelIdMap)
        }
        return if (count == ids.size) ids else ids.copyOf(count)
    }
    val ids = IntArray(cmdUnits.size)
    for (i in cmdUnits.indices) {
        ids[i] = resolveLabelId(cmdUnits[i], labelIdMap)
    }
    return ids
}

private fun collectFactionTargets(faction: Int, world: World): IntArray {
    val ids = IntArray(world.tags.size)
    var count = 0
    for ((id, tag) in world.tags) {
        if (tag.faction == faction) {
            ids[count++] = id
        }
    }
    return if (count == ids.size) ids else ids.copyOf(count)
}

private fun collectTypeTargets(typeId: String, world: World): IntArray {
    val ids = IntArray(world.tags.size)
    var count = 0
    for ((id, tag) in world.tags) {
        if (tag.typeId == typeId) {
            ids[count++] = id
        }
    }
    return if (count == ids.size) ids else ids.copyOf(count)
}

private fun emitOrderAppliedRecord(
    tick: Int,
    orderType: String,
    units: IntArray,
    target: Int?,
    x: Float?,
    y: Float?,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderOrderAppliedStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            orderType = orderType,
            units = units,
            target = target,
            x = x,
            y = y,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitOrderQueueRecord(
    tick: Int,
    orderType: String,
    units: IntArray,
    world: World,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val entities = ArrayList<OrderQueueEntityRecord>(units.size)
    for (i in units.indices) {
        val id = units[i]
        entities.add(OrderQueueEntityRecord(entityId = id, queueSize = world.orders[id]?.items?.size ?: 0))
    }
    emitSnapshotLine(
        renderOrderQueueStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            orderType = orderType,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitPathAssignedRecord(
    pathing: PathfindingSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || pathing.lastTickAssignedCount == 0) return
    val entities = ArrayList<PathAssignedEventRecord>(pathing.lastTickAssignedCount)
    for (i in 0 until pathing.lastTickAssignedCount) {
        entities.add(
            PathAssignedEventRecord(
                entityId = pathing.assignedEntityId(i),
                pathLength = pathing.assignedLength(i),
                goalX = pathing.assignedGoalX(i),
                goalY = pathing.assignedGoalY(i)
            )
        )
    }
    emitSnapshotLine(
        renderPathAssignedStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitPathProgressRecord(
    movement: MovementSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || movement.lastTickProgressCount == 0) return
    val entities = ArrayList<PathProgressEventRecord>(movement.lastTickProgressCount)
    for (i in 0 until movement.lastTickProgressCount) {
        entities.add(
            PathProgressEventRecord(
                entityId = movement.progressEntityId(i),
                waypointIndex = movement.progressWaypointIndex(i),
                remainingNodes = movement.progressRemainingNodes(i),
                completed = movement.progressCompleted(i)
            )
        )
    }
    emitSnapshotLine(
        renderPathProgressStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitOccupancyChangeRecord(
    tick: Int,
    changes: List<OccupancyChangeEventRecord>,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || changes.isEmpty()) return
    emitSnapshotLine(
        renderOccupancyChangeStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            changes = changes,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitMapStateRecord(
    map: MapGrid,
    occ: OccupancyGrid,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val blockedTiles = ArrayList<MapBlockedTileRecord>()
    val weightedTiles = ArrayList<MapCostTileRecord>()
    val staticOccupancyTiles = ArrayList<MapBlockedTileRecord>()
    for (y in 0 until map.height) {
        for (x in 0 until map.width) {
            if (map.isBlocked(x, y)) {
                blockedTiles.add(MapBlockedTileRecord(x, y))
            }
            val cost = map.cost(x, y)
            if (cost != 1f) {
                weightedTiles.add(MapCostTileRecord(x, y, cost))
            }
            if (occ.isStaticBlocked(x, y)) {
                staticOccupancyTiles.add(MapBlockedTileRecord(x, y))
            }
        }
    }
    emitSnapshotLine(
        renderMapStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            width = map.width,
            height = map.height,
            blockedTiles = blockedTiles,
            weightedTiles = weightedTiles,
            staticOccupancyTiles = staticOccupancyTiles,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitVisionRecord(
    fog1: FogGrid,
    fog2: FogGrid,
    tick: Int,
    prev1: BooleanArray,
    prev2: BooleanArray,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val changes = ArrayList<VisionChangeEventRecord>()
    collectVisionChanges(fog1, 1, prev1, changes)
    collectVisionChanges(fog2, 2, prev2, changes)
    if (changes.isEmpty()) return
    emitSnapshotLine(
        renderVisionStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            changes = changes,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun collectVisionChanges(
    fog: FogGrid,
    faction: Int,
    previous: BooleanArray,
    out: MutableList<VisionChangeEventRecord>
) {
    var idx = 0
    for (y in 0 until fog.height) {
        for (x in 0 until fog.width) {
            val visible = fog.isVisibleTile(x, y)
            if (previous[idx] != visible) {
                previous[idx] = visible
                out.add(VisionChangeEventRecord(faction = faction, x = x, y = y, visible = visible))
            }
            idx++
        }
    }
}
