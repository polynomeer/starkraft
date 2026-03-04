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
            wait 1
            selectArchetype infantry
            move 6 7
            wait 1
            harvest 12
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(3, program.selections.size)
        assertEquals(3, program.selections[0].tick)
        assertEquals(ScriptRunner.Selection.Faction(2), program.selections[0].selection)
        assertEquals(5, program.selections[1].tick)
        assertEquals(ScriptRunner.Selection.Type("Marine"), program.selections[1].selection)
        assertEquals(6, program.selections[2].tick)
        assertEquals(ScriptRunner.Selection.Archetype("infantry"), program.selections[2].selection)
        assertEquals(4, program.commands.size)
        assertEquals(Command.MoveArchetype(6, "infantry", 6f, 7f), program.commands[2])
        assertEquals(Command.HarvestArchetype(7, "infantry", 12), program.commands[3])
    }

    @Test
    fun `parses attack move commands`() {
        val path = Files.createTempFile("starkraft-attackmove-script", ".script")
        Files.writeString(
            path,
            """
            tick 2
            selectArchetype infantry
            attackMove 14 7
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.AttackMoveArchetype(2, "infantry", 14f, 7f), program.commands[0])
    }

    @Test
    fun `parses hold commands`() {
        val path = Files.createTempFile("starkraft-hold-script", ".script")
        Files.writeString(
            path,
            """
            tick 3
            selectFaction 2
            hold
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.HoldFaction(3, 2), program.commands[0])
    }

    @Test
    fun `parses patrol commands`() {
        val path = Files.createTempFile("starkraft-patrol-script", ".script")
        Files.writeString(
            path,
            """
            tick 4
            selectType Marine
            patrol 12 6
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.PatrolType(4, "Marine", 12f, 6f), program.commands[0])
    }

    @Test
    fun `parses research commands`() {
        val path = Files.createTempFile("starkraft-research-script", ".script")
        Files.writeString(
            path,
            """
            tick 4
            research @depot AdvancedTraining 60 75 0
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.Research(4, -1, "AdvancedTraining", 60, 75, 0), program.commands[0])
    }

    @Test
    fun `parses construct commands`() {
        val path = Files.createTempFile("starkraft-construct-script", ".script")
        Files.writeString(
            path,
            """
            tick 5
            selectArchetype worker
            construct @site
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.ConstructArchetype(5, "worker", -1), program.commands[0])
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
        assertEquals(Command.Build(4, 1, "Depot", 24, 4, 2, 2, 400, 1, 100, 0, null, null), program.commands[0])
    }

    @Test
    fun `parses spawn node commands with labels`() {
        val path = Files.createTempFile("starkraft-node-script", ".script")
        Files.writeString(
            path,
            """
            tick 4
            spawnNode @ore MineralField 9 10 250
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.SpawnNode(4, "MineralField", 9f, 10f, 250, 0, "ore", -1), program.commands[0])
    }

    @Test
    fun `parses spawn node commands with explicit yield`() {
        val path = Files.createTempFile("starkraft-node-script", ".script")
        Files.writeString(
            path,
            """
            tick 4
            spawnNode @ore MineralField 9 10 250 3
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(1, program.commands.size)
        assertEquals(Command.SpawnNode(4, "MineralField", 9f, 10f, 250, 3, "ore", -1), program.commands[0])
    }

    @Test
    fun `parses train commands with labels`() {
        val path = Files.createTempFile("starkraft-train-script", ".script")
        Files.writeString(
            path,
            """
            tick 0
            build @depot 1 Depot 4 4 2 2 400 0 100 0
            wait 1
            train @depot Marine 75 50 0
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(2, program.commands.size)
        assertEquals(Command.Build(0, 1, "Depot", 4, 4, 2, 2, 400, 0, 100, 0, "depot", -1), program.commands[0])
        assertEquals(Command.Train(1, -1, "Marine", 75, 50, 0), program.commands[1])
    }

    @Test
    fun `parses build and train commands with data defaults`() {
        val path = Files.createTempFile("starkraft-default-build-script", ".script")
        Files.writeString(
            path,
            """
            tick 2
            build @depot 1 Depot 10 10
            train @depot Marine
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(Command.Build(2, 1, "Depot", 10, 10, 0, 0, 0, 0, 0, 0, "depot", -1), program.commands[0])
        assertEquals(Command.Train(2, -1, "Marine", 0, 0, 0), program.commands[1])
    }

    @Test
    fun `parses cancel train commands`() {
        val path = Files.createTempFile("starkraft-cancel-train-script", ".script")
        Files.writeString(
            path,
            """
            tick 0
            build @depot 1 Depot 4 4
            wait 1
            cancelTrain @depot
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(Command.CancelTrain(1, -1), program.commands[1])
    }

    @Test
    fun `parses cancel build commands`() {
        val path = Files.createTempFile("starkraft-cancel-build-script", ".script")
        Files.writeString(
            path,
            """
            tick 0
            build @depot 1 Depot 4 4
            wait 1
            cancelBuild @depot
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(Command.CancelBuild(1, -1), program.commands[1])
    }

    @Test
    fun `parses rally commands`() {
        val path = Files.createTempFile("starkraft-rally-script", ".script")
        Files.writeString(
            path,
            """
            tick 0
            build @depot 1 Depot 4 4
            wait 1
            rally @depot 20 21
            """.trimIndent()
        )

        val program = ScriptRunner.loadProgram(path)

        assertEquals(Command.Rally(1, -1, 20f, 21f), program.commands[1])
    }
}
