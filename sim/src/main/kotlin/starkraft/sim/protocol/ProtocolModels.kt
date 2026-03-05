package starkraft.sim.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val CURRENT_PROTOCOL_VERSION: Int = 1

@Serializable
data class ProtocolEnvelope(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val simVersion: String,
    val buildHash: String? = null,
    val message: ProtocolMessage
)

@Serializable
sealed interface ProtocolMessage {
    @Serializable
    @SerialName("handshake")
    data class Handshake(
        val clientName: String,
        val requestedRoom: String? = null
    ) : ProtocolMessage

    @Serializable
    @SerialName("commandBatch")
    data class CommandBatch(
        val tick: Int,
        val commands: List<WireCommand>
    ) : ProtocolMessage

    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val tick: Int,
        val worldHash: Long,
        val payload: JsonElement? = null
    ) : ProtocolMessage
}

@Serializable
data class WireCommand(
    val commandType: String,
    val requestId: String? = null,
    val payload: JsonElement? = null
)

enum class ProtocolCompatibility {
    COMPATIBLE,
    UPGRADE_SERVER,
    UPGRADE_CLIENT
}

fun protocolCompatibility(localVersion: Int, remoteVersion: Int): ProtocolCompatibility =
    when {
        remoteVersion == localVersion -> ProtocolCompatibility.COMPATIBLE
        remoteVersion > localVersion -> ProtocolCompatibility.UPGRADE_CLIENT
        else -> ProtocolCompatibility.UPGRADE_SERVER
    }
