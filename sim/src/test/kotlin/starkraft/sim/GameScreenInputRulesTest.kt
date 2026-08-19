package starkraft.sim

import com.badlogic.gdx.graphics.Color
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
import starkraft.sim.client.buildHudFooterLine
import starkraft.sim.client.buildMinimapHintLine
import starkraft.sim.client.formatCommandButtonText
import starkraft.sim.client.formatCommandHeaderLine
import starkraft.sim.client.formatQueueStatusLine
import starkraft.sim.client.formatSelectionPageLabel
import starkraft.sim.client.formatControlGroupDeckLine
import starkraft.sim.client.gdxMiniMapBounds
import starkraft.sim.client.overlayBlocksWorldInput
import starkraft.sim.client.resolveEffectAccentColor
import starkraft.sim.client.resolveEffectDebrisColor
import starkraft.sim.client.resolveEffectSmokeColor
import starkraft.sim.client.resolveEscapeAction
import starkraft.sim.client.resolveCommandButtonHotkey
import starkraft.sim.client.resolveQueueHeaderLine
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
        assertTrue(compact.centerWidth >= compact.commandWidth, "1280 layout should prioritize center selection deck")
        assertTrue(wide.centerWidth >= compact.centerWidth, "wider layout should not shrink center panel further")
        assertTrue(wide.commandWidth >= compact.commandWidth, "wider layout should not shrink command panel further")
        assertTrue(wide.centerWidth >= wide.commandWidth, "desktop layout should keep center deck wider than command deck")
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
        val compact = computeSelectionSlotVisualLayout(30)
        val wide = computeSelectionSlotVisualLayout(40)

        assertTrue(compact.topBarHeight < wide.topBarHeight, "compact slots should use thinner chrome bars")
        assertTrue(compact.titleHeight < wide.titleHeight, "compact slots should use shorter title rows")
        assertTrue(compact.hpBarWidth < wide.hpBarWidth, "compact slots should use shorter hp bars")
        assertTrue(compact.hpBarHeight < wide.hpBarHeight, "compact slots should use thinner hp bars")
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
    fun `minimap and bottom hud stay compact on desktop layouts`() {
        val hdMinimap = gdxMiniMapBounds(1920, 1080)
        val compactMinimap = gdxMiniMapBounds(1280, 720)

        assertTrue(hdMinimap.width <= 192f, "desktop minimap should cap width")
        assertTrue(hdMinimap.height <= 160f, "desktop minimap should cap height")
        assertTrue(compactMinimap.width <= 192f, "compact minimap should stay narrow")
        assertTrue(compactMinimap.height <= 160f, "compact minimap should stay short")
        assertTrue(computeBottomHudHeight(1920, 1080) <= 160, "bottom hud should not exceed compact shell target")
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
    fun `hud footer line reflects selection and armed mode`() {
        assertEquals("LMB select  RMB move  drag box select", buildHudFooterLine(hasSelection = false, commandArmed = false))
        assertEquals("LMB reselect  RMB order  drag box add", buildHudFooterLine(hasSelection = true, commandArmed = false))
        assertEquals("LMB/RMB confirm  Esc cancel  MMB pan", buildHudFooterLine(hasSelection = true, commandArmed = true))
    }

    @Test
    fun `minimap hint reflects armed mode`() {
        assertEquals("minimap drag camera", buildMinimapHintLine(commandArmed = false))
        assertEquals("armed: world click confirm", buildMinimapHintLine(commandArmed = true))
    }

    @Test
    fun `queue header reflects mixed pipeline states`() {
        assertEquals("QUEUE", resolveQueueHeaderLine(hasProduction = false, hasResearch = false, underConstruction = false))
        assertEquals("PRODUCTION", resolveQueueHeaderLine(hasProduction = true, hasResearch = false, underConstruction = false))
        assertEquals("RESEARCH", resolveQueueHeaderLine(hasProduction = false, hasResearch = true, underConstruction = false))
        assertEquals("PIPELINE", resolveQueueHeaderLine(hasProduction = true, hasResearch = true, underConstruction = false))
        assertEquals("CONSTRUCT", resolveQueueHeaderLine(hasProduction = false, hasResearch = false, underConstruction = true))
    }

    @Test
    fun `queue status line formats compact readable summaries`() {
        assertEquals(
            "Train Marine x2 · 18t  |  Tech Stim x1 · 30t  |  Build · 12t",
            formatQueueStatusLine(
                productionType = "Marine",
                productionQueueSize = 2,
                productionRemainingTicks = 18,
                researchTech = "Stim",
                researchQueueSize = 1,
                researchRemainingTicks = 30,
                underConstruction = true,
                constructionRemainingTicks = 12
            )
        )
        assertEquals(
            "Idle",
            formatQueueStatusLine(
                productionType = null,
                productionQueueSize = 0,
                productionRemainingTicks = 0,
                researchTech = null,
                researchQueueSize = 0,
                researchRemainingTicks = 0,
                underConstruction = false,
                constructionRemainingTicks = null
            )
        )
    }

    @Test
    fun `selection pager helpers stay compact`() {
        assertEquals("Pg 0/0 · 0", formatSelectionPageLabel(selectedCount = 0, pageIndex = 0, pageCount = 1))
        assertEquals("Pg 2/3 · 14", formatSelectionPageLabel(selectedCount = 14, pageIndex = 1, pageCount = 3))
        assertEquals("Groups idle", formatControlGroupDeckLine(emptyList()))
        assertEquals("4:8  5:2  6:1", formatControlGroupDeckLine(listOf(4 to 8, 5 to 2, 6 to 1, 7 to 9)))
    }

    @Test
    fun `command header shows true production paging`() {
        assertEquals("Orders · DEFAULT", formatCommandHeaderLine("default", productionCount = 6, productionPage = 0))
        assertEquals("Orders · DEFAULT · 2/3", formatCommandHeaderLine("default", productionCount = 14, productionPage = 1))
        assertEquals("Orders · DEFAULT · 3/3", formatCommandHeaderLine("default", productionCount = 14, productionPage = 9))
    }

    @Test
    fun `command button labels stay compact and readable`() {
        assertEquals("Attack", formatCommandButtonText("attackMove", "Attack Move"))
        assertEquals("Expand", formatCommandButtonText("build:ResourceDepot", "Build ResourceDepot"))
        assertEquals("Cancel T", formatCommandButtonText("cancelTrain", "Cancel Train"))
        assertEquals("A", resolveCommandButtonHotkey("attackMove"))
        assertEquals("Esc", resolveCommandButtonHotkey("clear"))
        assertEquals("F1", resolveCommandButtonHotkey("help"))
    }

    @Test
    fun `effect palettes stay coherent across unit classes`() {
        val faction = Color(0.30f, 0.60f, 1.00f, 1f)

        val marineAccent = resolveEffectAccentColor("Marine", isStructure = false, factionColor = faction)
        val zergAccent = resolveEffectAccentColor("Zergling", isStructure = false, factionColor = faction)
        val structureDebris = resolveEffectDebrisColor("Depot", isStructure = true, factionColor = faction)
        val zergSmoke = resolveEffectSmokeColor("Zergling", isStructure = false, factionColor = faction)

        assertTrue(marineAccent.r > faction.r, "marine accents should warm the base faction tone")
        assertTrue(zergAccent.g < marineAccent.g, "zerg accents should read harsher than marine accents")
        assertTrue(structureDebris.r > zergSmoke.r, "structure debris should stay warmer than zerg smoke")
        assertTrue(zergSmoke.a == 1f, "palette helpers should return opaque base colors")
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
