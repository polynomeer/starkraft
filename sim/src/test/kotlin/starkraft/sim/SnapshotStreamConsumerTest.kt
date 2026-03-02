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
                    "{\"recordType\":\"sessionStats\",\"sequence\":5,\"ticks\":10,\"pathRequests\":4,\"pathSolved\":4,\"replans\":1,\"replansBlocked\":1,\"replansStuck\":0,\"attacks\":2,\"kills\":1,\"despawns\":1,\"finalVisibleTilesFaction1\":12,\"finalVisibleTilesFaction2\":10,\"finalWorldHash\":123,\"finalReplayHash\":456}",
                    "{\"recordType\":\"sessionEnd\",\"sequence\":6,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
                )
            )

        assertEquals(7, summary.totalRecords)
        assertEquals(1, summary.countsByType["sessionStart"])
        assertEquals(1, summary.countsByType["mapState"])
        assertEquals(1, summary.countsByType["command"])
        assertEquals(1, summary.countsByType["resourceDelta"])
        assertEquals(1, summary.countsByType["resourceDeltaSummary"])
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
                        "{\"recordType\":\"sessionEnd\",\"sequence\":3,\"tick\":10,\"worldHash\":123,\"replayHash\":456}"
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
    }
}
