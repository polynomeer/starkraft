package starkraft.sim.ecs

import starkraft.sim.data.DataRepo
import kotlin.math.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue

class AliveSystem(private val world: World) {
    fun tick() {
        world.updateAliveSnapshot()
    }
}

class MovementSystem(
    private val world: World,
    private val map: MapGrid,
    private val occ: OccupancyGrid,
    private val pathPool: PathPool,
    private val pathQueue: PathRequestQueue
) {
    private val speed = 0.06f // tiles/tick demo speed
    private val arrivalEps = 0.05f
    private val repathCooldownTicks = 10
    private val stuckThresholdTicks = 20
    private val stuckDist2 = 0.0001f

    var lastTickReplans: Int = 0
        private set

    fun tick() {
        pathQueue.beginTick()
        lastTickReplans = 0
        val alive = world.aliveSnapshot
        val ids = alive.ids
        val count = alive.count
        for (i in 0 until count) {
            val id = ids[i]
            val tr = world.transforms[id] ?: continue
            val cooldown = world.repathCooldowns[id]
            if (cooldown != null && cooldown.ticks > 0) cooldown.ticks--
            val stuck = world.stucks[id]
            if (stuck != null) {
                val dx = tr.x - stuck.lastX
                val dy = tr.y - stuck.lastY
                if (dx * dx + dy * dy < stuckDist2) stuck.ticks++ else {
                    stuck.ticks = 0
                    stuck.lastX = tr.x
                    stuck.lastY = tr.y
                }
            }

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

                    val blocked =
                        !map.isPassable(nx, ny) || occ.isBlockedAllowing(nx, ny, curX, curY) || isDiagonalCornerBlocked(curX, curY, nx, ny)
                    val isStuck = stuck != null && stuck.ticks >= stuckThresholdTicks
                    if ((blocked || isStuck) && cooldown != null && cooldown.ticks == 0) {
                        if (pathQueue.enqueue(id)) {
                            cooldown.ticks = repathCooldownTicks
                            world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                            lastTickReplans++
                        }
                        continue
                    }

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
        val cooldown = world.repathCooldowns[id]
        if (cooldown != null && cooldown.ticks > 0) return
        if (pathQueue.enqueue(id)) {
            if (cooldown != null) cooldown.ticks = repathCooldownTicks
            lastTickReplans++
        }
    }

    private fun isDiagonalCornerBlocked(cx: Int, cy: Int, nx: Int, ny: Int): Boolean {
        if (cx == nx || cy == ny) return false
        val ox = nx
        val oy = cy
        val px = cx
        val py = ny
        if (!map.isPassable(ox, oy) || occ.isBlocked(ox, oy)) return true
        if (!map.isPassable(px, py) || occ.isBlocked(px, py)) return true
        return false
    }
}

class CombatSystem(private val world: World, private val data: DataRepo) {
    private val enemyLists = mutableMapOf<Int, EnemyList>()
    private val emptyIds = IntArray(0)

    fun tick() {
        // ① 진영별 enemy 리스트를 틱 단위로 스냅샷 (맵 순회/필터 비용을 1회로 집약)
        val enemiesCache: Map<Int, EnemyList> = buildEnemyCache()

        val alive = world.aliveSnapshot
        val ids = alive.ids
        val count = alive.count
        for (i in 0 until count) {
            val id = ids[i]
            val w = world.weapons[id] ?: continue
            if (w.cooldownTicks > 0) {
                w.cooldownTicks--; continue
            }

            val unitTag = world.tags[id]!!
            val tr = world.transforms[id]!!
            val def = data.weapon(w.id)
            val rng2 = def.range * def.range

            // ② 아군은 제외된 "적 리스트"만 순회
            val enemies = enemiesCache[unitTag.faction]
            val enemyIds = enemies?.ids ?: emptyIds
            val enemyCount = enemies?.count ?: 0
            var best: EntityId = 0
            var bestD2 = Float.POSITIVE_INFINITY
            for (i in 0 until enemyCount) {
                val other = enemyIds[i]
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

    private fun buildEnemyCache(): Map<Int, EnemyList> {
        val factions = world.tags.values.asSequence().map { it.faction }.toSet()
        val cache = mutableMapOf<Int, EnemyList>()
        for (f in factions) {
            val list = enemyLists.getOrPut(f) { EnemyList(IntArray(32), 0) }
            var count = 0
            val seq = world.index.enemiesOf(f)
            for (id in seq) {
                if ((world.healths[id]?.hp ?: 0) <= 0) continue
                if (count >= list.ids.size) {
                    list.ids = list.ids.copyOf(list.ids.size * 2)
                }
                list.ids[count++] = id
            }
            list.count = count
            cache[f] = list
        }
        return cache
    }
}

private data class EnemyList(var ids: IntArray, var count: Int)

class OccupancySystem(private val world: World, private val occ: OccupancyGrid) {
    fun tick() {
        occ.clearDynamic()
        val alive = world.aliveSnapshot
        val ids = alive.ids
        val count = alive.count
        for (i in 0 until count) {
            val id = ids[i]
            val tr = world.transforms[id] ?: continue
            val x = floor(tr.x).toInt()
            val y = floor(tr.y).toInt()
            occ.addDynamic(x, y)
        }
    }
}

class VisionSystem(
    private val world: World,
    private val fogTeam1: starkraft.sim.ecs.services.FogGrid,
    private val fogTeam2: starkraft.sim.ecs.services.FogGrid
) {
    fun tick() {
        fogTeam1.clear(); fogTeam2.clear()
        val alive = world.aliveSnapshot
        val ids = alive.ids
        val count = alive.count
        for (i in 0 until count) {
            val id = ids[i]
            val v = world.visions[id] ?: continue
            val tr = world.transforms[id] ?: continue
            val tag = world.tags[id] ?: continue
            if (tag.faction == 1) fogTeam1.markVisible(tr.x, tr.y, v.range)
            else if (tag.faction == 2) fogTeam2.markVisible(tr.x, tr.y, v.range)
        }
    }
}
