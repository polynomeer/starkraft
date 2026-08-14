package starkraft.sim.client

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class PlayControlState(
    val paused: Boolean = false,
    val speed: Int = 1
)

internal fun clampPlaySpeed(speed: Int): Int = speed.coerceIn(1, 8)

internal fun renderPlayControlState(state: PlayControlState): String =
    buildString {
        append("paused=")
        append(if (state.paused) "1" else "0")
        append('\n')
        append("speed=")
        append(clampPlaySpeed(state.speed))
        append('\n')
    }

internal fun parsePlayControlState(text: String): PlayControlState {
    var paused = false
    var speed = 1
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val key = trimmed.substringBefore('=')
        val value = trimmed.substringAfter('=', "")
        when (key) {
            "paused" -> paused = value == "1" || value.equals("true", ignoreCase = true)
            "speed" -> speed = clampPlaySpeed(value.toIntOrNull() ?: speed)
        }
    }
    return PlayControlState(paused = paused, speed = speed)
}

internal fun writePlayControl(path: Path, state: PlayControlState) {
    writeTextAtomically(path, renderPlayControlState(state))
}

internal fun writeTextAtomically(path: Path, text: String) {
    val parent = path.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
    val temp =
        if (parent != null) {
            Files.createTempFile(parent, path.fileName.toString(), ".tmp")
        } else {
            Files.createTempFile(path.fileName.toString(), ".tmp")
        }
    try {
        Files.writeString(temp, text)
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
