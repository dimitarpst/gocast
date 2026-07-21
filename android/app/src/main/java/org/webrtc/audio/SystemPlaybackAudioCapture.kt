package org.webrtc.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.concurrent.atomic.AtomicReference

/**
 * Captures system / other-app playback via [AudioPlaybackCaptureConfiguration]
 * on the same [MediaProjection] as screen capture.
 *
 * Lives in [org.webrtc.audio] so it can touch package-private [WebRtcAudioRecord].
 * Stock stream-webrtc 1.3.10 never wires [AudioRecordDataCallback] into the
 * record loop, so we replace WebRTC's mic [AudioRecord] with a playback-capture
 * one when recording starts. Apps that opt out of capture / DRM stay silent —
 * platform limit, not a gocast bug.
 */
class SystemPlaybackAudioCapture {

    private val projectionRef = AtomicReference<MediaProjection?>(null)
    private val ourRecordRef = AtomicReference<AudioRecord?>(null)

    fun bindProjection(projection: MediaProjection) {
        projectionRef.set(projection)
    }

    /**
     * Called on WebRTC's audio record thread from
     * [JavaAudioDeviceModule.AudioRecordStateCallback.onWebRtcAudioRecordStart]
     * — swaps the mic [AudioRecord] for playback capture before the read loop.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun replaceWebRtcMic(adm: JavaAudioDeviceModule) {
        replaceWebRtcMic(adm.audioInput)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun replaceWebRtcMic(webrtcRecord: WebRtcAudioRecord) {
        val projection = projectionRef.get()
        if (projection == null) {
            Log.w(TAG, "No MediaProjection — leaving WebRTC mic AudioRecord")
            return
        }

        val audioRecordField = try {
            WebRtcAudioRecord::class.java.getDeclaredField("audioRecord").also {
                it.isAccessible = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot access WebRtcAudioRecord.audioRecord", e)
            return
        }

        val oldRecord = audioRecordField.get(webrtcRecord) as? AudioRecord
        if (oldRecord == null) {
            Log.w(TAG, "WebRTC AudioRecord is null at start")
            return
        }

        val sampleRate = oldRecord.sampleRate
        val channelCount = oldRecord.channelCount
        val encoding = oldRecord.audioFormat
        val channelMask = if (channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        val playbackRecord = try {
            buildPlaybackRecord(projection, sampleRate, channelMask, encoding)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build playback AudioRecord", e)
            return
        }

        try {
            playbackRecord.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "playback AudioRecord.startRecording failed", e)
            playbackRecord.release()
            return
        }
        if (playbackRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "playback AudioRecord not recording: ${playbackRecord.recordingState}")
            playbackRecord.release()
            return
        }

        audioRecordField.set(webrtcRecord, playbackRecord)
        ourRecordRef.set(playbackRecord)

        try {
            oldRecord.stop()
        } catch (_: Exception) {
        }
        try {
            oldRecord.release()
        } catch (_: Exception) {
        }

        Log.i(
            TAG,
            "Swapped WebRTC mic for playback capture " +
                "(rate=$sampleRate channels=$channelCount encoding=$encoding)",
        )
    }

    fun release() {
        projectionRef.set(null)
        val ours = ourRecordRef.getAndSet(null) ?: return
        try {
            ours.stop()
        } catch (_: Exception) {
        }
        try {
            ours.release()
        } catch (_: Exception) {
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun buildPlaybackRecord(
        projection: MediaProjection,
        sampleRate: Int,
        channelMask: Int,
        encoding: Int,
    ): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("getMinBufferSize failed: $minBuffer")
        }
        val bufferSize = minBuffer * 2

        return AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()
    }

    companion object {
        private const val TAG = "SystemPlaybackAudio"
    }
}
