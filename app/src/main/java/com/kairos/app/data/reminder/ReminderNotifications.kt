package com.kairos.app.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kairos.app.MainActivity
import com.kairos.app.R

/** Canal + construction de la notification de rappel d'échéance (deep-link vers la tâche). */
object ReminderNotifications {

    /** Action d'intent ouvrant directement l'éditeur d'une tâche depuis un rappel. */
    const val ACTION_OPEN_TASK = "com.kairos.app.action.OPEN_TASK"
    const val EXTRA_TASK_ID = "com.kairos.app.extra.TASK_ID"

    const val CHANNEL_REMINDERS = "task_reminders"
    /** Base des ids de notification : id de tâche décalé pour éviter les collisions avec le Pomodoro. */
    private const val ID_BASE = 2000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { enableVibration(true) },
        )
    }

    /** Affiche le rappel d'une tâche (titre + note en corps). Ignoré si les notifs sont coupées. */
    fun show(context: Context, taskId: Long, title: String, body: String?) {
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .apply { if (!body.isNullOrBlank()) setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)) }
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openTaskIntent(context, taskId))
            .build()
        try {
            manager.notify(ID_BASE + taskId.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS non accordée : on ignore silencieusement.
        }
    }

    private fun openTaskIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
