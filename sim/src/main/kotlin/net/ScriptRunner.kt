package starkraft.sim.net

import java.nio.file.Files
import java.nio.file.Path

object ScriptRunner {
    private val ALL_UNITS = intArrayOf(0)
    private fun labelId(label: String, map: MutableMap<String, Int>, next: () -> Int): Int {
        return map.getOrPut(label) { next() }
    }

    private sealed interface Selection {
        data class Units(val ids: IntArray) : Selection
        data object All : Selection
        data class Faction(val id: Int) : Selection
        data class Type(val typeId: String) : Selection
    }

    fun load(path: Path): List<Command> {
        val lines = Files.readAllLines(path)
        var tick = 0
        var selection: Selection? = null
        val labelIds = HashMap<String, Int>()
        var nextLabelId = -1
        val out = ArrayList<Command>(lines.size)

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
                }
                "selectAll" -> {
                    selection = Selection.All
                }
                "selectFaction" -> {
                    require(parts.size == 2) { "selectFaction <id>" }
                    selection = Selection.Faction(parts[1].toInt())
                }
                "selectType" -> {
                    require(parts.size == 2) { "selectType <typeId>" }
                    selection = Selection.Type(parts[1])
                }
                "move" -> {
                    require(parts.size == 3) { "move <x> <y>" }
                    require(selection != null) { "move requires selection" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(moveCommand(tick, selection!!, x, y))
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
                else -> error("Unknown command")
            }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                val token = parts.firstOrNull() ?: ""
                error("Script error at line ${idx + 1} token '$token': $msg")
            }
        }
        return out
    }

    private fun moveCommand(tick: Int, selection: Selection, x: Float, y: Float): Command {
        return when (selection) {
            is Selection.Units -> Command.Move(tick, selection.ids, x, y)
            is Selection.All -> Command.Move(tick, ALL_UNITS, x, y)
            is Selection.Faction -> Command.MoveFaction(tick, selection.id, x, y)
            is Selection.Type -> Command.MoveType(tick, selection.typeId, x, y)
        }
    }

    private fun attackCommand(tick: Int, selection: Selection, target: Int): Command {
        return when (selection) {
            is Selection.Units -> Command.Attack(tick, selection.ids, target)
            is Selection.All -> Command.Attack(tick, ALL_UNITS, target)
            is Selection.Faction -> Command.AttackFaction(tick, selection.id, target)
            is Selection.Type -> Command.AttackType(tick, selection.typeId, target)
        }
    }
}
