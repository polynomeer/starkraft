package starkraft.sim.ecs

class World {
    // Components stored in maps keyed by entity id for simplicity
    val transforms = mutableMapOf<EntityId, Transform>()
    val motions = mutableMapOf<EntityId, Motion>()
    val tags = mutableMapOf<EntityId, UnitTag>()
    val healths = mutableMapOf<EntityId, Health>()
    val weapons = mutableMapOf<EntityId, WeaponRef>()
    val orders = mutableMapOf<EntityId, OrderQueue>()

    val alive: Sequence<EntityId> get() = transforms.keys.asSequence().filter { healths[it]?.hp ?: 0 > 0 }

    fun spawn(t: Transform, tag: UnitTag, h: Health, w: WeaponRef?): EntityId {
        val id = Ids.newId()
        transforms[id] = t
        tags[id] = tag
        healths[id] = h
        if (w != null) weapons[id] = w
        orders[id] = OrderQueue()
        motions[id] = Motion()
        return id
    }

    fun remove(id: EntityId) {
        transforms.remove(id); motions.remove(id); tags.remove(id); healths.remove(id); weapons.remove(id); orders.remove(
            id
        )
    }
}
