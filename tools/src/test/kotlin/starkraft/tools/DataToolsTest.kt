package starkraft.tools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataToolsTest {
    @Test
    fun `validator accepts coherent json data`() {
        val dir = Files.createTempDirectory("starkraft-data-json")
        Files.writeString(
            dir.resolve("weapons.json"),
            """
            {"list":[{"id":"Gauss","damage":6,"range":4.0,"cooldownTicks":10}]}
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("buildings.json"),
            """
            {"list":[{"id":"Depot","hp":400,"footprintWidth":2,"footprintHeight":2,"supportsTraining":true,"supportsResearch":true}]}
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("techs.json"),
            """
            {"list":[{"id":"AdvancedTraining","buildTicks":100,"producerTypes":["Depot"]}]}
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("units.json"),
            """
            {"list":[{"id":"Marine","hp":45,"weaponId":"Gauss","buildTicks":20,"producerTypes":["Depot"],"requiredResearchIds":["AdvancedTraining"]}]}
            """.trimIndent()
        )

        val result =
            validateGameData(
                DataValidationInput(
                    unitsPath = dir.resolve("units.json"),
                    weaponsPath = dir.resolve("weapons.json"),
                    buildingsPath = dir.resolve("buildings.json"),
                    techsPath = dir.resolve("techs.json")
                )
            )

        assertTrue(result.valid)
    }

    @Test
    fun `validator catches missing references in yaml`() {
        val dir = Files.createTempDirectory("starkraft-data-yaml")
        Files.writeString(
            dir.resolve("weapons.yaml"),
            """
            list:
              - id: Gauss
                damage: 6
                range: 4.0
                cooldownTicks: 10
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("buildings.yaml"),
            """
            list:
              - id: Depot
                hp: 400
                footprintWidth: 2
                footprintHeight: 2
                supportsTraining: false
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("techs.yaml"),
            """
            list:
              - id: AdvancedTraining
                buildTicks: 120
                producerTypes: [Lab]
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("units.yaml"),
            """
            list:
              - id: Marine
                hp: 45
                weaponId: MissingWeapon
                buildTicks: 20
                producerTypes: [Depot]
                requiredResearchIds: [AdvancedTraining]
            """.trimIndent()
        )

        val result =
            validateGameData(
                DataValidationInput(
                    unitsPath = dir.resolve("units.yaml"),
                    weaponsPath = dir.resolve("weapons.yaml"),
                    buildingsPath = dir.resolve("buildings.yaml"),
                    techsPath = dir.resolve("techs.yaml")
                )
            )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("missing weapon") })
        assertTrue(result.errors.any { it.contains("does not support training") })
        assertTrue(result.errors.any { it.contains("producer building 'Lab' is missing") })
    }
}
