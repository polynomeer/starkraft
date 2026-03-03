package starkraft.sim.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import starkraft.sim.net.InputJson
import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val clientBridgeJson = Json { ignoreUnknownKeys = true }

internal data class ClientCommandAck(
    val tick: Int,
    val commandType: String,
    val requestId: String? = null,
    val accepted: Boolean,
    val reason: String? = null
)

internal data class ClientStreamState(
    val snapshot: ClientSnapshot? = null,
    val ack: ClientCommandAck? = null
)

internal interface ClientStreamSubscription : Closeable {
    fun poll(): ClientStreamState?
}

internal class FileClientStreamSubscription(path: Path) : ClientStreamSubscription {
    init {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        if (!Files.exists(path)) Files.createFile(path)
    }

    private val file = RandomAccessFile(path.toFile(), "r")

    override fun poll(): ClientStreamState? {
        var latestSnapshot: ClientSnapshot? = null
        var latestAck: ClientCommandAck? = null
        while (true) {
            val line = file.readLine() ?: break
            if (line.isBlank()) continue
            val update = parseClientStreamLine(line) ?: continue
            if (update.snapshot != null) latestSnapshot = update.snapshot
            if (update.ack != null) latestAck = update.ack
        }
        if (latestSnapshot == null && latestAck == null) return null
        return ClientStreamState(snapshot = latestSnapshot, ack = latestAck)
    }

    override fun close() {
        file.close()
    }
}

internal class NdjsonClientInputSink(private val path: Path) {
    private val json = Json { encodeDefaults = true }

    init {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        if (!Files.exists(path)) Files.createFile(path)
    }

    fun append(record: InputJson.InputCommandRecord) {
        appendLine(json.encodeToString(InputJson.InputCommandRecord.serializer(), record))
    }

    fun append(record: InputJson.InputSelectionRecord) {
        appendLine(json.encodeToString(InputJson.InputSelectionRecord.serializer(), record))
    }

    private fun appendLine(line: String) {
        Files.writeString(
            path,
            line + "\n",
            StandardOpenOption.APPEND
        )
    }
}

internal fun parseClientStreamLine(line: String): ClientStreamState? {
    if (line.isBlank()) return null
    val obj = clientBridgeJson.parseToJsonElement(line).jsonObject
    return when (obj["recordType"]?.jsonPrimitive?.content) {
        "snapshot" -> {
            val snapshot = obj["snapshot"] ?: return null
            ClientStreamState(
                snapshot = clientBridgeJson.decodeFromJsonElement(ClientSnapshot.serializer(), snapshot)
            )
        }
        "commandAck" ->
            ClientStreamState(
                ack =
                    ClientCommandAck(
                        tick = obj["tick"]?.jsonPrimitive?.content?.toInt() ?: 0,
                        commandType = obj["commandType"]?.jsonPrimitive?.content ?: "unknown",
                        requestId = obj["requestId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
                        accepted = obj["accepted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                        reason = obj["reason"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                    )
            )
        else -> null
    }
}
