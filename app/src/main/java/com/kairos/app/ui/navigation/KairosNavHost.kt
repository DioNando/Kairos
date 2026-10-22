package com.kairos.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.domain.ai.Classification
import com.kairos.app.domain.ai.EnhancedTask
import com.kairos.app.ui.KairosViewModel
import com.kairos.app.ui.archive.ArchiveScreen
import com.kairos.app.ui.editor.TaskEditorScreen
import com.kairos.app.ui.focus.FocusScreen
import com.kairos.app.ui.guide.GuideScreen
import com.kairos.app.ui.labels.LabelsScreen
import com.kairos.app.ui.matrix.MatrixScreen
import com.kairos.app.ui.pomodoro.PomodoroViewModel
import com.kairos.app.ui.pomodoro.PomodoroScreen
import com.kairos.app.ui.settings.SettingsScreen

private object Routes {
    const val MATRIX = "matrix"
    const val FOCUS = "focus"
    const val LABELS = "labels"
    const val SETTINGS = "settings"
    const val POMODORO = "pomodoro"
    const val ARCHIVE = "archive"
    const val GUIDE = "guide"
    const val EDITOR = "editor?taskId={taskId}&quadrant={quadrant}&title={title}"
}

@Composable
fun KairosNavHost(
    factory: ViewModelProvider.Factory,
    settings: AppSettings,
    aiEnabled: Boolean,
    geminiKey: String,
    openRouterKey: String,
    reformulateTask: suspend (title: String, note: String, subtasks: List<String>) -> Result<EnhancedTask>,
    suggestClassify: suspend (title: String, note: String) -> Result<Classification>,
    suggestSubtasks: suspend (title: String, note: String) -> Result<List<String>>,
    onSetAiProvider: (Int) -> Unit,
    onSetAiKey: (provider: Int, key: String) -> Unit,
    onTestAi: suspend (provider: Int) -> Result<Unit>,
    onSetThemeMode: (Int) -> Unit,
    onSetLanguage: (Int) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetPalette: (Int) -> Unit,
    onSetCustomPalette: (doColor: String, schedule: String, delegate: String, eliminate: String) -> Unit,
    onSetUrgencyThreshold: (Int) -> Unit,
    onSetSwimLanes: (Boolean) -> Unit,
    onSetArchivePurgeDays: (Int) -> Unit,
    onSetShowGuide: (Boolean) -> Unit,
    onSetPomodoroStyle: (Boolean) -> Unit,
    onSetPomodoroDurations: (focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onSetReminderLead: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    /** Signal (compteur) d'ouverture directe du Pomodoro depuis une notification ou un raccourci. */
    pomodoroLaunch: Int = 0,
    /** Raccourcis d'application : compteurs déclencheurs (nouvelle tâche / Focus). */
    newTaskLaunch: Int = 0,
    focusLaunch: Int = 0,
    /** Tâche à ouvrir depuis un rappel (id + compteur déclencheur). */
    openTaskId: Long = 0L,
    openTaskLaunch: Int = 0,
) {
    val viewModel: KairosViewModel = viewModel(factory = factory)
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val subtaskProgress by viewModel.subtaskProgress.collectAsStateWithLifecycle()
    // ViewModel Pomodoro hissé : l'état alimente le bandeau de la matrice ET l'écran dédié.
    val pomodoroViewModel: PomodoroViewModel = viewModel(factory = factory)
    val pomodoroState by pomodoroViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Ouvre l'écran Pomodoro quand une notification/un raccourci le demande (chaque intent incrémente).
    LaunchedEffect(pomodoroLaunch) {
        if (pomodoroLaunch > 0) navController.navigate(Routes.POMODORO)
    }

    // Raccourci « Nouvelle tâche » : ouvre l'éditeur de création (quadrant Faire par défaut).
    LaunchedEffect(newTaskLaunch) {
        if (newTaskLaunch > 0) {
            navController.navigate("editor?taskId=0&quadrant=${Quadrant.DO.ordinal}&title=")
        }
    }

    // Raccourci « Focus » : ouvre l'écran « Et maintenant ? ».
    LaunchedEffect(focusLaunch) {
        if (focusLaunch > 0) navController.navigate(Routes.FOCUS)
    }

    // Ouvre l'éditeur d'une tâche depuis un rappel d'échéance.
    LaunchedEffect(openTaskLaunch) {
        if (openTaskLaunch > 0 && openTaskId > 0L) {
            navController.navigate("editor?taskId=$openTaskId")
        }
    }

    // L'éditeur est une **page** dédiée (route EDITOR) : nav = retour natif + pas de saccadement.
    fun openNew(quadrant: Quadrant, initialTitle: String = "") {
        navController.navigate("editor?taskId=0&quadrant=${quadrant.ordinal}&title=${Uri.encode(initialTitle)}")
    }

    fun openExisting(task: Task) {
        navController.navigate("editor?taskId=${task.id}")
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MATRIX,
        // Transitions en fondu (pas de glissement) entre toutes les destinations.
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Routes.MATRIX) {
            MatrixScreen(
                tasks = tasks,
                labels = labels,
                subtaskProgress = subtaskProgress,
                pomodoro = pomodoroState,
                urgencyThresholdHours = settings.urgencyThresholdHours,
                now = System.currentTimeMillis(),
                swimLanes = settings.swimLanes,
                onQuickAdd = { title, q ->
                    viewModel.saveTask(Task(title = title, important = q.important, urgent = q.urgent))
                },
                onOpenEditor = { q, title -> openNew(q, title) },
                onOpenTask = { openExisting(it) },
                onComplete = { viewModel.setCompleted(it.id, true) },
                onMove = { task, quadrant -> viewModel.moveTo(task.id, quadrant) },
                onReorder = { task, index -> viewModel.reorder(task.id, index) },
                onRemove = { viewModel.removeTask(it.id) },
                onOpenFocus = { navController.navigate(Routes.FOCUS) },
                onOpenLabels = { navController.navigate(Routes.LABELS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPomodoro = { navController.navigate(Routes.POMODORO) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                showGuide = settings.showGuide,
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
            )
        }
        composable(Routes.FOCUS) {
            FocusScreen(
                tasks = tasks,
                labels = labels,
                onBack = { navController.popBackStack() },
                onComplete = { viewModel.setCompleted(it.id, true) },
                onOpenTask = { openExisting(it) },
            )
        }
        composable(Routes.LABELS) {
            LabelsScreen(
                labels = labels,
                onBack = { navController.popBackStack() },
                onSave = { viewModel.saveLabel(it) },
                onDelete = { viewModel.deleteLabel(it) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onSetThemeMode = onSetThemeMode,
                onSetLanguage = onSetLanguage,
                onSetDynamicColor = onSetDynamicColor,
                onSetPalette = onSetPalette,
                onSetCustomPalette = onSetCustomPalette,
                onSetUrgencyThreshold = onSetUrgencyThreshold,
                onSetSwimLanes = onSetSwimLanes,
                onSetArchivePurgeDays = onSetArchivePurgeDays,
                showGuide = settings.showGuide,
                onSetShowGuide = onSetShowGuide,
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
                onSetPomodoroStyle = onSetPomodoroStyle,
                onSetPomodoroDurations = onSetPomodoroDurations,
                onSetRemindersEnabled = onSetRemindersEnabled,
                onSetReminderLead = onSetReminderLead,
                geminiKey = geminiKey,
                openRouterKey = openRouterKey,
                onSetAiProvider = onSetAiProvider,
                onSetAiKey = onSetAiKey,
                onTestAi = onTestAi,
                onExport = onExport,
                onImport = onImport,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.POMODORO) {
            PomodoroScreen(
                viewModel = pomodoroViewModel,
                tasks = tasks,
                liquid = settings.pomodoroLiquid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                tasks = tasks,
                labels = labels,
                onBack = { navController.popBackStack() },
                onRestore = { viewModel.restoreFromArchive(it.id) },
                onDelete = { viewModel.deleteTask(it.id) },
                onClearAll = { viewModel.clearArchive() },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("quadrant") { type = NavType.IntType; defaultValue = Quadrant.SCHEDULE.ordinal },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val taskId = args?.getLong("taskId") ?: 0L
            val quadrantOrdinal = args?.getInt("quadrant") ?: Quadrant.SCHEDULE.ordinal
            val initialTitle = args?.getString("title").orEmpty()
            // taskId = 0 → création (aucune tâche ne correspond).
            val editorTask = tasks.firstOrNull { it.id == taskId }
            val editorSubtasks = subtasks.filter { it.taskId == taskId }.sortedBy { it.position }
            TaskEditorScreen(
                task = editorTask,
                defaultQuadrant = Quadrant.entries.getOrElse(quadrantOrdinal) { Quadrant.SCHEDULE },
                labels = labels,
                urgencyThresholdHours = settings.urgencyThresholdHours,
                now = System.currentTimeMillis(),
                initialTitle = initialTitle,
                initialSubtasks = editorSubtasks,
                aiEnabled = aiEnabled,
                onReformulateTask = reformulateTask,
                onSuggestClassify = suggestClassify,
                onSuggestSubtasks = suggestSubtasks,
                onBack = { navController.popBackStack() },
                onSave = { task, subs -> viewModel.saveTask(task, subs); navController.popBackStack() },
                // Depuis l'éditeur, « retirer » = archiver (réversible), pas de suppression dure.
                onDelete = { viewModel.removeTask(it.id); navController.popBackStack() },
            )
        }
    }
}
