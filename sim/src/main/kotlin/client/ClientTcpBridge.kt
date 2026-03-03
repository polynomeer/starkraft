package starkraft.sim.client

import java.io.Closeable
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections
import kotlin.concurrent.thread

internal class ClientTcpBridge(
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
        threads += thread(start = true, isDaemon = true, name = "snapshot-accept") { acceptSnapshotClients() }
        threads += thread(start = true, isDaemon = true, name = "snapshot-tail") { tailSnapshots() }
        threads += thread(start = true, isDaemon = true, name = "input-accept") { acceptInputClients() }
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
            snapshotClients += socket
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
            thread(start = true, isDaemon = true, name = "input-client") {
                socket.use { client ->
                    client.getInputStream().bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            Files.writeString(inputPath, line + "\n", StandardOpenOption.APPEND)
                        }
                    }
                }
            }
        }
    }

    private fun broadcastSnapshotLine(line: String) {
        val payload = (line + "\n").toByteArray()
        synchronized(snapshotClients) {
            val iterator = snapshotClients.iterator()
            while (iterator.hasNext()) {
                val socket = iterator.next()
                val success =
                    runCatching {
                        socket.getOutputStream().write(payload)
                        socket.getOutputStream().flush()
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

fun main(args: Array<String>) {
    require(args.size == 4) { "usage: ClientTcpBridgeKt <snapshot.ndjson> <input.ndjson> <snapshotPort> <inputPort>" }
    val snapshotPath = Path.of(args[0]).toAbsolutePath().normalize()
    val inputPath = Path.of(args[1]).toAbsolutePath().normalize()
    val snapshotPort = args[2].toInt()
    val inputPort = args[3].toInt()

    ClientTcpBridge(snapshotPath, inputPath, snapshotPort, inputPort).use { bridge ->
        bridge.start()
        while (true) {
            Thread.sleep(1_000L)
        }
    }
}
