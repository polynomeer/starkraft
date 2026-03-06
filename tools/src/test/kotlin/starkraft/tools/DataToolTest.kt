package starkraft.tools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataToolTest {
    @Test
    fun `data validator accepts sim data directory`() {
        val dir = resolvePath("sim/src/main/resources/data")
        val result = validateDataDir(dir)
        assertTrue(result.ok, result.errors.joinToString())
    }

    @Test
    fun `data validator rejects missing cross references`() {
        val dir = Files.createTempDirectory("starkraft-data-invalid")
        Files.writeString(
            dir.resolve("buildings.json"),
            """{"list":[{"id":"Depot","supportsDropoff":true,"dropoffResourceKinds":["minerals"]}]}"""
        )
        Files.writeString(
            dir.resolve("weapons.json"),
            """{"list":[{"id":"Gauss"}]}"""
        )
        Files.writeString(
            dir.resolve("techs.json"),
            """{"list":[{"id":"AdvancedTraining","producerTypes":["MissingDepot"]}]}"""
        )
        Files.writeString(
            dir.resolve("units.json"),
            """{"list":[{"id":"Marine","weaponId":"MissingWeapon","producerTypes":["Depot"]}]}"""
        )
        val result = validateDataDir(dir)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("MissingWeapon") })
        assertTrue(result.errors.any { it.contains("MissingDepot") })
    }

    @Test
    fun `data validator accepts yaml files`() {
        val dir = Files.createTempDirectory("starkraft-data-yaml")
        Files.writeString(
            dir.resolve("buildings.yaml"),
            """
            list:
              - id: Depot
                supportsDropoff: true
                dropoffResourceKinds: [minerals]
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("weapons.yaml"),
            """
            list:
              - id: Gauss
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("techs.yaml"),
            """
            list:
              - id: AdvancedTraining
                producerTypes: [Depot]
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("units.yaml"),
            """
            list:
              - id: Marine
                weaponId: Gauss
                producerTypes: [Depot]
            """.trimIndent()
        )
        val result = validateDataDir(dir)
        assertTrue(result.ok, result.errors.joinToString())
    }
}
