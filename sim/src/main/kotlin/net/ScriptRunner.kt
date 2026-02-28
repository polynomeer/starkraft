package starkraft.sim.net

import java.nio.file.Files
import java.nio.file.Path

object ScriptRunner {
    fun load(path: Path): List<Command> {
        val lines = Files.readAllLines(path)
        var tick = 0
        val selected = ArrayList<Int>()
        val out = ArrayList<Command>(lines.size)

        for ((idx, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "tick" -> {
                    require(parts.size == 2) { "tick <n> at line ${idx + 1}" }
                    tick = parts[1].toInt()
                }
                "wait" -> {
                    require(parts.size == 2) { "wait <n> at line ${idx + 1}" }
                    tick += parts[1].toInt()
                }
                "select" -> {
                    selected.clear()
                    for (i in 1 until parts.size) selected.add(parts[i].toInt())
                }
                "move" -> {
                    require(parts.size == 3) { "move <x> <y> at line ${idx + 1}" }
                    require(selected.isNotEmpty()) { "move requires selection at line ${idx + 1}" }
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    out.add(Command.Move(tick, selected.toIntArray(), x, y))
                }
                "attack" -> {
                    require(parts.size == 2) { "attack <targetId> at line ${idx + 1}" }
                    require(selected.isNotEmpty()) { "attack requires selection at line ${idx + 1}" }
                    val target = parts[1].toInt()
                    out.add(Command.Attack(tick, selected.toIntArray(), target))
                }
                else -> error("Unknown script command '${parts[0]}' at line ${idx + 1}")
            }
        }
        return out
    }
}
