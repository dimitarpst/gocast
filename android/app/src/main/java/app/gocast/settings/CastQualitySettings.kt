package app.gocast.settings

import kotlinx.serialization.Serializable

/**
 * Every tunable quality knob for screen cast.
 * While casting, changes apply live (RTP/capture), via renegotiation (codec),
 * or a soft media rebuild (encoder-factory flags) — no reshare required.
 * When idle / paused, prefs are used on the next capture start / resume.
 */
@Serializable
data class CastQualitySettings(
    // --- Capture (VirtualDisplay) ---
    /** 1.0 = native display pixels; 0.5 = half on each axis, etc. */
    val resolutionScale: Float = 1.0f,
    /** Target FPS passed into the capturer / RTP maxFramerate. */
    val captureFps: Int = 30,
    /** DPI reported to VirtualDisplay (affects density of captured content). */
    val virtualDisplayDpi: Int = 400,

    // --- WebRTC RTP sender encodings ---
    val maxBitrateBps: Int = 8_000_000,
    val minBitrateBps: Int = 1_000_000,
    /** BWE starting bitrate — WebRTC otherwise ramps up from ~300 kbps. */
    val startBitrateBps: Int = 6_000_000,
    val maxFramerate: Int = 60,
    /** Extra encoder-side downscale (1.0 = none, 2.0 = half resolution). */
    val scaleResolutionDownBy: Double = 1.0,
    val degradationPreference: DegradationPreference = DegradationPreference.MAINTAIN_RESOLUTION,
    val bitratePriority: Double = 1.0,
    val networkPriority: NetworkPriority = NetworkPriority.HIGH,
    /** Codec offered first in SDP — H264/H265 use the hardware encoder like scrcpy. */
    val preferredCodec: VideoCodec = VideoCodec.H264,

    // --- PeerConnectionFactory encoder ---
    val enableIntelVp8Encoder: Boolean = true,
    val enableH264HighProfile: Boolean = true,
) {
    @Serializable
    enum class DegradationPreference {
        DISABLED,
        MAINTAIN_FRAMERATE,
        MAINTAIN_RESOLUTION,
        BALANCED,
    }

    @Serializable
    enum class NetworkPriority {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
    }

    @Serializable
    enum class VideoCodec {
        H264,
        H265,
        VP8,
        VP9,
        AV1,
    }

    companion object {
        val Default = CastQualitySettings()

        /** Full-res 60 fps — prefers motion over sharpness when Wi‑Fi struggles. */
        val Smooth = CastQualitySettings(
            resolutionScale = 1.0f,
            captureFps = 60,
            maxFramerate = 60,
            maxBitrateBps = 12_000_000,
            startBitrateBps = 8_000_000,
            degradationPreference = DegradationPreference.MAINTAIN_FRAMERATE,
        )

        /** Native pixels, never blurs — for reading text / code. */
        val Sharp = CastQualitySettings(
            captureFps = 30,
            maxFramerate = 30,
            maxBitrateBps = 16_000_000,
            startBitrateBps = 8_000_000,
            degradationPreference = DegradationPreference.MAINTAIN_RESOLUTION,
        )

        val ResolutionScaleOptions = listOf(
            1.0f to "Full (100%)",
            0.75f to "75%",
            0.5f to "Half (50%)",
            0.33f to "33%",
            0.25f to "25%",
        )

        val FpsOptions = listOf(15, 24, 30, 60)

        val DpiOptions = listOf(160, 240, 320, 400, 480)

        val MaxBitrateMbpsOptions = listOf(1, 2, 4, 6, 8, 12, 16, 24, 32)

        val MinBitrateMbpsOptions = listOf(0, 1, 2, 4)

        val StartBitrateMbpsOptions = listOf(1, 2, 4, 6, 8, 12)

        val ScaleDownOptions = listOf(
            1.0 to "None",
            1.5 to "1.5× smaller",
            2.0 to "2× smaller",
            3.0 to "3× smaller",
            4.0 to "4× smaller",
        )
    }
}
