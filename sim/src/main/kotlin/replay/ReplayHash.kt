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
            is Command.MoveArchetype -> {
                mixInt(13)
                mixInt(cmd.tick)
                for (ch in cmd.archetype) mixInt(ch.code)
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
            is Command.AttackArchetype -> {
                mixInt(23)
                mixInt(cmd.tick)
                for (ch in cmd.archetype) mixInt(ch.code)
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
            is Command.Build -> {
                mixInt(4)
                mixInt(cmd.tick)
                mixInt(cmd.faction)
                for (ch in cmd.typeId) mixInt(ch.code)
                mixInt(cmd.tileX)
                mixInt(cmd.tileY)
                mixInt(cmd.width)
                mixInt(cmd.height)
                mixInt(cmd.hp)
                mixInt(cmd.armor)
                mixInt(cmd.mineralCost)
                mixInt(cmd.gasCost)
                if (cmd.label != null) {
                    for (ch in cmd.label) mixInt(ch.code)
                } else {
                    mixInt(0)
                }
                mixInt(cmd.labelId ?: 0)
            }
            is Command.Train -> {
                mixInt(5)
                mixInt(cmd.tick)
                mixInt(cmd.buildingId)
                for (ch in cmd.typeId) mixInt(ch.code)
                mixInt(cmd.buildTicks)
                mixInt(cmd.mineralCost)
                mixInt(cmd.gasCost)
            }
            is Command.CancelTrain -> {
                mixInt(6)
                mixInt(cmd.tick)
                mixInt(cmd.buildingId)
            }
            is Command.Rally -> {
                mixInt(7)
                mixInt(cmd.tick)
                mixInt(cmd.buildingId)
                mixInt(java.lang.Float.floatToRawIntBits(cmd.x))
                mixInt(java.lang.Float.floatToRawIntBits(cmd.y))
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
