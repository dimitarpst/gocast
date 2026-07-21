package app.gocast.capture

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.gocast.network.CastConnection

/**
 * Headless resume from the paused notification: only the system screen-capture
 * sheet — no Connect UI. Runs in its own empty-affinity task so tapping Resume
 * does not yank [app.gocast.MainActivity] to the foreground when gocast is still
 * in recents behind another app.
 *
 * MediaProjection cannot be restarted from a Service alone.
 */
class ResumeCastActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        CaptureGate.suppressAutoConsent = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ProjectionPermissionHolder.set(result.resultCode, result.data!!)
            CaptureService.start(applicationContext)
        }
        finish()
    }

    private val castPrepPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        captureLauncher.launch(CaptureConsent.createIntent(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureGate.suppressAutoConsent = true
        CastConnection.bindAppContext(applicationContext)
        CastConnection.ensureConnected()
        launchCaptureConsent()
    }

    private fun launchCaptureConsent() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            captureLauncher.launch(CaptureConsent.createIntent(this))
        } else {
            castPrepPermissions.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CaptureGate.suppressAutoConsent = false
        }
        super.onDestroy()
    }
}
