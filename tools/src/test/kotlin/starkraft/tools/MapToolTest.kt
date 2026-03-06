package starkraft.tools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MapToolTest {
    @Test
    fun `map validator accepts valid payload`() {
        val path = Files.createTempFile("starkraft-map-valid", ".json")
        Files.writeString(
            path,
            """
            {
              "schema": 1,
              "id": "demo",
              "width": 8,
              "height": 8,
              "blockedTiles": [{"x":1,"y":1}],
              "weightedTiles": [{"x":2,"y":2,"cost":1.5}],
              "resources": [{"kind":"MineralField","x":3,"y":3,"amount":100,"yieldPerTick":2}],
              "spawns": [{"faction":1,"x":0,"y":0}]
            }
            """.trimIndent()
        )

        val result = validateMap(path)
        assertTrue(result.ok, result.errors.joinToString())
    }

    @Test
    fun `map validator rejects out of bounds tiles`() {
        val path = Files.createTempFile("starkraft-map-invalid", ".json")
        Files.writeString(
            path,
            """
            {
              "schema": 1,
              "id": "demo",
              "width": 4,
              "height": 4,
              "blockedTiles": [{"x":9,"y":9}]
            }
            """.trimIndent()
        )

        val result = validateMap(path)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("out of bounds") })
    }
}
