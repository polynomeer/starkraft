package starkraft.sim.ecs

import starkraft.sim.data.DataRepo
import kotlin.math.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue

class MovementSystem(
    private val world: World,
    private val map: MapGrid,
    private val pathPool: PathPool,
    private val pathQueue: PathRequestQueue
) {
    private val speed = 0.06f // tiles/tick demo speed
    private val arrivalEps = 0.05f

    fun tick() {
        for (id in world.alive) {
            val tr = world.transforms[id] ?: continue
            val q = world.orders[id]?.items ?: continue
            if (q.isNotEmpty()) {
                val o = q.first()
                if (o is Order.Move) {
                    val pf = world.pathFollows[id]
                    if (pf == null) {
                        tryEnqueuePath(id)
                        continue
                    }

                    if (pf.index >= pf.length) {
                        world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                        q.removeFirst()
                        continue
                    }

                    val curX = floor(tr.x).toInt()
                    val curY = floor(tr.y).toInt()
                    val node = pf.nodes[pf.index]
                    val nx = node % map.width
                    val ny = node / map.width

                    val tx = nx + 0.5f
                    val ty = ny + 0.5f
                    val dx = tx - tr.x
                    val dy = ty - tr.y
                    val dist = hypot(dx, dy)
                    if (dist <= arrivalEps) {
                        pf.index++
                    } else {
                        val step = min(speed, dist)
                        tr.x += (dx / dist) * step
                        tr.y += (dy / dist) * step
                    }
                }
            }
        }
    }

    private fun tryEnqueuePath(id: EntityId) {
        if (pathQueue.enqueue(id)) {
            // queued
        }
    }
}

class CombatSystem(private val world: World, private val data: DataRepo) {
    fun tick() {
        // ① 진영별 enemy 리스트를 틱 단위로 스냅샷 (맵 순회/필터 비용을 1회로 집약)
        val enemiesCache: Map<Int, IntArray> = buildEnemyCache()

        for (id in world.alive) {
            val w = world.weapons[id] ?: continue
            if (w.cooldownTicks > 0) {
                w.cooldownTicks--; continue
            }

            val unitTag = world.tags[id]!!
            val tr = world.transforms[id]!!
            val def = data.weapon(w.id)
            val rng2 = def.range * def.range

            // ② 아군은 제외된 "적 리스트"만 순회
            val enemies = enemiesCache[unitTag.faction] ?: IntArray(0)
            var best: EntityId = 0
            var bestD2 = Float.POSITIVE_INFINITY
            for (other in enemies) {
                if (other == id) continue
                val oHp = world.healths[other] ?: continue
                if (oHp.hp <= 0) continue
                val otr = world.transforms[other] ?: continue
                val dx = otr.x - tr.x
                val dy = otr.y - tr.y
                val d2 = dx * dx + dy * dy
                if (d2 <= rng2 && d2 < bestD2) {
                    best = other; bestD2 = d2
                }
            }

            if (best != 0) {
                val targetHp = world.healths[best]!!
                val dmg = kotlin.math.max(0, def.damage - targetHp.armor)
                targetHp.hp -= dmg
                w.cooldownTicks = def.cooldownTicks
                if (targetHp.hp <= 0) world.remove(best)
            }
        }
    }

    private fun buildEnemyCache(): Map<Int, IntArray> {
        val factions = world.tags.values.asSequence().map { it.faction }.toSet()
        val cache = mutableMapOf<Int, IntArray>()
        for (f in factions) {
            val enemies = world.index.enemiesOf(f)
                .filter { id -> (world.healths[id]?.hp ?: 0) > 0 }
                .toList()
                .toIntArray()
            cache[f] = enemies
        }
        return cache
    }
}

class VisionSystem(
    private val world: World,
    private val fogTeam1: starkraft.sim.ecs.services.FogGrid,
    private val fogTeam2: starkraft.sim.ecs.services.FogGrid
) {
    fun tick() {
        fogTeam1.clear(); fogTeam2.clear()
        for (id in world.alive) {
            val v = world.visions[id] ?: continue
            val tr = world.transforms[id] ?: continue
            val tag = world.tags[id] ?: continue
            if (tag.faction == 1) fogTeam1.markVisible(tr.x, tr.y, v.range)
            else if (tag.faction == 2) fogTeam2.markVisible(tr.x, tr.y, v.range)
        }
    }
}
