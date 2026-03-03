package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.net.InputJson
import starkraft.sim.net.ScriptRunner
import java.nio.file.Files

class InputJsonTest {
    @Test
    fun `loads input json commands with label refs`() {
        val path = Files.createTempFile("starkraft-input", ".json")
        Files.writeString(
            path,
            """
            {
              "commands": [
                {"tick": 0, "commandType": "build", "faction": 1, "typeId": "Depot", "tileX": 4, "tileY": 4, "label": "depot"},
                {"tick": 1, "commandType": "train", "buildingLabel": "depot", "typeId": "Marine"},
                {"tick": 2, "commandType": "rally", "buildingLabel": "depot", "x": 14.0, "y": 15.0},
                {"tick": 3, "commandType": "spawnNode", "typeId": "MineralField", "x": 8.0, "y": 9.0, "amount": 250, "yieldPerTick": 3, "label": "ore"},
                {"tick": 4, "commandType": "harvest", "units": [7, 8], "targetLabel": "ore"}
              ]
            }
            """.trimIndent()
        )

        val program = InputJson.loadProgram(path)

        assertEquals(Command.Build(0, 1, "Depot", 4, 4, 0, 0, 0, 0, 0, 0, "depot", -1), program.commands[0])
        assertEquals(Command.Train(1, -1, "Marine", 0, 0, 0), program.commands[1])
        assertEquals(Command.Rally(2, -1, 14f, 15f), program.commands[2])
        assertEquals(Command.SpawnNode(3, "MineralField", 8f, 9f, 250, 3, "ore", -2), program.commands[3])
        val harvest = program.commands[4] as Command.Harvest
        assertArrayEquals(intArrayOf(7, 8), harvest.units)
        assertEquals(-2, harvest.target)
    }

    @Test
    fun `loads input json selections`() {
        val path = Files.createTempFile("starkraft-input-select", ".json")
        Files.writeString(
            path,
            """
            {
              "selections": [
                {"tick": 0, "selectionType": "units", "units": [1, 2]},
                {"tick": 2, "selectionType": "faction", "faction": 2},
                {"tick": 3, "selectionType": "type", "typeId": "Marine"},
                {"tick": 4, "selectionType": "archetype", "archetype": "infantry"},
                {"tick": 5, "selectionType": "all"}
              ]
            }
            """.trimIndent()
        )

        val program = InputJson.loadProgram(path)

        val units = program.selections[0].selection as ScriptRunner.Selection.Units
        assertEquals(0, program.selections[0].tick)
        assertArrayEquals(intArrayOf(1, 2), units.ids)
        assertEquals(ScriptRunner.Selection.Faction(2), program.selections[1].selection)
        assertEquals(ScriptRunner.Selection.Type("Marine"), program.selections[2].selection)
        assertEquals(ScriptRunner.Selection.Archetype("infantry"), program.selections[3].selection)
        assertEquals(ScriptRunner.Selection.All, program.selections[4].selection)
    }

    @Test
    fun `loads input ndjson records`() {
        val program =
            InputJson.loadProgram(
                """
            {"tick":0,"selectionType":"faction","faction":1}
            {"tick":0,"commandType":"moveFaction","faction":1,"x":12.0,"y":13.0}
            {"tick":2,"commandType":"build","faction":1,"typeId":"Depot","tileX":6,"tileY":6,"label":"depot"}
            {"tick":3,"commandType":"train","buildingLabel":"depot","typeId":"Marine"}
            """.trimIndent()
            )

        assertEquals(1, program.selections.size)
        assertEquals(ScriptRunner.Selection.Faction(1), program.selections[0].selection)
        assertEquals(Command.MoveFaction(0, 1, 12f, 13f), program.commands[0])
        assertEquals(Command.Build(2, 1, "Depot", 6, 6, 0, 0, 0, 0, 0, 0, "depot", -1), program.commands[1])
        assertEquals(Command.Train(3, -1, "Marine", 0, 0, 0), program.commands[2])
    }

    @Test
    fun `preserves request ids for client commands`() {
        val program =
            InputJson.loadLoadedProgram(
                """
            {"tick":0,"commandType":"move","requestId":"cli-1","units":[7],"x":12.0,"y":13.0}
            {"tick":1,"commandType":"harvest","units":[8],"target":21}
            """.trimIndent()
            )

        assertEquals("cli-1", program.commandRequestIds[program.program.commands[0]])
        assertEquals(null, program.commandRequestIds[program.program.commands[1]])
    }
}
