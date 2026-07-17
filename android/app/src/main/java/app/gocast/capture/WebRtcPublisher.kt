package app.gocast.capture

import android.util.Log
import app.gocast.domain.ConnectionState
import app.gocast.network.CastConnection
import app.gocast.network.SignalingBridge
import app.gocast.network.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class WebRtcPublisher(
    private val factory: PeerConnectionFactory,
    private val videoTrack: VideoTrack,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var peerConnection: PeerConnection? = null
    private var collectJob: Job? = null
    private var announceJob: Job? = null
    private var remoteDescriptionSet = false
    private val pendingIce = mutableListOf<IceCandidate>()

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            SignalingBridge.incoming.collect { text ->
                handleSignaling(text)
            }
        }
        // Announce on every (re)connect, not just once — a fire-and-forget send
        // while the socket is still reconnecting would be dropped.
        announceJob?.cancel()
        announceJob = scope.launch {
            CastConnection.state.collect { state ->
                if (state is ConnectionState.Connected) {
                    SignalingBridge.send(
                        json.encodeToString(SignalingMessage(type = "phone-media-ready")),
                    )
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        announceJob?.cancel()
        announceJob = null
        scope.launch {
            disposePeerConnectionAsync()
        }
    }

    private fun handleSignaling(text: String) {
        val msg = try {
            json.decodeFromString<SignalingMessage>(text)
        } catch (_: Exception) {
            return
        }
        when (msg.type) {
            "viewer-ready" -> createOfferForViewer()
            "answer" -> {
                val sdp = msg.sdp ?: return
                val pc = peerConnection ?: return
                pc.setRemoteDescription(
                    object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) = Unit
                        override fun onSetSuccess() {
                            remoteDescriptionSet = true
                            pendingIce.forEach { pc.addIceCandidate(it) }
                            pendingIce.clear()
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "setRemoteDescription create failure: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setRemoteDescription failure: $error")
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, sdp),
                )
            }
            "ice" -> {
                val candidate = msg.candidate ?: return
                val ice = IceCandidate(msg.sdpMid, msg.sdpMLineIndex ?: 0, candidate)
                val pc = peerConnection
                if (pc != null && remoteDescriptionSet) {
                    pc.addIceCandidate(ice)
                } else {
                    pendingIce.add(ice)
                }
            }
        }
    }

    private fun createOfferForViewer() {
        scope.launch {
            disposePeerConnectionAsync()
            remoteDescriptionSet = false
            pendingIce.clear()
            buildAndSendOffer()
        }
    }

    private suspend fun disposePeerConnectionAsync() {
        val pc = peerConnection ?: return
        peerConnection = null
        withContext(Dispatchers.Default) {
            try {
                pc.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "PeerConnection dispose failed", e)
            }
        }
    }

    private fun buildAndSendOffer() {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE connection: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val payload = SignalingMessage(
                    type = "ice",
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                )
                SignalingBridge.send(json.encodeToString(payload))
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?,
            ) = Unit
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
        if (pc == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            return
        }
        peerConnection = pc
        pc.addTrack(videoTrack, listOf("gocast"))
        applyVideoEncoding(pc)

        pc.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    pc.setLocalDescription(
                        object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) = Unit
                            override fun onSetSuccess() {
                                val payload = SignalingMessage(type = "offer", sdp = desc.description)
                                SignalingBridge.send(json.encodeToString(payload))
                            }
                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription create failure: $error")
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription failure: $error")
                            }
                        },
                        desc,
                    )
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "createOffer failure: $error")
                }
                override fun onSetFailure(error: String?) = Unit
            },
            MediaConstraints(),
        )
    }

    private fun applyVideoEncoding(pc: PeerConnection) {
        val sender = pc.senders.firstOrNull { it.track()?.id() == videoTrack.id() } ?: return
        val params = sender.parameters
        params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
        for (encoding in params.encodings) {
            encoding.maxBitrateBps = 4_000_000
            encoding.minBitrateBps = 1_000_000
            encoding.maxFramerate = 30
        }
        sender.parameters = params
    }

    companion object {
        private const val TAG = "WebRtcPublisher"
    }
}
