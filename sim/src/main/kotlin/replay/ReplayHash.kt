package starkraft.sim.replay

import starkraft.sim.net.Command

class ReplayHashRecorder : Recorder {
    private var hash: Long = OFFSET_BASIS

    override fun onCommand(cmd: Command) {
        when (cmd) {
            is Command.Move -> {
                mixInt(1)
                mixInt(cmd.tick)
                mixInt(cmd.units.size)
                for (u in cmd.units) mixInt(u)
                mixInt(java.lang.Float.floatToRawIntBits(cmd.x))
                mixInt(java.lang.Float.floatToRawIntBits(cmd.y))
            }
            is Command.Attack -> {
                mixInt(2)
                mixInt(cmd.tick)
                mixInt(cmd.units.size)
                for (u in cmd.units) mixInt(u)
                mixInt(cmd.target)
            }
        }
    }

    fun value(): Long = hash

    fun reset() {
        hash = OFFSET_BASIS
    }

    private fun mixInt(v: Int) {
        hash = (hash xor v.toLong()) * FNV_PRIME
    }

    companion object {
        private const val OFFSET_BASIS = 1469598103934665603L
        private const val FNV_PRIME = 1099511628211L
    }
}
