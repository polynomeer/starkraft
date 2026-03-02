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
                    "{\"recordType\":\"mapState\",\"sequence\":1,\"width\":32,\"height\":32,\"blockedTiles\":[],\"weightedTiles\":[],\"staticOccupancyTiles\":[]}",
                    "{\"recordType\":\"command\",\"sequence\":2,\"tick\":0,\"commandType\":\"move\",\"units\":[1],\"faction\":null,\"typeId\":null,\"target\":null,\"x\":2.0,\"y\":3.0,\"vision\":null,\"label\":null,\"labelId\":null}",
                    "{\"recordType\":\"resourceDelta\",\"sequence\":3,\"tick\":0,\"events\":[{\"faction\":1,\"kind\":\"spend\",\"minerals\":50,\"gas\":0},{\"faction\":1,\"kind\":\"refund\",\"minerals\":10,\"gas\":0}]}",
                    "{\"recordType\":\"resourceDeltaSummary\",\"sequence\":4,\"tick\":0,\"factions\":[{\"faction\":1,\"mineralsSpent\":50,\"gasSpent\":0,\"mineralsRefunded\":10,\"gasRefunded\":0},{\"faction\":2,\"mineralsSpent\":0,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0}]}",
                    "{\"recordType\":\"producerState\",\"sequence\":5,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"supportsTraining\":true,\"supportsRally\":true,\"productionQueueLimit\":3,\"defaultRallyOffsetX\":4.0,\"defaultRallyOffsetY\":0.0},{\"entityId\":2,\"faction\":2,\"typeId\":\"Tower\",\"supportsTraining\":false,\"supportsRally\":false,\"productionQueueLimit\":0,\"defaultRallyOffsetX\":0.0,\"defaultRallyOffsetY\":0.0}]}",
                    "{\"recordType\":\"production\",\"sequence\":6,\"tick\":0,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":75,\"spawnedEntityId\":null},{\"kind\":\"progress\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":74,\"spawnedEntityId\":null},{\"kind\":\"complete\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":0,\"spawnedEntityId\":9},{\"kind\":\"cancel\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":40,\"spawnedEntityId\":null}]}",
                    "{\"recordType\":\"combat\",\"sequence\":7,\"tick\":1,\"attacks\":2,\"kills\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                    "{\"recordType\":\"damage\",\"sequence\":8,\"tick\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                    "{\"recordType\":\"despawn\",\"sequence\":9,\"tick\":1,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"},{\"entityId\":14,\"faction\":1,\"typeId\":\"Marine\",\"reason\":\"despawn\"}]}",
                    "{\"recordType\":\"sessionStats\",\"sequence\":10,\"ticks\":10,\"pathRequests\":4,\"pathSolved\":4,\"replans\":1,\"replansBlocked\":1,\"replansStuck\":0,\"attacks\":2,\"kills\":1,\"despawns\":1,\"finalVisibleTilesFaction1\":12,\"finalVisibleTilesFaction2\":10,\"finalWorldHash\":123,\"finalReplayHash\":456}",
                    "{\"recordType\":\"sessionEnd\",\"sequence\":11,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
                )
            )

        assertEquals(12, summary.totalRecords)
        assertEquals(1, summary.countsByType["sessionStart"])
        assertEquals(1, summary.countsByType["mapState"])
        assertEquals(1, summary.countsByType["command"])
        assertEquals(1, summary.countsByType["resourceDelta"])
        assertEquals(1, summary.countsByType["resourceDeltaSummary"])
        assertEquals(1, summary.countsByType["producerState"])
        assertEquals(1, summary.countsByType["production"])
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
        assertEquals(2, summary.resourceDeltaEventCount)
        assertEquals(50, summary.resourceSpendMinerals)
        assertEquals(10, summary.resourceRefundMinerals)
        assertEquals(50, summary.resourceSummaryMineralsSpentFaction1)
        assertEquals(10, summary.resourceSummaryMineralsRefundedFaction1)
        assertEquals(2, summary.producerCount)
        assertEquals(1, summary.trainingProducerCount)
        assertEquals(1, summary.rallyProducerCount)
        assertEquals(3, summary.maxProducerQueueLimit)
        assertEquals(1, summary.productionEnqueueCount)
        assertEquals(1, summary.productionProgressCount)
        assertEquals(1, summary.productionCompleteCount)
        assertEquals(1, summary.productionCancelCount)
        assertEquals(2, summary.combatAttackCount)
        assertEquals(1, summary.combatKillCount)
        assertEquals(2, summary.combatDamageEventCount)
        assertEquals(15, summary.combatTotalDamage)
        assertEquals(1, summary.combatDeathDespawnCount)
    }

    @Test
    fun `renders snapshot stream summary text`() {
        val text =
            renderSnapshotStreamSummary(
                Paths.get("stream.ndjson"),
                summarizeSnapshotStream(
                    sequenceOf(
                        "{\"recordType\":\"sessionStart\",\"sequence\":0,\"mapId\":\"demo-map\",\"buildVersion\":\"test-build\",\"seed\":7}",
                        "{\"recordType\":\"resourceDelta\",\"sequence\":1,\"tick\":0,\"events\":[{\"faction\":1,\"kind\":\"spend\",\"minerals\":50,\"gas\":0}]}",
                        "{\"recordType\":\"resourceDeltaSummary\",\"sequence\":2,\"tick\":0,\"factions\":[{\"faction\":1,\"mineralsSpent\":50,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0},{\"faction\":2,\"mineralsSpent\":0,\"gasSpent\":0,\"mineralsRefunded\":0,\"gasRefunded\":0}]}",
                        "{\"recordType\":\"producerState\",\"sequence\":3,\"tick\":0,\"entities\":[{\"entityId\":1,\"faction\":1,\"typeId\":\"Depot\",\"supportsTraining\":true,\"supportsRally\":true,\"productionQueueLimit\":3,\"defaultRallyOffsetX\":4.0,\"defaultRallyOffsetY\":0.0}]}",
                        "{\"recordType\":\"production\",\"sequence\":4,\"tick\":0,\"events\":[{\"kind\":\"enqueue\",\"buildingId\":1,\"typeId\":\"Marine\",\"remainingTicks\":75,\"spawnedEntityId\":null}]}",
                        "{\"recordType\":\"combat\",\"sequence\":5,\"tick\":1,\"attacks\":2,\"kills\":1,\"events\":[]}",
                        "{\"recordType\":\"damage\",\"sequence\":6,\"tick\":1,\"events\":[{\"attackerId\":3,\"targetId\":8,\"damage\":6,\"targetHp\":12,\"killed\":false},{\"attackerId\":4,\"targetId\":9,\"damage\":9,\"targetHp\":-1,\"killed\":true}]}",
                        "{\"recordType\":\"despawn\",\"sequence\":7,\"tick\":1,\"entities\":[{\"entityId\":9,\"faction\":2,\"typeId\":\"Zergling\",\"reason\":\"death\"}]}",
                        "{\"recordType\":\"sessionEnd\",\"sequence\":8,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
                    )
                )
            )

        assertTrue(text.contains("snapshot stream summary:"))
        assertTrue(text.contains("sessionStart=1"))
        assertTrue(text.contains("sessionEnd=1"))
        assertTrue(text.contains("worldHash=123"))
        assertTrue(text.contains("replayHash=456"))
        assertTrue(text.contains("economy: events=1 spend=50/0 refund=0/0"))
        assertTrue(text.contains("f1=50/0->0/0"))
        assertTrue(text.contains("producers: total=1 training=1 rally=1 maxQueue=3"))
        assertTrue(text.contains("prod=e1/p0/c0/x0"))
        assertTrue(text.contains("combat: attacks=2 kills=1 damageEvents=2 damage=15 deathDespawns=1"))
    }
}
