package starkraft.sim.net

import java.nio.file.Files
import java.nio.file.Path

object ScriptRunner {
    private val ALL_UNITS = intArrayOf(0)
    private fun labelId(label: String, map: MutableMap<String, Int>, next: () -> Int): Int {
        return map.getOrPut(label) { next() }
    }

    fun load(path: Path): List<Command> {
        val lines = Files.readAllLines(path)
        var tick = 0
        val selected = ArrayList<Int>()
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
                    selected.clear()
                    for (i in 1 until parts.size) {
                        val token = parts[i]
                        if (token.startsWith("@")) {
                            selected.add(labelId(token.substring(1), labelIds) { nextLabelId-- })
                        } else {
                            selected.add(token.toInt())
                        }
                    }
                }
                "selectAll" -> {
                    selected.clear()
                    selected.addAll(ALL_UNITS)
                }
                "move" -> {
                    require(parts.size == 3) { "move <x> <y>" }
                    require(selected.isNotEmpty()) { "move requires selection" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(Command.Move(tick, selected.toIntArray(), x, y))
                }
                "attack" -> {
                    require(parts.size == 2) { "attack <targetId>" }
                    require(selected.isNotEmpty()) { "attack requires selection" }
                    val token = parts[1]
                    val target = if (token.startsWith("@")) {
                        labelId(token.substring(1), labelIds) { nextLabelId-- }
                    } else {
                        token.toInt()
                    }
                    out.add(Command.Attack(tick, selected.toIntArray(), target))
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
}
