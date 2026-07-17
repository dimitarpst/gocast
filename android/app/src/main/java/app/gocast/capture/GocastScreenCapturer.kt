package app.gocast.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Screen capturer based on WebRTC's ScreenCapturerAndroid, but uses
 * VirtualDisplay.resize() on format changes instead of releasing + recreating
 * (which throws SecurityException on Android 14+).
 */
class GocastScreenCapturer(
    private val mediaProjectionPermissionResultData: Intent,
    private val mediaProjectionCallback: MediaProjection.Callback,
    virtualDisplayDpi: Int = VIRTUAL_DISPLAY_DPI_DEFAULT,
) : VideoCapturer, VideoSink {

    private var width = 0
    private var height = 0
    private var virtualDisplayDpi: Int = virtualDisplayDpi
    private var virtualDisplay: VirtualDisplay? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var isDisposed = false
    private var numCapturedFrames = 0L

    @Synchronized
    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        applicationContext: Context,
        capturerObserver: CapturerObserver,
    ) {
        checkNotDisposed()
        this.capturerObserver = capturerObserver
        this.surfaceTextureHelper = surfaceTextureHelper
        mediaProjectionManager =
            applicationContext.getSystemService(MediaProjectionManager::class.java)
    }

    /**
     * Rebind to a new SurfaceTextureHelper / CapturerObserver after a soft
     * PeerConnectionFactory rebuild, keeping the existing MediaProjection.
     */
    @Synchronized
    fun rebind(
        surfaceTextureHelper: SurfaceTextureHelper,
        capturerObserver: CapturerObserver,
    ) {
        checkNotDisposed()
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    @Synchronized
    fun setVirtualDisplayDpi(dpi: Int) {
        checkNotDisposed()
        virtualDisplayDpi = dpi
    }

    @Synchronized
    override fun startCapture(width: Int, height: Int, unusedFramerate: Int) {
        checkNotDisposed()
        this.width = width
        this.height = height

        val helper = surfaceTextureHelper
            ?: throw IllegalStateException("No SurfaceTextureHelper")
        val observer = capturerObserver
            ?: throw IllegalStateException("No CapturerObserver")

        if (mediaProjection == null) {
            val manager = mediaProjectionManager
                ?: throw IllegalStateException("Not initialized")
            mediaProjection = manager.getMediaProjection(
                Activity.RESULT_OK,
                mediaProjectionPermissionResultData,
            )
        }
        val projection = mediaProjection
            ?: throw IllegalStateException("MediaProjection is null")
        // (Re)register — stopCaptureKeepingProjection unregisters the callback.
        projection.registerCallback(mediaProjectionCallback, helper.handler)

        createVirtualDisplay()
        observer.onCapturerStarted(true)
        helper.startListening(this)
    }

    @Synchronized
    override fun stopCapture() {
        checkNotDisposed()
        val helper = surfaceTextureHelper ?: return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.stopListening()
            capturerObserver?.onCapturerStopped()
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.let { projection ->
                projection.unregisterCallback(mediaProjectionCallback)
                projection.stop()
            }
            mediaProjection = null
        }
    }

    /**
     * Release the VirtualDisplay and stop feeding frames, but leave
     * MediaProjection alive so capture can restart without re-consent.
     */
    @Synchronized
    fun stopCaptureKeepingProjection() {
        checkNotDisposed()
        val helper = surfaceTextureHelper ?: return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.stopListening()
            capturerObserver?.onCapturerStopped()
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
        }
    }

    @Synchronized
    override fun dispose() {
        isDisposed = true
    }

    @Synchronized
    override fun changeCaptureFormat(width: Int, height: Int, unusedFramerate: Int) {
        applyDisplayParams(width, height, virtualDisplayDpi)
    }

    /** Resize and/or update VirtualDisplay density while capturing. */
    @Synchronized
    fun applyDisplayParams(width: Int, height: Int, dpi: Int) {
        checkNotDisposed()
        if (width <= 0 || height <= 0) return
        val w = width / 2 * 2
        val h = height / 2 * 2
        val dpiChanged = dpi != virtualDisplayDpi
        if (w == this.width && h == this.height && !dpiChanged) return

        this.width = w
        this.height = h
        virtualDisplayDpi = dpi
        val display = virtualDisplay ?: return
        val helper = surfaceTextureHelper ?: return

        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.setTextureSize(w, h)
            display.resize(w, h, virtualDisplayDpi)
        }
    }

    override fun isScreencast(): Boolean = true

    override fun onFrame(frame: VideoFrame) {
        numCapturedFrames++
        capturerObserver?.onFrameCaptured(frame)
    }

    private fun createVirtualDisplay() {
        val helper = surfaceTextureHelper ?: return
        val projection = mediaProjection ?: return
        helper.setTextureSize(width, height)
        virtualDisplay = projection.createVirtualDisplay(
            "GocastScreenCapture",
            width,
            height,
            virtualDisplayDpi,
            DISPLAY_FLAGS,
            Surface(helper.surfaceTexture),
            null,
            null,
        )
    }

    private fun checkNotDisposed() {
        if (isDisposed) {
            throw RuntimeException("capturer is disposed.")
        }
    }

    companion object {
        private const val DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        const val VIRTUAL_DISPLAY_DPI_DEFAULT = 400
    }
}
