package starkraft.sim.client

internal class ClientAppLoop(
    private val session: ClientSession,
    private val onUpdate: () -> Unit
) {
    fun tick(): Boolean {
        val changed = session.poll()
        if (changed) {
            onUpdate()
        }
        return changed
    }
}
