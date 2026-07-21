package app.gocast.capture

import android.util.Log
import app.gocast.domain.ConnectionState
import app.gocast.network.CastConnection
import app.gocast.network.SignalingBridge
import app.gocast.network.SignalingMessage
import app.gocast.settings.CastQualitySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
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
    quality: CastQualitySettings,
    private val audioTrack: AudioTrack? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var quality: CastQualitySettings = quality
    private var peerConnection: PeerConnection? = null
    private var collectJob: Job? = null
    private var announceJob: Job? = null
    private var remoteDescriptionSet = false
    private val pendingIce = mutableListOf<IceCandidate>()

    /**
     * Update quality on the live publisher.
     * @param renegotiate if true, dispose the PeerConnection and send a fresh offer
     *   (needed for codec preference changes).
     */
    fun applyQuality(next: CastQualitySettings, renegotiate: Boolean) {
        quality = next
        if (renegotiate) {
            createOfferForViewer()
            return
        }
        val pc = peerConnection ?: return
        applyVideoEncoding(pc)
        pc.setBitrate(
            quality.minBitrateBps.takeIf { it > 0 },
            quality.startBitrateBps,
            quality.maxBitrateBps,
        )
    }

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
        audioTrack?.let { pc.addTrack(it, listOf("gocast")) }
        applyCodecPreference(pc)
        applyVideoEncoding(pc)
        applyAudioEncoding(pc)
        // BWE start/min/max — without this WebRTC ramps up from ~300 kbps and
        // the first seconds (or longer on stable links) look far worse than scrcpy.
        pc.setBitrate(
            quality.minBitrateBps.takeIf { it > 0 },
            quality.startBitrateBps,
            quality.maxBitrateBps,
        )

        pc.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    val offer = SessionDescription(
                        desc.type,
                        preferHighQualityOpus(desc.description),
                    )
                    pc.setLocalDescription(
                        object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) = Unit
                            override fun onSetSuccess() {
                                val payload = SignalingMessage(type = "offer", sdp = offer.description)
                                SignalingBridge.send(json.encodeToString(payload))
                            }
                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription create failure: $error")
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription failure: $error")
                            }
                        },
                        offer,
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

    /** Put the preferred codec first in the offer so the hardware encoder is used. */
    private fun applyCodecPreference(pc: PeerConnection) {
        val wanted = quality.preferredCodec.name // matches SDP names: H264, H265, VP8, VP9, AV1
        try {
            val capabilities = factory.getRtpSenderCapabilities(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            )
            val codecs = capabilities.codecs
            if (codecs.none { it.name.equals(wanted, ignoreCase = true) }) {
                Log.w(TAG, "Codec $wanted not in sender capabilities — keeping defaults")
                return
            }
            val ordered = codecs.sortedByDescending { it.name.equals(wanted, ignoreCase = true) }
            val transceiver = pc.transceivers.firstOrNull {
                it.sender.track()?.id() == videoTrack.id()
            }
            if (transceiver == null) {
                Log.w(TAG, "No video transceiver found for codec preference")
                return
            }
            transceiver.setCodecPreferences(ordered)
        } catch (e: Exception) {
            Log.w(TAG, "setCodecPreferences failed — keeping defaults", e)
        }
    }

    private fun applyVideoEncoding(pc: PeerConnection) {
        val sender = pc.senders.firstOrNull { it.track()?.id() == videoTrack.id() } ?: return
        val params = sender.parameters
        params.degradationPreference = when (quality.degradationPreference) {
            CastQualitySettings.DegradationPreference.DISABLED ->
                RtpParameters.DegradationPreference.DISABLED
            CastQualitySettings.DegradationPreference.MAINTAIN_FRAMERATE ->
                RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
            CastQualitySettings.DegradationPreference.MAINTAIN_RESOLUTION ->
                RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            CastQualitySettings.DegradationPreference.BALANCED ->
                RtpParameters.DegradationPreference.BALANCED
        }
        val networkPriority = when (quality.networkPriority) {
            CastQualitySettings.NetworkPriority.VERY_LOW ->
                org.webrtc.Priority.VERY_LOW
            CastQualitySettings.NetworkPriority.LOW ->
                org.webrtc.Priority.LOW
            CastQualitySettings.NetworkPriority.MEDIUM ->
                org.webrtc.Priority.MEDIUM
            CastQualitySettings.NetworkPriority.HIGH ->
                org.webrtc.Priority.HIGH
        }
        for (encoding in params.encodings) {
            encoding.maxBitrateBps = quality.maxBitrateBps
            encoding.minBitrateBps = quality.minBitrateBps
            encoding.maxFramerate = quality.maxFramerate
            encoding.scaleResolutionDownBy = quality.scaleResolutionDownBy
            encoding.bitratePriority = quality.bitratePriority
            encoding.networkPriority = networkPriority
        }
        sender.parameters = params
    }

    private fun applyAudioEncoding(pc: PeerConnection) {
        if (audioTrack == null) return
        val sender = pc.senders.firstOrNull { it.track()?.id() == audioTrack.id() } ?: return
        val params = sender.parameters
        for (encoding in params.encodings) {
            encoding.maxBitrateBps = OPUS_MAX_AVERAGE_BITRATE_BPS
        }
        try {
            sender.parameters = params
        } catch (e: Exception) {
            Log.w(TAG, "audio encoding params failed", e)
        }
    }

    companion object {
        private const val TAG = "WebRtcPublisher"
        /** Stereo music bitrate — WebRTC voice defaults are far lower. */
        private const val OPUS_MAX_AVERAGE_BITRATE_BPS = 256_000

        /**
         * Ask Opus for stereo + high bitrate. Without this, the offer stays in
         * voice mode and bass/stereo image stay thin even with clean PCM.
         */
        fun preferHighQualityOpus(sdp: String): String {
            val opusPayloadTypes = Regex("""a=rtpmap:(\d+) opus/""", RegexOption.IGNORE_CASE)
                .findAll(sdp)
                .map { it.groupValues[1] }
                .toSet()
            if (opusPayloadTypes.isEmpty()) return sdp

            val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
            return sdp.split(eol).joinToString(eol) { line ->
                val fmtp = Regex("""^a=fmtp:(\d+) (.*)$""").matchEntire(line)
                if (fmtp == null || fmtp.groupValues[1] !in opusPayloadTypes) {
                    line
                } else {
                    val pt = fmtp.groupValues[1]
                    var params = fmtp.groupValues[2]
                    params = upsertFmtpParam(params, "stereo", "1")
                    params = upsertFmtpParam(params, "sprop-stereo", "1")
                    params = upsertFmtpParam(
                        params,
                        "maxaveragebitrate",
                        OPUS_MAX_AVERAGE_BITRATE_BPS.toString(),
                    )
                    "a=fmtp:$pt $params"
                }
            }
        }

        private fun upsertFmtpParam(params: String, key: String, value: String): String {
            val parts = params.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            val entry = "$key=$value"
            val idx = parts.indexOfFirst { it.startsWith("$key=") }
            if (idx >= 0) parts[idx] = entry else parts.add(entry)
            return parts.joinToString(";")
        }
    }
}
