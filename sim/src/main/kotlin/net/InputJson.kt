package starkraft.sim.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

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
        data class Command(val command: starkraft.sim.net.Command) : ParsedRecord
    }

    class StatefulParser {
        private val labelIds = HashMap<String, Int>()
        private var nextLabelId = -1

        fun parseProgram(program: InputProgram): ScriptRunner.ScriptProgram {
            val selections = program.selections.map(::toSelectionEvent)
            val commands = program.commands.map(::toCommand)
            return ScriptRunner.ScriptProgram(commands, selections)
        }

        fun parseLine(line: String): ParsedRecord? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val obj = json.parseToJsonElement(trimmed).jsonObject
            return when {
                "selectionType" in obj -> ParsedRecord.Selection(toSelectionEvent(json.decodeFromString(InputSelectionRecord.serializer(), trimmed)))
                "commandType" in obj -> ParsedRecord.Command(toCommand(json.decodeFromString(InputCommandRecord.serializer(), trimmed)))
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
                "attack" -> Command.Attack(record.tick, record.units, resolveEntityRef(record.target, record.targetLabel, ::labelId, "attack target"))
                "attackFaction" -> Command.AttackFaction(record.tick, record.faction ?: error("attackFaction faction is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackFaction target"))
                "attackType" -> Command.AttackType(record.tick, record.typeId ?: error("attackType typeId is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackType target"))
                "attackArchetype" -> Command.AttackArchetype(record.tick, record.archetype ?: error("attackArchetype archetype is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackArchetype target"))
                "harvest" -> Command.Harvest(record.tick, record.units, resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvest target"))
                "harvestFaction" -> Command.HarvestFaction(record.tick, record.faction ?: error("harvestFaction faction is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestFaction target"))
                "harvestType" -> Command.HarvestType(record.tick, record.typeId ?: error("harvestType typeId is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestType target"))
                "harvestArchetype" -> Command.HarvestArchetype(record.tick, record.archetype ?: error("harvestArchetype archetype is required"), resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestArchetype target"))
                "spawn" -> Command.Spawn(record.tick, record.faction ?: error("spawn faction is required"), record.typeId ?: error("spawn typeId is required"), record.x ?: error("spawn x is required"), record.y ?: error("spawn y is required"), record.vision, record.label, record.labelId ?: record.label?.let(::labelId))
                "spawnNode" -> Command.SpawnNode(record.tick, record.typeId ?: error("spawnNode typeId is required"), record.x ?: error("spawnNode x is required"), record.y ?: error("spawnNode y is required"), record.amount ?: error("spawnNode amount is required"), record.yieldPerTick ?: 0, record.label, record.labelId ?: record.label?.let(::labelId))
                "build" -> Command.Build(record.tick, record.faction ?: error("build faction is required"), record.typeId ?: error("build typeId is required"), record.tileX ?: error("build tileX is required"), record.tileY ?: error("build tileY is required"), record.width, record.height, record.hp, record.armor, record.minerals, record.gas, record.label, record.labelId ?: record.label?.let(::labelId))
                "train" -> Command.Train(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "train buildingId"), record.typeId ?: error("train typeId is required"), record.amount ?: 0, record.minerals, record.gas)
                "cancelTrain" -> Command.CancelTrain(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "cancelTrain buildingId"))
                "rally" -> Command.Rally(record.tick, resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "rally buildingId"), record.x ?: error("rally x is required"), record.y ?: error("rally y is required"))
                else -> error("unsupported input commandType '${record.commandType}'")
            }
    }

    fun loadProgram(path: Path): ScriptRunner.ScriptProgram = loadProgram(Files.readString(path))

    fun loadProgram(raw: String): ScriptRunner.ScriptProgram {
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

