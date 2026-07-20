package app.gocast.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import app.gocast.MainActivity
import app.gocast.R

/**
 * Android 16 Live Update (promoted ongoing). Avoid custom RemoteViews, setColorized,
 * or dropping setRequestPromotedOngoing — those break notch-chip promotion.
 * Do not setColor() a light brand blue: OEMs force a black small-icon glyph for contrast.
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
        base(
            context = context,
            text = context.getString(R.string.notif_casting_text),
            chip = context.getString(R.string.notif_chip_casting),
        )
            .setContentIntent(openAppPendingIntent(context))
            .addAction(
                0,
                context.getString(R.string.notif_action_stop),
                stopPendingIntent(context),
            )
            .setDeleteIntent(dismissedPendingIntent(context))
            .build()

    fun paused(context: Context): Notification =
        base(
            context = context,
            text = context.getString(R.string.notif_paused_text),
            chip = context.getString(R.string.notif_chip_paused),
        )
            .setContentIntent(resumePendingIntent(context))
            .addAction(
                0,
                context.getString(R.string.notif_action_resume),
                resumePendingIntent(context),
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_stop),
                stopPendingIntent(context),
            )
            .setDeleteIntent(dismissedPendingIntent(context))
            .build()

    private fun base(context: Context, text: String, chip: String): NotificationCompat.Builder {
        ensureChannel(context)
        val largeIcon = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_notification_gocast,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_casting_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_gocast)
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chip)
            .setShowWhen(false)
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
