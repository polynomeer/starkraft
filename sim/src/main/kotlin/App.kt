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
import starkraft.sim.client.MapResourceNodeRecord
import starkraft.sim.client.ConstructionStateEntityRecord
import starkraft.sim.client.BuilderStateEntityRecord
import starkraft.sim.client.DropoffStateEntityRecord
import starkraft.sim.client.HarvestCycleEventRecord
import starkraft.sim.client.HarvesterRetargetEventRecord
import starkraft.sim.client.HarvesterStateEntityRecord
import starkraft.sim.client.PathAssignedEventRecord
import starkraft.sim.client.PathProgressEventRecord
import starkraft.sim.client.ProducerStateEntityRecord
import starkraft.sim.client.ResearchStateEntityRecord
import starkraft.sim.client.ResearchStateFactionRecord
import starkraft.sim.client.ResearchEventRecord
import starkraft.sim.client.ResourceDeltaEventRecord
import starkraft.sim.client.ResourceDeltaSummaryFactionRecord
import starkraft.sim.client.ResourceNodeEventRecord
import starkraft.sim.client.VisionChangeEventRecord
import starkraft.sim.client.renderCombatStreamRecordJson
import starkraft.sim.client.renderClientSnapshotJson
import starkraft.sim.client.renderBuildFailureStreamRecordJson
import starkraft.sim.client.renderBuilderStateStreamRecordJson
import starkraft.sim.client.renderCommandStreamRecordJson
import starkraft.sim.client.renderCommandFailureStreamRecordJson
import starkraft.sim.client.renderCommandAckStreamRecordJson
import starkraft.sim.client.renderConstructionStateStreamRecordJson
import starkraft.sim.client.renderDamageStreamRecordJson
import starkraft.sim.client.renderDespawnStreamRecordJson
import starkraft.sim.client.renderDropoffStateStreamRecordJson
import starkraft.sim.client.renderEconomyStreamRecordJson
import starkraft.sim.client.renderHarvestCycleStreamRecordJson
import starkraft.sim.client.renderHarvesterRetargetStreamRecordJson
import starkraft.sim.client.renderHarvesterStateStreamRecordJson
import starkraft.sim.client.renderMetricsStreamRecordJson
import starkraft.sim.client.renderMapStateStreamRecordJson
import starkraft.sim.client.renderOrderAppliedStreamRecordJson
import starkraft.sim.client.renderOrderQueueStreamRecordJson
import starkraft.sim.client.renderOccupancyChangeStreamRecordJson
import starkraft.sim.client.renderPathAssignedStreamRecordJson
import starkraft.sim.client.renderPathProgressStreamRecordJson
import starkraft.sim.client.renderProducerFailureStreamRecordJson
import starkraft.sim.client.renderProducerStateStreamRecordJson
import starkraft.sim.client.renderRallyFailureStreamRecordJson
import starkraft.sim.client.renderRallyStreamRecordJson
import starkraft.sim.client.ResearchFailureCounts
import starkraft.sim.client.renderResearchStateStreamRecordJson
import starkraft.sim.client.renderResearchStreamRecordJson
import starkraft.sim.client.renderResourceDeltaStreamRecordJson
import starkraft.sim.client.renderResourceDeltaSummaryStreamRecordJson
import starkraft.sim.client.renderResourceNodeStreamRecordJson
import starkraft.sim.client.renderSnapshotSessionEndJson
import starkraft.sim.client.renderSnapshotSessionStartJson
import starkraft.sim.client.renderSnapshotStreamRecordJson
import starkraft.sim.client.renderSelectionStreamRecordJson
import starkraft.sim.client.renderSessionStatsStreamRecordJson
import starkraft.sim.client.renderSpawnStreamRecordJson
import starkraft.sim.client.renderTickSummaryStreamRecordJson
import starkraft.sim.client.renderTrainFailureStreamRecordJson
import starkraft.sim.client.renderVisionStreamRecordJson
import starkraft.sim.client.ProductionEventRecord
import starkraft.sim.client.PlayControlState
import starkraft.sim.client.TrainFailureCounts
import starkraft.sim.client.parsePlayControlState
import starkraft.sim.client.renderPlayControlState
import starkraft.sim.client.renderProductionStreamRecordJson
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.net.Command
import starkraft.sim.net.InputJson
import starkraft.sim.net.InputTailReader
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
import java.util.IdentityHashMap
import java.util.Random

object Time {
    const val TICK_MS = 20
}

private const val DEMO_MAP_ID = "demo-32x32-obstacles"
private const val BUILD_VERSION = "1.0-SNAPSHOT"
private val pendingHarvesterRetargetEvents = ArrayList<HarvesterRetargetEventRecord>()
private val RESOURCE_NODE_KINDS = setOf("MineralField", "GasGeyser")
private val CLI_FLAGS_WITH_VALUE =
    setOf(
        "--seed",
        "--replay",
        "--script",
        "--inputJson",
        "--inputTail",
        "--spawnScript",
        "--record",
        "--replayOut",
        "--snapshotOut",
        "--ticks",
        "--replayTicks",
        "--snapshotEvery",
        "--replayDump",
        "--playControlFile"
    )
private val CLI_TOGGLE_FLAGS =
    setOf(
        "--noSleep",
        "--scriptValidate",
        "--scriptDryRun",
        "--replayValidateOnly",
        "--dumpWorldHash",
        "--strictReplayHash",
        "--strictReplayMeta",
        "--printEntities",
        "--printOrders",
        "--labelDump",
        "--replayStats",
        "--replayStatsJson",
        "--replayMetaJson",
        "--snapshotJson",
        "--compactJson",
        "--version",
        "--help",
        "-h"
    )

fun main(args: Array<String>) {
    if (hasFlag(args, "--version")) {
        println(buildVersionText())
        return
    }
    if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
        println(buildAppUsageText())
        return
    }
    validateCliArgs(args)

    // Load data resources
    val unitsResource = object {}.javaClass.getResource("/data/units.json")
        ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
    val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
        ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")
    val buildingsResource = object {}.javaClass.getResource("/data/buildings.json")
        ?: error("Resource '/data/buildings.json' not found. Ensure it exists in the resources directory.")
    val techsResource = object {}.javaClass.getResource("/data/techs.json")
        ?: error("Resource '/data/techs.json' not found. Ensure it exists in the resources directory.")

    val unitsJson = unitsResource.readText()
    val weaponsJson = weaponsResource.readText()
    val buildingsJson = buildingsResource.readText()
    val techsJson = techsResource.readText()
    val data = DataRepo(unitsJson, weaponsJson, buildingsJson, techsJson)
    
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
                buildTicks = depotBuild.buildTicks,
                clearance = depotBuild.placementClearance,
                armor = depotBuild.armor,
                mineralCost = depotBuild.mineralCost,
                gasCost = depotBuild.gasCost
            )
        } else {
            null
        }
    if (depotId != null) {
        val startingResearch = data.researchSpec("AdvancedTraining")
        if (startingResearch != null) {
            research.enqueueResult(
                depotId,
                techId = startingResearch.techId,
                buildTicks = startingResearch.buildTicks,
                mineralCost = startingResearch.mineralCost,
                gasCost = startingResearch.gasCost
            )
        }
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
    val mineralNodeId =
        world.spawn(
            Transform(4f, 2f),
            UnitTag(1, "MineralField"),
            Health(1000, 1000),
            w = null
        )
    world.resourceNodes[mineralNodeId] = ResourceNode(remaining = 250)
    val workerId =
        world.spawn(
            Transform(3.6f, 2f),
            UnitTag(1, "Worker"),
            Health(40, 40),
            w = null
        )
    world.harvesters[workerId] = Harvester(targetNodeId = mineralNodeId, harvestPerTick = 2)

    val replayPath = parseReplayPath(args)
    val scriptPath = parseScriptPath(args)
    val inputJsonPath = parseInputJsonPath(args)
    val inputTailPath = parseInputTailPath(args)
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
    val playControlPath = parsePlayControlPath(args)
    val resolvedReplayPath = replayPath?.let(::resolvePath)
    val resolvedSnapshotOutPath = snapshotOutPath?.let(::resolvePath)
    val resolvedPlayControlPath = playControlPath?.let(::resolvePath)
    val replayMeta =
        if (resolvedReplayPath != null) ReplayIO.inspect(resolvedReplayPath) else null
    val streamSequence = longArrayOf(0L)
    val visionPrevTeam1 = BooleanArray(fog1.width * fog1.height)
    val visionPrevTeam2 = BooleanArray(fog2.width * fog2.height)
    validateCliSemantics(
        replayPath = replayPath,
        tickLimit = tickLimit,
        replayTicks = replayTicks,
        scriptPath = scriptPath,
        inputJsonPath = inputJsonPath,
        spawnScriptPath = spawnScriptPath,
        replayValidateOnly = replayValidateOnly,
        strictReplayHash = strictReplayHash,
        strictReplayMeta = strictReplayMeta,
        replayStats = replayStats,
        replayStatsJson = replayStatsJson,
        replayMetaJson = replayMetaJson,
        snapshotJson = snapshotJson,
        compactJson = compactJson,
        scriptValidate = scriptValidate,
        scriptDryRun = scriptDryRun,
        noSleep = noSleep,
        dumpWorldHash = dumpWorldHash,
        printEntities = printEntities,
        printOrders = printOrders,
        labelDump = labelDump,
        recordPath = recordPath,
        replayOutPath = replayOutPath,
        replayDumpPath = replayDumpPath,
        snapshotOutPath = snapshotOutPath,
        snapshotEvery = snapshotEvery,
        inputTailPath = inputTailPath,
        playControlPath = playControlPath
    )
    validateCliNumericSemantics(
        tickLimit = tickLimit,
        replayTicks = replayTicks,
        snapshotEvery = snapshotEvery
    )
    validateOutputPathConflicts(
        recordPath = recordPath,
        replayOutPath = replayOutPath,
        replayDumpPath = replayDumpPath,
        snapshotOutPath = snapshotOutPath
    )
    validateLivePathConflicts(
        snapshotOutPath = snapshotOutPath,
        inputTailPath = inputTailPath
    )
    validateScriptPathConflicts(
        scriptPath = scriptPath,
        spawnScriptPath = spawnScriptPath
    )
    validateInputOutputPathConflicts(
        replayPath = replayPath,
        recordPath = recordPath,
        replayOutPath = replayOutPath,
        replayDumpPath = replayDumpPath
    )
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
        emitMapStateRecord(world, map, occ, resolvedSnapshotOutPath, streamSequence)
    }
    requireReplayCompatibility(replayMeta, strictReplayMeta)
    val baseProgram: LoadedProgram = when {
        replayPath != null -> LoadedProgram(loadReplayCommands(replayPath, strictReplayHash), emptyArray())
        inputJsonPath != null -> loadInputJsonProgram(inputJsonPath)
        scriptPath != null -> loadScriptProgram(scriptPath)
        else -> LoadedProgram(arrayOf(), emptyArray())
    }
    val spawnProgram: LoadedProgram =
        if (spawnScriptPath != null) loadSpawnScriptProgram(spawnScriptPath) else LoadedProgram(arrayOf(), emptyArray())
    val baseCommands = baseProgram.commandsByTick
    val spawnCommands = spawnProgram.commandsByTick
    val commandsByTick = mergeCommands(spawnCommands, baseCommands)
    val selectionEventsByTick = mergeSelectionEvents(spawnProgram.selectionEventsByTick, baseProgram.selectionEventsByTick)
    val commandRequestIds = IdentityHashMap<Command, String>().apply {
        putAll(spawnProgram.commandRequestIds)
        putAll(baseProgram.commandRequestIds)
    }
    val liveCommandsByTick = ArrayList<ArrayList<Command>>()
    val liveSelectionEventsByTick = ArrayList<ArrayList<ScriptRunner.SelectionEvent>>()
    val liveCommandRequestIds = IdentityHashMap<Command, String>()
    val inputTailReader = inputTailPath?.let { InputTailReader.open(resolvePath(it)) }
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
    var totalBuildsCancelled = 0
    var totalBuildFailures = 0
    val totalBuildFailureReasons = BuildFailureCounterSet()
    var totalTrainsQueued = 0
    var totalTrainsCompleted = 0
    var totalTrainsCancelled = 0
    var totalTrainFailures = 0
    val totalTrainFailureReasons = TrainFailureCounterSet()
    var totalResearchQueued = 0
    var totalResearchCancelled = 0
    var totalResearchCompleted = 0
    var totalResearchFailures = 0
    val totalResearchFailureReasons = ResearchFailureCounterSet()
    val totalResourceDeltas = ResourceDeltaCounterSet()
    var totalHarvestedMinerals = 0
    var totalHarvestedGas = 0
    var totalDepletedNodes = 0
    var totalChangedResourceNodes = 0
    var totalHarvestedMineralsFaction1 = 0
    var totalHarvestedMineralsFaction2 = 0
    var totalHarvestedGasFaction1 = 0
    var totalHarvestedGasFaction2 = 0
    var totalHarvestPickupCount = 0
    var totalHarvestDepositCount = 0
    var totalHarvestPickupAmount = 0
    var totalHarvestDepositAmount = 0
    var totalHarvesterRetargets = 0

    if ((scriptValidate || scriptDryRun) && (scriptPath != null || spawnScriptPath != null)) {
        validateSpawnTypes(commandsByTick, data)
        validateBuildCommands(commandsByTick, data, world)
        validateTrainCommands(commandsByTick, data, world)
        validateResearchCommands(commandsByTick, data, world)
        validateSelectionUnitIds(selectionEventsByTick, commandsByTick, world)
        if (scriptPath != null) {
            validateCommandUnitIds(commandsByTick, world)
            validateLabelUsage(commandsByTick)
        }
        if (scriptDryRun) {
            println("script dry run ok")
        }
        printScriptSelections(selectionEventsByTick)
        printScriptCommands(commandsByTick)
        if (resolvedSnapshotOutPath != null) {
            Files.deleteIfExists(resolvedSnapshotOutPath)
        }
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
    if ((replayStats || replayStatsJson) && replayPath != null) {
        val stats = buildCommandStats(commandsByTick, replayMeta)
        if (replayStats) {
            printCommandStats(stats)
        }
        if (replayStatsJson) {
            println(renderCommandStatsJson(stats, pretty = !compactJson))
        }
        return
    }
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
        emitMapStateRecord(world, map, occ, resolvedSnapshotOutPath, streamSequence)
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
    ensurePlayControlFile(resolvedPlayControlPath)
    var tick = 0
    while (tick < totalTicks) {
        inputTailReader?.poll(liveCommandsByTick, liveSelectionEventsByTick, liveCommandRequestIds)
        val commandOutcomeCounters = CommandOutcomeCounters()
        var tickTrainsCompleted = 0
        world.clearRemovedEvents()
        resources.clearTickEvents()
        production.clearTickEvents()
        research.clearTickEvents()
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
        processTickInput(
            tick,
            selectionEventsByTick.asList(),
            commandsByTick.asList(),
            commandRequestIds,
            world,
            recorder,
            data,
            labelMap,
            labelIdMap,
            resolvedSnapshotOutPath,
            streamSequence,
            buildings,
            production,
            research,
            commandOutcomeCounters
        )
        processTickInput(
            tick,
            liveSelectionEventsByTick,
            liveCommandsByTick,
            liveCommandRequestIds,
            world,
            recorder,
            data,
            labelMap,
            labelIdMap,
            resolvedSnapshotOutPath,
            streamSequence,
            buildings,
            production,
            research,
            commandOutcomeCounters
        )

        harvest.tick()
        emitHarvestCycleRecord(harvest, tick, resolvedSnapshotOutPath, streamSequence)
        emitResourceNodeRecord(world, harvest, tick, resolvedSnapshotOutPath, streamSequence)
        removeDepletedResourceNodes(world, harvest)
        val tickHarvesterRetargets = pendingHarvesterRetargetEvents.size
        emitHarvesterRetargetRecord(tick, resolvedSnapshotOutPath, streamSequence)
        emitResourceDeltaRecord(resources, tick, resolvedSnapshotOutPath, streamSequence)
        val tickResourceDeltas = collectResourceDeltaCounters(resources)
        emitResourceDeltaSummaryRecord(tickResourceDeltas, tick, resolvedSnapshotOutPath, streamSequence)
        occupancy.tick()
        construction.tick()
        research.tick()
        emitResearchRecord(research, tick, resolvedSnapshotOutPath, streamSequence)
        production.tick()
        var tickResearchCompleted = 0
        var tickResearchCancelled = 0
        for (i in 0 until research.lastTickEventCount) {
            if (research.eventKind(i) == ResearchSystem.EVENT_COMPLETE) tickResearchCompleted++
            if (research.eventKind(i) == ResearchSystem.EVENT_CANCEL) tickResearchCancelled++
        }
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
        totalBuildsCancelled += commandOutcomeCounters.buildsCancelled
        totalBuildFailures += commandOutcomeCounters.buildFailures
        totalBuildFailureReasons.add(commandOutcomeCounters.buildFailureReasons)
        totalTrainsQueued += commandOutcomeCounters.trainsQueued
        totalTrainsCompleted += tickTrainsCompleted
        totalTrainsCancelled += commandOutcomeCounters.trainsCancelled
        totalTrainFailures += commandOutcomeCounters.trainFailures
        totalTrainFailureReasons.add(commandOutcomeCounters.trainFailureReasons)
        totalResearchQueued += commandOutcomeCounters.researchQueued
        totalResearchCancelled += commandOutcomeCounters.researchCancelled
        totalResearchCompleted += tickResearchCompleted
        totalResearchFailures += commandOutcomeCounters.researchFailures
        totalResearchFailureReasons.add(commandOutcomeCounters.researchFailureReasons)
        totalResourceDeltas.add(tickResourceDeltas)
        totalHarvestedMinerals += harvest.lastTickHarvestedMinerals
        totalHarvestedGas += harvest.lastTickHarvestedGas
        totalDepletedNodes += harvest.lastTickDepletedNodes
        totalChangedResourceNodes += harvest.lastTickEventCount
        totalHarvestedMineralsFaction1 += harvest.lastTickHarvestedMineralsFaction1
        totalHarvestedMineralsFaction2 += harvest.lastTickHarvestedMineralsFaction2
        totalHarvestedGasFaction1 += harvest.lastTickHarvestedGasFaction1
        totalHarvestedGasFaction2 += harvest.lastTickHarvestedGasFaction2
        totalHarvestPickupCount += harvest.lastTickPickupCount
        totalHarvestDepositCount += harvest.lastTickDepositCount
        totalHarvestPickupAmount += harvest.lastTickPickupAmount
        totalHarvestDepositAmount += harvest.lastTickDepositAmount
        totalHarvesterRetargets += tickHarvesterRetargets

        if (snapshotEvery != null && shouldEmitSnapshotAtTick(tick, snapshotEvery)) {
            emitVisionRecord(fog1, fog2, tick, visionPrevTeam1, visionPrevTeam2, resolvedSnapshotOutPath, streamSequence)
            emitMetricsRecord(world, fog1, fog2, tick, pathing, pathQueue, movement, resolvedSnapshotOutPath, streamSequence)
            emitEconomyRecord(world, tick, resolvedSnapshotOutPath, streamSequence)
            emitHarvesterStateRecord(world, tick, resolvedSnapshotOutPath, streamSequence)
            emitProducerStateRecord(world, data, tick, resolvedSnapshotOutPath, streamSequence)
            emitConstructionStateRecord(world, data, tick, resolvedSnapshotOutPath, streamSequence)
            emitBuilderStateRecord(world, data, tick, resolvedSnapshotOutPath, streamSequence)
            emitResearchStateRecord(world, data, tick, resolvedSnapshotOutPath, streamSequence)
            emitDropoffStateRecord(world, data, tick, resolvedSnapshotOutPath, streamSequence)
            emitTickSummaryRecord(
                world,
                fog1,
                fog2,
                data,
                tick,
                pathing,
                pathQueue,
                movement,
                combat,
                commandOutcomeCounters,
                tickTrainsCompleted,
                tickResearchCancelled,
                tickResearchCompleted,
                tickResourceDeltas,
                tickHarvesterRetargets,
                harvest,
                resolvedSnapshotOutPath,
                streamSequence
            )
            emitClientSnapshot(world, map, fog1, fog2, tick, seed, data, compactJson, resolvedSnapshotOutPath, streamSequence)
        }

        if (tick % 25 == 0) {
            val m1 = world.tags.filter { it.value.faction == 1 }.keys.size
            val m2 = world.tags.filter { it.value.faction == 2 }.keys.size
            val dropoffCounts = countDropoffBuildings(world, data)
            val outcomeSuffix =
                renderCommandOutcomeLogSuffix(
                    commandOutcomeCounters,
                    tickTrainsCompleted,
                    tickResearchCancelled,
                    tickResearchCompleted,
                    world.researchQueues.size,
                    harvest.lastTickPickupCount,
                    harvest.lastTickDepositCount,
                    harvest.lastTickPickupAmount,
                    harvest.lastTickDepositAmount,
                    tickHarvesterRetargets,
                    dropoffCounts.faction1,
                    dropoffCounts.faction2,
                    dropoffCounts.minerals,
                    dropoffCounts.gas
                )
            println(
                "tick=$tick  alive: team1=$m1 team2=$m2  visibleTiles: t1=${fog1.visibleCount()} t2=${fog2.visibleCount()} " +
                    "buildings=${world.footprints.size} prodQueues=${world.productionQueues.size} researchQueues=${world.researchQueues.size} " +
                    "techs: t1=${world.unlockedTechs(1).size} t2=${world.unlockedTechs(2).size} researchDone=$tickResearchCompleted " +
                    "minerals: t1=${world.stockpiles[1]?.minerals ?: 0} t2=${world.stockpiles[2]?.minerals ?: 0} " +
                    "harvested=${harvest.lastTickHarvestedMinerals}/${harvest.lastTickHarvestedGas} " +
                    "nodeRemaining=${world.resourceNodes[mineralNodeId]?.remaining ?: 0} " +
                    "pathReq=${pathing.lastTickRequests} pathSolved=${pathing.lastTickSolved} queue=${pathQueue.size} avgLen=${"%.2f".format(pathing.lastTickAvgPathLen)} " +
                    "replans=${movement.lastTickReplans} " +
                    "blocked=${movement.lastTickReplansBlocked} stuck=${movement.lastTickReplansStuck}$outcomeSuffix"
            )
        }
        tick++
        waitForNextTick(noSleep, resolvedPlayControlPath)
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
    val dropoffCounts = countDropoffBuildings(world, data)
    val finalOutcomeSummary =
        renderAggregateOutcomeSummary(
            totalBuilds,
            totalBuildsCancelled,
            totalBuildFailures,
            totalBuildFailureReasons,
            totalTrainsQueued,
            totalTrainsCompleted,
            totalTrainsCancelled,
            totalTrainFailures,
            totalTrainFailureReasons,
            totalResearchQueued,
            totalResearchCancelled,
            totalResearchCompleted,
            totalResearchFailures,
            totalResearchFailureReasons,
            world.researchQueues.size,
            totalHarvestedMinerals,
            totalHarvestedGas,
            totalDepletedNodes,
            totalChangedResourceNodes,
            totalHarvestedMineralsFaction1,
            totalHarvestedMineralsFaction2,
            totalHarvestedGasFaction1,
            totalHarvestedGasFaction2,
            totalHarvestPickupCount,
            totalHarvestDepositCount,
            totalHarvestPickupAmount,
            totalHarvestDepositAmount,
            totalHarvesterRetargets,
            dropoffCounts.faction1,
            dropoffCounts.faction2,
            dropoffCounts.minerals,
            dropoffCounts.gas,
            world.resourceNodes.values.count { it.remaining > 0 },
            world.resourceNodes.values.sumOf { it.remaining }
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

    if (snapshotJson) {
        emitClientSnapshot(world, map, fog1, fog2, tick, seed, data, compactJson, resolvedSnapshotOutPath, streamSequence)
    }
    if (resolvedSnapshotOutPath != null && (snapshotJson || snapshotEvery != null)) {
        val dropoffCounts = countDropoffBuildings(world, data)
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
                buildsCancelled = totalBuildsCancelled,
                buildFailures = totalBuildFailures,
                buildFailureReasons = totalBuildFailureReasons.toRecord(),
                trainsQueued = totalTrainsQueued,
                trainsCompleted = totalTrainsCompleted,
                trainsCancelled = totalTrainsCancelled,
                trainFailures = totalTrainFailures,
                trainFailureReasons = totalTrainFailureReasons.toRecord(),
                researchQueueBuildings = world.researchQueues.size,
                researchQueued = totalResearchQueued,
                researchCancelled = totalResearchCancelled,
                researchCompleted = totalResearchCompleted,
                researchFailures = totalResearchFailures,
                researchFailureReasons = totalResearchFailureReasons.toRecord(),
                mineralsSpent = totalResourceDeltas.mineralsSpent,
                gasSpent = totalResourceDeltas.gasSpent,
                mineralsRefunded = totalResourceDeltas.mineralsRefunded,
                gasRefunded = totalResourceDeltas.gasRefunded,
                mineralsSpentFaction1 = totalResourceDeltas.mineralsSpentFaction1,
                mineralsSpentFaction2 = totalResourceDeltas.mineralsSpentFaction2,
                gasSpentFaction1 = totalResourceDeltas.gasSpentFaction1,
                gasSpentFaction2 = totalResourceDeltas.gasSpentFaction2,
                mineralsRefundedFaction1 = totalResourceDeltas.mineralsRefundedFaction1,
                mineralsRefundedFaction2 = totalResourceDeltas.mineralsRefundedFaction2,
                gasRefundedFaction1 = totalResourceDeltas.gasRefundedFaction1,
                gasRefundedFaction2 = totalResourceDeltas.gasRefundedFaction2,
                harvestedMinerals = totalHarvestedMinerals,
                harvestedGas = totalHarvestedGas,
                harvestedMineralsFaction1 = totalHarvestedMineralsFaction1,
                harvestedMineralsFaction2 = totalHarvestedMineralsFaction2,
                harvestedGasFaction1 = totalHarvestedGasFaction1,
                harvestedGasFaction2 = totalHarvestedGasFaction2,
                harvestPickupCount = totalHarvestPickupCount,
                harvestDepositCount = totalHarvestDepositCount,
                harvestPickupAmount = totalHarvestPickupAmount,
                harvestDepositAmount = totalHarvestDepositAmount,
                harvesterRetargets = totalHarvesterRetargets,
                dropoffBuildingsFaction1 = dropoffCounts.faction1,
                dropoffBuildingsFaction2 = dropoffCounts.faction2,
                mineralDropoffBuildings = dropoffCounts.minerals,
                gasDropoffBuildings = dropoffCounts.gas,
                depletedNodes = totalDepletedNodes,
                changedResourceNodes = totalChangedResourceNodes,
                finalVisibleTilesFaction1 = fog1.visibleCount(),
                finalVisibleTilesFaction2 = fog2.visibleCount(),
                finalMineralsFaction1 = world.stockpiles[1]?.minerals ?: 0,
                finalMineralsFaction2 = world.stockpiles[2]?.minerals ?: 0,
                finalGasFaction1 = world.stockpiles[1]?.gas ?: 0,
                finalGasFaction2 = world.stockpiles[2]?.gas ?: 0,
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

    inputTailReader?.close()

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
    return parseStringOption(args, "--replay")
}

private fun parseRecordPath(args: Array<String>): String? {
    return parseStringOption(args, "--record")
}

private fun parseReplayOutPath(args: Array<String>): String? {
    return parseStringOption(args, "--replayOut")
}

private fun parseSnapshotOutPath(args: Array<String>): String? {
    return parseStringOption(args, "--snapshotOut")
}

private fun parseReplayDumpPath(args: Array<String>): String? {
    return parseStringOption(args, "--replayDump")
}

private fun parsePlayControlPath(args: Array<String>): String? {
    return parseStringOption(args, "--playControlFile")
}

private fun parseScriptPath(args: Array<String>): String? {
    return parseStringOption(args, "--script")
}

private fun parseInputJsonPath(args: Array<String>): String? {
    return parseStringOption(args, "--inputJson")
}

private fun parseInputTailPath(args: Array<String>): String? {
    return parseStringOption(args, "--inputTail")
}

private fun parseSpawnScriptPath(args: Array<String>): String? {
    return parseStringOption(args, "--spawnScript")
}

private fun parseTickLimit(args: Array<String>): Int? {
    return parseIntOption(args, "--ticks")
}

private fun parseSnapshotEvery(args: Array<String>): Int? {
    return parseIntOption(args, "--snapshotEvery")
}

private fun hasFlag(args: Array<String>, flag: String): Boolean =
    args.any { it == flag }

private fun isOptionToken(token: String): Boolean {
    if (!token.startsWith("-")) return false
    val key = token.substringBefore("=")
    return CLI_FLAGS_WITH_VALUE.contains(key) || CLI_TOGGLE_FLAGS.contains(key)
}

internal fun validateCliArgs(args: Array<String>) {
    val seenOptions = HashSet<String>(args.size)
    var i = 0
    while (i < args.size) {
        val raw = args[i]
        val key = raw.substringBefore("=")
        when {
            CLI_FLAGS_WITH_VALUE.contains(key) -> {
                if (!seenOptions.add(key)) {
                    error("Duplicate option '$key'")
                }
                if (raw.contains("=")) {
                    val value = raw.substringAfter("=")
                    if (value.isBlank()) {
                        error("Missing value for option '$key'")
                    }
                    i++
                } else {
                    if (i + 1 >= args.size) {
                        error("Missing value for option '$key'")
                    }
                    val value = args[i + 1]
                    if (value.isBlank() || isOptionToken(value)) {
                        error("Missing value for option '$key'")
                    }
                    i += 2
                }
            }
            CLI_TOGGLE_FLAGS.contains(key) -> {
                if (!seenOptions.add(key)) {
                    error("Duplicate option '$key'")
                }
                if (raw != key) {
                    error("Option '$key' does not accept a value")
                }
                i++
            }
            raw.startsWith("-") -> error("Unknown option '$raw'")
            else -> error("Unknown argument '$raw'")
        }
    }
}

internal fun validateCliSemantics(
    replayPath: String?,
    tickLimit: Int?,
    replayTicks: Int?,
    scriptPath: String?,
    inputJsonPath: String?,
    spawnScriptPath: String?,
    replayValidateOnly: Boolean,
    strictReplayHash: Boolean,
    strictReplayMeta: Boolean,
    replayStats: Boolean,
    replayStatsJson: Boolean,
    replayMetaJson: Boolean,
    snapshotJson: Boolean,
    compactJson: Boolean,
    scriptValidate: Boolean,
    scriptDryRun: Boolean,
    noSleep: Boolean = false,
    dumpWorldHash: Boolean = false,
    printEntities: Boolean = false,
    printOrders: Boolean = false,
    labelDump: Boolean = false,
    recordPath: String? = null,
    replayOutPath: String? = null,
    replayDumpPath: String? = null,
    snapshotOutPath: String? = null,
    snapshotEvery: Int? = null,
    inputTailPath: String? = null,
    playControlPath: String? = null
) {
    if (tickLimit != null && replayTicks != null) {
        error("--ticks cannot be combined with --replayTicks")
    }
    val baseSources =
        listOfNotNull(
            replayPath?.let { "replay" },
            scriptPath?.let { "script" },
            inputJsonPath?.let { "inputJson" }
        )
    if (baseSources.size > 1) {
        error("Base input sources are mutually exclusive: ${baseSources.joinToString(", ")}")
    }
    if (replayPath == null) {
        if (replayTicks != null) error("--replayTicks requires --replay")
        if (replayValidateOnly) error("--replayValidateOnly requires --replay")
        if (strictReplayHash) error("--strictReplayHash requires --replay")
        if (strictReplayMeta) error("--strictReplayMeta requires --replay")
        if (replayStats) error("--replayStats requires --replay")
        if (replayStatsJson) error("--replayStatsJson requires --replay")
        if (replayMetaJson) error("--replayMetaJson requires --replay")
    }
    if (replayValidateOnly && replayMetaJson) {
        error("--replayValidateOnly cannot be combined with --replayMetaJson")
    }
    if (replayValidateOnly && replayStats) {
        error("--replayValidateOnly cannot be combined with --replayStats")
    }
    if (replayValidateOnly && replayStatsJson) {
        error("--replayValidateOnly cannot be combined with --replayStatsJson")
    }
    if (replayValidateOnly && replayTicks != null) {
        error("--replayValidateOnly cannot be combined with --replayTicks")
    }
    if (replayValidateOnly && spawnScriptPath != null) {
        error("--replayValidateOnly cannot be combined with --spawnScript")
    }
    if (replayValidateOnly && inputTailPath != null) {
        error("--replayValidateOnly cannot be combined with --inputTail")
    }
    if (replayValidateOnly && tickLimit != null) {
        error("--replayValidateOnly cannot be combined with --ticks")
    }
    if (replayValidateOnly && snapshotJson) {
        error("--replayValidateOnly cannot be combined with --snapshotJson")
    }
    if (replayValidateOnly && snapshotOutPath != null) {
        error("--replayValidateOnly cannot be combined with --snapshotOut")
    }
    if (replayValidateOnly && snapshotEvery != null) {
        error("--replayValidateOnly cannot be combined with --snapshotEvery")
    }
    if (replayValidateOnly && recordPath != null) {
        error("--replayValidateOnly cannot be combined with --record")
    }
    if (replayValidateOnly && replayOutPath != null) {
        error("--replayValidateOnly cannot be combined with --replayOut")
    }
    if (replayValidateOnly && replayDumpPath != null) {
        error("--replayValidateOnly cannot be combined with --replayDump")
    }
    if (replayValidateOnly && playControlPath != null) {
        error("--replayValidateOnly cannot be combined with --playControlFile")
    }
    if (replayValidateOnly && noSleep) {
        error("--replayValidateOnly cannot be combined with --noSleep")
    }
    if (replayValidateOnly && printEntities) {
        error("--replayValidateOnly cannot be combined with --printEntities")
    }
    if (replayValidateOnly && printOrders) {
        error("--replayValidateOnly cannot be combined with --printOrders")
    }
    if (replayValidateOnly && labelDump) {
        error("--replayValidateOnly cannot be combined with --labelDump")
    }
    if (replayMetaJson && replayStats) {
        error("--replayMetaJson cannot be combined with --replayStats")
    }
    if (replayMetaJson && replayStatsJson) {
        error("--replayMetaJson cannot be combined with --replayStatsJson")
    }
    if (replayStats || replayStatsJson) {
        if (tickLimit != null) error("--replayStats/--replayStatsJson cannot be combined with --ticks")
        if (replayTicks != null) error("--replayStats/--replayStatsJson cannot be combined with --replayTicks")
        if (snapshotJson) error("--replayStats/--replayStatsJson cannot be combined with --snapshotJson")
        if (snapshotOutPath != null) error("--replayStats/--replayStatsJson cannot be combined with --snapshotOut")
        if (snapshotEvery != null) error("--replayStats/--replayStatsJson cannot be combined with --snapshotEvery")
        if (recordPath != null) error("--replayStats/--replayStatsJson cannot be combined with --record")
        if (replayOutPath != null) error("--replayStats/--replayStatsJson cannot be combined with --replayOut")
        if (replayDumpPath != null) error("--replayStats/--replayStatsJson cannot be combined with --replayDump")
        if (spawnScriptPath != null) error("--replayStats/--replayStatsJson cannot be combined with --spawnScript")
        if (inputTailPath != null) error("--replayStats/--replayStatsJson cannot be combined with --inputTail")
        if (playControlPath != null) error("--replayStats/--replayStatsJson cannot be combined with --playControlFile")
        if (noSleep) error("--replayStats/--replayStatsJson cannot be combined with --noSleep")
        if (printEntities) error("--replayStats/--replayStatsJson cannot be combined with --printEntities")
        if (printOrders) error("--replayStats/--replayStatsJson cannot be combined with --printOrders")
        if (labelDump) error("--replayStats/--replayStatsJson cannot be combined with --labelDump")
    }
    if (replayMetaJson) {
        if (tickLimit != null) error("--replayMetaJson cannot be combined with --ticks")
        if (replayTicks != null) error("--replayMetaJson cannot be combined with --replayTicks")
        if (snapshotJson) error("--replayMetaJson cannot be combined with --snapshotJson")
        if (snapshotOutPath != null) error("--replayMetaJson cannot be combined with --snapshotOut")
        if (snapshotEvery != null) error("--replayMetaJson cannot be combined with --snapshotEvery")
        if (recordPath != null) error("--replayMetaJson cannot be combined with --record")
        if (replayOutPath != null) error("--replayMetaJson cannot be combined with --replayOut")
        if (replayDumpPath != null) error("--replayMetaJson cannot be combined with --replayDump")
        if (spawnScriptPath != null) error("--replayMetaJson cannot be combined with --spawnScript")
        if (inputTailPath != null) error("--replayMetaJson cannot be combined with --inputTail")
        if (playControlPath != null) error("--replayMetaJson cannot be combined with --playControlFile")
        if (noSleep) error("--replayMetaJson cannot be combined with --noSleep")
        if (printEntities) error("--replayMetaJson cannot be combined with --printEntities")
        if (printOrders) error("--replayMetaJson cannot be combined with --printOrders")
        if (labelDump) error("--replayMetaJson cannot be combined with --labelDump")
    }
    if (compactJson && !replayStatsJson && !replayMetaJson && !snapshotJson) {
        error("--compactJson requires --replayStatsJson, --replayMetaJson, or --snapshotJson")
    }
    if (snapshotEvery != null && snapshotOutPath == null) {
        error("--snapshotEvery requires --snapshotOut")
    }
    if ((scriptValidate || scriptDryRun) && scriptPath == null && spawnScriptPath == null) {
        error("--scriptValidate/--scriptDryRun require --script or --spawnScript")
    }
    if (dumpWorldHash && (scriptValidate || scriptDryRun || replayValidateOnly || replayMetaJson || replayStats || replayStatsJson)) {
        error("--dumpWorldHash cannot be combined with validation/report-only modes")
    }
    if (
        replayDumpPath != null &&
            scriptPath == null &&
            spawnScriptPath == null &&
            inputJsonPath == null &&
            inputTailPath == null
    ) {
        error("--replayDump requires --script, --spawnScript, --inputJson, or --inputTail")
    }
    if (scriptValidate || scriptDryRun) {
        if (replayPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --replay")
        if (inputJsonPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --inputJson")
        if (tickLimit != null) error("--scriptValidate/--scriptDryRun cannot be combined with --ticks")
        if (replayTicks != null) error("--scriptValidate/--scriptDryRun cannot be combined with --replayTicks")
        if (replayValidateOnly) error("--scriptValidate/--scriptDryRun cannot be combined with --replayValidateOnly")
        if (replayStats) error("--scriptValidate/--scriptDryRun cannot be combined with --replayStats")
        if (replayStatsJson) error("--scriptValidate/--scriptDryRun cannot be combined with --replayStatsJson")
        if (replayMetaJson) error("--scriptValidate/--scriptDryRun cannot be combined with --replayMetaJson")
        if (snapshotJson) error("--scriptValidate/--scriptDryRun cannot be combined with --snapshotJson")
        if (snapshotOutPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --snapshotOut")
        if (snapshotEvery != null) error("--scriptValidate/--scriptDryRun cannot be combined with --snapshotEvery")
        if (recordPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --record")
        if (replayOutPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --replayOut")
        if (replayDumpPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --replayDump")
        if (playControlPath != null) error("--scriptValidate/--scriptDryRun cannot be combined with --playControlFile")
        if (noSleep) error("--scriptValidate/--scriptDryRun cannot be combined with --noSleep")
        if (dumpWorldHash) error("--scriptValidate/--scriptDryRun cannot be combined with --dumpWorldHash")
        if (printEntities) error("--scriptValidate/--scriptDryRun cannot be combined with --printEntities")
        if (printOrders) error("--scriptValidate/--scriptDryRun cannot be combined with --printOrders")
        if (labelDump) error("--scriptValidate/--scriptDryRun cannot be combined with --labelDump")
    }
}

internal fun validateCliNumericSemantics(
    tickLimit: Int?,
    replayTicks: Int?,
    snapshotEvery: Int?
) {
    if (tickLimit != null && tickLimit < 0) {
        error("--ticks must be >= 0 (was $tickLimit)")
    }
    if (replayTicks != null && replayTicks < 0) {
        error("--replayTicks must be >= 0 (was $replayTicks)")
    }
    if (snapshotEvery != null && snapshotEvery <= 0) {
        error("--snapshotEvery must be > 0 (was $snapshotEvery)")
    }
}

internal fun validateOutputPathConflicts(
    recordPath: String?,
    replayOutPath: String?,
    replayDumpPath: String?,
    snapshotOutPath: String?
) {
    val byPath = LinkedHashMap<String, MutableList<String>>()
    fun add(flag: String, path: String?) {
        if (path == null) return
        val normalized = Paths.get(path).toAbsolutePath().normalize().toString()
        byPath.getOrPut(normalized) { ArrayList(2) }.add(flag)
    }
    add("--record", recordPath)
    add("--replayOut", replayOutPath)
    add("--replayDump", replayDumpPath)
    add("--snapshotOut", snapshotOutPath)
    val conflict = byPath.entries.firstOrNull { it.value.size > 1 } ?: return
    error(
        "Output path conflict '${conflict.key}': " +
            conflict.value.joinToString(", ")
    )
}

internal fun validateInputOutputPathConflicts(
    replayPath: String?,
    recordPath: String?,
    replayOutPath: String?,
    replayDumpPath: String?
) {
    if (replayPath == null) return
    val input = Paths.get(replayPath).toAbsolutePath().normalize().toString()
    fun check(flag: String, path: String?) {
        if (path == null) return
        val normalized = Paths.get(path).toAbsolutePath().normalize().toString()
        if (normalized == input) {
            error("Replay input/output path conflict '$normalized': --replay and $flag")
        }
    }
    check("--record", recordPath)
    check("--replayOut", replayOutPath)
    check("--replayDump", replayDumpPath)
}

internal fun validateLivePathConflicts(
    snapshotOutPath: String?,
    inputTailPath: String?
) {
    if (snapshotOutPath == null || inputTailPath == null) return
    val snapshotPath = Paths.get(snapshotOutPath).toAbsolutePath().normalize().toString()
    val inputPath = Paths.get(inputTailPath).toAbsolutePath().normalize().toString()
    if (snapshotPath == inputPath) {
        error("Live path conflict '$snapshotPath': --snapshotOut and --inputTail")
    }
}

internal fun validateScriptPathConflicts(
    scriptPath: String?,
    spawnScriptPath: String?
) {
    if (scriptPath == null || spawnScriptPath == null) return
    val script = Paths.get(scriptPath).toAbsolutePath().normalize().toString()
    val spawn = Paths.get(spawnScriptPath).toAbsolutePath().normalize().toString()
    if (script == spawn) {
        error("Script path conflict '$script': --script and --spawnScript")
    }
}

internal fun buildAppUsageText(): String =
    listOf(
        "Usage: ./gradlew :sim:run --args=\"[options]\"",
        "Core:",
        "  --ticks <n>                 Run for N ticks",
        "  --noSleep                   Disable fixed-tick sleep for fast runs",
        "  --seed <n>                  Set deterministic RNG seed",
        "Input:",
        "  (choose exactly one base source: --replay, --script, or --inputJson)",
        "  --script <path>             Load scripted commands",
        "  --spawnScript <path>        Apply spawn-only script before main script/replay",
        "  --inputJson <path>          Load JSON/NDJSON commands",
        "  --inputTail <path>          Ingest commands live from append-only NDJSON",
        "Replay:",
        "  --replay <path>             Load replay commands",
        "  --record <path>             Save command stream as replay at end",
        "  --replayTicks <n>           Run replay partially for N ticks (with --replay, no --ticks)",
        "  --replayValidateOnly        Validate replay metadata/hash and exit",
        "  --strictReplayHash          Fail when replay hash is missing/mismatched",
        "  --strictReplayMeta          Fail on replay map/build metadata mismatch",
        "  --replayStats               Print per-tick replay command stats and exit",
        "  --replayStatsJson           Print replay stats as JSON and exit",
        "  --replayMetaJson            Print replay metadata/context as JSON and exit",
        "  --replayOut <path>          Write replay command stream",
        "  --replayDump <path>         Dump replay after script/input run",
        "Output/validation:",
        "  --snapshotOut <path>        Write snapshot/stream NDJSON",
        "  --snapshotEvery <n>         Snapshot cadence in ticks (requires --snapshotOut)",
        "  --snapshotJson              Print final client snapshot JSON",
        "  --compactJson               Print JSON outputs in compact form",
        "  --playControlFile <path>    Use external pause/speed control file",
        "  --scriptValidate            Parse+validate script(s) and exit",
        "  --scriptDryRun              Validate and print script selections/commands",
        "                               (standalone: no runtime/replay/output flags)",
        "  --dumpWorldHash             Print final world hash (runtime only)",
        "  --printEntities             Print alive entities at end",
        "  --printOrders               Print pending orders at end",
        "  --labelDump                 Print script label to entity mappings",
        "                               (runtime-only flags are rejected in report-only modes)",
        "  --version                   Print sim build/version context",
        "  --help, -h                  Show this help"
    ).joinToString("\n")

internal fun buildVersionText(): String =
    "starkraft-sim version=$BUILD_VERSION map=$DEMO_MAP_ID"

internal fun ensurePlayControlFile(path: java.nio.file.Path?) {
    if (path == null) return
    path.parent?.let { Files.createDirectories(it) }
    if (!Files.exists(path)) {
        Files.writeString(path, renderPlayControlState(PlayControlState()))
    }
}

internal fun readPlayControlState(path: java.nio.file.Path?): PlayControlState? {
    if (path == null || !Files.exists(path)) return null
    return parsePlayControlState(Files.readString(path))
}

internal fun tickSleepMs(noSleep: Boolean, controlState: PlayControlState?): Long? {
    if (noSleep) return null
    val speed = controlState?.speed ?: 1
    return maxOf(1L, Time.TICK_MS.toLong() / speed.toLong())
}

internal fun waitForNextTick(noSleep: Boolean, controlPath: java.nio.file.Path?) {
    var controlState = readPlayControlState(controlPath)
    while (controlState?.paused == true) {
        Thread.sleep(Time.TICK_MS.toLong())
        controlState = readPlayControlState(controlPath)
    }
    val sleepMs = tickSleepMs(noSleep, controlState) ?: return
    Thread.sleep(sleepMs)
}

private fun parseReplayTicks(args: Array<String>): Int? {
    return parseIntOption(args, "--replayTicks")
}

private fun parseSeed(args: Array<String>): Long? {
    return parseLongOption(args, "--seed")
}

internal fun parseIntOption(args: Array<String>, option: String): Int? {
    val raw = findOptionRawValue(args, option) ?: return null
    return raw.toIntOrNull() ?: error("Invalid integer value '$raw' for option '$option'")
}

internal fun parseLongOption(args: Array<String>, option: String): Long? {
    val raw = findOptionRawValue(args, option) ?: return null
    return raw.toLongOrNull() ?: error("Invalid long value '$raw' for option '$option'")
}

internal fun parseStringOption(args: Array<String>, option: String): String? =
    findOptionRawValue(args, option)

private fun findOptionRawValue(args: Array<String>, option: String): String? {
    var i = 0
    while (i < args.size) {
        val a = args[i]
        when {
            a == option && i + 1 < args.size -> return args[i + 1]
            a.startsWith("$option=") -> return a.substringAfter("=")
        }
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
    data: DataRepo,
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
        data = data,
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
                    starkraft.sim.client.MetricsFactionRecord(
                        1,
                        alive1,
                        fog1.visibleCount(),
                        world.stockpiles[1]?.minerals ?: 0,
                        world.stockpiles[1]?.gas ?: 0
                    ),
                    starkraft.sim.client.MetricsFactionRecord(
                        2,
                        alive2,
                        fog2.visibleCount(),
                        world.stockpiles[2]?.minerals ?: 0,
                        world.stockpiles[2]?.gas ?: 0
                    )
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

private fun emitEconomyRecord(
    world: World,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val factions = ArrayList<starkraft.sim.client.EconomyFactionRecord>(world.stockpiles.size)
    for (faction in world.stockpiles.keys.sorted()) {
        val stockpile = world.stockpiles[faction] ?: continue
        factions.add(starkraft.sim.client.EconomyFactionRecord(faction, stockpile.minerals, stockpile.gas))
    }
    emitSnapshotLine(
        renderEconomyStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            factions = factions,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitResourceDeltaRecord(
    resources: ResourceSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || resources.lastTickEventCount == 0) return
    val events = ArrayList<ResourceDeltaEventRecord>(resources.lastTickEventCount)
    for (i in 0 until resources.lastTickEventCount) {
        val kind =
            when (resources.eventKind(i)) {
                ResourceSystem.EVENT_REFUND -> "refund"
                else -> "spend"
            }
        events.add(
            ResourceDeltaEventRecord(
                faction = resources.eventFaction(i),
                kind = kind,
                minerals = resources.eventMinerals(i),
                gas = resources.eventGas(i)
            )
        )
    }
    emitSnapshotLine(
        renderResourceDeltaStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            events = events,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitResourceDeltaSummaryRecord(
    tickResourceDeltas: ResourceDeltaCounterSet,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderResourceDeltaSummaryStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            factions =
                listOf(
                    ResourceDeltaSummaryFactionRecord(
                        faction = 1,
                        mineralsSpent = tickResourceDeltas.mineralsSpentFaction1,
                        gasSpent = tickResourceDeltas.gasSpentFaction1,
                        mineralsRefunded = tickResourceDeltas.mineralsRefundedFaction1,
                        gasRefunded = tickResourceDeltas.gasRefundedFaction1
                    ),
                    ResourceDeltaSummaryFactionRecord(
                        faction = 2,
                        mineralsSpent = tickResourceDeltas.mineralsSpentFaction2,
                        gasSpent = tickResourceDeltas.gasSpentFaction2,
                        mineralsRefunded = tickResourceDeltas.mineralsRefundedFaction2,
                        gasRefunded = tickResourceDeltas.gasRefundedFaction2
                    )
                ),
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitResourceNodeRecord(
    world: World,
    harvest: ResourceHarvestSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || harvest.lastTickEventCount == 0) return
    val nodes = ArrayList<ResourceNodeEventRecord>(harvest.lastTickEventCount)
    for (i in 0 until harvest.lastTickEventCount) {
        val nodeId = harvest.eventNodeId(i)
        val transform = world.transforms[nodeId] ?: continue
        val tag = world.tags[nodeId] ?: continue
        nodes.add(
            ResourceNodeEventRecord(
                id = nodeId,
                kind = tag.typeId,
                x = transform.x,
                y = transform.y,
                harvested = harvest.eventHarvested(i),
                remaining = harvest.eventRemaining(i),
                depleted = harvest.eventDepleted(i),
                yieldPerTick = world.resourceNodes[nodeId]?.yieldPerTick ?: 1
            )
        )
    }
    if (nodes.isEmpty()) return
    emitSnapshotLine(
        renderResourceNodeStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            nodes = nodes,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitHarvestCycleRecord(
    harvest: ResourceHarvestSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || harvest.lastTickCycleEventCount == 0) return
    val events = ArrayList<HarvestCycleEventRecord>(harvest.lastTickCycleEventCount)
    for (i in 0 until harvest.lastTickCycleEventCount) {
        val kind =
            when (harvest.cycleEventKind(i)) {
                ResourceHarvestSystem.EVENT_DEPOSIT -> "deposit"
                else -> "pickup"
            }
        events.add(
            HarvestCycleEventRecord(
                kind = kind,
                workerId = harvest.cycleEventWorker(i),
                nodeId = harvest.cycleEventNode(i),
                dropoffId = harvest.cycleEventDropoff(i).takeIf { it >= 0 },
                resourceKind = harvest.cycleEventResourceKind(i),
                amount = harvest.cycleEventAmount(i)
            )
        )
    }
    emitSnapshotLine(
        renderHarvestCycleStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            events = events,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitHarvesterRetargetRecord(
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (pendingHarvesterRetargetEvents.isEmpty()) return
    if (snapshotOutPath == null || streamSequence == null) {
        pendingHarvesterRetargetEvents.clear()
        return
    }
    emitSnapshotLine(
        renderHarvesterRetargetStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            events = pendingHarvesterRetargetEvents.toList(),
            pretty = false
        ),
        snapshotOutPath
    )
    pendingHarvesterRetargetEvents.clear()
}

internal fun removeDepletedResourceNodes(world: World, harvest: ResourceHarvestSystem) {
    for (i in 0 until harvest.lastTickEventCount) {
        if (!harvest.eventDepleted(i)) continue
        val nodeId = harvest.eventNodeId(i)
        if (!world.resourceNodes.containsKey(nodeId)) continue
        val nodeTransform = world.transforms[nodeId]
        if (nodeTransform != null) {
            clearHarvestersForNode(world, nodeId, nodeTransform.x, nodeTransform.y)
        }
        world.remove(nodeId, "resourceDepleted")
    }
}

internal fun clearHarvestersForNode(world: World, nodeId: Int, nodeX: Float, nodeY: Float) {
    val depletedKind = world.resourceNodes[nodeId]?.kind
    val workerIds = ArrayList<Int>()
    for ((workerId, harvester) in world.harvesters) {
        if (harvester.targetNodeId == nodeId) {
            workerIds.add(workerId)
        }
    }
    for (workerId in workerIds) {
        val harvester = world.harvesters[workerId] ?: continue
        val queue = world.orders[workerId]?.items
        val first = queue?.firstOrNull() as? Order.Move
        if (first != null && first.tx == nodeX && first.ty == nodeY) {
            queue.removeFirst()
        }
        val nextNodeId = findNearestHarvestNode(world, workerId, depletedKind, excludeNodeId = nodeId)
        if (harvester.cargoAmount > 0) {
            harvester.targetNodeId = nextNodeId ?: -1
            if (nextNodeId != null) {
                pendingHarvesterRetargetEvents.add(
                    HarvesterRetargetEventRecord(
                        workerId = workerId,
                        fromNodeId = nodeId,
                        toNodeId = nextNodeId,
                        resourceKind = depletedKind ?: ResourceNode.KIND_MINERALS
                    )
                )
            }
            if (harvester.returnTargetId < 0 && nextNodeId != null) {
                val nextTransform = world.transforms[nextNodeId] ?: continue
                queue?.addFirst(Order.Move(nextTransform.x, nextTransform.y))
            } else if (nextNodeId == null && harvester.returnTargetId < 0) {
                world.harvesters.remove(workerId)
            }
        } else if (nextNodeId != null) {
            val nextTransform = world.transforms[nextNodeId] ?: continue
            harvester.targetNodeId = nextNodeId
            queue?.addFirst(Order.Move(nextTransform.x, nextTransform.y))
            pendingHarvesterRetargetEvents.add(
                HarvesterRetargetEventRecord(
                    workerId = workerId,
                    fromNodeId = nodeId,
                    toNodeId = nextNodeId,
                    resourceKind = depletedKind ?: ResourceNode.KIND_MINERALS
                )
            )
        } else {
            world.harvesters.remove(workerId)
        }
    }
}

internal fun drainPendingHarvesterRetargetEvents(): List<HarvesterRetargetEventRecord> {
    if (pendingHarvesterRetargetEvents.isEmpty()) return emptyList()
    return pendingHarvesterRetargetEvents.toList().also { pendingHarvesterRetargetEvents.clear() }
}

internal fun findNearestHarvestNode(
    world: World,
    workerId: Int,
    preferredKind: String?,
    excludeNodeId: Int = -1
): Int? {
    val workerTransform = world.transforms[workerId] ?: return null
    var bestId = -1
    var bestRemaining = -1
    var bestDist = Float.POSITIVE_INFINITY
    for ((nodeId, node) in world.resourceNodes) {
        if (nodeId == excludeNodeId || node.remaining <= 0) continue
        if (preferredKind != null && node.kind != preferredKind) continue
        val nodeTransform = world.transforms[nodeId] ?: continue
        val dx = nodeTransform.x - workerTransform.x
        val dy = nodeTransform.y - workerTransform.y
        val dist = (dx * dx) + (dy * dy)
        if (
            node.remaining > bestRemaining ||
            (node.remaining == bestRemaining && (dist < bestDist || (dist == bestDist && nodeId < bestId)))
        ) {
            bestRemaining = node.remaining
            bestDist = dist
            bestId = nodeId
        }
    }
    return bestId.takeIf { it != -1 }
}

private fun emitProducerStateRecord(
    world: World,
    data: DataRepo,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val entities = ArrayList<ProducerStateEntityRecord>()
    val alive = world.aliveSnapshot
    for (i in 0 until alive.count) {
        val id = alive.ids[i]
        val tag = world.tags[id] ?: continue
        val spec = data.buildSpec(tag.typeId) ?: continue
        entities.add(
            ProducerStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                archetype = spec.archetype,
                supportsTraining = spec.supportsTraining,
                supportsRally = spec.supportsRally,
                supportsDropoff = spec.supportsDropoff,
                dropoffResourceKinds = spec.dropoffResourceKinds,
                productionQueueLimit = spec.productionQueueLimit,
                defaultRallyOffsetX = spec.rallyOffsetX,
                defaultRallyOffsetY = spec.rallyOffsetY
            )
        )
    }
    if (entities.isEmpty()) return
    entities.sortBy { it.entityId }
    emitSnapshotLine(
        renderProducerStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitDropoffStateRecord(
    world: World,
    data: DataRepo,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val entities = ArrayList<DropoffStateEntityRecord>()
    val alive = world.aliveSnapshot
    for (i in 0 until alive.count) {
        val id = alive.ids[i]
        val tag = world.tags[id] ?: continue
        val spec = data.buildSpec(tag.typeId) ?: continue
        if (!spec.supportsDropoff) continue
        val transform = world.transforms[id] ?: continue
        entities.add(
            DropoffStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                archetype = spec.archetype,
                x = transform.x,
                y = transform.y
            )
        )
    }
    if (entities.isEmpty()) return
    entities.sortBy { it.entityId }
    emitSnapshotLine(
        renderDropoffStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitConstructionStateRecord(
    world: World,
    data: DataRepo,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    if (world.constructionSites.isEmpty()) return
    val entities = ArrayList<ConstructionStateEntityRecord>(world.constructionSites.size)
    for ((id, site) in world.constructionSites) {
        val tag = world.tags[id] ?: continue
        val health = world.healths[id] ?: continue
        val archetype = data.buildingArchetype(tag.typeId) ?: "genericBuilding"
        entities.add(
            ConstructionStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                archetype = archetype,
                hp = health.hp,
                maxHp = health.maxHp,
                remainingTicks = site.remainingTicks,
                totalTicks = site.totalTicks
            )
        )
    }
    if (entities.isEmpty()) return
    entities.sortBy { it.entityId }
    emitSnapshotLine(
        renderConstructionStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitBuilderStateRecord(
    world: World,
    data: DataRepo,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || world.builderTasks.isEmpty()) return
    val entities = ArrayList<BuilderStateEntityRecord>(world.builderTasks.size)
    val ids = world.builderTasks.keys.sorted()
    for (id in ids) {
        val task = world.builderTasks[id] ?: continue
        val tag = world.tags[id] ?: continue
        val archetype = data.unitArchetype(tag.typeId) ?: "unit"
        entities.add(
            BuilderStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                archetype = archetype,
                targetBuildingId = task.targetBuildingId
            )
        )
    }
    if (entities.isEmpty()) return
    emitSnapshotLine(
        renderBuilderStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitResearchStateRecord(
    world: World,
    data: DataRepo,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    if (world.researchQueues.isEmpty() && world.unlockedTechsByFaction.isEmpty()) return

    val factions = ArrayList<ResearchStateFactionRecord>(world.unlockedTechsByFaction.size)
    for (faction in world.unlockedTechsByFaction.keys.sorted()) {
        val unlocked = world.unlockedTechs(faction).sorted()
        if (unlocked.isEmpty()) continue
        factions.add(ResearchStateFactionRecord(faction = faction, unlockedTechIds = unlocked))
    }

    val entities = ArrayList<ResearchStateEntityRecord>(world.researchQueues.size)
    val ids = world.researchQueues.keys.sorted()
    for (id in ids) {
        val queue = world.researchQueues[id]?.items ?: continue
        val tag = world.tags[id] ?: continue
        val archetype = data.buildingArchetype(tag.typeId) ?: "genericBuilding"
        entities.add(
            ResearchStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                archetype = archetype,
                queueSize = queue.size,
                activeTechId = queue.firstOrNull()?.techId,
                activeRemainingTicks = queue.firstOrNull()?.remainingTicks ?: 0
            )
        )
    }

    if (factions.isEmpty() && entities.isEmpty()) return
    emitSnapshotLine(
        renderResearchStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            factions = factions,
            entities = entities,
            pretty = false
        ),
        snapshotOutPath
    )
}

private data class DropoffSummaryCounts(
    val faction1: Int,
    val faction2: Int,
    val minerals: Int,
    val gas: Int
)

private fun countDropoffBuildings(world: World, data: DataRepo): DropoffSummaryCounts {
    var faction1 = 0
    var faction2 = 0
    var minerals = 0
    var gas = 0
    val alive = world.aliveSnapshot
    for (i in 0 until alive.count) {
        val id = alive.ids[i]
        val tag = world.tags[id] ?: continue
        val spec = data.buildSpec(tag.typeId) ?: continue
        if (!spec.supportsDropoff) continue
        if ("minerals" in spec.dropoffResourceKinds) minerals++
        if ("gas" in spec.dropoffResourceKinds) gas++
        when (tag.faction) {
            1 -> faction1++
            2 -> faction2++
        }
    }
    return DropoffSummaryCounts(faction1 = faction1, faction2 = faction2, minerals = minerals, gas = gas)
}

private fun emitHarvesterStateRecord(
    world: World,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || world.harvesters.isEmpty()) return
    val entities = ArrayList<HarvesterStateEntityRecord>(world.harvesters.size)
    val ids = world.harvesters.keys.sorted()
    for (id in ids) {
        val harvester = world.harvesters[id] ?: continue
        val tag = world.tags[id] ?: continue
        entities.add(
            HarvesterStateEntityRecord(
                entityId = id,
                faction = tag.faction,
                typeId = tag.typeId,
                phase = if (harvester.cargoAmount > 0) "return" else "gather",
                targetNodeId = harvester.targetNodeId,
                cargoKind = harvester.cargoKind,
                cargoAmount = harvester.cargoAmount,
                returnTargetId = harvester.returnTargetId.takeIf { it >= 0 }
            )
        )
    }
    if (entities.isEmpty()) return
    emitSnapshotLine(
        renderHarvesterStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            entities = entities,
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

private fun emitResearchRecord(
    research: ResearchSystem,
    tick: Int,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null || research.lastTickEventCount == 0) return
    val events = ArrayList<ResearchEventRecord>(research.lastTickEventCount)
    for (i in 0 until research.lastTickEventCount) {
        val kind =
            when (research.eventKind(i)) {
                ResearchSystem.EVENT_ENQUEUE -> "enqueue"
                ResearchSystem.EVENT_COMPLETE -> "complete"
                ResearchSystem.EVENT_CANCEL -> "cancel"
                else -> "progress"
            }
        events.add(
            ResearchEventRecord(
                kind = kind,
                buildingId = research.eventBuildingId(i),
                techId = research.eventTechId(i) ?: "",
                remainingTicks = research.eventRemainingTicks(i)
            )
        )
    }
    emitSnapshotLine(
        renderResearchStreamRecordJson(
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

private fun emitBuildFailureRecord(
    tick: Int,
    faction: Int,
    typeId: String,
    tileX: Int,
    tileY: Int,
    reason: String,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderBuildFailureStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            faction = faction,
            typeId = typeId,
            tileX = tileX,
            tileY = tileY,
            reason = reason,
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
    data: DataRepo,
    tick: Int,
    pathing: PathfindingSystem,
    pathQueue: PathRequestQueue,
    movement: MovementSystem,
    combat: CombatSystem,
    commandOutcomeCounters: CommandOutcomeCounters,
    tickTrainsCompleted: Int,
    tickResearchCancelled: Int,
    tickResearchCompleted: Int,
    tickResourceDeltas: ResourceDeltaCounterSet,
    tickHarvesterRetargets: Int,
    harvest: ResourceHarvestSystem,
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
    val dropoffCounts = countDropoffBuildings(world, data)
    emitSnapshotLine(
        renderTickSummaryStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            aliveTotal = aliveTotal,
            visibleTilesFaction1 = fog1.visibleCount(),
            visibleTilesFaction2 = fog2.visibleCount(),
            mineralsFaction1 = world.stockpiles[1]?.minerals ?: 0,
            mineralsFaction2 = world.stockpiles[2]?.minerals ?: 0,
            gasFaction1 = world.stockpiles[1]?.gas ?: 0,
            gasFaction2 = world.stockpiles[2]?.gas ?: 0,
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
            buildsCancelled = commandOutcomeCounters.buildsCancelled,
            buildFailures = commandOutcomeCounters.buildFailures,
            buildFailureReasons = commandOutcomeCounters.buildFailureReasons.toRecord(),
            trainsQueued = commandOutcomeCounters.trainsQueued,
            trainsCompleted = tickTrainsCompleted,
            trainsCancelled = commandOutcomeCounters.trainsCancelled,
            trainFailures = commandOutcomeCounters.trainFailures,
            trainFailureReasons = commandOutcomeCounters.trainFailureReasons.toRecord(),
            researchQueueBuildings = world.researchQueues.size,
            researchQueued = commandOutcomeCounters.researchQueued,
            researchCancelled = tickResearchCancelled,
            researchCompleted = tickResearchCompleted,
            researchFailures = commandOutcomeCounters.researchFailures,
            researchFailureReasons = commandOutcomeCounters.researchFailureReasons.toRecord(),
            mineralsSpent = tickResourceDeltas.mineralsSpent,
            gasSpent = tickResourceDeltas.gasSpent,
            mineralsRefunded = tickResourceDeltas.mineralsRefunded,
            gasRefunded = tickResourceDeltas.gasRefunded,
            mineralsSpentFaction1 = tickResourceDeltas.mineralsSpentFaction1,
            mineralsSpentFaction2 = tickResourceDeltas.mineralsSpentFaction2,
            gasSpentFaction1 = tickResourceDeltas.gasSpentFaction1,
            gasSpentFaction2 = tickResourceDeltas.gasSpentFaction2,
            mineralsRefundedFaction1 = tickResourceDeltas.mineralsRefundedFaction1,
            mineralsRefundedFaction2 = tickResourceDeltas.mineralsRefundedFaction2,
            gasRefundedFaction1 = tickResourceDeltas.gasRefundedFaction1,
            gasRefundedFaction2 = tickResourceDeltas.gasRefundedFaction2,
            harvestedMinerals = harvest.lastTickHarvestedMinerals,
            harvestedGas = harvest.lastTickHarvestedGas,
            harvestedMineralsFaction1 = harvest.lastTickHarvestedMineralsFaction1,
            harvestedMineralsFaction2 = harvest.lastTickHarvestedMineralsFaction2,
            harvestedGasFaction1 = harvest.lastTickHarvestedGasFaction1,
            harvestedGasFaction2 = harvest.lastTickHarvestedGasFaction2,
            harvestPickupCount = harvest.lastTickPickupCount,
            harvestDepositCount = harvest.lastTickDepositCount,
            harvestPickupAmount = harvest.lastTickPickupAmount,
            harvestDepositAmount = harvest.lastTickDepositAmount,
            harvesterRetargets = tickHarvesterRetargets,
            dropoffBuildingsFaction1 = dropoffCounts.faction1,
            dropoffBuildingsFaction2 = dropoffCounts.faction2,
            mineralDropoffBuildings = dropoffCounts.minerals,
            gasDropoffBuildings = dropoffCounts.gas,
            depletedNodes = harvest.lastTickDepletedNodes,
            changedResourceNodes = harvest.lastTickEventCount,
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

internal fun loadScriptCommands(pathStr: String): Array<ArrayList<Command>> {
    return loadScriptProgram(pathStr).commandsByTick
}

internal data class LoadedProgram(
    val commandsByTick: Array<ArrayList<Command>>,
    val selectionEventsByTick: Array<ArrayList<ScriptRunner.SelectionEvent>>,
    val commandRequestIds: IdentityHashMap<Command, String> = IdentityHashMap()
)

private fun toLoadedProgram(
    program: ScriptRunner.ScriptProgram,
    commandRequestIds: IdentityHashMap<Command, String> = IdentityHashMap()
): LoadedProgram {
    val cmds = program.commands
    val selectionEvents = program.selections
    if (cmds.isEmpty() && selectionEvents.isEmpty()) return LoadedProgram(arrayOf(), emptyArray(), commandRequestIds)
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
    return LoadedProgram(byTick, selectionByTick, commandRequestIds)
}

private fun loadScriptProgram(pathStr: String): LoadedProgram {
    val path = resolvePath(pathStr)
    if (!Files.exists(path)) error("Script file not found: $pathStr")
    return toLoadedProgram(ScriptRunner.loadProgram(path))
}

private fun loadInputJsonProgram(pathStr: String): LoadedProgram {
    if (pathStr == "-") {
        val program = InputJson.loadLoadedProgram(generateSequence(::readLine).joinToString("\n"))
        return toLoadedProgram(program.program, program.commandRequestIds)
    }
    val path = resolvePath(pathStr)
    if (!Files.exists(path)) error("Input JSON file not found: $pathStr")
    val program = InputJson.loadLoadedProgram(path)
    return toLoadedProgram(program.program, program.commandRequestIds)
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

internal fun loadSpawnScriptProgram(pathStr: String): LoadedProgram {
    val program = loadScriptProgram(pathStr)
    val all = program.commandsByTick
    val selections = program.selectionEventsByTick
    for (tick in all.indices) {
        val cmds = all[tick]
        val it = cmds.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c !is Command.Spawn && c !is Command.SpawnNode && c !is Command.Build) it.remove()
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

private fun processTickInput(
    tick: Int,
    selectionEventsByTick: List<ArrayList<ScriptRunner.SelectionEvent>>,
    commandsByTick: List<ArrayList<Command>>,
    commandRequestIds: Map<Command, String>,
    world: World,
    recorder: ReplayRecorder,
    data: DataRepo,
    labelMap: HashMap<String, Int>,
    labelIdMap: HashMap<Int, Int>,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?,
    buildings: BuildingPlacementSystem,
    production: BuildingProductionSystem,
    research: ResearchSystem,
    commandOutcomeCounters: CommandOutcomeCounters
) {
    if (tick < selectionEventsByTick.size) {
        val selections = selectionEventsByTick[tick]
        for (i in 0 until selections.size) {
            emitSelectionRecord(selections[i], tick, snapshotOutPath, streamSequence)
        }
    }
    if (tick >= commandsByTick.size) return
    val cmds = commandsByTick[tick]
    for (i in 0 until cmds.size) {
        issue(
            cmds[i],
            world,
            recorder,
            data,
            labelMap,
            labelIdMap,
            snapshotOutPath,
            streamSequence,
            buildings,
            production,
            research,
            commandOutcomeCounters,
            requestId = commandRequestIds[cmds[i]]
        )
    }
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
            is ScriptRunner.Selection.Archetype ->
                renderSelectionStreamRecordJson(
                    sequence = nextStreamSequence(streamSequence),
                    tick = tick,
                    selectionType = "archetype",
                    archetype = selection.archetype,
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
                is Command.MoveArchetype -> {
                    println("tick=$tick moveArchetype archetype=${c.archetype} x=${c.x} y=${c.y}")
                }
                is Command.Patrol -> {
                    println("tick=$tick patrol units=${c.units.joinToString(",")} x=${c.x} y=${c.y}")
                }
                is Command.PatrolFaction -> {
                    println("tick=$tick patrolFaction faction=${c.faction} x=${c.x} y=${c.y}")
                }
                is Command.PatrolType -> {
                    println("tick=$tick patrolType type=${c.typeId} x=${c.x} y=${c.y}")
                }
                is Command.PatrolArchetype -> {
                    println("tick=$tick patrolArchetype archetype=${c.archetype} x=${c.x} y=${c.y}")
                }
                is Command.AttackMove -> {
                    println("tick=$tick attackMove units=${c.units.joinToString(",")} x=${c.x} y=${c.y}")
                }
                is Command.AttackMoveFaction -> {
                    println("tick=$tick attackMoveFaction faction=${c.faction} x=${c.x} y=${c.y}")
                }
                is Command.AttackMoveType -> {
                    println("tick=$tick attackMoveType type=${c.typeId} x=${c.x} y=${c.y}")
                }
                is Command.AttackMoveArchetype -> {
                    println("tick=$tick attackMoveArchetype archetype=${c.archetype} x=${c.x} y=${c.y}")
                }
                is Command.Hold -> {
                    println("tick=$tick hold units=${c.units.joinToString(",")}")
                }
                is Command.HoldFaction -> {
                    println("tick=$tick holdFaction faction=${c.faction}")
                }
                is Command.HoldType -> {
                    println("tick=$tick holdType type=${c.typeId}")
                }
                is Command.HoldArchetype -> {
                    println("tick=$tick holdArchetype archetype=${c.archetype}")
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
                is Command.AttackArchetype -> {
                    println("tick=$tick attackArchetype archetype=${c.archetype} target=${c.target}")
                }
                is Command.Harvest -> {
                    println("tick=$tick harvest units=${c.units.joinToString(",")} target=${c.target}")
                }
                is Command.HarvestFaction -> {
                    println("tick=$tick harvestFaction faction=${c.faction} target=${c.target}")
                }
                is Command.HarvestType -> {
                    println("tick=$tick harvestType type=${c.typeId} target=${c.target}")
                }
                is Command.HarvestArchetype -> {
                    println("tick=$tick harvestArchetype archetype=${c.archetype} target=${c.target}")
                }
                is Command.Construct -> {
                    println("tick=$tick construct units=${c.units.joinToString(",")} target=${c.target}")
                }
                is Command.ConstructFaction -> {
                    println("tick=$tick constructFaction faction=${c.faction} target=${c.target}")
                }
                is Command.ConstructType -> {
                    println("tick=$tick constructType type=${c.typeId} target=${c.target}")
                }
                is Command.ConstructArchetype -> {
                    println("tick=$tick constructArchetype archetype=${c.archetype} target=${c.target}")
                }
                is Command.SpawnNode -> {
                    val label = c.label?.let { "@$it " } ?: ""
                    val yield = if (c.yieldPerTick > 0) " yield=${c.yieldPerTick}" else ""
                    println("tick=$tick spawnNode ${label}kind=${c.kind} x=${c.x} y=${c.y} amount=${c.amount}${yield}")
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
                is Command.CancelBuild -> {
                    println("tick=$tick cancelBuild building=${c.buildingId}")
                }
                is Command.Research -> {
                    println(
                        "tick=$tick research building=${c.buildingId} tech=${c.techId} " +
                            "buildTicks=${c.buildTicks} minerals=${c.mineralCost} gas=${c.gasCost}"
                    )
                }
                is Command.CancelResearch -> {
                    println("tick=$tick cancelResearch building=${c.buildingId}")
                }
                is Command.Rally -> {
                    println("tick=$tick rally building=${c.buildingId} x=${c.x} y=${c.y}")
                }
            }
        }
    }
}

private fun printScriptSelections(selectionEventsByTick: Array<ArrayList<ScriptRunner.SelectionEvent>>) {
    println("script selections:")
    for (tick in selectionEventsByTick.indices) {
        val events = selectionEventsByTick[tick]
        for (i in 0 until events.size) {
            println(formatScriptSelection(events[i]))
        }
    }
}

internal fun formatScriptSelection(event: ScriptRunner.SelectionEvent): String =
    when (val selection = event.selection) {
        is ScriptRunner.Selection.Units ->
            "tick=${event.tick} select units=${selection.ids.joinToString(",")}"
        is ScriptRunner.Selection.All ->
            "tick=${event.tick} selectAll"
        is ScriptRunner.Selection.Faction ->
            "tick=${event.tick} selectFaction faction=${selection.id}"
        is ScriptRunner.Selection.Type ->
            "tick=${event.tick} selectType type=${selection.typeId}"
        is ScriptRunner.Selection.Archetype ->
            "tick=${event.tick} selectArchetype archetype=${selection.archetype}"
    }

internal fun validateSpawnTypes(commandsByTick: Array<ArrayList<Command>>, data: DataRepo) {
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Spawn -> {
                    try {
                        data.unit(c.typeId)
                    } catch (_: NoSuchElementException) {
                        error("Unknown unit typeId '${c.typeId}' in spawn at tick $tick")
                    }
                }
                is Command.SpawnNode -> {
                    if (!RESOURCE_NODE_KINDS.contains(c.kind)) {
                        error("Unknown resource node kind '${c.kind}' in spawnNode at tick $tick")
                    }
                    if (c.amount <= 0) {
                        error("Invalid resource node amount '${c.amount}' in spawnNode at tick $tick")
                    }
                    if (c.yieldPerTick < 0) {
                        error("Invalid resource node yield '${c.yieldPerTick}' in spawnNode at tick $tick")
                    }
                }
                else -> Unit
            }
        }
    }
}

internal fun validateBuildCommands(commandsByTick: Array<ArrayList<Command>>, data: DataRepo, world: World = World()) {
    val availableBuildingsByFaction = snapshotFactionBuildings(world)
    val availableResearchByFaction = snapshotFactionResearch(world)
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
            val missingTech = missingRequiredTypes(availableBuildingsByFaction[c.faction], spec?.requiredBuildingTypes)
            if (missingTech.isNotEmpty()) {
                error(
                    "Missing tech for build '${c.typeId}' at tick $tick " +
                        "(required=${missingTech.joinToString(",")})"
                )
            }
            val missingResearch = missingRequiredTypes(availableResearchByFaction[c.faction], spec?.requiredResearchIds)
            if (missingResearch.isNotEmpty()) {
                error(
                    "Missing research for build '${c.typeId}' at tick $tick " +
                        "(required=${missingResearch.joinToString(",")})"
                )
            }
            availableBuildingsByFaction.getOrPut(c.faction, ::HashSet).add(c.typeId)
        }
    }
}

internal fun validateTrainCommands(commandsByTick: Array<ArrayList<Command>>, data: DataRepo, world: World = World()) {
    val labeledBuildingTypes = HashMap<Int, String>()
    val labeledBuildingFactions = HashMap<Int, Int>()
    val labeledQueueState = HashMap<Int, ValidationQueueState>()
    val availableBuildingsByFaction = snapshotFactionBuildings(world)
    val availableResearchByFaction = snapshotFactionResearch(world)
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.Build -> {
                    val labelId = c.labelId
                    if (labelId != null) {
                        labeledBuildingTypes[labelId] = c.typeId
                        labeledBuildingFactions[labelId] = c.faction
                        val queueLimit = data.buildSpec(c.typeId)?.productionQueueLimit ?: 5
                        labeledQueueState[labelId] = ValidationQueueState(queueLimit)
                    }
                    availableBuildingsByFaction.getOrPut(c.faction, ::HashSet).add(c.typeId)
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
                        val buildingFaction = labeledBuildingFactions[c.buildingId]
                        val buildingSpec = buildingType?.let { data.buildSpec(it) }
                        if (buildingType != null && buildingSpec != null && !buildingSpec.supportsTraining) {
                            error("Producer '$buildingType' does not support training at tick $tick")
                        }
                        if (buildingType != null && spec != null && spec.producerTypes.isNotEmpty() && !spec.producerTypes.contains(buildingType)) {
                            error(
                                "Incompatible producer '$buildingType' for '${c.typeId}' in train at tick $tick " +
                                    "(allowed=${spec.producerTypes.joinToString(",")})"
                            )
                        }
                        val missingTech = missingRequiredTypes(buildingFaction?.let(availableBuildingsByFaction::get), spec?.requiredBuildingTypes)
                        if (missingTech.isNotEmpty()) {
                            error(
                                "Missing tech for train '${c.typeId}' at tick $tick " +
                                "(required=${missingTech.joinToString(",")})"
                            )
                        }
                        val missingResearch = missingRequiredTypes(buildingFaction?.let(availableResearchByFaction::get), spec?.requiredResearchIds)
                        if (missingResearch.isNotEmpty()) {
                            error(
                                "Missing research for train '${c.typeId}' at tick $tick " +
                                    "(required=${missingResearch.joinToString(",")})"
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
                    if (c.buildingId >= 0) {
                        val buildingTag = world.tags[c.buildingId]
                        val missingTech = missingRequiredTypes(buildingTag?.faction?.let(availableBuildingsByFaction::get), spec?.requiredBuildingTypes)
                        if (missingTech.isNotEmpty()) {
                            error(
                                "Missing tech for train '${c.typeId}' at tick $tick " +
                                "(required=${missingTech.joinToString(",")})"
                            )
                        }
                        val missingResearch = missingRequiredTypes(buildingTag?.faction?.let(availableResearchByFaction::get), spec?.requiredResearchIds)
                        if (missingResearch.isNotEmpty()) {
                            error(
                                "Missing research for train '${c.typeId}' at tick $tick " +
                                    "(required=${missingResearch.joinToString(",")})"
                            )
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

internal fun validateResearchCommands(commandsByTick: Array<ArrayList<Command>>, data: DataRepo, world: World = World()) {
    val labeledBuildingTypes = HashMap<Int, String>()
    val labeledBuildingFactions = HashMap<Int, Int>()
    val labeledQueueState = HashMap<Int, ValidationQueueState>()
    val availableBuildingsByFaction = snapshotFactionBuildings(world)
    val availableResearchByFaction = snapshotFactionResearch(world)
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            when (val c = cmds[i]) {
                is Command.Build -> {
                    val labelId = c.labelId
                    if (labelId != null) {
                        labeledBuildingTypes[labelId] = c.typeId
                        labeledBuildingFactions[labelId] = c.faction
                        val queueLimit = data.buildSpec(c.typeId)?.productionQueueLimit ?: 5
                        labeledQueueState[labelId] = ValidationQueueState(queueLimit)
                    }
                    availableBuildingsByFaction.getOrPut(c.faction, ::HashSet).add(c.typeId)
                }
                is Command.Research -> {
                    val spec = data.researchSpec(c.techId)
                    val buildTicks = if (c.buildTicks > 0) c.buildTicks else (spec?.buildTicks ?: 0)
                    if (spec == null && c.buildTicks <= 0) {
                        error("Unknown tech id '${c.techId}' in research at tick $tick (missing default buildTicks)")
                    }
                    if (buildTicks <= 0) {
                        error("Invalid research definition for '${c.techId}' at tick $tick (resolved buildTicks=$buildTicks)")
                    }
                    if (c.buildingId < 0) {
                        val buildingType = labeledBuildingTypes[c.buildingId]
                        val buildingFaction = labeledBuildingFactions[c.buildingId]
                        val buildingSpec = buildingType?.let(data::buildSpec)
                        if (buildingType != null && buildingSpec != null && !buildingSpec.supportsResearch) {
                            error("Producer '$buildingType' does not support research at tick $tick")
                        }
                        if (buildingType != null && spec != null && spec.producerTypes.isNotEmpty() && !spec.producerTypes.contains(buildingType)) {
                            error(
                                "Incompatible producer '$buildingType' for research '${c.techId}' at tick $tick " +
                                    "(allowed=${spec.producerTypes.joinToString(",")})"
                            )
                        }
                        val missingBuildings = missingRequiredTypes(buildingFaction?.let(availableBuildingsByFaction::get), spec?.requiredBuildingTypes)
                        if (missingBuildings.isNotEmpty()) {
                            error(
                                "Missing tech for research '${c.techId}' at tick $tick " +
                                    "(requiredBuildings=${missingBuildings.joinToString(",")})"
                            )
                        }
                        val missingResearch = missingRequiredTypes(buildingFaction?.let(availableResearchByFaction::get), spec?.requiredResearchIds)
                        if (missingResearch.isNotEmpty()) {
                            error(
                                "Missing research for '${c.techId}' at tick $tick " +
                                    "(requiredResearch=${missingResearch.joinToString(",")})"
                            )
                        }
                        if (buildingFaction != null && availableResearchByFaction.getOrPut(buildingFaction, ::HashSet).contains(c.techId)) {
                            error("Research '${c.techId}' already unlocked at tick $tick")
                        }
                        val queueState = labeledQueueState[c.buildingId]
                        if (queueState != null) {
                            queueState.discardCompleted(tick)
                            if (queueState.size >= queueState.limit) {
                                error(
                                    "Queue limit exceeded for '$buildingType' in research at tick $tick " +
                                        "(limit=${queueState.limit}, queued=${queueState.size})"
                                )
                            }
                            queueState.enqueue(tick, buildTicks)
                        }
                        if (buildingFaction != null) {
                            availableResearchByFaction.getOrPut(buildingFaction, ::HashSet).add(c.techId)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun snapshotFactionBuildings(world: World): HashMap<Int, HashSet<String>> {
    val availableBuildingsByFaction = HashMap<Int, HashSet<String>>()
    for (id in world.footprints.keys) {
        val tag = world.tags[id] ?: continue
        availableBuildingsByFaction.getOrPut(tag.faction, ::HashSet).add(tag.typeId)
    }
    return availableBuildingsByFaction
}

private fun snapshotFactionResearch(world: World): HashMap<Int, HashSet<String>> {
    val availableResearchByFaction = HashMap<Int, HashSet<String>>()
    for ((faction, techs) in world.unlockedTechsByFaction) {
        availableResearchByFaction.getOrPut(faction, ::HashSet).addAll(techs)
    }
    return availableResearchByFaction
}

private fun missingRequiredTypes(availableTypes: Set<String>?, requiredTypes: List<String>?): List<String> {
    if (requiredTypes.isNullOrEmpty()) return emptyList()
    if (availableTypes == null) return requiredTypes
    return requiredTypes.filterNot(availableTypes::contains)
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
                is Command.Patrol -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in patrol at tick $tick")
                        }
                    }
                }
                is Command.AttackMove -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in attackMove at tick $tick")
                        }
                    }
                }
                is Command.Hold -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in hold at tick $tick")
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
                is Command.MoveArchetype -> Unit
                is Command.PatrolFaction -> Unit
                is Command.PatrolType -> Unit
                is Command.PatrolArchetype -> Unit
                is Command.AttackMoveFaction -> Unit
                is Command.AttackMoveType -> Unit
                is Command.AttackMoveArchetype -> Unit
                is Command.HoldFaction -> Unit
                is Command.HoldType -> Unit
                is Command.HoldArchetype -> Unit
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
                is Command.AttackArchetype -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in attackArchetype at tick $tick")
                    }
                }
                is Command.Harvest -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in harvest at tick $tick")
                    }
                }
                is Command.HarvestFaction -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in harvestFaction at tick $tick")
                    }
                }
                is Command.HarvestType -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in harvestType at tick $tick")
                    }
                }
                is Command.HarvestArchetype -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in harvestArchetype at tick $tick")
                    }
                }
                is Command.Construct -> {
                    if (!(c.units.size == 1 && c.units[0] == 0)) {
                        for (id in c.units) if (id >= 0 && !existing.contains(id)) {
                            error("Unknown unit id '$id' in construct at tick $tick")
                        }
                    }
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in construct at tick $tick")
                    }
                }
                is Command.ConstructFaction -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in constructFaction at tick $tick")
                    }
                }
                is Command.ConstructType -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in constructType at tick $tick")
                    }
                }
                is Command.ConstructArchetype -> {
                    if (c.target >= 0 && !existing.contains(c.target)) {
                        error("Unknown target id '${c.target}' in constructArchetype at tick $tick")
                    }
                }
                is Command.SpawnNode -> Unit
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
                is Command.CancelBuild -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in cancelBuild at tick $tick")
                    }
                }
                is Command.Research -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in research at tick $tick")
                    }
                }
                is Command.CancelResearch -> {
                    if (c.buildingId >= 0 && !existing.contains(c.buildingId)) {
                        error("Unknown building id '${c.buildingId}' in cancelResearch at tick $tick")
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

internal fun validateSelectionUnitIds(
    selectionEventsByTick: Array<ArrayList<ScriptRunner.SelectionEvent>>,
    commandsByTick: Array<ArrayList<Command>>,
    world: World
) {
    val definedLabels = HashSet<Int>()
    val ticks = maxOf(selectionEventsByTick.size, commandsByTick.size)
    for (tick in 0 until ticks) {
        if (tick < commandsByTick.size) {
            val cmds = commandsByTick[tick]
            for (i in 0 until cmds.size) {
                when (val c = cmds[i]) {
                    is Command.SpawnNode -> c.labelId?.let(definedLabels::add)
                    is Command.Spawn -> c.labelId?.let(definedLabels::add)
                    is Command.Build -> c.labelId?.let(definedLabels::add)
                    else -> Unit
                }
            }
        }
        if (tick >= selectionEventsByTick.size) continue
        val events = selectionEventsByTick[tick]
        for (i in 0 until events.size) {
            val selection = events[i].selection
            if (selection !is ScriptRunner.Selection.Units) continue
            for (id in selection.ids) {
                if (id < 0 && !definedLabels.contains(id)) {
                    error("Unknown label id '$id' in select at tick $tick (spawn/build first)")
                }
                if (id >= 0 && !world.transforms.containsKey(id)) {
                    error("Unknown entity id '$id' in select at tick $tick")
                }
            }
        }
    }
}

internal fun validateLabelUsage(commandsByTick: Array<ArrayList<Command>>) {
    val defined = HashSet<Int>()
    for (tick in commandsByTick.indices) {
        val cmds = commandsByTick[tick]
        for (i in 0 until cmds.size) {
            val c = cmds[i]
            when (c) {
                is Command.SpawnNode -> {
                    val labelId = c.labelId
                    if (labelId != null) defined.add(labelId)
                }
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
                is Command.Patrol -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in patrol at tick $tick (spawn first)")
                    }
                }
                is Command.AttackMove -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in attackMove at tick $tick (spawn first)")
                    }
                }
                is Command.Hold -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in hold at tick $tick (spawn first)")
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
                is Command.MoveArchetype -> Unit
                is Command.PatrolFaction -> Unit
                is Command.PatrolType -> Unit
                is Command.PatrolArchetype -> Unit
                is Command.AttackMoveFaction -> Unit
                is Command.AttackMoveType -> Unit
                is Command.AttackMoveArchetype -> Unit
                is Command.HoldFaction -> Unit
                is Command.HoldType -> Unit
                is Command.HoldArchetype -> Unit
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
                is Command.AttackArchetype -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in attackArchetype at tick $tick (spawn first)")
                    }
                }
                is Command.Harvest -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in harvest at tick $tick (spawn first)")
                    }
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in harvest at tick $tick (spawn first)")
                    }
                }
                is Command.HarvestFaction -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in harvestFaction at tick $tick (spawn first)")
                    }
                }
                is Command.HarvestType -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in harvestType at tick $tick (spawn first)")
                    }
                }
                is Command.HarvestArchetype -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in harvestArchetype at tick $tick (spawn first)")
                    }
                }
                is Command.Construct -> {
                    for (id in c.units) if (id < 0 && !defined.contains(id)) {
                        error("Unknown label id '$id' in construct at tick $tick (spawn first)")
                    }
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in construct at tick $tick (spawn/build first)")
                    }
                }
                is Command.ConstructFaction -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in constructFaction at tick $tick (spawn/build first)")
                    }
                }
                is Command.ConstructType -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in constructType at tick $tick (spawn/build first)")
                    }
                }
                is Command.ConstructArchetype -> {
                    if (c.target < 0 && !defined.contains(c.target)) {
                        error("Unknown label id '${c.target}' in constructArchetype at tick $tick (spawn/build first)")
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
                is Command.CancelBuild -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in cancelBuild at tick $tick (spawn/build first)")
                    }
                }
                is Command.Research -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in research at tick $tick (spawn/build first)")
                    }
                }
                is Command.CancelResearch -> {
                    if (c.buildingId < 0 && !defined.contains(c.buildingId)) {
                        error("Unknown label id '${c.buildingId}' in cancelResearch at tick $tick (spawn/build first)")
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
                is Order.Patrol -> "patrol(${String.format("%.2f", if (o.toB) o.bx else o.ax)},${String.format("%.2f", if (o.toB) o.by else o.ay)})"
                is Order.AttackMove -> "attackMove(${String.format("%.2f", o.tx)},${String.format("%.2f", o.ty)})"
                is Order.Construct -> "construct(${o.target})"
                is Order.Hold -> "hold"
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
    val type: Int,
    val archetype: Int
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
    var archetype = 0
    var moveDirect = 0
    var moveFaction = 0
    var moveType = 0
    var moveArchetype = 0
    var attackDirect = 0
    var attackFaction = 0
    var attackType = 0
    var attackArchetype = 0
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
        var tickArchetype = 0
        var tickMoveDirect = 0
        var tickMoveFaction = 0
        var tickMoveType = 0
        var tickMoveArchetype = 0
        var tickAttackDirect = 0
        var tickAttackFaction = 0
        var tickAttackType = 0
        var tickAttackArchetype = 0
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
                is Command.MoveArchetype -> {
                    tickMoves++
                    tickArchetype++
                    tickMoveArchetype++
                }
                is Command.Patrol -> {
                    tickMoves++
                    tickDirect++
                    tickMoveDirect++
                }
                is Command.PatrolFaction -> {
                    tickMoves++
                    tickFaction++
                    tickMoveFaction++
                }
                is Command.PatrolType -> {
                    tickMoves++
                    tickType++
                    tickMoveType++
                }
                is Command.PatrolArchetype -> {
                    tickMoves++
                    tickArchetype++
                    tickMoveArchetype++
                }
                is Command.AttackMove -> {
                    tickMoves++
                    tickDirect++
                    tickMoveDirect++
                }
                is Command.AttackMoveFaction -> {
                    tickMoves++
                    tickFaction++
                    tickMoveFaction++
                }
                is Command.AttackMoveType -> {
                    tickMoves++
                    tickType++
                    tickMoveType++
                }
                is Command.AttackMoveArchetype -> {
                    tickMoves++
                    tickArchetype++
                    tickMoveArchetype++
                }
                is Command.Hold -> {
                    tickDirect++
                }
                is Command.HoldFaction -> {
                    tickFaction++
                }
                is Command.HoldType -> {
                    tickType++
                }
                is Command.HoldArchetype -> {
                    tickArchetype++
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
                is Command.AttackArchetype -> {
                    tickAttacks++
                    tickArchetype++
                    tickAttackArchetype++
                }
                is Command.Construct -> tickDirect++
                is Command.ConstructFaction -> tickFaction++
                is Command.ConstructType -> tickType++
                is Command.ConstructArchetype -> tickArchetype++
                is Command.Spawn -> tickSpawns++
                is Command.SpawnNode -> Unit
                is Command.Build -> Unit
                is Command.Train -> Unit
                is Command.CancelBuild -> Unit
                is Command.CancelTrain -> Unit
                is Command.Research -> Unit
                is Command.CancelResearch -> Unit
                is Command.Rally -> Unit
                is Command.Harvest -> Unit
                is Command.HarvestFaction -> Unit
                is Command.HarvestType -> Unit
                is Command.HarvestArchetype -> Unit
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
                    selectors = CommandSelectorTotals(direct = tickDirect, faction = tickFaction, type = tickType, archetype = tickArchetype),
                    breakdown =
                        CommandActionBreakdown(
                            move =
                                CommandSelectorTotals(
                                    direct = tickMoveDirect,
                                    faction = tickMoveFaction,
                                    type = tickMoveType,
                                    archetype = tickMoveArchetype
                                ),
                            attack =
                                CommandSelectorTotals(
                                    direct = tickAttackDirect,
                                    faction = tickAttackFaction,
                                    type = tickAttackType,
                                    archetype = tickAttackArchetype
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
                is Command.MoveArchetype -> {
                    moves++
                    archetype++
                    moveArchetype++
                }
                is Command.Patrol -> {
                    moves++
                    direct++
                    moveDirect++
                }
                is Command.PatrolFaction -> {
                    moves++
                    faction++
                    moveFaction++
                }
                is Command.PatrolType -> {
                    moves++
                    type++
                    moveType++
                }
                is Command.PatrolArchetype -> {
                    moves++
                    archetype++
                    moveArchetype++
                }
                is Command.AttackMove -> {
                    moves++
                    direct++
                    moveDirect++
                }
                is Command.AttackMoveFaction -> {
                    moves++
                    faction++
                    moveFaction++
                }
                is Command.AttackMoveType -> {
                    moves++
                    type++
                    moveType++
                }
                is Command.AttackMoveArchetype -> {
                    moves++
                    archetype++
                    moveArchetype++
                }
                is Command.Hold -> {
                    direct++
                }
                is Command.HoldFaction -> {
                    faction++
                }
                is Command.HoldType -> {
                    type++
                }
                is Command.HoldArchetype -> {
                    archetype++
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
                is Command.AttackArchetype -> {
                    attacks++
                    archetype++
                    attackArchetype++
                }
                is Command.Construct -> direct++
                is Command.ConstructFaction -> faction++
                is Command.ConstructType -> type++
                is Command.ConstructArchetype -> archetype++
                is Command.Spawn -> spawns++
                is Command.SpawnNode -> Unit
                is Command.Build -> Unit
                is Command.Train -> Unit
                is Command.CancelBuild -> Unit
                is Command.CancelTrain -> Unit
                is Command.Research -> Unit
                is Command.CancelResearch -> Unit
                is Command.Rally -> Unit
                is Command.Harvest -> Unit
                is Command.HarvestFaction -> Unit
                is Command.HarvestType -> Unit
                is Command.HarvestArchetype -> Unit
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
            selectors = CommandSelectorTotals(direct = direct, faction = faction, type = type, archetype = archetype),
            breakdown =
                CommandActionBreakdown(
                    move = CommandSelectorTotals(direct = moveDirect, faction = moveFaction, type = moveType, archetype = moveArchetype),
                    attack =
                        CommandSelectorTotals(
                            direct = attackDirect,
                            faction = attackFaction,
                            type = attackType,
                            archetype = attackArchetype
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
            "faction=${stats.totals.selectors.faction} type=${stats.totals.selectors.type} " +
            "archetype=${stats.totals.selectors.archetype}"
    )
    lines.add(
        "move selectors: direct=${stats.totals.breakdown.move.direct} " +
            "faction=${stats.totals.breakdown.move.faction} type=${stats.totals.breakdown.move.type} " +
            "archetype=${stats.totals.breakdown.move.archetype}"
    )
    lines.add(
        "attack selectors: direct=${stats.totals.breakdown.attack.direct} " +
            "faction=${stats.totals.breakdown.attack.faction} type=${stats.totals.breakdown.attack.type} " +
            "archetype=${stats.totals.breakdown.attack.archetype}"
    )
    return lines.joinToString(separator = "\n")
}

private fun formatTickStatsLine(tick: CommandTickCount): String {
    return "tick=${tick.tick} commands=${tick.commands} spawns=${tick.spawns} " +
        "moves=${tick.moves} attacks=${tick.attacks} " +
        "selectors=${tick.selectors.direct}/${tick.selectors.faction}/${tick.selectors.type}/${tick.selectors.archetype} " +
        "move=${tick.breakdown.move.direct}/${tick.breakdown.move.faction}/${tick.breakdown.move.type}/${tick.breakdown.move.archetype} " +
        "attack=${tick.breakdown.attack.direct}/${tick.breakdown.attack.faction}/${tick.breakdown.attack.type}/${tick.breakdown.attack.archetype}"
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

data class BuildFailureCounterSet(
    var invalidDefinition: Int = 0,
    var missingTech: Int = 0,
    var invalidFootprint: Int = 0,
    var invalidPlacement: Int = 0,
    var insufficientResources: Int = 0
) {
    fun add(other: BuildFailureCounterSet) {
        invalidDefinition += other.invalidDefinition
        missingTech += other.missingTech
        invalidFootprint += other.invalidFootprint
        invalidPlacement += other.invalidPlacement
        insufficientResources += other.insufficientResources
    }

    fun toRecord(): BuildFailureCounts =
        BuildFailureCounts(
            invalidDefinition = invalidDefinition,
            missingTech = missingTech,
            invalidFootprint = invalidFootprint,
            invalidPlacement = invalidPlacement,
            insufficientResources = insufficientResources
        )
}

data class TrainFailureCounterSet(
    var missingBuilding: Int = 0,
    var underConstruction: Int = 0,
    var missingTech: Int = 0,
    var invalidUnit: Int = 0,
    var invalidBuildTime: Int = 0,
    var incompatibleProducer: Int = 0,
    var insufficientResources: Int = 0,
    var queueFull: Int = 0,
    var nothingToCancel: Int = 0
) {
    fun add(other: TrainFailureCounterSet) {
        missingBuilding += other.missingBuilding
        underConstruction += other.underConstruction
        missingTech += other.missingTech
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
            underConstruction = underConstruction,
            missingTech = missingTech,
            invalidUnit = invalidUnit,
            invalidBuildTime = invalidBuildTime,
            incompatibleProducer = incompatibleProducer,
            insufficientResources = insufficientResources,
            queueFull = queueFull,
            nothingToCancel = nothingToCancel
        )
}

data class ResearchFailureCounterSet(
    var missingBuilding: Int = 0,
    var underConstruction: Int = 0,
    var invalidTech: Int = 0,
    var missingTech: Int = 0,
    var incompatibleProducer: Int = 0,
    var insufficientResources: Int = 0,
    var alreadyUnlocked: Int = 0,
    var queueFull: Int = 0,
    var nothingToCancel: Int = 0
) {
    fun add(other: ResearchFailureCounterSet) {
        missingBuilding += other.missingBuilding
        underConstruction += other.underConstruction
        invalidTech += other.invalidTech
        missingTech += other.missingTech
        incompatibleProducer += other.incompatibleProducer
        insufficientResources += other.insufficientResources
        alreadyUnlocked += other.alreadyUnlocked
        queueFull += other.queueFull
        nothingToCancel += other.nothingToCancel
    }

    fun toRecord(): ResearchFailureCounts =
        ResearchFailureCounts(
            missingBuilding = missingBuilding,
            underConstruction = underConstruction,
            invalidTech = invalidTech,
            missingTech = missingTech,
            incompatibleProducer = incompatibleProducer,
            insufficientResources = insufficientResources,
            alreadyUnlocked = alreadyUnlocked,
            queueFull = queueFull,
            nothingToCancel = nothingToCancel
        )
}

data class CommandOutcomeCounters(
    var builds: Int = 0,
    var buildsCancelled: Int = 0,
    var buildFailures: Int = 0,
    val buildFailureReasons: BuildFailureCounterSet = BuildFailureCounterSet(),
    var trainsQueued: Int = 0,
    var trainsCancelled: Int = 0,
    var trainFailures: Int = 0,
    val trainFailureReasons: TrainFailureCounterSet = TrainFailureCounterSet(),
    var researchQueued: Int = 0,
    var researchCancelled: Int = 0,
    var researchFailures: Int = 0,
    val researchFailureReasons: ResearchFailureCounterSet = ResearchFailureCounterSet()
)

internal fun renderCommandOutcomeLogSuffix(
    counters: CommandOutcomeCounters,
    trainsCompleted: Int,
    researchCancelled: Int = 0,
    researchCompleted: Int = 0,
    researchQueueBuildings: Int = 0,
    harvestPickupCount: Int = 0,
    harvestDepositCount: Int = 0,
    harvestPickupAmount: Int = 0,
    harvestDepositAmount: Int = 0,
    harvesterRetargets: Int = 0,
    dropoffBuildingsFaction1: Int = 0,
    dropoffBuildingsFaction2: Int = 0,
    mineralDropoffBuildings: Int = 0,
    gasDropoffBuildings: Int = 0
): String {
    val parts = ArrayList<String>(7)
    if (counters.builds > 0 || counters.buildsCancelled > 0) {
        parts.add("builds=${counters.builds}/x${counters.buildsCancelled}")
    }
    if (counters.buildFailures > 0) {
        parts.add("buildFails=${counters.buildFailures}[${formatBuildFailureReasons(counters.buildFailureReasons)}]")
    }
    if (counters.trainsQueued > 0 || trainsCompleted > 0 || counters.trainsCancelled > 0) {
        parts.add("train=q${counters.trainsQueued}/c$trainsCompleted/x${counters.trainsCancelled}")
    }
    if (counters.trainFailures > 0) {
        parts.add("trainFails=${counters.trainFailures}[${formatTrainFailureReasons(counters.trainFailureReasons)}]")
    }
    if (counters.researchQueued > 0 || researchCancelled > 0 || researchCompleted > 0 || researchQueueBuildings > 0) {
        parts.add("research=q${counters.researchQueued}/c$researchCompleted/x$researchCancelled queues=$researchQueueBuildings")
    }
    if (counters.researchFailures > 0) {
        parts.add("researchFails=${counters.researchFailures}[${formatResearchFailureReasons(counters.researchFailureReasons)}]")
    }
    if (harvestPickupCount > 0 || harvestDepositCount > 0 || harvestPickupAmount > 0 || harvestDepositAmount > 0) {
        parts.add("cycles=p$harvestPickupCount/$harvestPickupAmount d$harvestDepositCount/$harvestDepositAmount")
    }
    if (harvesterRetargets > 0) {
        parts.add("retargets=$harvesterRetargets")
    }
    if (dropoffBuildingsFaction1 > 0 || dropoffBuildingsFaction2 > 0) {
        parts.add("dropoffs=f1:$dropoffBuildingsFaction1/f2:$dropoffBuildingsFaction2")
    }
    if (mineralDropoffBuildings > 0 || gasDropoffBuildings > 0) {
        parts.add("compat=m$mineralDropoffBuildings/g$gasDropoffBuildings")
    }
    return if (parts.isEmpty()) "" else "  " + parts.joinToString(" ")
}

internal fun renderAggregateOutcomeSummary(
    totalBuilds: Int,
    totalBuildsCancelled: Int,
    totalBuildFailures: Int,
    totalBuildFailureReasons: BuildFailureCounterSet,
    totalTrainsQueued: Int,
    totalTrainsCompleted: Int,
    totalTrainsCancelled: Int,
    totalTrainFailures: Int,
    totalTrainFailureReasons: TrainFailureCounterSet,
    totalResearchQueued: Int = 0,
    totalResearchCancelled: Int = 0,
    totalResearchCompleted: Int = 0,
    totalResearchFailures: Int = 0,
    totalResearchFailureReasons: ResearchFailureCounterSet = ResearchFailureCounterSet(),
    currentResearchQueueBuildings: Int = 0,
    totalHarvestedMinerals: Int = 0,
    totalHarvestedGas: Int = 0,
    totalDepletedNodes: Int = 0,
    totalChangedResourceNodes: Int = 0,
    totalHarvestedMineralsFaction1: Int = 0,
    totalHarvestedMineralsFaction2: Int = 0,
    totalHarvestedGasFaction1: Int = 0,
    totalHarvestedGasFaction2: Int = 0,
    totalHarvestPickupCount: Int = 0,
    totalHarvestDepositCount: Int = 0,
    totalHarvestPickupAmount: Int = 0,
    totalHarvestDepositAmount: Int = 0,
    totalHarvesterRetargets: Int = 0,
    dropoffBuildingsFaction1: Int = 0,
    dropoffBuildingsFaction2: Int = 0,
    mineralDropoffBuildings: Int = 0,
    gasDropoffBuildings: Int = 0,
    currentResourceNodeCount: Int = 0,
    currentResourceNodeRemaining: Int = 0
): String? {
    if (
        totalBuilds == 0 &&
        totalBuildsCancelled == 0 &&
        totalBuildFailures == 0 &&
        totalTrainsQueued == 0 &&
        totalTrainsCompleted == 0 &&
        totalTrainsCancelled == 0 &&
        totalTrainFailures == 0 &&
        totalResearchQueued == 0 &&
        totalResearchCancelled == 0 &&
        totalResearchCompleted == 0 &&
        totalResearchFailures == 0 &&
        currentResearchQueueBuildings == 0 &&
        totalHarvestedMinerals == 0 &&
        totalHarvestedGas == 0 &&
        totalDepletedNodes == 0 &&
        totalChangedResourceNodes == 0 &&
        totalHarvestedMineralsFaction1 == 0 &&
        totalHarvestedMineralsFaction2 == 0 &&
        totalHarvestedGasFaction1 == 0 &&
        totalHarvestedGasFaction2 == 0 &&
        totalHarvestPickupCount == 0 &&
        totalHarvestDepositCount == 0 &&
        totalHarvestPickupAmount == 0 &&
        totalHarvestDepositAmount == 0 &&
        totalHarvesterRetargets == 0 &&
        dropoffBuildingsFaction1 == 0 &&
        dropoffBuildingsFaction2 == 0 &&
        mineralDropoffBuildings == 0 &&
        gasDropoffBuildings == 0 &&
        currentResourceNodeCount == 0 &&
        currentResourceNodeRemaining == 0
    ) {
        return null
    }
    val parts = ArrayList<String>(5)
    parts.add("builds=$totalBuilds/x$totalBuildsCancelled")
    if (totalBuildFailures > 0) {
        parts.add("buildFails=$totalBuildFailures[${formatBuildFailureReasons(totalBuildFailureReasons)}]")
    }
    parts.add("train=q$totalTrainsQueued/c$totalTrainsCompleted/x$totalTrainsCancelled")
    if (totalTrainFailures > 0) {
        parts.add("trainFails=$totalTrainFailures[${formatTrainFailureReasons(totalTrainFailureReasons)}]")
    }
    if (totalResearchQueued > 0 || totalResearchCancelled > 0 || totalResearchCompleted > 0 || currentResearchQueueBuildings > 0) {
        parts.add("research=q$totalResearchQueued/c$totalResearchCompleted/x$totalResearchCancelled queues=$currentResearchQueueBuildings")
    }
    if (totalResearchFailures > 0) {
        parts.add("researchFails=$totalResearchFailures[${formatResearchFailureReasons(totalResearchFailureReasons)}]")
    }
    if (totalHarvestedMinerals > 0 || totalHarvestedGas > 0 || totalDepletedNodes > 0 || totalChangedResourceNodes > 0) {
        parts.add(
            "harvest=${totalHarvestedMinerals}/${totalHarvestedGas} " +
                "f1=${totalHarvestedMineralsFaction1}/${totalHarvestedGasFaction1} " +
                "f2=${totalHarvestedMineralsFaction2}/${totalHarvestedGasFaction2} " +
                "cycles=p$totalHarvestPickupCount/$totalHarvestPickupAmount " +
                "d$totalHarvestDepositCount/$totalHarvestDepositAmount " +
                "retargets=$totalHarvesterRetargets " +
                "nodes=$totalChangedResourceNodes depleted=$totalDepletedNodes " +
                "active=$currentResourceNodeCount remaining=$currentResourceNodeRemaining"
        )
    }
    if (dropoffBuildingsFaction1 > 0 || dropoffBuildingsFaction2 > 0) {
        parts.add("dropoffs=f1:$dropoffBuildingsFaction1/f2:$dropoffBuildingsFaction2")
    }
    if (mineralDropoffBuildings > 0 || gasDropoffBuildings > 0) {
        parts.add("compat=m$mineralDropoffBuildings/g$gasDropoffBuildings")
    }
    return "command outcomes: " + parts.joinToString(" ")
}

internal fun formatBuildFailureReasons(reasons: BuildFailureCounterSet): String =
    listOfNotNull(
        reasons.invalidDefinition.takeIf { it > 0 }?.let { "invalidDefinition=$it" },
        reasons.missingTech.takeIf { it > 0 }?.let { "missingTech=$it" },
        reasons.invalidFootprint.takeIf { it > 0 }?.let { "invalidFootprint=$it" },
        reasons.invalidPlacement.takeIf { it > 0 }?.let { "invalidPlacement=$it" },
        reasons.insufficientResources.takeIf { it > 0 }?.let { "insufficientResources=$it" }
    ).joinToString(",")

internal fun formatTrainFailureReasons(reasons: TrainFailureCounterSet): String =
    listOfNotNull(
        reasons.missingBuilding.takeIf { it > 0 }?.let { "missingBuilding=$it" },
        reasons.underConstruction.takeIf { it > 0 }?.let { "underConstruction=$it" },
        reasons.missingTech.takeIf { it > 0 }?.let { "missingTech=$it" },
        reasons.invalidUnit.takeIf { it > 0 }?.let { "invalidUnit=$it" },
        reasons.invalidBuildTime.takeIf { it > 0 }?.let { "invalidBuildTime=$it" },
        reasons.incompatibleProducer.takeIf { it > 0 }?.let { "incompatibleProducer=$it" },
        reasons.insufficientResources.takeIf { it > 0 }?.let { "insufficientResources=$it" },
        reasons.queueFull.takeIf { it > 0 }?.let { "queueFull=$it" },
        reasons.nothingToCancel.takeIf { it > 0 }?.let { "nothingToCancel=$it" }
    ).joinToString(",")

internal fun formatResearchFailureReasons(reasons: ResearchFailureCounterSet): String =
    listOfNotNull(
        reasons.missingBuilding.takeIf { it > 0 }?.let { "missingBuilding=$it" },
        reasons.underConstruction.takeIf { it > 0 }?.let { "underConstruction=$it" },
        reasons.invalidTech.takeIf { it > 0 }?.let { "invalidTech=$it" },
        reasons.missingTech.takeIf { it > 0 }?.let { "missingTech=$it" },
        reasons.incompatibleProducer.takeIf { it > 0 }?.let { "incompatibleProducer=$it" },
        reasons.insufficientResources.takeIf { it > 0 }?.let { "insufficientResources=$it" },
        reasons.alreadyUnlocked.takeIf { it > 0 }?.let { "alreadyUnlocked=$it" },
        reasons.queueFull.takeIf { it > 0 }?.let { "queueFull=$it" },
        reasons.nothingToCancel.takeIf { it > 0 }?.let { "nothingToCancel=$it" }
    ).joinToString(",")

data class ResourceDeltaCounterSet(
    var mineralsSpent: Int = 0,
    var gasSpent: Int = 0,
    var mineralsRefunded: Int = 0,
    var gasRefunded: Int = 0,
    var mineralsSpentFaction1: Int = 0,
    var mineralsSpentFaction2: Int = 0,
    var gasSpentFaction1: Int = 0,
    var gasSpentFaction2: Int = 0,
    var mineralsRefundedFaction1: Int = 0,
    var mineralsRefundedFaction2: Int = 0,
    var gasRefundedFaction1: Int = 0,
    var gasRefundedFaction2: Int = 0
) {
    fun add(other: ResourceDeltaCounterSet) {
        mineralsSpent += other.mineralsSpent
        gasSpent += other.gasSpent
        mineralsRefunded += other.mineralsRefunded
        gasRefunded += other.gasRefunded
        mineralsSpentFaction1 += other.mineralsSpentFaction1
        mineralsSpentFaction2 += other.mineralsSpentFaction2
        gasSpentFaction1 += other.gasSpentFaction1
        gasSpentFaction2 += other.gasSpentFaction2
        mineralsRefundedFaction1 += other.mineralsRefundedFaction1
        mineralsRefundedFaction2 += other.mineralsRefundedFaction2
        gasRefundedFaction1 += other.gasRefundedFaction1
        gasRefundedFaction2 += other.gasRefundedFaction2
    }
}

private fun collectResourceDeltaCounters(resources: ResourceSystem): ResourceDeltaCounterSet {
    val counters = ResourceDeltaCounterSet()
    for (i in 0 until resources.lastTickEventCount) {
        val faction = resources.eventFaction(i)
        val minerals = resources.eventMinerals(i)
        val gas = resources.eventGas(i)
        when (resources.eventKind(i)) {
            ResourceSystem.EVENT_REFUND -> {
                counters.mineralsRefunded += minerals
                counters.gasRefunded += gas
                when (faction) {
                    1 -> {
                        counters.mineralsRefundedFaction1 += minerals
                        counters.gasRefundedFaction1 += gas
                    }
                    2 -> {
                        counters.mineralsRefundedFaction2 += minerals
                        counters.gasRefundedFaction2 += gas
                    }
                }
            }
            else -> {
                counters.mineralsSpent += minerals
                counters.gasSpent += gas
                when (faction) {
                    1 -> {
                        counters.mineralsSpentFaction1 += minerals
                        counters.gasSpentFaction1 += gas
                    }
                    2 -> {
                        counters.mineralsSpentFaction2 += minerals
                        counters.gasSpentFaction2 += gas
                    }
                }
            }
        }
    }
    return counters
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
    research: ResearchSystem? = null,
    outcomeCounters: CommandOutcomeCounters? = null,
    requestId: String? = null
) {
    recorder.onCommand(cmd)
    var commandSequence: Long? = null
    if (snapshotOutPath != null && streamSequence != null) {
        commandSequence = nextStreamSequence(streamSequence)
        emitSnapshotLine(
            renderCommandStreamRecordJson(cmd, sequence = commandSequence, requestId = requestId, pretty = false),
            snapshotOutPath
        )
    }
    fun emitCommandAck(accepted: Boolean, reason: String? = null, appliedUnits: Int? = null, entityId: Int? = null) {
        if (snapshotOutPath == null || streamSequence == null || commandSequence == null) return
        emitSnapshotLine(
            renderCommandAckStreamRecordJson(
                sequence = nextStreamSequence(streamSequence),
                tick = cmd.tick,
                commandType = commandTypeName(cmd),
                requestSequence = commandSequence!!,
                requestId = requestId,
                accepted = accepted,
                reason = reason,
                appliedUnits = appliedUnits,
                entityId = entityId,
                pretty = false
            ),
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
            emitCommandAck(true, appliedUnits = applied.size)
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
            emitCommandAck(true, appliedUnits = applied.size)
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
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.MoveArchetype -> {
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "move", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
                if (tagArchetype == cmd.archetype) {
                    world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "move", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.Patrol -> {
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "patrol", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            if (cmd.units.size == 1 && cmd.units[0] == 0) {
                for (id in world.orders.keys) {
                    val actual = resolveLabelId(id, labelIdMap)
                    val tr = world.transforms[actual] ?: continue
                    world.orders[actual]?.items?.addLast(Order.Patrol(tr.x, tr.y, cmd.x, cmd.y))
                }
            } else {
                cmd.units.forEach { id ->
                    val actual = resolveLabelId(id, labelIdMap)
                    val tr = world.transforms[actual] ?: return@forEach
                    world.orders[actual]?.items?.addLast(Order.Patrol(tr.x, tr.y, cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "patrol", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.PatrolFaction -> {
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "patrol", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    val tr = world.transforms[id] ?: continue
                    world.orders[id]?.items?.addLast(Order.Patrol(tr.x, tr.y, cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "patrol", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.PatrolType -> {
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "patrol", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    val tr = world.transforms[id] ?: continue
                    world.orders[id]?.items?.addLast(Order.Patrol(tr.x, tr.y, cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "patrol", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.PatrolArchetype -> {
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "patrol", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
                if (tagArchetype == cmd.archetype) {
                    val tr = world.transforms[id] ?: continue
                    world.orders[id]?.items?.addLast(Order.Patrol(tr.x, tr.y, cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "patrol", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.AttackMove -> {
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "attackMove", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            if (cmd.units.size == 1 && cmd.units[0] == 0) {
                for (id in world.orders.keys) {
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.AttackMove(cmd.x, cmd.y))
                }
            } else {
                cmd.units.forEach { id ->
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.AttackMove(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attackMove", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.AttackMoveFaction -> {
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "attackMove", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.AttackMove(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attackMove", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.AttackMoveType -> {
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "attackMove", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.AttackMove(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attackMove", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.AttackMoveArchetype -> {
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "attackMove", applied, null, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
                if (tagArchetype == cmd.archetype) {
                    world.orders[id]?.items?.addLast(Order.AttackMove(cmd.x, cmd.y))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attackMove", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.Hold -> {
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "hold", applied, null, null, null, snapshotOutPath, streamSequence)
            if (cmd.units.size == 1 && cmd.units[0] == 0) {
                for (id in world.orders.keys) {
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Hold)
                }
            } else {
                cmd.units.forEach { id ->
                    val actual = resolveLabelId(id, labelIdMap)
                    world.orders[actual]?.items?.addLast(Order.Hold)
                }
            }
            emitOrderQueueRecord(cmd.tick, "hold", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HoldFaction -> {
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "hold", applied, null, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.faction == cmd.faction) {
                    world.orders[id]?.items?.addLast(Order.Hold)
                }
            }
            emitOrderQueueRecord(cmd.tick, "hold", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HoldType -> {
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "hold", applied, null, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                if (tag.typeId == cmd.typeId) {
                    world.orders[id]?.items?.addLast(Order.Hold)
                }
            }
            emitOrderQueueRecord(cmd.tick, "hold", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HoldArchetype -> {
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "hold", applied, null, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
                if (tagArchetype == cmd.archetype) {
                    world.orders[id]?.items?.addLast(Order.Hold)
                }
            }
            emitOrderQueueRecord(cmd.tick, "hold", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
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
            emitCommandAck(true, appliedUnits = applied.size)
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
            emitCommandAck(true, appliedUnits = applied.size)
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
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.AttackArchetype -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "attack", applied, target, null, null, snapshotOutPath, streamSequence)
            for ((id, tag) in world.tags) {
                val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
                if (tagArchetype == cmd.archetype) {
                    world.orders[id]?.items?.addLast(Order.Attack(target))
                }
            }
            emitOrderQueueRecord(cmd.tick, "attack", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.Harvest -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "harvest", applied, target, null, null, snapshotOutPath, streamSequence)
            assignHarvesters(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "harvest", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HarvestFaction -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "harvest", applied, target, null, null, snapshotOutPath, streamSequence)
            assignHarvesters(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "harvest", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HarvestType -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "harvest", applied, target, null, null, snapshotOutPath, streamSequence)
            assignHarvesters(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "harvest", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.HarvestArchetype -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "harvest", applied, target, null, null, snapshotOutPath, streamSequence)
            assignHarvesters(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "harvest", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.Construct -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectDirectTargets(cmd.units, world, labelIdMap)
            emitOrderAppliedRecord(cmd.tick, "construct", applied, target, null, null, snapshotOutPath, streamSequence)
            assignConstructors(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "construct", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.ConstructFaction -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectFactionTargets(cmd.faction, world)
            emitOrderAppliedRecord(cmd.tick, "construct", applied, target, null, null, snapshotOutPath, streamSequence)
            assignConstructors(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "construct", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.ConstructType -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectTypeTargets(cmd.typeId, world)
            emitOrderAppliedRecord(cmd.tick, "construct", applied, target, null, null, snapshotOutPath, streamSequence)
            assignConstructors(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "construct", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.ConstructArchetype -> {
            val target = resolveLabelId(cmd.target, labelIdMap)
            val applied = collectArchetypeTargets(cmd.archetype, world, data)
            emitOrderAppliedRecord(cmd.tick, "construct", applied, target, null, null, snapshotOutPath, streamSequence)
            assignConstructors(applied, target, world)
            emitOrderQueueRecord(cmd.tick, "construct", applied, world, snapshotOutPath, streamSequence)
            emitCommandAck(true, appliedUnits = applied.size)
        }
        is Command.SpawnNode -> {
            require(RESOURCE_NODE_KINDS.contains(cmd.kind)) { "Unknown resource node kind '${cmd.kind}' in spawnNode command" }
            require(cmd.amount > 0) { "Invalid resource node amount '${cmd.amount}' in spawnNode command" }
            require(cmd.yieldPerTick >= 0) { "Invalid resource node yield '${cmd.yieldPerTick}' in spawnNode command" }
            val nodeId =
                world.spawn(
                    Transform(cmd.x, cmd.y),
                    UnitTag(0, cmd.kind),
                    Health(1000, 1000),
                    w = null
                )
            val resourceKind =
                if (cmd.kind == "GasGeyser") ResourceNode.KIND_GAS else ResourceNode.KIND_MINERALS
            world.resourceNodes[nodeId] = ResourceNode(kind = resourceKind, remaining = cmd.amount, yieldPerTick = cmd.yieldPerTick)
            if (cmd.label != null) {
                labelMap[cmd.label] = nodeId
            }
            if (cmd.labelId != null) {
                labelIdMap[cmd.labelId] = nodeId
            }
            if (snapshotOutPath != null && streamSequence != null) {
                emitSnapshotLine(
                    renderSpawnStreamRecordJson(
                        sequence = nextStreamSequence(streamSequence),
                        tick = cmd.tick,
                        entityId = nodeId,
                        faction = 0,
                        typeId = cmd.kind,
                        x = cmd.x,
                        y = cmd.y,
                        vision = null,
                        label = cmd.label,
                        labelId = cmd.labelId,
                        pretty = false
                    ),
                    snapshotOutPath
                )
            }
            emitCommandAck(true, entityId = nodeId)
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
            emitCommandAck(true, entityId = id)
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
            val buildTicks = spec?.buildTicks ?: 0
            val clearance = spec?.placementClearance ?: 0
            val requiredBuildingTypes = spec?.requiredBuildingTypes ?: emptyList()
            val requiredResearchIds = spec?.requiredResearchIds ?: emptyList()
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
                emitBuildFailureRecord(
                    tick = cmd.tick,
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    tileX = cmd.tileX,
                    tileY = cmd.tileY,
                    reason = "invalidDefinition",
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence
                )
                emitCommandAck(false, reason = "invalidDefinition")
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
                    buildTicks = buildTicks,
                    clearance = clearance,
                    armor = armor,
                    mineralCost = mineralCost,
                    gasCost = gasCost,
                    requiredBuildingTypes = requiredBuildingTypes,
                    requiredResearchIds = requiredResearchIds
                )
            val id = result.entityId
            if (id == null) {
                if (outcomeCounters != null) {
                    outcomeCounters.buildFailures++
                    when (result.failure) {
                        BuildFailureReason.MISSING_TECH -> outcomeCounters.buildFailureReasons.missingTech++
                        BuildFailureReason.INSUFFICIENT_RESOURCES -> outcomeCounters.buildFailureReasons.insufficientResources++
                        BuildFailureReason.INVALID_FOOTPRINT -> outcomeCounters.buildFailureReasons.invalidFootprint++
                        else -> outcomeCounters.buildFailureReasons.invalidPlacement++
                    }
                }
                val reason =
                    when (result.failure) {
                        BuildFailureReason.MISSING_TECH -> "missingTech"
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
                emitBuildFailureRecord(
                    tick = cmd.tick,
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    tileX = cmd.tileX,
                    tileY = cmd.tileY,
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence
                )
                emitCommandAck(false, reason = reason)
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
            emitCommandAck(true, entityId = id)
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
                        TrainFailureReason.UNDER_CONSTRUCTION -> outcomeCounters.trainFailureReasons.underConstruction++
                        TrainFailureReason.MISSING_TECH -> outcomeCounters.trainFailureReasons.missingTech++
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
                        TrainFailureReason.UNDER_CONSTRUCTION -> "underConstruction"
                        TrainFailureReason.MISSING_TECH -> "missingTech"
                        TrainFailureReason.INVALID_UNIT -> "invalidUnit"
                        TrainFailureReason.INVALID_BUILD_TIME -> "invalidBuildTime"
                        TrainFailureReason.INCOMPATIBLE_PRODUCER -> "incompatibleProducer"
                        TrainFailureReason.INSUFFICIENT_RESOURCES -> "insufficientResources"
                        TrainFailureReason.QUEUE_FULL -> "queueFull"
                    }
                val producerTypeId = world.tags[buildingId]?.typeId
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "train",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId,
                    typeId = cmd.typeId
                )
                if (
                    failure == TrainFailureReason.MISSING_BUILDING ||
                    failure == TrainFailureReason.INCOMPATIBLE_PRODUCER
                ) {
                    emitProducerFailureRecord(
                        tick = cmd.tick,
                        reason = reason,
                        snapshotOutPath = snapshotOutPath,
                        streamSequence = streamSequence,
                        buildingId = buildingId,
                        producerTypeId = producerTypeId,
                        typeId = cmd.typeId
                    )
                } else {
                    emitTrainFailureRecord(
                        tick = cmd.tick,
                        typeId = cmd.typeId,
                        reason = reason,
                        snapshotOutPath = snapshotOutPath,
                        streamSequence = streamSequence,
                        buildingId = buildingId,
                        producerTypeId = producerTypeId
                    )
                }
                emitCommandAck(false, reason = reason)
            } else {
                if (outcomeCounters != null) outcomeCounters.trainsQueued++
                emitCommandAck(true)
            }
        }
        is Command.CancelTrain -> {
            val productionSystem = production ?: error("CancelTrain requires BuildingProductionSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            if (productionSystem.cancelLast(buildingId)) {
                if (outcomeCounters != null) outcomeCounters.trainsCancelled++
                emitCommandAck(true)
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
                emitCommandAck(false, reason = "nothingToCancel")
            }
        }
        is Command.CancelBuild -> {
            val placement = buildings ?: error("CancelBuild requires BuildingPlacementSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            val failure = placement.cancelConstruction(buildingId)
            if (failure != null) {
                val reason =
                    when (failure) {
                        CancelConstructionFailure.MISSING_BUILDING -> "missingBuilding"
                        CancelConstructionFailure.NOT_UNDER_CONSTRUCTION -> "notUnderConstruction"
                    }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "cancelBuild",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId
                )
                emitCommandAck(false, reason = reason)
            } else {
                if (outcomeCounters != null) outcomeCounters.buildsCancelled++
                emitCommandAck(true)
            }
        }
        is Command.Research -> {
            val researchSystem = research ?: error("Research requires ResearchSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            val repo = data ?: error("Research requires DataRepo")
            val spec = repo.researchSpec(cmd.techId)
            val failure =
                researchSystem.enqueueResult(
                    buildingId = buildingId,
                    techId = cmd.techId,
                    buildTicks = if (cmd.buildTicks > 0) cmd.buildTicks else (spec?.buildTicks ?: 0),
                    mineralCost = if (cmd.mineralCost > 0) cmd.mineralCost else (spec?.mineralCost ?: 0),
                    gasCost = if (cmd.gasCost > 0) cmd.gasCost else (spec?.gasCost ?: 0)
                )
            if (failure != null) {
                if (outcomeCounters != null) {
                    outcomeCounters.researchFailures++
                    when (failure) {
                        ResearchFailureReason.MISSING_BUILDING -> outcomeCounters.researchFailureReasons.missingBuilding++
                        ResearchFailureReason.UNDER_CONSTRUCTION -> outcomeCounters.researchFailureReasons.underConstruction++
                        ResearchFailureReason.INVALID_TECH -> outcomeCounters.researchFailureReasons.invalidTech++
                        ResearchFailureReason.MISSING_TECH -> outcomeCounters.researchFailureReasons.missingTech++
                        ResearchFailureReason.INCOMPATIBLE_PRODUCER -> outcomeCounters.researchFailureReasons.incompatibleProducer++
                        ResearchFailureReason.INSUFFICIENT_RESOURCES -> outcomeCounters.researchFailureReasons.insufficientResources++
                        ResearchFailureReason.ALREADY_UNLOCKED -> outcomeCounters.researchFailureReasons.alreadyUnlocked++
                        ResearchFailureReason.QUEUE_FULL -> outcomeCounters.researchFailureReasons.queueFull++
                        ResearchFailureReason.NOTHING_TO_CANCEL -> outcomeCounters.researchFailureReasons.nothingToCancel++
                    }
                }
                val reason =
                    when (failure) {
                        ResearchFailureReason.MISSING_BUILDING -> "missingBuilding"
                        ResearchFailureReason.UNDER_CONSTRUCTION -> "underConstruction"
                        ResearchFailureReason.INVALID_TECH -> "invalidTech"
                        ResearchFailureReason.MISSING_TECH -> "missingTech"
                        ResearchFailureReason.INCOMPATIBLE_PRODUCER -> "incompatibleProducer"
                        ResearchFailureReason.INSUFFICIENT_RESOURCES -> "insufficientResources"
                        ResearchFailureReason.ALREADY_UNLOCKED -> "alreadyUnlocked"
                        ResearchFailureReason.QUEUE_FULL -> "queueFull"
                        ResearchFailureReason.NOTHING_TO_CANCEL -> "nothingToCancel"
                    }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "research",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId,
                    typeId = cmd.techId
                )
                emitCommandAck(false, reason = reason)
            } else {
                if (outcomeCounters != null) outcomeCounters.researchQueued++
                emitCommandAck(true)
            }
        }
        is Command.CancelResearch -> {
            val researchSystem = research ?: error("CancelResearch requires ResearchSystem")
            val buildingId = resolveLabelId(cmd.buildingId, labelIdMap)
            val failure = researchSystem.cancelLast(buildingId)
            if (failure != null) {
                if (outcomeCounters != null) {
                    outcomeCounters.researchFailures++
                    when (failure) {
                        ResearchFailureReason.MISSING_BUILDING -> outcomeCounters.researchFailureReasons.missingBuilding++
                        ResearchFailureReason.NOTHING_TO_CANCEL -> outcomeCounters.researchFailureReasons.nothingToCancel++
                        else -> Unit
                    }
                }
                val reason =
                    when (failure) {
                        ResearchFailureReason.MISSING_BUILDING -> "missingBuilding"
                        ResearchFailureReason.NOTHING_TO_CANCEL -> "nothingToCancel"
                        else -> error("Unexpected research cancel failure: $failure")
                    }
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "cancelResearch",
                    reason = reason,
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId
                )
                emitCommandAck(false, reason = reason)
            } else {
                if (outcomeCounters != null) outcomeCounters.researchCancelled++
                emitCommandAck(true)
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
                emitRallyFailureRecord(cmd.tick, "missingBuilding", snapshotOutPath, streamSequence, buildingId)
                emitCommandAck(false, reason = "missingBuilding")
                return
            }
            val buildingType = world.tags[buildingId]?.typeId
            val buildingSpec = buildingType?.let { data?.buildSpec(it) }
            if (buildingSpec != null && !buildingSpec.supportsRally) {
                emitCommandFailureRecord(
                    tick = cmd.tick,
                    commandType = "rally",
                    reason = "unsupportedRally",
                    snapshotOutPath = snapshotOutPath,
                    streamSequence = streamSequence,
                    buildingId = buildingId
                )
                emitRallyFailureRecord(cmd.tick, "unsupportedRally", snapshotOutPath, streamSequence, buildingId)
                emitCommandAck(false, reason = "unsupportedRally")
                return
            }
            world.rallyPoints[buildingId] = RallyPoint(cmd.x, cmd.y)
            emitRallyRecord(cmd.tick, buildingId, cmd.x, cmd.y, snapshotOutPath, streamSequence)
            emitCommandAck(true, entityId = buildingId)
        }
    }
}

private fun commandTypeName(cmd: Command): String =
    when (cmd) {
        is Command.Move -> "move"
        is Command.MoveFaction -> "moveFaction"
        is Command.MoveType -> "moveType"
        is Command.MoveArchetype -> "moveArchetype"
        is Command.Patrol -> "patrol"
        is Command.PatrolFaction -> "patrolFaction"
        is Command.PatrolType -> "patrolType"
        is Command.PatrolArchetype -> "patrolArchetype"
        is Command.AttackMove -> "attackMove"
        is Command.AttackMoveFaction -> "attackMoveFaction"
        is Command.AttackMoveType -> "attackMoveType"
        is Command.AttackMoveArchetype -> "attackMoveArchetype"
        is Command.Hold -> "hold"
        is Command.HoldFaction -> "holdFaction"
        is Command.HoldType -> "holdType"
        is Command.HoldArchetype -> "holdArchetype"
        is Command.Attack -> "attack"
        is Command.AttackFaction -> "attackFaction"
        is Command.AttackType -> "attackType"
        is Command.AttackArchetype -> "attackArchetype"
        is Command.Harvest -> "harvest"
        is Command.HarvestFaction -> "harvestFaction"
        is Command.HarvestType -> "harvestType"
        is Command.HarvestArchetype -> "harvestArchetype"
        is Command.Construct -> "construct"
        is Command.ConstructFaction -> "constructFaction"
        is Command.ConstructType -> "constructType"
        is Command.ConstructArchetype -> "constructArchetype"
        is Command.SpawnNode -> "spawnNode"
        is Command.Spawn -> "spawn"
        is Command.Build -> "build"
        is Command.Train -> "train"
        is Command.CancelBuild -> "cancelBuild"
        is Command.CancelTrain -> "cancelTrain"
        is Command.Research -> "research"
        is Command.CancelResearch -> "cancelResearch"
        is Command.Rally -> "rally"
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

private fun collectArchetypeTargets(archetype: String, world: World, data: DataRepo?): IntArray {
    val ids = IntArray(world.tags.size)
    var count = 0
    for ((id, tag) in world.tags) {
        val tagArchetype = data?.buildingArchetype(tag.typeId) ?: data?.unitArchetype(tag.typeId)
        if (tagArchetype == archetype) {
            ids[count++] = id
        }
    }
    return if (count == ids.size) ids else ids.copyOf(count)
}

private fun assignHarvesters(units: IntArray, target: Int, world: World) {
    val nodeTransform = world.transforms[target] ?: return
    if (!world.resourceNodes.containsKey(target)) return
    for (i in units.indices) {
        val unitId = units[i]
        world.harvesters[unitId] = Harvester(targetNodeId = target)
        world.orders[unitId]?.items?.addLast(Order.Move(nodeTransform.x, nodeTransform.y))
    }
}

private fun assignConstructors(units: IntArray, target: Int, world: World) {
    if (!world.constructionSites.containsKey(target)) return
    for (i in units.indices) {
        val unitId = units[i]
        world.builderTasks[unitId] = BuilderTask(target)
        world.orders[unitId]?.items?.addLast(Order.Construct(target))
    }
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

private fun emitRallyRecord(
    tick: Int,
    buildingId: Int,
    x: Float,
    y: Float,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderRallyStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            buildingId = buildingId,
            x = x,
            y = y,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitRallyFailureRecord(
    tick: Int,
    reason: String,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?,
    buildingId: Int? = null
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderRallyFailureStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            reason = reason,
            buildingId = buildingId,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitProducerFailureRecord(
    tick: Int,
    reason: String,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?,
    buildingId: Int? = null,
    producerTypeId: String? = null,
    typeId: String? = null
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderProducerFailureStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            reason = reason,
            buildingId = buildingId,
            producerTypeId = producerTypeId,
            typeId = typeId,
            pretty = false
        ),
        snapshotOutPath
    )
}

private fun emitTrainFailureRecord(
    tick: Int,
    typeId: String,
    reason: String,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?,
    buildingId: Int? = null,
    producerTypeId: String? = null
) {
    if (snapshotOutPath == null || streamSequence == null) return
    emitSnapshotLine(
        renderTrainFailureStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            tick = tick,
            typeId = typeId,
            reason = reason,
            buildingId = buildingId,
            producerTypeId = producerTypeId,
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
    world: World,
    map: MapGrid,
    occ: OccupancyGrid,
    snapshotOutPath: java.nio.file.Path?,
    streamSequence: LongArray?
) {
    if (snapshotOutPath == null || streamSequence == null) return
    val blockedTiles = ArrayList<MapBlockedTileRecord>()
    val weightedTiles = ArrayList<MapCostTileRecord>()
    val staticOccupancyTiles = ArrayList<MapBlockedTileRecord>()
    val resourceNodes = ArrayList<MapResourceNodeRecord>(world.resourceNodes.size)
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
    val resourceIds = world.resourceNodes.keys.sorted()
    for (id in resourceIds) {
        val node = world.resourceNodes[id] ?: continue
        val transform = world.transforms[id] ?: continue
        val tag = world.tags[id] ?: continue
        resourceNodes.add(
            MapResourceNodeRecord(
                id = id,
                kind = tag.typeId,
                x = transform.x,
                y = transform.y,
                remaining = node.remaining,
                yieldPerTick = node.yieldPerTick
            )
        )
    }
    emitSnapshotLine(
        renderMapStateStreamRecordJson(
            sequence = nextStreamSequence(streamSequence),
            width = map.width,
            height = map.height,
            blockedTiles = blockedTiles,
            weightedTiles = weightedTiles,
            staticOccupancyTiles = staticOccupancyTiles,
            resourceNodes = resourceNodes,
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
