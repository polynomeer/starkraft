package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.net.InputTailReader
import starkraft.sim.net.ScriptRunner
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.IdentityHashMap

class InputTailReaderTest {
    @Test
    fun `poll reads appended ndjson records incrementally`() {
        val path = Files.createTempFile("starkraft-tail", ".ndjson")
        val commandsByTick = ArrayList<ArrayList<Command>>()
        val selectionsByTick = ArrayList<ArrayList<ScriptRunner.SelectionEvent>>()
        val commandRequestIds = IdentityHashMap<Command, String>()

        InputTailReader.open(path).use { reader ->
            Files.writeString(
                path,
                """
                {"tick":0,"selectionType":"faction","faction":1}
                {"tick":1,"commandType":"build","requestId":"cli-1","faction":1,"typeId":"Depot","tileX":4,"tileY":4,"label":"depot"}
                """.trimIndent() + "\n",
                StandardOpenOption.APPEND
            )
            reader.poll(commandsByTick, selectionsByTick, commandRequestIds)

            assertEquals(ScriptRunner.Selection.Faction(1), selectionsByTick[0][0].selection)
            assertEquals(Command.Build(1, 1, "Depot", 4, 4, 0, 0, 0, 0, 0, 0, "depot", -1), commandsByTick[1][0])
            assertEquals("cli-1", commandRequestIds[commandsByTick[1][0]])

            Files.writeString(
                path,
                """
                {"tick":2,"commandType":"train","buildingLabel":"depot","typeId":"Marine"}
                {"tick":3,"commandType":"move","requestId":"cli-2","units":[7,8],"x":12.0,"y":13.0}
                """.trimIndent() + "\n",
                StandardOpenOption.APPEND
            )
            reader.poll(commandsByTick, selectionsByTick, commandRequestIds)

            assertEquals(Command.Train(2, -1, "Marine", 0, 0, 0), commandsByTick[2][0])
            val move = commandsByTick[3][0] as Command.Move
            assertArrayEquals(intArrayOf(7, 8), move.units)
            assertEquals(12f, move.x)
            assertEquals(13f, move.y)
            assertEquals("cli-2", commandRequestIds[move])
        }
    }
}
