package app.gocast.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Messages between the apps and the signaling Worker (`cloud/signal`).
 * Existing GoCast messages (hello, offer, answer, ice, …) pass through it unchanged;
 * only the control messages below are added.
 */
object RemoteSignaling {
    /** The one place the signaling address lives. */
    const val BASE_URL = "wss://signal.govrse.app"

    const val ROLE_PC = "pc"
    const val ROLE_PHONE = "phone"

    // Exact strings: the Worker answers PING without waking the room.
    const val PING = """{"type":"ping"}"""
    const val PONG = """{"type":"pong"}"""
    const val GET_ICE_SERVERS = """{"type":"get-ice-servers"}"""
    const val GET_RELAY_STATUS = """{"type":"get-relay-status"}"""

    const val TYPE_PHONE_JOINED = "phone-joined"
    const val TYPE_PHONE_LEFT = "phone-left"
    const val TYPE_PHONE_ACCEPTED = "phone-accepted"
    const val TYPE_PHONE_REJECTED = "phone-rejected"
    const val TYPE_ICE_SERVERS = "ice-servers"
    const val TYPE_RELAY_STATUS = "relay-status"
    const val TYPE_RELAY_UNLOCK = "relay-unlock"

    /** Worker close codes. */
    const val CLOSE_PC_REPLACED: Short = 4000
    const val CLOSE_PHONE_REPLACED: Short = 4001
    const val CLOSE_OTHER_PHONE_ACCEPTED: Short = 4002
    const val CLOSE_REJECTED: Short = 4003
    const val CLOSE_PC_OFFLINE: Short = 4004

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun roomUrl(roomId: String, role: String, baseUrl: String = BASE_URL): String =
        "$baseUrl/room/$roomId?role=$role"

    fun phoneAccepted(phoneId: String): String =
        json.encodeToString(PhoneDecision.serializer(), PhoneDecision(TYPE_PHONE_ACCEPTED, phoneId))

    fun phoneRejected(phoneId: String): String =
        json.encodeToString(PhoneDecision.serializer(), PhoneDecision(TYPE_PHONE_REJECTED, phoneId))

    /** PC only: the owner's relay password, checked by the Worker for this room. */
    fun relayUnlock(password: String): String =
        json.encodeToString(RelayUnlock.serializer(), RelayUnlock(TYPE_RELAY_UNLOCK, password))

    /** RTCIceServer-shaped JSON for the browser viewer. */
    fun encodeIceServers(servers: List<IceServer>): String =
        json.encodeToString(ListSerializer(IceServer.serializer()), servers)

    /** Worker control messages; null for anything else (including normal GoCast messages). */
    fun parseControl(text: String): RemoteControl? {
        if (text == PONG) return RemoteControl.Pong
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val type = runCatching { obj["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        return runCatching {
            when (type) {
                TYPE_PHONE_JOINED -> RemoteControl.PhoneJoined(phoneId(obj) ?: return null)
                TYPE_PHONE_LEFT -> RemoteControl.PhoneLeft(phoneId(obj) ?: return null)
                TYPE_ICE_SERVERS ->
                    RemoteControl.IceServers(json.decodeFromJsonElement<IceServersMessage>(obj).iceServers)
                TYPE_RELAY_STATUS -> json.decodeFromJsonElement<RelayStatusMessage>(obj).let {
                    RemoteControl.RelayStatus(RelayState.parse(it.relay), RelayError.parse(it.error))
                }
                "pong" -> RemoteControl.Pong
                else -> null
            }
        }.getOrNull()
    }

    private fun phoneId(obj: JsonObject): String? =
        obj["phoneId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
}

sealed interface RemoteControl {
    data class PhoneJoined(val phoneId: String) : RemoteControl
    data class PhoneLeft(val phoneId: String) : RemoteControl
    data class IceServers(val servers: List<IceServer>) : RemoteControl
    data class RelayStatus(val state: RelayState, val error: RelayError? = null) : RemoteControl
    data object Pong : RemoteControl
}

/** Whether this PC's room may use the TURN relay. */
enum class RelayState {
    /** Unlocked (or allowlisted) and the relay is switched on. */
    On,
    /** The owner's password would unlock it. */
    Locked,
    /** Switched off on the Worker, or no password configured. */
    Off,
    ;

    companion object {
        fun parse(value: String?): RelayState = when (value) {
            "on" -> On
            "locked" -> Locked
            else -> Off
        }
    }
}

enum class RelayError {
    WrongPassword,
    TooManyAttempts,
    ;

    companion object {
        fun parse(value: String?): RelayError? = when (value) {
            "wrong-password" -> WrongPassword
            "too-many-attempts" -> TooManyAttempts
            else -> null
        }
    }
}

@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
) {
    companion object {
        /** Free Cloudflare STUN; used when the Worker can't be reached. */
        val STUN_ONLY: List<IceServer> = listOf(IceServer(listOf("stun:stun.cloudflare.com:3478")))
    }
}

@Serializable
private data class PhoneDecision(val type: String, val phoneId: String)

@Serializable
private data class IceServersMessage(val type: String, val iceServers: List<IceServer> = emptyList())

@Serializable
private data class RelayUnlock(val type: String, val password: String)

@Serializable
private data class RelayStatusMessage(val type: String, val relay: String? = null, val error: String? = null)
