package starkraft.sim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.shouldIssueSelectionBox

class GameScreenInputRulesTest {
    @Test
    fun `drag issues selection box when command is not armed`() {
        assertTrue(
            shouldIssueSelectionBox(
                commandArmed = false,
                startX = 100f,
                startY = 100f,
                endX = 112f,
                endY = 100f
            )
        )
    }

    @Test
    fun `drag does not issue selection box when command is armed`() {
        assertFalse(
            shouldIssueSelectionBox(
                commandArmed = true,
                startX = 100f,
                startY = 100f,
                endX = 180f,
                endY = 180f
            )
        )
    }
}
