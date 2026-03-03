package starkraft.sim.replay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import starkraft.sim.net.Command
import java.nio.file.Files
import java.nio.file.Path

private const val SCHEMA_VERSION = 1

object ReplayIO {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun save(
        path: Path,
        commands: List<Command>,
        seed: Long? = null,
        mapId: String? = null,
        buildVersion: String? = null
    ) {
        val normalized = ensureLabelIds(commands)
        val recorder = ReplayHashRecorder()
        for (c in normalized) recorder.onCommand(c)
        val container = ReplayContainer(
            schema = SCHEMA_VERSION,
            replayHash = recorder.value(),
            seed = seed,
            mapId = mapId,
            buildVersion = buildVersion,
            events = normalized.map { ReplayEvent.fromCommand(it) }
        )
        val payload = json.encodeToString(container)
        Files.writeString(path, payload)
    }

    fun load(path: Path, strictHash: Boolean = false): List<Command> {
        val payload = Files.readString(path)
        return if (payload.trimStart().startsWith("[")) {
            if (strictHash) {
                error("Replay hash missing (legacy array format)")
            }
            val events = json.decodeFromString<List<ReplayEvent>>(payload)
            ensureLabelIds(events.map { it.toCommand() })
        } else {
            val container = json.decodeFromString<ReplayContainer>(payload)
            container.validateSchema()
            val cmds = ensureLabelIds(container.events.map { it.toCommand() })
            if (container.schema != 0) {
                verifyReplayHash(container.replayHash, cmds)
            } else if (strictHash) {
                error("Replay hash missing (legacy schema 0)")
            }
            cmds
        }
    }

    fun inspect(path: Path): ReplayMetadata {
        val payload = Files.readString(path)
        val fileSizeBytes = Files.size(path)
        if (payload.trimStart().startsWith("[")) {
            val events = json.decodeFromString<List<ReplayEvent>>(payload)
            return ReplayMetadata(
                schema = 0,
                replayHash = null,
                seed = null,
                mapId = null,
                buildVersion = null,
                eventCount = events.size,
                fileSizeBytes = fileSizeBytes,
                legacy = true
            )
        }
        val container = json.decodeFromString<ReplayContainer>(payload)
        return ReplayMetadata(
            schema = container.schema,
            replayHash = if (container.schema == 0) null else container.replayHash,
            seed = container.seed,
            mapId = container.mapId,
            buildVersion = container.buildVersion,
            eventCount = container.events.size,
            fileSizeBytes = fileSizeBytes,
            legacy = container.schema == 0
        )
    }
}

data class ReplayMetadata(
    val schema: Int,
    val replayHash: Long?,
    val seed: Long?,
    val mapId: String?,
    val buildVersion: String?,
    val eventCount: Int,
    val fileSizeBytes: Long,
    val legacy: Boolean
)

@Serializable
private data class ReplayContainer(
    val schema: Int,
    val replayHash: Long,
    val seed: Long? = null,
    val mapId: String? = null,
    val buildVersion: String? = null,
    val events: List<ReplayEvent>
) {
    fun validateSchema() {
        if (schema == SCHEMA_VERSION) return
        if (schema == 0) return
        error("Unsupported replay schema: $schema (expected $SCHEMA_VERSION)")
    }
}

@Serializable
private data class ReplayEvent(
    val type: String,
    val tick: Int,
    val units: IntArray = intArrayOf(),
    val x: Float? = null,
    val y: Float? = null,
    val target: Int? = null,
    val faction: Int? = null,
    val typeId: String? = null,
    val archetype: String? = null,
    val vision: Float? = null,
    val label: String? = null,
    val labelId: Int? = null,
    val amount: Int? = null,
    val yieldPerTick: Int? = null,
    val tileX: Int? = null,
    val tileY: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hp: Int? = null,
    val armor: Int? = null,
    val mineralCost: Int? = null,
    val gasCost: Int? = null,
    val buildTicks: Int? = null
) {
    fun toCommand(): Command {
        return when (type) {
            "move" -> Command.Move(tick, units, x ?: 0f, y ?: 0f)
            "moveFaction" -> Command.MoveFaction(tick, faction ?: 0, x ?: 0f, y ?: 0f)
            "moveType" -> Command.MoveType(tick, typeId ?: "", x ?: 0f, y ?: 0f)
            "moveArchetype" -> Command.MoveArchetype(tick, archetype ?: "", x ?: 0f, y ?: 0f)
            "attack" -> Command.Attack(tick, units, target ?: 0)
            "attackFaction" -> Command.AttackFaction(tick, faction ?: 0, target ?: 0)
            "attackType" -> Command.AttackType(tick, typeId ?: "", target ?: 0)
            "attackArchetype" -> Command.AttackArchetype(tick, archetype ?: "", target ?: 0)
            "harvest" -> Command.Harvest(tick, units, target ?: 0)
            "harvestFaction" -> Command.HarvestFaction(tick, faction ?: 0, target ?: 0)
            "harvestType" -> Command.HarvestType(tick, typeId ?: "", target ?: 0)
            "harvestArchetype" -> Command.HarvestArchetype(tick, archetype ?: "", target ?: 0)
            "spawnNode" -> Command.SpawnNode(tick, typeId ?: "", x ?: 0f, y ?: 0f, amount ?: 0, yieldPerTick ?: 0, label, labelId)
            "spawn" -> Command.Spawn(tick, faction ?: 0, typeId ?: "", x ?: 0f, y ?: 0f, vision, label, labelId)
            "build" ->
                Command.Build(
                    tick = tick,
                    faction = faction ?: 0,
                    typeId = typeId ?: "",
                    tileX = tileX ?: 0,
                    tileY = tileY ?: 0,
                    width = width ?: 0,
                    height = height ?: 0,
                    hp = hp ?: 0,
                    armor = armor ?: 0,
                    mineralCost = mineralCost ?: 0,
                    gasCost = gasCost ?: 0,
                    label = label,
                    labelId = labelId
                )
            "train" ->
                Command.Train(
                    tick = tick,
                    buildingId = target ?: 0,
                    typeId = typeId ?: "",
                    buildTicks = buildTicks ?: 0,
                    mineralCost = mineralCost ?: 0,
                    gasCost = gasCost ?: 0
                )
            "cancelTrain" -> Command.CancelTrain(tick = tick, buildingId = target ?: 0)
            "rally" -> Command.Rally(tick = tick, buildingId = target ?: 0, x = x ?: 0f, y = y ?: 0f)
            else -> error("Unknown replay event type: $type")
        }
    }

    companion object {
        fun fromCommand(cmd: Command): ReplayEvent {
            return when (cmd) {
                is Command.Move -> ReplayEvent("move", cmd.tick, cmd.units, cmd.x, cmd.y, null)
                is Command.MoveFaction -> ReplayEvent("moveFaction", cmd.tick, intArrayOf(), cmd.x, cmd.y, null, cmd.faction)
                is Command.MoveType -> ReplayEvent("moveType", cmd.tick, intArrayOf(), cmd.x, cmd.y, null, null, cmd.typeId)
                is Command.MoveArchetype -> ReplayEvent("moveArchetype", cmd.tick, intArrayOf(), cmd.x, cmd.y, null, null, null, cmd.archetype)
                is Command.Attack -> ReplayEvent("attack", cmd.tick, cmd.units, null, null, cmd.target)
                is Command.AttackFaction -> ReplayEvent("attackFaction", cmd.tick, intArrayOf(), null, null, cmd.target, cmd.faction)
                is Command.AttackType -> ReplayEvent("attackType", cmd.tick, intArrayOf(), null, null, cmd.target, null, cmd.typeId)
                is Command.AttackArchetype -> ReplayEvent("attackArchetype", cmd.tick, intArrayOf(), null, null, cmd.target, null, null, cmd.archetype)
                is Command.Harvest -> ReplayEvent("harvest", cmd.tick, cmd.units, null, null, cmd.target)
                is Command.HarvestFaction -> ReplayEvent("harvestFaction", cmd.tick, intArrayOf(), null, null, cmd.target, cmd.faction)
                is Command.HarvestType -> ReplayEvent("harvestType", cmd.tick, intArrayOf(), null, null, cmd.target, null, cmd.typeId)
                is Command.HarvestArchetype -> ReplayEvent("harvestArchetype", cmd.tick, intArrayOf(), null, null, cmd.target, null, null, cmd.archetype)
                is Command.SpawnNode ->
                    ReplayEvent(
                        type = "spawnNode",
                        tick = cmd.tick,
                        typeId = cmd.kind,
                        x = cmd.x,
                        y = cmd.y,
                        amount = cmd.amount,
                        yieldPerTick = cmd.yieldPerTick,
                        label = cmd.label,
                        labelId = cmd.labelId
                    )
                is Command.Spawn ->
                    ReplayEvent(
                        type = "spawn",
                        tick = cmd.tick,
                        units = intArrayOf(),
                        x = cmd.x,
                        y = cmd.y,
                        faction = cmd.faction,
                        typeId = cmd.typeId,
                        vision = cmd.vision,
                        label = cmd.label,
                        labelId = cmd.labelId
                    )
                is Command.Build ->
                    ReplayEvent(
                        type = "build",
                        tick = cmd.tick,
                        faction = cmd.faction,
                        typeId = cmd.typeId,
                        tileX = cmd.tileX,
                        tileY = cmd.tileY,
                        width = cmd.width,
                        height = cmd.height,
                        hp = cmd.hp,
                        armor = cmd.armor,
                        mineralCost = cmd.mineralCost,
                        gasCost = cmd.gasCost,
                        label = cmd.label,
                        labelId = cmd.labelId
                    )
                is Command.Train ->
                    ReplayEvent(
                        type = "train",
                        tick = cmd.tick,
                        target = cmd.buildingId,
                        typeId = cmd.typeId,
                        buildTicks = cmd.buildTicks,
                        mineralCost = cmd.mineralCost,
                        gasCost = cmd.gasCost
                    )
                is Command.CancelTrain ->
                    ReplayEvent(
                        type = "cancelTrain",
                        tick = cmd.tick,
                        target = cmd.buildingId
                    )
                is Command.Rally ->
                    ReplayEvent(
                        type = "rally",
                        tick = cmd.tick,
                        target = cmd.buildingId,
                        x = cmd.x,
                        y = cmd.y
                    )
            }
        }
    }
}

private fun verifyReplayHash(expected: Long, commands: List<Command>) {
    val recorder = ReplayHashRecorder()
    for (c in commands) recorder.onCommand(c)
    val actual = recorder.value()
    if (expected != actual) {
        error("Replay hash mismatch: expected=$expected actual=$actual")
    }
}

private fun ensureLabelIds(commands: List<Command>): List<Command> {
    val labelToId = HashMap<String, Int>()
    var nextLabelId = -1
    var changed = false
    val out = ArrayList<Command>(commands.size)
    for (c in commands) {
        if (c is Command.Spawn && c.label != null && c.labelId == null) {
            val id = labelToId.getOrPut(c.label) { nextLabelId-- }
            out.add(c.copy(labelId = id))
            changed = true
        } else if (c is Command.Build && c.label != null && c.labelId == null) {
            val id = labelToId.getOrPut(c.label) { nextLabelId-- }
            out.add(c.copy(labelId = id))
            changed = true
        } else {
            if (c is Command.Spawn && c.label != null && c.labelId != null) {
                labelToId.putIfAbsent(c.label, c.labelId)
            }
            if (c is Command.Build && c.label != null && c.labelId != null) {
                labelToId.putIfAbsent(c.label, c.labelId)
            }
            out.add(c)
        }
    }
    return if (changed) out else commands
}
