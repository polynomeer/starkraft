package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.VisibleTileRange
import starkraft.sim.client.parseVisualAssetCatalog
import starkraft.sim.client.shouldRenderTerrainSprites
import starkraft.sim.client.spriteScreenRect
import starkraft.sim.client.structureOriginTile

class VisualAssetCatalogTest {
    @Test
    fun `places a sprite so its anchor sits on the map point`() {
        val rect = spriteScreenRect(100f, 200f, 20f, anchorX = 0.5f, anchorY = 0.75f, widthTiles = 2f, imageWidth = 100, imageHeight = 200)

        assertEquals(40f, rect.width)
        assertEquals(80f, rect.height)
        assertEquals(80f, rect.left)
        assertEquals(140f, rect.top)
    }

    @Test
    fun `finds a structure's first tile from its centre`() {
        assertEquals(10, structureOriginTile(11f, 2))
        assertEquals(10, structureOriginTile(11.5f, 3))
    }

    @Test
    fun `reads anchor and map size from the manifest`() {
        val catalog =
            parseVisualAssetCatalog(
                """{"units": {"Worker": {"asset": "a.png", "anchorX": 0.5, "anchorY": 0.9, "widthTiles": 1.7}}}"""
            )

        val ref = catalog.entity("Worker", "worker", isStructure = false)
        assertEquals(0.9f, ref?.anchorY)
        assertEquals(1.7f, ref?.widthTiles)
    }

    @Test
    fun `shipped manifest points at sprites that exist`() {
        val payload = checkNotNull(javaClass.getResource("/data/visuals.json")).readText()
        val catalog = parseVisualAssetCatalog(payload)
        val refs =
            listOf("Worker", "Marine", "Zergling").map { catalog.entity(it, null, isStructure = false) } +
                listOf("Depot", "ResourceDepot", "GasDepot").map { catalog.entity(it, null, isStructure = true) } +
                listOf("minerals", "gas").map { catalog.resource(it) }

        for (ref in refs) {
            val asset = checkNotNull(ref).asset
            assertTrue(javaClass.getResource("/$asset") != null, "missing $asset")
        }
    }

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
