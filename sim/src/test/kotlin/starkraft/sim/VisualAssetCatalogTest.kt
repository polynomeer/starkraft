package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.VisibleTileRange
import starkraft.sim.client.parseVisualAssetCatalog
import starkraft.sim.client.shouldRenderTerrainSprites

class VisualAssetCatalogTest {
    @Test
    fun `resolves visual assets by type and archetype`() {
        val catalog =
            parseVisualAssetCatalog(
                """
                {
                  "units": {
                    "Marine": { "asset": "assets/sprites/units/marine.png", "scale": 1.1 }
                  },
                  "unitArchetypes": {
                    "worker": { "asset": "assets/sprites/units/worker.png", "teamTintStrength": 0.2 }
                  },
                  "buildings": {
                    "Depot": { "asset": "assets/sprites/buildings/depot.png" }
                  },
                  "buildingArchetypes": {
                    "tech": { "asset": "assets/sprites/buildings/tech.png", "tint": "#99CCFF" }
                  },
                  "resources": {
                    "gas": { "asset": "assets/sprites/resources/gas.png" }
                  },
                  "terrain": {
                    "base": { "asset": "assets/sprites/terrain/base.png" },
                    "blocked": { "asset": "assets/sprites/terrain/blocked.png" }
                  }
                }
                """.trimIndent()
            )

        assertEquals("assets/sprites/units/marine.png", catalog.entity("Marine", "infantry", isStructure = false)?.asset)
        assertEquals("assets/sprites/units/worker.png", catalog.entity("Worker", "worker", isStructure = false)?.asset)
        assertEquals(0.2f, catalog.entity("Worker", "worker", isStructure = false)?.teamTintStrength)
        assertEquals("assets/sprites/buildings/depot.png", catalog.entity("Depot", "producer", isStructure = true)?.asset)
        assertEquals("assets/sprites/buildings/tech.png", catalog.entity("Academy", "tech", isStructure = true)?.asset)
        assertEquals("assets/sprites/resources/gas.png", catalog.resource("gas")?.asset)
        assertEquals("assets/sprites/terrain/base.png", catalog.terrainBase()?.asset)
        assertEquals("assets/sprites/terrain/blocked.png", catalog.blockedTile()?.asset)
        assertNull(catalog.occupiedTile())
    }

    @Test
    fun `terrain sprites only render in bounded visible ranges`() {
        assertTrue(shouldRenderTerrainSprites(24f, VisibleTileRange(0, 20, 0, 20)))
        assertFalse(shouldRenderTerrainSprites(12f, VisibleTileRange(0, 20, 0, 20)))
        assertFalse(shouldRenderTerrainSprites(24f, VisibleTileRange(0, 40, 0, 40)))
    }
}
