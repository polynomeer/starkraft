package starkraft.sim

import com.badlogic.gdx.graphics.Color
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.badlogic.gdx.Input
import starkraft.sim.client.computeBottomHudLayout
import starkraft.sim.client.computeCenterPanelLayout
import starkraft.sim.client.computeCommandDeckLayout
import starkraft.sim.client.computeCommandDeckPresentation
import starkraft.sim.client.computeCommandHintRowHeight
import starkraft.sim.client.computeSelectionSlotVisualLayout
import starkraft.sim.client.computeTopBarLayout
import starkraft.sim.client.computeVisibleTileRange
import starkraft.sim.client.EscapeAction
import starkraft.sim.client.computeBottomHudHeight
import starkraft.sim.client.computeWorldViewportHeightForLayout
import starkraft.sim.client.feedbackPulseDecay
import starkraft.sim.client.buildHudFooterLine
import starkraft.sim.client.buildMinimapHintLine
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.formatCommandButtonText
import starkraft.sim.client.formatDeckCommandButtonText
import starkraft.sim.client.formatDeckCommandCaption
import starkraft.sim.client.formatDeckCommandButtonStateSuffix
import starkraft.sim.client.formatCommandHeaderLine
import starkraft.sim.client.formatFactionOverviewLine
import starkraft.sim.client.formatFixedHudHintSlot
import starkraft.sim.client.formatSelectionMetaLine
import starkraft.sim.client.formatSelectionMetaSummary
import starkraft.sim.client.formatSelectionHeadline
import starkraft.sim.client.formatFocusedEntityStatus
import starkraft.sim.client.formatQueueStatusLine
import starkraft.sim.client.formatSelectionPageLabel
import starkraft.sim.client.formatControlGroupDeckLine
import starkraft.sim.client.formatPerfMillis
import starkraft.sim.client.formatSelectionRosterSummary
import starkraft.sim.client.gdxMiniMapBounds
import starkraft.sim.client.overlayBlocksWorldInput
import starkraft.sim.client.resolveEffectAccentColor
import starkraft.sim.client.resolveEffectDebrisColor
import starkraft.sim.client.resolveEffectSmokeColor
import starkraft.sim.client.resolveEscapeAction
import starkraft.sim.client.resolveCommandButtonHotkey
import starkraft.sim.client.shouldRenderCompletionOverlay
import starkraft.sim.client.resolveQueueHeaderLine
import starkraft.sim.client.compactCommandHint
import starkraft.sim.client.MiniMapMarkerKind
import starkraft.sim.client.resolveMiniMapMarkerKind
import starkraft.sim.client.resolveMiniMapMarkerSize
import starkraft.sim.client.resolveMiniMapSampleStep
import starkraft.sim.client.resolveMiniMapMarkerFillColor
import starkraft.sim.client.resolveMiniMapMarkerAccentColor
import starkraft.sim.client.resolveTerrainTileColor
import starkraft.sim.client.resolveCommandDeckGroups
import starkraft.sim.client.resolveCommandDeckHeader
import starkraft.sim.client.resolveCommandButtonDisabledReason
import starkraft.sim.client.resolveResourceCoreColor
import starkraft.sim.client.resourceCoreRadius
import starkraft.sim.client.resolveResourceGlowColor
import starkraft.sim.client.resolveFallbackTeamTintStrength
import starkraft.sim.client.resolveSilhouetteBodyColor
import starkraft.sim.client.resolveSilhouetteStripeColor
import starkraft.sim.client.resolveSilhouetteTrimColor
import starkraft.sim.client.resolveStructureRoofColor
import starkraft.sim.client.groundPingBaseTone
import starkraft.sim.client.groundPingCoreTone
import starkraft.sim.client.groundPingQueuedTone
import starkraft.sim.client.overlayCompletionFlashAlpha
import starkraft.sim.client.overlayDamageFlashAlpha
import starkraft.sim.client.CombatSoundKind
import starkraft.sim.client.CompletionFlashKind
import starkraft.sim.client.deathBurstCoreRadius
import starkraft.sim.client.deathBurstOuterRadius
import starkraft.sim.client.selectionBoxCorner
import starkraft.sim.client.selectionBoxAccentTone
import starkraft.sim.client.selectionBoxFillTone
import starkraft.sim.client.selectionBoxFrameTone
import starkraft.sim.client.selectionUnitRadius
import starkraft.sim.client.shouldRenderDetailedTerrainForTileSize
import starkraft.sim.client.smoothMetricSample
import starkraft.sim.client.ClientCommandButton
import starkraft.sim.client.ClientGroundCommandMode
import starkraft.sim.client.GroundPingKind
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.ResourceNodeSnapshot
import starkraft.sim.client.ArmedPreviewKind
import starkraft.sim.client.PreviewTargetRelation
import starkraft.sim.client.defaultPresentationTheme
import starkraft.sim.client.isWorkerEntity
import starkraft.sim.client.resolveArmedPreviewKind
import starkraft.sim.client.resolvePreviewTargetRelation
import starkraft.sim.client.commandGroupAccentBackgroundTone
import starkraft.sim.client.commandGroupHeaderBackgroundTone
import starkraft.sim.client.commandGroupShellBackgroundTone
import starkraft.sim.client.commandButtonAccentBackgroundTone
import starkraft.sim.client.commandButtonCheckedAccentTone
import starkraft.sim.client.commandButtonPulseBackgroundTone
import starkraft.sim.client.commandButtonQueueBackgroundTone
import starkraft.sim.client.commandButtonShellBackgroundTone
import starkraft.sim.client.resolveCommandButtonRole
import starkraft.sim.client.CommandButtonRole
import starkraft.sim.client.minimapFooterAccentTone
import starkraft.sim.client.minimapFooterCardTone
import starkraft.sim.client.minimapHeaderAccentTone
import starkraft.sim.client.minimapHeaderCardTone
import starkraft.sim.client.selectionSlotBadgeTone
import starkraft.sim.client.selectionSlotCardTone
import starkraft.sim.client.selectionFocusAccentTone
import starkraft.sim.client.selectionFocusCardBackgroundTone
import starkraft.sim.client.selectionFocusShellBackgroundTone
import starkraft.sim.client.selectionSlotRoleTone
import starkraft.sim.client.selectionSlotShellTone
import starkraft.sim.client.statusChipTonePair
import starkraft.sim.client.StatusChipRole
import starkraft.sim.client.WarningBannerState
import starkraft.sim.client.warningBannerAccentTone
import starkraft.sim.client.warningBannerCardTone
import starkraft.sim.client.warningBannerState
import starkraft.sim.client.shouldAbortPointerGesture
import starkraft.sim.client.shouldDispatchCommandUiAction
import starkraft.sim.client.shouldHandleHotkeyWhileOverlayVisible
import starkraft.sim.client.shouldIssueSelectionBox
import starkraft.sim.client.CameraView
import starkraft.sim.client.NoticeKind

class GameScreenInputRulesTest {
    @Test
    fun `armed command preview resolves by active command mode`() {
        assertEquals(ArmedPreviewKind.MOVE, resolveArmedPreviewKind(ClientGroundCommandMode.MOVE, null))
        assertEquals(ArmedPreviewKind.ATTACK, resolveArmedPreviewKind(ClientGroundCommandMode.ATTACK_MOVE, null))
        assertEquals(ArmedPreviewKind.PATROL, resolveArmedPreviewKind(ClientGroundCommandMode.PATROL, null))
        assertEquals(ArmedPreviewKind.RALLY, resolveArmedPreviewKind(null, 17))
        assertEquals(null, resolveArmedPreviewKind(null, null))
    }

    @Test
    fun `preview target relation distinguishes friendly hostile and resource targets`() {
        val snapshot =
            ClientSnapshot(
                tick = 10,
                mapId = "test",
                buildVersion = "dev",
                mapWidth = 64,
                mapHeight = 64,
                factions = emptyList(),
                entities =
                    listOf(
                        EntitySnapshot(
                            id = 1,
                            faction = 1,
                            typeId = "Marine",
                            archetype = "infantry",
                            x = 5f,
                            y = 5f,
                            dir = 0f,
                            hp = 40,
                            maxHp = 40,
                            armor = 0,
                            weaponId = "Rifle"
                        ),
                        EntitySnapshot(
                            id = 2,
                            faction = 2,
                            typeId = "Zergling",
                            archetype = "melee",
                            x = 8f,
                            y = 8f,
                            dir = 0f,
                            hp = 35,
                            maxHp = 35,
                            armor = 0,
                            weaponId = "Claw"
                        )
                    ),
                resourceNodes =
                    listOf(
                        ResourceNodeSnapshot(
                            id = 9,
                            kind = "mineral",
                            x = 11f,
                            y = 11f,
                            remaining = 250,
                            yieldPerTick = 1
                        )
                    )
            )

        assertEquals(
            PreviewTargetRelation.FRIENDLY,
            resolvePreviewTargetRelation(snapshot, selectedIds = setOf(1), worldX = 5.1f, worldY = 5.1f)
        )
        assertEquals(
            PreviewTargetRelation.HOSTILE,
            resolvePreviewTargetRelation(snapshot, selectedIds = setOf(1), worldX = 8.1f, worldY = 8.1f)
        )
        assertEquals(
            PreviewTargetRelation.RESOURCE,
            resolvePreviewTargetRelation(snapshot, selectedIds = setOf(1), worldX = 11.1f, worldY = 11.1f)
        )
        assertEquals(
            PreviewTargetRelation.NONE,
            resolvePreviewTargetRelation(snapshot, selectedIds = setOf(1), worldX = 20f, worldY = 20f)
        )
    }

    @Test
    fun `overlay alpha helpers prioritize readability by event type`() {
        assertTrue(
            overlayDamageFlashAlpha(selected = false, kind = CombatSoundKind.MELEE) >
                overlayDamageFlashAlpha(selected = false, kind = CombatSoundKind.RANGED)
        )
        assertTrue(
            overlayDamageFlashAlpha(selected = false, kind = CombatSoundKind.RANGED) >
                overlayDamageFlashAlpha(selected = true, kind = CombatSoundKind.RANGED)
        )
        assertTrue(
            overlayCompletionFlashAlpha(selected = false, kind = CompletionFlashKind.CONSTRUCTION) >
                overlayCompletionFlashAlpha(selected = false, kind = CompletionFlashKind.PRODUCTION)
        )
        assertTrue(
            overlayCompletionFlashAlpha(selected = false, kind = CompletionFlashKind.PRODUCTION) >
                overlayCompletionFlashAlpha(selected = true, kind = CompletionFlashKind.PRODUCTION)
        )
        assertEquals(6f, selectionBoxCorner(8f, 10f))
        assertEquals(18f, selectionBoxCorner(300f, 220f))
        assertEquals(10f, selectionUnitRadius(18f))
        assertEquals(13f, selectionUnitRadius(64f))
        assertEquals(5f, deathBurstCoreRadius(0f, structure = false))
        assertEquals(20f, deathBurstCoreRadius(12f / 14f, structure = true))
        assertEquals(11f, deathBurstOuterRadius(0f, structure = false))
        assertEquals(44f, deathBurstOuterRadius(1f, structure = true))
    }

    @Test
    fun `feedback pulse decay clamps and eases remaining time`() {
        assertEquals(0f, feedbackPulseDecay(remainingMillis = -1L, durationMillis = 260L))
        assertEquals(0f, feedbackPulseDecay(remainingMillis = 42L, durationMillis = 0L))
        assertEquals(1f, feedbackPulseDecay(remainingMillis = 260L, durationMillis = 260L))
        assertEquals(0.25f, feedbackPulseDecay(remainingMillis = 130L, durationMillis = 260L))
        assertEquals(1f, feedbackPulseDecay(remainingMillis = 520L, durationMillis = 260L))
    }

    @Test
    fun `terrain and resource helpers preserve biome identity`() {
        val a = Color(0.10f, 0.20f, 0.30f, 1f)
        val b = Color(0.20f, 0.30f, 0.40f, 1f)
        val dust = Color(0.30f, 0.24f, 0.18f, 1f)
        val ridge = Color(0.18f, 0.18f, 0.20f, 1f)

        val dustTile = resolveTerrainTileColor(44, 44, a, b, dust, ridge)
        val ridgeTile = resolveTerrainTileColor(0, 0, a, b, dust, ridge)
        val patchTile = resolveTerrainTileColor(10, 8, a, b, dust, ridge)

        assertEquals(dust, dustTile)
        assertEquals(ridge, ridgeTile)
        assertTrue(patchTile != dust, "non-dust tiles should not collapse to dust biome")
        assertEquals(9f, resourceCoreRadius("gas"))
        assertEquals(8f, resourceCoreRadius("mineral"))
    }

    @Test
    fun `selection slot tones separate combat worker and structure roles`() {
        val colors = defaultPresentationTheme().colors
        val combat =
            EntitySnapshot(
                id = 1,
                faction = 1,
                typeId = "Marine",
                archetype = "infantry",
                x = 3f,
                y = 4f,
                dir = 0f,
                hp = 40,
                maxHp = 40,
                armor = 0,
                weaponId = "Rifle"
            )
        val worker =
            EntitySnapshot(
                id = 2,
                faction = 1,
                typeId = "Worker",
                archetype = "worker",
                x = 4f,
                y = 4f,
                dir = 0f,
                hp = 35,
                maxHp = 35,
                armor = 0
            )
        val structure =
            EntitySnapshot(
                id = 3,
                faction = 1,
                typeId = "Depot",
                archetype = "producer",
                x = 5f,
                y = 4f,
                dir = 0f,
                hp = 400,
                maxHp = 400,
                armor = 1,
                footprintWidth = 2,
                footprintHeight = 2
            )

        assertTrue(isWorkerEntity(worker))
        assertFalse(isWorkerEntity(combat))

        assertNotEquals(selectionSlotRoleTone(colors, combat), selectionSlotRoleTone(colors, worker))
        assertNotEquals(selectionSlotRoleTone(colors, structure), selectionSlotRoleTone(colors, worker))
        assertNotEquals(selectionSlotBadgeTone(colors, combat), selectionSlotBadgeTone(colors, structure))

        val damagedShell = selectionSlotShellTone(colors, combat, damaged = true, clickPulse = 0f)
        val activeShell = selectionSlotShellTone(colors, combat, damaged = false, clickPulse = 1f)
        val idleShell = selectionSlotShellTone(colors, combat, damaged = false, clickPulse = 0f)
        assertNotEquals(idleShell, damagedShell)
        assertNotEquals(idleShell, activeShell)

        val damagedCard = selectionSlotCardTone(colors, structure, damaged = true, clickPulse = 0f)
        val activeCard = selectionSlotCardTone(colors, structure, damaged = false, clickPulse = 1f)
        val idleCard = selectionSlotCardTone(colors, structure, damaged = false, clickPulse = 0f)
        assertNotEquals(idleCard, damagedCard)
        assertNotEquals(idleCard, activeCard)
    }

    @Test
    fun `command group tones stay role aligned across shell header and accent`() {
        val colors = defaultPresentationTheme().colors

        val orderAccent = commandGroupAccentBackgroundTone(colors, "Orders")
        val buildAccent = commandGroupAccentBackgroundTone(colors, "Build")
        val utilityAccent = commandGroupAccentBackgroundTone(colors, "Utility")

        assertNotEquals(orderAccent, buildAccent)
        assertNotEquals(buildAccent, utilityAccent)
        assertNotEquals(
            commandGroupHeaderBackgroundTone(colors, "Production"),
            commandGroupHeaderBackgroundTone(colors, "Research")
        )
        assertNotEquals(
            commandGroupShellBackgroundTone(colors, "Production"),
            commandGroupShellBackgroundTone(colors, "System")
        )
    }

    @Test
    fun `status chip tone pairs separate ready idle and production states`() {
        val colors = defaultPresentationTheme().colors

        val ready = statusChipTonePair(colors, StatusChipRole.READY)
        val idle = statusChipTonePair(colors, StatusChipRole.IDLE)
        val production = statusChipTonePair(colors, StatusChipRole.PRODUCTION)

        assertNotEquals(ready.first, idle.first)
        assertNotEquals(ready.second, production.second)
        assertNotEquals(idle.first, production.first)
    }

    @Test
    fun `selection focus tones stay aligned with green battlefield accent`() {
        val colors = defaultPresentationTheme().colors
        val accent = selectionFocusAccentTone(colors)

        assertTrue(accent.g >= accent.r, "focus accent should lean green over red")
        assertTrue(accent.g >= accent.b, "focus accent should lean green over blue")
        assertNotEquals(
            selectionFocusShellBackgroundTone(colors, 0f),
            selectionFocusShellBackgroundTone(colors, 1f)
        )
        assertNotEquals(
            selectionFocusCardBackgroundTone(colors, 0f),
            selectionFocusCardBackgroundTone(colors, 1f)
        )
    }

    @Test
    fun `warning banner tones separate attack loss and trade states`() {
        val colors = defaultPresentationTheme().colors

        val attack = warningBannerState(hasAttackWarning = true, isStructureLoss = false, noticeKind = null)
        val structureLoss = warningBannerState(hasAttackWarning = true, isStructureLoss = true, noticeKind = null)
        val trade = warningBannerState(hasAttackWarning = false, isStructureLoss = false, noticeKind = NoticeKind.TRADE)

        assertEquals(WarningBannerState.ATTACK, attack)
        assertEquals(WarningBannerState.STRUCTURE_LOSS, structureLoss)
        assertEquals(WarningBannerState.TRADE, trade)
        assertNotEquals(warningBannerCardTone(colors, attack), warningBannerCardTone(colors, trade))
        assertNotEquals(warningBannerAccentTone(colors, structureLoss), warningBannerAccentTone(colors, trade))
    }

    @Test
    fun `minimap chrome reacts to armed and alert states`() {
        val colors = defaultPresentationTheme().colors

        val idleHeader = minimapHeaderCardTone(colors, hasAttackWarning = false, viewedFaction = 1, groundModeArmed = false, rallyModeArmed = false, buildModeArmed = false)
        val alertHeader = minimapHeaderCardTone(colors, hasAttackWarning = true, viewedFaction = 1, groundModeArmed = false, rallyModeArmed = false, buildModeArmed = false)
        val armedHeaderAccent = minimapHeaderAccentTone(colors, hasAttackWarning = false, groundModeArmed = true, rallyModeArmed = false, buildModeArmed = false)
        val idleFooter = minimapFooterCardTone(colors, commandArmed = false)
        val armedFooter = minimapFooterCardTone(colors, commandArmed = true)

        assertNotEquals(idleHeader, alertHeader)
        assertNotEquals(armedHeaderAccent, minimapHeaderAccentTone(colors, hasAttackWarning = true, groundModeArmed = false, rallyModeArmed = false, buildModeArmed = false))
        assertNotEquals(idleFooter, armedFooter)
        assertNotEquals(minimapFooterAccentTone(colors, commandArmed = false), minimapFooterAccentTone(colors, commandArmed = true))
    }

    @Test
    fun `world selection box and ping tones align with hud accents`() {
        val colors = defaultPresentationTheme().colors

        val fill = selectionBoxFillTone(colors)
        val frame = selectionBoxFrameTone(colors)
        val accent = selectionBoxAccentTone(colors)

        assertTrue(frame.g >= frame.r, "selection frame should lean green")
        assertTrue(accent.g >= accent.r, "selection accent should lean green")
        assertTrue(fill.a < frame.a, "selection fill should stay subtler than frame")

        assertNotEquals(groundPingBaseTone(colors, GroundPingKind.MOVE), groundPingBaseTone(colors, GroundPingKind.ATTACK))
        assertNotEquals(groundPingCoreTone(colors, GroundPingKind.BUILD), groundPingCoreTone(colors, GroundPingKind.INVALID))
        assertNotEquals(groundPingQueuedTone(colors, GroundPingKind.MOVE), groundPingQueuedTone(colors, GroundPingKind.ATTACK))
    }

    @Test
    fun `command button role helpers keep action families visually distinct`() {
        val colors = defaultPresentationTheme().colors

        assertEquals(CommandButtonRole.ATTACK, resolveCommandButtonRole("attackMove"))
        assertEquals(CommandButtonRole.MOVE, resolveCommandButtonRole("move"))
        assertEquals(CommandButtonRole.BUILD, resolveCommandButtonRole("build:Depot"))
        assertEquals(CommandButtonRole.TRAIN, resolveCommandButtonRole("train:Marine"))
        assertEquals(CommandButtonRole.RESEARCH, resolveCommandButtonRole("research:Stim"))
        assertEquals(CommandButtonRole.RALLY, resolveCommandButtonRole("rally"))
        assertEquals(CommandButtonRole.SYSTEM, resolveCommandButtonRole("pause"))

        assertNotEquals(
            commandButtonAccentBackgroundTone(colors, "attackMove"),
            commandButtonAccentBackgroundTone(colors, "train:Marine")
        )
        assertNotEquals(
            commandButtonShellBackgroundTone(colors, "move"),
            commandButtonShellBackgroundTone(colors, "build:Depot")
        )
        assertNotEquals(
            commandButtonPulseBackgroundTone(colors, "research:Stim"),
            commandButtonPulseBackgroundTone(colors, "rally")
        )
        assertNotEquals(
            commandButtonQueueBackgroundTone(colors, "train:Marine"),
            commandButtonQueueBackgroundTone(colors, "cancelBuild")
        )
        assertNotEquals(
            commandButtonCheckedAccentTone(colors, "attackMove"),
            commandButtonCheckedAccentTone(colors, "move")
        )
        assertNotEquals(
            commandButtonCheckedAccentTone(colors, "build:Depot"),
            commandButtonCheckedAccentTone(colors, "research:Stim")
        )
    }

    @Test
    fun `bottom hud layout scales down at supported resolutions`() {
        val compact = computeBottomHudLayout(1280, 720)
        val wide = computeBottomHudLayout(1920, 1080)

        assertEquals(gdxMiniMapBounds(1280, 720).width.toInt(), compact.leftSlotWidth, "left HUD lane should match minimap width")
        assertEquals(gdxMiniMapBounds(1920, 1080).width.toInt(), wide.leftSlotWidth, "wide left HUD lane should match minimap width")
        assertTrue(compact.centerWidth in 226..286, "1280 layout should give center panel more readable width")
        assertTrue(compact.commandWidth in 196..238, "1280 layout should keep command panel compact but readable")
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
        assertTrue(compact.sectionGap < wide.sectionGap, "small screens should tighten command sections")
        assertTrue(compact.buttonGap < wide.buttonGap, "small screens should tighten command button gaps")
        assertTrue(compact.headerPadY < wide.headerPadY, "small screens should reduce header padding height")
        assertTrue(compact.pagerButtonWidth < wide.pagerButtonWidth, "small screens should use narrower pager buttons")
        assertTrue(compact.hintWidth < wide.hintWidth, "small screens should use narrower command hints")
    }

    @Test
    fun `structure command deck uses larger two column presentation`() {
        val base = computeCommandDeckLayout(1280, 720)
        val standard = computeCommandDeckPresentation(base, structureDeck = false)
        val structure = computeCommandDeckPresentation(base, structureDeck = true)

        assertEquals(3, standard.columns)
        assertEquals(2, structure.columns)
        assertTrue(structure.buttonHeight > standard.buttonHeight, "structure deck should use taller buttons")
        assertTrue(structure.scrollHeight > standard.scrollHeight, "structure deck should expose more vertical space")
        assertTrue(structure.actorInset < standard.actorInset, "structure deck should dedicate more width to button labels")
    }

    @Test
    fun `command hint row stays fixed and compact`() {
        assertEquals(20, computeCommandHintRowHeight(1280))
        assertEquals(22, computeCommandHintRowHeight(1920))
        assertEquals("f2", compactCommandHint("Switch to faction 2 view"))
        assertEquals("prod queue sel", compactCommandHint("production queue current selection"))
    }

    @Test
    fun `structure command labels stay compact and readable`() {
        assertEquals("Trn Mar", formatDeckCommandButtonText("train:Marine", "Train Marine"))
        assertEquals("Tech Adv", formatDeckCommandButtonText("research:AdvancedTraining", "Research Advanced"))
        assertEquals("Stop T", formatDeckCommandButtonText("cancelTrain", "Cancel Train"))
        assertEquals("Rally", formatDeckCommandButtonText("rally", "Rally"))
    }

    @Test
    fun `structure command labels expose active queue state`() {
        val selected =
            listOf(
                EntitySnapshot(
                    id = 7,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 4f,
                    y = 5f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    supportsTraining = true,
                    supportsResearch = true,
                    supportsRally = true,
                    productionQueueSize = 2,
                    activeProductionType = "Marine",
                    activeProductionRemainingTicks = 11,
                    researchQueueSize = 1,
                    activeResearchTech = "AdvancedTraining",
                    activeResearchRemainingTicks = 7,
                    rallyX = 12f,
                    rallyY = 9f
                ),
                EntitySnapshot(
                    id = 8,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 6f,
                    y = 5f,
                    dir = 0f,
                    hp = 200,
                    maxHp = 400,
                    armor = 1,
                    underConstruction = true
                )
            )

        assertEquals("11t", formatDeckCommandButtonStateSuffix("train:Marine", selected))
        assertEquals("7t", formatDeckCommandButtonStateSuffix("research:AdvancedTraining", selected))
        assertEquals("x2", formatDeckCommandButtonStateSuffix("cancelTrain", selected))
        assertEquals("x1", formatDeckCommandButtonStateSuffix("cancelResearch", selected))
        assertEquals("x1", formatDeckCommandButtonStateSuffix("cancelBuild", selected))
        assertEquals("set", formatDeckCommandButtonStateSuffix("rally", selected))
    }

    @Test
    fun `focused entity status summarizes live structure state`() {
        val entity =
            EntitySnapshot(
                id = 7,
                faction = 1,
                typeId = "Depot",
                archetype = "producer",
                x = 4f,
                y = 5f,
                dir = 0f,
                hp = 400,
                maxHp = 400,
                armor = 1,
                underConstruction = true,
                constructionRemainingTicks = 18,
                productionQueueSize = 2,
                activeProductionType = "Marine",
                activeProductionRemainingTicks = 11,
                researchQueueSize = 1,
                activeResearchTech = "AdvancedTraining",
                activeResearchRemainingTicks = 7,
                activeOrder = "attackMove",
                orderQueueSize = 3,
                rallyX = 12f,
                rallyY = 9f
            )

        assertEquals(
            "build 18t · prod Marine 11t · tech Adv 7t · ord attackmo · q3 · rally set",
            formatFocusedEntityStatus(entity)
        )
        assertEquals(null, formatFocusedEntityStatus(null))
    }

    @Test
    fun `selection headline favors player-readable unit and state summary`() {
        val focused =
            EntitySnapshot(
                id = 7,
                faction = 1,
                typeId = "Depot",
                archetype = "producer",
                x = 4f,
                y = 5f,
                dir = 0f,
                hp = 400,
                maxHp = 400,
                armor = 1,
                activeProductionType = "Marine",
                activeProductionRemainingTicks = 11,
                rallyX = 12f,
                rallyY = 9f
            )
        val other =
            EntitySnapshot(
                id = 8,
                faction = 1,
                typeId = "Marine",
                archetype = "infantry",
                x = 6f,
                y = 5f,
                dir = 0f,
                hp = 40,
                maxHp = 40,
                armor = 0
            )

        assertEquals(
            "Depot x2 · prod Marine 11t · rally set",
            formatSelectionHeadline(listOf(focused, other), focused, viewedFaction = 1, totalEntities = 14)
        )
        assertEquals(
            "Depot · Producer · prod Marine 11t · rally set",
            formatSelectionHeadline(listOf(focused), focused, viewedFaction = 1, totalEntities = 14)
        )
        assertEquals(
            "F1 view · 14 live",
            formatSelectionHeadline(emptyList(), null, viewedFaction = 1, totalEntities = 14)
        )
        assertEquals(
            "Observer · 14 live",
            formatSelectionHeadline(emptyList(), null, viewedFaction = null, totalEntities = 14)
        )
    }

    @Test
    fun `fixed hud hint slot stays compact and stable`() {
        assertEquals("f2", formatFixedHudHintSlot("Switch to faction 2 view"))
        assertEquals("cmd standby", formatFixedHudHintSlot(null))
        assertEquals("prod queue sel", formatFixedHudHintSlot("production queue current selection"))
    }

    @Test
    fun `disabled command reasons stay player-readable`() {
        assertEquals(
            "Select units first",
            resolveCommandButtonDisabledReason("move", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = 1)
        )
        assertEquals(
            "Select training structure",
            resolveCommandButtonDisabledReason("train:Marine", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1)
        )
        assertEquals(
            "Select research structure",
            resolveCommandButtonDisabledReason("research:AdvancedTraining", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1)
        )
        assertEquals(
            "Switch to faction view",
            resolveCommandButtonDisabledReason("select:viewFaction", hasSelection = false, canTrain = false, canResearch = false, viewedFaction = null)
        )
        assertEquals(
            null,
            resolveCommandButtonDisabledReason("move", hasSelection = true, canTrain = false, canResearch = false, viewedFaction = 1)
        )
    }

    @Test
    fun `center panel compacts on smaller resolutions`() {
        val compact = computeCenterPanelLayout(1280)
        val wide = computeCenterPanelLayout(1920)

        assertTrue(compact.portraitSize < wide.portraitSize, "small screens should use smaller portrait")
        assertTrue(compact.rosterSlotSize < wide.rosterSlotSize, "small screens should use smaller roster slots")
        assertTrue(compact.pagerButtonSize < wide.pagerButtonSize, "small screens should use smaller pager buttons")
        assertTrue(compact.healthBarWidth < wide.healthBarWidth, "small screens should use narrower health bars")
        assertTrue(compact.portraitGap < wide.portraitGap, "small screens should tighten portrait spacing")
        assertTrue(compact.sectionGap < wide.sectionGap, "small screens should tighten section spacing")
        assertTrue(compact.headerPadX < wide.headerPadX, "small screens should reduce header padding")
        assertTrue(compact.bodyPadX < wide.bodyPadX, "small screens should reduce body padding")
        assertTrue(compact.healthBarHeight < wide.healthBarHeight, "small screens should use shorter health bars")
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
    fun `structure selection uses split command deck groups`() {
        val buttons =
            listOf(
                ClientCommandButton("Rally", "rally"),
                ClientCommandButton("Train Marine", "train:Marine"),
                ClientCommandButton("Research Advanced", "research:AdvancedTraining"),
                ClientCommandButton("Build Depot", "build:Depot"),
                ClientCommandButton("Cancel Train", "cancelTrain"),
                ClientCommandButton("Pause", "pause"),
                ClientCommandButton("Help", "help")
            )
        val selected =
            listOf(
                EntitySnapshot(
                    id = 4,
                    faction = 1,
                    typeId = "Depot",
                    archetype = "producer",
                    x = 5f,
                    y = 6f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    footprintWidth = 2,
                    footprintHeight = 2,
                    supportsTraining = true,
                    supportsResearch = true,
                    supportsRally = true
                )
            )

        val groups = resolveCommandDeckGroups(buttons, selected, debugVisible = false)

        assertEquals(listOf("Orders", "Train", "Research", "Build", "System"), groups.map { it.first })
        assertEquals(listOf("rally"), groups.first { it.first == "Orders" }.second.map { it.actionId })
        assertEquals(listOf("train:Marine", "cancelTrain"), groups.first { it.first == "Train" }.second.map { it.actionId })
        assertEquals("Cmd depot", resolveCommandDeckHeader("default", groups, selected, productionPage = 0))
    }

    @Test
    fun `unit selection keeps compact shared command deck groups`() {
        val buttons =
            listOf(
                ClientCommandButton("Move", "move"),
                ClientCommandButton("Attack", "attackMove"),
                ClientCommandButton("Patrol", "patrol"),
                ClientCommandButton("Train Marine", "train:Marine"),
                ClientCommandButton("Pause", "pause")
            )
        val selected =
            listOf(
                EntitySnapshot(
                    id = 5,
                    faction = 1,
                    typeId = "Marine",
                    archetype = "infantry",
                    x = 8f,
                    y = 7f,
                    dir = 0f,
                    hp = 45,
                    maxHp = 45,
                    armor = 0,
                    weaponId = "Gauss"
                )
            )

        val groups = resolveCommandDeckGroups(buttons, selected, debugVisible = false)

        assertEquals(listOf("Orders", "Production", "Utility"), groups.map { it.first })
        assertEquals("Orders · DEF", resolveCommandDeckHeader("default", groups, selected, productionPage = 0))
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
    fun `visible tile range tracks camera instead of full map`() {
        val range =
            computeVisibleTileRange(
                camera = CameraView(panX = -640f, panY = -480f, zoom = 1f, baseTileSize = 20),
                screenWidth = 1280,
                screenHeight = 720,
                mapWidth = 96,
                mapHeight = 96
            )

        assertTrue(range.startX > 0, "camera offset should skip off-screen left tiles")
        assertTrue(range.startY > 0, "camera offset should skip off-screen top tiles")
        assertTrue(range.endXExclusive - range.startX < 96, "visible x-range should be smaller than full map")
        assertTrue(range.endYExclusive - range.startY < 96, "visible y-range should be smaller than full map")
    }

    @Test
    fun `visible tile range clamps to map edges`() {
        val range =
            computeVisibleTileRange(
                camera = CameraView(panX = 120f, panY = 120f, zoom = 1f, baseTileSize = 20),
                screenWidth = 1280,
                screenHeight = 720,
                mapWidth = 96,
                mapHeight = 96
            )

        assertEquals(0, range.startX)
        assertEquals(0, range.startY)
        assertTrue(range.endXExclusive <= 96)
        assertTrue(range.endYExclusive <= 96)
    }

    @Test
    fun `terrain detail drops on default play zoom for performance`() {
        assertFalse(shouldRenderDetailedTerrainForTileSize(20f))
        assertFalse(shouldRenderDetailedTerrainForTileSize(23.9f))
        assertTrue(shouldRenderDetailedTerrainForTileSize(24f))
    }

    @Test
    fun `minimap sampling coarsens on larger maps`() {
        assertEquals(2, resolveMiniMapSampleStep(32, 32))
        assertEquals(3, resolveMiniMapSampleStep(64, 64))
        assertEquals(4, resolveMiniMapSampleStep(96, 96))
        assertEquals(6, resolveMiniMapSampleStep(128, 128))
    }

    @Test
    fun `minimap marker helpers distinguish battlefield roles`() {
        val structure = EntitySnapshot(id = 1, faction = 1, typeId = "Depot", x = 1f, y = 1f, dir = 0f, hp = 100, maxHp = 100, armor = 1, footprintWidth = 2, footprintHeight = 2)
        val combat = EntitySnapshot(id = 2, faction = 1, typeId = "Marine", archetype = "infantry", x = 1f, y = 1f, dir = 0f, hp = 40, maxHp = 40, armor = 0, weaponId = "Gauss")
        val worker = EntitySnapshot(id = 3, faction = 1, typeId = "Worker", archetype = "worker", x = 1f, y = 1f, dir = 0f, hp = 20, maxHp = 20, armor = 0)
        val support = EntitySnapshot(id = 4, faction = 1, typeId = "Scout", archetype = "support", x = 1f, y = 1f, dir = 0f, hp = 30, maxHp = 30, armor = 0)
        val colors = defaultPresentationTheme().colors
        val faction = Color(0.30f, 0.60f, 1.00f, 1f)

        assertEquals(MiniMapMarkerKind.STRUCTURE, resolveMiniMapMarkerKind(structure))
        assertEquals(MiniMapMarkerKind.COMBAT, resolveMiniMapMarkerKind(combat))
        assertEquals(MiniMapMarkerKind.WORKER, resolveMiniMapMarkerKind(worker))
        assertEquals(MiniMapMarkerKind.SUPPORT, resolveMiniMapMarkerKind(support))
        assertTrue(resolveMiniMapMarkerSize(structure, selected = true) > resolveMiniMapMarkerSize(combat, selected = false))
        assertTrue(resolveMiniMapMarkerSize(combat, selected = true) > resolveMiniMapMarkerSize(worker, selected = false))
        val structureFill = resolveMiniMapMarkerFillColor(colors, structure, faction, visible = true)
        val combatFill = resolveMiniMapMarkerFillColor(colors, combat, faction, visible = true)
        val hiddenWorkerAccent = resolveMiniMapMarkerAccentColor(colors, worker, faction, visible = false)
        val visibleWorkerAccent = resolveMiniMapMarkerAccentColor(colors, worker, faction, visible = true)
        assertNotEquals(structureFill, combatFill)
        assertTrue(visibleWorkerAccent.a > hiddenWorkerAccent.a)
    }

    @Test
    fun `perf helpers smooth and format frame metrics predictably`() {
        assertEquals(12.5f, smoothMetricSample(0f, 12.5f))
        assertEquals(11.45f, smoothMetricSample(10f, 18.055557f), 0.0001f)
        assertEquals("0.8", formatPerfMillis(0.84f))
        assertEquals("16", formatPerfMillis(16.02f))
        assertEquals("16.7", formatPerfMillis(16.66f))
    }

    @Test
    fun `minimap and bottom hud stay compact on desktop layouts`() {
        val hdMinimap = gdxMiniMapBounds(1920, 1080)
        val compactMinimap = gdxMiniMapBounds(1280, 720)

        assertTrue(hdMinimap.width <= 176f, "desktop minimap should cap width")
        assertTrue(hdMinimap.height <= 144f, "desktop minimap should cap height")
        assertTrue(compactMinimap.width <= 176f, "compact minimap should stay narrow")
        assertTrue(compactMinimap.height <= 144f, "compact minimap should stay short")
        assertTrue(computeBottomHudHeight(1920, 1080) <= 144, "bottom hud should not exceed compact shell target")
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
        assertEquals("LMB reselect  RMB order  Shift queue  drag box add", buildHudFooterLine(hasSelection = true, commandArmed = false))
        assertEquals("LMB/RMB confirm  Shift queue  Esc cancel", buildHudFooterLine(hasSelection = true, commandArmed = true))
    }

    @Test
    fun `minimap hint reflects armed mode`() {
        assertEquals("minimap drag camera", buildMinimapHintLine(commandArmed = false))
        assertEquals("armed: minimap/world click confirm + shift queue", buildMinimapHintLine(commandArmed = true))
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
            "Trn Marine x2 · 18t  |  Tech Stim x1 · 30t  |  Bld · 12t",
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
    fun `faction overview line stays compact`() {
        assertEquals(
            "F1* 275/0 v42  |  F2 180/24 v31",
            formatFactionOverviewLine(
                factions = listOf(
                    FactionSnapshot(faction = 1, minerals = 275, gas = 0, visibleTiles = 42),
                    FactionSnapshot(faction = 2, minerals = 180, gas = 24, visibleTiles = 31)
                ),
                viewedFaction = 1
            )
        )
        assertEquals("No factions", formatFactionOverviewLine(emptyList(), viewedFaction = null))
    }

    @Test
    fun `command header shows true production paging`() {
        assertEquals("Orders · DEF", formatCommandHeaderLine("default", productionCount = 6, productionPage = 0))
        assertEquals("Orders · DEF · 2/3", formatCommandHeaderLine("default", productionCount = 14, productionPage = 1))
        assertEquals("Orders · DEF · 3/3", formatCommandHeaderLine("default", productionCount = 14, productionPage = 9))
    }

    @Test
    fun `command button labels stay compact and readable`() {
        assertEquals("Attack", formatCommandButtonText("attackMove", "Attack Move"))
        assertEquals("Expand", formatCommandButtonText("build:ResourceDepot", "Build ResourceDepot"))
        assertEquals("Cancel T", formatCommandButtonText("cancelTrain", "Cancel Train"))
        assertEquals("Attack · A · 11t", formatDeckCommandCaption("Attack", "A", "11t"))
        assertEquals("Move · M", formatDeckCommandCaption("Move", "M", null))
        assertEquals("A", resolveCommandButtonHotkey("attackMove"))
        assertEquals("Esc", resolveCommandButtonHotkey("clear"))
        assertEquals("F1", resolveCommandButtonHotkey("help"))
    }

    @Test
    fun `selection meta summary stays compact under dense hud`() {
        val selected =
            listOf(
                EntitySnapshot(id = 1, faction = 1, typeId = "Marine", archetype = "infantry", x = 0f, y = 0f, dir = 0f, hp = 40, maxHp = 40, armor = 0, weaponId = "Rifle"),
                EntitySnapshot(id = 2, faction = 1, typeId = "Worker", archetype = "worker", x = 0f, y = 0f, dir = 0f, hp = 35, maxHp = 35, armor = 0),
                EntitySnapshot(id = 3, faction = 1, typeId = "Depot", archetype = "producer", x = 0f, y = 0f, dir = 0f, hp = 400, maxHp = 400, armor = 1, footprintWidth = 2, footprintHeight = 2, supportsTraining = true, supportsResearch = true, supportsRally = true, rallyX = 8f, rallyY = 9f)
            )

        assertEquals("3 sel · cmb 1 · wrk 1 · bld 1 · prod 1 · tech 1 · ral 1/1", formatSelectionMetaSummary(selected))
        assertEquals("3 sel · cmb 1 · wrk 1 · bld 1 · prod 1 · tech 1 · ral 1/1", formatSelectionMetaLine(selected, viewedFaction = 1, totalEntities = 14))
        assertEquals("f1 · 14", formatSelectionMetaLine(emptyList(), viewedFaction = 1, totalEntities = 14))
        assertEquals("obs · 14", formatSelectionMetaLine(emptyList(), viewedFaction = null, totalEntities = 14))
    }

    @Test
    fun `selection roster summary stays concise for single and mixed selections`() {
        val single =
            listOf(
                EntitySnapshot(
                    id = 7,
                    faction = 1,
                    typeId = "ResourceDepot",
                    archetype = "producer",
                    x = 4f,
                    y = 5f,
                    dir = 0f,
                    hp = 400,
                    maxHp = 400,
                    armor = 1,
                    supportsTraining = true,
                    supportsResearch = true,
                    supportsRally = true,
                    rallyX = 12f,
                    rallyY = 9f,
                    activeProductionType = "Marine",
                    productionQueueSize = 2,
                    activeResearchTech = "AdvancedTraining",
                    researchQueueSize = 1,
                    underConstruction = true,
                    constructionRemainingTicks = 18
                )
            )
        val mixed =
            listOf(
                EntitySnapshot(id = 1, faction = 1, typeId = "Marine", archetype = "infantry", x = 0f, y = 0f, dir = 0f, hp = 40, maxHp = 40, armor = 0, weaponId = "Rifle"),
                EntitySnapshot(id = 2, faction = 1, typeId = "Marine", archetype = "infantry", x = 0f, y = 0f, dir = 0f, hp = 40, maxHp = 40, armor = 0, weaponId = "Rifle"),
                EntitySnapshot(id = 3, faction = 1, typeId = "Worker", archetype = "worker", x = 0f, y = 0f, dir = 0f, hp = 35, maxHp = 35, armor = 0),
                EntitySnapshot(id = 4, faction = 1, typeId = "ResourceDepot", archetype = "producer", x = 0f, y = 0f, dir = 0f, hp = 400, maxHp = 400, armor = 1, rallyX = 9f, rallyY = 8f)
            )

        assertEquals("Res · prod · train · tech · ral 12,9 · prod Marine x2 · tech Adv x1 · bld 18t", formatSelectionRosterSummary(single))
        assertEquals("Ros Mar:2   Wor:1   Res:1   ral:1", formatSelectionRosterSummary(mixed))
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
    fun `silhouette palettes differentiate units and structures`() {
        val faction = Color(0.30f, 0.60f, 1.00f, 1f)

        val marineBody = resolveSilhouetteBodyColor("Marine", isStructure = false, factionColor = faction)
        val zergBody = resolveSilhouetteBodyColor("Zergling", isStructure = false, factionColor = faction)
        val workerTrim = resolveSilhouetteTrimColor("Worker", isStructure = false, factionColor = faction)
        val structureStripe = resolveSilhouetteStripeColor("Depot", isStructure = true, factionColor = faction)
        val gasRoof = resolveStructureRoofColor("GasDepot", faction)

        assertTrue(zergBody.r > marineBody.r, "zerg body should read warmer than marine armor")
        assertTrue(workerTrim.b > zergBody.b, "worker trim should stay cooler than zerg flesh tones")
        assertTrue(structureStripe.g > faction.g, "structure stripe should brighten faction lane panels")
        assertTrue(gasRoof.g > gasRoof.r, "gas depot roof should skew greener than generic structures")
    }

    @Test
    fun `fallback tint strengths stay close to sprite manifests`() {
        assertEquals(0.06f, resolveFallbackTeamTintStrength("Worker", isStructure = false))
        assertEquals(0.05f, resolveFallbackTeamTintStrength("Marine", isStructure = false))
        assertEquals(0.04f, resolveFallbackTeamTintStrength("Zergling", isStructure = false))
        assertEquals(0.08f, resolveFallbackTeamTintStrength("Depot", isStructure = true))
    }

    @Test
    fun `completion overlay yields to damage overlay`() {
        assertFalse(shouldRenderCompletionOverlay(damageActive = true, completionActive = true))
        assertTrue(shouldRenderCompletionOverlay(damageActive = false, completionActive = true))
        assertFalse(shouldRenderCompletionOverlay(damageActive = false, completionActive = false))
    }

    @Test
    fun `resource palettes separate minerals and gas`() {
        val mineralCore = resolveResourceCoreColor("minerals")
        val gasCore = resolveResourceCoreColor("gas")
        val mineralGlow = resolveResourceGlowColor("minerals")
        val gasGlow = resolveResourceGlowColor("gas")

        assertTrue(mineralCore.r > gasCore.r, "minerals should stay warmer than gas")
        assertTrue(gasCore.g > mineralCore.g, "gas should read greener than minerals")
        assertTrue(mineralGlow.r > gasGlow.r, "mineral glows should stay brighter on red channel")
        assertTrue(gasGlow.g > mineralGlow.g, "gas glows should emphasize green channel")
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
