package starkraft.sim.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.World
import starkraft.sim.ecs.services.FogGrid

private val snapshotJsonPretty = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val snapshotJsonCompact = Json {
    prettyPrint = false
    encodeDefaults = true
}

@Serializable
data class ClientSnapshot(
    val tick: Int,
    val mapId: String,
    val buildVersion: String,
    val seed: Long? = null,
    val mapWidth: Int,
    val mapHeight: Int,
    val factions: List<FactionSnapshot>,
    val entities: List<EntitySnapshot>
)

@Serializable
data class FactionSnapshot(
    val faction: Int,
    val visibleTiles: Int
)

@Serializable
data class EntitySnapshot(
    val id: Int,
    val faction: Int,
    val typeId: String,
    val x: Float,
    val y: Float,
    val dir: Float,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val weaponId: String? = null,
    val weaponCooldownTicks: Int = 0,
    val visionRange: Float? = null,
    val orderQueueSize: Int = 0,
    val activeOrder: String? = null,
    val pathRemainingNodes: Int = 0
)

@Serializable
data class SnapshotStreamRecord(
    val recordType: String = "snapshot",
    val tick: Int,
    val snapshot: ClientSnapshot
)

@Serializable
data class SnapshotSessionStartRecord(
    val recordType: String = "sessionStart",
    val mapId: String,
    val buildVersion: String,
    val seed: Long? = null
)

fun buildClientSnapshot(
    world: World,
    map: MapGrid,
    tick: Int,
    mapId: String,
    buildVersion: String,
    seed: Long?,
    fogByFaction: Map<Int, FogGrid>
): ClientSnapshot {
    val entities = ArrayList<EntitySnapshot>(world.transforms.size)
    val ids = world.transforms.keys.sorted()
    for (id in ids) {
        val transform = world.transforms[id] ?: continue
        val tag = world.tags[id] ?: continue
        val health = world.healths[id] ?: continue
        if (health.hp <= 0) continue
        val weapon = world.weapons[id]
        val vision = world.visions[id]
        val orders = world.orders[id]?.items
        val path = world.pathFollows[id]
        entities.add(
            EntitySnapshot(
                id = id,
                faction = tag.faction,
                typeId = tag.typeId,
                x = transform.x,
                y = transform.y,
                dir = transform.dir,
                hp = health.hp,
                maxHp = health.maxHp,
                armor = health.armor,
                weaponId = weapon?.id,
                weaponCooldownTicks = weapon?.cooldownTicks ?: 0,
                visionRange = vision?.range,
                orderQueueSize = orders?.size ?: 0,
                activeOrder = orders?.firstOrNull()?.toSnapshotLabel(),
                pathRemainingNodes = path?.let { it.length - it.index } ?: 0
            )
        )
    }
    val factions = ArrayList<FactionSnapshot>(fogByFaction.size)
    for (faction in fogByFaction.keys.sorted()) {
        val fog = fogByFaction[faction] ?: continue
        factions.add(FactionSnapshot(faction = faction, visibleTiles = fog.visibleCount()))
    }
    return ClientSnapshot(
        tick = tick,
        mapId = mapId,
        buildVersion = buildVersion,
        seed = seed,
        mapWidth = map.width,
        mapHeight = map.height,
        factions = factions,
        entities = entities
    )
}

fun renderClientSnapshotJson(snapshot: ClientSnapshot, pretty: Boolean = true): String {
    return if (pretty) snapshotJsonPretty.encodeToString(snapshot) else snapshotJsonCompact.encodeToString(snapshot)
}

fun renderSnapshotStreamRecordJson(snapshot: ClientSnapshot, pretty: Boolean = false): String {
    val record = SnapshotStreamRecord(tick = snapshot.tick, snapshot = snapshot)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderSnapshotSessionStartJson(
    mapId: String,
    buildVersion: String,
    seed: Long?,
    pretty: Boolean = false
): String {
    val record = SnapshotSessionStartRecord(mapId = mapId, buildVersion = buildVersion, seed = seed)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

private fun Order.toSnapshotLabel(): String {
    return when (this) {
        is Order.Move -> "Move"
        is Order.Attack -> "Attack"
    }
}
