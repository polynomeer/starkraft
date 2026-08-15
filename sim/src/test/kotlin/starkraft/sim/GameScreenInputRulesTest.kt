package starkraft.sim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.badlogic.gdx.Input
import starkraft.sim.client.computeBottomHudLayout
import starkraft.sim.client.computeCenterPanelLayout
import starkraft.sim.client.computeCommandDeckLayout
import starkraft.sim.client.computeSelectionSlotVisualLayout
import starkraft.sim.client.computeTopBarLayout
import starkraft.sim.client.EscapeAction
import starkraft.sim.client.computeBottomHudHeight
import starkraft.sim.client.computeWorldViewportHeightForLayout
import starkraft.sim.client.gdxMiniMapBounds
import starkraft.sim.client.overlayBlocksWorldInput
import starkraft.sim.client.resolveEscapeAction
import starkraft.sim.client.shouldAbortPointerGesture
import starkraft.sim.client.shouldDispatchCommandUiAction
import starkraft.sim.client.shouldHandleHotkeyWhileOverlayVisible
import starkraft.sim.client.shouldIssueSelectionBox

class GameScreenInputRulesTest {
    @Test
    fun `bottom hud layout scales down at supported resolutions`() {
        val compact = computeBottomHudLayout(1280, 720)
        val wide = computeBottomHudLayout(1920, 1080)

        assertTrue(compact.centerWidth < 266, "1280 layout should shrink center panel")
        assertTrue(compact.commandWidth < 278, "1280 layout should shrink command panel")
        assertTrue(compact.leftSlotWidth <= 228, "1280 layout should keep minimap lane compact")
        assertTrue(wide.centerWidth >= compact.centerWidth, "wider layout should not shrink center panel further")
        assertTrue(wide.commandWidth >= compact.commandWidth, "wider layout should not shrink command panel further")
    }

    @Test
    fun `command deck compacts on smaller resolutions`() {
        val compact = computeCommandDeckLayout(1280, 720)
        val wide = computeCommandDeckLayout(1920, 1080)

        assertTrue(compact.scrollHeight < wide.scrollHeight, "small screens should use shorter command deck")
        assertTrue(compact.buttonHeight < wide.buttonHeight, "small screens should use shorter command buttons")
        assertTrue(compact.actorInset < wide.actorInset, "small screens should reduce command button chrome")
    }

    @Test
    fun `center panel compacts on smaller resolutions`() {
        val compact = computeCenterPanelLayout(1280)
        val wide = computeCenterPanelLayout(1920)

        assertTrue(compact.portraitSize < wide.portraitSize, "small screens should use smaller portrait")
        assertTrue(compact.rosterSlotSize < wide.rosterSlotSize, "small screens should use smaller roster slots")
        assertTrue(compact.pagerButtonSize < wide.pagerButtonSize, "small screens should use smaller pager buttons")
        assertTrue(compact.healthBarWidth < wide.healthBarWidth, "small screens should use narrower health bars")
    }

    @Test
    fun `top bar compacts on smaller resolutions`() {
        val compact = computeTopBarLayout(1280)
        val wide = computeTopBarLayout(1920)

        assertTrue(compact.selectionWidth < wide.selectionWidth, "small screens should use smaller selection card")
        assertTrue(compact.modeWidth < wide.modeWidth, "small screens should use smaller mode card")
        assertTrue(compact.statusWidth < wide.statusWidth, "small screens should use smaller status card")
    }

    @Test
    fun `selection slot visuals compact with smaller slot size`() {
        val compact = computeSelectionSlotVisualLayout(34)
        val wide = computeSelectionSlotVisualLayout(40)

        assertTrue(compact.topBarHeight < wide.topBarHeight, "compact slots should use thinner chrome bars")
        assertTrue(compact.titleHeight < wide.titleHeight, "compact slots should use shorter title rows")
        assertTrue(compact.hpBarWidth < wide.hpBarWidth, "compact slots should use shorter hp bars")
        assertTrue(compact.glyphScale < wide.glyphScale, "compact slots should use smaller glyphs")
        assertTrue(compact.markerSize < wide.markerSize, "compact slots should use smaller markers")
    }

    @Test
    fun `world viewport stays above bottom hud and minimap at supported resolutions`() {
        listOf(1280 to 720, 1440 to 900, 1600 to 900, 1920 to 1080).forEach { (width, height) ->
            val viewportHeight = computeWorldViewportHeightForLayout(width, height)
            val hudTop = height - computeBottomHudHeight(width, height)
            val minimapTop = gdxMiniMapBounds(width, height).top.toInt()

            assertTrue(viewportHeight <= hudTop, "viewport should stop above hud at ${width}x$height")
            assertTrue(viewportHeight <= minimapTop, "viewport should stop above minimap at ${width}x$height")
            assertTrue(viewportHeight >= 240, "viewport should remain playable at ${width}x$height")
        }
    }

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
    fun `overlay only allows close hotkeys`() {
        assertTrue(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.ESCAPE, pauseVisible = true, helpVisible = false))
        assertFalse(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.M, pauseVisible = true, helpVisible = false))
        assertTrue(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.ESCAPE, pauseVisible = false, helpVisible = true))
        assertTrue(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.F1, pauseVisible = false, helpVisible = true))
        assertFalse(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.SPACE, pauseVisible = false, helpVisible = true))
        assertTrue(shouldHandleHotkeyWhileOverlayVisible(Input.Keys.M, pauseVisible = false, helpVisible = false))
    }

    @Test
    fun `overlay blocks command ui actions`() {
        assertFalse(shouldDispatchCommandUiAction(pauseVisible = true, helpVisible = false))
        assertFalse(shouldDispatchCommandUiAction(pauseVisible = false, helpVisible = true))
        assertTrue(shouldDispatchCommandUiAction(pauseVisible = false, helpVisible = false))
    }

    @Test
    fun `overlay aborts pointer gestures`() {
        assertTrue(shouldAbortPointerGesture(pauseVisible = true, helpVisible = false))
        assertTrue(shouldAbortPointerGesture(pauseVisible = false, helpVisible = true))
        assertFalse(shouldAbortPointerGesture(pauseVisible = false, helpVisible = false))
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
