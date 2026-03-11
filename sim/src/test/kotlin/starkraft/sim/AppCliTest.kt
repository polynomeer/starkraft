package starkraft.sim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppCliTest {
    @Test
    fun `bootstrap orders are disabled when noBootstrapOrders flag is present`() {
        assertFalse(shouldIssueBootstrapOrders(arrayOf("--noBootstrapOrders"), replayPath = null))
    }

    @Test
    fun `bootstrap orders stay disabled for replays`() {
        assertFalse(shouldIssueBootstrapOrders(emptyArray(), replayPath = "/tmp/replay.json"))
    }

    @Test
    fun `bootstrap orders remain enabled for normal headless runs`() {
        assertTrue(shouldIssueBootstrapOrders(emptyArray(), replayPath = null))
    }

    @Test
    fun `interactive gdx requests replace queued orders`() {
        assertTrue(shouldReplaceInteractiveOrders("gdx-17"))
        assertFalse(shouldReplaceInteractiveOrders(null))
        assertFalse(shouldReplaceInteractiveOrders("script-17"))
    }

    @Test
    fun `default demo map uses large play dimensions`() {
        assertTrue(DEMO_MAP_WIDTH >= 96)
        assertTrue(DEMO_MAP_HEIGHT >= 96)
    }
}
