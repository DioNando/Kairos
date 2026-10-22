package com.kairos.app

import android.app.Application
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.LlmProvider
import com.kairos.app.data.model.Task
import com.kairos.app.data.remote.GeminiConfig
import com.kairos.app.data.remote.GeminiHttpService
import com.kairos.app.data.remote.OpenRouterConfig
import com.kairos.app.data.remote.OpenRouterHttpService
import com.kairos.app.data.reminder.ReminderNotifications
import com.kairos.app.data.reminder.ReminderScheduler
import com.kairos.app.data.repository.KairosRepository
import com.kairos.app.data.repository.PomodoroSessionStore
import com.kairos.app.data.repository.SecureCredentialStore
import com.kairos.app.data.repository.SettingsRepository
import com.kairos.app.widget.KairosWidget
import com.kairos.app.widget.KairosRowWidget
import androidx.glance.appwidget.updateAll
import com.kairos.app.domain.ai.DynamicNoteEnhancer
import com.kairos.app.domain.ai.LlmNoteEnhancer
import com.kairos.app.domain.ai.NoteEnhancer
import com.kairos.app.domain.backup.BackupManager
import com.kairos.app.pomodoro.PomodoroNotifications
import com.kairos.app.pomodoro.PomodoroService
import com.kairos.app.ui.pomodoro.PomodoroDurations
import com.kairos.app.ui.pomodoro.PomodoroEngine
import com.kairos.app.ui.theme.toAccentPalette
import com.kairos.app.wear.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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

    // --- Assistant IA (reformulation des notes) -------------------------------------------------

    /** Stockage chiffré des clés API (Gemini, OpenRouter). */
    val secureCredentialStore by lazy { SecureCredentialStore(this) }

    /** Fournisseur IA primaire courant (issu des réglages), lu dynamiquement par l'enhancer. */
    @Volatile
    private var aiPrimaryProvider: Int = AppSettings.AI_GEMINI

    // Clé saisie dans l'app prioritaire ; sinon repli sur la clé de build (.env / local.properties).
    private val geminiConfig by lazy {
        GeminiConfig({ secureCredentialStore.geminiKey ?: BuildConfig.GEMINI_API_KEY })
    }
    private val openRouterConfig by lazy {
        OpenRouterConfig({ secureCredentialStore.openRouterKey ?: BuildConfig.OPENROUTER_API_KEY })
    }

    private fun providerEnabled(provider: LlmProvider): Boolean = when (provider) {
        LlmProvider.GEMINI -> geminiConfig.isEnabled
        LlmProvider.OPENROUTER -> openRouterConfig.isEnabled
    }

    /** Un reformulateur par fournisseur (clé lue live via la config). Réutilisé par la chaîne et le test. */
    private val aiEnhancers: Map<LlmProvider, LlmNoteEnhancer> by lazy {
        val client = OkHttpClient()
        mapOf(
            LlmProvider.GEMINI to LlmNoteEnhancer(GeminiHttpService(geminiConfig, client)),
            LlmProvider.OPENROUTER to LlmNoteEnhancer(OpenRouterHttpService(openRouterConfig, client)),
        )
    }

    /**
     * Reformulateur de notes : chaîne Gemini/OpenRouter résolue à chaque appel (primaire choisi en
     * tête, l'autre en repli), limitée aux fournisseurs dont la clé est saisie.
     */
    val noteEnhancer: NoteEnhancer by lazy {
        DynamicNoteEnhancer(aiEnhancers) {
            val primary = if (aiPrimaryProvider == AppSettings.AI_OPENROUTER) LlmProvider.OPENROUTER else LlmProvider.GEMINI
            (listOf(primary) + LlmProvider.entries.filter { it != primary }).filter { providerEnabled(it) }
        }
    }

    /**
     * Teste la connexion d'un **fournisseur précis** (clé in-app ou repli .env) par un appel minimal.
     * Échoue d'emblée si aucune clé n'est configurée pour ce fournisseur.
     */
    suspend fun testAi(provider: Int): Result<Unit> {
        val p = if (provider == AppSettings.AI_OPENROUTER) LlmProvider.OPENROUTER else LlmProvider.GEMINI
        if (!providerEnabled(p)) return Result.failure(IllegalStateException(getString(R.string.ai_test_no_key)))
        return aiEnhancers.getValue(p).testConnection()
    }

    /** Clé API courante d'un fournisseur (pour préremplir les réglages). */
    fun aiKey(provider: Int): String =
        (if (provider == AppSettings.AI_OPENROUTER) secureCredentialStore.openRouterKey
        else secureCredentialStore.geminiKey).orEmpty()

    /** Enregistre (ou efface) la clé API d'un fournisseur. */
    fun setAiKey(provider: Int, key: String?) {
        if (provider == AppSettings.AI_OPENROUTER) secureCredentialStore.setOpenRouterKey(key)
        else secureCredentialStore.setGeminiKey(key)
    }

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
        ReminderNotifications.createChannel(this)
        // Crée les étiquettes par défaut au premier lancement (saisie immédiate).
        applicationScope.launch { repository.seedIfEmpty(System.currentTimeMillis()) }
        // Purge l'archive selon le réglage (tâches archivées trop anciennes), une fois au démarrage,
        // et nettoie les occurrences récurrentes terminées héritées de l'ancien modèle (sinon les
        // restaurer recréerait des doublons — la récurrence avance désormais sur place).
        applicationScope.launch {
            val days = settingsRepository.settings.first().archivePurgeDays
            repository.purgeArchive(days, System.currentTimeMillis())
            repository.purgeLegacyRecurringArchive()
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
                aiPrimaryProvider = it.aiProvider
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
        // Widget écran d'accueil : rafraîchi à chaque changement de la liste des tâches **et** des réglages
        // (les tuiles suivent la **palette de quadrants** choisie → un changement de palette doit se
        // propager au widget, pas seulement à l'app).
        applicationScope.launch {
            combine(repository.tasks, settingsRepository.settings) { _, _ -> }.collect {
                try {
                    KairosWidget().updateAll(this@KairosApp)
                    KairosRowWidget().updateAll(this@KairosApp)
                } catch (_: Throwable) {
                    // Widget indisponible / non posé : sans conséquence.
                }
            }
        }
        // Wear OS : pousse vers la montre les compteurs (tuile), la file « À faire en priorité »
        // (app montre) et la palette de quadrants. Combiné sur tâches **et** réglages → un changement
        // de palette se propage même sans modification de tâche (sinon il ne partirait jamais).
        // NB : param de combine typé explicitement (K2 ne déduit pas un paramètre déstructuré de collect).
        applicationScope.launch {
            combine(
                repository.tasks,
                repository.labels,
                settingsRepository.settings,
            ) { tasks: List<Task>, labels: List<Label>, settings: AppSettings ->
                Triple(tasks, labels, settings)
            }.collect { (tasks, labels, settings) ->
                val palette = settings.toAccentPalette()
                val labelsById = labels.associateBy { it.id }
                runCatching { WearSync.pushCounts(this@KairosApp, tasks) }
                runCatching { WearSync.pushFocus(this@KairosApp, tasks, labelsById) }
                runCatching { WearSync.pushPalette(this@KairosApp, palette) }
            }
        }
        // Rappels d'échéance : resynchronisés à chaque changement des tâches ou des réglages de
        // rappel. WorkManager reflète toujours l'état courant des données.
        applicationScope.launch {
            val reminderPrefs = settingsRepository.settings
                .map { it.remindersEnabled to it.reminderLeadMinutes }
                .distinctUntilChanged()
            combine(repository.tasks, reminderPrefs) { tasks: List<Task>, prefs: Pair<Boolean, Int> ->
                Triple(tasks, prefs.first, prefs.second)
            }.collect { snapshot ->
                runCatching {
                    ReminderScheduler.sync(
                        this@KairosApp,
                        snapshot.first,
                        snapshot.second,
                        snapshot.third,
                        System.currentTimeMillis(),
                    )
                }
            }
        }
    }
}
