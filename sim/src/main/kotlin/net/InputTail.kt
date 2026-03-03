package starkraft.sim.net

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class InputTailReader(path: Path) : Closeable {
    private val file = RandomAccessFile(path.toFile(), "r")
    private val parser = InputJson.StatefulParser()

    fun poll(
        commandsByTick: MutableList<ArrayList<Command>>,
        selectionsByTick: MutableList<ArrayList<ScriptRunner.SelectionEvent>>
    ) {
        while (true) {
            val line = file.readLine() ?: break
            when (val record = parser.parseLine(line) ?: continue) {
                is InputJson.ParsedRecord.Command -> {
                    ensureCapacity(commandsByTick, record.command.tick)
                    commandsByTick[record.command.tick].add(record.command)
                }
                is InputJson.ParsedRecord.Selection -> {
                    ensureCapacity(selectionsByTick, record.event.tick)
                    selectionsByTick[record.event.tick].add(record.event)
                }
            }
        }
    }

    override fun close() {
        file.close()
    }

    companion object {
        fun open(path: Path): InputTailReader {
            val parent = path.parent
            if (parent != null) Files.createDirectories(parent)
            if (!Files.exists(path)) Files.createFile(path)
            return InputTailReader(path)
        }

        private fun <T> ensureCapacity(store: MutableList<ArrayList<T>>, tick: Int) {
            while (store.size <= tick) {
                store.add(ArrayList())
            }
        }
    }
}

