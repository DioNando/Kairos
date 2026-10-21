@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.kairos.app.ui.matrix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.TextField
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.text.style.TextAlign
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
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.QuadrantVisual
import com.kairos.app.ui.components.dueStatus
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.quadrantEmptyRes
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.LocalizedOverlay
import com.kairos.app.ui.editor.QuickAddSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ordre des quadrants pour la vue en lanes (haut → bas). */
private val LaneOrder = listOf(Quadrant.DO, Quadrant.SCHEDULE, Quadrant.DELEGATE, Quadrant.ELIMINATE)

/**
 * Écran d'accueil : la matrice. Vue **grille 2×2** ou **swim lanes** (réglable). On **glisse vers la
 * droite** pour terminer, on **glisse vers la gauche** pour **retirer** (la tâche part à l'archive,
 * d'où on peut la restaurer ou la supprimer définitivement — plus de suppression dure ni de
 * snackbar « Annuler » ici). On **glisse** une ligne (appui long) vers un autre quadrant ou pour la
 * réordonner, ou on utilise le menu ⋮ (chemin accessible). Navigation + ajout en **barre basse**.
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
    onMove: (Task, Quadrant) -> Unit,
    onReorder: (Task, Int) -> Unit,
    onRemove: (Task) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenArchive: () -> Unit,
    showGuide: Boolean,
    onOpenGuide: () -> Unit,
) {
    val active = remember(tasks) { tasks.filter { it.isActive } }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val drag = remember { QuadrantDragState() }
    var quickAddQuadrant by remember { mutableStateOf<Quadrant?>(null) }

    // Recherche + filtre par étiquette (transversal aux quadrants). Le panneau se déplie via l'icône
    // de recherche ; les compteurs et cellules reflètent alors le sous-ensemble filtré.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterLabelId by remember { mutableStateOf<Long?>(null) }
    val filtered = remember(active, query, filterLabelId) {
        val q = query.trim()
        active.filter { t ->
            (filterLabelId == null || t.labelId == filterLabelId) &&
                (q.isBlank() || t.title.contains(q, ignoreCase = true) || t.note.contains(q, ignoreCase = true))
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
                    // Recherche + filtre par étiquette (déplie le panneau).
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) {
                            query = ""
                            filterLabelId = null
                        }
                    }) {
                        Icon(
                            if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search),
                        )
                    }
                    // Guide à gauche de l'archive (masquable via les réglages).
                    if (showGuide) {
                        IconButton(onClick = onOpenGuide) {
                            Icon(Icons.Rounded.HelpOutline, contentDescription = stringResource(R.string.cd_guide))
                        }
                    }
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Quand le clavier de recherche s'ouvre, la zone se redimensionne au-dessus de lui :
                // la grille 2×2 (non scrollable) se comprime et la rangée du bas (Planifier/Supprimer)
                // reste visible au lieu d'être masquée derrière le clavier. consumeWindowInsets évite
                // de cumuler l'inset déjà pris en compte par le Scaffold.
                .consumeWindowInsets(padding)
                .imePadding()
                .onGloballyPositioned { drag.rootOffset = it.positionInRoot() },
        ) {
            val cellContent: @Composable (Quadrant, Modifier, Boolean) -> Unit = { quadrant, modifier, lazy ->
                QuadrantCell(
                    quadrant = quadrant,
                    tasks = filtered.filter { it.quadrant == quadrant },
                    labelsById = labelsById,
                    subtaskProgress = subtaskProgress,
                    urgencyThresholdHours = urgencyThresholdHours,
                    now = now,
                    drag = drag,
                    scrollableList = lazy,
                    onAdd = { quickAddQuadrant = quadrant },
                    onOpenTask = onOpenTask,
                    onComplete = onComplete,
                    onMove = onMove,
                    onReorder = onReorder,
                    onRemove = onRemove,
                    modifier = modifier,
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Panneau recherche + filtre étiquette (déplié via l'icône de la barre du haut).
                AnimatedVisibility(visible = searching) {
                    FilterPanel(
                        query = query,
                        onQueryChange = { query = it },
                        labels = labels,
                        filterLabelId = filterLabelId,
                        onFilterLabel = { filterLabelId = it },
                    )
                }
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
    onRemove: (Task) -> Unit,
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
                // Plafonne à « 999+ » : un compteur à 4+ chiffres écraserait le verbe du quadrant.
                if (tasks.size > 999) "999+" else tasks.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
                onRemove = { onRemove(task) },
            )
        }
        if (tasks.isEmpty()) {
            EmptyQuadrant(quadrant = quadrant, visual = visual, onAdd = onAdd)
        } else if (scrollableList) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // animateItem : le réordonnancement intra-quadrant (et l'ajout/retrait) glisse en
                // douceur au lieu de sauter d'une position à l'autre.
                items(tasks, key = { it.id }) { task ->
                    Box(modifier = Modifier.animateItem()) { rowContent(task) }
                }
            }
        } else {
            // key() par tâche : l'état de swipe (remember { SwipeToDismissBoxState(...) }) suit la
            // tâche et non sa position. Sans ça, retirer une ligne fait « glisser » la suivante dans
            // la position libérée, qui hérite de l'état de swipe → carte fantôme (fond archive/coche)
            // au lieu de la vraie tâche.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tasks.forEach { task -> key(task.id) { rowContent(task) } }
            }
        }
    }
}

/**
 * Panneau de recherche + filtre par étiquette (transversal aux quadrants). Champ texte (titre/note)
 * + rangée de puces d'étiquettes défilante (« Toutes » + une par étiquette, avec sa pastille).
 */
@Composable
private fun FilterPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    labels: List<Label>,
    filterLabelId: Long?,
    onFilterLabel: (Long?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            shape = KairosFieldShape,
            colors = kairosFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KairosFilterChip(
                    selected = filterLabelId == null,
                    onClick = { onFilterLabel(null) },
                    label = stringResource(R.string.filter_all),
                )
                labels.forEach { label ->
                    KairosFilterChip(
                        selected = filterLabelId == label.id,
                        onClick = { onFilterLabel(if (filterLabelId == label.id) null else label.id) },
                        label = label.name,
                        leadingIcon = {
                            Box(modifier = Modifier.size(10.dp).background(label.colorHex.toColor(), CircleShape))
                        },
                    )
                }
            }
        }
    }
}

/**
 * État vide d'un quadrant : message d'encouragement propre au quadrant + petit CTA « + Ajouter ».
 * On n'y répète pas l'icône du quadrant (déjà présente dans l'en-tête juste au-dessus) ; toute la
 * zone est cliquable pour ajouter une tâche (double le « + » de l'en-tête).
 */
@Composable
private fun EmptyQuadrant(quadrant: Quadrant, visual: QuadrantVisual, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAdd)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(quadrantEmptyRes(quadrant)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = visual.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.quadrant_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = visual.accent,
            )
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
    onRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Swipe (API SwipeToDismissBox alpha21, pattern `onDismiss`) : droite = terminer, gauche = retirer.
    // Choix clés qui règlent les bugs successivement rencontrés :
    //  • **`onDismiss`** ne se déclenche qu'**au settle** dans une direction (le geste doit aller au
    //    bout / être un vrai flick) et **une seule fois** par geste → fini le « ça part au moindre
    //    effleurement » et fini l'avance de plusieurs jours d'un coup sur une tâche récurrente.
    //  • État **non sauvegardé** (`remember` + constructeur direct, PAS `rememberSaveable`) : une
    //    tâche re-affichée / **restaurée depuis l'archive** repart TOUJOURS centrée — l'ancien état
    //    sauvegardait `currentValue` = position « dismissée », d'où la ligne figée hors-centre (et
    //    même un re-déclenchement) au retour. (C'est CE point qui règle le bug de restauration, pas
    //    le `reset()` ci-dessous.)
    //  • **`reset()` UNIQUEMENT quand la tâche reste** sur le board, c.-à-d. une **récurrente
    //    terminée** (elle avance sur place) : la box revient au centre et redevient glissable (sinon
    //    settledValue reste « dismissé » et `gesturesEnabled` la verrouille). Pour tout ce qui **part**
    //    (retrait, ou complétion non récurrente) on **ne reset PAS** : la ligne reste glissée hors du
    //    cadre et disparaît par l'animation naturelle de retrait de la liste (`animateItem`), la
    //    suivante remonte — le rendu « la tâche s'enlève et la liste se referme » attendu.
    // onComplete/onRemove/recurring capturés via rememberUpdatedState pour garder `onDismiss` **stable**
    // (le LaunchedEffect interne de la box est keyé dessus → un onDismiss changeant pourrait re-tirer).
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentRecurring by rememberUpdatedState(task.recurrenceType != Recurrence.NONE)
    val dismissState = remember {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { totalDistance -> totalDistance * 0.6f },
        )
    }
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember {
        { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnComplete()
                    // Récurrente : elle reste sur le board → on la recentre. Sinon elle part, on la
                    // laisse glisser dehors (retrait animé de la liste).
                    if (currentRecurring) scope.launch { dismissState.reset() }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnRemove()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        // Clip à la forme de la ligne : le contenu qui glisse et le fond révélé restent DANS la
        // tuile (fini le débordement sous les tuiles voisines / « masqué derrière »).
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        onDismiss = onDismiss,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection, accent) },
    ) {
        TaskRowContent(
            task, label, progress, accent, urgencyThresholdHours, now, drag,
            onOpen = onOpen,
            onComplete = onComplete,
            onMove = onMove,
            onReorder = onReorder,
            onRemove = onRemove,
        )
    }
}

/** Fond révélé par le swipe : coche (droite/terminer) ou archive (gauche/retirer). */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, accent: Color) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    // Le retrait n'est plus destructif (la tâche va à l'archive) → fond tonal neutre + icône
    // d'archive, au lieu de l'ancien rouge « errorContainer » qui évoquait une suppression dure.
    val color = if (direction == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.secondaryContainer
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
                Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
    onRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val beingDragged = drag.dragged?.id == task.id
    val status = dueStatus(task.dueAt, now, urgencyThresholdHours, task.isCompleted)

    // Profondeur tonale : ligne = surfaceContainerHigh ; pulsation d'accent si échéance proche.
    val pulse = status == DueStatus.SOON || status == DueStatus.OVERDUE
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // Sur une surface **claire** l'accent translucide se délave (rose pâle qui ne ressort pas) ; sur
    // une surface **sombre** un alpha modéré suffit déjà à trancher. On monte donc l'alpha en clair
    // pour que la surbrillance « pousse » la tâche en avant dans les deux thèmes. Le thème est
    // déduit de la luminance de la surface (robuste au forçage clair/sombre in-app).
    val darkTheme = surface.luminance() < 0.5f
    val pulseAlpha = if (darkTheme) 0.22f else 0.40f
    val containerColor by animateColorAsState(
        // Surbrillance rendue **opaque** : on compose l'accent translucide SUR la surface, au lieu
        // d'un simple alpha qui laissait le fond du swipe (et son icône) transparaître à travers la
        // ligne pendant le glissement (icônes superposées au contenu).
        if (pulse) accent.copy(alpha = pulseAlpha).compositeOver(surface) else surface,
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
            // Méta en FlowRow : label / récurrence / sous-tâches / échéance passent **à la ligne**
            // au lieu de s'écraser (carte étroite en grille 2×2). Chaque info sur une seule ligne.
            val hasMeta = label != null || task.recurrenceType != Recurrence.NONE ||
                (progress != null && progress.total > 0) || task.dueAt != null
            if (hasMeta) {
                FlowRow(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (label != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(label.colorHex.toColor(), CircleShape))
                            Spacer(Modifier.size(4.dp))
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (task.recurrenceType != Recurrence.NONE) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = stringResource(R.string.cd_recurring),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (progress != null && progress.total > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "${progress.done}/${progress.total}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (task.dueAt != null) {
                        Text(
                            formatDue(task.dueAt),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = when (status) {
                                DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                                DueStatus.SOON -> accent
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
              LocalizedOverlay {
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
                // Retirer : la tâche part à l'archive (réversible), plus de suppression dure ici →
                // pas de confirmation, teinte neutre et icône d'archive (pas la corbeille rouge).
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRemove()
                    },
                )
              }
            }
        }
    }
}
