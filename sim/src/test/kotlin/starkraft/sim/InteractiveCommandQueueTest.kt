package starkraft.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.World
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

class InteractiveCommandQueueTest {
    @Test
    fun `queued move command preserves existing interactive orders`() {
        val world = World()
        val workerId = world.spawn(Transform(2f, 2f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.orders[workerId]?.items?.addLast(Order.Move(5f, 5f))

        issue(
            cmd = Command.Move(tick = 1, units = intArrayOf(workerId), x = 8f, y = 8f, queued = true),
            world = world,
            recorder = NullRecorder(),
            requestId = "gdx-1"
        )

        val queue = world.orders[workerId]?.items?.toList() ?: emptyList()
        assertEquals(listOf(Order.Move(5f, 5f), Order.Move(8f, 8f)), queue)
    }

    @Test
    fun `immediate move command still replaces existing interactive orders`() {
        val world = World()
        val workerId = world.spawn(Transform(2f, 2f), UnitTag(1, "Worker"), Health(40, 40), w = null)
        world.orders[workerId]?.items?.addLast(Order.Move(5f, 5f))

        issue(
            cmd = Command.Move(tick = 1, units = intArrayOf(workerId), x = 8f, y = 8f),
            world = world,
            recorder = NullRecorder(),
            requestId = "gdx-1"
        )

        val queue = world.orders[workerId]?.items?.toList() ?: emptyList()
        assertEquals(listOf(Order.Move(8f, 8f)), queue)
    }
}
