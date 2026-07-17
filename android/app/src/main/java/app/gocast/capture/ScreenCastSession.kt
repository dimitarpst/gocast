package app.gocast.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class ScreenCastSession(
    private val appContext: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null

    @Volatile
    private var stopping = false

    var videoTrack: VideoTrack? = null
        private set

    fun start(projectionData: Intent) {
        if (videoTrack != null || stopping) return

        Log.i(TAG, "Starting screen capturer")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )

        val egl = EglBase.create()
        eglBase = egl

        val pcFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(egl.eglBaseContext, true, true),
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(egl.eglBaseContext),
            )
            .createPeerConnectionFactory()
        factory = pcFactory

        val screenCapturer = ScreenCapturerAndroid(
            projectionData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    mainHandler.post { stop() }
                }
            },
        )
        capturer = screenCapturer

        val helper = SurfaceTextureHelper.create("GocastCapture", egl.eglBaseContext)
        surfaceHelper = helper

        val source = pcFactory.createVideoSource(true)
        videoSource = source

        screenCapturer.initialize(helper, appContext, source.capturerObserver)
        val metrics = appContext.resources.displayMetrics
        Log.i(TAG, "Calling startCapture ${metrics.widthPixels}x${metrics.heightPixels}")
        screenCapturer.startCapture(metrics.widthPixels, metrics.heightPixels, 30)

        val track = pcFactory.createVideoTrack("gocast_screen", source)
        track.setEnabled(true)
        videoTrack = track

        Log.i(TAG, "Screen capturer started ${metrics.widthPixels}x${metrics.heightPixels}")
    }

    fun stop() {
        if (stopping) return
        stopping = true

        try {
            capturer?.stopCapture()
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            Log.w(TAG, "stopCapture failed", e)
        }
        capturer?.dispose()
        capturer = null

        videoTrack?.dispose()
        videoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceHelper?.dispose()
        surfaceHelper = null

        factory?.dispose()
        factory = null

        eglBase?.release()
        eglBase = null

        Log.i(TAG, "Screen capturer stopped")
    }

    companion object {
        private const val TAG = "ScreenCastSession"
    }
}
