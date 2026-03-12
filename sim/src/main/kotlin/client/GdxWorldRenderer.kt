package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.abs
import kotlin.math.floor

internal class GdxWorldRenderer(
    private val assets: GdxUiAssets
) {
    private val screenCamera = OrthographicCamera()
    private val textCamera = OrthographicCamera()
    private val friendlyColor = Color(0.30f, 0.60f, 1.00f, 1f)
    private val enemyColor = Color(0.90f, 0.36f, 0.28f, 1f)
    private val neutralColor = Color(0.78f, 0.69f, 0.42f, 1f)
    private val selectionColor = Color(0.96f, 0.90f, 0.45f, 1f)
    private val selectionSoftColor = Color(0.44f, 0.80f, 0.92f, 0.28f)
    private val fogColor = Color(0.02f, 0.05f, 0.08f, 0.78f)
    private val shroudColor = Color(0.01f, 0.02f, 0.04f, 0.96f)
    private val minimapFogColor = Color(0.01f, 0.03f, 0.05f, 0.72f)
    private val minimapShroudColor = Color(0.01f, 0.02f, 0.03f, 0.92f)
    private val impactFlashColor = Color(1.00f, 0.46f, 0.28f, 0.34f)
    private val impactSparkColor = Color(1.00f, 0.86f, 0.54f, 0.92f)
    private val terrainA = Color(0.09f, 0.13f, 0.10f, 1f)
    private val terrainB = Color(0.11f, 0.16f, 0.12f, 1f)
    private val terrainRidge = Color(0.16f, 0.20f, 0.14f, 1f)
    private val terrainDust = Color(0.19f, 0.17f, 0.12f, 1f)
    private val terrainMetal = Color(0.16f, 0.19f, 0.22f, 1f)
    private val mapFrameColor = Color(0.18f, 0.42f, 0.48f, 0.90f)

    fun render(runtime: GdxClientRuntime, width: Int, height: Int, dragBox: DragSelectionBox?) {
        val snapshot = runtime.snapshot ?: return
        val shape = assets.shapeRenderer
        screenCamera.setToOrtho(true, width.toFloat(), height.toFloat())
        screenCamera.update()
        shape.projectionMatrix = screenCamera.combined

        Gdx.gl.glClearColor(0.05f, 0.08f, 0.11f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        shape.begin(ShapeRenderer.ShapeType.Filled)
        drawTerrain(shape, runtime)
        drawResources(shape, runtime)
        drawFog(shape, runtime)
        drawEntities(shape, runtime)
        drawActivityMarkers(shape, runtime)
        drawOrderMarkers(shape, runtime)
        drawGroundPing(shape, runtime)
        drawMiniMap(shape, runtime, width, height)
        drawBuildPreview(shape, runtime)
        drawSelectionBox(shape, dragBox)
        shape.end()

        shape.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(shape, runtime)
        drawTerrainEdges(shape, runtime)
        drawWorldFrame(shape, runtime)
        drawSelectionBrackets(shape, runtime)
        drawSelectionOverlays(shape, runtime)
        drawMiniMapViewport(shape, runtime, width, height)
        shape.end()

        drawLabels(runtime, width, height)
    }

    private fun drawTerrain(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState
        val tileSize = runtime.camera.tileSize
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                val sx = runtime.camera.worldToScreenX(x.toFloat())
                val sy = runtime.camera.worldToScreenY(y.toFloat())
                val base =
                    when {
                        x in 40..56 && y in 40..56 -> terrainDust
                        (x + y) % 11 < 3 -> terrainRidge
                        (x / 6 + y / 6) % 2 == 0 -> terrainA
                        else -> terrainB
                    }
                shape.color = base
                shape.rect(sx, sy, tileSize, tileSize)
                shape.color = base.cpy().lerp(Color.WHITE, 0.05f)
                shape.rect(sx + 1f, sy + 1f, tileSize - 2f, (tileSize * 0.28f).coerceAtLeast(2f))
            }
        }
        mapState?.blockedTiles?.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = terrainMetal
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
            shape.color = Color(0.28f, 0.30f, 0.34f, 0.82f)
            shape.rect(sx + 2f, sy + 2f, runtime.camera.tileSize - 4f, runtime.camera.tileSize - 4f)
        }
        mapState?.staticOccupancyTiles?.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = Color(0.37f, 0.24f, 0.14f, 0.84f)
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
            shape.color = Color(0.52f, 0.34f, 0.20f, 0.38f)
            shape.rect(sx + 3f, sy + 3f, runtime.camera.tileSize - 6f, runtime.camera.tileSize - 6f)
        }
    }

    private fun drawGrid(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        shape.color = Color(0.16f, 0.20f, 0.18f, 0.35f)
        for (x in 0..snapshot.mapWidth) {
            val px = runtime.camera.worldToScreenX(x.toFloat())
            shape.line(px, 0f, px, runtime.camera.worldToScreenY(snapshot.mapHeight.toFloat()))
        }
        for (y in 0..snapshot.mapHeight) {
            val py = runtime.camera.worldToScreenY(y.toFloat())
            shape.line(0f, py, runtime.camera.worldToScreenX(snapshot.mapWidth.toFloat()), py)
        }
    }

    private fun drawTerrainEdges(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState ?: return
        shape.color = Color(0.38f, 0.44f, 0.48f, 0.55f)
        mapState.blockedTiles.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            val tile = runtime.camera.tileSize
            shape.rect(sx, sy, tile, tile)
        }
        shape.color = Color(0.55f, 0.40f, 0.20f, 0.42f)
        for (x in 40..56) {
            val topY = runtime.camera.worldToScreenY(40f)
            val bottomY = runtime.camera.worldToScreenY(57f)
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            shape.line(sx, topY, sx, bottomY)
        }
        for (y in 40..56) {
            val leftX = runtime.camera.worldToScreenX(40f)
            val rightX = runtime.camera.worldToScreenX(57f)
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.line(leftX, sy, rightX, sy)
        }
    }

    private fun drawResources(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val pulse = ambientPulse(1400L)
        for (node in snapshot.resourceNodes) {
            val sx = runtime.camera.worldToScreenX(node.x)
            val sy = runtime.camera.worldToScreenY(node.y)
            if (node.kind == "gas") {
                shape.color = Color(0.10f, 0.18f, 0.16f, 0.92f)
                shape.circle(sx + 1.5f, sy + 1.5f, 10f)
                shape.color = Color(0.24f, 0.94f, 0.70f, 0.12f + (pulse * 0.10f))
                shape.circle(sx, sy, 12f + (pulse * 2f))
                shape.color = Color(0.26f, 0.82f, 0.60f, 0.95f)
                shape.circle(sx, sy, 9f)
                shape.color = Color(0.64f, 1.00f, 0.86f, 0.32f)
                shape.circle(sx - 2f, sy - 2f, 4f)
                shape.rect(sx - 2.5f, sy - 9f, 5f, 4f)
            } else {
                shape.color = Color(0.16f, 0.13f, 0.08f, 0.90f)
                shape.circle(sx + 1.5f, sy + 1.5f, 9f)
                shape.color = neutralColor
                shape.rect(sx - 7f, sy - 5f, 6f, 5f)
                shape.rect(sx - 1f, sy - 8f, 7f, 6f)
                shape.rect(sx - 6f, sy + 1f, 8f, 5f)
                shape.color = Color(1f, 0.95f, 0.72f, 0.12f + (pulse * 0.12f))
                shape.rect(sx - 5f, sy - 4f, 3f, 2f)
                shape.rect(sx, sy - 6f, 3f, 2f)
                shape.color = Color(1f, 0.98f, 0.80f, 0.16f + (pulse * 0.12f))
                shape.rect(sx - 2f, sy + 2f, 4f, 2f)
            }
        }
    }

    private fun drawEntities(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val viewedFaction = runtime.session.state.viewedFaction
        for (entity in snapshot.entities) {
            if (!isEntityVisible(entity, runtime)) continue
            val screenX = runtime.camera.worldToScreenX(entity.x)
            val screenY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(screenX, screenY)) continue
            val footprintWidth = entity.footprintWidth
            val footprintHeight = entity.footprintHeight
            val selected = entity.id in runtime.session.state.selectedIds
            if (footprintWidth != null && footprintHeight != null) {
                val tileX = floor(entity.x).toInt()
                val tileY = floor(entity.y).toInt()
                val w = footprintWidth * runtime.camera.tileSize
                val h = footprintHeight * runtime.camera.tileSize
                val left = runtime.camera.worldToScreenX(tileX.toFloat())
                val top = runtime.camera.worldToScreenY(tileY.toFloat())
                shape.color = Color(0f, 0f, 0f, 0.18f)
                shape.rect(left + 7f, top + 8f, w, h)
                shape.color = Color(0f, 0f, 0f, 0.12f)
                shape.rect(left + 3f, top + h - 4f, w + 4f, 8f)
                if (runtime.isDamageFlashActive(entity.id)) {
                    shape.color = impactFlashColor
                    shape.rect(left - 6f, top - 6f, w + 12f, h + 12f)
                }
                if (selected) {
                    val pulse = selectionPulse()
                    shape.color = Color(selectionColor.r, selectionColor.g, selectionColor.b, 0.12f + (pulse * 0.10f))
                    shape.rect(left - 8f, top - 8f, w + 16f, h + 16f)
                }
                shape.color = Color(0f, 0f, 0f, 0.22f)
                shape.rect(left + 3f, top + 3f, w, h)
                drawStructureSilhouette(
                    shape = shape,
                    entity = entity,
                    tileX = tileX,
                    tileY = tileY,
                    width = w,
                    height = h,
                    runtime = runtime,
                    factionColor = factionColor(entity.faction, viewedFaction)
                )
                if (selected) {
                    shape.color = selectionSoftColor
                    shape.rect(left - 5f, top - 5f, w + 10f, h + 10f)
                    shape.color = selectionColor
                    shape.rect(left - 3f, top - 3f, w + 6f, h + 6f)
                }
            } else {
                shape.color = Color(0f, 0f, 0f, 0.20f)
                shape.circle(screenX + 2.5f, screenY + 3.5f, if (selected) 8.5f else 7f)
                if (runtime.isDamageFlashActive(entity.id)) {
                    shape.color = impactFlashColor
                    shape.circle(screenX, screenY, if (selected) 15.5f else 13f)
                    shape.color = impactSparkColor
                    shape.rect(screenX - 1.5f, screenY - 9f, 3f, 18f)
                    shape.rect(screenX - 9f, screenY - 1.5f, 18f, 3f)
                }
                if (selected) {
                    val pulse = selectionPulse()
                    shape.color = Color(selectionColor.r, selectionColor.g, selectionColor.b, 0.14f + (pulse * 0.12f))
                    shape.circle(screenX, screenY, 14f)
                }
                drawUnitSilhouette(shape, entity, screenX, screenY, factionColor(entity.faction, viewedFaction), selected)
                if (selected) {
                    shape.color = selectionSoftColor
                    shape.circle(screenX, screenY, 11f)
                }
            }
            drawHealthBar(shape, screenX, screenY, entity.hp, entity.maxHp, selected)
        }
    }

    private fun drawHealthBar(shape: ShapeRenderer, x: Float, y: Float, hp: Int, maxHp: Int, selected: Boolean) {
        val barWidth = if (selected) 22f else 18f
        val barHeight = if (selected) 4f else 3f
        val top = y - if (selected) 16f else 14f
        shape.color = if (selected) Color(0.04f, 0.04f, 0.04f, 0.98f) else Color(0.1f, 0.1f, 0.1f, 0.92f)
        shape.rect(x - (barWidth / 2f), top, barWidth, barHeight)
        shape.color =
            when {
                hp * 100 >= maxHp * 66 -> Color(0.30f, 0.83f, 0.43f, 1f)
                hp * 100 >= maxHp * 33 -> Color(0.89f, 0.71f, 0.22f, 1f)
                else -> Color(0.84f, 0.29f, 0.29f, 1f)
            }
        shape.rect(x - (barWidth / 2f), top, barWidth * (hp.toFloat() / maxHp.coerceAtLeast(1)), barHeight)
    }

    private fun drawSelectionOverlays(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val entitiesById = snapshot.entities.associateBy { it.id }
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val startX = runtime.camera.worldToScreenX(entity.x)
            val startY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(startX, startY)) continue
            shape.color = Color(0.48f, 0.84f, 0.95f, 1f)
            shape.circle(startX, startY, 11f)
            if (entity.pathRemainingNodes > 0 && entity.pathGoalX != null && entity.pathGoalY != null) {
                shape.line(startX, startY, runtime.camera.worldToScreenX(entity.pathGoalX + 0.5f), runtime.camera.worldToScreenY(entity.pathGoalY + 0.5f))
            }
            if (entity.rallyX != null && entity.rallyY != null) {
                shape.color = Color(0.55f, 0.93f, 0.55f, 1f)
                shape.line(startX, startY, runtime.camera.worldToScreenX(entity.rallyX), runtime.camera.worldToScreenY(entity.rallyY))
            }
            if (entity.buildTargetId != null) {
                entitiesById[entity.buildTargetId]?.let { target ->
                    shape.color = Color(0.95f, 0.80f, 0.36f, 1f)
                    shape.line(startX, startY, runtime.camera.worldToScreenX(target.x), runtime.camera.worldToScreenY(target.y))
                }
            }
        }
    }

    private fun drawSelectionBrackets(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        shape.color = selectionColor
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val screenX = runtime.camera.worldToScreenX(entity.x)
            val screenY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(screenX, screenY)) continue
            val footprintWidth = entity.footprintWidth
            val footprintHeight = entity.footprintHeight
            if (footprintWidth != null && footprintHeight != null) {
                val tileX = floor(entity.x).toInt()
                val tileY = floor(entity.y).toInt()
                val left = runtime.camera.worldToScreenX(tileX.toFloat()) - 6f
                val top = runtime.camera.worldToScreenY(tileY.toFloat()) - 6f
                val width = footprintWidth * runtime.camera.tileSize + 12f
                val height = footprintHeight * runtime.camera.tileSize + 12f
                val corner = 12f
                shape.line(left, top, left + corner, top)
                shape.line(left, top, left, top + corner)
                shape.line(left + width, top, left + width - corner, top)
                shape.line(left + width, top, left + width, top + corner)
                shape.line(left, top + height, left + corner, top + height)
                shape.line(left, top + height, left, top + height - corner)
                shape.line(left + width, top + height, left + width - corner, top + height)
                shape.line(left + width, top + height, left + width, top + height - corner)
            } else {
                val radius = 12f
                shape.circle(screenX, screenY, radius)
                shape.line(screenX - radius - 3f, screenY, screenX - radius + 2f, screenY)
                shape.line(screenX + radius - 2f, screenY, screenX + radius + 3f, screenY)
                shape.line(screenX, screenY - radius - 3f, screenX, screenY - radius + 2f)
                shape.line(screenX, screenY + radius - 2f, screenX, screenY + radius + 3f)
            }
        }
    }

    private fun drawOrderMarkers(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val entitiesById = snapshot.entities.associateBy { it.id }
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val startX = runtime.camera.worldToScreenX(entity.x)
            val startY = runtime.camera.worldToScreenY(entity.y)
            if (entity.pathRemainingNodes > 0 && entity.pathGoalX != null && entity.pathGoalY != null) {
                val goalX = runtime.camera.worldToScreenX(entity.pathGoalX + 0.5f)
                val goalY = runtime.camera.worldToScreenY(entity.pathGoalY + 0.5f)
                shape.color = Color(0.96f, 0.90f, 0.45f, 0.18f)
                shape.rectLine(startX, startY, goalX, goalY, 2.4f)
                shape.color = Color(0.96f, 0.90f, 0.45f, 0.28f)
                shape.circle(goalX, goalY, 10f)
                shape.color = Color(0.96f, 0.90f, 0.45f, 0.92f)
                shape.circle(goalX, goalY, 4f)
                drawChevronTrail(shape, startX, startY, goalX, goalY, Color(0.98f, 0.92f, 0.58f, 0.72f))
            }
            if (entity.rallyX != null && entity.rallyY != null) {
                val rallyX = runtime.camera.worldToScreenX(entity.rallyX)
                val rallyY = runtime.camera.worldToScreenY(entity.rallyY)
                shape.color = Color(0.37f, 0.90f, 0.52f, 0.16f)
                shape.rectLine(startX, startY, rallyX, rallyY, 2f)
                shape.color = Color(0.37f, 0.90f, 0.52f, 0.25f)
                shape.circle(rallyX, rallyY, 9f)
                shape.color = Color(0.37f, 0.90f, 0.52f, 0.95f)
                shape.rect(rallyX - 3f, rallyY - 3f, 6f, 6f)
                drawChevronTrail(shape, startX, startY, rallyX, rallyY, Color(0.54f, 0.96f, 0.66f, 0.64f))
            }
            if (entity.buildTargetId != null) {
                entitiesById[entity.buildTargetId]?.let { target ->
                    val targetX = runtime.camera.worldToScreenX(target.x)
                    val targetY = runtime.camera.worldToScreenY(target.y)
                    shape.color = Color(0.92f, 0.60f, 0.24f, 0.18f)
                    shape.rectLine(startX, startY, targetX, targetY, 2.2f)
                    shape.color = Color(0.92f, 0.60f, 0.24f, 0.22f)
                    shape.circle(targetX, targetY, 10f)
                    shape.color = Color(0.92f, 0.60f, 0.24f, 0.92f)
                    shape.circle(targetX, targetY, 3.5f)
                    drawChevronTrail(shape, startX, startY, targetX, targetY, Color(0.96f, 0.72f, 0.38f, 0.68f))
                }
            }
        }
    }

    private fun drawActivityMarkers(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        for (entity in snapshot.entities) {
            if (!isEntityVisible(entity, runtime)) continue
            val screenX = runtime.camera.worldToScreenX(entity.x)
            val screenY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(screenX, screenY)) continue
            if (entity.weaponCooldownTicks > 0 && entity.weaponId != null) {
                shape.color = Color(0.98f, 0.54f, 0.22f, 0.18f)
                shape.circle(screenX, screenY, 12f)
                shape.color = Color(0.98f, 0.72f, 0.26f, 0.95f)
                shape.rect(screenX - 7f, screenY + 9f, (entity.weaponCooldownTicks.coerceAtMost(20) / 20f) * 14f, 2f)
                val muzzleX = screenX + directionDx(entity.dir, 8f)
                val muzzleY = screenY + directionDy(entity.dir, 8f)
                shape.color = Color(1.00f, 0.84f, 0.48f, 0.48f)
                shape.circle(muzzleX, muzzleY, 3.5f)
                shape.color = Color(1.00f, 0.63f, 0.28f, 0.78f)
                shape.rectLine(screenX, screenY, screenX + directionDx(entity.dir, 13f), screenY + directionDy(entity.dir, 13f), 1.8f)
            }
            if (entity.pathRemainingNodes > 0) {
                val trailX = screenX - directionDx(entity.dir, 7f)
                val trailY = screenY - directionDy(entity.dir, 7f)
                shape.color = Color(0.74f, 0.90f, 1.00f, 0.14f)
                shape.circle(trailX, trailY, 4.5f)
                shape.color = Color(0.68f, 0.86f, 0.96f, 0.34f)
                shape.rectLine(trailX, trailY, screenX, screenY, 1.4f)
            }
            if (entity.harvestCargoAmount != null && entity.harvestCargoAmount > 0) {
                shape.color =
                    when (entity.harvestCargoKind) {
                        "gas" -> Color(0.50f, 0.96f, 0.78f, 0.74f)
                        else -> Color(0.98f, 0.84f, 0.42f, 0.74f)
                    }
                shape.circle(screenX + 6f, screenY - 6f, 3f)
                shape.color =
                    when (entity.harvestCargoKind) {
                        "gas" -> Color(0.60f, 1.00f, 0.86f, 0.26f)
                        else -> Color(1.00f, 0.92f, 0.62f, 0.26f)
                    }
                shape.circle(screenX + 6f, screenY - 6f, 6f)
            }
            if (entity.activeProductionType != null || entity.productionQueueSize > 0 || entity.activeResearchTech != null || entity.underConstruction) {
                val markerX = screenX + 10f
                val markerY = screenY - 11f
                shape.color = Color(0.08f, 0.09f, 0.11f, 0.90f)
                shape.rect(markerX, markerY, 18f, 5f)
                shape.color =
                    when {
                        entity.activeResearchTech != null -> Color(0.58f, 0.70f, 1.00f, 0.95f)
                        entity.activeProductionType != null || entity.productionQueueSize > 0 -> Color(0.96f, 0.74f, 0.28f, 0.95f)
                        entity.underConstruction -> Color(0.42f, 0.92f, 0.54f, 0.95f)
                        else -> Color(0.78f, 0.78f, 0.78f, 0.95f)
                    }
                val fillRatio =
                    when {
                        entity.underConstruction && entity.constructionTotalTicks != null && entity.constructionRemainingTicks != null ->
                            1f - (entity.constructionRemainingTicks.toFloat() / entity.constructionTotalTicks.coerceAtLeast(1).toFloat())
                        entity.activeProductionType != null && entity.activeProductionRemainingTicks > 0 ->
                            1f - (entity.activeProductionRemainingTicks.coerceAtMost(120).toFloat() / 120f)
                        entity.activeResearchTech != null && entity.activeResearchRemainingTicks > 0 ->
                            1f - (entity.activeResearchRemainingTicks.coerceAtMost(180).toFloat() / 180f)
                        entity.productionQueueSize > 0 -> 0.35f
                        else -> 0.15f
                }.coerceIn(0.08f, 1f)
                shape.rect(markerX + 1f, markerY + 1f, 16f * fillRatio, 3f)
            }
            if (runtime.isDamageFlashActive(entity.id)) {
                shape.color = impactSparkColor
                if (entity.footprintWidth != null && entity.footprintHeight != null) {
                    val tileX = floor(entity.x).toInt()
                    val tileY = floor(entity.y).toInt()
                    val left = runtime.camera.worldToScreenX(tileX.toFloat())
                    val top = runtime.camera.worldToScreenY(tileY.toFloat())
                    val width = entity.footprintWidth * runtime.camera.tileSize
                    val height = entity.footprintHeight * runtime.camera.tileSize
                    shape.rect(left - 2f, top - 2f, width + 4f, 2f)
                    shape.rect(left - 2f, top + height, width + 4f, 2f)
                    shape.rect(left - 2f, top - 2f, 2f, height + 4f)
                    shape.rect(left + width, top - 2f, 2f, height + 4f)
                } else {
                    shape.circle(screenX, screenY, 5f)
                }
            }
        }
    }

    private fun drawFog(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val viewedFaction = runtime.session.state.viewedFaction ?: return
        val visibleTiles = runtime.session.state.visionState?.visibleTiles(viewedFaction) ?: return
        val exploredTiles = runtime.session.state.visionState?.exploredTiles(viewedFaction) ?: emptySet()
        val tileSize = runtime.camera.tileSize
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                if ((x to y) in visibleTiles) continue
                shape.color = if ((x to y) in exploredTiles) fogColor else shroudColor
                shape.rect(runtime.camera.worldToScreenX(x.toFloat()), runtime.camera.worldToScreenY(y.toFloat()), tileSize, tileSize)
            }
        }
    }

    private fun drawMiniMap(shape: ShapeRenderer, runtime: GdxClientRuntime, width: Int, height: Int) {
        val snapshot = runtime.snapshot ?: return
        val bounds = gdxMiniMapBounds(width, height)
        val boundsWidth = bounds.width
        val boundsHeight = bounds.height
        val left = bounds.left
        val top = bounds.top
        shape.color = Color(0.05f, 0.09f, 0.11f, 0.95f)
        shape.rect(left, top, boundsWidth, boundsHeight)
        val viewedFaction = runtime.session.state.viewedFaction
        val visibleTiles = viewedFaction?.let { runtime.session.state.visionState?.visibleTiles(it) }
        val exploredTiles = viewedFaction?.let { runtime.session.state.visionState?.exploredTiles(it) }
        val tileWidth = boundsWidth / snapshot.mapWidth
        val tileHeight = boundsHeight / snapshot.mapHeight
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                shape.color =
                    when {
                        x in 40..56 && y in 40..56 -> terrainDust.cpy().lerp(Color.BLACK, 0.15f)
                        (x + y) % 11 < 3 -> terrainRidge.cpy().lerp(Color.BLACK, 0.10f)
                        (x / 6 + y / 6) % 2 == 0 -> terrainA.cpy().lerp(Color.BLACK, 0.10f)
                        else -> terrainB.cpy().lerp(Color.BLACK, 0.10f)
                    }
                shape.rect(left + (x * tileWidth), top + (y * tileHeight), tileWidth + 0.4f, tileHeight + 0.4f)
            }
        }
        if (visibleTiles != null) {
            for (x in 0 until snapshot.mapWidth) {
                for (y in 0 until snapshot.mapHeight) {
                    if ((x to y) in visibleTiles) continue
                    shape.color = if (exploredTiles != null && (x to y) in exploredTiles) minimapFogColor else minimapShroudColor
                    shape.rect(left + (x * tileWidth), top + (y * tileHeight), tileWidth + 0.4f, tileHeight + 0.4f)
                }
            }
        }
        runtime.session.state.mapState?.blockedTiles?.forEach { (x, y) ->
            shape.color = Color(0.30f, 0.34f, 0.38f, 0.95f)
            shape.rect(left + (x * tileWidth), top + (y * tileHeight), tileWidth + 0.4f, tileHeight + 0.4f)
        }
        for (node in snapshot.resourceNodes) {
            val nodeX = left + (node.x / snapshot.mapWidth) * boundsWidth
            val nodeY = top + (node.y / snapshot.mapHeight) * boundsHeight
            shape.color = if (node.kind == "gas") Color(0.28f, 0.88f, 0.64f, 0.95f) else Color(0.90f, 0.81f, 0.42f, 0.95f)
            shape.rect(nodeX - 1f, nodeY - 1f, 3f, 3f)
        }
        for (entity in snapshot.entities) {
            val x = left + (entity.x / snapshot.mapWidth) * boundsWidth
            val y = top + (entity.y / snapshot.mapHeight) * boundsHeight
            val visible = visibleTiles == null || isEntityVisible(entity, runtime)
            shape.color = factionColor(entity.faction, viewedFaction).cpy().apply { a = if (visible) 1f else 0.28f }
            val size = if (entity.id in runtime.session.state.selectedIds) 5f else 4f
            shape.rect(x - (size / 2f), y - (size / 2f), size, size)
            if (runtime.isDamageFlashActive(entity.id)) {
                shape.color = Color(1.00f, 0.48f, 0.30f, if (visible) 0.34f else 0.16f)
                shape.rect(x - 5f, y - 5f, 10f, 10f)
                shape.color = impactSparkColor.cpy().apply { a = if (visible) 0.95f else 0.45f }
                shape.rect(x - 3f, y - 3f, 6f, 6f)
            }
            if (entity.id in runtime.session.state.selectedIds) {
                shape.color = Color(0.95f, 0.97f, 1f, if (visible) 0.95f else 0.40f)
                shape.rect(x - 4.5f, y - 4.5f, 9f, 1f)
                shape.rect(x - 4.5f, y + 3.5f, 9f, 1f)
                shape.rect(x - 4.5f, y - 4.5f, 1f, 9f)
                shape.rect(x + 3.5f, y - 4.5f, 1f, 9f)
            }
        }
        runtime.currentGroundPing()?.let { ping ->
            val x = left + (ping.worldX / snapshot.mapWidth) * boundsWidth
            val y = top + (ping.worldY / snapshot.mapHeight) * boundsHeight
            shape.color =
                when (ping.kind) {
                    GroundPingKind.MOVE -> Color(0.38f, 0.92f, 0.56f, 0.75f)
                    GroundPingKind.ATTACK -> Color(1.00f, 0.58f, 0.30f, 0.82f)
                    GroundPingKind.BUILD -> Color(0.56f, 0.82f, 1.00f, 0.82f)
                    GroundPingKind.INVALID -> Color(0.96f, 0.30f, 0.30f, 0.85f)
                }
            shape.circle(x, y, 5f)
        }
    }

    private fun drawMiniMapViewport(shape: ShapeRenderer, runtime: GdxClientRuntime, width: Int, height: Int) {
        val snapshot = runtime.snapshot ?: return
        val bounds = gdxMiniMapBounds(width, height)
        val boundsWidth = bounds.width
        val boundsHeight = bounds.height
        val left = bounds.left
        val top = bounds.top
        val leftWorld = runtime.camera.screenToWorldX(0f).coerceIn(0f, snapshot.mapWidth.toFloat())
        val rightWorld = runtime.camera.screenToWorldX(width.toFloat()).coerceIn(0f, snapshot.mapWidth.toFloat())
        val topWorld = runtime.camera.screenToWorldY(0f).coerceIn(0f, snapshot.mapHeight.toFloat())
        val bottomWorld = runtime.camera.screenToWorldY(height.toFloat()).coerceIn(0f, snapshot.mapHeight.toFloat())
        val viewportLeft = left + (leftWorld / snapshot.mapWidth) * boundsWidth
        val viewportTop = top + (topWorld / snapshot.mapHeight) * boundsHeight
        val viewportWidth = ((rightWorld - leftWorld) / snapshot.mapWidth) * boundsWidth
        val viewportHeight = ((bottomWorld - topWorld) / snapshot.mapHeight) * boundsHeight
        shape.color = Color(0.82f, 0.92f, 0.98f, 0.12f)
        shape.rect(viewportLeft, viewportTop, viewportWidth, viewportHeight)
        shape.color = Color(0.10f, 0.18f, 0.22f, 0.95f)
        shape.rect(left, top, boundsWidth, boundsHeight)
        shape.color = Color(0.92f, 0.98f, 1f, 0.90f)
        shape.rect(
            viewportLeft - 1f,
            viewportTop - 1f,
            viewportWidth + 2f,
            viewportHeight + 2f
        )
        shape.rect(
            viewportLeft - 2f,
            viewportTop - 2f,
            viewportWidth + 4f,
            viewportHeight + 4f
        )
        val corner = 8f
        shape.color = selectionColor
        shape.line(viewportLeft, viewportTop, viewportLeft + corner, viewportTop)
        shape.line(viewportLeft, viewportTop, viewportLeft, viewportTop + corner)
        shape.line(viewportLeft + viewportWidth, viewportTop, viewportLeft + viewportWidth - corner, viewportTop)
        shape.line(viewportLeft + viewportWidth, viewportTop, viewportLeft + viewportWidth, viewportTop + corner)
        shape.line(viewportLeft, viewportTop + viewportHeight, viewportLeft + corner, viewportTop + viewportHeight)
        shape.line(viewportLeft, viewportTop + viewportHeight, viewportLeft, viewportTop + viewportHeight - corner)
        shape.line(
            viewportLeft + viewportWidth,
            viewportTop + viewportHeight,
            viewportLeft + viewportWidth - corner,
            viewportTop + viewportHeight
        )
        shape.line(
            viewportLeft + viewportWidth,
            viewportTop + viewportHeight,
            viewportLeft + viewportWidth,
            viewportTop + viewportHeight - corner
        )
    }

    private fun drawBuildPreview(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState ?: return
        val typeId = runtime.buildModeTypeId ?: return
        val spec = buildPreviewSpec(typeId) ?: return
        val tileX = floor(runtime.camera.screenToWorldX(Gdx.input.x.toFloat())).toInt()
        val tileY = floor(runtime.camera.screenToWorldY(Gdx.input.y.toFloat())).toInt()
        val valid = isBuildPreviewValid(mapState, snapshot, spec, tileX, tileY)
        shape.color = if (valid) Color(0.29f, 0.84f, 0.49f, 0.35f) else Color(0.86f, 0.30f, 0.30f, 0.35f)
        shape.rect(
            runtime.camera.worldToScreenX(tileX.toFloat()),
            runtime.camera.worldToScreenY(tileY.toFloat()),
            spec.width * runtime.camera.tileSize,
            spec.height * runtime.camera.tileSize
        )
    }

    private fun drawGroundPing(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val ping = runtime.currentGroundPing() ?: return
        val x = runtime.camera.worldToScreenX(ping.worldX)
        val y = runtime.camera.worldToScreenY(ping.worldY)
        val pulse = ambientPulse(900L)
        when (ping.kind) {
            GroundPingKind.MOVE -> {
                shape.color = Color(0.34f, 0.92f, 0.54f, 0.22f + (pulse * 0.10f))
                shape.circle(x, y, 16f + (pulse * 8f))
                shape.color = Color(0.58f, 0.98f, 0.70f, 0.90f)
                shape.rect(x - 2f, y - 10f, 4f, 20f)
                shape.rect(x - 10f, y - 2f, 20f, 4f)
            }
            GroundPingKind.ATTACK -> {
                shape.color = Color(0.98f, 0.42f, 0.24f, 0.22f + (pulse * 0.10f))
                shape.circle(x, y, 18f + (pulse * 10f))
                shape.color = Color(1.00f, 0.76f, 0.46f, 0.96f)
                shape.rectLine(x - 10f, y - 10f, x + 10f, y + 10f, 2.2f)
                shape.rectLine(x - 10f, y + 10f, x + 10f, y - 10f, 2.2f)
            }
            GroundPingKind.BUILD -> {
                shape.color = Color(0.48f, 0.78f, 1.00f, 0.22f + (pulse * 0.10f))
                shape.rect(x - 14f - (pulse * 2f), y - 14f - (pulse * 2f), 28f + (pulse * 4f), 28f + (pulse * 4f))
                shape.color = Color(0.72f, 0.90f, 1.00f, 0.90f)
                shape.rect(x - 8f, y - 8f, 16f, 16f)
            }
            GroundPingKind.INVALID -> {
                shape.color = Color(0.96f, 0.28f, 0.28f, 0.18f + (pulse * 0.08f))
                shape.circle(x, y, 18f + (pulse * 8f))
                shape.color = Color(1.00f, 0.70f, 0.70f, 0.92f)
                shape.rectLine(x - 9f, y - 9f, x + 9f, y + 9f, 2f)
                shape.rectLine(x - 9f, y + 9f, x + 9f, y - 9f, 2f)
            }
        }
    }

    private fun drawSelectionBox(shape: ShapeRenderer, dragBox: DragSelectionBox?) {
        if (dragBox == null || !dragBox.isVisible) return
        val minX = minOf(dragBox.startX, dragBox.currentX)
        val minY = minOf(dragBox.startY, dragBox.currentY)
        shape.color = Color(0.96f, 0.89f, 0.45f, 0.18f)
        shape.rect(minX, minY, abs(dragBox.currentX - dragBox.startX), abs(dragBox.currentY - dragBox.startY))
    }

    private fun drawLabels(runtime: GdxClientRuntime, width: Int, height: Int) {
        val snapshot = runtime.snapshot ?: return
        val batch = assets.batch
        textCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        textCamera.update()
        batch.projectionMatrix = textCamera.combined
        batch.begin()
        for (node in snapshot.resourceNodes) {
            assets.font.color = Color.WHITE
            assets.font.draw(
                batch,
                node.remaining.toString(),
                runtime.camera.worldToScreenX(node.x) - 8f,
                height - runtime.camera.worldToScreenY(node.y) - 10f
            )
        }
        runtime.buildModeTypeId?.let { typeId ->
            val spec = buildPreviewSpec(typeId)
            val tileX = floor(runtime.camera.screenToWorldX(Gdx.input.x.toFloat())).toInt()
            val tileY = floor(runtime.camera.screenToWorldY(Gdx.input.y.toFloat())).toInt()
            val valid = isBuildPreviewValid(runtime.session.state.mapState, snapshot, spec, tileX, tileY)
            val label = buildPreviewLabel(spec, valid)
            if (label != null) {
                assets.font.color = if (label.valid) Color.WHITE else Color.SCARLET
                assets.font.draw(batch, "${label.title} ${label.cost} ${label.size}", 18f, height - 22f)
            }
        }
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val status = buildEntityStatusLabel(entity) ?: continue
            assets.font.color = Color(0.94f, 0.96f, 0.98f, 1f)
            assets.font.draw(
                batch,
                status,
                runtime.camera.worldToScreenX(entity.x) + 10f,
                height - runtime.camera.worldToScreenY(entity.y) + 14f
            )
        }
        if (snapshot.matchEnded) {
            assets.font.color = Color(1f, 0.86f, 0.63f, 1f)
            val state = buildGameState(snapshot, runtime.session.state.viewedFaction)
            assets.font.draw(batch, state?.title ?: "Match Ended", width / 2f - 70f, height - 48f)
            assets.font.draw(batch, state?.detail ?: "", width / 2f - 90f, height - 68f)
        }
        batch.end()
    }

    private fun factionColor(faction: Int, viewedFaction: Int?): Color =
        when {
            faction <= 0 -> neutralColor
            viewedFaction == null -> if (faction == 1) friendlyColor else enemyColor
            faction == viewedFaction -> friendlyColor
            else -> enemyColor
        }

    private fun drawUnitSilhouette(
        shape: ShapeRenderer,
        entity: EntitySnapshot,
        screenX: Float,
        screenY: Float,
        factionColor: Color,
        selected: Boolean
    ) {
        val bobY = unitBob(entity.id, if (selected) 0.9f else 0.6f)
        val x = screenX
        val y = screenY + bobY
        val body = Color(0.17f, 0.19f, 0.22f, 1f)
        val teamStripe = factionColor.cpy().lerp(Color.WHITE, 0.08f)
        val shadowRadius = if (selected) 9f else 7f
        shape.color = Color(0f, 0f, 0f, 0.34f)
        shape.circle(x + 1.5f, screenY + 1.5f, shadowRadius)
        when {
            entity.archetype == "worker" -> {
                shape.color = body
                shape.circle(x, y, 6.5f)
                shape.color = teamStripe
                shape.rect(x - 5f, y - 2f, 10f, 4f)
                shape.color = Color(0.94f, 0.96f, 0.98f, 0.65f)
                shape.rectLine(x, y, x + directionDx(entity.dir, 5.5f), y + directionDy(entity.dir, 5.5f), 1.4f)
                shape.color = Color.WHITE.cpy().apply { a = 0.18f }
                shape.circle(x - 1f, y - 1f, 2.2f)
            }
            entity.weaponId != null -> {
                shape.color = body
                shape.rect(x - 3.5f, y - 6.5f, 7f, 13f)
                shape.rect(x - 6.5f, y - 1.8f, 13f, 3.6f)
                shape.color = teamStripe
                shape.rect(x - 2.5f, y - 5.5f, 5f, 11f)
                shape.color = Color(0.98f, 0.94f, 0.74f, 0.72f)
                shape.rectLine(x, y, x + directionDx(entity.dir, 7.5f), y + directionDy(entity.dir, 7.5f), 1.8f)
                if (entity.weaponCooldownTicks > 0) {
                    shape.color = Color(1.00f, 0.68f, 0.32f, 0.40f)
                    shape.rectLine(x - directionDx(entity.dir, 4f), y - directionDy(entity.dir, 4f), x, y, 2.4f)
                }
                shape.color = Color.WHITE.cpy().apply { a = 0.18f }
                shape.rect(x - 1.5f, y - 4.5f, 3f, 4f)
            }
            else -> {
                shape.color = body
                shape.rect(x - 5.5f, y - 4.5f, 11f, 9f)
                shape.color = teamStripe
                shape.rect(x - 4.5f, y - 3.5f, 9f, 7f)
                shape.color = Color(0.85f, 0.92f, 0.98f, 0.58f)
                shape.rectLine(x, y, x + directionDx(entity.dir, 6f), y + directionDy(entity.dir, 6f), 1.5f)
                shape.color = Color.WHITE.cpy().apply { a = 0.14f }
                shape.rect(x - 3.5f, y - 2.5f, 4.5f, 2.5f)
            }
        }
    }

    private fun drawStructureSilhouette(
        shape: ShapeRenderer,
        entity: EntitySnapshot,
        tileX: Int,
        tileY: Int,
        width: Float,
        height: Float,
        runtime: GdxClientRuntime,
        factionColor: Color
    ) {
        val left = runtime.camera.worldToScreenX(tileX.toFloat())
        val top = runtime.camera.worldToScreenY(tileY.toFloat())
        val shell = Color(0.18f, 0.20f, 0.22f, 1f)
        val roof = Color(0.24f, 0.27f, 0.30f, 1f)
        val isResourceDepot = entity.typeId.contains("ResourceDepot", ignoreCase = true)
        val isGasDepot = entity.typeId.contains("GasDepot", ignoreCase = true)
        val isDepot = entity.typeId.contains("Depot", ignoreCase = true) && !isResourceDepot && !isGasDepot
        shape.color = Color(0.08f, 0.10f, 0.12f, 0.74f)
        shape.rect(left - 3f, top - 3f, width + 6f, height + 6f)
        shape.color = Color(0.14f, 0.18f, 0.20f, 0.64f)
        shape.rect(left - 1f, top + height - 2f, width + 2f, 5f)
        shape.color = shell
        shape.rect(left, top, width, height)
        shape.color = roof
        shape.rect(left + 3f, top + 3f, width - 6f, height - 6f)
        shape.color =
            when {
                isGasDepot -> Color(0.26f, 0.82f, 0.60f, 0.95f)
                isResourceDepot -> Color(0.92f, 0.78f, 0.36f, 0.95f)
                else -> factionColor.cpy().lerp(Color.WHITE, 0.10f)
            }
        shape.rect(left + 4f, top + 4f, width - 8f, (height * 0.22f).coerceAtLeast(5f))
        shape.color = Color(0.82f, 0.88f, 0.93f, 0.10f)
        shape.rect(left + 6f, top + 6f, (width * 0.32f).coerceAtLeast(6f), (height * 0.18f).coerceAtLeast(4f))
        if (isDepot) {
            shape.color = Color(0.95f, 0.82f, 0.36f, 0.88f)
            shape.rect(left + width - 10f, top + 5f, 5f, 5f)
            shape.color = Color(0.88f, 0.70f, 0.24f, 0.42f)
            shape.rect(left + width * 0.25f, top + height - 6f, width * 0.5f, 3f)
        }
        if (isResourceDepot) {
            shape.color = Color(0.95f, 0.86f, 0.42f, 0.88f)
            shape.rect(left + width - 13f, top + height - 12f, 8f, 6f)
            shape.rect(left + 6f, top + height - 12f, 8f, 6f)
            shape.color = Color(0.96f, 0.84f, 0.42f, 0.26f)
            shape.rect(left + width * 0.18f, top + height - 5f, width * 0.64f, 3f)
        }
        if (isGasDepot) {
            shape.color = Color(0.52f, 0.98f, 0.78f, 0.82f)
            shape.circle(left + width * 0.5f, top + height * 0.55f, 6f)
            shape.color = Color(0.40f, 0.90f, 0.72f, 0.28f + (ambientPulse(1600L) * 0.12f))
            shape.circle(left + width * 0.5f, top + height * 0.55f, 9f)
        }
        if (entity.supportsResearch == true) {
            shape.color = Color(0.66f, 0.74f, 1.00f, 0.82f)
            shape.rect(left + width - 11f, top + height - 11f, 6f, 6f)
        }
        if (entity.supportsTraining == true) {
            shape.color = Color(0.97f, 0.68f, 0.28f, 0.82f)
            shape.rect(left + 5f, top + height - 11f, 6f, 6f)
        }
        if (entity.underConstruction) {
            shape.color = Color(0.78f, 0.64f, 0.30f, 0.35f)
            shape.rect(left + 2f, top + 2f, width - 4f, height - 4f)
            val stripeCount = (width / 8f).toInt().coerceAtLeast(2)
            shape.color = Color(0.92f, 0.76f, 0.40f, 0.55f)
            for (i in 0..stripeCount) {
                val stripeX = left + 4f + (i * ((width - 8f) / stripeCount))
                shape.rect(stripeX, top + 4f, 2f, height - 8f)
            }
        }
        if (entity.activeProductionType != null || entity.productionQueueSize > 0) {
            shape.color = Color(0.98f, 0.76f, 0.34f, 0.18f + (ambientPulse(1100L) * 0.10f))
            shape.rect(left + width - 14f, top + 6f, 8f, height - 12f)
        }
        if (entity.activeResearchTech != null) {
            shape.color = Color(0.62f, 0.76f, 1.00f, 0.18f + (ambientPulse(1200L) * 0.10f))
            shape.rect(left + 6f, top + 6f, 8f, height - 12f)
        }
    }

    private fun selectionPulse(): Float {
        val phase = (System.currentTimeMillis() % 900L).toFloat() / 900f
        return if (phase <= 0.5f) phase * 2f else (1f - phase) * 2f
    }

    private fun ambientPulse(periodMillis: Long): Float {
        val phase = (System.currentTimeMillis() % periodMillis).toFloat() / periodMillis.toFloat()
        return if (phase <= 0.5f) phase * 2f else (1f - phase) * 2f
    }

    private fun unitBob(entityId: Int, amplitude: Float): Float {
        val phase = ((System.currentTimeMillis() % 1400L).toFloat() / 1400f) + ((entityId % 11) * 0.07f)
        return kotlin.math.sin(phase * Math.PI * 2.0).toFloat() * amplitude
    }

    private fun drawChevronTrail(shape: ShapeRenderer, startX: Float, startY: Float, endX: Float, endY: Float, color: Color) {
        val dx = endX - startX
        val dy = endY - startY
        val length = kotlin.math.sqrt((dx * dx) + (dy * dy))
        if (length < 26f) return
        val nx = dx / length
        val ny = dy / length
        val px = -ny
        val py = nx
        val chevronSpacing = 18f
        val chevronSize = 5f
        val count = (length / chevronSpacing).toInt().coerceAtMost(7)
        shape.color = color
        for (i in 1..count) {
            val t = i / (count + 1f)
            val cx = startX + (dx * t)
            val cy = startY + (dy * t)
            shape.rectLine(cx - (nx * chevronSize) + (px * chevronSize), cy - (ny * chevronSize) + (py * chevronSize), cx, cy, 1.4f)
            shape.rectLine(cx - (nx * chevronSize) - (px * chevronSize), cy - (ny * chevronSize) - (py * chevronSize), cx, cy, 1.4f)
        }
    }

    private fun directionDx(dir: Float, scale: Float): Float = kotlin.math.cos(dir) * scale

    private fun directionDy(dir: Float, scale: Float): Float = kotlin.math.sin(dir) * scale

    private fun drawWorldFrame(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val left = runtime.camera.worldToScreenX(0f)
        val top = runtime.camera.worldToScreenY(0f)
        val right = runtime.camera.worldToScreenX(snapshot.mapWidth.toFloat())
        val bottom = runtime.camera.worldToScreenY(snapshot.mapHeight.toFloat())
        shape.color = mapFrameColor
        shape.rect(left, top, right - left, bottom - top)
    }

    private fun isOnScreen(screenX: Float, screenY: Float): Boolean =
        screenX >= -32f &&
            screenX <= Gdx.graphics.width + 32f &&
            screenY >= -32f &&
            screenY <= Gdx.graphics.height + 32f

    private fun isEntityVisible(entity: EntitySnapshot, runtime: GdxClientRuntime): Boolean {
        val viewedFaction = runtime.session.state.viewedFaction ?: return true
        val visibleTiles = runtime.session.state.visionState?.visibleTiles(viewedFaction) ?: return true
        if (entity.footprintWidth != null && entity.footprintHeight != null) {
            val tileX = floor(entity.x).toInt()
            val tileY = floor(entity.y).toInt()
            for (x in tileX until (tileX + entity.footprintWidth)) {
                for (y in tileY until (tileY + entity.footprintHeight)) {
                    if ((x to y) in visibleTiles) return true
                }
            }
            return false
        }
        return (floor(entity.x).toInt() to floor(entity.y).toInt()) in visibleTiles
    }
}

internal data class DragSelectionBox(
    val startX: Float,
    val startY: Float,
    val currentX: Float,
    val currentY: Float,
    val isVisible: Boolean
)

internal data class GdxMiniMapBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    fun contains(x: Float, y: Float): Boolean =
        x in left..(left + width) && y in top..(top + height)
}

internal fun gdxMiniMapBounds(screenWidth: Int, screenHeight: Int): GdxMiniMapBounds =
    GdxMiniMapBounds(
        left = 20f,
        top = screenHeight - minOf(184, screenHeight / 5).toFloat() - 20f,
        width = minOf(220, screenWidth / 5).toFloat(),
        height = minOf(184, screenHeight / 5).toFloat()
    )

internal fun gdxMiniMapWorldPosition(
    screenX: Float,
    screenY: Float,
    screenWidth: Int,
    screenHeight: Int,
    snapshot: ClientSnapshot
): Pair<Float, Float>? {
    val bounds = gdxMiniMapBounds(screenWidth, screenHeight)
    if (!bounds.contains(screenX, screenY)) return null
    val worldX = (((screenX - bounds.left) / bounds.width) * snapshot.mapWidth).coerceIn(0f, snapshot.mapWidth.toFloat())
    val worldY = (((screenY - bounds.top) / bounds.height) * snapshot.mapHeight).coerceIn(0f, snapshot.mapHeight.toFloat())
    return worldX to worldY
}
