package starkraft.sim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import starkraft.sim.client.ClientVisionChange
import starkraft.sim.client.applyVisionChanges

class ClientVisionStateTest {
    @Test
    fun `explored tiles persist after vision is lost`() {
        val visible =
            applyVisionChanges(
                current = null,
                changes = listOf(ClientVisionChange(faction = 1, x = 4, y = 7, visible = true))
            )
        val hidden =
            applyVisionChanges(
                current = visible,
                changes = listOf(ClientVisionChange(faction = 1, x = 4, y = 7, visible = false))
            )

        assertFalse((4 to 7) in hidden.visibleTiles(1))
        assertTrue((4 to 7) in hidden.exploredTiles(1))
    }
}
