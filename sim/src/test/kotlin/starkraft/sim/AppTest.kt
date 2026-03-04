package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.Harvester
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.ResourceHarvestSystem
import starkraft.sim.ecs.ResourceNode
import starkraft.sim.ecs.ResourceSystem
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayMetadata
import java.nio.file.Files

class AppTest {
    @Test
    fun `issue emits command ack records for success and failure`() {
        val world = World()
        val data = DataRepo("""{"list":[]}""", """{"list":[]}""", """{"list":[]}""")
        val out = Files.createTempFile("starkraft-command-ack", ".ndjson")
        val seq = longArrayOf(0L)
        val unitId = world.spawn(Transform(2f, 2f), UnitTag(1, "Marine"), Health(45, 45), null)

        issue(
            Command.Move(0, intArrayOf(unitId), 5f, 6f),
            world,
            starkraft.sim.replay.NullRecorder(),
            data = data,
            snapshotOutPath = out,
            streamSequence = seq,
            requestId = "cli-1"
        )
        issue(
            Command.Rally(1, 999, 10f, 11f),
            world,
            starkraft.sim.replay.NullRecorder(),
            data = data,
            snapshotOutPath = out,
            streamSequence = seq,
            requestId = "cli-2"
        )

        val lines = Files.readAllLines(out)
        assertTrue(lines.any { it.contains("\"recordType\":\"commandAck\"") && it.contains("\"commandType\":\"move\"") && it.contains("\"requestId\":\"cli-1\"") && it.contains("\"accepted\":true") && it.contains("\"appliedUnits\":1") })
        assertTrue(lines.any { it.contains("\"recordType\":\"commandAck\"") && it.contains("\"commandType\":\"rally\"") && it.contains("\"requestId\":\"cli-2\"") && it.contains("\"accepted\":false") && it.contains("\"reason\":\"missingBuilding\"") })
    }

    @Test
    fun `warns on replay compatibility mismatch`() {
        val warnings =
            replayCompatibilityWarnings(
                    ReplayMetadata(
                        schema = 1,
                        replayHash = 10L,
                        seed = 5L,
                        mapId = "other-map",
                        buildVersion = "0.9.0",
                        eventCount = 2,
                        fileSizeBytes = 128,
                        legacy = false
                    )
            )

        assertEquals(
            listOf(
                "replay warning: mapId=other-map current=demo-32x32-obstacles",
                "replay warning: buildVersion=0.9.0 current=1.0-SNAPSHOT"
            ),
            warnings
        )
    }

    @Test
    fun `skips warning for matching or legacy replay metadata`() {
        assertEquals(
            emptyList<String>(),
            replayCompatibilityWarnings(
                    ReplayMetadata(
                        schema = 1,
                        replayHash = 10L,
                        seed = 5L,
                        mapId = "demo-32x32-obstacles",
                        buildVersion = "1.0-SNAPSHOT",
                        eventCount = 2,
                        fileSizeBytes = 128,
                        legacy = false
                    )
            )
        )
        assertEquals(
            emptyList<String>(),
            replayCompatibilityWarnings(
                    ReplayMetadata(
                        schema = 0,
                        replayHash = null,
                        seed = null,
                        mapId = null,
                        buildVersion = "0.9.0",
                        eventCount = 0,
                        fileSizeBytes = 0,
                        legacy = true
                    )
            )
        )
    }

    @Test
    fun `strict replay compatibility fails on mismatch`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                requireReplayCompatibility(
                    ReplayMetadata(
                        schema = 1,
                        replayHash = 10L,
                        seed = 5L,
                        mapId = "other-map",
                        buildVersion = "0.9.0",
                        eventCount = 2,
                        fileSizeBytes = 128,
                        legacy = false
                    ),
                    strict = true
                )
            }

        assertEquals(
            "replay warning: mapId=other-map current=demo-32x32-obstacles\n" +
                "replay warning: buildVersion=0.9.0 current=1.0-SNAPSHOT",
            ex.message
        )
    }

    @Test
    fun `prints current runtime metadata line`() {
        assertEquals(
            "runtime metadata: mapId=demo-32x32-obstacles buildVersion=1.0-SNAPSHOT seed=42",
            currentRuntimeMetadataLine(42L)
        )
    }

    @Test
    fun `renders compact command outcome log suffix`() {
        val counters =
            CommandOutcomeCounters(
                builds = 1,
                buildsCancelled = 1,
                buildFailures = 2,
                buildFailureReasons =
                    BuildFailureCounterSet(
                        invalidPlacement = 1,
                        insufficientResources = 1
                    ),
                trainsQueued = 3,
                trainsCancelled = 1,
                trainFailures = 2,
                trainFailureReasons =
                    TrainFailureCounterSet(
                        queueFull = 1,
                        incompatibleProducer = 1
                    ),
                researchQueued = 2,
                researchCancelled = 1,
                researchFailures = 2,
                researchFailureReasons =
                    ResearchFailureCounterSet(
                        invalidTech = 1,
                        alreadyUnlocked = 1,
                        nothingToCancel = 1
                    )
            )

        assertEquals(
            "  builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q3/c2/x1 trainFails=2[incompatibleProducer=1,queueFull=1] research=q2/c1/x1 queues=1 researchFails=2[invalidTech=1,alreadyUnlocked=1,nothingToCancel=1] cycles=p2/3 d1/1 retargets=1 dropoffs=f1:1/f2:0 compat=m1/g0",
            renderCommandOutcomeLogSuffix(
                counters,
                trainsCompleted = 2,
                researchCancelled = 1,
                researchCompleted = 1,
                researchQueueBuildings = 1,
                harvestPickupCount = 2,
                harvestDepositCount = 1,
                harvestPickupAmount = 3,
                harvestDepositAmount = 1,
                harvesterRetargets = 1,
                dropoffBuildingsFaction1 = 1,
                dropoffBuildingsFaction2 = 0,
                mineralDropoffBuildings = 1,
                gasDropoffBuildings = 0
            )
        )
    }

    @Test
    fun `renders aggregate command outcome summary`() {
        assertEquals(
            "command outcomes: builds=2/x1 buildFails=1[invalidDefinition=1] train=q4/c3/x1 trainFails=2[missingBuilding=1,nothingToCancel=1] research=q2/c1/x1 queues=1 researchFails=2[missingTech=1,queueFull=1,nothingToCancel=1]",
            renderAggregateOutcomeSummary(
                totalBuilds = 2,
                totalBuildsCancelled = 1,
                totalBuildFailures = 1,
                totalBuildFailureReasons = BuildFailureCounterSet(invalidDefinition = 1),
                totalTrainsQueued = 4,
                totalTrainsCompleted = 3,
                totalTrainsCancelled = 1,
                totalTrainFailures = 2,
                totalTrainFailureReasons = TrainFailureCounterSet(missingBuilding = 1, nothingToCancel = 1),
                totalResearchQueued = 2,
                totalResearchCancelled = 1,
                totalResearchCompleted = 1,
                totalResearchFailures = 2,
                totalResearchFailureReasons = ResearchFailureCounterSet(missingTech = 1, queueFull = 1, nothingToCancel = 1),
                currentResearchQueueBuildings = 1
            )
        )
    }

    @Test
    fun `renders aggregate harvest summary`() {
        assertEquals(
            "command outcomes: builds=0/x0 train=q0/c0/x0 harvest=9/2 f1=7/0 f2=2/2 cycles=p6/11 d5/9 retargets=4 nodes=6 depleted=1 active=2 remaining=347 dropoffs=f1:1/f2:2 compat=m2/g1",
            renderAggregateOutcomeSummary(
                totalBuilds = 0,
                totalBuildsCancelled = 0,
                totalBuildFailures = 0,
                totalBuildFailureReasons = BuildFailureCounterSet(),
                totalTrainsQueued = 0,
                totalTrainsCompleted = 0,
                totalTrainsCancelled = 0,
                totalTrainFailures = 0,
                totalTrainFailureReasons = TrainFailureCounterSet(),
                totalHarvestedMinerals = 9,
                totalHarvestedGas = 2,
                totalDepletedNodes = 1,
                totalChangedResourceNodes = 6,
                totalHarvestedMineralsFaction1 = 7,
                totalHarvestedMineralsFaction2 = 2,
                totalHarvestedGasFaction1 = 0,
                totalHarvestedGasFaction2 = 2,
                totalHarvestPickupCount = 6,
                totalHarvestDepositCount = 5,
                totalHarvestPickupAmount = 11,
                totalHarvestDepositAmount = 9,
                totalHarvesterRetargets = 4,
                dropoffBuildingsFaction1 = 1,
                dropoffBuildingsFaction2 = 2,
                mineralDropoffBuildings = 2,
                gasDropoffBuildings = 1,
                currentResourceNodeCount = 2,
                currentResourceNodeRemaining = 347
            )
        )
    }

    @Test
    fun `omits aggregate command outcome summary when empty`() {
        assertEquals(
            null,
            renderAggregateOutcomeSummary(
                totalBuilds = 0,
                totalBuildsCancelled = 0,
                totalBuildFailures = 0,
                totalBuildFailureReasons = BuildFailureCounterSet(),
                totalTrainsQueued = 0,
                totalTrainsCompleted = 0,
                totalTrainsCancelled = 0,
                totalTrainFailures = 0,
                totalTrainFailureReasons = TrainFailureCounterSet(),
                totalHarvestedMinerals = 0,
                totalHarvestedGas = 0,
                totalDepletedNodes = 0,
                totalChangedResourceNodes = 0,
                totalHarvestedMineralsFaction1 = 0,
                totalHarvestedMineralsFaction2 = 0,
                totalHarvestedGasFaction1 = 0,
                totalHarvestedGasFaction2 = 0,
                totalHarvestPickupCount = 0,
                totalHarvestDepositCount = 0,
                totalHarvestPickupAmount = 0,
                totalHarvestDepositAmount = 0,
                totalHarvesterRetargets = 0,
                currentResourceNodeCount = 0,
                currentResourceNodeRemaining = 0
            )
        )
    }

    @Test
    fun `removes depleted resource nodes after harvest`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val nodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 1)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId)

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(null, world.resourceNodes[nodeId])
        assertEquals(null, world.transforms[nodeId])
        assertEquals(1, world.removedEventCount)
        assertEquals("resourceDepleted", world.removedReason(0))
    }

    @Test
    fun `clears harvesters targeting depleted node`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val nodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[nodeId] = ResourceNode(remaining = 1)
        world.harvesters[workerId] = Harvester(targetNodeId = nodeId)
        world.orders[workerId]?.items?.addLast(starkraft.sim.ecs.Order.Move(5f, 5f))

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(null, world.harvesters[workerId])
        assertEquals(null, world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `retargets harvester to nearest matching node on depletion`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val depletedNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val fallbackNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val gasNodeId = world.spawn(Transform(5.5f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedNodeId] = ResourceNode(remaining = 1)
        world.resourceNodes[fallbackNodeId] = ResourceNode(remaining = 20)
        world.resourceNodes[gasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 20)
        world.harvesters[workerId] = Harvester(targetNodeId = depletedNodeId)
        world.orders[workerId]?.items?.addLast(starkraft.sim.ecs.Order.Move(5f, 5f))

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(fallbackNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(starkraft.sim.ecs.Order.Move(7f, 5f), world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `retargets harvester by resource kind on depletion`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val depletedNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val richFallbackNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "RichMineralField"), Health(1, 1), w = null)
        val gasNodeId = world.spawn(Transform(5.5f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 1)
        world.resourceNodes[richFallbackNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.resourceNodes[gasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 20)
        world.harvesters[workerId] = Harvester(targetNodeId = depletedNodeId)
        world.orders[workerId]?.items?.addLast(starkraft.sim.ecs.Order.Move(5f, 5f))

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(richFallbackNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(starkraft.sim.ecs.Order.Move(7f, 5f), world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `retarget prefers richer compatible nodes over nearer ones`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val depletedNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val nearPoorNodeId = world.spawn(Transform(5.6f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val farRichNodeId = world.spawn(Transform(8f, 5f), UnitTag(0, "RichMineralField"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 1)
        world.resourceNodes[nearPoorNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 2)
        world.resourceNodes[farRichNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.harvesters[workerId] = Harvester(targetNodeId = depletedNodeId)
        world.orders[workerId]?.items?.addLast(starkraft.sim.ecs.Order.Move(5f, 5f))

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(farRichNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(starkraft.sim.ecs.Order.Move(8f, 5f), world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `retargets gas harvesters only to gas nodes on depletion`() {
        val world = World()
        val resources = ResourceSystem(world)
        val harvest = ResourceHarvestSystem(world, resources)
        val depletedGasNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val mineralNodeId = world.spawn(Transform(5.5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val fallbackGasNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(2, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedGasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 1)
        world.resourceNodes[mineralNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.resourceNodes[fallbackGasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 20)
        world.harvesters[workerId] = Harvester(targetNodeId = depletedGasNodeId)
        world.orders[workerId]?.items?.addLast(starkraft.sim.ecs.Order.Move(5f, 5f))

        harvest.tick()
        removeDepletedResourceNodes(world, harvest)

        assertEquals(fallbackGasNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(starkraft.sim.ecs.Order.Move(7f, 5f), world.orders[workerId]?.items?.firstOrNull())
    }

    @Test
    fun `retargets loaded gas harvesters only to gas nodes on depletion`() {
        drainPendingHarvesterRetargetEvents()
        val world = World()
        val depletedGasNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val mineralNodeId = world.spawn(Transform(5.5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val fallbackGasNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(2, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedGasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 0)
        world.resourceNodes[mineralNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.resourceNodes[fallbackGasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 20)
        world.harvesters[workerId] =
            Harvester(
                targetNodeId = depletedGasNodeId,
                cargoKind = ResourceNode.KIND_GAS,
                cargoAmount = 1,
                returnTargetId = 1
            )
        world.orders[workerId]?.items?.addLast(Order.Move(5f, 5f))

        clearHarvestersForNode(world, depletedGasNodeId, 5f, 5f)

        assertEquals(fallbackGasNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(null, world.orders[workerId]?.items?.firstOrNull())
        val events = drainPendingHarvesterRetargetEvents()
        assertEquals(1, events.size)
        assertEquals(fallbackGasNodeId, events[0].toNodeId)
        assertEquals(ResourceNode.KIND_GAS, events[0].resourceKind)
    }

    @Test
    fun `retargets loaded mineral harvesters by mineral kind on depletion`() {
        drainPendingHarvesterRetargetEvents()
        val world = World()
        val depletedMineralNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val gasNodeId = world.spawn(Transform(5.5f, 5f), UnitTag(0, "GasGeyser"), Health(1, 1), w = null)
        val fallbackMineralNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "RichMineralField"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedMineralNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 0)
        world.resourceNodes[gasNodeId] = ResourceNode(kind = ResourceNode.KIND_GAS, remaining = 20)
        world.resourceNodes[fallbackMineralNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.harvesters[workerId] =
            Harvester(
                targetNodeId = depletedMineralNodeId,
                cargoKind = ResourceNode.KIND_MINERALS,
                cargoAmount = 1,
                returnTargetId = 1
            )
        world.orders[workerId]?.items?.addLast(Order.Move(5f, 5f))

        clearHarvestersForNode(world, depletedMineralNodeId, 5f, 5f)

        assertEquals(fallbackMineralNodeId, world.harvesters[workerId]?.targetNodeId)
        assertEquals(null, world.orders[workerId]?.items?.firstOrNull())
        val events = drainPendingHarvesterRetargetEvents()
        assertEquals(1, events.size)
        assertEquals(fallbackMineralNodeId, events[0].toNodeId)
        assertEquals(ResourceNode.KIND_MINERALS, events[0].resourceKind)
    }

    @Test
    fun `records harvester retarget events during depletion cleanup`() {
        drainPendingHarvesterRetargetEvents()
        val world = World()
        val depletedNodeId = world.spawn(Transform(5f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val fallbackNodeId = world.spawn(Transform(7f, 5f), UnitTag(0, "MineralField"), Health(1, 1), w = null)
        val workerId = world.spawn(Transform(5.4f, 5f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.resourceNodes[depletedNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 0)
        world.resourceNodes[fallbackNodeId] = ResourceNode(kind = ResourceNode.KIND_MINERALS, remaining = 20)
        world.harvesters[workerId] =
            Harvester(
                targetNodeId = depletedNodeId,
                cargoKind = ResourceNode.KIND_MINERALS,
                cargoAmount = 1,
                returnTargetId = 1
            )
        world.orders[workerId]?.items?.addLast(Order.Move(5f, 5f))

        clearHarvestersForNode(world, depletedNodeId, 5f, 5f)

        val events = drainPendingHarvesterRetargetEvents()
        assertEquals(1, events.size)
        assertEquals(workerId, events[0].workerId)
        assertEquals(depletedNodeId, events[0].fromNodeId)
        assertEquals(fallbackNodeId, events[0].toNodeId)
        assertEquals(ResourceNode.KIND_MINERALS, events[0].resourceKind)
    }

    @Test
    fun `finds project root from nested sim path`() {
        val root = Files.createTempDirectory("starkraft-root")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"starkraft\"")
        val nested = Files.createDirectories(root.resolve("sim/build/tmp"))

        assertEquals(root, findProjectRoot(nested))
    }

    @Test
    fun `resolves script path relative to project root`() {
        val root = Files.createTempDirectory("starkraft-root")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"starkraft\"")
        val simDir = Files.createDirectories(root.resolve("sim"))
        val script = Files.createDirectories(root.resolve("sim/scripts")).resolve("sample.script")
        Files.writeString(script, "wait 1")
        val resolved = resolvePathFromBase("sim/scripts/sample.script", simDir)

        assertEquals(script, resolved)
    }

    @Test
    fun `spawn script keeps build commands for dropoff setup`() {
        val root = Files.createTempDirectory("starkraft-root")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"starkraft\"")
        val script = root.resolve("spawn.script")
        Files.writeString(
            script,
            """
            tick 0
            build @dropoff 1 ResourceDepot 7 5
            spawnNode @ore MineralField 6 6 250
            spawn @worker 1 Worker 6.5 6 6
            select @worker
            harvest @ore
            """.trimIndent()
        )

        val program = loadSpawnScriptProgram(script.toString())

        assertEquals(1, program.commandsByTick.size)
        assertEquals(
            listOf(
                Command.Build(0, 1, "ResourceDepot", 7, 5, 0, 0, 0, 0, 0, 0, "dropoff", -1),
                Command.SpawnNode(0, "MineralField", 6f, 6f, 250, 0, "ore", -2),
                Command.Spawn(0, 1, "Worker", 6.5f, 6f, 6f, "worker", -3)
            ),
            program.commandsByTick[0]
        )
        assertEquals(1, program.selectionEventsByTick[0].size)
    }

    @Test
    fun `script validation rejects build with unknown type and missing defaults`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "MissingDepot", 4, 4, 0, 0, 0, 0, 0, 0)
                )
            )
        val data = DataRepo("""{"list":[]}""", """{"list":[]}""", """{"list":[]}""")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateBuildCommands(commandsByTick, data)
            }

        assertEquals(
            "Unknown building typeId 'MissingDepot' in build at tick 0 (missing defaults for width/height/hp)",
            ex.message
        )
    }

    @Test
    fun `script validation rejects unknown spawn node kind`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.SpawnNode(0, "CrystalField", 4f, 4f, 100, 1, "ore", -1)
                )
            )
        val data = DataRepo("""{"list":[]}""", """{"list":[]}""", """{"list":[]}""")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateSpawnTypes(commandsByTick, data)
            }

        assertEquals("Unknown resource node kind 'CrystalField' in spawnNode at tick 0", ex.message)
    }

    @Test
    fun `script validation rejects negative spawn node yield`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.SpawnNode(0, "MineralField", 4f, 4f, 100, -1, "ore", -1)
                )
            )
        val data = DataRepo("""{"list":[]}""", """{"list":[]}""", """{"list":[]}""")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateSpawnTypes(commandsByTick, data)
            }

        assertEquals("Invalid resource node yield '-1' in spawnNode at tick 0", ex.message)
    }

    @Test
    fun `script validation rejects build with invalid resolved footprint`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "Depot", 4, 4, 0, 0, 0, 0, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[]}""",
                """{"list":[]}""",
                """{"list":[{"id":"Depot","hp":0,"armor":1,"footprintWidth":2,"footprintHeight":0,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateBuildCommands(commandsByTick, data)
            }

        assertEquals(
            "Invalid build definition for 'Depot' at tick 0 (resolved width=2 height=0 hp=0)",
            ex.message
        )
    }

    @Test
    fun `script validation rejects build when required tech is missing`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "Factory", 4, 4, 0, 0, 0, 0, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[]}""",
                """{"list":[]}""",
                """{"list":[{"id":"Factory","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":150,"gasCost":0,"requiredBuildingTypes":["Depot"]}]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateBuildCommands(commandsByTick, data)
            }

        assertEquals("Missing tech for build 'Factory' at tick 0 (required=Depot)", ex.message)
    }

    @Test
    fun `script validation rejects train with unknown type and missing defaults`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Train(0, -1, "MissingMarine", 0, 0, 0)
                )
            )
        val data = DataRepo("""{"list":[]}""", """{"list":[]}""", """{"list":[]}""")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateTrainCommands(commandsByTick, data)
            }

        assertEquals(
            "Unknown unit typeId 'MissingMarine' in train at tick 0 (missing default buildTicks)",
            ex.message
        )
    }

    @Test
    fun `script validation rejects incompatible labeled producer for train`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "Factory", 4, 4, 2, 2, 400, 0, 100, 0, "factory", -2),
                    Command.Train(0, -2, "Marine", 0, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[{"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Depot"]}]}""",
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """{"list":[
                    {"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0},
                    {"id":"Factory","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}
                ]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateTrainCommands(commandsByTick, data)
            }

        assertEquals(
            "Incompatible producer 'Factory' for 'Marine' in train at tick 0 (allowed=Depot)",
            ex.message
        )
    }

    @Test
    fun `script validation rejects train when tech is missing`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "Depot", 4, 4, 2, 2, 400, 0, 100, 0, "depot", -1),
                    Command.Train(0, -1, "Marine", 0, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[{"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Depot"],"requiredBuildingTypes":["Academy"]}]}""",
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """{"list":[
                    {"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0},
                    {"id":"Academy","hp":250,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":125,"gasCost":0,"requiredBuildingTypes":["Depot"]}
                ]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateTrainCommands(commandsByTick, data)
            }

        assertEquals("Missing tech for train 'Marine' at tick 0 (required=Academy)", ex.message)
    }

    @Test
    fun `script validation rejects labeled queue overflow`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Build(0, 1, "Depot", 4, 4, 2, 2, 400, 0, 100, 0, "depot", -1),
                    Command.Train(0, -1, "Marine", 5, 0, 0),
                    Command.Train(0, -1, "Marine", 5, 0, 0),
                    Command.Train(0, -1, "Marine", 5, 0, 0),
                    Command.Train(0, -1, "Marine", 5, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[{"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Depot"]}]}""",
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """{"list":[{"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateTrainCommands(commandsByTick, data)
            }

        assertEquals(
            "Queue limit exceeded for 'Depot' in train at tick 0 (limit=3, queued=3)",
            ex.message
        )
    }

    @Test
    fun `script validation allows queue reuse after optimistic completion`() {
        val commandsByTick =
            Array(7) { arrayListOf<Command>() }.also { ticks ->
                ticks[0].add(Command.Build(0, 1, "Depot", 4, 4, 2, 2, 400, 0, 100, 0, "depot", -1))
                ticks[0].add(Command.Train(0, -1, "Marine", 2, 0, 0))
                ticks[0].add(Command.Train(0, -1, "Marine", 2, 0, 0))
                ticks[0].add(Command.Train(0, -1, "Marine", 2, 0, 0))
                ticks[6].add(Command.Train(6, -1, "Marine", 2, 0, 0))
            }
        val data =
            DataRepo(
                """{"list":[{"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Depot"]}]}""",
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """{"list":[{"id":"Depot","hp":400,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":true,"supportsRally":true,"productionQueueLimit":3,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}]}"""
            )

        validateTrainCommands(commandsByTick, data)
    }

    @Test
    fun `script validation rejects producer without training support`() {
        val commandsByTick =
            arrayOf(
                arrayListOf(
                    Command.Build(0, 1, "Tower", 4, 4, 2, 2, 300, 0, 100, 0, "tower", -1),
                    Command.Train(0, -1, "Marine", 0, 0, 0)
                )
            )
        val data =
            DataRepo(
                """{"list":[{"id":"Marine","hp":45,"armor":0,"speed":0.06,"weaponId":"Gauss","mineralCost":50,"buildTicks":75,"producerTypes":["Tower"]}]}""",
                """{"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":15}]}""",
                """{"list":[{"id":"Tower","hp":300,"armor":1,"footprintWidth":2,"footprintHeight":2,"placementClearance":1,"supportsTraining":false,"supportsRally":false,"productionQueueLimit":0,"rallyOffsetX":0.0,"rallyOffsetY":0.0,"mineralCost":100,"gasCost":0}]}"""
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                validateTrainCommands(commandsByTick, data)
            }

        assertEquals("Producer 'Tower' does not support training at tick 0", ex.message)
    }

    @Test
    fun `builds selector split command totals`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Move(0, intArrayOf(1, 2), 4f, 5f),
                    Command.AttackFaction(0, 1, 9)
                ),
                arrayListOf<Command>(
                    Command.MoveType(1, "Marine", 7f, 8f),
                    Command.AttackArchetype(1, "infantry", 10),
                    Command.Spawn(1, 1, "Marine", 2f, 3f)
                )
            )

        val stats = buildCommandStats(commandsByTick, replayMeta = null)

        assertEquals(5, stats.totals.total)
        assertEquals(1, stats.totals.spawns)
        assertEquals(2, stats.totals.moves)
        assertEquals(2, stats.totals.attacks)
        assertEquals(2, stats.ticks.size)
        assertEquals(2, stats.ticks[0].commands)
        assertEquals(0, stats.ticks[0].spawns)
        assertEquals(1, stats.ticks[0].moves)
        assertEquals(1, stats.ticks[0].attacks)
        assertEquals(1, stats.ticks[0].selectors.direct)
        assertEquals(1, stats.ticks[0].selectors.faction)
        assertEquals(0, stats.ticks[0].selectors.type)
        assertEquals(1, stats.ticks[1].spawns)
        assertEquals(1, stats.ticks[1].moves)
        assertEquals(1, stats.ticks[1].attacks)
        assertEquals(0, stats.ticks[1].selectors.direct)
        assertEquals(0, stats.ticks[1].selectors.faction)
        assertEquals(1, stats.ticks[1].selectors.type)
        assertEquals(1, stats.ticks[1].selectors.archetype)
        assertEquals(1, stats.ticks[0].selectors.direct)
        assertEquals(0, stats.ticks[0].selectors.archetype)
        assertEquals(1, stats.totals.selectors.direct)
        assertEquals(1, stats.totals.selectors.faction)
        assertEquals(1, stats.totals.selectors.type)
        assertEquals(1, stats.totals.selectors.archetype)
        assertEquals(1, stats.totals.breakdown.move.direct)
        assertEquals(0, stats.totals.breakdown.move.faction)
        assertEquals(1, stats.totals.breakdown.move.type)
        assertEquals(0, stats.totals.breakdown.move.archetype)
        assertEquals(0, stats.totals.breakdown.attack.direct)
        assertEquals(1, stats.totals.breakdown.attack.faction)
        assertEquals(0, stats.totals.breakdown.attack.type)
        assertEquals(1, stats.totals.breakdown.attack.archetype)
    }

    @Test
    fun `renders command stats json golden shape`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Move(0, intArrayOf(1), 4f, 5f),
                    Command.Spawn(0, 1, "Marine", 2f, 3f)
                )
            )

        val json = renderCommandStatsJson(buildCommandStats(commandsByTick, replayMeta = null))

        assertEquals(
            """
            {
                "metadata": null,
                "warnings": [],
                "ticks": [
                    {
                        "tick": 0,
                        "commands": 2,
                        "spawns": 1,
                        "moves": 1,
                        "attacks": 0,
                        "selectors": {
                            "direct": 1,
                            "faction": 0,
                            "type": 0,
                            "archetype": 0
                        },
                        "breakdown": {
                            "move": {
                                "direct": 1,
                                "faction": 0,
                                "type": 0,
                                "archetype": 0
                            },
                            "attack": {
                                "direct": 0,
                                "faction": 0,
                                "type": 0,
                                "archetype": 0
                            }
                        }
                    }
                ],
                "totals": {
                    "total": 2,
                    "spawns": 1,
                    "moves": 1,
                    "attacks": 0,
                    "selectors": {
                        "direct": 1,
                        "faction": 0,
                        "type": 0,
                        "archetype": 0
                    },
                    "breakdown": {
                        "move": {
                            "direct": 1,
                            "faction": 0,
                            "type": 0,
                            "archetype": 0
                        },
                        "attack": {
                            "direct": 0,
                            "faction": 0,
                            "type": 0,
                            "archetype": 0
                        }
                    }
                }
            }
            """.trimIndent(),
            json
        )
    }

    @Test
    fun `renders compact command stats json on one line`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Move(0, intArrayOf(1), 4f, 5f),
                    Command.Spawn(0, 1, "Marine", 2f, 3f)
                )
            )

        val json = renderCommandStatsJson(buildCommandStats(commandsByTick, replayMeta = null), pretty = false)

        assertTrue(!json.contains("\n"))
        assertTrue(json.startsWith("{\"metadata\":null,"))
        assertTrue(json.contains("\"totals\":{\"total\":2"))
    }

    @Test
    fun `renders compact command stats text for many ticks`() {
        val commandsByTick =
            Array(13) { tick ->
                arrayListOf<Command>(Command.Move(tick, intArrayOf(tick + 1), tick.toFloat(), tick.toFloat()))
            }

        val text = renderCommandStatsText(buildCommandStats(commandsByTick, replayMeta = null))

        assertTrue(text.contains("tick=0 commands=1"))
        assertTrue(text.contains("tick=4 commands=1"))
        assertTrue(text.contains("... 3 ticks omitted ..."))
        assertTrue(text.contains("tick=8 commands=1"))
        assertTrue(text.contains("tick=12 commands=1"))
        assertTrue(text.contains("total=13 spawns=0 moves=13 attacks=0"))
    }

    @Test
    fun `renders replay metadata json golden shape`() {
        val json =
            renderReplayMetaJson(
                buildReplayMetaReport(
                    replayMeta =
                        ReplayMetadata(
                            schema = 1,
                            replayHash = 12L,
                            seed = 42L,
                            mapId = "other-map",
                            buildVersion = "0.9.0",
                            eventCount = 5,
                            fileSizeBytes = 256,
                            legacy = false
                        ),
                    replayPath = "/tmp/demo.replay.json",
                    currentMapId = "demo-32x32-obstacles",
                    currentBuildVersion = "1.0-SNAPSHOT",
                    currentSeed = 99L,
                    strictReplayMeta = true,
                    strictReplayHash = true
                )
            )

        assertEquals(
            """
            {
                "replayPath": "/tmp/demo.replay.json",
                "currentMapId": "demo-32x32-obstacles",
                "currentBuildVersion": "1.0-SNAPSHOT",
                "currentSeed": 99,
                "strictReplayMeta": true,
                "strictReplayHash": true,
                "metadata": {
                    "schema": 1,
                    "replayHash": 12,
                    "seed": 42,
                    "mapId": "other-map",
                    "buildVersion": "0.9.0",
                    "eventCount": 5,
                    "fileSizeBytes": 256,
                    "legacy": false
                },
                "warnings": [
                    "replay warning: mapId=other-map current=demo-32x32-obstacles",
                    "replay warning: buildVersion=0.9.0 current=1.0-SNAPSHOT"
                ]
            }
            """.trimIndent(),
            json
        )
    }

    @Test
    fun `renders compact replay metadata json on one line`() {
        val json =
            renderReplayMetaJson(
                buildReplayMetaReport(
                    replayMeta =
                        ReplayMetadata(
                            schema = 1,
                            replayHash = 12L,
                            seed = 42L,
                            mapId = "other-map",
                            buildVersion = "0.9.0",
                            eventCount = 5,
                            fileSizeBytes = 256,
                            legacy = false
                        ),
                    replayPath = "/tmp/demo.replay.json",
                    currentMapId = "demo-32x32-obstacles",
                    currentBuildVersion = "1.0-SNAPSHOT",
                    currentSeed = 99L,
                    strictReplayMeta = true,
                    strictReplayHash = true
                ),
                pretty = false
            )

        assertTrue(!json.contains("\n"))
        assertTrue(json.startsWith("{\"replayPath\":\"/tmp/demo.replay.json\""))
        assertTrue(json.contains("\"metadata\":{\"schema\":1"))
    }

    @Test
    fun `builds replay metadata report with warnings`() {
        val report =
            buildReplayMetaReport(
                replayMeta =
                    ReplayMetadata(
                        schema = 1,
                        replayHash = 12L,
                        seed = 42L,
                        mapId = "other-map",
                        buildVersion = "0.9.0",
                        eventCount = 5,
                        fileSizeBytes = 256,
                        legacy = false
                    ),
                replayPath = "/tmp/demo.replay.json",
                currentMapId = "demo-32x32-obstacles",
                currentBuildVersion = "1.0-SNAPSHOT",
                currentSeed = 99L,
                strictReplayMeta = true,
                strictReplayHash = true
            )

        assertEquals("/tmp/demo.replay.json", report.replayPath)
        assertEquals("demo-32x32-obstacles", report.currentMapId)
        assertEquals("1.0-SNAPSHOT", report.currentBuildVersion)
        assertEquals(99L, report.currentSeed)
        assertEquals(true, report.strictReplayMeta)
        assertEquals(true, report.strictReplayHash)
        assertEquals(1, report.metadata?.schema)
        assertEquals(12L, report.metadata?.replayHash)
        assertEquals(42L, report.metadata?.seed)
        assertEquals("other-map", report.metadata?.mapId)
        assertEquals("0.9.0", report.metadata?.buildVersion)
        assertEquals(5, report.metadata?.eventCount)
        assertEquals(256L, report.metadata?.fileSizeBytes)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun `builds replay metadata report defaults without context`() {
        val report =
            buildReplayMetaReport(
                replayMeta =
                    ReplayMetadata(
                        schema = 1,
                        replayHash = 12L,
                        seed = 42L,
                        mapId = "demo-32x32-obstacles",
                        buildVersion = "1.0-SNAPSHOT",
                        eventCount = 1,
                        fileSizeBytes = 64,
                        legacy = false
                    )
            )

        assertEquals(null, report.replayPath)
        assertEquals(null, report.currentMapId)
        assertEquals(null, report.currentBuildVersion)
        assertEquals(null, report.currentSeed)
        assertEquals(false, report.strictReplayMeta)
        assertEquals(false, report.strictReplayHash)
        assertEquals(1, report.metadata?.schema)
        assertEquals(1, report.metadata?.eventCount)
        assertEquals(64L, report.metadata?.fileSizeBytes)
        assertEquals(emptyList<String>(), report.warnings)
    }
}
