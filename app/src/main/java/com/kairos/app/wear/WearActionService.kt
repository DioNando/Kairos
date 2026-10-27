package com.kairos.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.kairos.app.KairosApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reçoit les **actions** envoyées par la montre (MessageClient) et les applique côté téléphone, seul
 * détenteur de la base : terminer une tâche, piloter le Pomodoro. Les DataItems repartent ensuite
 * d'eux-mêmes vers la montre (les flux de l'app observent la base et le moteur Pomodoro).
 */
class WearActionService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val app = application as KairosApp
        val payload = String(event.data, Charsets.UTF_8)
        when (event.path) {
            PATH_COMPLETE -> payload.toLongOrNull()?.let { id ->
                app.applicationScope.launch { app.repository.setCompleted(id, System.currentTimeMillis()) }
            }
            // Le moteur Pomodoro est piloté depuis le thread principal (comme depuis l'UI).
            PATH_POMODORO -> app.applicationScope.launch(Dispatchers.Main) {
                val engine = app.pomodoroEngine
                when (payload) {
                    POMODORO_TOGGLE -> if (engine.state.value.isRunning) engine.pause() else engine.start()
                    POMODORO_RESET -> engine.reset()
                }
            }
        }
    }

    companion object {
        const val PATH_COMPLETE = "/kairos/action/complete"
        const val PATH_POMODORO = "/kairos/action/pomodoro"
        const val POMODORO_TOGGLE = "toggle"
        const val POMODORO_RESET = "reset"
    }
}
