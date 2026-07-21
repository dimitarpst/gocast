package app.gocast.capture

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Mute phone speakers while casting (RemotePhone / typical Play Store mirror apps).
 *
 * Public [android.media.AudioPlaybackCaptureConfiguration] always keeps rendering
 * to the device, so we zero [AudioManager.STREAM_MUSIC] for the cast and restore
 * afterward. PC volume is controlled only by the desktop viewer slider — phone
 * volume keys stay local (and will just bump a muted stream).
 */
class LocalPlaybackMute(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private var savedVolume = 0
    private var active = false

    fun start() {
        if (active) return
        val am = audioManager ?: return
        active = true
        savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            Log.i(TAG, "Phone speakers muted for cast (was $savedVolume)")
        } catch (e: Exception) {
            Log.w(TAG, "Mute STREAM_MUSIC failed", e)
        }
    }

    fun stop() {
        if (!active) return
        active = false
        val am = audioManager ?: return
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            Log.i(TAG, "Phone speaker volume restored to $savedVolume")
        } catch (e: Exception) {
            Log.w(TAG, "Restore STREAM_MUSIC failed", e)
        }
    }

    companion object {
        private const val TAG = "LocalPlaybackMute"
    }
}
