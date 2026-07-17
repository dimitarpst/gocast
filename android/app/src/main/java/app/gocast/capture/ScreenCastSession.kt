package app.gocast.capture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class ScreenCastSession(
    private val appContext: Context,
    private val onProjectionStopped: (() -> Unit)? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastWidth = 0
    private var lastHeight = 0

    @Volatile
    private var stopping = false

    var videoTrack: VideoTrack? = null
        private set

    val peerConnectionFactory: PeerConnectionFactory?
        get() = factory

    fun start(projectionData: Intent) {
        if (videoTrack != null || stopping) return

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

        val screenCapturer = GocastScreenCapturer(
            projectionData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { onProjectionStopped?.invoke() }
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    mainHandler.post { applyCaptureSize(width, height) }
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
        lastWidth = metrics.widthPixels
        lastHeight = metrics.heightPixels
        screenCapturer.startCapture(lastWidth, lastHeight, 30)

        val track = pcFactory.createVideoTrack("gocast_screen", source)
        track.setEnabled(true)
        videoTrack = track

        registerDisplayListener()
    }

    private fun registerDisplayListener() {
        val dm = appContext.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                mainHandler.post {
                    val metrics = appContext.resources.displayMetrics
                    applyCaptureSize(metrics.widthPixels, metrics.heightPixels)
                }
            }
        }
        displayListener = listener
        dm.registerDisplayListener(listener, mainHandler)
    }

    private fun applyCaptureSize(width: Int, height: Int) {
        if (stopping) return
        val w = width / 2 * 2
        val h = height / 2 * 2
        if (w <= 0 || h <= 0) return
        if (w == lastWidth && h == lastHeight) return
        lastWidth = w
        lastHeight = h
        try {
            capturer?.changeCaptureFormat(w, h, 30)
        } catch (e: Exception) {
            Log.w(TAG, "changeCaptureFormat failed", e)
        }
    }

    fun stop(projectionAlreadyStopped: Boolean = false) {
        if (stopping) return
        stopping = true

        displayListener?.let { listener ->
            appContext.getSystemService(DisplayManager::class.java)
                .unregisterDisplayListener(listener)
        }
        displayListener = null

        if (!projectionAlreadyStopped) {
            try {
                capturer?.stopCapture()
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.w(TAG, "stopCapture failed", e)
            }
        }

        try {
            capturer?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "capturer dispose failed", e)
        }
        capturer = null

        try {
            videoTrack?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "track dispose failed", e)
        }
        videoTrack = null

        try {
            videoSource?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "source dispose failed", e)
        }
        videoSource = null

        try {
            surfaceHelper?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "surfaceHelper dispose failed", e)
        }
        surfaceHelper = null

        try {
            factory?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "factory dispose failed", e)
        }
        factory = null

        try {
            eglBase?.release()
        } catch (e: Exception) {
            Log.w(TAG, "egl release failed", e)
        }
        eglBase = null
    }

    companion object {
        private const val TAG = "ScreenCastSession"
    }
}
