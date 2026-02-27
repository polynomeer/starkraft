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

    val index = FactionIndex(this) // NEW

    val alive: Sequence<EntityId>
        get() =
            transforms.keys.asSequence().filter { healths[it]?.hp ?: 0 > 0 }

    fun spawn(t: Transform, tag: UnitTag, h: Health, w: WeaponRef?, v: Vision? = null): EntityId {
        val id = Ids.newId()
        transforms[id] = t
        tags[id] = tag
        healths[id] = h
        if (w != null) weapons[id] = w
        orders[id] = OrderQueue()
        motions[id] = Motion()
        if (v != null) visions[id] = v
        index.add(id, tag.faction)       // keep index updated
        return id
    }

    fun remove(id: EntityId) {
        val f = tags[id]?.faction
        if (f != null) index.remove(id, f)
        transforms.remove(id); motions.remove(id); tags.remove(id); healths.remove(id);
        weapons.remove(id); orders.remove(id); visions.remove(id)
        pathFollows.remove(id)
    }
}
