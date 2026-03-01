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
            is Command.MoveFaction -> {
                mixInt(11)
                mixInt(cmd.tick)
                mixInt(cmd.faction)
                mixInt(java.lang.Float.floatToRawIntBits(cmd.x))
                mixInt(java.lang.Float.floatToRawIntBits(cmd.y))
            }
            is Command.MoveType -> {
                mixInt(12)
                mixInt(cmd.tick)
                for (ch in cmd.typeId) mixInt(ch.code)
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
            is Command.AttackFaction -> {
                mixInt(21)
                mixInt(cmd.tick)
                mixInt(cmd.faction)
                mixInt(cmd.target)
            }
            is Command.AttackType -> {
                mixInt(22)
                mixInt(cmd.tick)
                for (ch in cmd.typeId) mixInt(ch.code)
                mixInt(cmd.target)
            }
            is Command.Spawn -> {
                mixInt(3)
                mixInt(cmd.tick)
                mixInt(cmd.faction)
                for (ch in cmd.typeId) mixInt(ch.code)
                mixInt(java.lang.Float.floatToRawIntBits(cmd.x))
                mixInt(java.lang.Float.floatToRawIntBits(cmd.y))
                mixInt(java.lang.Float.floatToRawIntBits(cmd.vision ?: -1f))
                if (cmd.label != null) {
                    for (ch in cmd.label) mixInt(ch.code)
                } else {
                    mixInt(0)
                }
                mixInt(cmd.labelId ?: 0)
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
