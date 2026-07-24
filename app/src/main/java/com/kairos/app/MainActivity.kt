package com.kairos.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairos.app.data.model.AppSettings
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
                CompositionLocalProvider(LocalAccentPalette provides palette) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (loaded != null && !settings.onboardingDone) {
                            OnboardingScreen(
                                onDone = { scope.launch { settingsRepository.setOnboardingDone(true) } },
                            )
                        } else if (loaded != null) {
                            KairosNavHost(
                                factory = factory,
                                settings = settings,
                                onSetThemeMode = { scope.launch { settingsRepository.setThemeMode(it) } },
                                onSetDynamicColor = { scope.launch { settingsRepository.setDynamicColor(it) } },
                                onSetPalette = { scope.launch { settingsRepository.setPalette(it) } },
                                onSetCustomPalette = { d, s, dg, e ->
                                    scope.launch { settingsRepository.setCustomPalette(d, s, dg, e) }
                                },
                                onSetUrgencyThreshold = { scope.launch { settingsRepository.setUrgencyThreshold(it) } },
                                onSetSwimLanes = { scope.launch { settingsRepository.setSwimLanes(it) } },
                                onSetArchivePurgeDays = { scope.launch { settingsRepository.setArchivePurgeDays(it) } },
                                onSetPomodoroStyle = { scope.launch { settingsRepository.setPomodoroStyle(it) } },
                                onSetPomodoroDurations = { f, s, l, c ->
                                    scope.launch { settingsRepository.setPomodoroDurations(f, s, l, c) }
                                },
                                onExport = { exportLauncher.launch("kairos-backup.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json")) },
                                pomodoroLaunch = pomodoroLaunch.intValue,
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

    /** Une notification Pomodoro demande d'ouvrir l'écran : on incrémente le signal observé. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PomodoroNotifications.ACTION_OPEN_POMODORO) {
            pomodoroLaunch.intValue++
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
