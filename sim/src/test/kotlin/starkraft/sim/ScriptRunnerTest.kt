package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
}
