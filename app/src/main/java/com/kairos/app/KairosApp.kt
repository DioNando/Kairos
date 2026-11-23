package com.kairos.app

import android.app.Application
import com.kairos.app.data.calendar.CalendarSource
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.LlmProvider
import com.kairos.app.data.model.Task
import com.kairos.app.data.model.TimeBlock
import com.kairos.app.data.remote.GeminiConfig
import com.kairos.app.data.remote.GeminiHttpService
import com.kairos.app.data.remote.LlmService
import com.kairos.app.data.remote.OpenRouterConfig
import com.kairos.app.data.remote.OpenRouterHttpService
import com.kairos.app.data.reminder.ReminderNotifications
import com.kairos.app.data.reminder.ReminderScheduler
import com.kairos.app.data.repository.AttachmentStore
import com.kairos.app.data.repository.KairosRepository
import com.kairos.app.data.repository.PomodoroSessionStore
import com.kairos.app.data.repository.SecureCredentialStore
import com.kairos.app.data.repository.SettingsRepository
import com.kairos.app.widget.KairosFocusCompactWidget
import com.kairos.app.widget.KairosFocusWidget
import com.kairos.app.widget.KairosWidget
import com.kairos.app.widget.KairosRowWidget
import com.kairos.app.widget.PriorityRefreshWorker
import androidx.glance.appwidget.updateAll
import com.kairos.app.domain.ai.DynamicNoteEnhancer
import com.kairos.app.domain.ai.LlmNoteEnhancer
import com.kairos.app.domain.ai.NoteEnhancer
import com.kairos.app.domain.backup.BackupManager
import com.kairos.app.domain.focus.FocusSelector
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** État complet poussé vers la montre à chaque changement. */
private data class WearSnapshot(
    val tasks: List<Task>,
    val subtaskDues: Map<Long, Long>,
    val timeBlocks: List<TimeBlock>,
    val labels: List<Label>,
    val settings: AppSettings,
)

/**
 * Conteneur de dépendances minimaliste (pas de framework DI) : base et repositories créés
 * paresseusement et partagés par toute l'application. Calque de l'archi WorkSync, allégé.
 */
class KairosApp : Application() {

    val database by lazy { KairosDatabase.getInstance(this) }

    val attachmentStore by lazy { AttachmentStore(this) }

    val repository by lazy {
        KairosRepository(
            taskDao = database.taskDao(),
            labelDao = database.labelDao(),
            subtaskDao = database.subtaskDao(),
            savedFilterDao = database.savedFilterDao(),
            attachmentDao = database.attachmentDao(),
            attachmentStore = attachmentStore,
            timeBlockDao = database.timeBlockDao(),
        )
    }

    /** Créneaux occupés des agendas (appareil + flux iCal), pour le time-blocking. */
    val calendarSource by lazy { CalendarSource(this, OkHttpClient()) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val backupManager by lazy {
        BackupManager(
            taskDao = database.taskDao(),
            labelDao = database.labelDao(),
            subtaskDao = database.subtaskDao(),
            savedFilterDao = database.savedFilterDao(),
            timeBlockDao = database.timeBlockDao(),
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

    /** Modèles choisis dans les réglages, lus dynamiquement par les configs. */
    @Volatile
    private var geminiModel: String = AppSettings.DEFAULT_GEMINI_MODEL

    @Volatile
    private var openRouterModel: String = AppSettings.DEFAULT_OPENROUTER_MODEL

    // Clés saisies uniquement dans l'app (store chiffré) : aucune clé n'est embarquée dans l'APK.
    private val geminiConfig by lazy {
        GeminiConfig({ secureCredentialStore.geminiKey.orEmpty() }, { geminiModel })
    }
    private val openRouterConfig by lazy {
        OpenRouterConfig({ secureCredentialStore.openRouterKey.orEmpty() }, { openRouterModel })
    }

    private fun providerEnabled(provider: LlmProvider): Boolean = when (provider) {
        LlmProvider.GEMINI -> geminiConfig.isEnabled
        LlmProvider.OPENROUTER -> openRouterConfig.isEnabled
    }

    /** Un reformulateur par fournisseur (clé lue live via la config). Réutilisé par la chaîne et le test. */
    private val aiServices: Map<LlmProvider, LlmService> by lazy {
        val client = OkHttpClient()
        mapOf(
            LlmProvider.GEMINI to GeminiHttpService(geminiConfig, client),
            LlmProvider.OPENROUTER to OpenRouterHttpService(openRouterConfig, client),
        )
    }

    private val aiEnhancers: Map<LlmProvider, LlmNoteEnhancer> by lazy {
        aiServices.mapValues { LlmNoteEnhancer(it.value) }
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
     * Teste la connexion d'un **fournisseur précis** (clé saisie + modèle choisi) par un appel minimal.
     * Échoue d'emblée si aucune clé n'est configurée pour ce fournisseur.
     */
    suspend fun testAi(provider: Int): Result<Unit> {
        val p = if (provider == AppSettings.AI_OPENROUTER) LlmProvider.OPENROUTER else LlmProvider.GEMINI
        if (!providerEnabled(p)) return Result.failure(IllegalStateException(getString(R.string.ai_test_no_key)))
        return aiEnhancers.getValue(p).testConnection()
    }

    /** Modèles disponibles chez un fournisseur (requiert sa clé, sauf OpenRouter dont la liste est publique). */
    suspend fun listAiModels(provider: Int): Result<List<String>> {
        val p = if (provider == AppSettings.AI_OPENROUTER) LlmProvider.OPENROUTER else LlmProvider.GEMINI
        if (p == LlmProvider.GEMINI && !providerEnabled(p)) {
            return Result.failure(IllegalStateException(getString(R.string.ai_test_no_key)))
        }
        return try {
            Result.success(aiServices.getValue(p).listModels())
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    /**
     * Republie le classement « À faire en priorité » (widgets, montre), qui évolue avec l'heure même
     * quand aucune donnée ne change.
     */
    suspend fun refreshPriorities() {
        val tasks = database.taskDao().getAll()
        val labels = database.labelDao().getAll().associateBy { it.id }
        val subtaskDues = FocusSelector.subtaskDues(database.subtaskDao().getAll())
        val settings = settingsRepository.settings.first()
        try {
            KairosFocusWidget().updateAll(this)
            KairosFocusCompactWidget().updateAll(this)
        } catch (_: Throwable) {
            // Widget indisponible / non posé : sans conséquence.
        }
        runCatching { WearSync.pushFocus(this, tasks, labels, settings.focusDueWeight, subtaskDues) }
    }

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
        ReminderNotifications.createChannel(this)
        PriorityRefreshWorker.schedule(this)
        // Ne réagit qu'aux sous-tâches qui changent le classement (cocher une case sans échéance : non).
        val subtaskDues = repository.subtasks.map { FocusSelector.subtaskDues(it) }.distinctUntilChanged()
        // Crée les étiquettes par défaut au premier lancement (saisie immédiate).
        applicationScope.launch { repository.seedIfEmpty(System.currentTimeMillis()) }
        // Purge l'archive selon le réglage (tâches archivées trop anciennes), une fois au démarrage,
        // et nettoie les occurrences récurrentes terminées héritées de l'ancien modèle (sinon les
        // restaurer recréerait des doublons — la récurrence avance désormais sur place).
        applicationScope.launch {
            val days = settingsRepository.settings.first().archivePurgeDays
            repository.purgeArchive(days, System.currentTimeMillis())
            repository.purgeLegacyRecurringArchive()
            // Fichiers de pièces jointes orphelins (tâche supprimée, import, éditeur abandonné).
            repository.sweepAttachmentFiles()
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
                geminiModel = it.geminiModel
                openRouterModel = it.openRouterModel
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
            combine(repository.tasks, subtaskDues, settingsRepository.settings) { _, _, _ -> }.collect {
                try {
                    KairosWidget().updateAll(this@KairosApp)
                    KairosRowWidget().updateAll(this@KairosApp)
                    KairosFocusWidget().updateAll(this@KairosApp)
                    KairosFocusCompactWidget().updateAll(this@KairosApp)
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
                subtaskDues,
                repository.timeBlocks,
                repository.labels,
                settingsRepository.settings,
            ) { tasks: List<Task>, dues: Map<Long, Long>, blocks: List<TimeBlock>, labels: List<Label>, settings: AppSettings ->
                WearSnapshot(tasks, dues, blocks, labels, settings)
            }.collect { (tasks, dues, blocks, labels, settings) ->
                val palette = settings.toAccentPalette(this@KairosApp)
                val labelsById = labels.associateBy { it.id }
                runCatching { WearSync.pushCounts(this@KairosApp, tasks) }
                runCatching { WearSync.pushFocus(this@KairosApp, tasks, labelsById, settings.focusDueWeight, dues) }
                runCatching { WearSync.pushQuadrants(this@KairosApp, tasks, labelsById, settings.quadrantSort) }
                runCatching { WearSync.pushPlanning(this@KairosApp, tasks, blocks) }
                runCatching { WearSync.pushPalette(this@KairosApp, palette) }
            }
        }
        // Pomodoro vers la montre : uniquement aux transitions (marche/pause, phase, remise à zéro) —
        // en marche la montre décompte seule à partir de l'heure de fin, pas d'envoi par seconde.
        applicationScope.launch {
            pomodoroEngine.state
                .distinctUntilChangedBy { s ->
                    listOf(s.active, s.isRunning, s.phase, s.totalSeconds, if (s.isRunning) 0 else s.remainingSeconds)
                }
                .collect { runCatching { WearSync.pushPomodoro(this@KairosApp, it) } }
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
