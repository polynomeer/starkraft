package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import starkraft.sim.replay.ReplayMetadata

class AppTest {
    @Test
    fun `warns on replay build version mismatch`() {
        val warning =
            replayCompatibilityWarning(
                ReplayMetadata(
                    schema = 1,
                    replayHash = 10L,
                    seed = 5L,
                    mapId = "demo",
                    buildVersion = "0.9.0",
                    legacy = false
                )
            )

        assertEquals("replay warning: buildVersion=0.9.0 current=1.0-SNAPSHOT", warning)
    }

    @Test
    fun `skips warning for matching or legacy replay metadata`() {
        assertNull(
            replayCompatibilityWarning(
                ReplayMetadata(
                    schema = 1,
                    replayHash = 10L,
                    seed = 5L,
                    mapId = "demo",
                    buildVersion = "1.0-SNAPSHOT",
                    legacy = false
                )
            )
        )
        assertNull(
            replayCompatibilityWarning(
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
}
