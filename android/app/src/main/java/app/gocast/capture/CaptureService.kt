package app.gocast.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"

        fun start(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var screenCast: ScreenCastSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = CaptureNotifications.build(this)
        ServiceCompat.startForeground(
            this,
            CaptureNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        if (screenCast == null) {
            val data = ProjectionPermissionHolder.take()
            if (data != null) {
                try {
                    screenCast = ScreenCastSession(applicationContext).also { it.start(data) }
                } catch (e: Exception) {
                    Log.e(TAG, "screen cast failed", e)
                    stopSelf()
                }
            } else {
                Log.e(TAG, "No projection permission data — stopping")
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        screenCast?.stop()
        screenCast = null
        super.onDestroy()
    }
}
