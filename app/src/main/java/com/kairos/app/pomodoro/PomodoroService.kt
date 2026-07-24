package com.kairos.app.pomodoro

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kairos.app.KairosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service de premier plan qui maintient la session Pomodoro en vie hors application et affiche une
 * notification persistante (temps restant + Pause/Reprendre + Réinitialiser). Le service ne porte
 * **aucune logique de timer** : il reflète l'état du [com.kairos.app.ui.pomodoro.PomodoroEngine]
 * (source unique) et relaie les actions de la notification vers lui.
 */
class PomodoroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private val app get() = application as KairosApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> app.pomodoroEngine.pause()
            ACTION_RESUME -> app.pomodoroEngine.start()
            ACTION_RESET -> app.pomodoroEngine.reset()
        }
        pushForeground(app.pomodoroEngine.state.value)
        if (!observing) {
            observing = true
            scope.launch {
                app.pomodoroEngine.state.collect { state ->
                    if (state.active) {
                        pushForeground(state)
                    } else {
                        ServiceCompat.stopForeground(this@PomodoroService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun pushForeground(state: com.kairos.app.ui.pomodoro.PomodoroState) {
        ServiceCompat.startForeground(
            this,
            PomodoroNotifications.ONGOING_ID,
            PomodoroNotifications.buildOngoing(this, state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        observing = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.kairos.app.pomodoro.PAUSE"
        const val ACTION_RESUME = "com.kairos.app.pomodoro.RESUME"
        const val ACTION_RESET = "com.kairos.app.pomodoro.RESET"

        /** Démarre le service en premier plan (à appeler quand l'app est au premier plan). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PomodoroService::class.java))
        }
    }
}
