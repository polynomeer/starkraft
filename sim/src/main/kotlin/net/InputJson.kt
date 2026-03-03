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

    fun loadProgram(path: Path): ScriptRunner.ScriptProgram {
        val raw = Files.readString(path)
        val program = parseProgram(raw)
        val labelIds = HashMap<String, Int>()
        var nextLabelId = -1

        fun labelId(label: String): Int = labelIds.getOrPut(label) { nextLabelId-- }

        val selections =
            program.selections.map { record ->
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
            }

        val commands =
            program.commands.map { record ->
                when (record.commandType) {
                    "move" ->
                        Command.Move(
                            tick = record.tick,
                            units = record.units,
                            x = record.x ?: error("move x is required"),
                            y = record.y ?: error("move y is required")
                        )
                    "moveFaction" ->
                        Command.MoveFaction(
                            tick = record.tick,
                            faction = record.faction ?: error("moveFaction faction is required"),
                            x = record.x ?: error("moveFaction x is required"),
                            y = record.y ?: error("moveFaction y is required")
                        )
                    "moveType" ->
                        Command.MoveType(
                            tick = record.tick,
                            typeId = record.typeId ?: error("moveType typeId is required"),
                            x = record.x ?: error("moveType x is required"),
                            y = record.y ?: error("moveType y is required")
                        )
                    "moveArchetype" ->
                        Command.MoveArchetype(
                            tick = record.tick,
                            archetype = record.archetype ?: error("moveArchetype archetype is required"),
                            x = record.x ?: error("moveArchetype x is required"),
                            y = record.y ?: error("moveArchetype y is required")
                        )
                    "attack" ->
                        Command.Attack(
                            tick = record.tick,
                            units = record.units,
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "attack target")
                        )
                    "attackFaction" ->
                        Command.AttackFaction(
                            tick = record.tick,
                            faction = record.faction ?: error("attackFaction faction is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackFaction target")
                        )
                    "attackType" ->
                        Command.AttackType(
                            tick = record.tick,
                            typeId = record.typeId ?: error("attackType typeId is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackType target")
                        )
                    "attackArchetype" ->
                        Command.AttackArchetype(
                            tick = record.tick,
                            archetype = record.archetype ?: error("attackArchetype archetype is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "attackArchetype target")
                        )
                    "harvest" ->
                        Command.Harvest(
                            tick = record.tick,
                            units = record.units,
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvest target")
                        )
                    "harvestFaction" ->
                        Command.HarvestFaction(
                            tick = record.tick,
                            faction = record.faction ?: error("harvestFaction faction is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestFaction target")
                        )
                    "harvestType" ->
                        Command.HarvestType(
                            tick = record.tick,
                            typeId = record.typeId ?: error("harvestType typeId is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestType target")
                        )
                    "harvestArchetype" ->
                        Command.HarvestArchetype(
                            tick = record.tick,
                            archetype = record.archetype ?: error("harvestArchetype archetype is required"),
                            target = resolveEntityRef(record.target, record.targetLabel, ::labelId, "harvestArchetype target")
                        )
                    "spawn" ->
                        Command.Spawn(
                            tick = record.tick,
                            faction = record.faction ?: error("spawn faction is required"),
                            typeId = record.typeId ?: error("spawn typeId is required"),
                            x = record.x ?: error("spawn x is required"),
                            y = record.y ?: error("spawn y is required"),
                            vision = record.vision,
                            label = record.label,
                            labelId = record.labelId ?: record.label?.let(::labelId)
                        )
                    "spawnNode" ->
                        Command.SpawnNode(
                            tick = record.tick,
                            kind = record.typeId ?: error("spawnNode typeId is required"),
                            x = record.x ?: error("spawnNode x is required"),
                            y = record.y ?: error("spawnNode y is required"),
                            amount = record.amount ?: error("spawnNode amount is required"),
                            yieldPerTick = record.yieldPerTick ?: 0,
                            label = record.label,
                            labelId = record.labelId ?: record.label?.let(::labelId)
                        )
                    "build" ->
                        Command.Build(
                            tick = record.tick,
                            faction = record.faction ?: error("build faction is required"),
                            typeId = record.typeId ?: error("build typeId is required"),
                            tileX = record.tileX ?: error("build tileX is required"),
                            tileY = record.tileY ?: error("build tileY is required"),
                            width = record.width,
                            height = record.height,
                            hp = record.hp,
                            armor = record.armor,
                            mineralCost = record.minerals,
                            gasCost = record.gas,
                            label = record.label,
                            labelId = record.labelId ?: record.label?.let(::labelId)
                        )
                    "train" ->
                        Command.Train(
                            tick = record.tick,
                            buildingId = resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "train buildingId"),
                            typeId = record.typeId ?: error("train typeId is required"),
                            buildTicks = record.amount ?: 0,
                            mineralCost = record.minerals,
                            gasCost = record.gas
                        )
                    "cancelTrain" ->
                        Command.CancelTrain(
                            tick = record.tick,
                            buildingId = resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "cancelTrain buildingId")
                        )
                    "rally" ->
                        Command.Rally(
                            tick = record.tick,
                            buildingId = resolveEntityRef(record.buildingId, record.buildingLabel, ::labelId, "rally buildingId"),
                            x = record.x ?: error("rally x is required"),
                            y = record.y ?: error("rally y is required")
                        )
                    else -> error("unsupported input commandType '${record.commandType}'")
                }
            }
        return ScriptRunner.ScriptProgram(commands, selections)
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
