package starkraft.sim.replay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import starkraft.sim.net.Command
import java.nio.file.Files
import java.nio.file.Path

object ReplayIO {
    private const val SCHEMA_VERSION = 1
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun save(path: Path, commands: List<Command>) {
        val recorder = ReplayHashRecorder()
        for (c in commands) recorder.onCommand(c)
        val container = ReplayContainer(
            schema = SCHEMA_VERSION,
            replayHash = recorder.value(),
            events = commands.map { ReplayEvent.fromCommand(it) }
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
            events.map { it.toCommand() }
        } else {
            val container = json.decodeFromString<ReplayContainer>(payload)
            container.validateSchema()
            val cmds = container.events.map { it.toCommand() }
            if (container.schema != 0) {
                verifyReplayHash(container.replayHash, cmds)
            } else if (strictHash) {
                error("Replay hash missing (legacy schema 0)")
            }
            cmds
        }
    }
}

@Serializable
private data class ReplayContainer(
    val schema: Int,
    val replayHash: Long,
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
    val vision: Float? = null
) {
    fun toCommand(): Command {
        return when (type) {
            "move" -> Command.Move(tick, units, x ?: 0f, y ?: 0f)
            "attack" -> Command.Attack(tick, units, target ?: 0)
            "spawn" -> Command.Spawn(tick, faction ?: 0, typeId ?: "", x ?: 0f, y ?: 0f, vision)
            else -> error("Unknown replay event type: $type")
        }
    }

    companion object {
        fun fromCommand(cmd: Command): ReplayEvent {
            return when (cmd) {
                is Command.Move -> ReplayEvent("move", cmd.tick, cmd.units, cmd.x, cmd.y, null)
                is Command.Attack -> ReplayEvent("attack", cmd.tick, cmd.units, null, null, cmd.target)
                is Command.Spawn ->
                    ReplayEvent("spawn", cmd.tick, intArrayOf(), cmd.x, cmd.y, null, cmd.faction, cmd.typeId, cmd.vision)
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
