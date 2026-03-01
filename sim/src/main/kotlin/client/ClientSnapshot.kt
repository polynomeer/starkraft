package starkraft.sim.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.World
import starkraft.sim.ecs.services.FogGrid
import starkraft.sim.net.Command

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
    val sequence: Long,
    val tick: Int,
    val snapshot: ClientSnapshot
)

@Serializable
data class SnapshotSessionStartRecord(
    val recordType: String = "sessionStart",
    val sequence: Long,
    val mapId: String,
    val buildVersion: String,
    val seed: Long? = null
)

@Serializable
data class SnapshotSessionEndRecord(
    val recordType: String = "sessionEnd",
    val sequence: Long,
    val tick: Int,
    val worldHash: Long,
    val replayHash: Long? = null
)

@Serializable
data class CommandStreamRecord(
    val recordType: String = "command",
    val sequence: Long,
    val tick: Int,
    val commandType: String,
    val units: IntArray = intArrayOf(),
    val faction: Int? = null,
    val typeId: String? = null,
    val target: Int? = null,
    val x: Float? = null,
    val y: Float? = null,
    val vision: Float? = null,
    val label: String? = null,
    val labelId: Int? = null
)

@Serializable
data class MetricsFactionRecord(
    val faction: Int,
    val alive: Int,
    val visibleTiles: Int
)

@Serializable
data class MetricsStreamRecord(
    val recordType: String = "metrics",
    val sequence: Long,
    val tick: Int,
    val factions: List<MetricsFactionRecord>,
    val pathRequests: Int,
    val pathSolved: Int,
    val pathQueueSize: Int,
    val avgPathLength: Float,
    val replans: Int,
    val replansBlocked: Int,
    val replansStuck: Int
)

@Serializable
data class CombatEventRecord(
    val attackerId: Int,
    val targetId: Int,
    val damage: Int,
    val targetHp: Int,
    val killed: Boolean
)

@Serializable
data class CombatStreamRecord(
    val recordType: String = "combat",
    val sequence: Long,
    val tick: Int,
    val attacks: Int,
    val kills: Int,
    val events: List<CombatEventRecord>
)

@Serializable
data class DespawnEventRecord(
    val entityId: Int,
    val faction: Int,
    val typeId: String? = null,
    val reason: String? = null
)

@Serializable
data class DespawnStreamRecord(
    val recordType: String = "despawn",
    val sequence: Long,
    val tick: Int,
    val entities: List<DespawnEventRecord>
)

@Serializable
data class TickSummaryStreamRecord(
    val recordType: String = "tickSummary",
    val sequence: Long,
    val tick: Int,
    val aliveTotal: Int,
    val visibleTilesFaction1: Int,
    val visibleTilesFaction2: Int,
    val pathRequests: Int,
    val pathSolved: Int,
    val pathQueueSize: Int,
    val avgPathLength: Float,
    val replans: Int,
    val replansBlocked: Int,
    val replansStuck: Int,
    val attacks: Int,
    val kills: Int,
    val despawns: Int
)

@Serializable
data class SpawnStreamRecord(
    val recordType: String = "spawn",
    val sequence: Long,
    val tick: Int,
    val entityId: Int,
    val faction: Int,
    val typeId: String,
    val x: Float,
    val y: Float,
    val vision: Float? = null,
    val label: String? = null,
    val labelId: Int? = null
)

@Serializable
data class SelectionStreamRecord(
    val recordType: String = "selection",
    val sequence: Long,
    val tick: Int,
    val selectionType: String,
    val units: IntArray = intArrayOf(),
    val faction: Int? = null,
    val typeId: String? = null
)

@Serializable
data class OrderAppliedStreamRecord(
    val recordType: String = "orderApplied",
    val sequence: Long,
    val tick: Int,
    val orderType: String,
    val units: IntArray,
    val target: Int? = null,
    val x: Float? = null,
    val y: Float? = null
)

@Serializable
data class OrderQueueEntityRecord(
    val entityId: Int,
    val queueSize: Int
)

@Serializable
data class OrderQueueStreamRecord(
    val recordType: String = "orderQueue",
    val sequence: Long,
    val tick: Int,
    val orderType: String,
    val entities: List<OrderQueueEntityRecord>
)

@Serializable
data class PathAssignedEventRecord(
    val entityId: Int,
    val pathLength: Int,
    val goalX: Int,
    val goalY: Int
)

@Serializable
data class PathAssignedStreamRecord(
    val recordType: String = "pathAssigned",
    val sequence: Long,
    val tick: Int,
    val entities: List<PathAssignedEventRecord>
)

@Serializable
data class PathProgressEventRecord(
    val entityId: Int,
    val waypointIndex: Int,
    val remainingNodes: Int,
    val completed: Boolean
)

@Serializable
data class PathProgressStreamRecord(
    val recordType: String = "pathProgress",
    val sequence: Long,
    val tick: Int,
    val entities: List<PathProgressEventRecord>
)

@Serializable
data class OccupancyChangeEventRecord(
    val x: Int,
    val y: Int,
    val blocked: Boolean
)

@Serializable
data class OccupancyChangeStreamRecord(
    val recordType: String = "occupancy",
    val sequence: Long,
    val tick: Int,
    val changes: List<OccupancyChangeEventRecord>
)

@Serializable
data class MapBlockedTileRecord(
    val x: Int,
    val y: Int
)

@Serializable
data class MapCostTileRecord(
    val x: Int,
    val y: Int,
    val cost: Float
)

@Serializable
data class MapStateStreamRecord(
    val recordType: String = "mapState",
    val sequence: Long,
    val width: Int,
    val height: Int,
    val blockedTiles: List<MapBlockedTileRecord>,
    val weightedTiles: List<MapCostTileRecord>,
    val staticOccupancyTiles: List<MapBlockedTileRecord>
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

fun renderSnapshotStreamRecordJson(snapshot: ClientSnapshot, sequence: Long, pretty: Boolean = false): String {
    val record = SnapshotStreamRecord(sequence = sequence, tick = snapshot.tick, snapshot = snapshot)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderSnapshotSessionStartJson(
    sequence: Long,
    mapId: String,
    buildVersion: String,
    seed: Long?,
    pretty: Boolean = false
): String {
    val record = SnapshotSessionStartRecord(sequence = sequence, mapId = mapId, buildVersion = buildVersion, seed = seed)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderSnapshotSessionEndJson(
    sequence: Long,
    tick: Int,
    worldHash: Long,
    replayHash: Long?,
    pretty: Boolean = false
): String {
    val record = SnapshotSessionEndRecord(sequence = sequence, tick = tick, worldHash = worldHash, replayHash = replayHash)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderCommandStreamRecordJson(cmd: Command, sequence: Long, pretty: Boolean = false): String {
    val record =
        when (cmd) {
            is Command.Move ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "move",
                    units = cmd.units,
                    x = cmd.x,
                    y = cmd.y
                )
            is Command.MoveFaction ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "moveFaction",
                    faction = cmd.faction,
                    x = cmd.x,
                    y = cmd.y
                )
            is Command.MoveType ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "moveType",
                    typeId = cmd.typeId,
                    x = cmd.x,
                    y = cmd.y
                )
            is Command.Attack ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "attack",
                    units = cmd.units,
                    target = cmd.target
                )
            is Command.AttackFaction ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "attackFaction",
                    faction = cmd.faction,
                    target = cmd.target
                )
            is Command.AttackType ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "attackType",
                    typeId = cmd.typeId,
                    target = cmd.target
                )
            is Command.Spawn ->
                CommandStreamRecord(
                    sequence = sequence,
                    tick = cmd.tick,
                    commandType = "spawn",
                    faction = cmd.faction,
                    typeId = cmd.typeId,
                    x = cmd.x,
                    y = cmd.y,
                    vision = cmd.vision,
                    label = cmd.label,
                    labelId = cmd.labelId
                )
        }
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderMetricsStreamRecordJson(
    sequence: Long,
    tick: Int,
    factions: List<MetricsFactionRecord>,
    pathRequests: Int,
    pathSolved: Int,
    pathQueueSize: Int,
    avgPathLength: Float,
    replans: Int,
    replansBlocked: Int,
    replansStuck: Int,
    pretty: Boolean = false
): String {
    val record =
        MetricsStreamRecord(
            sequence = sequence,
            tick = tick,
            factions = factions,
            pathRequests = pathRequests,
            pathSolved = pathSolved,
            pathQueueSize = pathQueueSize,
            avgPathLength = avgPathLength,
            replans = replans,
            replansBlocked = replansBlocked,
            replansStuck = replansStuck
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderCombatStreamRecordJson(
    sequence: Long,
    tick: Int,
    attacks: Int,
    kills: Int,
    events: List<CombatEventRecord>,
    pretty: Boolean = false
): String {
    val record =
        CombatStreamRecord(
            sequence = sequence,
            tick = tick,
            attacks = attacks,
            kills = kills,
            events = events
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderDespawnStreamRecordJson(
    sequence: Long,
    tick: Int,
    entities: List<DespawnEventRecord>,
    pretty: Boolean = false
): String {
    val record = DespawnStreamRecord(sequence = sequence, tick = tick, entities = entities)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderTickSummaryStreamRecordJson(
    sequence: Long,
    tick: Int,
    aliveTotal: Int,
    visibleTilesFaction1: Int,
    visibleTilesFaction2: Int,
    pathRequests: Int,
    pathSolved: Int,
    pathQueueSize: Int,
    avgPathLength: Float,
    replans: Int,
    replansBlocked: Int,
    replansStuck: Int,
    attacks: Int,
    kills: Int,
    despawns: Int,
    pretty: Boolean = false
): String {
    val record =
        TickSummaryStreamRecord(
            sequence = sequence,
            tick = tick,
            aliveTotal = aliveTotal,
            visibleTilesFaction1 = visibleTilesFaction1,
            visibleTilesFaction2 = visibleTilesFaction2,
            pathRequests = pathRequests,
            pathSolved = pathSolved,
            pathQueueSize = pathQueueSize,
            avgPathLength = avgPathLength,
            replans = replans,
            replansBlocked = replansBlocked,
            replansStuck = replansStuck,
            attacks = attacks,
            kills = kills,
            despawns = despawns
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderSpawnStreamRecordJson(
    sequence: Long,
    tick: Int,
    entityId: Int,
    faction: Int,
    typeId: String,
    x: Float,
    y: Float,
    vision: Float?,
    label: String?,
    labelId: Int?,
    pretty: Boolean = false
): String {
    val record =
        SpawnStreamRecord(
            sequence = sequence,
            tick = tick,
            entityId = entityId,
            faction = faction,
            typeId = typeId,
            x = x,
            y = y,
            vision = vision,
            label = label,
            labelId = labelId
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderSelectionStreamRecordJson(
    sequence: Long,
    tick: Int,
    selectionType: String,
    units: IntArray = intArrayOf(),
    faction: Int? = null,
    typeId: String? = null,
    pretty: Boolean = false
): String {
    val record =
        SelectionStreamRecord(
            sequence = sequence,
            tick = tick,
            selectionType = selectionType,
            units = units,
            faction = faction,
            typeId = typeId
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderOrderAppliedStreamRecordJson(
    sequence: Long,
    tick: Int,
    orderType: String,
    units: IntArray,
    target: Int? = null,
    x: Float? = null,
    y: Float? = null,
    pretty: Boolean = false
): String {
    val record =
        OrderAppliedStreamRecord(
            sequence = sequence,
            tick = tick,
            orderType = orderType,
            units = units,
            target = target,
            x = x,
            y = y
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderOrderQueueStreamRecordJson(
    sequence: Long,
    tick: Int,
    orderType: String,
    entities: List<OrderQueueEntityRecord>,
    pretty: Boolean = false
): String {
    val record =
        OrderQueueStreamRecord(
            sequence = sequence,
            tick = tick,
            orderType = orderType,
            entities = entities
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderPathAssignedStreamRecordJson(
    sequence: Long,
    tick: Int,
    entities: List<PathAssignedEventRecord>,
    pretty: Boolean = false
): String {
    val record = PathAssignedStreamRecord(sequence = sequence, tick = tick, entities = entities)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderPathProgressStreamRecordJson(
    sequence: Long,
    tick: Int,
    entities: List<PathProgressEventRecord>,
    pretty: Boolean = false
): String {
    val record = PathProgressStreamRecord(sequence = sequence, tick = tick, entities = entities)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderOccupancyChangeStreamRecordJson(
    sequence: Long,
    tick: Int,
    changes: List<OccupancyChangeEventRecord>,
    pretty: Boolean = false
): String {
    val record = OccupancyChangeStreamRecord(sequence = sequence, tick = tick, changes = changes)
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

fun renderMapStateStreamRecordJson(
    sequence: Long,
    width: Int,
    height: Int,
    blockedTiles: List<MapBlockedTileRecord>,
    weightedTiles: List<MapCostTileRecord>,
    staticOccupancyTiles: List<MapBlockedTileRecord>,
    pretty: Boolean = false
): String {
    val record =
        MapStateStreamRecord(
            sequence = sequence,
            width = width,
            height = height,
            blockedTiles = blockedTiles,
            weightedTiles = weightedTiles,
            staticOccupancyTiles = staticOccupancyTiles
        )
    return if (pretty) snapshotJsonPretty.encodeToString(record) else snapshotJsonCompact.encodeToString(record)
}

private fun Order.toSnapshotLabel(): String {
    return when (this) {
        is Order.Move -> "Move"
        is Order.Attack -> "Attack"
    }
}
