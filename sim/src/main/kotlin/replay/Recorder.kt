package starkraft.sim.replay

import starkraft.sim.net.Command

interface Recorder {
    fun onCommand(cmd: Command)
}

class NullRecorder : Recorder {
    override fun onCommand(cmd: Command) {}
}

class ReplayRecorder : Recorder {
    private val commands = ArrayList<Command>(256)

    override fun onCommand(cmd: Command) {
        commands.add(cmd)
    }

    fun snapshot(): List<Command> = commands.toList()

    fun clear() {
        commands.clear()
    }
}
