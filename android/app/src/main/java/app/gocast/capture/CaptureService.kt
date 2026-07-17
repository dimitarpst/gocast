package app.gocast.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

class CaptureService : Service() {

    companion object{
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent){
            val intent = Intent(context, CaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        projectionResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        projectionData = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java)
        }

        val notification = CaptureNotifications.build(this)
        ServiceCompat.startForeground(
            this,
            CaptureNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        projectionData = null
        super.onDestroy()
    }
}