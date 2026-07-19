package app.gocast.tile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.gocast.MainActivity
import app.gocast.R
import app.gocast.capture.CaptureConsent
import app.gocast.capture.CaptureGate
import app.gocast.capture.CaptureService
import app.gocast.capture.ProjectionPermissionHolder
import app.gocast.cast.TrustedCastResolver
import app.gocast.data.TrustedPcStore
import app.gocast.domain.ConnectionState
import app.gocast.network.CastConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headless quick-cast from the QS tile: find the trusted PC on Wi‑Fi, join,
 * then show only the system screen-capture sheet (no Connect UI).
 */
class QuickCastActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* continue either way */ }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        CaptureGate.suppressAutoConsent = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ProjectionPermissionHolder.set(result.resultCode, result.data!!)
            CaptureService.start(applicationContext)
        } else {
            CastConnection.stop()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureGate.suppressAutoConsent = true

        if (TrustedPcStore(this).load() == null) {
            CaptureGate.suppressAutoConsent = false
            openApp()
            finish()
            return
        }

        lifecycleScope.launch {
            // Last connected PC only — refresh IP via discovery when possible,
            // otherwise use the saved host (same as in-app tap-to-cast).
            val resolved = TrustedCastResolver.resolveTrustedJoin(applicationContext)
            if (resolved == null) {
                CaptureGate.suppressAutoConsent = false
                openApp()
                finish()
                return@launch
            }

            val (_, params) = resolved
            CastConnection.bindAppContext(applicationContext)
            when (CastConnection.state.value) {
                is ConnectionState.Idle,
                is ConnectionState.Failed,
                -> CastConnection.start(params)
                is ConnectionState.Connecting,
                is ConnectionState.Reconnecting,
                is ConnectionState.Connected,
                -> Unit
            }

            val state = withTimeoutOrNull(12_000) {
                CastConnection.state.first {
                    it is ConnectionState.Connected || it is ConnectionState.Failed
                }
            }

            if (state !is ConnectionState.Connected) {
                Toast.makeText(
                    this@QuickCastActivity,
                    getString(R.string.tile_pc_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
                CaptureGate.suppressAutoConsent = false
                CastConnection.stop()
                finish()
                return@launch
            }

            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            captureLauncher.launch(CaptureConsent.createIntent(this@QuickCastActivity))
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CaptureGate.suppressAutoConsent = false
        }
        super.onDestroy()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
        )
    }
}
