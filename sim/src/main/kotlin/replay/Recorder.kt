package starkraft.sim.replay

import starkraft.sim.net.Command

interface Recorder {
    fun onCommand(cmd: Command)
}

class NullRecorder : Recorder {
    override fun onCommand(cmd: Command) {}
}
