package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.net.ScriptRunner
import java.nio.file.Files

class ScriptRunnerTest {
    @Test
    fun `captures selection events with ticks`() {
        val path = Files.createTempFile("starkraft-script", ".script")
        Files.writeString(
            path,
            """
            tick 3
            selectFaction 2
            move 4 5
            wait 2
            selectType Marine
            attack 9
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(2, program.selections.size)
        assertEquals(3, program.selections[0].tick)
        assertEquals(ScriptRunner.Selection.Faction(2), program.selections[0].selection)
        assertEquals(5, program.selections[1].tick)
        assertEquals(ScriptRunner.Selection.Type("Marine"), program.selections[1].selection)
        assertEquals(2, program.commands.size)
    }

    @Test
    fun `parses build commands`() {
        val path = Files.createTempFile("starkraft-build-script", ".script")
        Files.writeString(
            path,
            """
            tick 4
            build 1 Depot 24 4 2 2 400 1 100 0
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.Build(4, 1, "Depot", 24, 4, 2, 2, 400, 1, 100, 0), program.commands[0])
    }
}
