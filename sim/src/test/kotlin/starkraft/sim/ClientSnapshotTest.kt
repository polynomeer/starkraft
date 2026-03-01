package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.BuildFailureCounts
import starkraft.sim.client.buildClientSnapshot
import starkraft.sim.client.renderBuildFailureStreamRecordJson
import starkraft.sim.client.CombatEventRecord
import starkraft.sim.client.DamageEventRecord
import starkraft.sim.client.DespawnEventRecord
import starkraft.sim.client.EconomyFactionRecord
import starkraft.sim.client.OrderQueueEntityRecord
import starkraft.sim.client.OccupancyChangeEventRecord
import starkraft.sim.client.MapBlockedTileRecord
import starkraft.sim.client.MapCostTileRecord
import starkraft.sim.client.PathAssignedEventRecord
import starkraft.sim.client.PathProgressEventRecord
import starkraft.sim.client.ProductionEventRecord
import starkraft.sim.client.VisionChangeEventRecord
import starkraft.sim.client.renderClientSnapshotJson
import starkraft.sim.client.renderCombatStreamRecordJson
import starkraft.sim.client.renderCommandStreamRecordJson
import starkraft.sim.client.renderCommandFailureStreamRecordJson
import starkraft.sim.client.renderDamageStreamRecordJson
import starkraft.sim.client.renderDespawnStreamRecordJson
import starkraft.sim.client.renderEconomyStreamRecordJson
import starkraft.sim.client.renderMetricsStreamRecordJson
import starkraft.sim.client.renderMapStateStreamRecordJson
import starkraft.sim.client.renderOrderAppliedStreamRecordJson
import starkraft.sim.client.renderOrderQueueStreamRecordJson
import starkraft.sim.client.renderOccupancyChangeStreamRecordJson
import starkraft.sim.client.renderPathAssignedStreamRecordJson
import starkraft.sim.client.renderPathProgressStreamRecordJson
import starkraft.sim.client.renderProducerFailureStreamRecordJson
import starkraft.sim.client.renderRallyFailureStreamRecordJson
import starkraft.sim.client.renderRallyStreamRecordJson
import starkraft.sim.client.renderSelectionStreamRecordJson
import starkraft.sim.client.renderSessionStatsStreamRecordJson
import starkraft.sim.client.renderSnapshotSessionEndJson
import starkraft.sim.client.renderSnapshotSessionStartJson
import starkraft.sim.client.renderSnapshotStreamRecordJson
import starkraft.sim.client.renderSpawnStreamRecordJson
import starkraft.sim.client.renderTickSummaryStreamRecordJson
import starkraft.sim.client.renderTrainFailureStreamRecordJson
import starkraft.sim.client.renderVisionStreamRecordJson
import starkraft.sim.client.renderProductionStreamRecordJson
import starkraft.sim.client.MetricsFactionRecord
import starkraft.sim.client.TrainFailureCounts
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.BuildingFootprint
import starkraft.sim.ecs.ProductionJob
import starkraft.sim.ecs.ProductionQueue
import starkraft.sim.ecs.RallyPoint
import starkraft.sim.ecs.ResourceStockpile
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.Vision
import starkraft.sim.ecs.WeaponRef
import starkraft.sim.ecs.World
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.net.Command
import java.nio.file.Files

class ClientSnapshotTest {
    @Test
    fun `builds sorted client snapshot from world state`() {
        val world = World()
        val map = MapGrid(32, 32)
        val fog1 = FogGrid(16, 16, 1f)
        val fog2 = FogGrid(16, 16, 1f)
        val idA = world.spawn(Transform(4f, 5f, 0.5f), UnitTag(2, "Zergling"), Health(30, 35, 1), WeaponRef("Claw", 3))
        val idB = world.spawn(Transform(1f, 2f, 1.5f), UnitTag(1, "Marine"), Health(40, 45, 0), WeaponRef("Gauss", 1))
        world.visions[idA] = Vision(6f)
        world.visions[idB] = Vision(7f)
        world.orders[idA]?.items?.addLast(Order.Attack(idB))
        world.orders[idB]?.items?.addLast(Order.Move(9f, 9f))
        world.productionQueues[idB] = ProductionQueue(ArrayDeque(listOf(ProductionJob("Marine", 12), ProductionJob("Marine", 30))))
        world.footprints[idB] = BuildingFootprint(tileX = 0, tileY = 1, width = 2, height = 3, clearance = 1)
        world.rallyPoints[idB] = RallyPoint(10f, 11f)
        world.stockpiles[1] = ResourceStockpile(150, 25)
        world.stockpiles[2] = ResourceStockpile(80, 10)
        fog1.markVisible(1f, 2f, 2f)
        fog2.markVisible(4f, 5f, 3f)

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 12,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = 7L,
            fogByFaction = mapOf(1 to fog1, 2 to fog2)
        )

        assertEquals(12, snapshot.tick)
        assertEquals("demo-map", snapshot.mapId)
        assertEquals("test-build", snapshot.buildVersion)
        assertEquals(7L, snapshot.seed)
        assertEquals(listOf(idA, idB).sorted(), snapshot.entities.map { it.id })
        val entitiesById = snapshot.entities.associateBy { it.id }
        assertEquals("Attack", entitiesById[idA]?.activeOrder)
        assertEquals("Move", entitiesById[idB]?.activeOrder)
        assertEquals(2, entitiesById[idB]?.productionQueueSize)
        assertEquals("Marine", entitiesById[idB]?.activeProductionType)
        assertEquals(12, entitiesById[idB]?.activeProductionRemainingTicks)
        assertEquals(2, entitiesById[idB]?.footprintWidth)
        assertEquals(3, entitiesById[idB]?.footprintHeight)
        assertEquals(1, entitiesById[idB]?.placementClearance)
        assertEquals(10f, entitiesById[idB]?.rallyX)
        assertEquals(11f, entitiesById[idB]?.rallyY)
        assertEquals(listOf(1, 2), snapshot.factions.map { it.faction })
        assertTrue(snapshot.factions[0].visibleTiles > 0)
        assertTrue(snapshot.factions[1].visibleTiles > 0)
        assertEquals(150, snapshot.factions[0].minerals)
        assertEquals(25, snapshot.factions[0].gas)
        assertEquals(80, snapshot.factions[1].minerals)
        assertEquals(10, snapshot.factions[1].gas)
    }

    @Test
    fun `renders compact client snapshot json`() {
        val world = World()
        val map = MapGrid(8, 8)
        val fog = FogGrid(8, 8, 1f)
        world.spawn(Transform(1f, 1f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 1,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = null,
            fogByFaction = mapOf(1 to fog)
        )

        val json = renderClientSnapshotJson(snapshot, pretty = false)

        assertTrue(!json.contains("\n"))
        assertTrue(json.startsWith("{\"tick\":1,"))
        assertTrue(json.contains("\"entities\":[{\"id\":"))
    }

    @Test
    fun `emits snapshot ticks on configured cadence`() {
        assertEquals(true, shouldEmitSnapshotAtTick(0, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(1, 5))
        assertEquals(true, shouldEmitSnapshotAtTick(5, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(6, 5))
        assertEquals(false, shouldEmitSnapshotAtTick(5, 0))
    }

    @Test
    fun `writes snapshot lines to ndjson file`() {
        val path = Files.createTempFile("starkraft-snapshot", ".ndjson")
        Files.deleteIfExists(path)

        emitSnapshotLine("{\"recordType\":\"snapshot\",\"sequence\":0,\"tick\":1,\"snapshot\":{\"tick\":1}}", path)
        emitSnapshotLine("{\"recordType\":\"snapshot\",\"sequence\":1,\"tick\":2,\"snapshot\":{\"tick\":2}}", path)

        assertEquals(
            "{\"recordType\":\"snapshot\",\"sequence\":0,\"tick\":1,\"snapshot\":{\"tick\":1}}\n" +
                "{\"recordType\":\"snapshot\",\"sequence\":1,\"tick\":2,\"snapshot\":{\"tick\":2}}\n",
            Files.readString(path)
        )
    }

    @Test
    fun `renders snapshot stream record json`() {
        val world = World()
        val map = MapGrid(8, 8)
        val fog = FogGrid(8, 8, 1f)
        world.spawn(Transform(1f, 1f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))

        val snapshot = buildClientSnapshot(
            world = world,
            map = map,
            tick = 3,
            mapId = "demo-map",
            buildVersion = "test-build",
            seed = null,
            fogByFaction = mapOf(1 to fog)
        )

        val json = renderSnapshotStreamRecordJson(snapshot, sequence = 4L, pretty = false)

        assertTrue(json.startsWith("{\"recordType\":\"snapshot\",\"sequence\":4,\"tick\":3,"))
        assertTrue(json.contains("\"snapshot\":{\"tick\":3"))
    }

    @Test
    fun `renders snapshot session start json`() {
        val json = renderSnapshotSessionStartJson(0L, "demo-map", "test-build", 7L, pretty = false)

        assertEquals(
            "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
            json
        )
    }

    @Test
    fun `renders snapshot session end json`() {
        val json = renderSnapshotSessionEndJson(sequence = 5L, tick = 15, worldHash = 123L, replayHash = 456L, pretty = false)

        assertEquals(
            "{\"recordType\":\"sessionEnd\",\"sequence\":5,\"tick\":15,\"worldHash\":123,\"replayHash\":456}",
            json
        )
    }

    @Test
    fun `increments snapshot sequence monotonically`() {
        val state = longArrayOf(0L)
        assertEquals(0L, nextStreamSequence(state))
        assertEquals(1L, nextStreamSequence(state))
        assertEquals(2L, nextStreamSequence(state))
    }

    @Test
    fun `renders command stream record json`() {
        val json = renderCommandStreamRecordJson(Command.Move(3, intArrayOf(7, 8), 4f, 5f), sequence = 9L, pretty = false)

        assertEquals(
            "{\"recordType\":\"command\",\"sequence\":9,\"tick\":3,\"commandType\":\"move\",\"units\":[7,8],\"faction\":null,\"typeId\":null,\"target\":null,\"x\":4.0,\"y\":5.0,\"vision\":null,\"label\":null,\"labelId\":null}",
            json
        )
    }

    @Test
    fun `renders metrics stream record json`() {
        val json =
            renderMetricsStreamRecordJson(
                sequence = 10L,
                tick = 4,
                factions = listOf(MetricsFactionRecord(1, 5, 20, 150, 25), MetricsFactionRecord(2, 4, 18, 80, 10)),
                pathRequests = 3,
                pathSolved = 2,
                pathQueueSize = 7,
                avgPathLength = 6.5f,
                replans = 2,
                replansBlocked = 1,
                replansStuck = 1,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"metrics\",\"sequence\":10,\"tick\":4,\"factions\":[{\"faction\":1,\"alive\":5,\"visibleTiles\":20,\"minerals\":150,\"gas\":25},{\"faction\":2,\"alive\":4,\"visibleTiles\":18,\"minerals\":80,\"gas\":10}],\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":7,\"avgPathLength\":6.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1}",
            json
        )
    }

    @Test
    fun `renders economy stream record json`() {
        val json =
            renderEconomyStreamRecordJson(
                sequence = 11L,
                tick = 5,
                factions = listOf(EconomyFactionRecord(1, 150, 25), EconomyFactionRecord(2, 80, 10)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"economy\",\"sequence\":11,\"tick\":5,\"factions\":[{\"faction\":1,\"minerals\":150,\"gas\":25},{\"faction\":2,\"minerals\":80,\"gas\":10}]}",
            json
        )
    }

    @Test
    fun `renders combat stream record json`() {
        val json =
            renderCombatStreamRecordJson(
                sequence = 12L,
                tick = 6,
                attacks = 2,
                kills = 1,
                events =
                    listOf(
                        CombatEventRecord(attackerId = 3, targetId = 8, damage = 6, targetHp = 12, killed = false),
                        CombatEventRecord(attackerId = 4, targetId = 9, damage = 9, targetHp = -1, killed = true)
                    ),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"combat\",\"sequence\":12,\"tick\":6,\"attacks\":2,\"kills\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
            json
        )
    }

    @Test
    fun `renders despawn stream record json`() {
        val json =
            renderDespawnStreamRecordJson(
                sequence = 13L,
                tick = 7,
                entities =
                    listOf(
                        DespawnEventRecord(entityId = 9, faction = 2, typeId = "Zergling", reason = "death"),
                        DespawnEventRecord(entityId = 14, faction = 1, typeId = "Marine", reason = "despawn")
                    ),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"despawn\",\"sequence\":13,\"tick\":7,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"},{\"entityId\":14,\"faction\":1,\"typeId\":\"Marine\",\"reason\":\"despawn\"}]}",
            json
        )
    }

    @Test
    fun `renders tick summary stream record json`() {
        val json =
            renderTickSummaryStreamRecordJson(
                sequence = 13L,
                tick = 8,
                aliveTotal = 7,
                visibleTilesFaction1 = 20,
                visibleTilesFaction2 = 18,
                mineralsFaction1 = 150,
                mineralsFaction2 = 80,
                gasFaction1 = 25,
                gasFaction2 = 10,
                pathRequests = 3,
                pathSolved = 2,
                pathQueueSize = 6,
                avgPathLength = 5.5f,
                replans = 2,
                replansBlocked = 1,
                replansStuck = 1,
                attacks = 4,
                kills = 1,
                despawns = 1,
                builds = 1,
                buildFailures = 0,
                buildFailureReasons = BuildFailureCounts(0, 0, 0, 0),
                trainsQueued = 2,
                trainsCompleted = 1,
                trainsCancelled = 0,
                trainFailures = 1,
                trainFailureReasons = TrainFailureCounts(0, 0, 0, 0, 0, 1, 0),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"tickSummary\",\"sequence\":13,\"tick\":8,\"aliveTotal\":7,\"visibleTilesFaction1\":20,\"visibleTilesFaction2\":18,\"mineralsFaction1\":150,\"mineralsFaction2\":80,\"gasFaction1\":25,\"gasFaction2\":10,\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":6,\"avgPathLength\":5.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1,\"attacks\":4,\"kills\":1,\"despawns\":1,\"builds\":1,\"buildFailures\":0,\"buildFailureReasons\":{\"invalidDefinition\":0,\"invalidFootprint\":0,\"invalidPlacement\":0,\"insufficientResources\":0},\"trainsQueued\":2,\"trainsCompleted\":1,\"trainsCancelled\":0,\"trainFailures\":1,\"trainFailureReasons\":{\"missingBuilding\":0,\"invalidUnit\":0,\"invalidBuildTime\":0,\"incompatibleProducer\":0,\"insufficientResources\":0,\"queueFull\":1,\"nothingToCancel\":0}}",
            json
        )
    }

    @Test
    fun `renders spawn stream record json`() {
        val json =
            renderSpawnStreamRecordJson(
                sequence = 14L,
                tick = 9,
                entityId = 21,
                faction = 1,
                typeId = "Marine",
                x = 3f,
                y = 4f,
                vision = 7f,
                label = "alpha",
                labelId = -1,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"spawn\",\"sequence\":14,\"tick\":9,\"entityId\":21,\"faction\":1,\"typeId\":\"Marine\",\"x\":3.0,\"y\":4.0,\"vision\":7.0,\"label\":\"alpha\",\"labelId\":-1}",
            json
        )
    }

    @Test
    fun `renders selection stream record json`() {
        val json =
            renderSelectionStreamRecordJson(
                sequence = 15L,
                tick = 10,
                selectionType = "faction",
                faction = 2,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"selection\",\"sequence\":15,\"tick\":10,\"selectionType\":\"faction\",\"units\":[],\"faction\":2,\"typeId\":null}",
            json
        )
    }

    @Test
    fun `renders order applied stream record json`() {
        val json =
            renderOrderAppliedStreamRecordJson(
                sequence = 16L,
                tick = 11,
                orderType = "attack",
                units = intArrayOf(3, 4, 5),
                target = 9,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"orderApplied\",\"sequence\":16,\"tick\":11,\"orderType\":\"attack\",\"units\":[3,4,5],\"target\":9,\"x\":null,\"y\":null}",
            json
        )
    }

    @Test
    fun `renders rally stream record json`() {
        val json =
            renderRallyStreamRecordJson(
                sequence = 17L,
                tick = 12,
                buildingId = 41,
                x = 15f,
                y = 16f,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"rally\",\"sequence\":17,\"tick\":12,\"buildingId\":41,\"x\":15.0,\"y\":16.0}",
            json
        )
    }

    @Test
    fun `renders rally failure stream record json`() {
        val json =
            renderRallyFailureStreamRecordJson(
                sequence = 18L,
                tick = 12,
                buildingId = 41,
                reason = "unsupportedRally",
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"rallyFailure\",\"sequence\":18,\"tick\":12,\"buildingId\":41,\"reason\":\"unsupportedRally\"}",
            json
        )
    }

    @Test
    fun `renders producer failure stream record json`() {
        val json =
            renderProducerFailureStreamRecordJson(
                sequence = 19L,
                tick = 13,
                buildingId = 41,
                producerTypeId = "Depot",
                typeId = "Marine",
                reason = "incompatibleProducer",
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"producerFailure\",\"sequence\":19,\"tick\":13,\"buildingId\":41,\"producerTypeId\":\"Depot\",\"typeId\":\"Marine\",\"reason\":\"incompatibleProducer\"}",
            json
        )
    }

    @Test
    fun `renders build failure stream record json`() {
        val json =
            renderBuildFailureStreamRecordJson(
                sequence = 20L,
                tick = 13,
                faction = 1,
                typeId = "Depot",
                tileX = 9,
                tileY = 10,
                reason = "invalidPlacement",
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"buildFailure\",\"sequence\":20,\"tick\":13,\"faction\":1,\"typeId\":\"Depot\",\"tileX\":9,\"tileY\":10,\"reason\":\"invalidPlacement\"}",
            json
        )
    }

    @Test
    fun `renders train failure stream record json`() {
        val json =
            renderTrainFailureStreamRecordJson(
                sequence = 21L,
                tick = 14,
                buildingId = 41,
                producerTypeId = "Depot",
                typeId = "Marine",
                reason = "queueFull",
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"trainFailure\",\"sequence\":21,\"tick\":14,\"buildingId\":41,\"producerTypeId\":\"Depot\",\"typeId\":\"Marine\",\"reason\":\"queueFull\"}",
            json
        )
    }

    @Test
    fun `renders order queue stream record json`() {
        val json =
            renderOrderQueueStreamRecordJson(
                sequence = 18L,
                tick = 13,
                orderType = "move",
                entities = listOf(OrderQueueEntityRecord(3, 2), OrderQueueEntityRecord(4, 1)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"orderQueue\",\"sequence\":18,\"tick\":13,\"orderType\":\"move\",\"entities\":[{\"entityId\":3,\"queueSize\":2},{\"entityId\":4,\"queueSize\":1}]}",
            json
        )
    }

    @Test
    fun `renders path assigned stream record json`() {
        val json =
            renderPathAssignedStreamRecordJson(
                sequence = 18L,
                tick = 13,
                entities = listOf(PathAssignedEventRecord(7, 9, 28, 28), PathAssignedEventRecord(8, 6, 12, 10)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"pathAssigned\",\"sequence\":18,\"tick\":13,\"entities\":[{\"entityId\":7,\"pathLength\":9,\"goalX\":28,\"goalY\":28},{\"entityId\":8,\"pathLength\":6,\"goalX\":12,\"goalY\":10}]}",
            json
        )
    }

    @Test
    fun `renders path progress stream record json`() {
        val json =
            renderPathProgressStreamRecordJson(
                sequence = 19L,
                tick = 14,
                entities = listOf(PathProgressEventRecord(7, 3, 5, false), PathProgressEventRecord(8, 6, 0, true)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"pathProgress\",\"sequence\":19,\"tick\":14,\"entities\":[{\"entityId\":7,\"waypointIndex\":3,\"remainingNodes\":5,\"completed\":false},{\"entityId\":8,\"waypointIndex\":6,\"remainingNodes\":0,\"completed\":true}]}",
            json
        )
    }

    @Test
    fun `renders occupancy change stream record json`() {
        val json =
            renderOccupancyChangeStreamRecordJson(
                sequence = 20L,
                tick = 15,
                changes = listOf(OccupancyChangeEventRecord(14, 10, true), OccupancyChangeEventRecord(20, 10, false)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"occupancy\",\"sequence\":20,\"tick\":15,\"changes\":[{\"x\":14,\"y\":10,\"blocked\":true},{\"x\":20,\"y\":10,\"blocked\":false}]}",
            json
        )
    }

    @Test
    fun `renders map state stream record json`() {
        val json =
            renderMapStateStreamRecordJson(
                sequence = 21L,
                width = 32,
                height = 32,
                blockedTiles = listOf(MapBlockedTileRecord(6, 14), MapBlockedTileRecord(12, 6)),
                weightedTiles = listOf(MapCostTileRecord(18, 18, 3f)),
                staticOccupancyTiles = emptyList(),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"mapState\",\"sequence\":21,\"width\":32,\"height\":32,\"blockedTiles\":[{\"x\":6,\"y\":14},{\"x\":12,\"y\":6}],\"weightedTiles\":[{\"x\":18,\"y\":18,\"cost\":3.0}],\"staticOccupancyTiles\":[]}",
            json
        )
    }

    @Test
    fun `renders vision stream record json`() {
        val json =
            renderVisionStreamRecordJson(
                sequence = 22L,
                tick = 16,
                changes = listOf(VisionChangeEventRecord(1, 8, 8, true), VisionChangeEventRecord(2, 12, 12, false)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"vision\",\"sequence\":22,\"tick\":16,\"changes\":[{\"faction\":1,\"x\":8,\"y\":8,\"visible\":true},{\"faction\":2,\"x\":12,\"y\":12,\"visible\":false}]}",
            json
        )
    }

    @Test
    fun `renders damage stream record json`() {
        val json =
            renderDamageStreamRecordJson(
                sequence = 23L,
                tick = 17,
                events = listOf(DamageEventRecord(3, 8, 6, 12, false), DamageEventRecord(4, 9, 9, -1, true)),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"damage\",\"sequence\":23,\"tick\":17,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
            json
        )
    }

    @Test
    fun `renders session stats stream record json`() {
        val json =
            renderSessionStatsStreamRecordJson(
                sequence = 24L,
                ticks = 1500,
                pathRequests = 120,
                pathSolved = 110,
                replans = 30,
                replansBlocked = 12,
                replansStuck = 5,
                attacks = 80,
                kills = 14,
                despawns = 14,
                builds = 3,
                buildFailures = 1,
                buildFailureReasons = BuildFailureCounts(0, 0, 1, 0),
                trainsQueued = 8,
                trainsCompleted = 6,
                trainsCancelled = 1,
                trainFailures = 2,
                trainFailureReasons = TrainFailureCounts(0, 0, 0, 1, 0, 1, 0),
                finalVisibleTilesFaction1 = 220,
                finalVisibleTilesFaction2 = 198,
                finalMineralsFaction1 = 350,
                finalMineralsFaction2 = 500,
                finalGasFaction1 = 25,
                finalGasFaction2 = 10,
                finalWorldHash = 123456789L,
                finalReplayHash = 987654321L,
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"sessionStats\",\"sequence\":24,\"ticks\":1500,\"pathRequests\":120,\"pathSolved\":110,\"replans\":30,\"replansBlocked\":12,\"replansStuck\":5,\"attacks\":80,\"kills\":14,\"despawns\":14,\"builds\":3,\"buildFailures\":1,\"buildFailureReasons\":{\"invalidDefinition\":0,\"invalidFootprint\":0,\"invalidPlacement\":1,\"insufficientResources\":0},\"trainsQueued\":8,\"trainsCompleted\":6,\"trainsCancelled\":1,\"trainFailures\":2,\"trainFailureReasons\":{\"missingBuilding\":0,\"invalidUnit\":0,\"invalidBuildTime\":0,\"incompatibleProducer\":1,\"insufficientResources\":0,\"queueFull\":1,\"nothingToCancel\":0},\"finalVisibleTilesFaction1\":220,\"finalVisibleTilesFaction2\":198,\"finalMineralsFaction1\":350,\"finalMineralsFaction2\":500,\"finalGasFaction1\":25,\"finalGasFaction2\":10,\"finalWorldHash\":123456789,\"finalReplayHash\":987654321}",
            json
        )
    }

    @Test
    fun `renders production stream record json`() {
        val json =
            renderProductionStreamRecordJson(
                sequence = 25L,
                tick = 18,
                events =
                    listOf(
                        ProductionEventRecord("enqueue", 41, "Marine", 75, null),
                        ProductionEventRecord("complete", 41, "Marine", 0, 88)
                    ),
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"production\",\"sequence\":25,\"tick\":18,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":41,\"typeId\":\"Marine\",\"remainingTicks\":75,\"spawnedEntityId\":null},{\"kind\":\"complete\",\"buildingId\":41,\"typeId\":\"Marine\",\"remainingTicks\":0,\"spawnedEntityId\":88}]}",
            json
        )
    }

    @Test
    fun `renders command failure stream record json`() {
        val json =
            renderCommandFailureStreamRecordJson(
                sequence = 26L,
                tick = 19,
                commandType = "train",
                reason = "queueFull",
                buildingId = 41,
                typeId = "Marine",
                pretty = false
            )

        assertEquals(
            "{\"recordType\":\"commandFailure\",\"sequence\":26,\"tick\":19,\"commandType\":\"train\",\"reason\":\"queueFull\",\"faction\":null,\"buildingId\":41,\"typeId\":\"Marine\",\"tileX\":null,\"tileY\":null}",
            json
        )
    }
}
