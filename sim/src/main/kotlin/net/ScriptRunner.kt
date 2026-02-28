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
                    for (i in 1 until parts.size) selected.add(parts[i].toInt())
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
                    val target = parts[1].toInt()
                    out.add(Command.Attack(tick, selected.toIntArray(), target))
                }
                "spawn" -> {
                    require(parts.size in 5..6) { "spawn <faction> <typeId> <x> <y> [vision]" }
                    val faction = parts[1].toInt()
                    val typeId = parts[2]
                    val x = parts[3].toFloat()
                    val y = parts[4].toFloat()
                    val vision = if (parts.size == 6) parts[5].toFloat() else null
                    out.add(Command.Spawn(tick, faction, typeId, x, y, vision))
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
