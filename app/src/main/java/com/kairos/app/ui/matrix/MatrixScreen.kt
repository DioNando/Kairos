@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kairos.app.ui.matrix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.domain.recurrence.Recurrence
import com.kairos.app.ui.SubtaskProgress
import com.kairos.app.ui.pomodoro.PomodoroPhase
import com.kairos.app.ui.pomodoro.PomodoroState
import com.kairos.app.ui.components.DueStatus
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.dueStatus
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.editor.QuickAddSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ordre des quadrants pour la vue en lanes (haut → bas). */
private val LaneOrder = listOf(Quadrant.DO, Quadrant.SCHEDULE, Quadrant.DELEGATE, Quadrant.ELIMINATE)

/**
 * Écran d'accueil : la matrice. Vue **grille 2×2** ou **swim lanes** (réglable). On coche ou on
 * **glisse vers la droite** pour terminer, on **glisse vers la gauche** pour supprimer (avec
 * annulation), on **glisse** une ligne (appui long) vers un autre quadrant ou pour la réordonner,
 * ou on utilise le menu ⋮ (chemin accessible). Navigation + ajout en **barre basse**.
 */
@Composable
fun MatrixScreen(
    tasks: List<Task>,
    labels: List<Label>,
    subtaskProgress: Map<Long, SubtaskProgress>,
    pomodoro: PomodoroState,
    urgencyThresholdHours: Int,
    now: Long,
    swimLanes: Boolean,
    onQuickAdd: (String, Quadrant) -> Unit,
    onOpenEditor: (Quadrant, String) -> Unit,
    onOpenTask: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onUndoComplete: (Task) -> Unit,
    onMove: (Task, Quadrant) -> Unit,
    onReorder: (Task, Int) -> Unit,
    onDelete: (Task) -> Unit,
    onRestore: (Task) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val active = remember(tasks) { tasks.filter { !it.isCompleted } }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val drag = remember { QuadrantDragState() }
    var quickAddQuadrant by remember { mutableStateOf<Quadrant?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val completedMsg = stringResource(R.string.snack_task_completed)
    val deletedMsg = stringResource(R.string.snack_task_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    fun completeWithUndo(task: Task) {
        onComplete(task)
        scope.launch {
            val result = snackbarHostState.showSnackbar(completedMsg, undoLabel)
            if (result == SnackbarResult.ActionPerformed) onUndoComplete(task)
        }
    }

    fun deleteWithUndo(task: Task) {
        onDelete(task)
        scope.launch {
            val result = snackbarHostState.showSnackbar(deletedMsg, undoLabel)
            if (result == SnackbarResult.ActionPerformed) onRestore(task)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = stringResource(R.string.cd_archive))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = onOpenFocus) {
                        Icon(Icons.Rounded.CenterFocusStrong, contentDescription = stringResource(R.string.cd_focus))
                    }
                    IconButton(onClick = onOpenPomodoro) {
                        Icon(Icons.Rounded.Timer, contentDescription = stringResource(R.string.cd_pomodoro))
                    }
                    IconButton(onClick = onOpenLabels) {
                        Icon(Icons.Rounded.Category, contentDescription = stringResource(R.string.cd_labels))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { quickAddQuadrant = Quadrant.SCHEDULE }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_task))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { drag.rootOffset = it.positionInRoot() },
        ) {
            val cellContent: @Composable (Quadrant, Modifier, Boolean) -> Unit = { quadrant, modifier, lazy ->
                QuadrantCell(
                    quadrant = quadrant,
                    tasks = active.filter { it.quadrant == quadrant },
                    labelsById = labelsById,
                    subtaskProgress = subtaskProgress,
                    urgencyThresholdHours = urgencyThresholdHours,
                    now = now,
                    drag = drag,
                    scrollableList = lazy,
                    onAdd = { quickAddQuadrant = quadrant },
                    onOpenTask = onOpenTask,
                    onComplete = { completeWithUndo(it) },
                    onMove = onMove,
                    onReorder = onReorder,
                    onDelete = { deleteWithUndo(it) },
                    modifier = modifier,
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Bandeau Pomodoro : visible tant qu'une session est active, tap = ouvrir l'écran.
                AnimatedVisibility(visible = pomodoro.active) {
                    ActivePomodoroBar(
                        state = pomodoro,
                        onClick = onOpenPomodoro,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (swimLanes) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LaneOrder.forEach { quadrant ->
                                cellContent(quadrant, Modifier.fillMaxWidth(), false)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .padding(top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            listOf(
                                Quadrant.DO to Quadrant.DELEGATE,
                                Quadrant.SCHEDULE to Quadrant.ELIMINATE,
                            ).forEach { (left, right) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    cellContent(left, Modifier.weight(1f).fillMaxSize(), true)
                                    cellContent(right, Modifier.weight(1f).fillMaxSize(), true)
                                }
                            }
                        }
                    }
                }
            }

            DragOverlay(drag = drag, labelsById = labelsById)
        }
    }

    quickAddQuadrant?.let { start ->
        QuickAddSheet(
            startQuadrant = start,
            onDismiss = { quickAddQuadrant = null },
            onQuickAdd = { title, q -> onQuickAdd(title, q); quickAddQuadrant = null },
            onMoreOptions = { title, q -> quickAddQuadrant = null; onOpenEditor(q, title) },
        )
    }
}

@Composable
private fun DragOverlay(drag: QuadrantDragState, labelsById: Map<Long, Label>) {
    val task = drag.dragged ?: return
    val visual = quadrantVisual(task.quadrant)
    val label = task.labelId?.let { labelsById[it] }
    val lift by animateFloatAsState(
        targetValue = if (drag.isDragging) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "drag-lift",
    )
    // Même arrondi que la ligne de tâche (12.dp) ; l'ombre est portée par le graphicsLayer avec la
    // forme + clip → elle épouse les coins arrondis au lieu d'être carrée.
    val overlayShape = RoundedCornerShape(12.dp)
    Surface(
        shape = overlayShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .width(220.dp)
            .offset {
                // pointer est en repère racine ; l'overlay est rendu dans le Box décalé par le
                // padding du Scaffold → on retranche rootOffset pour coller au doigt.
                val local = drag.pointer - drag.rootOffset
                IntOffset(
                    (local.x - 110.dp.toPx()).roundToInt(),
                    (local.y - 24.dp.toPx()).roundToInt(),
                )
            }
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                shadowElevation = 10.dp.toPx()
                shape = overlayShape
                clip = true
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(8.dp).background(visual.accent, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(
                task.title.ifBlank { visual.verb },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (label != null) {
                Spacer(Modifier.size(8.dp))
                Box(modifier = Modifier.size(8.dp).background(label.colorHex.toColor(), CircleShape))
            }
        }
    }
}

/**
 * Bandeau compact de la session Pomodoro en cours (repris de WorkSync) : phase, état, temps
 * restant et barre de progression ondulée M3 Expressive. Tap → ouvre l'écran Pomodoro.
 */
@Composable
private fun ActivePomodoroBar(state: PomodoroState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val phaseLabel = stringResource(
        when (state.phase) {
            PomodoroPhase.FOCUS -> R.string.pomo_focus
            PomodoroPhase.SHORT_BREAK -> R.string.pomo_short
            PomodoroPhase.LONG_BREAK -> R.string.pomo_long
        },
    )
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = onContainer)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainer,
                    )
                    Text(
                        text = stringResource(if (state.isRunning) R.string.pomo_running else R.string.pomo_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                    )
                }
                Text(
                    text = formatClock(state.remainingSeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = onContainer,
                )
            }
            LinearWavyProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = onContainer,
                trackColor = onContainer.copy(alpha = 0.25f),
                amplitude = { 1f },
                wavelength = 26.dp,
            )
        }
    }
}

private fun formatClock(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

@Composable
private fun QuadrantCell(
    quadrant: Quadrant,
    tasks: List<Task>,
    labelsById: Map<Long, Label>,
    subtaskProgress: Map<Long, SubtaskProgress>,
    urgencyThresholdHours: Int,
    now: Long,
    drag: QuadrantDragState,
    scrollableList: Boolean,
    onAdd: () -> Unit,
    onOpenTask: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onMove: (Task, Quadrant) -> Unit,
    onReorder: (Task, Int) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = quadrantVisual(quadrant)
    val isTarget = drag.isDragging && drag.hoverTarget == quadrant
    val bg by animateColorAsState(
        targetValue = visual.accent.copy(alpha = if (isTarget) 0.30f else 0.12f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "quadrant-bg",
    )
    // Publie l'ordre courant des ids pour le calcul de réordonnancement (hors composition).
    val orderIds = tasks.map { it.id }
    SideEffect { drag.quadrantOrder[quadrant] = orderIds }

    Column(
        modifier = modifier
            .onGloballyPositioned { drag.bounds[quadrant] = it.boundsInRoot() }
            .background(bg, RoundedCornerShape(20.dp))
            .padding(10.dp),
    ) {
        // En-tête : icône (pastille morphante = cible de drop pendant un drag) + verbe + compteur + ajout.
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingIconPill(
                icon = visual.icon,
                accent = visual.accent,
                selected = isTarget,
                size = 30.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                visual.verb,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = visual.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                tasks.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_add_in_quadrant, visual.verb),
                    tint = visual.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val rowContent: @Composable (Task) -> Unit = { task ->
            TaskRow(
                task, task.labelId?.let { labelsById[it] }, subtaskProgress[task.id], visual.accent,
                urgencyThresholdHours, now, drag,
                onOpen = { onOpenTask(task) },
                onComplete = { onComplete(task) },
                onMove = { onMove(task, it) },
                onReorder = { onReorder(task, it) },
                onDelete = { onDelete(task) },
            )
        }
        if (tasks.isEmpty()) {
            Text(
                stringResource(R.string.quadrant_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (scrollableList) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tasks, key = { it.id }) { task -> rowContent(task) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tasks.forEach { task -> rowContent(task) }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    label: Label?,
    progress: SubtaskProgress?,
    accent: Color,
    urgencyThresholdHours: Int,
    now: Long,
    drag: QuadrantDragState,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onMove: (Quadrant) -> Unit,
    onReorder: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Swipe : droite = terminer, gauche = supprimer (l'annulation se fait via la snackbar).
    // Seuil relevé à 60 % de la largeur (au lieu de ~50 % par défaut) : le geste doit être franc,
    // fini les déclenchements accidentels au moindre glissement.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.6f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onComplete(); true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDelete(); true
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        // Clip à la forme de la ligne : le contenu qui glisse et le fond révélé restent DANS la
        // tuile (fini le débordement sous les tuiles voisines / « masqué derrière »).
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        backgroundContent = { SwipeBackground(dismissState.dismissDirection, accent) },
    ) {
        TaskRowContent(
            task, label, progress, accent, urgencyThresholdHours, now, drag,
            onOpen = onOpen,
            onComplete = onComplete,
            onMove = onMove,
            onReorder = onReorder,
            onDelete = onDelete,
        )
    }
}

/** Fond révélé par le swipe : coche (droite/terminer) ou corbeille (gauche/supprimer). */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, accent: Color) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    val color = if (direction == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        accent.copy(alpha = 0.30f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        if (direction == SwipeToDismissBoxValue.EndToStart) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent)
        }
    }
}

@Composable
private fun TaskRowContent(
    task: Task,
    label: Label?,
    progress: SubtaskProgress?,
    accent: Color,
    urgencyThresholdHours: Int,
    now: Long,
    drag: QuadrantDragState,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onMove: (Quadrant) -> Unit,
    onReorder: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val beingDragged = drag.dragged?.id == task.id
    val status = dueStatus(task.dueAt, now, urgencyThresholdHours, task.isCompleted)

    // Profondeur tonale : ligne = surfaceContainerHigh ; pulsation d'accent si échéance proche.
    val pulse = status == DueStatus.SOON || status == DueStatus.OVERDUE
    val containerColor by animateColorAsState(
        if (pulse) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "task-pulse",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                drag.itemBounds[task.id] = it.boundsInRoot()
            }
            .graphicsLayer { alpha = if (beingDragged) 0.3f else 1f }
            // Clip AVANT le clickable : le ripple/halo du long-press épouse les coins arrondis
            // (fini le carré d'ombre qui dénotait) et la ligne glisse proprement au-dessus du fond.
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onOpen)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { touch ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.start(task, origin + touch)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.move(amount)
                    },
                    onDragEnd = {
                        when (val result = drag.end()) {
                            is DragResult.Move -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMove(result.target)
                            }
                            is DragResult.Reorder -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReorder(result.targetIndex)
                            }
                            null -> Unit
                        }
                    },
                    onDragCancel = { drag.cancel() },
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label != null) {
                    Box(modifier = Modifier.size(8.dp).background(label.colorHex.toColor(), CircleShape))
                    Spacer(Modifier.size(4.dp))
                    Text(
                        label.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.recurrenceType != Recurrence.NONE) {
                    if (label != null) Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.cd_recurring),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (progress != null && progress.total > 0) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.Rounded.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        "${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.dueAt != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        formatDue(task.dueAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                            DueStatus.SOON -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_task_menu),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.widthIn(min = 220.dp),
            ) {
                Text(
                    stringResource(R.string.move_to),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Quadrant.entries.filter { it != task.quadrant }.forEach { target ->
                    val tv = quadrantVisual(target)
                    DropdownMenuItem(
                        text = { Text(tv.verb) },
                        leadingIcon = { Icon(tv.icon, contentDescription = null, tint = tv.accent) },
                        onClick = {
                            menuOpen = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMove(target)
                        },
                    )
                }
                HorizontalDivider()
                // Marquer terminée : la checkbox de ligne a été retirée (le swipe droite fait déjà
                // le geste) — on garde l'action ici, accessible et juste au-dessus de Supprimer.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_mark_done)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent)
                    },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onComplete()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message, task.title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
