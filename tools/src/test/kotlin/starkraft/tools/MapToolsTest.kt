package starkraft.tools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapToolsTest {
    @Test
    fun `generated map validates`() {
        val map = generateToolMap(width = 32, height = 32, seed = 42L, blockedPercent = 5, weightedPercent = 8)

        val result = validateToolMap(map)

        assertTrue(result.valid)
    }

    @Test
    fun `validator rejects out-of-bounds tiles`() {
        val map =
            ToolMapFile(
                width = 16,
                height = 16,
                blockedTiles = listOf(ToolTile(20, 1))
            )

        val result = validateToolMap(map)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("out of bounds") })
    }
}
