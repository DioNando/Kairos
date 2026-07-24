package com.kairos.app.ui.pomodoro

import com.kairos.app.data.repository.PomodoroSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Phases du cycle Pomodoro. La durée par défaut sert de repli ; la durée réelle vient des réglages. */
enum class PomodoroPhase(val minutes: Int) {
    FOCUS(25),
    SHORT_BREAK(5),
    LONG_BREAK(15),
}

/** Durées (minutes) et cadence configurables, fournies par les réglages. */
data class PomodoroDurations(
    val focus: Int = 25,
    val shortBreak: Int = 5,
    val longBreak: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
)

/** État immuable du Pomodoro. */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val totalSeconds: Int = PomodoroPhase.FOCUS.minutes * 60,
    val remainingSeconds: Int = PomodoroPhase.FOCUS.minutes * 60,
    val isRunning: Boolean = false,
    val completedFocus: Int = 0,
) {
    val progress: Float
        get() = if (totalSeconds == 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds

    val active: Boolean
        get() = isRunning || remainingSeconds < totalSeconds
}

/**
 * Moteur Pomodoro à portée **application** (repris de WorkSync, allégé de l'analytics). Le décompte
 * est ancré sur un **instant de fin absolu** ([endAt]) : le temps restant se recalcule depuis
 * l'horloge, ce qui rend la session robuste à la navigation **et** au redémarrage du process (via
 * [PomodoroSessionStore]). [onPhaseFinished] permet de notifier la fin (son + vibration).
 */
class PomodoroEngine(
    private val scope: CoroutineScope,
    private val durations: () -> PomodoroDurations = { PomodoroDurations() },
    private val sessionStore: PomodoroSessionStore? = null,
    private val onPhaseFinished: (PomodoroPhase) -> Unit = {},
    private val onFocusLogged: (taskId: Long, seconds: Int) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    /** Tâche liée à la session : le temps des focus terminés lui est crédité (null = aucune). */
    private val _linkedTaskId = MutableStateFlow<Long?>(null)
    val linkedTaskId: StateFlow<Long?> = _linkedTaskId.asStateFlow()

    fun setLinkedTask(taskId: Long?) {
        _linkedTaskId.value = taskId
    }

    /** Minutes d'un focus tout juste terminé (retour visuel : snackbar). */
    private val _loggedMinutes = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val loggedMinutes: SharedFlow<Int> = _loggedMinutes

    private var timerJob: Job? = null

    /** Instant de fin (epoch ms) de la phase en cours quand le timer tourne. */
    private var endAt: Long = 0L

    private fun secondsFor(phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.FOCUS -> durations().focus
        PomodoroPhase.SHORT_BREAK -> durations().shortBreak
        PomodoroPhase.LONG_BREAK -> durations().longBreak
    }.coerceAtLeast(1) * 60

    fun selectPhase(phase: PomodoroPhase) {
        if (_state.value.active) return
        val total = secondsFor(phase)
        _state.update { it.copy(phase = phase, totalSeconds = total, remainingSeconds = total, isRunning = false) }
        persist()
    }

    /** Met à jour la durée de la phase **inactive** courante quand les réglages changent. */
    fun onDurationsChanged() {
        if (_state.value.active) return
        val total = secondsFor(_state.value.phase)
        _state.update { it.copy(totalSeconds = total, remainingSeconds = total) }
        persist()
    }

    fun start() {
        if (_state.value.isRunning) return
        if (_state.value.remainingSeconds <= 0) {
            _state.update { it.copy(remainingSeconds = it.totalSeconds) }
        }
        endAt = now() + _state.value.remainingSeconds * 1000L
        _state.update { it.copy(isRunning = true) }
        persist()
        startTicker()
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _state.value.isRunning) {
                val remaining = ((endAt - now()) / 1000L).toInt().coerceAtLeast(0)
                _state.update { it.copy(remainingSeconds = remaining) }
                if (remaining <= 0) {
                    onPhaseComplete()
                    break
                }
                delay(500)
            }
        }
    }

    fun pause() {
        timerJob?.cancel()
        val remaining = ((endAt - now()) / 1000L).toInt().coerceAtLeast(0)
        endAt = 0L
        _state.update { it.copy(isRunning = false, remainingSeconds = remaining) }
        persist()
    }

    fun reset() {
        timerJob?.cancel()
        endAt = 0L
        _state.update { it.copy(remainingSeconds = it.totalSeconds, isRunning = false) }
        persist()
    }

    private fun onPhaseComplete() {
        val state = _state.value
        val finishedPhase = state.phase
        if (finishedPhase == PomodoroPhase.FOCUS) {
            val minutes = state.totalSeconds / 60
            if (minutes > 0) _loggedMinutes.tryEmit(minutes)
            _linkedTaskId.value?.let { taskId -> onFocusLogged(taskId, state.totalSeconds) }
            val completed = state.completedFocus + 1
            val cyclesBeforeLong = durations().cyclesBeforeLongBreak.coerceAtLeast(1)
            val next = if (completed % cyclesBeforeLong == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            val total = secondsFor(next)
            _state.update {
                it.copy(
                    completedFocus = completed,
                    phase = next,
                    totalSeconds = total,
                    remainingSeconds = total,
                    isRunning = false,
                )
            }
        } else {
            val total = secondsFor(PomodoroPhase.FOCUS)
            _state.update {
                it.copy(
                    phase = PomodoroPhase.FOCUS,
                    totalSeconds = total,
                    remainingSeconds = total,
                    isRunning = false,
                )
            }
        }
        endAt = 0L
        persist()
        onPhaseFinished(finishedPhase)
    }

    /** Restaure une session persistée au démarrage du process (ticker relancé si encore en cours). */
    suspend fun restore() {
        val snap = sessionStore?.load() ?: return
        val phase = PomodoroPhase.entries.getOrElse(snap.phaseOrdinal) { PomodoroPhase.FOCUS }
        if (snap.isRunning) {
            val remaining = ((snap.endAt - now()) / 1000L).toInt()
            if (remaining > 0) {
                endAt = snap.endAt
                _state.value = PomodoroState(phase, snap.totalSeconds, remaining, true, snap.completedFocus)
                startTicker()
            } else {
                // Terminé pendant l'absence : on rejoue la complétion (phase suivante + notif).
                _state.value = PomodoroState(phase, snap.totalSeconds, 0, false, snap.completedFocus)
                onPhaseComplete()
            }
        } else {
            _state.value =
                PomodoroState(phase, snap.totalSeconds, snap.remainingSeconds, false, snap.completedFocus)
        }
    }

    private fun persist() {
        val store = sessionStore ?: return
        val s = _state.value
        scope.launch {
            store.save(
                PomodoroSessionStore.Snapshot(
                    phaseOrdinal = s.phase.ordinal,
                    totalSeconds = s.totalSeconds,
                    remainingSeconds = s.remainingSeconds,
                    isRunning = s.isRunning,
                    endAt = endAt,
                    completedFocus = s.completedFocus,
                ),
            )
        }
    }
}
