package starkraft.sim.ecs

typealias EntityId = Int

/** Very small ID generator (demo only) */
object Ids {
    private var next = 1;
    fun newId(): EntityId = next++
}
