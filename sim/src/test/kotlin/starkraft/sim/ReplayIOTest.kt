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
            Command.MoveArchetype(8, "infantry", 6f, 7f),
            Command.Patrol(8, intArrayOf(1, 2), 7f, 7f),
            Command.PatrolFaction(8, 1, 8f, 7f),
            Command.PatrolType(8, "Marine", 9f, 7f),
            Command.PatrolArchetype(8, "infantry", 10f, 7f),
            Command.AttackMove(9, intArrayOf(1, 3), 7f, 8f),
            Command.AttackMoveFaction(9, 1, 8f, 8f),
            Command.AttackMoveType(9, "Marine", 9f, 8f),
            Command.AttackMoveArchetype(9, "infantry", 10f, 8f),
            Command.Hold(9, intArrayOf(1, 3)),
            Command.HoldFaction(9, 1),
            Command.HoldType(9, "Marine"),
            Command.HoldArchetype(9, "infantry"),
            Command.Attack(10, intArrayOf(2), 7),
            Command.AttackFaction(12, 2, 9),
            Command.AttackType(14, "Zergling", 3),
            Command.AttackArchetype(15, "lightMelee", 4),
            Command.Harvest(16, intArrayOf(1, 2), 99),
            Command.HarvestFaction(17, 1, 99),
            Command.HarvestType(18, "Worker", 99),
            Command.HarvestArchetype(19, "worker", 99),
            Command.SpawnNode(19, "MineralField", 6f, 7f, 250, 2, "ore", -3),
            Command.Spawn(20, 1, "Marine", 3f, 4f, 6f, label = "alpha", labelId = -1),
            Command.Build(25, 1, "Depot", 24, 4, 2, 2, 400, 1, 100, 0, label = "depot", labelId = -2),
            Command.Train(30, -2, "Marine", 75, 50, 0),
            Command.CancelTrain(31, -2),
            Command.Research(31, -2, "AdvancedTraining", 60, 75, 0),
            Command.Rally(32, -2, 20f, 21f)
        )
        ReplayIO.save(tmp, cmds, seed = 1234L, mapId = "demo-map", buildVersion = "test-build")
        val loaded = ReplayIO.load(tmp)
        val payload = Files.readString(tmp)
        assertTrue(payload.contains("\"replayHash\""))
        assertTrue(payload.contains("\"seed\":1234"))
        assertTrue(payload.contains("\"mapId\":\"demo-map\""))
        assertTrue(payload.contains("\"buildVersion\":\"test-build\""))
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
        ReplayIO.save(tmp, cmds, seed = 77L, mapId = "demo-map", buildVersion = "test-build")
        val meta = ReplayIO.inspect(tmp)
        assertEquals(1, meta.schema)
        assertEquals(77L, meta.seed)
        assertEquals("demo-map", meta.mapId)
        assertEquals("test-build", meta.buildVersion)
        assertEquals(1, meta.eventCount)
        assertTrue(meta.fileSizeBytes > 0)
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
        is Command.AttackMove -> {
            require(b is Command.AttackMove)
            assertEquals(a.tick, b.tick)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.MoveFaction -> {
            require(b is Command.MoveFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.AttackMoveFaction -> {
            require(b is Command.AttackMoveFaction)
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
        is Command.AttackMoveType -> {
            require(b is Command.AttackMoveType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.MoveArchetype -> {
            require(b is Command.MoveArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.Patrol -> {
            require(b is Command.Patrol)
            assertEquals(a.tick, b.tick)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.PatrolFaction -> {
            require(b is Command.PatrolFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.PatrolType -> {
            require(b is Command.PatrolType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.PatrolArchetype -> {
            require(b is Command.PatrolArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.AttackMoveArchetype -> {
            require(b is Command.AttackMoveArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
        is Command.Hold -> {
            require(b is Command.Hold)
            assertEquals(a.tick, b.tick)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.HoldFaction -> {
            require(b is Command.HoldFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
        }
        is Command.HoldType -> {
            require(b is Command.HoldType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
        }
        is Command.HoldArchetype -> {
            require(b is Command.HoldArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
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
        is Command.AttackArchetype -> {
            require(b is Command.AttackArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
            assertEquals(a.target, b.target)
        }
        is Command.Harvest -> {
            require(b is Command.Harvest)
            assertEquals(a.tick, b.tick)
            assertEquals(a.target, b.target)
            assertEquals(a.units.toList(), b.units.toList())
        }
        is Command.HarvestFaction -> {
            require(b is Command.HarvestFaction)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.target, b.target)
        }
        is Command.HarvestType -> {
            require(b is Command.HarvestType)
            assertEquals(a.tick, b.tick)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.target, b.target)
        }
        is Command.HarvestArchetype -> {
            require(b is Command.HarvestArchetype)
            assertEquals(a.tick, b.tick)
            assertEquals(a.archetype, b.archetype)
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
        is Command.SpawnNode -> {
            require(b is Command.SpawnNode)
            assertEquals(a.tick, b.tick)
            assertEquals(a.kind, b.kind)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
            assertEquals(a.amount, b.amount)
            assertEquals(a.yieldPerTick, b.yieldPerTick)
            assertEquals(a.label, b.label)
            assertEquals(a.labelId, b.labelId)
        }
        is Command.Build -> {
            require(b is Command.Build)
            assertEquals(a.tick, b.tick)
            assertEquals(a.faction, b.faction)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.tileX, b.tileX)
            assertEquals(a.tileY, b.tileY)
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            assertEquals(a.hp, b.hp)
            assertEquals(a.armor, b.armor)
            assertEquals(a.mineralCost, b.mineralCost)
            assertEquals(a.gasCost, b.gasCost)
            assertEquals(a.label, b.label)
            assertEquals(a.labelId, b.labelId)
        }
        is Command.Train -> {
            require(b is Command.Train)
            assertEquals(a.tick, b.tick)
            assertEquals(a.buildingId, b.buildingId)
            assertEquals(a.typeId, b.typeId)
            assertEquals(a.buildTicks, b.buildTicks)
            assertEquals(a.mineralCost, b.mineralCost)
            assertEquals(a.gasCost, b.gasCost)
        }
        is Command.CancelTrain -> {
            require(b is Command.CancelTrain)
            assertEquals(a.tick, b.tick)
            assertEquals(a.buildingId, b.buildingId)
        }
        is Command.Research -> {
            require(b is Command.Research)
            assertEquals(a.tick, b.tick)
            assertEquals(a.buildingId, b.buildingId)
            assertEquals(a.techId, b.techId)
            assertEquals(a.buildTicks, b.buildTicks)
            assertEquals(a.mineralCost, b.mineralCost)
            assertEquals(a.gasCost, b.gasCost)
        }
        is Command.Rally -> {
            require(b is Command.Rally)
            assertEquals(a.tick, b.tick)
            assertEquals(a.buildingId, b.buildingId)
            assertEquals(a.x, b.x)
            assertEquals(a.y, b.y)
        }
    }
}
