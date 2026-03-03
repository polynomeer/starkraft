package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.client.defaultClientInputPath
import java.nio.file.Paths

class GraphicalClientTest {
    @Test
    fun `default client input path is adjacent to snapshot file`() {
        val snapshotPath = Paths.get("/tmp/starkraft/live/snapshots.ndjson")

        assertEquals(
            Paths.get("/tmp/starkraft/live/client-input.ndjson"),
            defaultClientInputPath(snapshotPath)
        )
    }
}

