package com.kairos.app.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.ui.pomodoro.PomodoroPhase
import com.kairos.app.ui.pomodoro.PomodoroState
import java.util.Locale

/** Canaux + construction des notifications Pomodoro (timer persistant + alerte de fin de cycle). */
object PomodoroNotifications {

    /** Action d'intent ouvrant directement l'écran Pomodoro depuis une notification. */
    const val ACTION_OPEN_POMODORO = "com.kairos.app.action.POMODORO"

    const val CHANNEL_TIMER = "pomodoro_timer"
    const val CHANNEL_ALERTS = "pomodoro_alerts"
    const val ONGOING_ID = 1001
    private const val COMPLETE_ID = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, context.getString(R.string.pomo_channel_timer), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.pomo_channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
    }

    /** Notification persistante du service de premier plan : phase + temps restant + actions. */
    fun buildOngoing(context: Context, state: PomodoroState): android.app.Notification {
        val phaseLabel = context.getString(phaseLabelRes(state.phase))
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(phaseLabel)
            .setContentText(formatClock(state.remainingSeconds))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPomodoroIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.isRunning) {
            builder.addAction(0, context.getString(R.string.pomo_pause), serviceAction(context, PomodoroService.ACTION_PAUSE))
        } else {
            builder.addAction(0, context.getString(R.string.pomo_resume), serviceAction(context, PomodoroService.ACTION_RESUME))
        }
        builder.addAction(0, context.getString(R.string.pomo_reset), serviceAction(context, PomodoroService.ACTION_RESET))
        return builder.build()
    }

    /** Alerte (son + vibration) de fin d'une phase : focus → pause, pause → focus. */
    fun showPhaseComplete(context: Context, finished: PomodoroPhase) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val (title, text) = if (finished == PomodoroPhase.FOCUS) {
            context.getString(R.string.pomo_done_focus_title) to context.getString(R.string.pomo_done_focus_text)
        } else {
            context.getString(R.string.pomo_done_break_title) to context.getString(R.string.pomo_done_break_text)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPomodoroIntent(context))
            .build()
        try {
            manager.notify(COMPLETE_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS non accordée : on ignore silencieusement.
        }
    }

    private fun phaseLabelRes(phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.FOCUS -> R.string.pomo_focus
        PomodoroPhase.SHORT_BREAK -> R.string.pomo_short
        PomodoroPhase.LONG_BREAK -> R.string.pomo_long
    }

    private fun openPomodoroIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_POMODORO
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun serviceAction(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PomodoroService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun formatClock(totalSeconds: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
