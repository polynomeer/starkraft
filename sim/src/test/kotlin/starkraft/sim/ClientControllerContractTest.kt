package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientCommandIds
import starkraft.sim.client.ClientGroundCommandMode
import starkraft.sim.client.ClientIntent
import starkraft.sim.client.ClientSnapshot
import starkraft.sim.client.EntitySnapshot
import starkraft.sim.client.FactionSnapshot
import starkraft.sim.client.ResourceNodeSnapshot
import starkraft.sim.client.buildClientIntent
import java.util.LinkedHashSet

class ClientControllerContractTest {
    @Test
    fun `forced move ignores enemy auto attack and issues move`() {
        val intent =
            buildClientIntent(
                snapshot = contractSnapshot(),
                selectedIds = linkedSetOf(4),
                viewedFaction = 1,
                worldX = 8f,
                worldY = 6f,
                leftClick = false,
                rightClick = true,
                attackMoveModifier = false,
                forcedGroundCommandType = ClientGroundCommandMode.MOVE.commandType,
                additiveSelection = false,
                requestIds = ClientCommandIds("test")
            )

        assertTrue(intent is ClientIntent.Command)
        val record = (intent as ClientIntent.Command).record
        assertEquals("move", record.commandType)
        assertEquals(8f, record.x)
        assertEquals(6f, record.y)
    }

    @Test
    fun `forced patrol ignores resource auto harvest and issues patrol`() {
        val intent =
            buildClientIntent(
                snapshot = contractSnapshot(),
                selectedIds = linkedSetOf(4),
                viewedFaction = 1,
                worldX = 6f,
                worldY = 6f,
                leftClick = false,
                rightClick = true,
                attackMoveModifier = false,
                forcedGroundCommandType = ClientGroundCommandMode.PATROL.commandType,
                additiveSelection = false,
                requestIds = ClientCommandIds("test")
            )

        assertTrue(intent is ClientIntent.Command)
        val record = (intent as ClientIntent.Command).record
        assertEquals("patrol", record.commandType)
        assertEquals(6f, record.x)
        assertEquals(6f, record.y)
    }

    @Test
    fun `forced attack move ignores resource auto harvest and issues attack move`() {
        val intent =
            buildClientIntent(
                snapshot = contractSnapshot(),
                selectedIds = linkedSetOf(4),
                viewedFaction = 1,
                worldX = 6f,
                worldY = 6f,
                leftClick = false,
                rightClick = true,
                attackMoveModifier = false,
                forcedGroundCommandType = ClientGroundCommandMode.ATTACK_MOVE.commandType,
                additiveSelection = false,
                requestIds = ClientCommandIds("test")
            )

        assertTrue(intent is ClientIntent.Command)
        val record = (intent as ClientIntent.Command).record
        assertEquals("attackMove", record.commandType)
        assertEquals(6f, record.x)
        assertEquals(6f, record.y)
    }

    private fun contractSnapshot(): ClientSnapshot =
        ClientSnapshot(
            tick = 7,
            mapId = "demo-map",
            buildVersion = "test-build",
            mapWidth = 32,
            mapHeight = 32,
            factions = listOf(FactionSnapshot(faction = 1, visibleTiles = 8), FactionSnapshot(faction = 2, visibleTiles = 8)),
            entities =
                listOf(
                    EntitySnapshot(id = 4, faction = 1, typeId = "Marine", archetype = "infantry", x = 5f, y = 6f, dir = 0f, hp = 45, maxHp = 45, armor = 0, weaponId = "Gauss"),
                    EntitySnapshot(id = 9, faction = 2, typeId = "Zergling", archetype = "infantry", x = 8f, y = 6f, dir = 0f, hp = 35, maxHp = 35, armor = 0, weaponId = "Claw")
                ),
            resourceNodes =
                listOf(
                    ResourceNodeSnapshot(id = 20, kind = "MineralField", x = 6f, y = 6f, remaining = 250, yieldPerTick = 2)
                )
        )
}
