package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.net.Command
import starkraft.sim.replay.ReplayIO
import java.nio.file.Files

class ReplayIOTest {
    @Test
    fun roundTripCommands() {
        val tmp = Files.createTempFile("starkraft-replay", ".json")
        val cmds = listOf(
            Command.Move(0, intArrayOf(1, 2, 3), 5.5f, 6.5f),
            Command.MoveFaction(5, 1, 8f, 9f),
            Command.MoveType(7, "Marine", 4f, 5f),
            Command.Attack(10, intArrayOf(2), 7),
            Command.AttackFaction(12, 2, 9),
            Command.AttackType(14, "Zergling", 3),
            Command.Spawn(20, 1, "Marine", 3f, 4f, 6f, label = "alpha", labelId = -1)
        )
        ReplayIO.save(tmp, cmds, seed = 1234L)
        val loaded = ReplayIO.load(tmp)
        val payload = Files.readString(tmp)
        assertTrue(payload.contains("\"replayHash\""))
        assertTrue(payload.contains("\"seed\":1234"))
        assertEquals(cmds.size, loaded.size)
        for (i in cmds.indices) {
            assertCommandsEqual(cmds[i], loaded[i])
        }
    }

    @Test
    fun assignsLabelIdsWhenMissing() {
        val tmp = Files.createTempFile("starkraft-replay", ".json")
        val cmds = listOf(
            Command.Spawn(0, 1, "Marine", 1f, 1f, 6f, label = "alpha", labelId = null),
            Command.Move(1, intArrayOf(-1), 2f, 2f)
        )
        ReplayIO.save(tmp, cmds)
        val loaded = ReplayIO.load(tmp)
        val spawn = loaded[0] as Command.Spawn
        assertEquals("alpha", spawn.label)
        assertEquals(-1, spawn.labelId)
    }

    @Test
    fun inspectsReplayMetadata() {
        val tmp = Files.createTempFile("starkraft-replay", ".json")
        val cmds = listOf(Command.Move(0, intArrayOf(1), 2f, 3f))
        ReplayIO.save(tmp, cmds, seed = 77L)
        val meta = ReplayIO.inspect(tmp)
        assertEquals(1, meta.schema)
        assertEquals(77L, meta.seed)
        assertTrue(meta.replayHash != null)
        assertEquals(false, meta.legacy)
    }
}

private fun assertCommandsEqual(a: Command, b: Command) {
    when (a) {
        is Command.Move -> {
            require(b is Command.Move)
            assertEquals(a.tick, b.tick)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.Attack -> {
            require(b is Command.Attack)
            assertEquals(a.tick, b.tick)
            assertEquals(a.target, b.target)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.MoveFaction -> {
            require(b is Command.MoveFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.MoveType -> {
            require(b is Command.MoveType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.AttackFaction -> {
            require(b is Command.AttackFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.target, b.target)
        }
        is Command.AttackType -> {
            require(b is Command.AttackType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.target, b.target)
        }
        is Command.Spawn -> {
            require(b is Command.Spawn)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.vision, b.vision)
            assertEquals(a.label, b.label)
            assertEquals(a.labelId, b.labelId)
        }
    }
}
