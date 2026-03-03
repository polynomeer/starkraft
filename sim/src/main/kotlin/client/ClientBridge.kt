package starkraft.sim.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import starkraft.sim.net.InputJson
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

internal interface ClientInputSink : Closeable {
    fun append(record: InputJson.InputCommandRecord)
    fun append(record: InputJson.InputSelectionRecord)
}

internal open class ReaderClientStreamSubscription(
    private val reader: BufferedReader
) : ClientStreamSubscription {
    override fun poll(): ClientStreamState? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isBlank()) continue
            return parseClientStreamLine(line)
        }
    }

    override fun close() {
        reader.close()
    }
}

internal class StdinClientStreamSubscription : ReaderClientStreamSubscription(
    BufferedReader(InputStreamReader(System.`in`))
)

internal class SocketClientStreamSubscription(
    host: String,
    port: Int,
    timeoutMs: Int = 25
) : ClientStreamSubscription {
    private val socket = Socket(host, port).apply { soTimeout = timeoutMs }
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

    override fun poll(): ClientStreamState? {
        try {
            while (true) {
                val line = reader.readLine() ?: return null
                if (line.isBlank()) continue
                return parseClientStreamLine(line)
            }
        } catch (_: SocketTimeoutException) {
            return null
        }
    }

    override fun close() {
        socket.close()
    }
}

internal class WebSocketClientStreamSubscription(
    uri: String
) : ClientStreamSubscription {
    private val queue = ConcurrentLinkedQueue<ClientStreamState>()
    private val connected = CountDownLatch(1)
    private val webSocket =
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(uri), object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) {
                    connected.countDown()
                    webSocket.request(1)
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
                    if (last) {
                        parseClientStreamLine(data.toString())?.let(queue::add)
                    }
                    webSocket.request(1)
                    return null
                }
            }).join()

    init {
        connected.await(2, TimeUnit.SECONDS)
    }

    override fun poll(): ClientStreamState? = queue.poll()

    override fun close() {
        runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join() }
    }
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

internal class FileClientInputSink(path: Path) : ClientInputSink {
    private val delegate = NdjsonClientInputSink(path)

    override fun append(record: InputJson.InputCommandRecord) = delegate.append(record)

    override fun append(record: InputJson.InputSelectionRecord) = delegate.append(record)

    override fun close() = Unit
}

internal class SocketClientInputSink(
    host: String,
    port: Int
) : ClientInputSink {
    private val json = Json { encodeDefaults = true }
    private val socket = Socket(host, port)
    private val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)

    override fun append(record: InputJson.InputCommandRecord) {
        writer.println(json.encodeToString(InputJson.InputCommandRecord.serializer(), record))
    }

    override fun append(record: InputJson.InputSelectionRecord) {
        writer.println(json.encodeToString(InputJson.InputSelectionRecord.serializer(), record))
    }

    override fun close() {
        socket.close()
    }
}

internal class WebSocketClientInputSink(
    uri: String
) : ClientInputSink {
    private val json = Json { encodeDefaults = true }
    private val connected = CountDownLatch(1)
    private val webSocket =
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(uri), object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) {
                    connected.countDown()
                    webSocket.request(1)
                }
            }).join()

    init {
        connected.await(2, TimeUnit.SECONDS)
    }

    override fun append(record: InputJson.InputCommandRecord) {
        webSocket.sendText(json.encodeToString(InputJson.InputCommandRecord.serializer(), record), true).join()
    }

    override fun append(record: InputJson.InputSelectionRecord) {
        webSocket.sendText(json.encodeToString(InputJson.InputSelectionRecord.serializer(), record), true).join()
    }

    override fun close() {
        runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join() }
    }
}

internal data class ClientSocketEndpoint(val host: String, val port: Int)

internal fun parseClientEndpoint(spec: String): ClientSocketEndpoint? {
    if (!spec.startsWith("tcp://")) return null
    val rest = spec.removePrefix("tcp://")
    val colon = rest.lastIndexOf(':')
    require(colon > 0 && colon < rest.length - 1) { "invalid tcp endpoint '$spec'" }
    return ClientSocketEndpoint(rest.substring(0, colon), rest.substring(colon + 1).toInt())
}

internal fun openClientStreamSubscription(spec: String): ClientStreamSubscription {
    if (spec.startsWith("ws://") || spec.startsWith("wss://")) {
        return WebSocketClientStreamSubscription(spec)
    }
    val endpoint = parseClientEndpoint(spec)
    return if (endpoint != null) {
        SocketClientStreamSubscription(endpoint.host, endpoint.port)
    } else {
        FileClientStreamSubscription(Path.of(spec).toAbsolutePath().normalize())
    }
}

internal fun openClientInputSink(spec: String): ClientInputSink {
    if (spec.startsWith("ws://") || spec.startsWith("wss://")) {
        return WebSocketClientInputSink(spec)
    }
    val endpoint = parseClientEndpoint(spec)
    return if (endpoint != null) {
        SocketClientInputSink(endpoint.host, endpoint.port)
    } else {
        FileClientInputSink(Path.of(spec).toAbsolutePath().normalize())
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
