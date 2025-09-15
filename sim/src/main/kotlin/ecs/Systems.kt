package starkraft.sim.ecs

import starkraft.sim.data.DataRepo
import kotlin.math.*

class MovementSystem(private val world: World) {
    fun tick() {
        for (id in world.alive) {
            val q = world.orders[id]?.items ?: continue
            if (q.isNotEmpty()) {
                val o = q.first()
                if (o is Order.Move) {
                    val tr = world.transforms[id]!!
                    val dx = o.tx - tr.x;
                    val dy = o.ty - tr.y
                    val dist = hypot(dx, dy)
                    val speed = 0.06f // tiles/tick demo speed
                    if (dist < speed) {
                        tr.x = o.tx; tr.y = o.ty; q.removeFirst()
                    } else {
                        tr.x += (dx / dist) * speed; tr.y += (dy / dist) * speed
                    }
                }
            }
        }
    }
}

class CombatSystem(private val world: World, private val data: DataRepo) {
    fun tick() {
        // Cooldowns & simple auto-targeting within range
        for (id in world.alive) {
            val w = world.weapons[id] ?: continue
            if (w.cooldownTicks > 0) {
                w.cooldownTicks--; continue
            }
            val unit = world.tags[id]!!
            val tr = world.transforms[id]!!
            val def = data.weapon(w.id)
            val rng2 = def.range * def.range
            // find nearest enemy
            var best: EntityId? = null
            var bestD2 = Float.POSITIVE_INFINITY
            for (other in world.alive) {
                if (other == id) continue
                val oTag = world.tags[other]!!
                if (oTag.faction == unit.faction) continue
                val otr = world.transforms[other]!!
                val d2 = (otr.x - tr.x).let { dx -> dx * dx } + (otr.y - tr.y).let { dy -> dy * dy }
                if (d2 <= rng2 && d2 < bestD2) {
                    best = other; bestD2 = d2
                }
            }
            if (best != null) {
                // fire
                val targetHp = world.healths[best!!]!!
                val dmg = max(0, def.damage - targetHp.armor)
                targetHp.hp -= dmg
                w.cooldownTicks = def.cooldownTicks
                if (targetHp.hp <= 0) world.remove(best!!)
            }
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
        for (id in world.alive) {
            val v = world.visions[id] ?: continue
            val tr = world.transforms[id] ?: continue
            val tag = world.tags[id] ?: continue
            if (tag.faction == 1) fogTeam1.markVisible(tr.x, tr.y, v.range)
            else if (tag.faction == 2) fogTeam2.markVisible(tr.x, tr.y, v.range)
        }
    }
}
