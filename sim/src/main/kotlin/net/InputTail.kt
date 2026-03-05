package starkraft.sim.net

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class InputTailReader(
    path: Path,
    private val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
    private val maxRecordsPerPoll: Int = DEFAULT_MAX_RECORDS_PER_POLL
) : Closeable {
    private val file = RandomAccessFile(path.toFile(), "r")
    private val parser = InputJson.StatefulParser()
    var lastPollAcceptedRecords: Int = 0
        private set
    var lastPollDroppedOversized: Int = 0
        private set
    var lastPollDroppedInvalid: Int = 0
        private set
    var lastPollDroppedRateLimited: Int = 0
        private set

    fun poll(
        commandsByTick: MutableList<ArrayList<Command>>,
        selectionsByTick: MutableList<ArrayList<ScriptRunner.SelectionEvent>>,
        commandRequestIds: MutableMap<Command, String>
    ) {
        lastPollAcceptedRecords = 0
        lastPollDroppedOversized = 0
        lastPollDroppedInvalid = 0
        lastPollDroppedRateLimited = 0
        while (true) {
            val line = file.readLine() ?: break
            if (line.length > maxLineLength) {
                lastPollDroppedOversized++
                continue
            }
            val record =
                runCatching { parser.parseLine(line) }
                    .getOrElse {
                        lastPollDroppedInvalid++
                        null
                    } ?: continue
            if (lastPollAcceptedRecords >= maxRecordsPerPoll) {
                lastPollDroppedRateLimited++
                continue
            }
            when (record) {
                is InputJson.ParsedRecord.Command -> {
                    ensureCapacity(commandsByTick, record.command.tick)
                    commandsByTick[record.command.tick].add(record.command)
                    if (record.requestId != null) {
                        commandRequestIds[record.command] = record.requestId
                    }
                    lastPollAcceptedRecords++
                }
                is InputJson.ParsedRecord.Selection -> {
                    ensureCapacity(selectionsByTick, record.event.tick)
                    selectionsByTick[record.event.tick].add(record.event)
                    lastPollAcceptedRecords++
                }
            }
        }
    }

    override fun close() {
        file.close()
    }

    companion object {
        fun open(
            path: Path,
            maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
            maxRecordsPerPoll: Int = DEFAULT_MAX_RECORDS_PER_POLL
        ): InputTailReader {
            val parent = path.parent
            if (parent != null) Files.createDirectories(parent)
            if (!Files.exists(path)) Files.createFile(path)
            return InputTailReader(path, maxLineLength, maxRecordsPerPoll)
        }

        const val DEFAULT_MAX_LINE_LENGTH: Int = 16 * 1024
        const val DEFAULT_MAX_RECORDS_PER_POLL: Int = 1024

        private fun <T> ensureCapacity(store: MutableList<ArrayList<T>>, tick: Int) {
            while (store.size <= tick) {
                store.add(ArrayList())
            }
        }
    }
}
