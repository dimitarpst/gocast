package app.gocast.network

import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: String,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
)
