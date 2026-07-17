package app.gocast.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.gocast.MainActivity

/**
 * Android 16 (SDK 36) Live Update: a "promoted ongoing" notification. Normal layout
 * (title/text/actions, no call UI, no media scrubber), pinned at the top of the shade
 * with a status-bar chip. Some OEMs (HyperOS) still allow swiping; deleteIntent
 * re-posts via CaptureService so a swipe never kills the cast.
 */
object CaptureNotifications {

    const val CHANNEL_ID = "gocast_cast_v5"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen casting",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            description = "Shown while gocast is casting (foreground service)"
        }
        manager.createNotificationChannel(channel)
    }

    fun casting(context: Context): Notification =
        base(context, "Casting screen", chip = "Casting")
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, "Stop", stopPendingIntent(context))
            .setDeleteIntent(dismissedPendingIntent(context))
            .build()

    fun paused(context: Context): Notification =
        base(context, "Cast paused — tap to resume", chip = "Paused")
            .setContentIntent(resumePendingIntent(context))
            .addAction(0, "Resume", resumePendingIntent(context))
            .addAction(0, "Stop", stopPendingIntent(context))
            .setDeleteIntent(dismissedPendingIntent(context))
            .build()

    private fun base(context: Context, text: String, chip: String): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("gocast")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chip)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLocalOnly(true)
    }

    private fun resumePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RESUME_CAST
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            1,
            Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissedPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            3,
            Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
