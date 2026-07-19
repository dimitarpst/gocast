package app.gocast.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.gocast.MainActivity
import app.gocast.R
import app.gocast.capture.CaptureService
import app.gocast.data.TrustedPcStore

/**
 * Quick Settings tile — pin gocast in the shade edit menu.
 * Tap stops an active cast, or quick-casts to the last PC when it is on Wi‑Fi
 * (system capture sheet only). Otherwise opens the app.
 */
class GocastTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (CaptureService.isMediaActive()) {
                startService(
                    Intent(this, CaptureService::class.java)
                        .setAction(CaptureService.ACTION_STOP),
                )
                refreshTile(forceInactive = true)
            } else if (TrustedPcStore(this).load() != null) {
                openQuickCast()
            } else {
                openApp()
            }
        }
    }

    private fun openQuickCast() {
        collapseAndStart(
            Intent(this, QuickCastActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            REQUEST_QUICK_CAST,
        )
    }

    private fun openApp() {
        collapseAndStart(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            REQUEST_OPEN,
        )
    }

    private fun collapseAndStart(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile(forceInactive: Boolean = false) {
        val tile = qsTile ?: return
        val casting = !forceInactive && CaptureService.isMediaActive()
        val hasTrusted = TrustedPcStore(this).load() != null
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_gocast)
        tile.state = if (casting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            casting -> getString(R.string.tile_casting)
            hasTrusted -> getString(R.string.tile_quick_cast)
            else -> getString(R.string.tile_idle)
        }
        tile.updateTile()
    }

    companion object {
        private const val REQUEST_OPEN = 42
        private const val REQUEST_QUICK_CAST = 43

        /** Ask the system to refresh this tile (e.g. when cast starts/stops). */
        fun requestUpdate(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, GocastTileService::class.java),
            )
        }
    }
}
