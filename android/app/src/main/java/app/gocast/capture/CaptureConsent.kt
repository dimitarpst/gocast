package app.gocast.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

object CaptureConsent{
    fun createIntent(context: Context): Intent{
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        return manager.createScreenCaptureIntent()
    }
}