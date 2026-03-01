package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
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
}
