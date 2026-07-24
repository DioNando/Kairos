package com.kairos.app.ui.pomodoro

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Façade UI au-dessus du [PomodoroEngine] (à portée application) : l'état survit à la navigation,
 * le ViewModel ne fait que relayer l'état et les actions.
 */
class PomodoroViewModel(
    private val engine: PomodoroEngine,
) : ViewModel() {

    val state: StateFlow<PomodoroState> = engine.state

    /** Tâche liée à la session courante (son temps de focus lui sera crédité). */
    val linkedTaskId: StateFlow<Long?> = engine.linkedTaskId

    fun setLinkedTask(taskId: Long?) = engine.setLinkedTask(taskId)

    /** Minutes d'une session de concentration tout juste terminée (retour visuel). */
    val loggedMinutes: SharedFlow<Int> = engine.loggedMinutes

    fun selectPhase(phase: PomodoroPhase) = engine.selectPhase(phase)
    fun start() = engine.start()
    fun pause() = engine.pause()
    fun reset() = engine.reset()
}
