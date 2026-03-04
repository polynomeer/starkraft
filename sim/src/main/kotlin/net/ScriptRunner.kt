package starkraft.sim.net

import java.nio.file.Files
import java.nio.file.Path

object ScriptRunner {
    private val ALL_UNITS = intArrayOf(0)
    private fun labelId(label: String, map: MutableMap<String, Int>, next: () -> Int): Int {
        return map.getOrPut(label) { next() }
    }

    sealed interface Selection {
        data class Units(val ids: IntArray) : Selection
        data object All : Selection
        data class Faction(val id: Int) : Selection
        data class Type(val typeId: String) : Selection
        data class Archetype(val archetype: String) : Selection
    }

    data class SelectionEvent(
        val tick: Int,
        val selection: Selection
    )

    data class ScriptProgram(
        val commands: List<Command>,
        val selections: List<SelectionEvent>
    )

    fun load(path: Path): List<Command> {
        return loadProgram(path).commands
    }

    fun loadProgram(path: Path): ScriptProgram {
        val lines = Files.readAllLines(path)
        var tick = 0
        var selection: Selection? = null
        val labelIds = HashMap<String, Int>()
        var nextLabelId = -1
        val out = ArrayList<Command>(lines.size)
        val selections = ArrayList<SelectionEvent>()

        for ((idx, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            try {
                when (parts[0]) {
                "tick" -> {
                    require(parts.size == 2) { "tick <n>" }
                    tick = parts[1].toInt()
                }
                "wait" -> {
                    require(parts.size == 2) { "wait <n>" }
                    tick += parts[1].toInt()
                }
                "select" -> {
                    val selected = ArrayList<Int>()
                    for (i in 1 until parts.size) {
                        val token = parts[i]
                        if (token.startsWith("@")) {
                            selected.add(labelId(token.substring(1), labelIds) { nextLabelId-- })
                        } else {
                            selected.add(token.toInt())
                        }
                    }
                    selection = Selection.Units(selected.toIntArray())
                    selections.add(SelectionEvent(tick, selection))
                }
                "selectAll" -> {
                    selection = Selection.All
                    selections.add(SelectionEvent(tick, selection))
                }
                "selectFaction" -> {
                    require(parts.size == 2) { "selectFaction <id>" }
                    selection = Selection.Faction(parts[1].toInt())
                    selections.add(SelectionEvent(tick, selection))
                }
                "selectType" -> {
                    require(parts.size == 2) { "selectType <typeId>" }
                    selection = Selection.Type(parts[1])
                    selections.add(SelectionEvent(tick, selection))
                }
                "selectArchetype" -> {
                    require(parts.size == 2) { "selectArchetype <id>" }
                    selection = Selection.Archetype(parts[1])
                    selections.add(SelectionEvent(tick, selection))
                }
                "move" -> {
                    require(parts.size == 3) { "move <x> <y>" }
                    require(selection != null) { "move requires selection" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(moveCommand(tick, selection!!, x, y))
                }
                "patrol" -> {
                    require(parts.size == 3) { "patrol <x> <y>" }
                    require(selection != null) { "patrol requires selection" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(patrolCommand(tick, selection!!, x, y))
                }
                "attackMove" -> {
                    require(parts.size == 3) { "attackMove <x> <y>" }
                    require(selection != null) { "attackMove requires selection" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(attackMoveCommand(tick, selection!!, x, y))
                }
                "hold" -> {
                    require(parts.size == 1) { "hold" }
                    require(selection != null) { "hold requires selection" }
                    out.add(holdCommand(tick, selection!!))
                }
                "attack" -> {
                    require(parts.size == 2) { "attack <targetId>" }
                    require(selection != null) { "attack requires selection" }
                    val token = parts[1]
                    val target = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(attackCommand(tick, selection!!, target))
                }
                "harvest" -> {
                    require(parts.size == 2) { "harvest <targetId>" }
                    require(selection != null) { "harvest requires selection" }
                    val token = parts[1]
                    val target = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(harvestCommand(tick, selection!!, target))
                }
                "construct" -> {
                    require(parts.size == 2) { "construct <targetId>" }
                    require(selection != null) { "construct requires selection" }
                    val token = parts[1]
                    val target = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(constructCommand(tick, selection!!, target))
                }
                "spawn" -> {
                    require(parts.size in 5..7) { "spawn [@label] <faction> <typeId> <x> <y> [vision]" }
                    var idxStart = 1
                    var label: String? = null
                    var labelIdValue: Int? = null
                    if (parts[1].startsWith("@")) {
                        label = parts[1].substring(1)
                        labelIdValue = labelId(label, labelIds) { nextLabelId-- }
                        idxStart++
                    }
                    val faction = parts[idxStart].toInt()
                    val typeId = parts[idxStart + 1]
                    val x = parts[idxStart + 2].toFloat()
                    val y = parts[idxStart + 3].toFloat()
                    val vision = if (parts.size > idxStart + 4) parts[idxStart + 4].toFloat() else null
                    out.add(Command.Spawn(tick, faction, typeId, x, y, vision, label, labelIdValue))
                }
                "spawnNode" -> {
                    require(parts.size in 5..7) { "spawnNode [@label] <kind> <x> <y> <amount> [yield]" }
                    var idxStart = 1
                    var label: String? = null
                    var labelIdValue: Int? = null
                    if (parts[1].startsWith("@")) {
                        label = parts[1].substring(1)
                        labelIdValue = labelId(label, labelIds) { nextLabelId-- }
                        idxStart++
                    }
                    val kind = parts[idxStart]
                    val x = parts[idxStart + 1].toFloat()
                    val y = parts[idxStart + 2].toFloat()
                    val amount = parts[idxStart + 3].toInt()
                    val yieldPerTick = if (parts.size > idxStart + 4) parts[idxStart + 4].toInt() else 0
                    out.add(Command.SpawnNode(tick, kind, x, y, amount, yieldPerTick, label, labelIdValue))
                }
                "build" -> {
                    require(parts.size in 5..12) {
                        "build [@label] <faction> <typeId> <tileX> <tileY> [width] [height] [hp] [armor] [minerals] [gas]"
                    }
                    var idxStart = 1
                    var label: String? = null
                    var labelIdValue: Int? = null
                    if (parts[1].startsWith("@")) {
                        label = parts[1].substring(1)
                        labelIdValue = labelId(label, labelIds) { nextLabelId-- }
                        idxStart++
                    }
                    val faction = parts[idxStart].toInt()
                    val typeId = parts[idxStart + 1]
                    val tileX = parts[idxStart + 2].toInt()
                    val tileY = parts[idxStart + 3].toInt()
                    val width = if (parts.size > idxStart + 4) parts[idxStart + 4].toInt() else 0
                    val height = if (parts.size > idxStart + 5) parts[idxStart + 5].toInt() else 0
                    val hp = if (parts.size > idxStart + 6) parts[idxStart + 6].toInt() else 0
                    val armor = if (parts.size > idxStart + 7) parts[idxStart + 7].toInt() else 0
                    val minerals = if (parts.size > idxStart + 8) parts[idxStart + 8].toInt() else 0
                    val gas = if (parts.size > idxStart + 9) parts[idxStart + 9].toInt() else 0
                    out.add(Command.Build(tick, faction, typeId, tileX, tileY, width, height, hp, armor, minerals, gas, label, labelIdValue))
                }
                "train" -> {
                    require(parts.size in 3..6) { "train <buildingId|@label> <typeId> [buildTicks] [minerals] [gas]" }
                    val token = parts[1]
                    val buildingId = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    val typeId = parts[2]
                    val buildTicks = if (parts.size > 3) parts[3].toInt() else 0
                    val minerals = if (parts.size > 4) parts[4].toInt() else 0
                    val gas = if (parts.size > 5) parts[5].toInt() else 0
                    out.add(Command.Train(tick, buildingId, typeId, buildTicks, minerals, gas))
                }
                "cancelTrain" -> {
                    require(parts.size == 2) { "cancelTrain <buildingId|@label>" }
                    val token = parts[1]
                    val buildingId = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(Command.CancelTrain(tick, buildingId))
                }
                "cancelBuild" -> {
                    require(parts.size == 2) { "cancelBuild <buildingId|@label>" }
                    val token = parts[1]
                    val buildingId = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(Command.CancelBuild(tick, buildingId))
                }
                "research" -> {
                    require(parts.size in 3..6) { "research <buildingId|@label> <techId> [buildTicks] [minerals] [gas]" }
                    val token = parts[1]
                    val buildingId = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    val techId = parts[2]
                    val buildTicks = if (parts.size > 3) parts[3].toInt() else 0
                    val minerals = if (parts.size > 4) parts[4].toInt() else 0
                    val gas = if (parts.size > 5) parts[5].toInt() else 0
                    out.add(Command.Research(tick, buildingId, techId, buildTicks, minerals, gas))
                }
                "rally" -> {
                    require(parts.size == 4) { "rally <buildingId|@label> <x> <y>" }
                    val token = parts[1]
                    val buildingId = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(Command.Rally(tick, buildingId, parts[2].toFloat(), parts[3].toFloat()))
                }
                else -> error("Unknown command")
            }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                val token = parts.firstOrNull() ?: ""
                error("Script error at line ${idx + 1} token '$token': $msg")
            }
        }
        return ScriptProgram(commands = out, selections = selections)
    }

    private fun moveCommand(tick: Int, selection: Selection, x: Float, y: Float): Command {
        return when (selection) {
            is Selection.Units -> Command.Move(tick, selection.ids, x, y)
            is Selection.All -> Command.Move(tick, ALL_UNITS, x, y)
            is Selection.Faction -> Command.MoveFaction(tick, selection.id, x, y)
            is Selection.Type -> Command.MoveType(tick, selection.typeId, x, y)
            is Selection.Archetype -> Command.MoveArchetype(tick, selection.archetype, x, y)
        }
    }

    private fun patrolCommand(tick: Int, selection: Selection, x: Float, y: Float): Command {
        return when (selection) {
            is Selection.Units -> Command.Patrol(tick, selection.ids, x, y)
            is Selection.All -> Command.Patrol(tick, ALL_UNITS, x, y)
            is Selection.Faction -> Command.PatrolFaction(tick, selection.id, x, y)
            is Selection.Type -> Command.PatrolType(tick, selection.typeId, x, y)
            is Selection.Archetype -> Command.PatrolArchetype(tick, selection.archetype, x, y)
        }
    }

    private fun attackCommand(tick: Int, selection: Selection, target: Int): Command {
        return when (selection) {
            is Selection.Units -> Command.Attack(tick, selection.ids, target)
            is Selection.All -> Command.Attack(tick, ALL_UNITS, target)
            is Selection.Faction -> Command.AttackFaction(tick, selection.id, target)
            is Selection.Type -> Command.AttackType(tick, selection.typeId, target)
            is Selection.Archetype -> Command.AttackArchetype(tick, selection.archetype, target)
        }
    }

    private fun constructCommand(tick: Int, selection: Selection, target: Int): Command {
        return when (selection) {
            is Selection.Units -> Command.Construct(tick, selection.ids, target)
            is Selection.All -> Command.Construct(tick, ALL_UNITS, target)
            is Selection.Faction -> Command.ConstructFaction(tick, selection.id, target)
            is Selection.Type -> Command.ConstructType(tick, selection.typeId, target)
            is Selection.Archetype -> Command.ConstructArchetype(tick, selection.archetype, target)
        }
    }

    private fun attackMoveCommand(tick: Int, selection: Selection, x: Float, y: Float): Command {
        return when (selection) {
            is Selection.Units -> Command.AttackMove(tick, selection.ids, x, y)
            is Selection.All -> Command.AttackMove(tick, ALL_UNITS, x, y)
            is Selection.Faction -> Command.AttackMoveFaction(tick, selection.id, x, y)
            is Selection.Type -> Command.AttackMoveType(tick, selection.typeId, x, y)
            is Selection.Archetype -> Command.AttackMoveArchetype(tick, selection.archetype, x, y)
        }
    }

    private fun harvestCommand(tick: Int, selection: Selection, target: Int): Command {
        return when (selection) {
            is Selection.Units -> Command.Harvest(tick, selection.ids, target)
            is Selection.All -> Command.Harvest(tick, ALL_UNITS, target)
            is Selection.Faction -> Command.HarvestFaction(tick, selection.id, target)
            is Selection.Type -> Command.HarvestType(tick, selection.typeId, target)
            is Selection.Archetype -> Command.HarvestArchetype(tick, selection.archetype, target)
        }
    }

    private fun holdCommand(tick: Int, selection: Selection): Command {
        return when (selection) {
            is Selection.Units -> Command.Hold(tick, selection.ids)
            is Selection.All -> Command.Hold(tick, ALL_UNITS)
            is Selection.Faction -> Command.HoldFaction(tick, selection.id)
            is Selection.Type -> Command.HoldType(tick, selection.typeId)
            is Selection.Archetype -> Command.HoldArchetype(tick, selection.archetype)
        }
    }
}
