package app.gocast.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

object CaptureConsent {
    fun createIntent(context: Context): Intent {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        // Plain consent: Android already preselects Entire screen and still offers
        // Share one app. Do NOT use createConfigForDefaultDisplay() — that opts
        // the app out of single-app share and the system labels it unsupported.
        return manager.createScreenCaptureIntent()
    }
}
