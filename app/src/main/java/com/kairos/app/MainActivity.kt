package com.kairos.app

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.reminder.ReminderNotifications
import com.kairos.app.pomodoro.PomodoroNotifications
import com.kairos.app.ui.ViewModelFactory
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.navigation.KairosNavHost
import com.kairos.app.ui.onboarding.OnboardingScreen
import com.kairos.app.ui.theme.AccentPalette
import com.kairos.app.ui.theme.AppPalettes
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.ui.theme.LocalAccentPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Compteur incrémenté à chaque intent « ouvrir le Pomodoro » (notification). */
    private val pomodoroLaunch = mutableIntStateOf(0)

    /** Tâche à ouvrir depuis un rappel : son id + un compteur pour redéclencher la navigation. */
    private val openTaskId = mutableLongStateOf(0L)
    private val openTaskLaunch = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val app = application as KairosApp
        val factory = ViewModelFactory(app.repository, app.pomodoroEngine)
        val settingsRepository = app.settingsRepository

        setContent {
            val scope = rememberCoroutineScope()
            val loaded by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: AppSettings()

            // IA : un compteur bumpé à chaque changement de clé force le recalcul de la dispo et du
            // préremplissage (les clés vivent dans le store chiffré, hors du Flow des réglages).
            var aiKeysVersion by remember { mutableIntStateOf(0) }
            val aiEnabled = remember(aiKeysVersion) { app.noteEnhancer.isEnabled }
            val geminiKey = remember(aiKeysVersion) { app.aiKey(AppSettings.AI_GEMINI) }
            val openRouterKey = remember(aiKeysVersion) { app.aiKey(AppSettings.AI_OPENROUTER) }

            // Langue de l'app : override de la locale (Système = pas d'override → suit le téléphone).
            val langTag = when (settings.language) {
                AppSettings.LANG_FR -> "fr"
                AppSettings.LANG_EN -> "en"
                else -> null
            }
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, langTag) {
                if (langTag == null) baseContext
                else baseContext.createConfigurationContext(
                    Configuration(baseContext.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(langTag))
                    },
                )
            }

            // Système de fichiers (SAF) : choisir un fichier de sortie / d'entrée pour la sauvegarde.
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> uri?.let { doExport(app, it) } }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let { doImport(app, it) } }

            val darkTheme = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            // Transition douce des couleurs de quadrants lors d'un changement de palette.
            // paletteId == AppPalettes.size → palette « Personnalisée » construite depuis les réglages.
            val target = if (settings.paletteId in AppPalettes.indices) {
                AppPalettes[settings.paletteId]
            } else {
                AccentPalette(
                    name = "Personnalisée",
                    doColor = settings.customDoColor.toColor(),
                    scheduleColor = settings.customScheduleColor.toColor(),
                    delegateColor = settings.customDelegateColor.toColor(),
                    eliminateColor = settings.customEliminateColor.toColor(),
                )
            }
            val spec = tween<Color>(durationMillis = 450)
            val doC by animateColorAsState(target.doColor, spec, label = "accent-do")
            val schedC by animateColorAsState(target.scheduleColor, spec, label = "accent-schedule")
            val delC by animateColorAsState(target.delegateColor, spec, label = "accent-delegate")
            val elimC by animateColorAsState(target.eliminateColor, spec, label = "accent-eliminate")
            val palette = AccentPalette(target.name, doC, schedC, delC, elimC)

            KairosTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalAccentPalette provides palette,
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    // L'override de LocalContext (langue) casse la résolution par défaut du
                    // propriétaire ActivityResult (il remonte la chaîne de contexte, qui ne mène plus
                    // à l'Activity via createConfigurationContext). On le re-fournit explicitement —
                    // sinon rememberLauncherForActivityResult (écran Pomodoro) plante.
                    LocalActivityResultRegistryOwner provides this@MainActivity,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (loaded != null && !settings.onboardingDone) {
                            OnboardingScreen(
                                onDone = { scope.launch { settingsRepository.setOnboardingDone(true) } },
                            )
                        } else if (loaded != null) {
                            KairosNavHost(
                                factory = factory,
                                settings = settings,
                                aiEnabled = aiEnabled,
                                geminiKey = geminiKey,
                                openRouterKey = openRouterKey,
                                reformulateTask = { title, note, subs -> app.noteEnhancer.enhance(title, note, subs) },
                                suggestClassify = { title, note -> app.noteEnhancer.suggestClassification(title, note) },
                                suggestSubtasks = { title, note -> app.noteEnhancer.suggestSubtasks(title, note) },
                                onSetAiProvider = { scope.launch { settingsRepository.setAiProvider(it) } },
                                onSetAiKey = { provider, key -> app.setAiKey(provider, key); aiKeysVersion++ },
                                onSetThemeMode = { scope.launch { settingsRepository.setThemeMode(it) } },
                                onSetLanguage = { scope.launch { settingsRepository.setLanguage(it) } },
                                onSetDynamicColor = { scope.launch { settingsRepository.setDynamicColor(it) } },
                                onSetPalette = { scope.launch { settingsRepository.setPalette(it) } },
                                onSetCustomPalette = { d, s, dg, e ->
                                    scope.launch { settingsRepository.setCustomPalette(d, s, dg, e) }
                                },
                                onSetUrgencyThreshold = { scope.launch { settingsRepository.setUrgencyThreshold(it) } },
                                onSetSwimLanes = { scope.launch { settingsRepository.setSwimLanes(it) } },
                                onSetArchivePurgeDays = { scope.launch { settingsRepository.setArchivePurgeDays(it) } },
                                onSetShowGuide = { scope.launch { settingsRepository.setShowGuide(it) } },
                                onSetPomodoroStyle = { scope.launch { settingsRepository.setPomodoroStyle(it) } },
                                onSetPomodoroDurations = { f, s, l, c ->
                                    scope.launch { settingsRepository.setPomodoroDurations(f, s, l, c) }
                                },
                                onExport = { exportLauncher.launch("kairos-backup.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json")) },
                                pomodoroLaunch = pomodoroLaunch.intValue,
                                openTaskId = openTaskId.longValue,
                                openTaskLaunch = openTaskLaunch.intValue,
                                onSetRemindersEnabled = { scope.launch { settingsRepository.setRemindersEnabled(it) } },
                                onSetReminderLead = { scope.launch { settingsRepository.setReminderLeadMinutes(it) } },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Une notification demande d'ouvrir un écran : on incrémente le signal observé. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            PomodoroNotifications.ACTION_OPEN_POMODORO -> pomodoroLaunch.intValue++
            ReminderNotifications.ACTION_OPEN_TASK -> {
                val id = intent.getLongExtra(ReminderNotifications.EXTRA_TASK_ID, 0L)
                if (id > 0L) {
                    openTaskId.longValue = id
                    openTaskLaunch.intValue++
                }
            }
        }
    }

    private fun doExport(app: KairosApp, uri: Uri) {
        app.applicationScope.launch {
            val result = runCatching {
                val json = app.backupManager.export()
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("flux indisponible")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { toast(getString(R.string.backup_exported)) }
                    .onFailure { toast(getString(R.string.backup_error, it.message ?: "")) }
            }
        }
    }

    private fun doImport(app: KairosApp, uri: Uri) {
        app.applicationScope.launch {
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("flux indisponible")
                app.backupManager.import(text)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { toast(getString(R.string.backup_imported, it.tasks, it.labels)) }
                    .onFailure { toast(getString(R.string.backup_error, it.message ?: "")) }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
