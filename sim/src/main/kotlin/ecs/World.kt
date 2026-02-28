package starkraft.sim.ecs

class World {
    val transforms = mutableMapOf<EntityId, Transform>()
    val motions = mutableMapOf<EntityId, Motion>()
    val tags = mutableMapOf<EntityId, UnitTag>()
    val healths = mutableMapOf<EntityId, Health>()
    val weapons = mutableMapOf<EntityId, WeaponRef>()
    val orders = mutableMapOf<EntityId, OrderQueue>()
    val visions = mutableMapOf<EntityId, Vision>()
    val pathFollows = mutableMapOf<EntityId, PathFollow>()
    val repathCooldowns = mutableMapOf<EntityId, RepathCooldown>()
    val stucks = mutableMapOf<EntityId, StuckTracker>()

    val index = FactionIndex(this) // NEW
    val aliveSnapshot = AliveSnapshot(IntArray(64), 0)

    val alive: Sequence<EntityId>
        get() =
            transforms.keys.asSequence().filter { healths[it]?.hp ?: 0 > 0 }

    fun updateAliveSnapshot() {
        var ids = aliveSnapshot.ids
        var count = 0
        for (id in transforms.keys) {
            if ((healths[id]?.hp ?: 0) <= 0) continue
            if (count >= ids.size) {
                ids = ids.copyOf(ids.size * 2)
            }
            ids[count++] = id
        }
        aliveSnapshot.ids = ids
        aliveSnapshot.count = count
    }

    fun spawn(t: Transform, tag: UnitTag, h: Health, w: WeaponRef?, v: Vision? = null): EntityId {
        val id = Ids.newId()
        transforms[id] = t
        tags[id] = tag
        healths[id] = h
        if (w != null) weapons[id] = w
        orders[id] = OrderQueue()
        motions[id] = Motion()
        if (v != null) visions[id] = v
        repathCooldowns[id] = RepathCooldown()
        stucks[id] = StuckTracker(t.x, t.y, 0)
        index.add(id, tag.faction)       // keep index updated
        return id
    }

    fun remove(id: EntityId) {
        val f = tags[id]?.faction
        if (f != null) index.remove(id, f)
        transforms.remove(id); motions.remove(id); tags.remove(id); healths.remove(id);
        weapons.remove(id); orders.remove(id); visions.remove(id)
        pathFollows.remove(id); repathCooldowns.remove(id); stucks.remove(id)
    }
}

data class AliveSnapshot(var ids: IntArray, var count: Int)
