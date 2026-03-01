package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayMetadata

class AppTest {
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
    fun `builds selector split command totals`() {
        val commandsByTick =
            arrayOf(
                arrayListOf<Command>(
                    Command.Move(0, intArrayOf(1, 2), 4f, 5f),
                    Command.AttackFaction(0, 1, 9)
                ),
                arrayListOf<Command>(
                    Command.MoveType(1, "Marine", 7f, 8f),
                    Command.Attack(1, intArrayOf(3), 10),
                    Command.Spawn(1, 1, "Marine", 2f, 3f)
                )
            )

        val stats = buildCommandStats(commandsByTick, replayMeta = null)

        assertEquals(5, stats.totals.total)
        assertEquals(1, stats.totals.spawns)
        assertEquals(2, stats.totals.moves)
        assertEquals(2, stats.totals.attacks)
        assertEquals(2, stats.totals.selectors.direct)
        assertEquals(1, stats.totals.selectors.faction)
        assertEquals(1, stats.totals.selectors.type)
        assertEquals(1, stats.totals.breakdown.move.direct)
        assertEquals(0, stats.totals.breakdown.move.faction)
        assertEquals(1, stats.totals.breakdown.move.type)
        assertEquals(1, stats.totals.breakdown.attack.direct)
        assertEquals(1, stats.totals.breakdown.attack.faction)
        assertEquals(0, stats.totals.breakdown.attack.type)
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
        assertEquals(emptyList<String>(), report.warnings)
    }
}
