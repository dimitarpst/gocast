package app.gocast.capture

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import app.gocast.settings.CastQualitySettings.VideoCodec

/**
 * A hardware encoder refuses frames bigger than it supports (the Poco X5's H.264 chip
 * stops at 2176 px tall, its screen is 2400), and WebRTC then silently drops to
 * software VP8. Shrink the frame just enough to fit, keeping the aspect ratio.
 */
object EncoderSizeFit {
    private const val TAG = "EncoderSizeFit"
    /** WebRTC crops hardware-encoded frames to multiples of 16 anyway. */
    private const val ALIGN = 16

    private val capsByMime = mutableMapOf<String, MediaCodecInfo.VideoCapabilities?>()

    fun fit(width: Int, height: Int, codec: VideoCodec): Pair<Int, Int> {
        // H.265 falls back to H.264 when the PC can't play it, so both must fit.
        val mimes = when (codec) {
            VideoCodec.H264 -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
            VideoCodec.H265 -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
            else -> return width to height
        }
        var size = width to height
        for (mime in mimes) {
            val caps = hardwareCaps(mime) ?: continue
            size = fitTo(size.first, size.second, caps)
        }
        if (size != width to height) {
            Log.i(TAG, "${width}x$height is too big for the $codec chip, sending ${size.first}x${size.second}")
        }
        return size
    }

    private fun fitTo(width: Int, height: Int, caps: MediaCodecInfo.VideoCapabilities): Pair<Int, Int> {
        if (caps.isSizeSupported(width, height)) return width to height
        var scale = minOf(
            1.0,
            caps.supportedWidths.upper.toDouble() / width,
            caps.supportedHeights.upper.toDouble() / height,
        )
        // Block-count limits can still say no; step down until the chip accepts it.
        repeat(50) {
            val w = (width * scale).toInt() / ALIGN * ALIGN
            val h = (height * scale).toInt() / ALIGN * ALIGN
            if (caps.isSizeSupported(w, h)) return w to h
            scale *= 0.98
        }
        return width to height
    }

    /** The first hardware encoder for [mime], as WebRTC's hardware factory picks it. */
    @Synchronized
    private fun hardwareCaps(mime: String): MediaCodecInfo.VideoCapabilities? =
        capsByMime.getOrPut(mime) {
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .firstOrNull { info ->
                        info.isEncoder && info.isHardwareAccelerated &&
                            info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                    }
                    ?.getCapabilitiesForType(mime)?.videoCapabilities
            }.getOrNull()
        }
}
