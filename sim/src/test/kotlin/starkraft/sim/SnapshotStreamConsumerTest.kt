package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.renderSnapshotStreamSummary
import starkraft.sim.client.summarizeSnapshotStream
import java.nio.file.Paths

class SnapshotStreamConsumerTest {
    @Test
    fun `summarizes snapshot stream records`() {
        val summary =
            summarizeSnapshotStream(
                sequenceOf(
                    "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
                    "{\"recordType\":\"mapState\",\"sequence\":1,\"width\":32,\"height\":32,\"blockedTiles\":[],\"weightedTiles\":[],\"staticOccupancyTiles\":[],\"resourceNodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"remaining\":250},{\"id\":10,\"kind\":\"MineralField\",\"x\":8.0,\"y\":6.0,\"remaining\":2},{\"id\":11,\"kind\":\"GasGeyser\",\"x\":10.0,\"y\":6.0,\"remaining\":100}]}",
                    "{\"recordType\":\"resourceNode\",\"sequence\":2,\"tick\":0,\"nodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"harvested\":3,\"remaining\":247,\"depleted\":false},{\"id\":10,\"kind\":\"MineralField\",\"x\":8.0,\"y\":6.0,\"harvested\":2,\"remaining\":0,\"depleted\":true}]}",
                    "{\"recordType\":\"selection\",\"sequence\":3,\"tick\":0,\"selectionType\":\"archetype\",\"units\":[],\"faction\":null,\"typeId\":null,\"archetype\":\"infantry\"}",
                    "{\"recordType\":\"command\",\"sequence\":4,\"tick\":0,\"commandType\":\"moveArchetype\",\"units\":[],\"faction\":null,\"typeId\":null,\"archetype\":\"infantry\",\"target\":null,\"x\":2.0,\"y\":3.0,\"vision\":null,\"label\":null,\"labelId\":null}",
                    "{\"recordType\":\"resourceDelta\",\"sequence\":5,\"tick\":0,\"events\":[{\"faction\":1,\"kind\":\"spend\",\"minerals\":50,\"gas\":0},{\"faction\":1,\"kind\":\"refund\",\"minerals\":10,\"gas\":0}]}",
                    "{\"recordType\":\"resourceDeltaSummary\",\"sequence\":6,\"tick\":0,\"factions\":[{\"faction\":1,\"mineralsSpent\":50,\"gasSpent\":0,\"mineralsRefunded\":10,\"gasRefunded\":0},{\"faction\":2,\"mineralsSpent\":0,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0}]}",
                    "{\"recordType\":\"producerState\",\"sequence\":7,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"archetype\":\"producer\",\"supportsTraining\":true,\"supportsRally\":true,\"supportsDropoff\":true,\"productionQueueLimit\":3,\"defaultRallyOffsetX\":4.0,\"defaultRallyOffsetY\":0.0},{\"entityId\":2,\"faction\":2,\"typeId\":\"Tower\",\"archetype\":\"defense\",\"supportsTraining\":false,\"supportsRally\":false,\"supportsDropoff\":false,\"productionQueueLimit\":0,\"defaultRallyOffsetX\":0.0,\"defaultRallyOffsetY\":0.0}]}",
                    "{\"recordType\":\"dropoffState\",\"sequence\":8,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"archetype\":\"producer\",\"x\":25.0,\"y\":5.0},{\"entityId\":12,\"faction\":2,\"typeId\":\"ResourceDepot\",\"archetype\":\"econDepot\",\"x\":7.0,\"y\":6.0}]}",
                    "{\"recordType\":\"harvesterState\",\"sequence\":9,\"tick\":0,\"entities\":[{\"entityId\":13,\"faction\":1,\"typeId\":\"Worker\",\"phase\":\"return\",\"targetNodeId\":9,\"cargoKind\":\"MineralField\",\"cargoAmount\":2,\"returnTargetId\":1},{\"entityId\":14,\"faction\":1,\"typeId\":\"Worker\",\"phase\":\"gather\",\"targetNodeId\":11,\"cargoKind\":null,\"cargoAmount\":0,\"returnTargetId\":null}]}",
                    "{\"recordType\":\"harvestCycle\",\"sequence\":10,\"tick\":0,\"events\":[{\"kind\":\"pickup\",\"workerId\":13,\"nodeId\":9,\"dropoffId\":1,\"resourceKind\":\"minerals\",\"amount\":2},{\"kind\":\"deposit\",\"workerId\":13,\"nodeId\":9,\"dropoffId\":1,\"resourceKind\":\"minerals\",\"amount\":2}]}",
                    "{\"recordType\":\"production\",\"sequence\":11,\"tick\":0,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":75,\"spawnedEntityId\":null},{\"kind\":\"progress\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":74,\"spawnedEntityId\":null},{\"kind\":\"complete\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":0,\"spawnedEntityId\":9},{\"kind\":\"cancel\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":40,\"spawnedEntityId\":null}]}",
                    "{\"recordType\":\"metrics\",\"sequence\":12,\"tick\":1,\"factions\":[{\"faction\":1,\"alive\":5,\"visibleTiles\":20,\"minerals\":150,\"gas\":25},{\"faction\":2,\"alive\":4,\"visibleTiles\":18,\"minerals\":80,\"gas\":10}],\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":7,\"avgPathLength\":6.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1}",
                    "{\"recordType\":\"pathAssigned\",\"sequence\":13,\"tick\":1,\"entities\":[{\"entityId\":7,\"pathLength\":9,\"goalX\":28,\"goalY\":28},{\"entityId\":8,\"pathLength\":6,\"goalX\":12,\"goalY\":10}]}",
                    "{\"recordType\":\"pathProgress\",\"sequence\":14,\"tick\":1,\"entities\":[{\"entityId\":7,\"waypointIndex\":3,\"remainingNodes\":5,\"completed\":false},{\"entityId\":8,\"waypointIndex\":6,\"remainingNodes\":0,\"completed\":true}]}",
                    "{\"recordType\":\"vision\",\"sequence\":15,\"tick\":1,\"changes\":[{\"faction\":1,\"x\":8,\"y\":8,\"visible\":true},{\"faction\":2,\"x\":12,\"y\":12,\"visible\":false}]}",
                    "{\"recordType\":\"combat\",\"sequence\":16,\"tick\":1,\"attacks\":2,\"kills\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                    "{\"recordType\":\"damage\",\"sequence\":17,\"tick\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                    "{\"recordType\":\"despawn\",\"sequence\":18,\"tick\":1,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"},{\"entityId\":14,\"faction\":1,\"typeId\":\"Marine\",\"reason\":\"despawn\"}]}",
                    "{\"recordType\":\"sessionStats\",\"sequence\":19,\"ticks\":10,\"pathRequests\":4,\"pathSolved\":4,\"replans\":1,\"replansBlocked\":1,\"replansStuck\":0,\"attacks\":2,\"kills\":1,\"despawns\":1,\"harvestedMineralsFaction1\":4,\"harvestedMineralsFaction2\":1,\"harvestedGasFaction1\":0,\"harvestedGasFaction2\":2,\"finalVisibleTilesFaction1\":12,\"finalVisibleTilesFaction2\":10,\"finalWorldHash\":123,\"finalReplayHash\":456}",
                    "{\"recordType\":\"sessionEnd\",\"sequence\":20,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
                )
            )

        assertEquals(21, summary.totalRecords)
        assertEquals(1, summary.countsByType["sessionStart"])
        assertEquals(1, summary.countsByType["mapState"])
        assertEquals(1, summary.countsByType["resourceNode"])
        assertEquals(1, summary.countsByType["selection"])
        assertEquals(1, summary.countsByType["command"])
        assertEquals(1, summary.countsByType["resourceDelta"])
        assertEquals(1, summary.countsByType["resourceDeltaSummary"])
        assertEquals(1, summary.countsByType["producerState"])
        assertEquals(1, summary.countsByType["dropoffState"])
        assertEquals(1, summary.countsByType["harvesterState"])
        assertEquals(1, summary.countsByType["harvestCycle"])
        assertEquals(1, summary.countsByType["production"])
        assertEquals(1, summary.countsByType["metrics"])
        assertEquals(1, summary.countsByType["pathAssigned"])
        assertEquals(1, summary.countsByType["pathProgress"])
        assertEquals(1, summary.countsByType["vision"])
        assertEquals(1, summary.countsByType["combat"])
        assertEquals(1, summary.countsByType["damage"])
        assertEquals(1, summary.countsByType["despawn"])
        assertEquals(1, summary.countsByType["sessionStats"])
        assertEquals(1, summary.countsByType["sessionEnd"])
        assertEquals("demo-map", summary.mapId)
        assertEquals("test-build", summary.buildVersion)
        assertEquals(7L, summary.seed)
        assertEquals(123L, summary.worldHash)
        assertEquals(456L, summary.replayHash)
        assertEquals(2, summary.resourceNodeChangeCount)
        assertEquals(5, summary.resourceNodeHarvestedTotal)
        assertEquals(1, summary.resourceNodeDepletedCount)
        assertEquals(4, summary.resourceNodeHarvestedMineralsFaction1)
        assertEquals(1, summary.resourceNodeHarvestedMineralsFaction2)
        assertEquals(0, summary.resourceNodeHarvestedGasFaction1)
        assertEquals(2, summary.resourceNodeHarvestedGasFaction2)
        assertEquals(2, summary.currentResourceNodeCount)
        assertEquals(347, summary.currentResourceNodeRemainingTotal)
        assertEquals(2, summary.resourceDeltaEventCount)
        assertEquals(50, summary.resourceSpendMinerals)
        assertEquals(10, summary.resourceRefundMinerals)
        assertEquals(50, summary.resourceSummaryMineralsSpentFaction1)
        assertEquals(10, summary.resourceSummaryMineralsRefundedFaction1)
        assertEquals(2, summary.producerCount)
        assertEquals(1, summary.trainingProducerCount)
        assertEquals(1, summary.rallyProducerCount)
        assertEquals(1, summary.dropoffProducerCount)
        assertEquals(2, summary.dropoffStateCount)
        assertEquals(1, summary.dropoffStateFaction1Count)
        assertEquals(1, summary.dropoffStateFaction2Count)
        assertEquals(3, summary.maxProducerQueueLimit)
        assertEquals(2, summary.harvesterCount)
        assertEquals(1, summary.harvesterGatherCount)
        assertEquals(1, summary.harvesterReturnCount)
        assertEquals(2, summary.harvesterCargoTotal)
        assertEquals(1, summary.harvestCyclePickupCount)
        assertEquals(1, summary.harvestCycleDepositCount)
        assertEquals(2, summary.harvestCyclePickupAmount)
        assertEquals(2, summary.harvestCycleDepositAmount)
        assertEquals(1, summary.productionEnqueueCount)
        assertEquals(1, summary.productionProgressCount)
        assertEquals(1, summary.productionCompleteCount)
        assertEquals(1, summary.productionCancelCount)
        assertEquals(3, summary.pathRequestCount)
        assertEquals(2, summary.pathSolvedCount)
        assertEquals(2, summary.pathReplanCount)
        assertEquals(2, summary.pathAssignedCount)
        assertEquals(2, summary.pathProgressCount)
        assertEquals(1, summary.pathCompletedCount)
        assertEquals(2, summary.visionChangeCount)
        assertEquals(1, summary.visionVisibleFaction1)
        assertEquals(0, summary.visionHiddenFaction1)
        assertEquals(0, summary.visionVisibleFaction2)
        assertEquals(1, summary.visionHiddenFaction2)
        assertEquals(12, summary.finalVisibleTilesFaction1)
        assertEquals(10, summary.finalVisibleTilesFaction2)
        assertEquals(2, summary.combatAttackCount)
        assertEquals(1, summary.combatKillCount)
        assertEquals(2, summary.combatDamageEventCount)
        assertEquals(15, summary.combatTotalDamage)
        assertEquals(1, summary.combatDeathDespawnCount)
        assertEquals(1, summary.archetypeSelectionCount)
        assertEquals(1, summary.archetypeMoveCommandCount)
        assertEquals(0, summary.archetypeAttackCommandCount)
        assertEquals(listOf("infantry"), summary.archetypesUsed)
    }

    @Test
    fun `renders snapshot stream summary text`() {
        val text =
            renderSnapshotStreamSummary(
                Paths.get("stream.ndjson"),
                summarizeSnapshotStream(
                    sequenceOf(
                        "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
                        "{\"recordType\":\"mapState\",\"sequence\":1,\"width\":32,\"height\":32,\"blockedTiles\":[],\"weightedTiles\":[],\"staticOccupancyTiles\":[],\"resourceNodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"remaining\":250},{\"id\":11,\"kind\":\"GasGeyser\",\"x\":10.0,\"y\":6.0,\"remaining\":100}]}",
                        "{\"recordType\":\"resourceNode\",\"sequence\":2,\"tick\":0,\"nodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"harvested\":3,\"remaining\":247,\"depleted\":false}]}",
                        "{\"recordType\":\"selection\",\"sequence\":3,\"tick\":0,\"selectionType\":\"archetype\",\"units\":[],\"faction\":null,\"typeId\":null,\"archetype\":\"infantry\"}",
                        "{\"recordType\":\"command\",\"sequence\":4,\"tick\":0,\"commandType\":\"moveArchetype\",\"units\":[],\"faction\":null,\"typeId\":null,\"archetype\":\"infantry\",\"target\":null,\"x\":4.0,\"y\":5.0,\"vision\":null,\"label\":null,\"labelId\":null}",
                        "{\"recordType\":\"resourceDelta\",\"sequence\":5,\"tick\":0,\"events\":[{\"faction\":1,\"kind\":\"spend\",\"minerals\":50,\"gas\":0}]}",
                        "{\"recordType\":\"resourceDeltaSummary\",\"sequence\":6,\"tick\":0,\"factions\":[{\"faction\":1,\"mineralsSpent\":50,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0},{\"faction\":2,\"mineralsSpent\":0,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0}]}",
                        "{\"recordType\":\"producerState\",\"sequence\":7,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"archetype\":\"producer\",\"supportsTraining\":true,\"supportsRally\":true,\"supportsDropoff\":true,\"productionQueueLimit\":3,\"defaultRallyOffsetX\":4.0,\"defaultRallyOffsetY\":0.0}]}",
                        "{\"recordType\":\"dropoffState\",\"sequence\":8,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"archetype\":\"producer\",\"x\":25.0,\"y\":5.0}]}",
                        "{\"recordType\":\"harvesterState\",\"sequence\":9,\"tick\":0,\"entities\":[{\"entityId\":13,\"faction\":1,\"typeId\":\"Worker\",\"phase\":\"return\",\"targetNodeId\":9,\"cargoKind\":\"MineralField\",\"cargoAmount\":3,\"returnTargetId\":1}]}",
                        "{\"recordType\":\"harvestCycle\",\"sequence\":10,\"tick\":0,\"events\":[{\"kind\":\"pickup\",\"workerId\":13,\"nodeId\":9,\"dropoffId\":1,\"resourceKind\":\"minerals\",\"amount\":3},{\"kind\":\"deposit\",\"workerId\":13,\"nodeId\":9,\"dropoffId\":1,\"resourceKind\":\"minerals\",\"amount\":3}]}",
                        "{\"recordType\":\"production\",\"sequence\":11,\"tick\":0,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":75,\"spawnedEntityId\":null}]}",
                        "{\"recordType\":\"metrics\",\"sequence\":12,\"tick\":1,\"factions\":[],\"pathRequests\":3,\"pathSolved\":2,\"pathQueueSize\":7,\"avgPathLength\":6.5,\"replans\":2,\"replansBlocked\":1,\"replansStuck\":1}",
                        "{\"recordType\":\"pathAssigned\",\"sequence\":13,\"tick\":1,\"entities\":[{\"entityId\":7,\"pathLength\":9,\"goalX\":28,\"goalY\":28}]}",
                        "{\"recordType\":\"pathProgress\",\"sequence\":14,\"tick\":1,\"entities\":[{\"entityId\":8,\"waypointIndex\":6,\"remainingNodes\":0,\"completed\":true}]}",
                        "{\"recordType\":\"vision\",\"sequence\":15,\"tick\":1,\"changes\":[{\"faction\":1,\"x\":8,\"y\":8,\"visible\":true},{\"faction\":2,\"x\":12,\"y\":12,\"visible\":false}]}",
                        "{\"recordType\":\"combat\",\"sequence\":16,\"tick\":1,\"attacks\":2,\"kills\":1,\"events\":[]}",
                        "{\"recordType\":\"damage\",\"sequence\":17,\"tick\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                        "{\"recordType\":\"despawn\",\"sequence\":18,\"tick\":1,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"}]}",
                        "{\"recordType\":\"sessionStats\",\"sequence\":19,\"ticks\":10,\"pathRequests\":4,\"pathSolved\":4,\"replans\":1,\"replansBlocked\":1,\"replansStuck\":0,\"attacks\":2,\"kills\":1,\"despawns\":1,\"harvestedMineralsFaction1\":3,\"harvestedMineralsFaction2\":0,\"harvestedGasFaction1\":0,\"harvestedGasFaction2\":0,\"finalVisibleTilesFaction1\":12,\"finalVisibleTilesFaction2\":10,\"finalWorldHash\":123,\"finalReplayHash\":456}",
                        "{\"recordType\":\"sessionEnd\",\"sequence\":20,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
                    )
                )
            )

        assertTrue(text.contains("snapshot stream summary:"))
        assertTrue(text.contains("sessionStart=1"))
        assertTrue(text.contains("sessionEnd=1"))
        assertTrue(text.contains("worldHash=123"))
        assertTrue(text.contains("replayHash=456"))
        assertTrue(text.contains("resourceNodes: changed=1 harvested=3 f1=3/0 f2=0/0 depleted=0 active=2 remaining=347"))
        assertTrue(text.contains("economy: events=1 spend=50/0 refund=0/0"))
        assertTrue(text.contains("f1=50/0->0/0"))
        assertTrue(text.contains("producers: total=1 training=1 rally=1 dropoff=1 maxQueue=3"))
        assertTrue(text.contains("dropoffs: total=1 f1=1 f2=0"))
        assertTrue(text.contains("harvesters: total=1 gather=0 return=1 cargo=3"))
        assertTrue(text.contains("harvestCycle: pickup=1/3 deposit=1/3"))
        assertTrue(text.contains("prod=e1/p0/c0/x0"))
        assertTrue(text.contains("combat: attacks=2 kills=1 damageEvents=2 damage=15 deathDespawns=1"))
        assertTrue(text.contains("pathing: req=3 solved=2 replans=2 assigned=1 progress=1 completed=1"))
        assertTrue(text.contains("vision: changes=2 f1=+1/-0 f2=+0/-1 final=12/10"))
        assertTrue(text.contains("archetypes: select=1 move=1 attack=0 ids=infantry"))
    }

    @Test
    fun `renders gas harvest split in resource summary`() {
        val text =
            renderSnapshotStreamSummary(
                Paths.get("gas-stream.ndjson"),
                summarizeSnapshotStream(
                    sequenceOf(
                        "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
                        "{\"recordType\":\"mapState\",\"sequence\":1,\"width\":32,\"height\":32,\"blockedTiles\":[],\"weightedTiles\":[],\"staticOccupancyTiles\":[],\"resourceNodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"remaining\":250},{\"id\":11,\"kind\":\"GasGeyser\",\"x\":10.0,\"y\":6.0,\"remaining\":120}]}",
                        "{\"recordType\":\"resourceNode\",\"sequence\":2,\"tick\":0,\"nodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"harvested\":2,\"remaining\":248,\"depleted\":false},{\"id\":11,\"kind\":\"GasGeyser\",\"x\":10.0,\"y\":6.0,\"harvested\":1,\"remaining\":119,\"depleted\":false}]}",
                        "{\"recordType\":\"sessionStats\",\"sequence\":3,\"ticks\":3,\"pathRequests\":0,\"pathSolved\":0,\"replans\":0,\"replansBlocked\":0,\"replansStuck\":0,\"attacks\":0,\"kills\":0,\"despawns\":0,\"harvestedMineralsFaction1\":2,\"harvestedMineralsFaction2\":0,\"harvestedGasFaction1\":0,\"harvestedGasFaction2\":1,\"finalVisibleTilesFaction1\":12,\"finalVisibleTilesFaction2\":10,\"finalWorldHash\":123,\"finalReplayHash\":456}",
                        "{\"recordType\":\"sessionEnd\",\"sequence\":4,\"tick\":3,\"worldHash\":123,\"replayHash\":456}"
                    )
                )
            )

        assertTrue(text.contains("resourceNodes: changed=2 harvested=3 f1=2/0 f2=0/1 depleted=0 active=2 remaining=367"))
    }

    @Test
    fun `removes depleted resource nodes from consumer state on despawn`() {
        val summary =
            summarizeSnapshotStream(
                sequenceOf(
                    "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
                    "{\"recordType\":\"mapState\",\"sequence\":1,\"width\":32,\"height\":32,\"blockedTiles\":[],\"weightedTiles\":[],\"staticOccupancyTiles\":[],\"resourceNodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"remaining\":1},{\"id\":11,\"kind\":\"GasGeyser\",\"x\":10.0,\"y\":6.0,\"remaining\":120}]}",
                    "{\"recordType\":\"resourceNode\",\"sequence\":2,\"tick\":0,\"nodes\":[{\"id\":9,\"kind\":\"MineralField\",\"x\":6.0,\"y\":6.0,\"harvested\":1,\"remaining\":0,\"depleted\":true}]}",
                    "{\"recordType\":\"despawn\",\"sequence\":3,\"tick\":0,\"entities\":[{\"entityId\":9,\"faction\":0,\"typeId\":\"MineralField\",\"reason\":\"resourceDepleted\"}]}"
                )
            )

        assertEquals(1, summary.currentResourceNodeCount)
        assertEquals(120, summary.currentResourceNodeRemainingTotal)
    }
}
