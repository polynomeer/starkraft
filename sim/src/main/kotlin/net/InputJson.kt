package starkraft.sim.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap

object InputJson {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Serializable
    data class InputProgram(
        val selections: List<InputSelectionRecord> = emptyList(),
        val commands: List<InputCommandRecord> = emptyList()
    )

    @Serializable
    data class InputSelectionRecord(
        val tick: Int,
        val selectionType: String,
        val units: IntArray = intArrayOf(),
        val faction: Int? = null,
        val typeId: String? = null,
        val archetype: String? = null
    )

    @Serializable
    data class InputCommandRecord(
        val tick: Int,
        val commandType: String,
        val requestId: String? = null,
        val units: IntArray = intArrayOf(),
        val faction: Int? = null,
        val typeId: String? = null,
        val archetype: String? = null,
        val target: Int? = null,
        val targetLabel: String? = null,
        val buildingId: Int? = null,
        val buildingLabel: String? = null,
        val x: Float? = null,
        val y: Float? = null,
        val tileX: Int? = null,
        val tileY: Int? = null,
        val vision: Float? = null,
        val amount: Int? = null,
        val yieldPerTick: Int? = null,
        val width: Int = 0,
        val height: Int = 0,
        val hp: Int = 0,
        val armor: Int = 0,
        val minerals: Int = 0,
        val gas: Int = 0,
        val label: String? = null,
        val labelId: Int? = null
    )

    sealed interface ParsedRecord {
        data class Selection(val event: ScriptRunner.SelectionEvent) : ParsedRecord
        data class Command(val command: starkraft.sim.net.Command, val requestId: String? = null) : ParsedRecord
    }

    data class LoadedInputProgram(
        val program: ScriptRunner.ScriptProgram,
        val commandRequestIds: IdentityHashMap<starkraft.sim.net.Command, String> = IdentityHashMap()
    )

    class StatefulParser {
        private val labelIds = HashMap<String, Int>()
        private var nextLabelId = -1

        fun parseProgram(program: InputProgram): LoadedInputProgram {
            val selections = program.selections.map(::toSelectionEvent)
            val commands = ArrayList<starkraft.sim.net.Command>(program.commands.size)
            val commandRequestIds = IdentityHashMap<starkraft.sim.net.Command, String>()
            for (record in program.commands) {
                val command = toCommand(record)
                commands.add(command)
                if (record.requestId != null) {
                    commandRequestIds[command] = record.requestId
                }
            }
            return LoadedInputProgram(
                program = ScriptRunner.ScriptProgram(commands, selections),
                commandRequestIds = commandRequestIds
            )
        }

        fun parseLine(line: String): ParsedRecord? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val obj = json.parseToJsonElement(trimmed).jsonObject
            return when {
                "selectionType" in obj -> ParsedRecord.Selection(toSelectionEvent(json.decodeFromString(InputSelectionRecord.serializer(), trimmed)))
                "commandType" in obj -> {
                    val record = json.decodeFromString(InputCommandRecord.serializer(), trimmed)
                    ParsedRecord.Command(toCommand(record), record.requestId)
                }
                else -> error("unsupported input NDJSON record")
            }
        }

        private fun labelId(label: String): Int = labelIds.getOrPut(label) { nextLabelId-- }

        private fun toSelectionEvent(record: InputSelectionRecord): ScriptRunner.SelectionEvent =
            ScriptRunner.SelectionEvent(
                tick = record.tick,
                selection =
                    when (record.selectionType) {
                        "units" -> ScriptRunner.Selection.Units(record.units)
                        "all" -> ScriptRunner.Selection.All
                        "faction" -> ScriptRunner.Selection.Faction(record.faction ?: error("selection faction is required"))
                        "type" -> ScriptRunner.Selection.Type(record.typeId ?: error("selection typeId is required"))
                        "archetype" -> ScriptRunner.Selection.Archetype(record.archetype ?: error("selection archetype is required"))
                        else -> error("unsupported input selectionType '${record.selectionType}'")
                    }
            )

        private fun toCommand(record: InputCommandRecord): starkraft.sim.net.Command =
            when (record.commandType) {
                "move" -> Command.Move(record.tick, record.units, record.x ?: error("move x is required"), record.y ?: error("move y is required"))
                "moveFaction" -> Command.MoveFaction(record.tick, record.faction ?: error("moveFaction faction is required"), record.x ?: error("moveFaction x is required"), record.y ?: error("moveFaction y is required"))
                "moveType" -> Command.MoveType(record.tick, record.typeId ?: error("moveType typeId is required"), record.x ?: error("moveType x is required"), record.y ?: error("moveType y is required"))
                "moveArchetype" -> Command.MoveArchetype(record.tick, record.archetype ?: error("moveArchetype archetype is required"), record.x ?: error("moveArchetype x is required"), record.y ?: error("moveArchetype y is required"))
                "patrol" -> Command.Patrol(record.tick, record.units, record.x ?: error("patrol x is required"), record.y ?: error("patrol y is required"))
                "patrolFaction" -> Command.PatrolFaction(record.tick, record.faction ?: error("patrolFaction faction is required"), record.x ?: error("patrolFaction x is required"), record.y ?: error("patrolFaction y is required"))
                "patrolType" -> Command.PatrolType(record.tick, record.typeId ?: error("patrolType typeId is required"), record.x ?: error("patrolType x is required"), record.y ?: error("patrolType y is required"))
                "patrolArchetype" -> Command.PatrolArchetype(record.tick, record.archetype ?: error("patrolArchetype archetype is required"), record.x ?: error("patrolArchetype x is required"), record.y ?: error("patrolArchetype y is required"))
                "attackMove" -> Command.AttackMove(record.tick, record.units, record.x ?: error("attackMove x is required"), record.y ?: error("attackMove y is required"))
                "attackMoveFaction" -> Command.AttackMoveFaction(record.tick, record.faction ?: error("attackMoveFaction faction is required"), record.x ?: error("attackMoveFaction x is required"), record.y ?: error("attackMoveFaction y is required"))
                "attackMoveType" -> Command.AttackMoveType(record.tick, record.typeId ?: error("attackMoveType typeId is required"), record.x ?: error("attackMoveType x is required"), record.y ?: error("attackMoveType y is required"))
                "attackMoveArchetype" -> Command.AttackMoveArchetype(record.tick, record.archetype ?: error("attackMoveArchetype archetype is required"), record.x ?: error("attackMoveArchetype x is required"), record.y ?: error("attackMoveArchetype y is required"))
                "hold" -> Command.Hold(record.tick, record.units)
                "holdFaction" -> Command.HoldFaction(record.tick, record.faction ?: error("holdFaction faction is required"))
                "holdType" -> Command.HoldType(record.tick, record.typeId ?: error("holdType typeId is required"))
                "holdArchetype" -> Command.HoldArchetype(record.tick, record.archetype ?: error("holdArchetype archetype is required"))
                "attack" -> Command.Attack(record.tick, record.units, resolveEntityRef(record.target, record.targetLabel, ::labelId, "attack target"))
                "attackFaction" -> Command.AttackFaction(record.tick, record.faction ?: error("attackFaction faction is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackFaction target"))
                "attackType" -> Command.AttackType(record.tick, record.typeId ?: error("attackType typeId is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackType target"))
                "attackArchetype" -> Command.AttackArchetype(record.tick, record.archetype ?: error("attackArchetype archetype is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackArchetype target"))
                "harvest" -> Command.Harvest(record.tick, record.units, resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvest target"))
                "harvestFaction" -> Command.HarvestFaction(record.tick, record.faction ?: error("harvestFaction faction is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestFaction target"))
                "harvestType" -> Command.HarvestType(record.tick, record.typeId ?: error("harvestType typeId is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestType target"))
                "harvestArchetype" -> Command.HarvestArchetype(record.tick, record.archetype ?: error("harvestArchetype archetype is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestArchetype target"))
                "construct" -> Command.Construct(record.tick, record.units, resolveEntityRef(record.target, record.targetLabel, ::labelId, "construct target"))
                "constructFaction" -> Command.ConstructFaction(record.tick, record.faction ?: error("constructFaction faction is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "constructFaction target"))
                "constructType" -> Command.ConstructType(record.tick, record.typeId ?: error("constructType typeId is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "constructType target"))
                "constructArchetype" -> Command.ConstructArchetype(record.tick, record.archetype ?: error("constructArchetype archetype is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "constructArchetype target"))
                "spawn" -> Command.Spawn(record.tick, record.faction ?: error("spawn faction is required"), record.typeId ?: error("spawn typeId is required"), record.x ?: error("spawn x is required"), record.y ?: error("spawn y is required"), record.vision, record.label, record.labelId ?: record.label?.let(::labelId))
                "spawnNode" -> Command.SpawnNode(record.tick, record.typeId ?: error("spawnNode typeId is required"), record.x ?: error("spawnNode x is required"), record.y ?: error("spawnNode y is required"), record.amount ?: error("spawnNode amount is required"), record.yieldPerTick ?: 0, record.label, record.labelId ?: record.label?.let(::labelId))
                "build" -> Command.Build(record.tick, record.faction ?: error("build faction is required"), record.typeId ?: error("build typeId is required"), record.tileX ?: error("build tileX is required"), record.tileY ?: error("build tileY is required"), record.width, record.height, record.hp, record.armor, record.minerals, record.gas, record.label, record.labelId ?: record.label?.let(::labelId))
                "train" -> Command.Train(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "train buildingId"), record.typeId ?: error("train typeId is required"), record.amount ?: 0, record.minerals, record.gas)
                "cancelTrain" -> Command.CancelTrain(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "cancelTrain buildingId"))
                "research" -> Command.Research(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "research buildingId"), record.typeId ?: error("research typeId is required"), record.amount ?: 0, record.minerals, record.gas)
                "rally" -> Command.Rally(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "rally buildingId"), record.x ?: error("rally x is required"), record.y ?: error("rally y is required"))
                else -> error("unsupported input commandType '${record.commandType}'")
            }
    }

    fun loadProgram(path: Path): ScriptRunner.ScriptProgram = loadLoadedProgram(path).program

    fun loadProgram(raw: String): ScriptRunner.ScriptProgram {
        return loadLoadedProgram(raw).program
    }

    fun loadLoadedProgram(path: Path): LoadedInputProgram = loadLoadedProgram(Files.readString(path))

    fun loadLoadedProgram(raw: String): LoadedInputProgram {
        val program = parseProgram(raw)
        return StatefulParser().parseProgram(program)
    }

    private fun parseProgram(raw: String): InputProgram {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return InputProgram()
        if (!trimmed.contains('\n')) {
            return json.decodeFromString(InputProgram.serializer(), trimmed)
        }
        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        if (root != null && ("commands" in root.jsonObject || "selections" in root.jsonObject)) {
            return json.decodeFromString(InputProgram.serializer(), trimmed)
        }
        val selections = ArrayList<InputSelectionRecord>()
        val commands = ArrayList<InputCommandRecord>()
        for ((index, rawLine) in trimmed.lineSequence().withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val obj = json.parseToJsonElement(line).jsonObject
            when {
                "selectionType" in obj -> selections.add(json.decodeFromString(InputSelectionRecord.serializer(), line))
                "commandType" in obj -> commands.add(json.decodeFromString(InputCommandRecord.serializer(), line))
                else -> error("unsupported input NDJSON record at line ${index + 1}")
            }
        }
        return InputProgram(selections = selections, commands = commands)
    }

    private fun resolveEntityRef(
        id: Int?,
        label: String?,
        labelId: (String) -> Int,
        fieldName: String
    ): Int {
        if (id != null) return id
        if (label != null) return labelId(label)
        error("$fieldName is required")
    }
}
