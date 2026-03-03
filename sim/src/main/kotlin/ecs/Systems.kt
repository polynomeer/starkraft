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
    private val pathQueue: PathRequestQueue,
    private val data: DataRepo? = null
) {
    private val speed = 0.06f // tiles/tick demo speed
    private val arrivalEps = 0.05f
    private val repathCooldownTicks = 10
    private val stuckThresholdTicks = 20
    private val stuckDist2 = 0.0001f

    var lastTickReplans: Int = 0
        private set
    var lastTickReplansBlocked: Int = 0
        private set
    var lastTickReplansStuck: Int = 0
        private set
    private var progressEntityIds = IntArray(16)
    private var progressWaypointIndices = IntArray(16)
    private var progressRemainingNodes = IntArray(16)
    private var progressCompleted = BooleanArray(16)
    var lastTickProgressCount: Int = 0
        private set

    fun tick() {
        pathQueue.beginTick()
        lastTickReplans = 0
        lastTickReplansBlocked = 0
        lastTickReplansStuck = 0
        lastTickProgressCount = 0
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
                if (o is Order.Move || o is Order.AttackMove) {
                    val pf = world.pathFollows[id]
                    if (pf == null) {
                        tryEnqueuePath(id)
                        continue
                    }

                    if (pf.index >= pf.length) {
                        recordProgress(id, pf.index, 0, completed = true)
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
                            if (blocked) lastTickReplansBlocked++
                            if (isStuck) lastTickReplansStuck++
                        }
                        continue
                    }

                    val tx = nx + 0.5f
                    val ty = ny + 0.5f
                    val dx = tx - tr.x
                    val dy = ty - tr.y
                    val dist = hypot(dx, dy)
                    if (o is Order.AttackMove && hasEnemyInRange(id, tr)) {
                        continue
                    }
                    if (dist <= arrivalEps) {
                        pf.index++
                        val remaining = (pf.length - pf.index).coerceAtLeast(0)
                        recordProgress(id, pf.index, remaining, completed = pf.index >= pf.length)
                        if (pf.index >= pf.length) {
                            world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                            q.removeFirst()
                        }
                    } else {
                        val step = min(speed, dist)
                        tr.x += (dx / dist) * step
                        tr.y += (dy / dist) * step
                    }
                } else if (o is Order.Attack) {
                    val targetHp = world.healths[o.target]
                    val targetTransform = world.transforms[o.target]
                    val targetTag = world.tags[o.target]
                    val unitTag = world.tags[id]
                    if (targetHp == null || targetTransform == null || targetHp.hp <= 0 || targetTag == null || unitTag == null || targetTag.faction == unitTag.faction) {
                        world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                        q.removeFirst()
                        continue
                    }

                    if (isAttackTargetInRange(id, tr, targetTransform)) {
                        world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                        continue
                    }

                    val pf = world.pathFollows[id]
                    val targetTileX = floor(targetTransform.x).toInt()
                    val targetTileY = floor(targetTransform.y).toInt()
                    val needsGoalRefresh = pf != null && (pf.goalX != targetTileX || pf.goalY != targetTileY)
                    if ((pf == null || needsGoalRefresh) && cooldown != null && cooldown.ticks == 0) {
                        if (pf != null) {
                            world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                        }
                        if (pathQueue.enqueue(id)) {
                            cooldown.ticks = repathCooldownTicks
                            lastTickReplans++
                        }
                        continue
                    }
                    if (pf == null) continue

                    if (pf.index >= pf.length) {
                        world.pathFollows.remove(id)?.let { pathPool.recycle(it.nodes) }
                        if (cooldown != null && cooldown.ticks == 0 && pathQueue.enqueue(id)) {
                            cooldown.ticks = repathCooldownTicks
                            lastTickReplans++
                        }
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
                            if (blocked) lastTickReplansBlocked++
                            if (isStuck) lastTickReplansStuck++
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
                        val remaining = (pf.length - pf.index).coerceAtLeast(0)
                        recordProgress(id, pf.index, remaining, completed = pf.index >= pf.length)
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
            // This is a "missing path" request, not caused by blocked/stuck.
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

    private fun isAttackTargetInRange(id: Int, source: Transform, target: Transform): Boolean {
        val weapon = world.weapons[id] ?: return false
        val def = data?.weapon(weapon.id) ?: return false
        val dx = target.x - source.x
        val dy = target.y - source.y
        return dx * dx + dy * dy <= def.range * def.range
    }

    private fun hasEnemyInRange(id: Int, source: Transform): Boolean {
        val weapon = world.weapons[id] ?: return false
        val tag = world.tags[id] ?: return false
        val def = data?.weapon(weapon.id) ?: return false
        val range2 = def.range * def.range
        val alive = world.aliveSnapshot
        val ids = alive.ids
        for (i in 0 until alive.count) {
            val other = ids[i]
            if (other == id) continue
            val otherTag = world.tags[other] ?: continue
            if (otherTag.faction == tag.faction) continue
            val hp = world.healths[other] ?: continue
            if (hp.hp <= 0) continue
            val target = world.transforms[other] ?: continue
            val dx = target.x - source.x
            val dy = target.y - source.y
            if (dx * dx + dy * dy <= range2) return true
        }
        return false
    }

    fun progressEntityId(index: Int): Int = progressEntityIds[index]

    fun progressWaypointIndex(index: Int): Int = progressWaypointIndices[index]

    fun progressRemainingNodes(index: Int): Int = progressRemainingNodes[index]

    fun progressCompleted(index: Int): Boolean = progressCompleted[index]

    private fun recordProgress(entityId: Int, waypointIndex: Int, remainingNodes: Int, completed: Boolean) {
        val index = lastTickProgressCount
        if (index >= progressEntityIds.size) {
            val nextSize = progressEntityIds.size * 2
            progressEntityIds = progressEntityIds.copyOf(nextSize)
            progressWaypointIndices = progressWaypointIndices.copyOf(nextSize)
            progressRemainingNodes = progressRemainingNodes.copyOf(nextSize)
            progressCompleted = progressCompleted.copyOf(nextSize)
        }
        progressEntityIds[index] = entityId
        progressWaypointIndices[index] = waypointIndex
        progressRemainingNodes[index] = remainingNodes
        progressCompleted[index] = completed
        lastTickProgressCount = index + 1
    }
}

class CombatSystem(private val world: World, private val data: DataRepo) {
    private val enemyLists = mutableMapOf<Int, EnemyList>()
    private val emptyIds = IntArray(0)
    private var eventAttackers = IntArray(16)
    private var eventTargets = IntArray(16)
    private var eventDamage = IntArray(16)
    private var eventTargetHp = IntArray(16)
    private var eventKilled = BooleanArray(16)
    private var plannedAttackers = IntArray(16)
    private var plannedTargets = IntArray(16)
    private var plannedDamage = IntArray(16)
    private var plannedOrdered = BooleanArray(16)
    private var plannedCount = 0
    private var reservedDamage = IntArray(32)
    private var reservedTouched = IntArray(16)
    private var reservedTouchedCount = 0
    var lastTickEventCount = 0
        private set
    var lastTickAttacks = 0
        private set
    var lastTickKills = 0
        private set

    fun tick() {
        lastTickEventCount = 0
        lastTickAttacks = 0
        lastTickKills = 0
        plannedCount = 0
        clearReservedDamage()
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
            var best: EntityId = 0
            var bestD2 = Float.POSITIVE_INFINITY
            var bestSurvivesHit = false
            var orderedTarget = false
            val attackOrder = world.orders[id]?.items?.firstOrNull() as? Order.Attack
            if (attackOrder != null) {
                val target = attackOrder.target
                val targetTag = world.tags[target]
                val targetHp = world.healths[target]
                val targetTransform = world.transforms[target]
                if (targetTag == null || targetHp == null || targetTransform == null || targetHp.hp <= 0 || targetTag.faction == unitTag.faction) {
                    world.orders[id]?.items?.removeFirst()
                    world.pathFollows.remove(id)
                } else {
                    val dx = targetTransform.x - tr.x
                    val dy = targetTransform.y - tr.y
                    val d2 = dx * dx + dy * dy
                    if (d2 <= rng2) {
                        best = target
                        bestD2 = d2
                        bestSurvivesHit = targetHp.hp > kotlin.math.max(0, def.damage - targetHp.armor)
                        orderedTarget = true
                    }
                }
            }

            if (best == 0) {
                val enemies = enemiesCache[unitTag.faction]
                val enemyIds = enemies?.ids ?: emptyIds
                val enemyCount = enemies?.count ?: 0
                for (i in 0 until enemyCount) {
                    val other = enemyIds[i]
                    if (other == id) continue
                    val oHp = world.healths[other] ?: continue
                    if (oHp.hp <= 0) continue
                    val otr = world.transforms[other] ?: continue
                    val dx = otr.x - tr.x
                    val dy = otr.y - tr.y
                    val d2 = dx * dx + dy * dy
                    if (d2 > rng2) continue
                    val effectiveHp = oHp.hp - reservedDamageAt(other)
                    if (effectiveHp <= 0) continue
                    val dmg = kotlin.math.max(0, def.damage - oHp.armor)
                    val survivesHit = effectiveHp > dmg
                    if (
                        best == 0 ||
                        (survivesHit && !bestSurvivesHit) ||
                        (survivesHit == bestSurvivesHit && d2 < bestD2)
                    ) {
                        best = other
                        bestD2 = d2
                        bestSurvivesHit = survivesHit
                    }
                }
            }

            if (best != 0) {
                val targetHp = world.healths[best] ?: continue
                val dmg = kotlin.math.max(0, def.damage - targetHp.armor)
                reserveDamage(best, dmg)
                recordPlannedAttack(id, best, dmg, orderedTarget)
            }
        }

        for (i in 0 until plannedCount) {
            val attackerId = plannedAttackers[i]
            val weapon = world.weapons[attackerId] ?: continue
            weapon.cooldownTicks = data.weapon(weapon.id).cooldownTicks

            val targetId = plannedTargets[i]
            val targetHp = world.healths[targetId]
            if (targetHp == null || targetHp.hp <= 0) continue

            val dmg = plannedDamage[i]
            targetHp.hp -= dmg
            val killed = targetHp.hp <= 0
            recordEvent(attackerId, targetId, dmg, targetHp.hp, killed)
            if (killed) {
                world.remove(targetId, reason = "death")
                if (plannedOrdered[i]) {
                    val q = world.orders[attackerId]?.items
                    if (q?.firstOrNull() is Order.Attack && (q.firstOrNull() as Order.Attack).target == targetId) {
                        q.removeFirst()
                    }
                }
            }
        }
    }

    fun eventAttacker(index: Int): Int = eventAttackers[index]

    fun eventTarget(index: Int): Int = eventTargets[index]

    fun eventDamage(index: Int): Int = eventDamage[index]

    fun eventTargetHp(index: Int): Int = eventTargetHp[index]

    fun eventKilled(index: Int): Boolean = eventKilled[index]

    private fun reservedDamageAt(id: Int): Int {
        return if (id < reservedDamage.size) reservedDamage[id] else 0
    }

    private fun reserveDamage(id: Int, damage: Int) {
        if (damage <= 0) return
        ensureReservedCapacity(id)
        if (reservedDamage[id] == 0) {
            if (reservedTouchedCount >= reservedTouched.size) {
                reservedTouched = reservedTouched.copyOf(reservedTouched.size * 2)
            }
            reservedTouched[reservedTouchedCount++] = id
        }
        reservedDamage[id] += damage
    }

    private fun clearReservedDamage() {
        for (i in 0 until reservedTouchedCount) {
            reservedDamage[reservedTouched[i]] = 0
        }
        reservedTouchedCount = 0
    }

    private fun ensureReservedCapacity(id: Int) {
        if (id < reservedDamage.size) return
        var nextSize = reservedDamage.size
        while (id >= nextSize) nextSize *= 2
        reservedDamage = reservedDamage.copyOf(nextSize)
    }

    private fun recordPlannedAttack(attacker: Int, target: Int, damage: Int, ordered: Boolean) {
        val index = plannedCount
        if (index >= plannedAttackers.size) {
            val nextSize = plannedAttackers.size * 2
            plannedAttackers = plannedAttackers.copyOf(nextSize)
            plannedTargets = plannedTargets.copyOf(nextSize)
            plannedDamage = plannedDamage.copyOf(nextSize)
            plannedOrdered = plannedOrdered.copyOf(nextSize)
        }
        plannedAttackers[index] = attacker
        plannedTargets[index] = target
        plannedDamage[index] = damage
        plannedOrdered[index] = ordered
        plannedCount = index + 1
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

    private fun recordEvent(attacker: Int, target: Int, damage: Int, targetHp: Int, killed: Boolean) {
        val index = lastTickEventCount
        if (index >= eventAttackers.size) {
            val nextSize = eventAttackers.size * 2
            eventAttackers = eventAttackers.copyOf(nextSize)
            eventTargets = eventTargets.copyOf(nextSize)
            eventDamage = eventDamage.copyOf(nextSize)
            eventTargetHp = eventTargetHp.copyOf(nextSize)
            eventKilled = eventKilled.copyOf(nextSize)
        }
        eventAttackers[index] = attacker
        eventTargets[index] = target
        eventDamage[index] = damage
        eventTargetHp[index] = targetHp
        eventKilled[index] = killed
        lastTickEventCount = index + 1
        lastTickAttacks++
        if (killed) lastTickKills++
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
