package app.gocast.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.gocast.network.CastConnection
import app.gocast.settings.CastQualityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        const val ACTION_STOP = "app.gocast.action.STOP_CAST"
        const val ACTION_DISMISSED = "app.gocast.action.NOTIFICATION_DISMISSED"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptureService::class.java),
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenCast: ScreenCastSession? = null
    private var publisher: WebRtcPublisher? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var paused = false

    @Volatile
    private var startingCapture = false

    override fun onCreate() {
        super.onCreate()
        // Keep / restore phone↔PC signaling for the lifetime of this service
        // (including paused), independent of Activity / ViewModel.
        CastConnection.ensureConnected()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    tearDownMedia(projectionAlreadyStopped = false)
                    paused = false
                    startingCapture = false
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

    private fun startOrResumeCapture(data: Intent) {
        paused = false
        startingCapture = true
        CastConnection.ensureConnected()
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
            publisher = WebRtcPublisher(factory, track, serviceScope, quality).also { it.start() }
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
            ServiceCompat.startForeground(
                this,
                CaptureNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    override fun onDestroy() {
        publisher?.stop()
        screenCast?.stop(projectionAlreadyStopped = true)
        publisher = null
        screenCast = null
        startingCapture = false
        releaseCastWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enterPaused() {
        if (paused) return
        paused = true
        startingCapture = false
        showForegroundNotification()
        serviceScope.launch {
            tearDownMedia(projectionAlreadyStopped = true)
        }
    }

    private suspend fun tearDownMedia(projectionAlreadyStopped: Boolean) {
        val pub = publisher
        val session = screenCast
        publisher = null
        screenCast = null
        releaseCastWakeLock()

        pub?.stop()
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
