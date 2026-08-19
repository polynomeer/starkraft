package starkraft.sim.client

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
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
    private val selectionColor = Color(0.62f, 1.00f, 0.56f, 1f)
    private val selectionSoftColor = Color(0.46f, 0.96f, 0.54f, 0.14f)

    private fun pingTone(kind: GroundPingKind): Color =
        when (kind) {
            GroundPingKind.MOVE -> Color(0.34f, 0.98f, 0.58f, 0.94f)
            GroundPingKind.ATTACK -> Color(1.00f, 0.58f, 0.30f, 0.96f)
            GroundPingKind.BUILD -> Color(0.64f, 0.84f, 1.00f, 0.96f)
            GroundPingKind.INVALID -> Color(0.96f, 0.30f, 0.30f, 0.96f)
        }

    private fun activityTone(entity: EntitySnapshot): Color =
        when {
            entity.activeResearchTech != null -> completionResearchSparkColor
            entity.activeProductionType != null || entity.productionQueueSize > 0 -> completionProductionSparkColor
            entity.underConstruction -> completionBuildSparkColor
            else -> Color(0.80f, 0.80f, 0.80f, 0.92f)
        }

    private fun damageFlashAlpha(selected: Boolean): Float = if (selected) 0.10f else 0.14f

    private fun completionFlashAlpha(selected: Boolean): Float = if (selected) 0.10f else 0.12f
    private val fogColor = Color(0.07f, 0.12f, 0.14f, 0.18f)
    private val shroudColor = Color(0.05f, 0.09f, 0.11f, 0.32f)
    private val minimapFogColor = Color(0.05f, 0.09f, 0.11f, 0.14f)
    private val minimapShroudColor = Color(0.04f, 0.08f, 0.10f, 0.26f)
    private val impactFlashColor = Color(1.00f, 0.46f, 0.28f, 0.34f)
    private val impactSparkColor = Color(1.00f, 0.86f, 0.54f, 0.92f)
    private val meleeImpactFlashColor = Color(0.84f, 1.00f, 0.66f, 0.30f)
    private val meleeImpactSparkColor = Color(0.92f, 1.00f, 0.78f, 0.94f)
    private val completionBuildFlashColor = Color(0.58f, 0.96f, 0.72f, 0.26f)
    private val completionBuildSparkColor = Color(0.74f, 1.00f, 0.82f, 0.92f)
    private val completionProductionFlashColor = Color(1.00f, 0.84f, 0.42f, 0.26f)
    private val completionProductionSparkColor = Color(1.00f, 0.92f, 0.62f, 0.92f)
    private val completionResearchFlashColor = Color(0.56f, 0.92f, 1.00f, 0.26f)
    private val completionResearchSparkColor = Color(0.78f, 0.96f, 1.00f, 0.92f)
    private val terrainA = Color(0.10f, 0.15f, 0.11f, 1f)
    private val terrainB = Color(0.12f, 0.18f, 0.13f, 1f)
    private val terrainRidge = Color(0.18f, 0.22f, 0.15f, 1f)
    private val terrainDust = Color(0.22f, 0.19f, 0.13f, 1f)
    private val terrainMetal = Color(0.15f, 0.18f, 0.21f, 1f)
    private val mapFrameColor = Color(0.20f, 0.38f, 0.42f, 0.82f)

    fun render(runtime: GdxClientRuntime, width: Int, height: Int, worldViewportHeight: Int, dragBox: DragSelectionBox?) {
        val snapshot = runtime.snapshot ?: return
        val shape = assets.shapeRenderer
        screenCamera.setToOrtho(true, width.toFloat(), height.toFloat())
        screenCamera.update()
        shape.projectionMatrix = screenCamera.combined

        Gdx.gl.glClearColor(0.06f, 0.09f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        beginWorldScissor(width, height, worldViewportHeight)
        shape.begin(ShapeRenderer.ShapeType.Filled)
        drawTerrain(shape, runtime)
        shape.end()

        renderWorldSprites(runtime)

        shape.begin(ShapeRenderer.ShapeType.Filled)
        drawResources(shape, runtime)
        drawFog(shape, runtime)
        drawEntities(shape, runtime)
        drawActivityMarkers(shape, runtime)
        drawOrderMarkers(shape, runtime)
        drawGroundPing(shape, runtime)
        drawSelectionClickPulse(shape, runtime)
        drawDeathRemains(shape, runtime)
        drawDeathBursts(shape, runtime)
        drawBuildPreview(shape, runtime)
        drawSelectionBox(shape, dragBox)
        shape.end()

        shape.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(shape, runtime)
        drawTerrainEdges(shape, runtime)
        drawWorldFrame(shape, runtime)
        drawSelectionBrackets(shape, runtime)
        drawSelectionOverlays(shape, runtime)
        shape.end()
        endWorldScissor()

        shape.begin(ShapeRenderer.ShapeType.Filled)
        drawMiniMap(shape, runtime, width, height)
        shape.end()

        shape.begin(ShapeRenderer.ShapeType.Line)
        drawMiniMapViewport(shape, runtime, width, height)
        shape.end()

        drawLabels(runtime, width, height)
    }

    private fun beginWorldScissor(width: Int, height: Int, worldViewportHeight: Int) {
        return
    }

    private fun endWorldScissor() {
        return
    }

    private fun renderWorldSprites(runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val batch = assets.batch
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        drawTerrainSprites(batch, runtime)
        drawResourceSprites(batch, runtime)
        drawEntitySprites(batch, runtime, snapshot)
        batch.color = Color.WHITE
        batch.end()
    }

    private fun drawTerrain(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState
        val tileSize = runtime.camera.tileSize
        val terrainSprite = assets.spriteAssets.terrainBase()
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                val sx = runtime.camera.worldToScreenX(x.toFloat())
                val sy = runtime.camera.worldToScreenY(y.toFloat())
                if (terrainSprite != null) continue
                val macroPatch = ((x / 10) + (y / 8)) % 3
                val ridgeBand = ((x * 3) + (y * 2)) % 17
                val terrainDrift = ((x / 14) - (y / 11)) % 4
                val base =
                    when {
                        x in 40..56 && y in 40..56 -> terrainDust
                        ridgeBand < 3 -> terrainRidge
                        macroPatch == 0 -> terrainA
                        macroPatch == 1 && terrainDrift != 0 -> terrainB
                        terrainDrift == 0 -> terrainA.cpy().lerp(terrainDust, 0.18f)
                        else -> terrainB
                    }
                shape.color = base
                shape.rect(sx, sy, tileSize, tileSize)
                shape.color = base.cpy().lerp(Color.WHITE, 0.08f)
                shape.rect(sx + 1f, sy + 1f, tileSize - 2f, (tileSize * 0.22f).coerceAtLeast(2f))
                shape.color = base.cpy().lerp(Color.BLACK, 0.18f)
                shape.rect(sx + 1f, sy + (tileSize * 0.68f), tileSize - 2f, (tileSize * 0.22f).coerceAtLeast(2f))
                if ((x / 3 + y / 2) % 4 == 0) {
                    shape.color = base.cpy().lerp(Color.BLACK, 0.24f).apply { a = 0.18f }
                    shape.rect(sx + (tileSize * 0.58f), sy + 2f, 2f, tileSize - 4f)
                }
                if (((x / 5) + (y / 7)) % 6 == 0) {
                    shape.color = base.cpy().lerp(Color.WHITE, 0.10f).apply { a = 0.12f }
                    shape.rect(sx + 2f, sy + (tileSize * 0.48f), tileSize - 5f, 2f)
                }
                if ((x + y) % 5 == 0) {
                    shape.color = base.cpy().lerp(Color.WHITE, 0.14f).apply { a = 0.18f }
                    shape.rect(sx + 2f, sy + 2f, 2f, 2f)
                }
            }
        }
        val blockedSprite = assets.spriteAssets.blockedTile()
        mapState?.blockedTiles?.forEach { (x, y) ->
            if (blockedSprite != null) return@forEach
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = terrainMetal
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
            shape.color = Color(0.28f, 0.30f, 0.34f, 0.82f)
            shape.rect(sx + 2f, sy + 2f, runtime.camera.tileSize - 4f, runtime.camera.tileSize - 4f)
        }
        val occupiedSprite = assets.spriteAssets.occupiedTile()
        mapState?.staticOccupancyTiles?.forEach { (x, y) ->
            if (occupiedSprite != null) return@forEach
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            shape.color = Color(0.37f, 0.24f, 0.14f, 0.84f)
            shape.rect(sx, sy, runtime.camera.tileSize, runtime.camera.tileSize)
            shape.color = Color(0.52f, 0.34f, 0.20f, 0.38f)
            shape.rect(sx + 3f, sy + 3f, runtime.camera.tileSize - 6f, runtime.camera.tileSize - 6f)
        }
    }

    private fun drawTerrainSprites(batch: SpriteBatch, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val mapState = runtime.session.state.mapState
        val tileSize = runtime.camera.tileSize
        assets.spriteAssets.terrainBase()?.let { sprite ->
            for (x in 0 until snapshot.mapWidth) {
                for (y in 0 until snapshot.mapHeight) {
                    drawSprite(
                        batch = batch,
                        sprite = sprite,
                        x = runtime.camera.worldToScreenX(x.toFloat()),
                        y = runtime.camera.worldToScreenY(y.toFloat()),
                        width = tileSize,
                        height = tileSize
                    )
                }
            }
        }
        assets.spriteAssets.blockedTile()?.let { sprite ->
            mapState?.blockedTiles?.forEach { (x, y) ->
                drawSprite(
                    batch = batch,
                    sprite = sprite,
                    x = runtime.camera.worldToScreenX(x.toFloat()),
                    y = runtime.camera.worldToScreenY(y.toFloat()),
                    width = tileSize,
                    height = tileSize
                )
            }
        }
        assets.spriteAssets.occupiedTile()?.let { sprite ->
            mapState?.staticOccupancyTiles?.forEach { (x, y) ->
                drawSprite(
                    batch = batch,
                    sprite = sprite,
                    x = runtime.camera.worldToScreenX(x.toFloat()),
                    y = runtime.camera.worldToScreenY(y.toFloat()),
                    width = tileSize,
                    height = tileSize
                )
            }
        }
    }

    private fun drawGrid(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        shape.color = Color(0.18f, 0.22f, 0.18f, 0.22f)
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
        shape.color = Color(0.34f, 0.40f, 0.42f, 0.42f)
        mapState.blockedTiles.forEach { (x, y) ->
            val sx = runtime.camera.worldToScreenX(x.toFloat())
            val sy = runtime.camera.worldToScreenY(y.toFloat())
            val tile = runtime.camera.tileSize
            shape.rect(sx, sy, tile, tile)
        }
        shape.color = Color(0.58f, 0.42f, 0.22f, 0.34f)
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
            if (assets.spriteAssets.resource(node.kind) != null) continue
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

    private fun drawResourceSprites(batch: SpriteBatch, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        for (node in snapshot.resourceNodes) {
            val sprite = assets.spriteAssets.resource(node.kind) ?: continue
            val size = runtime.camera.tileSize * sprite.scale
            drawSprite(
                batch = batch,
                sprite = sprite,
                x = runtime.camera.worldToScreenX(node.x) - (size / 2f),
                y = runtime.camera.worldToScreenY(node.y) - (size / 2f),
                width = size,
                height = size
            )
        }
    }

    private fun drawEntitySprites(batch: SpriteBatch, runtime: GdxClientRuntime, snapshot: ClientSnapshot) {
        val viewedFaction = runtime.session.state.viewedFaction
        for (entity in snapshot.entities) {
            if (!isEntityVisible(entity, runtime)) continue
            val sprite = assets.spriteAssets.entity(entity) ?: continue
            val recoil = damageRecoilOffset(runtime, snapshot, entity)
            val criticalShake = criticalShakeOffset(entity)
            val screenX = runtime.camera.worldToScreenX(entity.x) + recoil.first + criticalShake.first
            val screenY = runtime.camera.worldToScreenY(entity.y) + recoil.second + criticalShake.second
            if (!isOnScreen(screenX, screenY)) continue
            if (entity.footprintWidth != null && entity.footprintHeight != null) {
                val tileX = floor(entity.x).toInt()
                val tileY = floor(entity.y).toInt()
                val width = entity.footprintWidth * runtime.camera.tileSize * sprite.scale
                val height = entity.footprintHeight * runtime.camera.tileSize * sprite.scale
                val left = runtime.camera.worldToScreenX(tileX.toFloat()) - ((width - (entity.footprintWidth * runtime.camera.tileSize)) / 2f)
                val top = runtime.camera.worldToScreenY(tileY.toFloat()) - ((height - (entity.footprintHeight * runtime.camera.tileSize)) / 2f)
                drawSprite(
                    batch = batch,
                    sprite = sprite,
                    x = left,
                    y = top,
                    width = width,
                    height = height,
                    teamColor = factionColor(entity.faction, viewedFaction)
                )
            } else {
                val size = runtime.camera.tileSize * sprite.scale
                drawSprite(
                    batch = batch,
                    sprite = sprite,
                    x = screenX - (size / 2f),
                    y = screenY - (size / 2f),
                    width = size,
                    height = size,
                    teamColor = factionColor(entity.faction, viewedFaction)
                )
            }
        }
    }

    private fun drawEntities(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val viewedFaction = runtime.session.state.viewedFaction
        for (entity in snapshot.entities) {
            if (!isEntityVisible(entity, runtime)) continue
            val hasSprite = assets.spriteAssets.entity(entity) != null
            val recoil = damageRecoilOffset(runtime, snapshot, entity)
            val criticalShake = criticalShakeOffset(entity)
            val screenX = runtime.camera.worldToScreenX(entity.x) + recoil.first + criticalShake.first
            val screenY = runtime.camera.worldToScreenY(entity.y) + recoil.second + criticalShake.second
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
                if (!hasSprite) {
                    shape.color = Color(0f, 0f, 0f, 0.18f)
                    shape.rect(left + 7f, top + 8f, w, h)
                    shape.color = Color(0f, 0f, 0f, 0.12f)
                    shape.rect(left + 3f, top + h - 4f, w + 4f, 8f)
                }
                if (runtime.isDamageFlashActive(entity.id)) {
                    val flashColor = impactFlashForEntity(runtime, entity.id)
                    shape.color = Color(flashColor.r, flashColor.g, flashColor.b, damageFlashAlpha(selected))
                    shape.rect(left - 8f, top - 8f, w + 16f, h + 16f)
                    shape.color = flashColor
                    shape.rect(left - 5f, top - 5f, w + 10f, h + 10f)
                }
                if (runtime.isCompletionFlashActive(entity.id)) {
                    val completionColor = completionFlashColor(runtime, entity.id)
                    val sparkColor = completionSparkColor(runtime, entity.id)
                    shape.color = completionColor.cpy().apply { a = completionFlashAlpha(selected) }
                    shape.rect(left - 9f, top - 9f, w + 18f, h + 18f)
                    shape.color = completionColor
                    when (runtime.completionFlashKind(entity.id)) {
                        CompletionFlashKind.CONSTRUCTION -> {
                            shape.rect(left - 6f, top - 6f, w + 12f, h + 12f)
                            shape.color = sparkColor.cpy().apply { a = 0.84f }
                            shape.rect(left - 8f, top + (h * 0.5f), w + 16f, 2f)
                            shape.rect(left + (w * 0.5f), top - 8f, 2f, h + 16f)
                        }
                        CompletionFlashKind.PRODUCTION -> {
                            shape.rect(left - 6f, top - 2f, w + 12f, h + 4f)
                            shape.color = sparkColor.cpy().apply { a = 0.86f }
                            shape.rect(left - 8f, top + h + 2f, w + 16f, 3f)
                            shape.rect(left - 8f, top - 5f, w + 16f, 3f)
                        }
                        CompletionFlashKind.RESEARCH, null -> {
                            shape.rect(left - 6f, top - 6f, w + 12f, h + 12f)
                            shape.color = sparkColor.cpy().apply { a = 0.84f }
                            shape.rect(left + (w * 0.18f), top - 7f, w * 0.64f, 2f)
                            shape.rect(left + (w * 0.18f), top + h + 5f, w * 0.64f, 2f)
                            shape.rect(left - 7f, top + (h * 0.18f), 2f, h * 0.64f)
                            shape.rect(left + w + 5f, top + (h * 0.18f), 2f, h * 0.64f)
                        }
                    }
                }
                if (!hasSprite) {
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
                }
            } else {
                if (!hasSprite) {
                    shape.color = Color(0f, 0f, 0f, 0.20f)
                    shape.circle(screenX + 2.5f, screenY + 3.5f, if (selected) 8.5f else 7f)
                }
                if (runtime.isDamageFlashActive(entity.id)) {
                    val flashColor = impactFlashForEntity(runtime, entity.id)
                    val sparkColor = impactSparkForEntity(runtime, entity.id)
                    shape.color = Color(flashColor.r, flashColor.g, flashColor.b, damageFlashAlpha(selected))
                    shape.circle(screenX, screenY, if (selected) 16f else 14f)
                    shape.color = flashColor
                    shape.circle(screenX, screenY, if (selected) 13.5f else 12f)
                    shape.color = sparkColor
                    shape.rect(screenX - 1.5f, screenY - 9f, 3f, 18f)
                    shape.rect(screenX - 9f, screenY - 1.5f, 18f, 3f)
                }
                if (runtime.isCompletionFlashActive(entity.id)) {
                    val completionColor = completionFlashColor(runtime, entity.id)
                    val sparkColor = completionSparkColor(runtime, entity.id)
                    shape.color = completionColor.cpy().apply { a = completionFlashAlpha(selected) }
                    shape.circle(screenX, screenY, if (selected) 17f else 15f)
                    shape.color = completionColor
                    when (runtime.completionFlashKind(entity.id)) {
                        CompletionFlashKind.CONSTRUCTION -> {
                            shape.rect(screenX - 8f, screenY - 8f, 16f, 16f)
                            shape.color = sparkColor
                            shape.rect(screenX - 10f, screenY - 1f, 20f, 2f)
                            shape.rect(screenX - 1f, screenY - 10f, 2f, 20f)
                        }
                        CompletionFlashKind.PRODUCTION -> {
                            shape.circle(screenX, screenY, if (selected) 15f else 13f)
                            shape.color = sparkColor
                            shape.rect(screenX - 10f, screenY - 5f, 20f, 3f)
                            shape.rect(screenX - 10f, screenY + 2f, 20f, 3f)
                        }
                        CompletionFlashKind.RESEARCH, null -> {
                            shape.circle(screenX, screenY, if (selected) 15f else 13f)
                            shape.color = sparkColor
                            shape.rectLine(screenX - 8f, screenY, screenX, screenY - 8f, 1.8f)
                            shape.rectLine(screenX, screenY - 8f, screenX + 8f, screenY, 1.8f)
                            shape.rectLine(screenX + 8f, screenY, screenX, screenY + 8f, 1.8f)
                            shape.rectLine(screenX, screenY + 8f, screenX - 8f, screenY, 1.8f)
                        }
                    }
                }
                if (!hasSprite) {
                    drawUnitSilhouette(shape, entity, screenX, screenY, factionColor(entity.faction, viewedFaction), selected)
                }
            }
            drawHealthBar(shape, runtime, entity, screenX, screenY, selected)
        }
    }

    private fun drawSprite(
        batch: SpriteBatch,
        sprite: LoadedSpriteAsset,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        teamColor: Color? = null
    ) {
        val tint = sprite.tint ?: Color.WHITE
        batch.color =
            if (teamColor != null && sprite.teamTintStrength > 0f) {
                tint.cpy().lerp(teamColor, sprite.teamTintStrength)
            } else {
                tint
            }
        batch.draw(sprite.region, x, y, width, height)
    }

    private fun drawHealthBar(shape: ShapeRenderer, runtime: GdxClientRuntime, entity: EntitySnapshot, x: Float, y: Float, selected: Boolean) {
        val hp = entity.hp
        val maxHp = entity.maxHp
        val damaged = runtime.isDamageFlashActive(entity.id)
        val impactKind = runtime.damageImpactKind(entity.id)
        val meleeImpact = impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE
        val barWidth = if (selected) 20f else 17f
        val barHeight = if (selected) 3f else 2f
        val top = y - if (selected) 16f else 14f
        shape.color =
            when {
                damaged && meleeImpact -> Color(0.10f, 0.18f, 0.08f, 0.78f)
                damaged -> Color(0.18f, 0.08f, 0.08f, 0.78f)
                selected -> Color(0.04f, 0.04f, 0.04f, 0.66f)
                else -> Color(0.1f, 0.1f, 0.1f, 0.58f)
            }
        shape.rect(x - (barWidth / 2f), top, barWidth, barHeight)
        val fillColor =
            when {
                meleeImpact && hp * 100 >= maxHp * 66 -> Color(0.68f, 1.00f, 0.60f, 1f)
                meleeImpact && hp * 100 >= maxHp * 33 -> Color(0.92f, 1.00f, 0.42f, 1f)
                meleeImpact -> Color(1.00f, 0.58f, 0.34f, 1f)
                hp * 100 >= maxHp * 66 -> Color(0.30f, 0.83f, 0.43f, 1f)
                hp * 100 >= maxHp * 33 -> Color(0.89f, 0.71f, 0.22f, 1f)
                else -> Color(0.84f, 0.29f, 0.29f, 1f)
            }
        shape.color = if (damaged) fillColor.cpy().lerp(Color.WHITE, 0.24f) else fillColor
        val fillWidth = barWidth * (hp.toFloat() / maxHp.coerceAtLeast(1))
        shape.rect(x - (barWidth / 2f), top, fillWidth, barHeight)
        if (damaged) {
            shape.color = Color(1.00f, 0.88f, 0.76f, 0.48f)
            shape.rect(x - (barWidth / 2f), top - 1f, fillWidth.coerceAtLeast(3f), 1f)
        }
    }

    private fun drawSelectionOverlays(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val leadSelectedId = runtime.session.state.selectedIds.firstOrNull()
        val pulse = selectionPulse()
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val startX = runtime.camera.worldToScreenX(entity.x)
            val startY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(startX, startY)) continue
            if (entity.id == leadSelectedId) {
                shape.color = Color(selectionSoftColor.r, selectionSoftColor.g, selectionSoftColor.b, 0.18f + (pulse * 0.08f))
                shape.circle(startX, startY, 12f + (pulse * 2.2f))
                shape.color = Color(0.82f, 1.00f, 0.84f, 0.14f + (pulse * 0.06f))
                shape.circle(startX, startY, 17f + (pulse * 3.4f))
            } else {
                shape.color = selectionColor.cpy().apply { a = 0.12f }
                shape.circle(startX, startY, 7f)
            }
        }
    }

    private fun drawSelectionBrackets(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val leadSelectedId = runtime.session.state.selectedIds.firstOrNull()
        shape.color = selectionColor
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val screenX = runtime.camera.worldToScreenX(entity.x)
            val screenY = runtime.camera.worldToScreenY(entity.y)
            if (!isOnScreen(screenX, screenY)) continue
            val leadSelected = entity.id == leadSelectedId
            val impactKind = runtime.damageImpactKind(entity.id)
            val bracketColor =
                when (impactKind) {
                    CombatSoundKind.MELEE,
                    CombatSoundKind.ZERGLING_MELEE -> Color(0.82f, 1.00f, 0.68f, 1f)
                    CombatSoundKind.MARINE_RANGED,
                    CombatSoundKind.RANGED,
                    null -> selectionColor
                }
            val footprintWidth = entity.footprintWidth
            val footprintHeight = entity.footprintHeight
            val confirmPulse = runtime.selectionConfirmPulse(entity.id)
            if (footprintWidth != null && footprintHeight != null) {
                val tileX = floor(entity.x).toInt()
                val tileY = floor(entity.y).toInt()
                val left = runtime.camera.worldToScreenX(tileX.toFloat()) - 6f
                val top = runtime.camera.worldToScreenY(tileY.toFloat()) - 6f
                val width = footprintWidth * runtime.camera.tileSize + 12f
                val height = footprintHeight * runtime.camera.tileSize + 12f
                val corner = if (leadSelected) 15f else 12f
                val pulse = selectionPulse()
                shape.color = Color(selectionSoftColor.r, selectionSoftColor.g, selectionSoftColor.b, 0.12f + (pulse * 0.08f))
                shape.line(left - 3f, top - 3f, left + width + 3f, top - 3f)
                shape.line(left - 3f, top + height + 3f, left + width + 3f, top + height + 3f)
                shape.line(left - 3f, top - 3f, left - 3f, top + height + 3f)
                shape.line(left + width + 3f, top - 3f, left + width + 3f, top + height + 3f)
                shape.color = bracketColor
                shape.line(left, top, left + corner, top)
                shape.line(left, top, left, top + corner)
                shape.line(left + width, top, left + width - corner, top)
                shape.line(left + width, top, left + width, top + corner)
                shape.line(left, top + height, left + corner, top + height)
                shape.line(left, top + height, left, top + height - corner)
                shape.line(left + width, top + height, left + width - corner, top + height)
                shape.line(left + width, top + height, left + width, top + height - corner)
                if (leadSelected) {
                    shape.color = Color(0.90f, 1.00f, 0.86f, 0.88f)
                    shape.rect(left + (width * 0.30f), top - 6f, width * 0.40f, 1.6f)
                    shape.rect(left + (width * 0.30f), top + height + 4.4f, width * 0.40f, 1.6f)
                    shape.color = bracketColor
                }
                if (confirmPulse > 0f) {
                    shape.color = Color(bracketColor.r, bracketColor.g, bracketColor.b, 0.22f * confirmPulse)
                    shape.line(left - 6f, top - 6f, left + width + 6f, top - 6f)
                    shape.line(left - 6f, top + height + 6f, left + width + 6f, top + height + 6f)
                    shape.line(left - 6f, top - 6f, left - 6f, top + height + 6f)
                    shape.line(left + width + 6f, top - 6f, left + width + 6f, top + height + 6f)
                }
                if (runtime.isDamageFlashActive(entity.id)) {
                    shape.color =
                        when (impactKind) {
                            CombatSoundKind.MELEE,
                            CombatSoundKind.ZERGLING_MELEE -> Color(0.92f, 1.00f, 0.72f, 0.76f)
                            else -> Color(0.92f, 1.00f, 0.78f, 0.76f)
                        }
                    shape.rect(left - 2f, top + (height * 0.5f), width + 4f, 1.5f)
                    shape.rect(left + (width * 0.5f), top - 2f, 1.5f, height + 4f)
                }
                shape.color = Color(0.84f, 1.00f, 0.76f, 0.44f + (pulse * 0.14f))
                shape.rect(left + (width * 0.22f), top - 4f, width * 0.56f, 1.5f)
                shape.rect(left + (width * 0.22f), top + height + 2.5f, width * 0.56f, 1.5f)
            } else {
                val radius = if (leadSelected) 12.5f else 11f
                val pulse = selectionPulse()
                shape.color = Color(selectionSoftColor.r, selectionSoftColor.g, selectionSoftColor.b, 0.12f + (pulse * 0.06f))
                shape.circle(screenX, screenY, radius + if (leadSelected) 2.5f else 1.5f)
                if (confirmPulse > 0f) {
                    shape.color = Color(bracketColor.r, bracketColor.g, bracketColor.b, 0.18f * confirmPulse)
                    shape.circle(screenX, screenY, radius + 6f + ((1f - confirmPulse) * 4f))
                }
                shape.color = bracketColor
                shape.circle(screenX, screenY, radius)
                val wing = if (leadSelected) 7f else 5.5f
                shape.line(screenX - radius - wing, screenY, screenX - radius + 1.5f, screenY)
                shape.line(screenX + radius - 1.5f, screenY, screenX + radius + wing, screenY)
                shape.line(screenX, screenY - radius - wing, screenX, screenY - radius + 1.5f)
                shape.line(screenX, screenY + radius - 1.5f, screenX, screenY + radius + wing)
                shape.line(screenX - 7f, screenY - 7f, screenX - 3.5f, screenY - 3.5f)
                shape.line(screenX + 7f, screenY - 7f, screenX + 3.5f, screenY - 3.5f)
                shape.line(screenX - 7f, screenY + 7f, screenX - 3.5f, screenY + 3.5f)
                shape.line(screenX + 7f, screenY + 7f, screenX + 3.5f, screenY + 3.5f)
                if (leadSelected) {
                    shape.color = Color(0.90f, 1.00f, 0.86f, 0.92f)
                    shape.rect(screenX - 6f, screenY - radius - 5.8f, 12f, 1.8f)
                    shape.rect(screenX - 6f, screenY + radius + 4f, 12f, 1.8f)
                    shape.color = bracketColor
                }
                if (runtime.isDamageFlashActive(entity.id)) {
                    shape.color = Color(0.92f, 1.00f, 0.78f, 0.82f)
                    shape.rect(screenX - radius - 2f, screenY - 0.75f, (radius * 2f) + 4f, 1.5f)
                    shape.rect(screenX - 0.75f, screenY - radius - 2f, 1.5f, (radius * 2f) + 4f)
                }
                shape.color = Color(0.84f, 1.00f, 0.76f, 0.40f + (pulse * 0.14f))
                shape.rect(screenX - 4.5f, screenY + radius + 2.5f, 9f, 1.5f)
            }
        }
    }

    private fun drawOrderMarkers(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val snapshot = runtime.snapshot ?: return
        val entitiesById = snapshot.entities.associateBy { it.id }
        val leadSelectedId = runtime.session.state.selectedIds.firstOrNull()
        for (entity in snapshot.entities) {
            if (entity.id !in runtime.session.state.selectedIds) continue
            if (!isEntityVisible(entity, runtime)) continue
            val startX = runtime.camera.worldToScreenX(entity.x)
            val startY = runtime.camera.worldToScreenY(entity.y)
            if (entity.pathRemainingNodes > 0 && entity.pathGoalX != null && entity.pathGoalY != null) {
                if (entity.id == leadSelectedId) {
                    val goalX = runtime.camera.worldToScreenX(entity.pathGoalX.toFloat() + 0.5f)
                    val goalY = runtime.camera.worldToScreenY(entity.pathGoalY.toFloat() + 0.5f)
                    val pathKind = orderMarkerKind(entity)
                    val tone = pingTone(pathKind)
                    shape.color = tone.cpy().mul(1f, 1f, 1f, 0.12f)
                    shape.rectLine(startX, startY, goalX, goalY, 1.6f)
                    shape.color = tone.cpy().mul(1f, 1f, 1f, 0.26f)
                    shape.circle(goalX, goalY, 9f)
                    shape.color = tone.cpy().mul(1f, 1f, 1f, 0.92f)
                    shape.circle(goalX, goalY, 2.6f)
                    drawChevronTrail(shape, startX, startY, goalX, goalY, tone.cpy().mul(1f, 1f, 1f, 0.56f))
                }
            }
            if (entity.rallyX != null && entity.rallyY != null) {
                val rallyX = runtime.camera.worldToScreenX(entity.rallyX)
                val rallyY = runtime.camera.worldToScreenY(entity.rallyY)
                shape.color = pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.14f)
                shape.rectLine(startX, startY, rallyX, rallyY, 2f)
                shape.color = pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.24f)
                shape.circle(rallyX, rallyY, 11f)
                shape.color = pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.94f)
                shape.rect(rallyX - 3f, rallyY - 3f, 6f, 6f)
                shape.rect(rallyX - 9f, rallyY - 1f, 18f, 2f)
                shape.rect(rallyX - 1f, rallyY - 9f, 2f, 18f)
                drawChevronTrail(shape, startX, startY, rallyX, rallyY, pingTone(GroundPingKind.MOVE).cpy().mul(1f, 1f, 1f, 0.64f))
            }
            if (entity.buildTargetId != null) {
                entitiesById[entity.buildTargetId]?.let { target ->
                    val targetX = runtime.camera.worldToScreenX(target.x)
                    val targetY = runtime.camera.worldToScreenY(target.y)
                    shape.color = pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.16f)
                    shape.rectLine(startX, startY, targetX, targetY, 2.2f)
                    shape.color = pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.22f)
                    shape.circle(targetX, targetY, 10f)
                    shape.color = pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.92f)
                    shape.circle(targetX, targetY, 3.5f)
                    shape.rect(targetX - 8f, targetY - 8f, 16f, 2f)
                    shape.rect(targetX - 8f, targetY + 6f, 16f, 2f)
                    drawChevronTrail(shape, startX, startY, targetX, targetY, pingTone(GroundPingKind.BUILD).cpy().mul(1f, 1f, 1f, 0.68f))
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
                val hostile = nearestHostile(snapshot, entity)
                val cooldownRatio = (entity.weaponCooldownTicks.coerceAtMost(20) / 20f)
                val isMelee = isMeleeWeapon(entity)
                shape.color = if (isMelee) Color(0.70f, 0.98f, 0.60f, 0.18f) else Color(0.98f, 0.54f, 0.22f, 0.18f)
                shape.circle(screenX, screenY, if (isMelee) 14f else 12f)
                shape.color = if (isMelee) Color(0.78f, 1.00f, 0.70f, 0.95f) else Color(0.98f, 0.72f, 0.26f, 0.95f)
                shape.rect(screenX - 7f, screenY + 9f, cooldownRatio * 14f, 2f)
                val muzzleX = screenX + directionDx(entity.dir, 8f)
                val muzzleY = screenY + directionDy(entity.dir, 8f)
                val flashPulse = ambientPulse(420L)
                val marineStyle = entity.typeId.contains("Marine", ignoreCase = true)
                val zerglingStyle = entity.typeId.contains("Zergling", ignoreCase = true)
                shape.color = if (isMelee) Color(0.86f, 1.00f, 0.74f, 0.24f + (flashPulse * 0.14f)) else if (marineStyle) Color(1.00f, 0.92f, 0.60f, 0.22f + (flashPulse * 0.16f)) else Color(1.00f, 0.80f, 0.46f, 0.26f + (flashPulse * 0.16f))
                if (marineStyle && !isMelee) {
                    shape.rectLine(muzzleX - directionDy(entity.dir, 3.5f), muzzleY + directionDx(entity.dir, 3.5f), muzzleX + directionDy(entity.dir, 3.5f), muzzleY - directionDx(entity.dir, 3.5f), 3.2f)
                    shape.rectLine(muzzleX, muzzleY, muzzleX + directionDx(entity.dir, 9f), muzzleY + directionDy(entity.dir, 9f), 2.4f)
                    shape.rectLine(
                        muzzleX + directionDx(entity.dir, 3f) - directionDy(entity.dir, 1.8f),
                        muzzleY + directionDy(entity.dir, 3f) + directionDx(entity.dir, 1.8f),
                        muzzleX + directionDx(entity.dir, 6.5f) + directionDy(entity.dir, 1.4f),
                        muzzleY + directionDy(entity.dir, 6.5f) - directionDx(entity.dir, 1.4f),
                        1.8f
                    )
                } else {
                    shape.circle(muzzleX, muzzleY, if (isMelee) 7f + (flashPulse * 2.6f) else 6f + (flashPulse * 2f))
                }
                shape.color = if (isMelee) Color(0.92f, 1.00f, 0.82f, 0.46f) else if (marineStyle) Color(1.00f, 0.98f, 0.80f, 0.54f) else Color(1.00f, 0.84f, 0.48f, 0.48f)
                if (marineStyle && !isMelee) {
                    shape.rectLine(muzzleX - directionDy(entity.dir, 2f), muzzleY + directionDx(entity.dir, 2f), muzzleX + directionDy(entity.dir, 2f), muzzleY - directionDx(entity.dir, 2f), 1.8f)
                    shape.rectLine(muzzleX, muzzleY, muzzleX + directionDx(entity.dir, 6f), muzzleY + directionDy(entity.dir, 6f), 1.4f)
                } else {
                    shape.circle(muzzleX, muzzleY, if (isMelee) 4.2f else 3.5f)
                }
                shape.color = if (isMelee) Color(0.74f, 0.98f, 0.58f, 0.74f) else if (marineStyle) Color(1.00f, 0.72f, 0.24f, 0.86f) else Color(1.00f, 0.63f, 0.28f, 0.78f)
                shape.rectLine(screenX, screenY, screenX + directionDx(entity.dir, if (isMelee) 10f else if (marineStyle) 15f else 13f), screenY + directionDy(entity.dir, if (isMelee) 10f else if (marineStyle) 15f else 13f), if (isMelee) 2.2f else if (marineStyle) 1.4f else 1.8f)
                hostile?.let { target ->
                    val targetX = runtime.camera.worldToScreenX(target.x)
                    val targetY = runtime.camera.worldToScreenY(target.y)
                    if (isMelee) {
                        val strikeX = screenX + ((targetX - screenX) * 0.62f)
                        val strikeY = screenY + ((targetY - screenY) * 0.62f)
                        val slashDx = directionDx(entity.dir, 8f)
                        val slashDy = directionDy(entity.dir, 8f)
                        shape.color = Color(0.84f, 1.00f, 0.74f, 0.12f)
                        shape.circle(strikeX, strikeY, 14f + (flashPulse * 3.5f))
                        if (entity.typeId.contains("Zergling", ignoreCase = true)) {
                            shape.color = Color(0.92f, 1.00f, 0.84f, 0.88f)
                            shape.rectLine(strikeX - slashDy, strikeY + slashDx, strikeX + slashDy, strikeY - slashDx, 2.6f)
                            shape.rectLine(strikeX - (slashDx * 0.9f), strikeY - (slashDy * 0.9f), strikeX + (slashDx * 0.9f), strikeY + (slashDy * 0.9f), 1.8f)
                            shape.rectLine(strikeX - (slashDx * 0.2f) - (slashDy * 0.9f), strikeY - (slashDy * 0.2f) + (slashDx * 0.9f), strikeX, strikeY, 1.4f)
                            shape.color = Color(0.82f, 1.00f, 0.74f, 0.40f)
                            shape.circle(targetX, targetY, 8f)
                            shape.rectLine(targetX - 5f, targetY, targetX + 5f, targetY, 1.4f)
                        } else {
                            shape.color = Color(0.96f, 1.00f, 0.88f, 0.82f)
                            shape.rectLine(strikeX - slashDy, strikeY + slashDx, strikeX + slashDy, strikeY - slashDx, 2.0f)
                        }
                        shape.color = Color(0.72f, 0.98f, 0.58f, 0.78f)
                        shape.circle(targetX, targetY, 5.8f)
                        shape.rectLine(strikeX, strikeY, targetX, targetY, 1.8f)
                    } else {
                        val nearX = muzzleX + ((targetX - muzzleX) * 0.22f)
                        val nearY = muzzleY + ((targetY - muzzleY) * 0.22f)
                        val midX = muzzleX + ((targetX - muzzleX) * 0.48f)
                        val midY = muzzleY + ((targetY - muzzleY) * 0.48f)
                        val farX = muzzleX + ((targetX - muzzleX) * 0.78f)
                        val farY = muzzleY + ((targetY - muzzleY) * 0.78f)
                        val tailX = targetX - ((targetX - muzzleX) * 0.10f)
                        val tailY = targetY - ((targetY - muzzleY) * 0.10f)
                        shape.color = if (marineStyle) Color(1.00f, 0.82f, 0.46f, 0.20f) else Color(1.00f, 0.72f, 0.36f, 0.26f)
                        shape.rectLine(muzzleX, muzzleY, targetX, targetY, if (marineStyle) 3.4f else 4.2f)
                        shape.color = if (marineStyle) Color(1.00f, 0.96f, 0.74f, 0.86f) else Color(1.00f, 0.88f, 0.62f, 0.74f)
                        shape.rectLine(muzzleX, muzzleY, targetX, targetY, if (marineStyle) 1.1f else 1.6f)
                        shape.color = Color(1.00f, 0.92f, 0.76f, 0.72f)
                        shape.circle(nearX, nearY, if (marineStyle) 1.2f else 1.6f)
                        shape.circle(midX, midY, if (marineStyle) 1.8f else 2.4f)
                        shape.circle(farX, farY, if (marineStyle) 1.4f else 1.8f)
                        shape.color = if (marineStyle) Color(1.00f, 0.86f, 0.56f, 0.16f) else Color(1.00f, 0.78f, 0.44f, 0.18f)
                        shape.circle(targetX, targetY, 8f)
                        shape.color = if (marineStyle) Color(1.00f, 0.98f, 0.80f, 0.84f) else Color(1.00f, 0.92f, 0.72f, 0.76f)
                        shape.circle(targetX, targetY, 3.6f)
                        shape.color = if (marineStyle) Color(1.00f, 0.92f, 0.74f, 0.20f) else Color(1.00f, 0.82f, 0.58f, 0.18f)
                        shape.circle(tailX, tailY, if (marineStyle) 3.8f else 4.8f)
                        shape.color = if (marineStyle) Color(0.76f, 0.78f, 0.82f, 0.10f) else Color(0.40f, 0.38f, 0.34f, 0.12f)
                        shape.circle(targetX + 2f, targetY + 3f, if (marineStyle) 2.8f else 3.4f)
                        shape.circle(targetX - 3f, targetY + 5f, if (marineStyle) 2.1f else 2.8f)
                        shape.rect(targetX - 7f, targetY - 1f, 14f, 2f)
                        shape.rect(targetX - 1f, targetY - 7f, 2f, 14f)
                        shape.color = Color(1.00f, 0.90f, 0.70f, 0.36f)
                        shape.circle(midX, midY, 2.2f)
                        if (marineStyle) {
                            shape.color = Color(1.00f, 0.96f, 0.78f, 0.44f)
                            shape.rectLine(targetX - 4f, targetY - 4f, targetX + 4f, targetY + 4f, 1.2f)
                            shape.rectLine(targetX - 4f, targetY + 4f, targetX + 4f, targetY - 4f, 1.2f)
                        }
                    }
                }
            }
            if (entity.pathRemainingNodes > 0) {
                val trailX = screenX - directionDx(entity.dir, 7f)
                val trailY = screenY - directionDy(entity.dir, 7f)
                shape.color = Color(0.74f, 0.90f, 1.00f, 0.14f)
                shape.circle(trailX, trailY, 4.5f)
                shape.color = Color(0.68f, 0.86f, 0.96f, 0.34f)
                shape.rectLine(trailX, trailY, screenX, screenY, 1.4f)
                shape.color = Color(0.84f, 0.96f, 1.00f, 0.12f)
                shape.circle(trailX - 4f, trailY - 2f, 2.8f)
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
            entity.harvestPhase?.lowercase()?.let { phase ->
                val harvestTone =
                    when (entity.harvestCargoKind) {
                        "gas" -> Color(0.58f, 1.00f, 0.84f, 0.82f)
                        else -> Color(1.00f, 0.90f, 0.56f, 0.82f)
                    }
                val harvestGlow =
                    when (entity.harvestCargoKind) {
                        "gas" -> Color(0.42f, 0.96f, 0.78f, 0.20f)
                        else -> Color(1.00f, 0.84f, 0.42f, 0.18f)
                    }
                when {
                    phase.contains("return") || phase.contains("drop") -> {
                        val arrowX = screenX + directionDx(entity.dir, 10f)
                        val arrowY = screenY + directionDy(entity.dir, 10f)
                        shape.color = harvestGlow.cpy().apply { a = 0.16f + (ambientPulse(700L) * 0.08f) }
                        shape.circle(screenX, screenY, 10f)
                        shape.color = harvestTone
                        shape.rectLine(screenX, screenY, arrowX, arrowY, 1.8f)
                        shape.rectLine(
                            arrowX,
                            arrowY,
                            arrowX - directionDx(entity.dir, 4f) + directionDy(entity.dir, 3f),
                            arrowY - directionDy(entity.dir, 4f) - directionDx(entity.dir, 3f),
                            1.4f
                        )
                        shape.rectLine(
                            arrowX,
                            arrowY,
                            arrowX - directionDx(entity.dir, 4f) - directionDy(entity.dir, 3f),
                            arrowY - directionDy(entity.dir, 4f) + directionDx(entity.dir, 3f),
                            1.4f
                        )
                    }
                    phase.contains("harvest") || phase.contains("gather") || phase.contains("mine") -> {
                        val pulse = ambientPulse(820L)
                        shape.color = harvestGlow.cpy().apply { a = 0.14f + (pulse * 0.08f) }
                        shape.circle(screenX, screenY, 9f + (pulse * 3f))
                        shape.color = harvestTone
                        shape.circle(screenX - 6f, screenY, 1.6f)
                        shape.circle(screenX + 5f, screenY - 2f, 1.4f)
                        shape.circle(screenX + 1f, screenY + 5f, 1.2f)
                    }
                }
            }
            if (entity.activeProductionType != null || entity.productionQueueSize > 0 || entity.activeResearchTech != null || entity.underConstruction) {
                val markerX = screenX + 10f
                val markerY = screenY - 11f
                shape.color = Color(0.08f, 0.09f, 0.11f, 0.90f)
                shape.rect(markerX, markerY, 18f, 5f)
                shape.color = activityTone(entity).cpy().apply { a = 0.95f }
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
                when {
                    entity.activeResearchTech != null -> {
                        shape.color = completionResearchFlashColor.cpy().apply { a = 0.82f }
                        shape.rect(markerX - 4f, markerY + 1f, 2f, 3f)
                    }
                    entity.activeProductionType != null || entity.productionQueueSize > 0 -> {
                        shape.color = completionProductionFlashColor.cpy().apply { a = 0.82f }
                        shape.rect(markerX - 4f, markerY + 1f, 2f, 3f)
                    }
                    entity.underConstruction -> {
                        shape.color = completionBuildFlashColor.cpy().apply { a = 0.82f }
                        shape.rect(markerX - 4f, markerY + 1f, 2f, 3f)
                    }
                }
            }
            if (runtime.isDamageFlashActive(entity.id)) {
                nearestHostile(snapshot, entity)?.let { attacker ->
                    val attackDir = directionTo(attacker.x, attacker.y, entity.x, entity.y)
                    val hitX = screenX - directionDx(attackDir, 10f)
                    val hitY = screenY - directionDy(attackDir, 10f)
                    val warnX = screenX - directionDx(attackDir, 14f)
                    val warnY = screenY - directionDy(attackDir, 14f)
                    val flankX = directionDy(attackDir, 4.5f)
                    val flankY = -directionDx(attackDir, 4.5f)
                    val meleeHit = isMeleeAttacker(attacker)
                    shape.color = if (meleeHit) selectionColor.cpy().apply { a = 0.86f } else pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.82f)
                    shape.rectLine(hitX, hitY, screenX, screenY, 2.2f)
                    shape.color = if (meleeHit) Color(0.90f, 1.00f, 0.78f, 0.84f) else Color(1.00f, 0.82f, 0.58f, 0.88f)
                    shape.rectLine(warnX - flankX, warnY - flankY, warnX, warnY, 1.8f)
                    shape.rectLine(warnX + flankX, warnY + flankY, warnX, warnY, 1.8f)
                    shape.color = if (meleeHit) Color(0.82f, 1.00f, 0.72f, 0.36f) else Color(1.00f, 0.68f, 0.42f, 0.32f)
                    shape.circle(warnX, warnY, if (meleeHit) 3.8f else 3.4f)
                    if (meleeHit) {
                        shape.rectLine(hitX + directionDy(attackDir, 6f), hitY - directionDx(attackDir, 6f), hitX - directionDy(attackDir, 6f), hitY + directionDx(attackDir, 6f), 2.2f)
                        shape.color = selectionColor.cpy().apply { a = 0.48f }
                        shape.circle(hitX, hitY, 4f)
                    } else {
                        shape.rectLine(
                            hitX + directionDy(attackDir, 4f),
                            hitY - directionDx(attackDir, 4f),
                            hitX,
                            hitY,
                            1.8f
                        )
                        shape.rectLine(
                            hitX - directionDy(attackDir, 4f),
                            hitY + directionDx(attackDir, 4f),
                            hitX,
                            hitY,
                            1.8f
                        )
                        shape.color = pingTone(GroundPingKind.ATTACK).cpy().mul(1f, 1f, 1f, 0.44f)
                        shape.circle(hitX, hitY, 3f)
                    }
                    shape.rectLine(hitX, hitY, screenX - directionDx(attackDir, 2f), screenY - directionDy(attackDir, 2f), 1.2f)
                }
                shape.color = impactSparkForEntity(runtime, entity.id)
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
                    shape.rect(screenX - 7f, screenY - 1f, 14f, 2f)
                    shape.rect(screenX - 1f, screenY - 7f, 2f, 14f)
                }
            }
            if (runtime.isCompletionFlashActive(entity.id)) {
                shape.color = completionSparkColor(runtime, entity.id)
                if (entity.footprintWidth != null && entity.footprintHeight != null) {
                    val tileX = floor(entity.x).toInt()
                    val tileY = floor(entity.y).toInt()
                    val left = runtime.camera.worldToScreenX(tileX.toFloat())
                    val top = runtime.camera.worldToScreenY(tileY.toFloat())
                    val width = entity.footprintWidth * runtime.camera.tileSize
                    val height = entity.footprintHeight * runtime.camera.tileSize
                    shape.rect(left - 3f, top - 3f, width + 6f, 2f)
                    shape.rect(left - 3f, top + height + 1f, width + 6f, 2f)
                    shape.rect(left - 3f, top - 3f, 2f, height + 6f)
                    shape.rect(left + width + 1f, top - 3f, 2f, height + 6f)
                } else {
                    shape.circle(screenX, screenY, 6f)
                    shape.rect(screenX - 8f, screenY - 1f, 16f, 2f)
                    shape.rect(screenX - 1f, screenY - 8f, 2f, 16f)
                }
            }
        }
    }

    private fun drawFog(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        return
    }

    private fun drawMiniMap(shape: ShapeRenderer, runtime: GdxClientRuntime, width: Int, height: Int) {
        val snapshot = runtime.snapshot ?: return
        val bounds = gdxMiniMapBounds(width, height)
        val boundsWidth = bounds.width
        val boundsHeight = bounds.height
        val left = bounds.left
        val top = bounds.top
        shape.color = Color(0.10f, 0.18f, 0.20f, 0.78f)
        shape.rect(left - 2f, top - 2f, boundsWidth + 4f, 2f)
        shape.rect(left - 2f, top + boundsHeight, boundsWidth + 4f, 2f)
        shape.rect(left - 2f, top, 2f, boundsHeight)
        shape.rect(left + boundsWidth, top, 2f, boundsHeight)
        shape.color = Color(0.18f, 0.28f, 0.30f, 0.82f)
        shape.rect(left + 6f, top - 8f, 26f, 3f)
        shape.rect(left + 36f, top - 8f, 8f, 3f)
        shape.rect(left + boundsWidth - 20f, top - 8f, 14f, 3f)
        shape.rect(left + boundsWidth - 8f, top + boundsHeight - 22f, 3f, 14f)
        shape.color = Color(0.05f, 0.09f, 0.10f, 0.88f)
        shape.rect(left, top, boundsWidth, boundsHeight)
        val viewedFaction = runtime.session.state.viewedFaction
        val visibleTiles = viewedFaction?.let { runtime.session.state.visionState?.visibleTiles(it) }
        val exploredTiles = viewedFaction?.let { runtime.session.state.visionState?.exploredTiles(it) }
        val tileWidth = boundsWidth / snapshot.mapWidth
        val tileHeight = boundsHeight / snapshot.mapHeight
        for (x in 0 until snapshot.mapWidth) {
            for (y in 0 until snapshot.mapHeight) {
                val macroPatch = ((x / 10) + (y / 8)) % 3
                val ridgeBand = ((x * 3) + (y * 2)) % 17
                val terrainDrift = ((x / 14) - (y / 11)) % 4
                shape.color =
                    when {
                        x in 40..56 && y in 40..56 -> terrainDust.cpy().lerp(Color.BLACK, 0.15f)
                        ridgeBand < 3 -> terrainRidge.cpy().lerp(Color.BLACK, 0.10f)
                        macroPatch == 0 -> terrainA.cpy().lerp(Color.BLACK, 0.10f)
                        terrainDrift == 0 -> terrainA.cpy().lerp(terrainDust, 0.16f).lerp(Color.BLACK, 0.08f)
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
            shape.rect(nodeX - 0.75f, nodeY - 0.75f, 2.5f, 2.5f)
        }
        for (entity in snapshot.entities) {
            val x = left + (entity.x / snapshot.mapWidth) * boundsWidth
            val y = top + (entity.y / snapshot.mapHeight) * boundsHeight
            val visible = visibleTiles == null || isEntityVisible(entity, runtime)
            val selected = entity.id in runtime.session.state.selectedIds
            val damaged = runtime.isDamageFlashActive(entity.id)
            val impactKind = runtime.damageImpactKind(entity.id)
            val meleeImpact = impactKind == CombatSoundKind.MELEE || impactKind == CombatSoundKind.ZERGLING_MELEE
            shape.color = factionColor(entity.faction, viewedFaction).cpy().apply { a = if (visible) 1f else 0.28f }
            val size = if (selected) 5f else 4f
            shape.rect(x - (size / 2f), y - (size / 2f), size, size)
            if (damaged) {
                shape.color =
                    if (meleeImpact) Color(0.86f, 1.00f, 0.62f, if (visible) 0.88f else 0.30f)
                    else Color(1.00f, 0.48f, 0.30f, if (visible) 0.82f else 0.28f)
                shape.rect(x - 4f, y - 0.75f, 8f, 1.5f)
                shape.rect(x - 0.75f, y - 4f, 1.5f, 8f)
            }
            if (runtime.isCompletionFlashActive(entity.id)) {
                shape.color = completionSparkColor(runtime, entity.id).cpy().apply { a = if (visible) 0.90f else 0.40f }
                when (runtime.completionFlashKind(entity.id)) {
                    CompletionFlashKind.CONSTRUCTION -> {
                        shape.rect(x - 5f, y - 1f, 10f, 2f)
                        shape.rect(x - 1f, y - 5f, 2f, 10f)
                    }
                    CompletionFlashKind.PRODUCTION -> {
                        shape.rect(x - 5f, y - 3f, 10f, 6f)
                    }
                    CompletionFlashKind.RESEARCH, null -> {
                        shape.rect(x - 4f, y - 4f, 8f, 8f)
                        shape.rect(x - 6f, y - 1f, 12f, 2f)
                        shape.rect(x - 1f, y - 6f, 2f, 12f)
                    }
                }
            }
            if (entity.pathRemainingNodes > 0) {
                shape.color = Color(0.64f, 0.88f, 0.98f, if (visible) 0.70f else 0.28f)
                shape.rect(x - 0.8f, y + 3.5f, 1.6f, 2.4f)
                shape.rect(x + 3.5f, y + 3.5f, 1.6f, 1.6f)
            }
            if (entity.activeProductionType != null || entity.productionQueueSize > 0) {
                shape.color = completionProductionSparkColor.cpy().apply { a = if (visible) 0.82f else 0.34f }
                shape.rect(x + 3.5f, y - 0.8f, 2.4f, 1.6f)
                shape.rect(x + 6.4f, y - 0.8f, 1.4f, 1.6f)
            }
            if (entity.activeResearchTech != null) {
                shape.color = completionResearchSparkColor.cpy().apply { a = if (visible) 0.82f else 0.34f }
                shape.rect(x - 5.8f, y - 0.8f, 2.4f, 1.6f)
                shape.rect(x - 8.6f, y - 0.8f, 1.4f, 1.6f)
            }
            if (entity.harvestCargoAmount != null && entity.harvestCargoAmount > 0) {
                shape.color =
                    when (entity.harvestCargoKind) {
                        "gas" -> Color(0.56f, 0.98f, 0.82f, if (visible) 0.82f else 0.36f)
                        else -> Color(0.98f, 0.86f, 0.48f, if (visible) 0.82f else 0.36f)
                    }
                shape.rect(x - 1.2f, y - 5.4f, 2.4f, 2.4f)
            }
            if (selected) {
                val selectedTone =
                    when (impactKind) {
                        CombatSoundKind.MELEE,
                        CombatSoundKind.ZERGLING_MELEE -> Color(0.82f, 1.00f, 0.68f, if (visible) 0.90f else 0.36f)
                        else -> selectionColor.cpy().apply { a = if (visible) 0.90f else 0.36f }
                    }
                shape.color = selectedTone
                shape.rect(x - 4.5f, y - 4.5f, 4f, 1f)
                shape.rect(x - 4.5f, y - 4.5f, 1f, 4f)
                shape.rect(x + 0.5f, y - 4.5f, 4f, 1f)
                shape.rect(x + 3.5f, y - 4.5f, 1f, 4f)
                shape.rect(x - 4.5f, y + 3.5f, 4f, 1f)
                shape.rect(x - 4.5f, y + 0.5f, 1f, 4f)
                shape.rect(x + 0.5f, y + 3.5f, 4f, 1f)
                shape.rect(x + 3.5f, y + 0.5f, 1f, 4f)
                if (damaged) {
                    shape.color =
                        if (meleeImpact) Color(0.94f, 1.00f, 0.72f, if (visible) 0.88f else 0.34f)
                        else Color(0.92f, 1.00f, 0.78f, if (visible) 0.82f else 0.30f)
                    shape.rect(x - 5.5f, y - 0.75f, 11f, 1.5f)
                    shape.rect(x - 0.75f, y - 5.5f, 1.5f, 11f)
                }
            }
        }
        drawMiniMapStatusLegend(shape, left, top, boundsWidth)
        runtime.currentGroundPing()?.let { ping ->
            val x = left + (ping.worldX / snapshot.mapWidth) * boundsWidth
            val y = top + (ping.worldY / snapshot.mapHeight) * boundsHeight
            shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.82f)
            shape.circle(x, y, 5f)
            when (ping.kind) {
                GroundPingKind.MOVE -> {
                    shape.rect(x - 1f, y - 6f, 2f, 12f)
                    shape.rect(x - 6f, y - 1f, 12f, 2f)
                    shape.circle(x, y, 2f)
                }
                GroundPingKind.ATTACK -> {
                    shape.rectLine(x - 5f, y - 5f, x + 5f, y + 5f, 1.4f)
                    shape.rectLine(x - 5f, y + 5f, x + 5f, y - 5f, 1.4f)
                    shape.rect(x - 1f, y - 7f, 2f, 14f)
                    shape.rect(x - 7f, y - 1f, 14f, 2f)
                }
                GroundPingKind.BUILD -> {
                    shape.rect(x - 5f, y - 1f, 10f, 2f)
                    shape.rect(x - 1f, y - 5f, 2f, 10f)
                    shape.rect(x - 5f, y - 5f, 2f, 2f)
                    shape.rect(x + 3f, y - 5f, 2f, 2f)
                    shape.rect(x - 5f, y + 3f, 2f, 2f)
                    shape.rect(x + 3f, y + 3f, 2f, 2f)
                }
                GroundPingKind.INVALID -> {
                    shape.rectLine(x - 5f, y + 5f, x + 5f, y - 5f, 1.4f)
                    shape.rectLine(x - 5f, y - 5f, x + 5f, y + 5f, 1.4f)
                    shape.rect(x - 1f, y - 6f, 2f, 12f)
                }
            }
        }
        runtime.currentMinimapConfirm()?.let { confirm ->
            val x = left + (confirm.worldX / snapshot.mapWidth) * boundsWidth
            val y = top + (confirm.worldY / snapshot.mapHeight) * boundsHeight
            drawConfirmPulse(
                shape,
                x,
                y,
                scale = if (confirm.subtle) 0.62f else 0.78f,
                periodMillis = if (confirm.subtle) 700L else 820L
            )
        }
    }

    private fun drawMiniMapStatusLegend(shape: ShapeRenderer, left: Float, top: Float, width: Float) {
        val legendTop = top + 5f
        val startX = left + width - 49f
        shape.color = Color(0.04f, 0.07f, 0.09f, 0.30f)
        shape.rect(startX - 4f, legendTop - 3f, 52f, 10f)
        shape.color = Color(0.64f, 0.88f, 0.98f, 0.72f)
        shape.rect(startX, legendTop + 1f, 3f, 3f)
        shape.color = completionProductionSparkColor.cpy().apply { a = 0.74f }
        shape.rect(startX + 9f, legendTop, 4f, 5f)
        shape.color = completionResearchSparkColor.cpy().apply { a = 0.74f }
        shape.rect(startX + 18f, legendTop + 1f, 3f, 3f)
        shape.rect(startX + 17f, legendTop + 2f, 5f, 1f)
        shape.color = completionBuildSparkColor.cpy().apply { a = 0.74f }
        shape.rect(startX + 27f, legendTop + 1f, 3f, 3f)
        shape.rect(startX + 28f, legendTop - 1f, 1.5f, 7f)
        shape.color = Color(0.98f, 0.48f, 0.30f, 0.72f)
        shape.rect(startX + 36f, legendTop + 1f, 3f, 3f)
        shape.rect(startX + 34f, legendTop + 2f, 7f, 1f)
        shape.rect(startX + 37f, legendTop - 1f, 1.5f, 7f)
        shape.color = Color(0.98f, 0.86f, 0.48f, 0.72f)
        shape.circle(startX + 46f, legendTop + 2.5f, 2f)
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
        val pulse = ambientPulse(1050L)
        shape.color = Color(0.82f, 0.96f, 1.00f, 0.07f + (pulse * 0.05f))
        shape.rect(viewportLeft, viewportTop, viewportWidth, viewportHeight)
        shape.color = Color(0.92f, 0.98f, 1f, 0.66f + (pulse * 0.14f))
        shape.rect(viewportLeft - 0.8f, viewportTop - 0.8f, viewportWidth + 1.6f, viewportHeight + 1.6f)
        val corner = minOf(9f, viewportWidth * 0.28f, viewportHeight * 0.28f).coerceAtLeast(4f)
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
        val left = runtime.camera.worldToScreenX(tileX.toFloat())
        val top = runtime.camera.worldToScreenY(tileY.toFloat())
        val width = spec.width * runtime.camera.tileSize
        val height = spec.height * runtime.camera.tileSize
        val tileSize = runtime.camera.tileSize
        val fillColor = if (valid) Color(0.34f, 0.98f, 0.58f, 0.18f) else Color(1.00f, 0.34f, 0.34f, 0.18f)
        val frameColor = if (valid) Color(0.76f, 1.00f, 0.84f, 0.92f) else Color(1.00f, 0.72f, 0.72f, 0.94f)
        val accentColor = if (valid) Color(0.50f, 1.00f, 0.70f, 0.34f) else Color(1.00f, 0.48f, 0.48f, 0.32f)
        for (dx in 0 until spec.width) {
            for (dy in 0 until spec.height) {
                val cellX = left + (dx * tileSize)
                val cellY = top + (dy * tileSize)
                shape.color = fillColor
                shape.rect(cellX + 1f, cellY + 1f, tileSize - 2f, tileSize - 2f)
                shape.color = accentColor
                shape.rect(cellX + 2f, cellY + 2f, tileSize - 4f, 2f)
                if (!valid) {
                    shape.color = Color(1.00f, 0.78f, 0.78f, 0.22f)
                    shape.rectLine(cellX + 2f, cellY + 2f, cellX + tileSize - 2f, cellY + tileSize - 2f, 1.4f)
                    shape.rectLine(cellX + 2f, cellY + tileSize - 2f, cellX + tileSize - 2f, cellY + 2f, 1.4f)
                }
            }
        }
        shape.color = frameColor.cpy().mul(1f, 1f, 1f, 0.18f)
        shape.rect(left - 5f, top - 5f, width + 10f, height + 10f)
        shape.color = frameColor
        shape.rect(left - 1f, top - 1f, width + 2f, 2f)
        shape.rect(left - 1f, top + height - 1f, width + 2f, 2f)
        shape.rect(left - 1f, top, 2f, height)
        shape.rect(left + width - 1f, top, 2f, height)
        val corner = 10f
        shape.rect(left - 3f, top - 3f, corner, 2f)
        shape.rect(left - 3f, top - 3f, 2f, corner)
        shape.rect(left + width - corner + 3f, top - 3f, corner, 2f)
        shape.rect(left + width + 1f, top - 3f, 2f, corner)
        shape.rect(left - 3f, top + height + 1f, corner, 2f)
        shape.rect(left - 3f, top + height - corner + 3f, 2f, corner)
        shape.rect(left + width - corner + 3f, top + height + 1f, corner, 2f)
        shape.rect(left + width + 1f, top + height - corner + 3f, 2f, corner)
        val centerX = left + (width / 2f)
        val centerY = top + (height / 2f)
        shape.color = frameColor
        if (valid) {
            shape.rect(centerX - 1f, centerY - 8f, 2f, 16f)
            shape.rect(centerX - 8f, centerY - 1f, 16f, 2f)
            shape.circle(centerX, centerY, 4f)
        } else {
            shape.rectLine(centerX - 7f, centerY - 7f, centerX + 7f, centerY + 7f, 1.8f)
            shape.rectLine(centerX - 7f, centerY + 7f, centerX + 7f, centerY - 7f, 1.8f)
        }
    }

    private fun drawGroundPing(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val ping = runtime.currentGroundPing() ?: return
        val x = runtime.camera.worldToScreenX(ping.worldX)
        val y = runtime.camera.worldToScreenY(ping.worldY)
        val pulse = ambientPulse(760L)
        when (ping.kind) {
            GroundPingKind.MOVE -> {
                val outer = 10f + (pulse * 7f)
                val inner = 4f + (pulse * 3.5f)
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.10f + (pulse * 0.06f))
                shape.circle(x, y, outer)
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.20f + (pulse * 0.08f))
                shape.circle(x, y, inner)
                shape.color = Color(0.74f, 1.00f, 0.82f, 0.92f)
                shape.circle(x, y, 2.2f + (pulse * 1.0f))
                shape.color = Color(0.78f, 1.00f, 0.86f, 0.16f + (pulse * 0.06f))
                shape.circle(x, y, 12f + (pulse * 4f))
                shape.rect(x - 0.75f, y - 5f, 1.5f, 10f)
                shape.rect(x - 5f, y - 0.75f, 10f, 1.5f)
            }
            GroundPingKind.ATTACK -> {
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.12f + (pulse * 0.08f))
                shape.circle(x, y, 14f + (pulse * 5f))
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.22f + (pulse * 0.08f))
                shape.circle(x, y, 8f + (pulse * 3f))
                shape.color = Color(1.00f, 0.82f, 0.50f, 0.98f)
                shape.rectLine(x - 7f, y - 7f, x + 7f, y + 7f, 1.6f)
                shape.rectLine(x - 7f, y + 7f, x + 7f, y - 7f, 1.6f)
                shape.rect(x - 0.75f, y - 8.5f, 1.5f, 17f)
                shape.rect(x - 8.5f, y - 0.75f, 17f, 1.5f)
                shape.color = Color(1.00f, 0.92f, 0.72f, 0.78f)
                shape.circle(x, y, 3.2f)
            }
            GroundPingKind.BUILD -> {
                val outer = 12f + (pulse * 3f)
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.14f + (pulse * 0.08f))
                shape.rect(x - outer, y - outer, outer * 2f, outer * 2f)
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.22f + (pulse * 0.08f))
                shape.rect(x - 7f, y - 7f, 14f, 14f)
                shape.color = Color(0.84f, 0.96f, 1.00f, 0.96f)
                shape.rect(x - 6f, y - 6f, 12f, 2f)
                shape.rect(x - 6f, y + 4f, 12f, 2f)
                shape.rect(x - 6f, y - 6f, 2f, 12f)
                shape.rect(x + 4f, y - 6f, 2f, 12f)
                shape.rect(x - 9f, y - 9f, 3f, 3f)
                shape.rect(x + 6f, y - 9f, 3f, 3f)
                shape.rect(x - 9f, y + 6f, 3f, 3f)
                shape.rect(x + 6f, y + 6f, 3f, 3f)
            }
            GroundPingKind.INVALID -> {
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.12f + (pulse * 0.08f))
                shape.circle(x, y, 12f + (pulse * 4f))
                shape.color = pingTone(ping.kind).cpy().mul(1f, 1f, 1f, 0.20f + (pulse * 0.08f))
                shape.circle(x, y, 7f + (pulse * 2f))
                shape.color = Color(1.00f, 0.76f, 0.76f, 0.98f)
                shape.rectLine(x - 6.5f, y - 6.5f, x + 6.5f, y + 6.5f, 1.6f)
                shape.rectLine(x - 6.5f, y + 6.5f, x + 6.5f, y - 6.5f, 1.6f)
                shape.rect(x - 7.5f, y - 0.75f, 15f, 1.5f)
                shape.rect(x - 0.75f, y - 7.5f, 1.5f, 15f)
                shape.circle(x, y, 3f)
            }
        }
    }

    private fun drawSelectionClickPulse(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val pulse = runtime.currentSelectionClickPulse() ?: return
        val x = runtime.camera.worldToScreenX(pulse.worldX)
        val y = runtime.camera.worldToScreenY(pulse.worldY)
        drawConfirmPulse(shape, x, y, scale = 0.82f, periodMillis = 720L, alphaScale = 0.72f)
        shape.color = Color(0.78f, 1.00f, 0.84f, 0.82f)
        shape.rectLine(x - 8f, y, x - 3.5f, y, 1.2f)
        shape.rectLine(x + 3.5f, y, x + 8f, y, 1.2f)
        shape.rectLine(x, y - 8f, x, y - 3.5f, 1.2f)
        shape.rectLine(x, y + 3.5f, x, y + 8f, 1.2f)
    }

    private fun drawConfirmPulse(shape: ShapeRenderer, x: Float, y: Float, scale: Float, periodMillis: Long, alphaScale: Float = 1f) {
        val phase = ambientPulse(periodMillis)
        shape.color = Color(0.66f, 1.00f, 0.74f, (0.10f + (phase * 0.06f)) * alphaScale)
        shape.circle(x, y, (9f + (phase * 4f)) * scale)
        shape.color = Color(0.74f, 1.00f, 0.82f, (0.18f + (phase * 0.06f)) * alphaScale)
        shape.circle(x, y, (4f + (phase * 2f)) * scale)
        shape.color = Color(0.82f, 1.00f, 0.88f, 0.88f * alphaScale)
        shape.rect(x - (0.75f * scale), y - (4.5f * scale), 1.5f * scale, 9f * scale)
        shape.rect(x - (4.5f * scale), y - (0.75f * scale), 9f * scale, 1.5f * scale)
        shape.circle(x, y, 1.7f * scale)
    }

    private fun drawSelectionBox(shape: ShapeRenderer, dragBox: DragSelectionBox?) {
        if (dragBox == null || !dragBox.isVisible) return
        val minX = minOf(dragBox.startX, dragBox.currentX)
        val minY = minOf(dragBox.startY, dragBox.currentY)
        val width = abs(dragBox.currentX - dragBox.startX)
        val height = abs(dragBox.currentY - dragBox.startY)
        val right = minX + width
        val bottom = minY + height
        val corner = minOf(14f, width * 0.28f, height * 0.28f)
        val pulse = ambientPulse(1200L)

        shape.color = Color(0.52f, 0.98f, 0.54f, 0.10f + (pulse * 0.05f))
        shape.line(minX, minY, right, minY)
        shape.line(minX, bottom, right, bottom)
        shape.line(minX, minY, minX, bottom)
        shape.line(right, minY, right, bottom)

        shape.color = Color(0.70f, 1.00f, 0.70f, 0.94f)
        shape.line(minX, minY, minX + corner, minY)
        shape.line(minX, minY, minX, minY + corner)
        shape.line(right, minY, right - corner, minY)
        shape.line(right, minY, right, minY + corner)
        shape.line(minX, bottom, minX + corner, bottom)
        shape.line(minX, bottom, minX, bottom - corner)
        shape.line(right, bottom, right - corner, bottom)
        shape.line(right, bottom, right, bottom - corner)

        shape.color = Color(0.84f, 1.00f, 0.76f, 0.34f + (pulse * 0.10f))
        shape.rect(minX + (width * 0.24f), minY - 0.75f, width * 0.52f, 1.2f)
        shape.rect(minX + (width * 0.24f), bottom - 0.45f, width * 0.52f, 1.2f)
    }

    private fun drawDeathBursts(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val bursts = runtime.activeDeathBursts()
        if (bursts.isEmpty()) return
        val now = System.currentTimeMillis()
        for (burst in bursts) {
            val x = runtime.camera.worldToScreenX(burst.x)
            val y = runtime.camera.worldToScreenY(burst.y)
            if (!isOnScreen(x, y)) continue
            val progress = (1f - ((burst.expiresAtMillis - now).toFloat() / 980f)).coerceIn(0f, 1f)
            val fade = (1f - progress).coerceIn(0f, 1f)
            val core = if (burst.isStructure) 8f + (progress * 14f) else 5f + (progress * 9f)
            val outer = if (burst.isStructure) 16f + (progress * 28f) else 11f + (progress * 18f)
            shape.color = Color(1.00f, 0.58f, 0.28f, 0.22f * fade)
            shape.circle(x, y, outer)
            shape.color = Color(1.00f, 0.78f, 0.40f, 0.40f * fade)
            shape.circle(x, y, core)
            shape.color = Color(1.00f, 0.92f, 0.72f, 0.62f * fade)
            shape.circle(x, y, core * 0.42f)
            shape.color = Color(0.22f, 0.24f, 0.22f, 0.20f * fade)
            shape.circle(x + (progress * 6f), y - (progress * 10f), outer * 0.82f)
            shape.circle(x - (progress * 8f), y - (progress * 6f), outer * 0.64f)
            val shard = if (burst.isStructure) 18f else 12f
            shape.color = Color(1.00f, 0.76f, 0.44f, 0.50f * fade)
            shape.rectLine(x - shard, y, x - (shard * 0.35f), y, 2.2f)
            shape.rectLine(x + shard, y, x + (shard * 0.35f), y, 2.2f)
            shape.rectLine(x, y - shard, x, y - (shard * 0.35f), 2.2f)
            shape.rectLine(x, y + shard, x, y + (shard * 0.35f), 2.2f)
            shape.rectLine(x - (shard * 0.72f), y - (shard * 0.72f), x - (shard * 0.22f), y - (shard * 0.22f), 1.6f)
            shape.rectLine(x + (shard * 0.72f), y - (shard * 0.72f), x + (shard * 0.22f), y - (shard * 0.22f), 1.6f)
            shape.rectLine(x - (shard * 0.72f), y + (shard * 0.72f), x - (shard * 0.22f), y + (shard * 0.22f), 1.6f)
            shape.rectLine(x + (shard * 0.72f), y + (shard * 0.72f), x + (shard * 0.22f), y + (shard * 0.22f), 1.6f)
            val debrisColor =
                when {
                    burst.typeId.contains("Zergling", ignoreCase = true) -> Color(0.74f, 0.46f, 0.34f, 0.44f * fade)
                    burst.isStructure -> Color(0.52f, 0.50f, 0.44f, 0.42f * fade)
                    else -> Color(0.68f, 0.66f, 0.60f, 0.44f * fade)
                }
            shape.color = debrisColor
            shape.rect(x - (shard * 0.46f), y + (shard * 0.16f), 5f, 3f)
            shape.rect(x + (shard * 0.22f), y - (shard * 0.34f), 4f, 3f)
            if (burst.isStructure) {
                shape.rect(x - (shard * 0.10f), y + (shard * 0.40f), 7f, 4f)
                shape.rect(x - (shard * 0.58f), y - (shard * 0.28f), 6f, 4f)
                shape.color = Color(0.72f, 0.66f, 0.52f, 0.36f * fade)
                val collapseTilt = (((burst.entityId % 7) - 3) / 3f).coerceIn(-1f, 1f)
                shape.rectLine(
                    x - (shard * 1.18f),
                    y + (shard * (0.18f + (collapseTilt * 0.12f))),
                    x - (shard * 0.52f),
                    y + (shard * (0.48f + (collapseTilt * 0.08f))),
                    2.8f
                )
                shape.rectLine(
                    x + (shard * 1.12f),
                    y - (shard * (0.08f - (collapseTilt * 0.10f))),
                    x + (shard * 0.44f),
                    y - (shard * (0.34f - (collapseTilt * 0.06f))),
                    2.6f
                )
                shape.rectLine(
                    x - (shard * (0.14f + (collapseTilt * 0.18f))),
                    y + (shard * 1.18f),
                    x + (shard * (0.22f - (collapseTilt * 0.10f))),
                    y + (shard * 0.46f),
                    2.4f
                )
                shape.color = Color(0.26f, 0.24f, 0.20f, 0.28f * fade)
                shape.rect(x - (shard * 0.88f), y + (shard * 0.86f), 10f, 3.5f)
                shape.rect(x + (shard * 0.44f), y + (shard * 0.74f), 8f, 3f)
                shape.rect(x - (shard * 0.16f), y + (shard * 1.02f), 7f, 3f)
            }
        }
    }

    private fun drawDeathRemains(shape: ShapeRenderer, runtime: GdxClientRuntime) {
        val remains = runtime.activeDeathRemains()
        if (remains.isEmpty()) return
        val now = System.currentTimeMillis()
        for (remain in remains) {
            val progress = ((remain.expiresAtMillis - now).toFloat() / 2600f).coerceIn(0f, 1f)
            val settle = (1f - progress).coerceIn(0f, 1f)
            val marineRemain = remain.typeId.contains("Marine", ignoreCase = true)
            val zerglingRemain = remain.typeId.contains("Zergling", ignoreCase = true)
            val settleDrop =
                when {
                    remain.isStructure -> settle * 4.5f
                    zerglingRemain -> settle * 1.1f
                    marineRemain -> settle * 2.6f
                    else -> settle * 2.0f
                }
            val spread =
                when {
                    remain.isStructure -> settle * 2.6f
                    zerglingRemain -> settle * 2.4f
                    marineRemain -> settle * 1.5f
                    else -> settle * 1.2f
                }
            val x = runtime.camera.worldToScreenX(remain.x)
            val y = runtime.camera.worldToScreenY(remain.y) + settleDrop
            if (!isOnScreen(x, y)) continue
            val alpha = 0.08f + (progress * 0.22f)
            val hotAlpha = (progress * progress).coerceIn(0f, 1f)
            val smokeDrift = if (remain.isStructure) settle * 1.8f else settle * 0.8f
            val smokeRise = if (remain.isStructure) settle * 6f else settle * 3f
            if (remain.isStructure) {
                shape.color = Color(1.00f, 0.62f, 0.32f, 0.08f * hotAlpha)
                shape.circle(x, y, 14f + ((1f - progress) * 10f))
                shape.color = Color(0.18f, 0.18f, 0.18f, alpha)
                shape.rect(x - 12f, y - 12f, 24f, 24f)
                shape.color = Color(0.34f, 0.32f, 0.28f, alpha * 0.9f)
                shape.rect(x - 8f - spread, y - 9f, 7f, 5f)
                shape.rect(x + 1f + spread, y - 6f, 8f, 6f)
                shape.rect(x - 3f, y + 2f + (settle * 0.8f), 9f, 4f)
                shape.color = Color(0.22f, 0.20f, 0.18f, alpha * 0.86f)
                shape.rect(x - 14f - (spread * 0.6f), y + 10f, 5f, 4f)
                shape.rect(x + 8f + (spread * 0.5f), y + 6f, 4f, 5f)
                shape.color = Color(0.16f, 0.18f, 0.18f, alpha * 0.56f)
                shape.circle(x - 3f, y + 15f, 8f + ((1f - progress) * 6f))
                shape.circle(x + 6f, y + 11f, 6f + ((1f - progress) * 5f))
                shape.circle(x - 8f, y + 6f, 5f + ((1f - progress) * 4f))
                shape.color = Color(0.24f, 0.22f, 0.18f, alpha * 0.78f)
                shape.rect(x - 17f - spread, y + 5f, 7f, 4f)
                shape.rect(x + 10f + spread, y - 10f, 6f, 5f)
            } else {
                val debris =
                    when {
                        zerglingRemain -> Color(0.34f, 0.24f, 0.20f, alpha)
                        else -> Color(0.28f, 0.30f, 0.30f, alpha)
                    }
                shape.color = debris
                shape.rect(x - 6f - (spread * 0.4f), y - 2f, 5f, 3f)
                shape.rect(x + 1f + (spread * 0.5f), y - 4f, 4f, 3f)
                shape.rect(x - 1f, y + 1f + (settle * 0.3f), 3f, 2f)
                if (marineRemain) {
                    shape.color = Color(0.34f, 0.36f, 0.38f, alpha * 0.92f)
                    shape.rect(x - 3f, y - 6f + (settle * 0.4f), 6f, 2f)
                    shape.rect(x - 9f - spread, y - 1f, 3f, 2f)
                    shape.color = Color(0.20f, 0.22f, 0.24f, alpha * 0.52f)
                    shape.circle(x + 4f + (spread * 0.3f), y + 4f, 4f + ((1f - progress) * 2f))
                }
                if (zerglingRemain) {
                    shape.color = Color(0.40f, 0.28f, 0.22f, alpha * 0.88f)
                    shape.rect(x - 8f - spread, y + 1f, 5f, 2f)
                    shape.rect(x + 4f + spread, y - 1f, 4f, 2f)
                    shape.rect(x - 2f, y - 6f + (settle * 0.5f), 4f, 2f)
                    shape.color = Color(0.30f, 0.20f, 0.18f, alpha * 0.42f)
                    shape.circle(x - 2f - (spread * 0.2f), y + 5f, 3.6f + ((1f - progress) * 2f))
                    shape.circle(x + 5f + (spread * 0.4f), y + 2f, 2.8f + ((1f - progress) * 1.8f))
                }
            }
            val smokeColor =
                when {
                    remain.isStructure -> Color(0.16f, 0.18f, 0.18f, alpha * (0.52f + ((1f - progress) * 0.30f)))
                    zerglingRemain -> Color(0.20f, 0.18f, 0.16f, alpha * (0.32f + ((1f - progress) * 0.16f)))
                    else -> Color(0.16f, 0.18f, 0.18f, alpha * (0.42f + ((1f - progress) * 0.18f)))
                }
            shape.color = smokeColor
            shape.circle(x + 3f + smokeDrift, y + 6f + smokeRise, 7f + ((1f - progress) * 5f))
            shape.circle(x - 5f - (smokeDrift * 0.6f), y + 3f + (smokeRise * 0.7f), 5f + ((1f - progress) * 3f))
            if (remain.isStructure) {
                shape.circle(x + 8f + (smokeDrift * 0.8f), y + 10f + (smokeRise * 1.1f), 8f + ((1f - progress) * 6f))
            }
        }
    }

    private fun drawLabels(runtime: GdxClientRuntime, width: Int, height: Int) {
        val snapshot = runtime.snapshot ?: return
        val batch = assets.batch
        textCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        textCamera.update()
        batch.projectionMatrix = textCamera.combined
        batch.begin()
        for (node in snapshot.resourceNodes) {
            val labelX = runtime.camera.worldToScreenX(node.x) - 8f
            val labelY = height - runtime.camera.worldToScreenY(node.y) - 10f
            if (!isOnScreen(runtime.camera.worldToScreenX(node.x), runtime.camera.worldToScreenY(node.y))) continue
            assets.font.color = Color(0f, 0f, 0f, 0.58f)
            val widthHint = (node.remaining.toString().length * 7f) + 6f
            shapeRendererForLabels(batch = batch, width = widthHint, x = labelX - 2f, y = labelY - 9f, color = Color(0.04f, 0.07f, 0.09f, 0.30f))
            assets.font.draw(
                batch,
                node.remaining.toString(),
                labelX + 0.5f,
                labelY + 0.5f
            )
            assets.font.color = if (node.kind == "gas") Color(0.72f, 0.98f, 0.84f, 1f) else Color(1f, 0.92f, 0.70f, 1f)
            assets.font.draw(
                batch,
                node.remaining.toString(),
                labelX,
                labelY
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
            val labelX = runtime.camera.worldToScreenX(entity.x) + 10f
            val labelY = height - runtime.camera.worldToScreenY(entity.y) + 14f
            val widthHint = (status.length * 7f) + 8f
            shapeRendererForLabels(batch = batch, width = widthHint, x = labelX - 3f, y = labelY - 9f, color = Color(0.04f, 0.07f, 0.09f, 0.32f))
            assets.font.color = Color(0f, 0f, 0f, 0.60f)
            assets.font.draw(
                batch,
                status,
                labelX + 0.5f,
                labelY + 0.5f
            )
            assets.font.color = Color(0.94f, 0.96f, 0.98f, 1f)
            assets.font.draw(
                batch,
                status,
                labelX,
                labelY
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
        val moving = entity.pathRemainingNodes > 0
        val attackReady = if (entity.weaponCooldownTicks in 1..8) 1f - (entity.weaponCooldownTicks / 8f) else 0f
        val attackRecovery = if (entity.weaponCooldownTicks > 8) ((entity.weaponCooldownTicks.coerceAtMost(22) - 8) / 14f) else 0f
        val damageTilt = if (entity.hp < entity.maxHp) recentDamageTilt(entity) else 0f
        val bobY = unitBob(entity.id, if (moving) 1.5f else if (selected) 0.9f else 0.6f)
        val stride = moveStride(entity.id, if (moving) 1f else 0f)
        val sway = moveStride(entity.id + 17, if (moving) 0.8f else 0f)
        val lead = if (moving) (moveStride(entity.id + 31, 0.55f) + 0.55f).coerceAtLeast(0f) else 0f
        val settle = unitBob(entity.id + 23, if (moving) 0.45f else 0.2f)
        val aimBias = attackReady * 1.8f
        val recoilBack = attackRecovery * 1.4f
        val x = screenX + directionDy(entity.dir, stride * 1.2f) + directionDx(entity.dir, (lead * 0.9f) - (recoilBack * 0.45f))
        val y = screenY + bobY + directionDy(entity.dir, (lead * 0.5f) - (recoilBack * 0.30f))
        val body = Color(0.17f, 0.19f, 0.22f, 1f)
        val teamStripe = factionColor.cpy().lerp(Color.WHITE, 0.08f)
        val trim = factionColor.cpy().lerp(Color.WHITE, 0.30f)
        val shadowRadius = if (selected) 9f else 7f
        val typeName = entity.typeId.orEmpty()
        shape.color = Color(0f, 0f, 0f, 0.34f)
        val shadowX = x + 1.5f - directionDx(entity.dir, lead * 0.45f)
        val shadowY = screenY + 1.5f - directionDy(entity.dir, lead * 0.25f)
        shape.circle(shadowX, shadowY, shadowRadius)
        if (moving) {
            shape.color = Color(0f, 0f, 0f, 0.18f)
            shape.rectLine(
                shadowX - directionDx(entity.dir, 1.5f),
                shadowY - directionDy(entity.dir, 1.5f),
                shadowX - directionDx(entity.dir, 5.5f),
                shadowY - directionDy(entity.dir, 5.5f),
                shadowRadius * 1.15f
            )
        }
        when {
            entity.archetype == "worker" || typeName.contains("Worker", ignoreCase = true) -> {
                val toolSwing = stride * 1.4f
                val bodyLean = (sway * 1.2f) + (damageTilt * 0.55f)
                shape.color = body
                shape.circle(x + bodyLean * 0.25f, y + settle * 0.35f, 6.5f)
                shape.color = teamStripe
                shape.rect(x - 5f, y - 2f + (toolSwing * 0.15f), 10f, 4f)
                shape.color = trim
                shape.circle(x + bodyLean * 0.4f, y, 2.8f)
                shape.rectLine(x - 4f - bodyLean, y + 4f + toolSwing, x + 4f + bodyLean, y + 4f - toolSwing, 1.2f)
                shape.rect(x - 6.2f - bodyLean, y - 0.7f + toolSwing, 1.6f, 1.4f)
                shape.rect(x + 4.6f + bodyLean, y - 0.7f - toolSwing, 1.6f, 1.4f)
                shape.color = Color(0.94f, 0.96f, 0.98f, 0.65f)
                shape.rectLine(
                    x + directionDx(entity.dir, 0.8f),
                    y + directionDy(entity.dir, 0.8f),
                    x + directionDx(entity.dir, 5.8f) + bodyLean,
                    y + directionDy(entity.dir, 5.8f),
                    1.4f
                )
                shape.color = Color.WHITE.cpy().apply { a = 0.18f }
                shape.circle(x - 1f + bodyLean * 0.3f, y - 1f, 2.2f)
            }
            typeName.contains("Zergling", ignoreCase = true) -> {
                val lunge = stride * 1.8f
                val clawSpread = (sway * 1.5f) + (damageTilt * 0.60f)
                val attackLunge = lunge + aimBias - (attackRecovery * 0.9f)
                val crouch = attackReady * 1.8f
                shape.color = body
                shape.rect(
                    x - 6.5f + directionDx(entity.dir, attackLunge * 0.4f),
                    y - 3.2f + settle * 0.25f + crouch,
                    13f,
                    6.4f
                )
                shape.color = teamStripe
                shape.rect(x - 5.2f + directionDx(entity.dir, attackLunge * 0.5f), y - 2.2f + crouch, 10.4f, 4.4f)
                shape.color = trim
                shape.rectLine(
                    x - 5f,
                    y + 2.2f + (attackLunge * 0.15f) + crouch,
                    x + 5f,
                    y + 2.2f - (attackLunge * 0.15f) + crouch,
                    1.1f
                )
                shape.rectLine(
                    x - 4.5f - clawSpread,
                    y - 2.4f + attackLunge + crouch,
                    x - 6.8f - clawSpread,
                    y + 3.8f - attackLunge + crouch,
                    1f
                )
                shape.rectLine(
                    x + 4.5f + clawSpread,
                    y - 2.4f - attackLunge + crouch,
                    x + 6.8f + clawSpread,
                    y + 3.8f + attackLunge + crouch,
                    1f
                )
                shape.color = Color(0.98f, 0.94f, 0.74f, 0.68f)
                shape.rectLine(
                    x,
                    y + crouch,
                    x + directionDx(entity.dir, 9.2f + (aimBias * 0.9f)) + clawSpread,
                    y + directionDy(entity.dir, 9.2f + (aimBias * 0.9f)) + crouch,
                    1.6f
                )
            }
            typeName.contains("Marine", ignoreCase = true) -> {
                val march = stride * 0.8f
                val torsoLean = (sway * 0.55f) + (damageTilt * 0.40f)
                val aimLean = torsoLean + (aimBias * 0.45f) - (attackRecovery * 0.60f)
                shape.color = body
                shape.rect(x - 3.8f + aimLean, y - 6.8f + settle * 0.18f, 7.6f, 13.6f)
                shape.rect(x - 6.8f, y - 1.4f + (march * 0.7f), 13.6f, 2.8f)
                shape.color = teamStripe
                shape.rect(x - 2.6f + aimLean, y - 5.8f, 5.2f, 11.6f)
                shape.color = trim
                shape.rect(x - 1.5f + aimLean, y - 6.8f, 3f, 2.8f)
                shape.rectLine(x - 5.6f, y - 0.8f + march, x + 5.6f, y - 0.8f - march, 1.2f)
                shape.color = Color(0.98f, 0.94f, 0.74f, 0.72f)
                shape.rectLine(
                    x + aimLean,
                    y,
                    x + directionDx(entity.dir, 9.8f + (aimBias * 0.7f)) + aimLean,
                    y + directionDy(entity.dir, 9.8f + (aimBias * 0.7f)),
                    1.9f
                )
                if (attackReady > 0f) {
                    shape.color = Color(1.00f, 0.78f, 0.34f, 0.20f + (attackReady * 0.22f))
                    shape.rectLine(
                        x + directionDx(entity.dir, 4.2f),
                        y + directionDy(entity.dir, 4.2f),
                        x + directionDx(entity.dir, 8.8f),
                        y + directionDy(entity.dir, 8.8f),
                        2.3f
                    )
                }
                if (attackRecovery > 0f) {
                    shape.color = Color(0.78f, 0.82f, 0.86f, 0.12f + (attackRecovery * 0.10f))
                    shape.circle(
                        x + directionDx(entity.dir, 10.5f + (attackRecovery * 1.5f)),
                        y + directionDy(entity.dir, 10.5f + (attackRecovery * 1.5f)),
                        2.8f + (attackRecovery * 1.8f)
                    )
                    shape.circle(
                        x + directionDx(entity.dir, 13.2f + (attackRecovery * 1.8f)),
                        y + directionDy(entity.dir, 13.2f + (attackRecovery * 1.8f)),
                        2.0f + (attackRecovery * 1.4f)
                    )
                }
            }
            entity.weaponId != null -> {
                val brace = (sway * 0.35f) + (damageTilt * 0.35f)
                val aimBrace = brace + (aimBias * 0.35f) - (attackRecovery * 0.45f)
                shape.color = body
                shape.rect(x - 3.5f + aimBrace, y - 6.5f + settle * 0.15f, 7f, 13f)
                shape.rect(x - 6.5f, y - 1.8f + stride * 0.35f, 13f, 3.6f)
                shape.color = teamStripe
                shape.rect(x - 2.5f + aimBrace, y - 5.5f, 5f, 11f)
                shape.color = trim
                shape.rect(x - 1.4f + aimBrace, y - 6.5f, 2.8f, 2.8f)
                shape.rect(x - 6.5f, y - 0.8f, 2.4f, 1.6f)
                shape.rect(x + 4.1f, y - 0.8f, 2.4f, 1.6f)
                shape.color = Color(0.98f, 0.94f, 0.74f, 0.72f)
                shape.rectLine(
                    x + aimBrace,
                    y,
                    x + directionDx(entity.dir, 7.8f + (aimBias * 0.6f)) + aimBrace,
                    y + directionDy(entity.dir, 7.8f + (aimBias * 0.6f)),
                    1.8f
                )
                if (entity.weaponCooldownTicks > 0) {
                    shape.color = Color(1.00f, 0.68f, 0.32f, 0.40f)
                    shape.rectLine(x - directionDx(entity.dir, 4f), y - directionDy(entity.dir, 4f), x, y, 2.4f)
                }
                if (attackRecovery > 0f) {
                    shape.color = Color(0.74f, 0.80f, 0.84f, 0.10f + (attackRecovery * 0.08f))
                    shape.circle(
                        x + directionDx(entity.dir, 8.8f + (attackRecovery * 1.4f)),
                        y + directionDy(entity.dir, 8.8f + (attackRecovery * 1.4f)),
                        2.4f + (attackRecovery * 1.5f)
                    )
                }
                shape.color = Color.WHITE.cpy().apply { a = 0.18f }
                shape.rect(x - 1.5f, y - 4.5f, 3f, 4f)
            }
            else -> {
                val hover = settle * 0.25f
                shape.color = body
                shape.rect(x - 5.5f, y - 4.5f + hover, 11f, 9f)
                shape.color = teamStripe
                shape.rect(x - 4.5f, y - 3.5f, 9f, 7f)
                shape.color = trim
                shape.rect(x - 2.5f, y - 4.5f, 5f, 2.4f)
                shape.rect(x - 5.5f, y + 2.2f, 11f, 1.6f)
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
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        val collapseSeverity = ((0.32f - hpRatio) / 0.32f).coerceIn(0f, 1f)
        val collapseWobble = if (collapseSeverity > 0f) {
            moveStride(entity.id + 41, 1.2f + (collapseSeverity * 2.4f))
        } else {
            0f
        }
        val settleDrop = collapseSeverity * 1.4f
        val left = runtime.camera.worldToScreenX(tileX.toFloat()) + collapseWobble
        val top = runtime.camera.worldToScreenY(tileY.toFloat()) + settleDrop
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
        shape.color = Color(0.82f, 0.88f, 0.94f, 0.08f)
        shape.rect(left + 4f, top + 4f, width - 8f, (height * 0.16f).coerceAtLeast(4f))
        shape.color = Color(0f, 0f, 0f, 0.16f)
        shape.rect(left + 5f, top + height * 0.48f, width - 10f, 2f)
        shape.color =
            when {
                isGasDepot -> Color(0.26f, 0.82f, 0.60f, 0.95f)
                isResourceDepot -> Color(0.92f, 0.78f, 0.36f, 0.95f)
                else -> factionColor.cpy().lerp(Color.WHITE, 0.10f)
            }
        shape.rect(left + 4f, top + 4f, width - 8f, (height * 0.22f).coerceAtLeast(5f))
        shape.color = factionColor.cpy().lerp(Color.WHITE, 0.26f).apply { a = 0.42f }
        shape.rect(left + width - 6f, top + 5f, 2f, height - 10f)
        shape.color = Color(0.82f, 0.88f, 0.92f, 0.10f)
        shape.rect(left + 6f, top + height - 10f, width * 0.22f, 3f)
        shape.color = Color(0.82f, 0.88f, 0.93f, 0.10f)
        shape.rect(left + 6f, top + 6f, (width * 0.32f).coerceAtLeast(6f), (height * 0.18f).coerceAtLeast(4f))
        if (isDepot) {
            shape.color = Color(0.95f, 0.82f, 0.36f, 0.88f)
            shape.rect(left + width - 10f, top + 5f, 5f, 5f)
            shape.color = Color(0.88f, 0.70f, 0.24f, 0.42f)
            shape.rect(left + width * 0.25f, top + height - 6f, width * 0.5f, 3f)
            shape.color = Color(0.84f, 0.74f, 0.36f, 0.24f)
            shape.rect(left + width * 0.18f, top + height * 0.30f, width * 0.18f, height * 0.26f)
            shape.rect(left + width * 0.62f, top + height * 0.30f, width * 0.18f, height * 0.18f)
        }
        if (isResourceDepot) {
            shape.color = Color(0.95f, 0.86f, 0.42f, 0.88f)
            shape.rect(left + width - 13f, top + height - 12f, 8f, 6f)
            shape.rect(left + 6f, top + height - 12f, 8f, 6f)
            shape.color = Color(0.96f, 0.84f, 0.42f, 0.26f)
            shape.rect(left + width * 0.18f, top + height - 5f, width * 0.64f, 3f)
            shape.color = Color(1.00f, 0.90f, 0.56f, 0.24f)
            shape.rect(left + width * 0.38f, top + height * 0.24f, width * 0.24f, height * 0.22f)
            shape.rect(left + width * 0.22f, top + height * 0.46f, width * 0.56f, height * 0.10f)
        }
        if (isGasDepot) {
            shape.color = Color(0.52f, 0.98f, 0.78f, 0.82f)
            shape.circle(left + width * 0.5f, top + height * 0.55f, 6f)
            shape.color = Color(0.40f, 0.90f, 0.72f, 0.28f + (ambientPulse(1600L) * 0.12f))
            shape.circle(left + width * 0.5f, top + height * 0.55f, 9f)
            shape.color = Color(0.64f, 1.00f, 0.86f, 0.22f)
            shape.rect(left + width * 0.42f, top + height * 0.18f, width * 0.16f, height * 0.16f)
            shape.rect(left + width * 0.28f, top + height * 0.62f, width * 0.44f, height * 0.08f)
        }
        if (entity.supportsResearch == true) {
            shape.color = Color(0.66f, 0.74f, 1.00f, 0.82f)
            shape.rect(left + width - 11f, top + height - 11f, 6f, 6f)
            shape.rect(left + width - 16f, top + height - 8f, 3f, 3f)
        }
        if (entity.supportsTraining == true) {
            shape.color = Color(0.97f, 0.68f, 0.28f, 0.82f)
            shape.rect(left + 5f, top + height - 11f, 6f, 6f)
            shape.rect(left + 13f, top + height - 8f, 3f, 3f)
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
        if (collapseSeverity > 0f) {
            shape.color = Color(0.08f, 0.08f, 0.08f, 0.30f + (collapseSeverity * 0.18f))
            shape.rectLine(left + width * 0.24f, top + height * 0.18f, left + width * 0.48f, top + height * 0.48f, 1.8f)
            shape.rectLine(left + width * 0.62f, top + height * 0.22f, left + width * 0.40f, top + height * 0.64f, 1.6f)
            shape.color = Color(0.34f, 0.34f, 0.36f, 0.16f + (collapseSeverity * 0.24f))
            shape.circle(left + width * 0.32f, top + 6f, 4f + (collapseSeverity * 2f))
            shape.circle(left + width * 0.66f, top + 8f, 5f + (collapseSeverity * 3f))
            shape.color = Color(0.58f, 0.58f, 0.60f, 0.10f + (collapseSeverity * 0.18f))
            shape.circle(left + width * 0.34f, top + 4f, 6f + (collapseSeverity * 3f))
            shape.circle(left + width * 0.68f, top + 5f, 7f + (collapseSeverity * 4f))
            shape.color = Color(1.00f, 0.70f, 0.38f, 0.04f + (collapseSeverity * 0.10f))
            shape.rect(left + width * 0.18f, top + height * 0.22f, width * 0.18f, 2f)
            shape.rect(left + width * 0.58f, top + height * 0.52f, width * 0.14f, 2f)
            if (collapseSeverity > 0.40f) {
                val slump = (collapseSeverity - 0.40f) / 0.60f
                val slumpDrop = 1.2f + (slump * 4.0f)
                shape.color = Color(0.12f, 0.13f, 0.14f, 0.22f + (slump * 0.18f))
                shape.rect(left + width * 0.20f, top + height * 0.18f + slumpDrop, width * 0.54f, height * 0.12f)
                shape.color = Color(0.42f, 0.42f, 0.44f, 0.18f + (slump * 0.18f))
                shape.rect(left + width * 0.24f, top + height * 0.16f + slumpDrop, width * 0.46f, 3f)
                shape.color = Color(0.08f, 0.08f, 0.08f, 0.18f + (slump * 0.18f))
                shape.rect(left + width * 0.34f, top + height * 0.18f + slumpDrop, width * 0.14f, height * 0.18f)
            }
            if (collapseSeverity > 0.62f) {
                val fracture = (collapseSeverity - 0.62f) / 0.38f
                val emberPulse = ambientPulse(540L)
                shape.color = Color(1.00f, 0.78f, 0.46f, 0.10f + (fracture * 0.14f) + (emberPulse * 0.04f))
                shape.circle(left + width * 0.28f, top + height * 0.30f, 4f + (fracture * 4f))
                shape.circle(left + width * 0.62f, top + height * 0.26f, 3f + (fracture * 3f))
                shape.color = Color(0.60f, 0.56f, 0.48f, 0.20f + (fracture * 0.24f))
                shape.rect(left + width * 0.16f, top + height - 5f, 5f, 4f)
                shape.rect(left + width * 0.72f, top + height - 8f, 4f, 5f)
                shape.rect(left + width * 0.48f, top + height - 12f, 4f, 4f)
                shape.color = Color(0.18f, 0.18f, 0.18f, 0.16f + (fracture * 0.20f))
                shape.circle(left + width * 0.22f, top + height - 2f, 5f + (fracture * 3f))
                shape.circle(left + width * 0.70f, top + height - 3f, 4f + (fracture * 3f))
            }
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

    private fun moveStride(entityId: Int, amplitude: Float): Float {
        if (amplitude == 0f) return 0f
        val phase = ((System.currentTimeMillis() % 520L).toFloat() / 520f) + ((entityId % 7) * 0.11f)
        return kotlin.math.sin(phase * Math.PI * 2.0).toFloat() * amplitude
    }

    private fun recentDamageTilt(entity: EntitySnapshot): Float {
        val phase = ((System.currentTimeMillis() % 220L).toFloat() / 220f) + ((entity.id % 9) * 0.09f)
        val base = kotlin.math.sin(phase * Math.PI * 2.0).toFloat()
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        val severity = ((1f - hpRatio) * 0.28f).coerceIn(0f, 0.28f)
        return base * severity
    }

    private fun damageRecoilOffset(runtime: GdxClientRuntime, snapshot: ClientSnapshot, entity: EntitySnapshot): Pair<Float, Float> {
        if (!runtime.isDamageFlashActive(entity.id)) return 0f to 0f
        val attacker = nearestHostile(snapshot, entity) ?: return 0f to 0f
        val dir = directionTo(attacker.x, attacker.y, entity.x, entity.y)
        val scale = if (entity.footprintWidth != null && entity.footprintHeight != null) 1.6f else 2.8f
        return directionDx(dir, scale) to directionDy(dir, scale)
    }

    private fun criticalShakeOffset(entity: EntitySnapshot): Pair<Float, Float> {
        val hpRatio = entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1).toFloat()
        if (hpRatio > 0.35f) return 0f to 0f
        val severity = ((0.35f - hpRatio) / 0.35f).coerceIn(0f, 1f)
        val phase = ((System.currentTimeMillis() % 170L).toFloat() / 170f) + ((entity.id % 11) * 0.08f)
        val lateral = kotlin.math.sin(phase * Math.PI * 2.0).toFloat() * (0.7f + (severity * 1.9f))
        val vertical = kotlin.math.cos(phase * Math.PI * 2.0).toFloat() * (0.2f + (severity * 0.9f))
        return lateral to vertical
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

    private fun nearestHostile(snapshot: ClientSnapshot, entity: EntitySnapshot): EntitySnapshot? =
        snapshot.entities
            .asSequence()
            .filter { it.faction > 0 && it.faction != entity.faction }
            .minByOrNull { distanceSq(entity.x, entity.y, it.x, it.y) }

    private fun isMeleeAttacker(entity: EntitySnapshot): Boolean = isMeleeWeapon(entity)

    private fun isMeleeWeapon(entity: EntitySnapshot): Boolean =
        entity.weaponId?.contains("Claw", ignoreCase = true) == true ||
            entity.typeId.contains("Zergling", ignoreCase = true)

    private fun orderMarkerKind(entity: EntitySnapshot): GroundPingKind =
        when {
            entity.buildTargetId != null -> GroundPingKind.BUILD
            entity.activeOrder.equals("attack", ignoreCase = true) || entity.activeOrder.equals("attackMove", ignoreCase = true) -> GroundPingKind.ATTACK
            else -> GroundPingKind.MOVE
        }

    private fun distanceSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return (dx * dx) + (dy * dy)
    }

    private fun directionTo(fromX: Float, fromY: Float, toX: Float, toY: Float): Float =
        kotlin.math.atan2((toY - fromY), (toX - fromX))

    private fun completionFlashColor(runtime: GdxClientRuntime, entityId: Int): Color =
        when (runtime.completionFlashKind(entityId)) {
            CompletionFlashKind.CONSTRUCTION -> completionBuildFlashColor
            CompletionFlashKind.PRODUCTION -> completionProductionFlashColor
            CompletionFlashKind.RESEARCH, null -> completionResearchFlashColor
        }

    private fun completionSparkColor(runtime: GdxClientRuntime, entityId: Int): Color =
        when (runtime.completionFlashKind(entityId)) {
            CompletionFlashKind.CONSTRUCTION -> completionBuildSparkColor
            CompletionFlashKind.PRODUCTION -> completionProductionSparkColor
            CompletionFlashKind.RESEARCH, null -> completionResearchSparkColor
        }

    private fun impactFlashForEntity(runtime: GdxClientRuntime, entityId: Int): Color =
        when (runtime.damageImpactKind(entityId)) {
            CombatSoundKind.MELEE,
            CombatSoundKind.ZERGLING_MELEE -> meleeImpactFlashColor
            CombatSoundKind.MARINE_RANGED,
            CombatSoundKind.RANGED, null -> impactFlashColor
        }

    private fun impactSparkForEntity(runtime: GdxClientRuntime, entityId: Int): Color =
        when (runtime.damageImpactKind(entityId)) {
            CombatSoundKind.MELEE,
            CombatSoundKind.ZERGLING_MELEE -> meleeImpactSparkColor
            CombatSoundKind.MARINE_RANGED,
            CombatSoundKind.RANGED, null -> impactSparkColor
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

    private fun shapeRendererForLabels(
        batch: com.badlogic.gdx.graphics.g2d.SpriteBatch,
        width: Float,
        x: Float,
        y: Float,
        color: Color
    ) {
        batch.end()
        val shape = assets.shapeRenderer
        shape.projectionMatrix = textCamera.combined
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.color = color
        shape.rect(x, y, width, 8f)
        shape.color = color.cpy().lerp(Color.WHITE, 0.05f).apply { a = color.a * 0.52f }
        shape.rect(x, y, width, 1f)
        shape.end()
        batch.begin()
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
