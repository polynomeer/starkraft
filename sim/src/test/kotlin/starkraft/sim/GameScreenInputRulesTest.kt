package starkraft.sim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.EscapeAction
import starkraft.sim.client.overlayBlocksWorldInput
import starkraft.sim.client.resolveEscapeAction
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

    @Test
    fun `overlay visibility blocks world input`() {
        assertTrue(overlayBlocksWorldInput(pauseVisible = true, helpVisible = false))
        assertTrue(overlayBlocksWorldInput(pauseVisible = false, helpVisible = true))
        assertFalse(overlayBlocksWorldInput(pauseVisible = false, helpVisible = false))
    }

    @Test
    fun `escape prioritizes help then armed mode then selection then pause`() {
        assertEquals(
            EscapeAction.CLOSE_HELP,
            resolveEscapeAction(
                pauseVisible = false,
                helpVisible = true,
                hasSelection = true,
                hasArmedMode = true
            )
        )
        assertEquals(
            EscapeAction.CANCEL_ARMED_MODE,
            resolveEscapeAction(
                pauseVisible = false,
                helpVisible = false,
                hasSelection = true,
                hasArmedMode = true
            )
        )
        assertEquals(
            EscapeAction.CLEAR_SELECTION,
            resolveEscapeAction(
                pauseVisible = false,
                helpVisible = false,
                hasSelection = true,
                hasArmedMode = false
            )
        )
        assertEquals(
            EscapeAction.OPEN_PAUSE,
            resolveEscapeAction(
                pauseVisible = false,
                helpVisible = false,
                hasSelection = false,
                hasArmedMode = false
            )
        )
    }
}
