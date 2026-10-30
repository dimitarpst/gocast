package app.gocast.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.gocast.R
import app.gocast.network.CastConnection
import app.gocast.network.CastLinkMonitor
import app.gocast.network.SignalingBridge
import app.gocast.settings.CastQualityDiff
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualityStore
import app.gocast.tile.GocastTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CaptureService : Service() {

    enum class CastUiState {
        Idle,
        Casting,
        Paused,
    }

    companion object {
        private const val TAG = "CaptureService"
        const val ACTION_STOP = "app.gocast.action.STOP_CAST"
        const val ACTION_DISMISSED = "app.gocast.action.NOTIFICATION_DISMISSED"
        const val ACTION_APPLY_QUALITY = "app.gocast.action.APPLY_QUALITY"

        @Volatile
        private var mediaActive = false

        private val _castUiState = MutableStateFlow(CastUiState.Idle)
        val castUiState: StateFlow<CastUiState> = _castUiState.asStateFlow()

        fun isMediaActive(): Boolean = mediaActive

        private fun publishCastUiState(paused: Boolean) {
            _castUiState.value = when {
                paused -> CastUiState.Paused
                mediaActive -> CastUiState.Casting
                else -> CastUiState.Idle
            }
        }

        private fun setMediaActive(context: Context, active: Boolean, paused: Boolean) {
            mediaActive = active
            publishCastUiState(paused)
            GocastTileService.requestUpdate(context)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptureService::class.java),
            )
        }

        /** Ask a running cast to reload quality prefs (no-op if not casting). */
        fun applyQualityIfCasting(context: Context) {
            if (!mediaActive) return
            val intent = Intent(context, CaptureService::class.java)
                .setAction(ACTION_APPLY_QUALITY)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "applyQualityIfCasting failed", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenCast: ScreenCastSession? = null
    private var publisher: WebRtcPublisher? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var appliedQuality: CastQualitySettings? = null

    @Volatile
    private var paused = false

    @Volatile
    private var startingCapture = false

    override fun onCreate() {
        super.onCreate()
        CastConnection.bindAppContext(applicationContext)
        // Keep / restore phone↔PC signaling for the lifetime of this service
        // (including paused), independent of Activity / ViewModel.
        CastConnection.ensureConnected()
        // A toast reaches the user while they cast another app; the panel shows only inside gocast.
        serviceScope.launch {
            CastLinkMonitor.codecFallback.filterNotNull().collect { fallback ->
                val text = getString(
                    fallback.messageRes,
                    CastLinkMonitor.codecLabel(fallback.wanted),
                    CastLinkMonitor.codecLabel(fallback.used),
                )
                Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    tearDownMedia(projectionAlreadyStopped = false)
                    paused = false
                    startingCapture = false
                    appliedQuality = null
                    setMediaActive(applicationContext, active = false, paused = false)
                    CastConnection.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_DISMISSED -> {
                // Swipe-dismissed (allowed on Android 14+) — re-post the FGS notification.
                showForegroundNotification()
                return START_STICKY
            }
            ACTION_APPLY_QUALITY -> {
                applyQualityFromStore()
                return START_STICKY
            }
        }

        val data = ProjectionPermissionHolder.take()
        if (data != null) {
            startOrResumeCapture(data)
        } else {
            showForegroundNotification()
            val hasSession = screenCast != null || startingCapture || paused
            if (!hasSession) {
                Log.w(TAG, "No projection data and no active session — stopSelf")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun applyQualityFromStore() {
        if (paused || startingCapture) return
        val session = screenCast ?: return
        val pub = publisher ?: return
        val next = CastQualityStore.load(applicationContext)
        val old = appliedQuality
        if (old == next) return

        if (old == null) {
            appliedQuality = next
            return
        }

        serviceScope.launch {
            try {
                when {
                    CastQualityDiff.needsFactoryRebuild(old, next) -> {
                        rebuildMediaKeepingProjection(next)
                    }
                    else -> {
                        if (CastQualityDiff.needsCaptureUpdate(old, next)) {
                            session.applyQuality(next)
                        }
                        val renegotiate = CastQualityDiff.needsRenegotiation(old, next)
                        if (renegotiate || CastQualityDiff.needsRtpUpdate(old, next)) {
                            pub.applyQuality(next, renegotiate = renegotiate)
                        } else {
                            // Capture-only change: keep publisher snapshot in sync.
                            pub.applyQuality(next, renegotiate = false)
                        }
                        appliedQuality = next
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyQualityFromStore failed", e)
            }
        }
    }

    private suspend fun rebuildMediaKeepingProjection(next: CastQualitySettings) {
        val session = screenCast ?: return
        val oldPub = publisher
        publisher = null
        // The rebuild disposes the factory; its connection must be gone first.
        oldPub?.stopAndDispose()

        val ok = session.rebuildKeepingProjection(next)
        if (!ok) {
            Log.e(TAG, "Soft factory rebuild failed — enter paused")
            appliedQuality = null
            enterPaused()
            return
        }

        val factory = session.peerConnectionFactory
        val track = session.videoTrack
        if (factory == null || track == null) {
            Log.e(TAG, "Missing factory/track after soft rebuild")
            enterPaused()
            return
        }

        publisher = WebRtcPublisher(
            factory,
            track,
            serviceScope,
            next,
            audioTrack = session.audioTrack,
        ).also { it.start() }
        appliedQuality = next
        setMediaActive(applicationContext, active = true, paused = false)
        showForegroundNotification()
    }

    private fun startOrResumeCapture(data: Intent) {
        paused = false
        startingCapture = true
        CastConnection.ensureConnected()
        publishCastUiState(paused = false)
        showForegroundNotification()
        acquireCastWakeLock()

        serviceScope.launch {
            try {
                if (screenCast != null || publisher != null) {
                    tearDownMedia(projectionAlreadyStopped = false)
                }
                beginCapture(data)
            } catch (e: Exception) {
                Log.e(TAG, "screen cast failed", e)
                startingCapture = false
                enterPaused()
            }
        }
    }

    private fun beginCapture(data: Intent) {
        val session = ScreenCastSession(
            appContext = applicationContext,
            onProjectionStopped = { enterPaused() },
        ).also { it.start(data) }
        screenCast = session
        startingCapture = false

        val factory = session.peerConnectionFactory
        val track = session.videoTrack
        if (factory != null && track != null) {
            val quality = CastQualityStore.load(applicationContext)
            appliedQuality = quality
            publisher = WebRtcPublisher(
                factory,
                track,
                serviceScope,
                quality,
                audioTrack = session.audioTrack,
            ).also { it.start() }
            setMediaActive(applicationContext, active = true, paused = false)
            SignalingBridge.send("""{"type":"cast-resumed"}""")
        } else {
            Log.e(TAG, "Missing factory/track after capturer start")
        }
        showForegroundNotification()
    }

    private fun showForegroundNotification() {
        val notification = if (paused) {
            CaptureNotifications.paused(this)
        } else {
            CaptureNotifications.casting(this)
        }
        try {
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            val types = if (hasMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            ServiceCompat.startForeground(
                this,
                CaptureNotifications.NOTIFICATION_ID,
                notification,
                types,
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    override fun onDestroy() {
        paused = false
        setMediaActive(applicationContext, active = false, paused = false)
        // Blocks briefly: the connection must be disposed before the session disposes the factory.
        runBlocking { publisher?.stopAndDispose() }
        screenCast?.stop(projectionAlreadyStopped = true)
        publisher = null
        screenCast = null
        startingCapture = false
        appliedQuality = null
        releaseCastWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enterPaused() {
        if (paused) return
        paused = true
        startingCapture = false
        // Tell the PC immediately — don't wait for ICE to time out on a black frame.
        SignalingBridge.send("""{"type":"cast-paused"}""")
        setMediaActive(applicationContext, active = false, paused = true)
        showForegroundNotification()
        serviceScope.launch {
            tearDownMedia(projectionAlreadyStopped = true)
            appliedQuality = null
        }
    }

    private suspend fun tearDownMedia(projectionAlreadyStopped: Boolean) {
        val pub = publisher
        val session = screenCast
        publisher = null
        screenCast = null
        if (!paused) {
            setMediaActive(applicationContext, active = false, paused = false)
        }
        releaseCastWakeLock()

        pub?.stopAndDispose()
        withContext(Dispatchers.Default) {
            session?.stop(projectionAlreadyStopped = projectionAlreadyStopped)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireCastWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "gocast:casting",
        ).also { lock ->
            lock.setReferenceCounted(false)
            lock.acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseCastWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }
}
