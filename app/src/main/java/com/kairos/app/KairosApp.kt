package com.kairos.app

import android.app.Application
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.repository.KairosRepository
import com.kairos.app.data.repository.PomodoroSessionStore
import com.kairos.app.data.repository.SettingsRepository
import com.kairos.app.domain.backup.BackupManager
import com.kairos.app.pomodoro.PomodoroNotifications
import com.kairos.app.pomodoro.PomodoroService
import com.kairos.app.ui.pomodoro.PomodoroDurations
import com.kairos.app.ui.pomodoro.PomodoroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Conteneur de dépendances minimaliste (pas de framework DI) : base et repositories créés
 * paresseusement et partagés par toute l'application. Calque de l'archi WorkSync, allégé.
 */
class KairosApp : Application() {

    val database by lazy { KairosDatabase.getInstance(this) }

    val repository by lazy {
        KairosRepository(
            taskDao = database.taskDao(),
            labelDao = database.labelDao(),
            subtaskDao = database.subtaskDao(),
        )
    }

    val settingsRepository by lazy { SettingsRepository(this) }

    val backupManager by lazy {
        BackupManager(
            taskDao = database.taskDao(),
            labelDao = database.labelDao(),
            subtaskDao = database.subtaskDao(),
            settingsRepository = settingsRepository,
        )
    }

    /** Portée liée au cycle de vie du process, pour le travail hors UI (export/import). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Pomodoro (repris de WorkSync) ----------------------------------------------------------

    /** Persistance de la session Pomodoro (survie au redémarrage du process). */
    private val pomodoroSessionStore by lazy { PomodoroSessionStore(this) }

    /** Durées Pomodoro courantes (issues des réglages), lues dynamiquement par le moteur. */
    @Volatile
    private var pomodoroDurations = PomodoroDurations()

    /** Moteur Pomodoro partagé : décompte ancré sur l'horloge, persistant, notifie la fin de phase. */
    val pomodoroEngine by lazy {
        PomodoroEngine(
            scope = applicationScope,
            durations = { pomodoroDurations },
            sessionStore = pomodoroSessionStore,
            onPhaseFinished = { phase -> PomodoroNotifications.showPhaseComplete(this, phase) },
            // Crédite le temps d'un focus terminé à la tâche liée (le cas échéant).
            onFocusLogged = { taskId, seconds ->
                applicationScope.launch { repository.addFocusTime(taskId, seconds) }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
        // Crée les étiquettes par défaut au premier lancement (saisie immédiate).
        applicationScope.launch { repository.seedIfEmpty(System.currentTimeMillis()) }
        // Purge l'archive selon le réglage (tâches terminées trop anciennes), une fois au démarrage.
        applicationScope.launch {
            val days = settingsRepository.settings.first().archivePurgeDays
            repository.purgeArchive(days, System.currentTimeMillis())
        }
        // Suit les durées Pomodoro choisies dans les réglages.
        applicationScope.launch {
            settingsRepository.settings.collect {
                pomodoroDurations = PomodoroDurations(
                    focus = it.focusMinutes,
                    shortBreak = it.shortBreakMinutes,
                    longBreak = it.longBreakMinutes,
                    cyclesBeforeLongBreak = it.cyclesBeforeLongBreak,
                )
                pomodoroEngine.onDurationsChanged()
            }
        }
        // Restaure une session Pomodoro en cours après un redémarrage du process.
        applicationScope.launch { pomodoroEngine.restore() }
        // Démarre le service de premier plan quand une session devient active (app au 1er plan).
        applicationScope.launch {
            var wasActive = false
            pomodoroEngine.state.collect { state ->
                if (state.active && !wasActive) {
                    runCatching { PomodoroService.start(this@KairosApp) }
                }
                wasActive = state.active
            }
        }
    }
}
