package app.gocast.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualityStore
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.SystemPlaybackAudioCapture

class ScreenCastSession(
    private val appContext: Context,
    private val onProjectionStopped: (() -> Unit)? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var quality: CastQualitySettings = CastQualityStore.load(appContext)

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var systemAudio: SystemPlaybackAudioCapture? = null
    private var localPlaybackMute: LocalPlaybackMute? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var projectionData: Intent? = null

    @Volatile
    private var stopping = false

    var videoTrack: VideoTrack? = null
        private set

    var audioTrack: AudioTrack? = null
        private set

    val peerConnectionFactory: PeerConnectionFactory?
        get() = factory

    fun start(projectionData: Intent) {
        if (videoTrack != null || stopping) return
        this.projectionData = projectionData
        quality = CastQualityStore.load(appContext)

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )

        createFactoryAndCapturer(projectionData, existingCapturer = null)
        registerDisplayListener()
    }

    /**
     * Live-apply capture knobs (resolution scale, FPS, DPI) without tearing down
     * MediaProjection or the PeerConnectionFactory.
     */
    fun applyQuality(next: CastQualitySettings) {
        if (stopping || videoTrack == null) return
        quality = next
        val metrics = appContext.resources.displayMetrics
        val (w, h) = scaledSize(metrics.widthPixels, metrics.heightPixels)
        lastWidth = w
        lastHeight = h
        val screenCapturer = capturer as? GocastScreenCapturer ?: return
        try {
            screenCapturer.applyDisplayParams(w, h, next.virtualDisplayDpi)
        } catch (e: Exception) {
            Log.w(TAG, "applyDisplayParams failed", e)
        }
    }

    /**
     * Rebuild PeerConnectionFactory / track / surface helper while keeping the
     * same MediaProjection (no re-consent). Returns false if rebuild is not possible.
     */
    fun rebuildKeepingProjection(next: CastQualitySettings): Boolean {
        if (stopping) return false
        val screenCapturer = capturer as? GocastScreenCapturer ?: return false
        val data = projectionData ?: return false

        quality = next
        try {
            screenCapturer.stopCaptureKeepingProjection()
        } catch (e: Exception) {
            Log.w(TAG, "stopCaptureKeepingProjection failed", e)
            return false
        }

        disposeWebRtcStack(disposeCapturer = false)

        try {
            createFactoryAndCapturer(data, existingCapturer = screenCapturer)
            return videoTrack != null && factory != null
        } catch (e: Exception) {
            Log.e(TAG, "rebuildKeepingProjection failed", e)
            return false
        }
    }

    private fun createFactoryAndCapturer(
        projectionData: Intent,
        existingCapturer: GocastScreenCapturer?,
    ) {
        val egl = EglBase.create()
        eglBase = egl

        val canCaptureAudio = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val playbackCapture = if (canCaptureAudio) {
            SystemPlaybackAudioCapture().also { systemAudio = it }
        } else {
            Log.w(TAG, "RECORD_AUDIO denied — video-only cast")
            // Silent permission denials are the #1 cause of "audio just doesn't
            // work" reports — sideloaded APKs commonly get RECORD_AUDIO grants
            // silently blocked by Android's Restricted Settings. Surface it.
            mainHandler.post {
                Toast.makeText(
                    appContext,
                    "Casting without audio — mic permission not granted. " +
                        "If this app was sideloaded, check Settings > Apps > gocast > " +
                        "(3-dot menu) Allow restricted settings, then re-enable Microphone.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            systemAudio = null
            null
        }

        val adm = if (playbackCapture != null) {
            JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setAudioRecordStateCallback(
                    object : JavaAudioDeviceModule.AudioRecordStateCallback {
                        override fun onWebRtcAudioRecordStart() {
                            val module = audioDeviceModule ?: return
                            playbackCapture.replaceWebRtcMic(module)
                        }

                        override fun onWebRtcAudioRecordStop() = Unit
                    },
                )
                .createAudioDeviceModule()
                .also {
                    // Send-only cast — mute WebRTC playout so a local AudioTrack
                    // doesn't steal hardware volume keys from our MediaSession.
                    it.setSpeakerMute(true)
                    audioDeviceModule = it
                }
        } else {
            audioDeviceModule = null
            null
        }

        val factoryBuilder = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    egl.eglBaseContext,
                    quality.enableIntelVp8Encoder,
                    quality.enableH264HighProfile,
                ),
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(egl.eglBaseContext),
            )
        if (adm != null) {
            factoryBuilder.setAudioDeviceModule(adm)
        }
        val pcFactory = factoryBuilder.createPeerConnectionFactory()
        // ADM is retained by native until we release it in disposeWebRtcStack.
        factory = pcFactory

        val screenCapturer = existingCapturer ?: GocastScreenCapturer(
            projectionData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { onProjectionStopped?.invoke() }
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    mainHandler.post { applyCaptureSize(width, height) }
                }
            },
            virtualDisplayDpi = quality.virtualDisplayDpi,
        )
        capturer = screenCapturer

        val helper = SurfaceTextureHelper.create("GocastCapture", egl.eglBaseContext)
        surfaceHelper = helper

        val source = pcFactory.createVideoSource(true)
        videoSource = source

        if (existingCapturer != null) {
            screenCapturer.rebind(helper, source.capturerObserver)
        } else {
            screenCapturer.initialize(helper, appContext, source.capturerObserver)
        }
        screenCapturer.setVirtualDisplayDpi(quality.virtualDisplayDpi)

        val metrics = appContext.resources.displayMetrics
        val sized = scaledSize(metrics.widthPixels, metrics.heightPixels)
        lastWidth = sized.first
        lastHeight = sized.second
        screenCapturer.startCapture(lastWidth, lastHeight, quality.captureFps)

        val track = pcFactory.createVideoTrack("gocast_screen", source)
        track.setEnabled(true)
        videoTrack = track

        if (playbackCapture != null) {
            val projection = screenCapturer.mediaProjectionOrNull()
            if (projection != null) {
                playbackCapture.bindProjection(projection)
                val aSource = pcFactory.createAudioSource(MediaConstraints())
                audioSource = aSource
                val aTrack = pcFactory.createAudioTrack("gocast_system_audio", aSource)
                aTrack.setEnabled(true)
                audioTrack = aTrack
                // Mute phone speakers; PC volume is the viewer slider only.
                LocalPlaybackMute(appContext).also {
                    localPlaybackMute = it
                    it.start()
                }
            } else {
                Log.w(TAG, "MediaProjection null after startCapture — skipping audio")
                audioTrack = null
                audioSource = null
            }
        } else {
            audioTrack = null
            audioSource = null
        }
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
        val (w, h) = scaledSize(width, height)
        if (w <= 0 || h <= 0) return
        if (w == lastWidth && h == lastHeight) return
        lastWidth = w
        lastHeight = h
        try {
            val screenCapturer = capturer as? GocastScreenCapturer
            if (screenCapturer != null) {
                screenCapturer.applyDisplayParams(w, h, quality.virtualDisplayDpi)
            } else {
                capturer?.changeCaptureFormat(w, h, quality.captureFps)
            }
        } catch (e: Exception) {
            Log.w(TAG, "changeCaptureFormat failed", e)
        }
    }

    private fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = quality.resolutionScale.coerceIn(0.1f, 1.0f)
        val w = ((width * scale).toInt() / 2) * 2
        val h = ((height * scale).toInt() / 2) * 2
        return w to h
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

        disposeWebRtcStack(disposeCapturer = true)
        projectionData = null
    }

    private fun disposeWebRtcStack(disposeCapturer: Boolean) {
        try {
            localPlaybackMute?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "localPlaybackMute stop failed", e)
        }
        localPlaybackMute = null

        try {
            systemAudio?.release()
        } catch (e: Exception) {
            Log.w(TAG, "systemAudio release failed", e)
        }
        systemAudio = null

        try {
            audioTrack?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "audioTrack dispose failed", e)
        }
        audioTrack = null

        try {
            audioSource?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "audioSource dispose failed", e)
        }
        audioSource = null

        if (disposeCapturer) {
            try {
                capturer?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "capturer dispose failed", e)
            }
            capturer = null
        }

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
            audioDeviceModule?.release()
        } catch (e: Exception) {
            Log.w(TAG, "ADM release failed", e)
        }
        audioDeviceModule = null

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
