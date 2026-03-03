package starkraft.sim.client

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import kotlin.concurrent.thread

internal class ClientWebSocketBridge(
    private val snapshotPath: Path,
    private val inputPath: Path,
    snapshotPort: Int,
    inputPort: Int,
    private val pollMs: Long = 25L
) : Closeable {
    private val snapshotServer = ServerSocket(snapshotPort)
    private val inputServer = ServerSocket(inputPort)
    private val snapshotClients = Collections.synchronizedList(mutableListOf<Socket>())
    @Volatile private var running = true
    private val threads = ArrayList<Thread>(3)

    fun start() {
        ensureFile(snapshotPath)
        ensureFile(inputPath)
        threads += thread(start = true, isDaemon = true, name = "ws-snapshot-accept") { acceptSnapshotClients() }
        threads += thread(start = true, isDaemon = true, name = "ws-snapshot-tail") { tailSnapshots() }
        threads += thread(start = true, isDaemon = true, name = "ws-input-accept") { acceptInputClients() }
    }

    override fun close() {
        running = false
        snapshotServer.close()
        inputServer.close()
        synchronized(snapshotClients) {
            for (client in snapshotClients) {
                runCatching { client.close() }
            }
            snapshotClients.clear()
        }
        for (worker in threads) {
            worker.join(200)
        }
    }

    private fun acceptSnapshotClients() {
        while (running) {
            val socket = runCatching { snapshotServer.accept() }.getOrNull() ?: break
            if (performHandshake(socket)) {
                snapshotClients += socket
            } else {
                runCatching { socket.close() }
            }
        }
    }

    private fun tailSnapshots() {
        RandomAccessFile(snapshotPath.toFile(), "r").use { file ->
            while (running) {
                var line = file.readLine()
                if (line == null) {
                    Thread.sleep(pollMs)
                    continue
                }
                while (line != null) {
                    if (line.isNotBlank()) {
                        broadcastSnapshotLine(line)
                    }
                    line = file.readLine()
                }
            }
        }
    }

    private fun acceptInputClients() {
        while (running) {
            val socket = runCatching { inputServer.accept() }.getOrNull() ?: break
            thread(start = true, isDaemon = true, name = "ws-input-client") {
                socket.use { client ->
                    if (!performHandshake(client)) return@thread
                    val input = BufferedInputStream(client.getInputStream())
                    while (running) {
                        val line = readTextFrame(input) ?: break
                        if (line.isBlank()) continue
                        Files.writeString(inputPath, line + "\n", StandardOpenOption.APPEND)
                    }
                }
            }
        }
    }

    private fun performHandshake(socket: Socket): Boolean {
        return runCatching {
            val input = socket.getInputStream().bufferedReader()
            var line = input.readLine()
            var key: String? = null
            while (line != null && line.isNotEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                    key = line.substringAfter(':').trim()
                }
                line = input.readLine()
            }
            val accept = websocketAccept(key ?: return false)
            val response =
                "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
            socket.getOutputStream().flush()
            true
        }.getOrDefault(false)
    }

    private fun broadcastSnapshotLine(line: String) {
        synchronized(snapshotClients) {
            val iterator = snapshotClients.iterator()
            while (iterator.hasNext()) {
                val socket = iterator.next()
                val success =
                    runCatching {
                        writeTextFrame(socket.getOutputStream(), line)
                    }.isSuccess
                if (!success) {
                    runCatching { socket.close() }
                    iterator.remove()
                }
            }
        }
    }

    private fun ensureFile(path: Path) {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        if (!Files.exists(path)) Files.createFile(path)
    }
}

private fun websocketAccept(key: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val bytes = digest.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.UTF_8))
    return Base64.getEncoder().encodeToString(bytes)
}

private fun writeTextFrame(output: OutputStream, text: String) {
    val payload = text.toByteArray(StandardCharsets.UTF_8)
    output.write(0x81)
    when {
        payload.size <= 125 -> output.write(payload.size)
        payload.size <= 0xFFFF -> {
            output.write(126)
            output.write(payload.size ushr 8)
            output.write(payload.size and 0xFF)
        }
        else -> error("payload too large")
    }
    output.write(payload)
    output.flush()
}

private fun readTextFrame(input: InputStream): String? {
    val b1 = input.read()
    if (b1 < 0) return null
    val b2 = input.read()
    if (b2 < 0) return null
    val opcode = b1 and 0x0F
    if (opcode == 0x8) return null
    val masked = (b2 and 0x80) != 0
    var length = b2 and 0x7F
    if (length == 126) {
        length = (input.read() shl 8) or input.read()
    } else if (length == 127) {
        error("unsupported websocket frame length")
    }
    val mask =
        if (masked) {
            byteArrayOf(input.read().toByte(), input.read().toByte(), input.read().toByte(), input.read().toByte())
        } else {
            byteArrayOf()
        }
    val payload = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = input.read(payload, offset, length - offset)
        if (read < 0) return null
        offset += read
    }
    if (masked) {
        for (i in payload.indices) {
            payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
    }
    return String(payload, StandardCharsets.UTF_8)
}

fun main(args: Array<String>) {
    require(args.size == 4) { "usage: ClientWebSocketBridgeKt <snapshot.ndjson> <input.ndjson> <snapshotPort> <inputPort>" }
    val snapshotPath = Path.of(args[0]).toAbsolutePath().normalize()
    val inputPath = Path.of(args[1]).toAbsolutePath().normalize()
    val snapshotPort = args[2].toInt()
    val inputPort = args[3].toInt()

    ClientWebSocketBridge(snapshotPath, inputPath, snapshotPort, inputPort).use { bridge ->
        bridge.start()
        while (true) {
            Thread.sleep(1_000L)
        }
    }
}
