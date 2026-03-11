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
    private val minimapFogColor = Color(0.01f, 0.03f, 0.05f, 0.72f)
    private val terrainA = Color(0.08f, 0.12f, 0.15f, 1f)
    private val terrainB = Color(0.10f, 0.15f, 0.18f, 1f)
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
        drawOrderMarkers(shape, runtime)
        drawMiniMap(shape, runtime, width, height)
        drawBuildPreview(shape, runtime)
        drawSelectionBox(shape, dragBox)
        shape.end()

        shape.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(shape, runtime)
        drawWorldFrame(shape, runtime)
        drawSelectionOverlays(shape, runtime)
        drawMiniMapViewport(shape, runtime, width, height)
        shape.end()

        drawLabels(runtime, width, height)
    }

    private fun drawTerrain(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState
        shape.color = terrainA
        shape.rect(0f, 0f, runtime.camera.worldToScreenX(snapshot.mapWidth.toFloat()), runtime.camera.worldToScreenY(snapshot.mapHeight.toFloat()))
        val bandSize = runtime.camera.tileSize * 4f
        var row = 0
        var bandY = 0f
        while (bandY < runtime.camera.worldToScreenY(snapshot.mapHeight.toFloat())) {
            shape.color = if (row % 2 == 0) terrainB else terrainA
            shape.rect(0f, bandY, runtime.camera.worldToScreenX(snapshot.mapWidth.toFloat()), bandSize)
            bandY += bandSize
            row++
        }
        mapState?.blockedTiles?.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = Color(0.16f, 0.16f, 0.18f, 1f)
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
        }
        mapState?.staticOccupancyTiles?.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = Color(0.29f, 0.21f, 0.16f, 0.78f)
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
        }
    }

    private fun drawGrid(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        shape.color = Color(0.18f, 0.23f, 0.28f, 0.8f)
        for (x in 0..snapshot.mapWidth) {
            val px = runtime.camera.worldToScreenX(x.toFloat())
            shape.line(px, 0f, px, runtime.camera.worldToScreenY(snapshot.mapHeight.toFloat()))
        }
        for (y in 0..snapshot.mapHeight) {
            val py = runtime.camera.worldToScreenY(y.toFloat())
            shape.line(0f, py, runtime.camera.worldToScreenX(snapshot.mapWidth.toFloat()), py)
        }
    }

    private fun drawResources(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        for (node in snapshot.resourceNodes) {
            shape.color = if (node.kind == "gas") Color(0.24f, 0.77f, 0.57f, 1f) else neutralColor
            shape.circle(runtime.camera.worldToScreenX(node.x), runtime.camera.worldToScreenY(node.y), 7f)
            shape.color = Color(1f, 1f, 1f, 0.12f)
            shape.circle(runtime.camera.worldToScreenX(node.x), runtime.camera.worldToScreenY(node.y), 11f)
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
                shape.color = Color(0f, 0f, 0f, 0.28f)
                shape.rect(runtime.camera.worldToScreenX(tileX.toFloat()) + 3f, runtime.camera.worldToScreenY(tileY.toFloat()) + 3f, w, h)
                shape.color = factionColor(entity.faction, viewedFaction)
                shape.rect(runtime.camera.worldToScreenX(tileX.toFloat()), runtime.camera.worldToScreenY(tileY.toFloat()), w, h)
                shape.color = Color(1f, 1f, 1f, 0.10f)
                shape.rect(runtime.camera.worldToScreenX(tileX.toFloat()) + 2f, runtime.camera.worldToScreenY(tileY.toFloat()) + 2f, w - 4f, h - 4f)
                if (selected) {
                    shape.color = selectionSoftColor
                    shape.rect(runtime.camera.worldToScreenX(tileX.toFloat()) - 5f, runtime.camera.worldToScreenY(tileY.toFloat()) - 5f, w + 10f, h + 10f)
                    shape.color = selectionColor
                    shape.rect(runtime.camera.worldToScreenX(tileX.toFloat()) - 3f, runtime.camera.worldToScreenY(tileY.toFloat()) - 3f, w + 6f, h + 6f)
                }
            } else {
                shape.color = Color(0f, 0f, 0f, 0.32f)
                shape.circle(screenX + 1.5f, screenY + 1.5f, if (selected) 9f else 7f)
                shape.color = factionColor(entity.faction, viewedFaction)
                val radius = if (selected) 8f else 6f
                shape.circle(screenX, screenY, radius)
                shape.color = Color(1f, 1f, 1f, if (selected) 0.30f else 0.16f)
                shape.circle(screenX - 1.5f, screenY - 1.5f, radius * 0.45f)
                if (selected) {
                    shape.color = selectionSoftColor
                    shape.circle(screenX, screenY, 11f)
                }
            }
            drawHealthBar(shape, screenX, screenY, entity.hp, entity.maxHp)
        }
    }

    private fun drawHealthBar(shape: ShapeRenderer, x: Float, y: Float, hp: Int, maxHp: Int) {
        val barWidth = 18f
        val top = y - 14f
        shape.color = Color(0.1f, 0.1f, 0.1f, 0.92f)
        shape.rect(x - (barWidth / 2f), top, barWidth, 3f)
        shape.color =
            when {
                hp * 100 >= maxHp * 66 -> Color(0.30f, 0.83f, 0.43f, 1f)
                hp * 100 >= maxHp * 33 -> Color(0.89f, 0.71f, 0.22f, 1f)
                else -> Color(0.84f, 0.29f, 0.29f, 1f)
            }
        shape.rect(x - (barWidth / 2f), top, barWidth * (hp.toFloat() / maxHp.coerceAtLeast(1)), 3f)
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

    private fun drawOrderMarkers(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val entitiesById = snapshot.entities.associateBy { it.id }
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            if (entity.pathRemainingNodes > 0 && entity.pathGoalX != null && entity.pathGoalY != null) {
                val goalX = runtime.camera.worldToScreenX(entity.pathGoalX + 0.5f)
                val goalY = runtime.camera.worldToScreenY(entity.pathGoalY + 0.5f)
                shape.color = Color(0.96f, 0.90f, 0.45f, 0.28f)
                shape.circle(goalX, goalY, 10f)
                shape.color = Color(0.96f, 0.90f, 0.45f, 0.92f)
                shape.circle(goalX, goalY, 4f)
            }
            if (entity.rallyX != null && entity.rallyY != null) {
                val rallyX = runtime.camera.worldToScreenX(entity.rallyX)
                val rallyY = runtime.camera.worldToScreenY(entity.rallyY)
                shape.color = Color(0.37f, 0.90f, 0.52f, 0.25f)
                shape.circle(rallyX, rallyY, 9f)
                shape.color = Color(0.37f, 0.90f, 0.52f, 0.95f)
                shape.rect(rallyX - 3f, rallyY - 3f, 6f, 6f)
            }
            if (entity.buildTargetId != null) {
                entitiesById[entity.buildTargetId]?.let { target ->
                    val targetX = runtime.camera.worldToScreenX(target.x)
                    val targetY = runtime.camera.worldToScreenY(target.y)
                    shape.color = Color(0.92f, 0.60f, 0.24f, 0.22f)
                    shape.circle(targetX, targetY, 10f)
                    shape.color = Color(0.92f, 0.60f, 0.24f, 0.92f)
                    shape.circle(targetX, targetY, 3.5f)
                }
            }
        }
    }

    private fun drawFog(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val viewedFaction = runtime.session.state.viewedFaction ?: return
        val visibleTiles = runtime.session.state.visionState?.visibleTiles(viewedFaction) ?: return
        val tileSize = runtime.camera.tileSize
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                if ((x to y) in visibleTiles) continue
                shape.color = fogColor
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
        shape.color = Color(0.16f, 0.25f, 0.29f, 0.70f)
        shape.rect(left + 4f, top + 4f, boundsWidth - 8f, boundsHeight - 8f)
        val viewedFaction = runtime.session.state.viewedFaction
        val visibleTiles = viewedFaction?.let { runtime.session.state.visionState?.visibleTiles(it) }
        val tileWidth = boundsWidth / snapshot.mapWidth
        val tileHeight = boundsHeight / snapshot.mapHeight
        if (visibleTiles != null) {
            for (x in 0 until snapshot.mapWidth) {
                for (y in 0 until snapshot.mapHeight) {
                    if ((x to y) in visibleTiles) continue
                    shape.color = minimapFogColor
                    shape.rect(left + (x * tileWidth), top + (y * tileHeight), tileWidth + 0.4f, tileHeight + 0.4f)
                }
            }
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
        shape.color = selectionColor
        shape.rect(
            left + (leftWorld / snapshot.mapWidth) * boundsWidth,
            top + (topWorld / snapshot.mapHeight) * boundsHeight,
            ((rightWorld - leftWorld) / snapshot.mapWidth) * boundsWidth,
            ((bottomWorld - topWorld) / snapshot.mapHeight) * boundsHeight
        )
        shape.color = Color(0.10f, 0.18f, 0.22f, 0.95f)
        shape.rect(left, top, boundsWidth, boundsHeight)
        shape.color = Color(0.92f, 0.98f, 1f, 0.90f)
        shape.rect(
            left + (leftWorld / snapshot.mapWidth) * boundsWidth - 1f,
            top + (topWorld / snapshot.mapHeight) * boundsHeight - 1f,
            ((rightWorld - leftWorld) / snapshot.mapWidth) * boundsWidth + 2f,
            ((bottomWorld - topWorld) / snapshot.mapHeight) * boundsHeight + 2f
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
