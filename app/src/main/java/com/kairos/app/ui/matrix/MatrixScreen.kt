@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.kairos.app.ui.matrix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.TextField
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.SavedFilter
import com.kairos.app.data.model.Task
import com.kairos.app.domain.recurrence.Recurrence
import com.kairos.app.domain.sort.TaskSort
import com.kairos.app.ui.SubtaskProgress
import com.kairos.app.ui.pomodoro.PomodoroPhase
import com.kairos.app.ui.pomodoro.PomodoroState
import com.kairos.app.ui.components.DueStatus
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.QuadrantVisual
import com.kairos.app.ui.components.dueStatus
import com.kairos.app.ui.components.formatBlock
import com.kairos.app.ui.components.onAccent
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.quadrantEmptyRes
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.LocalizedOverlay
import com.kairos.app.ui.editor.QuickAddSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ordre des quadrants pour la vue en lanes (haut → bas). */
private val LaneOrder = listOf(Quadrant.DO, Quadrant.SCHEDULE, Quadrant.DELEGATE, Quadrant.ELIMINATE)

/** Espacement vertical entre deux lignes de tâche (entre aussi dans le décalage de l'aperçu de dépôt). */
private val RowSpacing = 6.dp

/** Clé de l'emplacement vide ajouté en fin de liste quand une tâche y arrive d'un autre quadrant. */
private const val DropGapKey = "drop-gap"

/** Clé de la colonne défilante des lanes dans les zones d'auto-défilement. */
private const val LanesScrollKey = "lanes"

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
    onMove: (Task, Quadrant, Long?) -> Unit,
    onReorder: (Task, Long?) -> Unit,
    onRemove: (Task) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenArchive: () -> Unit,
    showGuide: Boolean,
    onOpenGuide: () -> Unit,
    quadrantSort: Int = AppSettings.SORT_MANUAL,
    focusDueWeight: Int = 30,
    onSetQuadrantSort: (Int) -> Unit = {},
    savedFilters: List<SavedFilter> = emptyList(),
    onSaveFilter: (name: String, query: String, labelId: Long?) -> Unit = { _, _, _ -> },
    onMoveMany: (Set<Long>, Quadrant) -> Unit = { _, _ -> },
    onLabelMany: (Set<Long>, Long?) -> Unit = { _, _ -> },
    onCompleteMany: (Set<Long>) -> Unit = {},
    onRemoveMany: (Set<Long>) -> Unit = {},
    onOpenPlanning: () -> Unit = {},
) {
    val active = remember(tasks) { tasks.filter { it.isActive } }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val drag = remember { QuadrantDragState() }
    SideEffect { drag.manualOrder = quadrantSort == AppSettings.SORT_MANUAL }
    var quickAddQuadrant by remember { mutableStateOf<Quadrant?>(null) }

    // Sélection multiple : non vide = mode sélection (tap = cocher, drag/swipe désactivés, barre
    // d'actions en lot). Les tâches qui quittent le board en sortent d'elles-mêmes.
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val activeIds = remember(active) { active.map { it.id }.toSet() }
    if (selection.isNotEmpty() && !activeIds.containsAll(selection)) selection = selection intersect activeIds
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { selection = emptySet() }
    val toggleSelect: (Task) -> Unit = { t ->
        selection = if (t.id in selection) selection - t.id else selection + t.id
    }
    val pinnedLabels = remember(labels) { labels.filter { it.pinned } }

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
          if (selecting) {
            SelectionTopBar(
                count = selection.size,
                labels = labels,
                onClear = { selection = emptySet() },
                onSelectAll = { selection = filtered.map { it.id }.toSet() },
                onMove = { onMoveMany(selection, it); selection = emptySet() },
                onLabel = { onLabelMany(selection, it); selection = emptySet() },
                onComplete = { onCompleteMany(selection); selection = emptySet() },
                onRemove = { onRemoveMany(selection); selection = emptySet() },
            )
          } else {
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
                    // Tri des tâches dans les quadrants.
                    SortMenu(current = quadrantSort, onSelect = onSetQuadrantSort)
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
          }
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
                    IconButton(onClick = onOpenPlanning) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.cd_planning))
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
                    tasks = TaskSort.apply(filtered.filter { it.quadrant == quadrant }, quadrantSort, focusDueWeight, now),
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
                    selection = selection,
                    onToggleSelect = toggleSelect,
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
                        savedFilters = savedFilters,
                        onApplyFilter = { f ->
                            val applied = f.query == query && f.labelId == filterLabelId
                            query = if (applied) "" else f.query
                            filterLabelId = if (applied) null else f.labelId
                        },
                        onSaveFilter = { name -> onSaveFilter(name, query.trim(), filterLabelId) },
                    )
                }
                // Étiquettes épinglées : filtres rapides toujours visibles (panneau de recherche fermé).
                AnimatedVisibility(visible = !searching && pinnedLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pinnedLabels.forEach { label ->
                            LabelFilterChip(
                                label = label,
                                selected = filterLabelId == label.id,
                                onClick = { filterLabelId = if (filterLabelId == label.id) null else label.id },
                            )
                        }
                    }
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
                        val lanesScroll = rememberScrollState()
                        DisposableEffect(Unit) { onDispose { drag.scrollAreas.remove(LanesScrollKey) } }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                // Viewport (avant verticalScroll) inscrit pour l'auto-défilement.
                                .onGloballyPositioned {
                                    drag.scrollAreas[LanesScrollKey] = AutoScrollArea(it.boundsInRoot(), lanesScroll)
                                }
                                .verticalScroll(lanesScroll)
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
                                Quadrant.DO to Quadrant.SCHEDULE,
                                Quadrant.DELEGATE to Quadrant.ELIMINATE,
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
            DragEffects(drag = drag)
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

/**
 * Effets du drag isolés dans leur propre composable (pour ne pas recomposer tout l'écran) :
 * auto-défilement près des bords pendant le geste, et filet de sécurité qui lève l'aperçu figé après
 * un dépôt si la base n'émet pas de nouvel ordre.
 */
@Composable
private fun DragEffects(drag: QuadrantDragState) {
    val density = LocalDensity.current
    if (drag.isDragging) {
        LaunchedEffect(Unit) {
            with(density) { drag.autoScroll(edge = 56.dp.toPx(), maxSpeed = 1100.dp.toPx()) }
        }
    }
    if (drag.isSettling) {
        LaunchedEffect(Unit) {
            delay(1_000)
            drag.clearPreview()
        }
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
    onMove: (Task, Quadrant, Long?) -> Unit,
    onReorder: (Task, Long?) -> Unit,
    onRemove: (Task) -> Unit,
    selection: Set<Long>,
    onToggleSelect: (Task) -> Unit,
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
    SideEffect {
        drag.quadrantOrder[quadrant] = orderIds
        drag.onOrderPublished()
    }
    // Aperçu du dépôt : un emplacement = hauteur de la ligne saisie + espacement entre lignes.
    val density = LocalDensity.current
    val slotPx = (drag.preview?.rowHeight ?: 0f) + with(density) { RowSpacing.toPx() }
    val trailingGap = with(density) { drag.trailingGap(quadrant, orderIds).toDp() }

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
                offsetY = drag.slotShift(task.id, quadrant, orderIds) * slotPx,
                hidden = drag.isHidden(task.id, quadrant, orderIds),
                onOpen = { onOpenTask(task) },
                onComplete = { onComplete(task) },
                onMove = { target, beforeId -> onMove(task, target, beforeId) },
                onReorder = { onReorder(task, it) },
                onRemove = { onRemove(task) },
                selecting = selection.isNotEmpty(),
                selected = task.id in selection,
                onToggleSelect = { onToggleSelect(task) },
            )
        }
        if (tasks.isEmpty()) {
            EmptyQuadrant(quadrant = quadrant, visual = visual, onAdd = onAdd)
        } else if (scrollableList) {
            val listState = rememberLazyListState()
            // Liste inscrite pour l'auto-défilement pendant un drag (viewport en repère racine).
            DisposableEffect(quadrant) { onDispose { drag.scrollAreas.remove(quadrant) } }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(RowSpacing),
                modifier = Modifier.onGloballyPositioned {
                    drag.scrollAreas[quadrant] = AutoScrollArea(it.boundsInRoot(), listState, quadrant)
                },
            ) {
                // animateItem : le réordonnancement intra-quadrant (et l'ajout/retrait) glisse en
                // douceur au lieu de sauter d'une position à l'autre. Sauf au **dépôt** d'un drag : les
                // lignes sont déjà visuellement à leur nouvelle place (aperçu décalé), la nouvelle mise
                // en page doit s'appliquer telle quelle — sinon elles repartiraient de l'ancienne
                // position, et la tâche quittant ce quadrant laisserait un fantôme en fondu.
                val settling = drag.isSettling
                items(tasks, key = { it.id }) { task ->
                    Box(
                        modifier = if (settling) {
                            Modifier
                        } else {
                            Modifier.animateItem()
                        },
                    ) { rowContent(task) }
                }
                if (trailingGap > 0.dp) {
                    item(key = DropGapKey) { Spacer(Modifier.height(trailingGap)) }
                }
            }
        } else {
            // key() par tâche : l'état de swipe (remember { SwipeToDismissBoxState(...) }) suit la
            // tâche et non sa position. Sans ça, retirer une ligne fait « glisser » la suivante dans
            // la position libérée, qui hérite de l'état de swipe → carte fantôme (fond archive/coche)
            // au lieu de la vraie tâche.
            Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
                tasks.forEach { task -> key(task.id) { rowContent(task) } }
                if (trailingGap > 0.dp) Spacer(Modifier.height(trailingGap))
            }
        }
    }
}

/** Puce de filtre d'étiquette : pastille en tête, couleur de la pastille une fois sélectionnée. */
@Composable
private fun LabelFilterChip(label: Label, selected: Boolean, onClick: () -> Unit) {
    val swatch = label.colorHex.toColor()
    KairosFilterChip(
        selected = selected,
        onClick = onClick,
        label = label.name,
        selectedContainerColor = swatch,
        leadingIcon = {
            if (label.pinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = if (selected) onAccent(swatch) else swatch,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (selected) onAccent(swatch) else swatch, CircleShape),
                )
            }
        },
    )
}

/** Menu de tri des quadrants (icône de la barre du haut), option courante cochée. */
@Composable
private fun SortMenu(current: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LocalizedOverlay {
                Text(
                    stringResource(R.string.sort_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                listOf(
                    AppSettings.SORT_MANUAL to R.string.sort_manual,
                    AppSettings.SORT_DUE to R.string.sort_due,
                    AppSettings.SORT_PRIORITY to R.string.sort_priority,
                    AppSettings.SORT_CREATED to R.string.sort_created,
                    AppSettings.SORT_ALPHA to R.string.sort_alpha,
                ).forEach { (value, res) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(res)) },
                        trailingIcon = {
                            if (value == current) Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                        onClick = { open = false; onSelect(value) },
                    )
                }
            }
        }
    }
}

/**
 * Barre contextuelle du mode sélection : nombre de tâches cochées + actions en lot (tout
 * sélectionner, déplacer, étiqueter, terminer, retirer).
 */
@Composable
private fun SelectionTopBar(
    count: Int,
    labels: List<Label>,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: (Quadrant) -> Unit,
    onLabel: (Long?) -> Unit,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
) {
    var moveOpen by remember { mutableStateOf(false) }
    var labelOpen by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_selection))
            }
        },
        title = { Text(stringResource(R.string.selection_count, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.cd_select_all))
            }
            Box {
                IconButton(onClick = { moveOpen = true }) {
                    Icon(Icons.Rounded.DriveFileMove, contentDescription = stringResource(R.string.move_to))
                }
                DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                    LocalizedOverlay {
                        Quadrant.entries.forEach { target ->
                            val tv = quadrantVisual(target)
                            DropdownMenuItem(
                                text = { Text(tv.verb) },
                                leadingIcon = { Icon(tv.icon, contentDescription = null, tint = tv.accent) },
                                onClick = { moveOpen = false; onMove(target) },
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { labelOpen = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = stringResource(R.string.cd_set_label))
                }
                DropdownMenu(expanded = labelOpen, onDismissRequest = { labelOpen = false }) {
                    LocalizedOverlay {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.label_none)) },
                            onClick = { labelOpen = false; onLabel(null) },
                        )
                        labels.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label.name) },
                                leadingIcon = {
                                    Box(Modifier.size(12.dp).background(label.colorHex.toColor(), CircleShape))
                                },
                                onClick = { labelOpen = false; onLabel(label.id) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onComplete) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.cd_mark_done))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Inventory2, contentDescription = stringResource(R.string.action_remove))
            }
        },
    )
}

/**
 * Panneau de recherche + filtre par étiquette (transversal aux quadrants). Champ texte (titre/note),
 * filtres enregistrés (réappliqués d'un tap) et rangée de puces d'étiquettes défilante (« Toutes » +
 * une par étiquette, avec sa pastille). L'icône signet du champ enregistre le filtre courant.
 */
@Composable
private fun FilterPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    labels: List<Label>,
    filterLabelId: Long?,
    onFilterLabel: (Long?) -> Unit,
    savedFilters: List<SavedFilter>,
    onApplyFilter: (SavedFilter) -> Unit,
    onSaveFilter: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    val canSave = query.isNotBlank() || filterLabelId != null
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
                Row {
                    if (canSave) {
                        IconButton(onClick = { naming = true }) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.filter_save))
                        }
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                }
            },
            shape = KairosFieldShape,
            colors = kairosFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (savedFilters.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                savedFilters.forEach { filter ->
                    KairosFilterChip(
                        selected = filter.query == query && filter.labelId == filterLabelId,
                        onClick = { onApplyFilter(filter) },
                        label = filter.name,
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KairosFilterChip(
                    selected = filterLabelId == null,
                    onClick = { onFilterLabel(null) },
                    label = stringResource(R.string.filter_all),
                )
                labels.forEach { label ->
                    LabelFilterChip(
                        label = label,
                        selected = filterLabelId == label.id,
                        onClick = { onFilterLabel(if (filterLabelId == label.id) null else label.id) },
                    )
                }
            }
        }
    }

    if (naming) {
        var name by remember { mutableStateOf(query.trim()) }
        // Strings résolues hors du dialog (fenêtre détachée qui perd l'override de langue in-app).
        val dialogTitle = stringResource(R.string.filter_save)
        val nameLabel = stringResource(R.string.filter_name)
        val saveLabel = stringResource(R.string.action_save)
        val cancelLabel = stringResource(R.string.action_cancel)
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(dialogTitle) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    shape = KairosFieldShape,
                    colors = kairosFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { naming = false; onSaveFilter(name.trim()) },
                ) { Text(saveLabel) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(cancelLabel) } },
        )
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
    offsetY: Float,
    hidden: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onMove: (Quadrant, Long?) -> Unit,
    onReorder: (Long?) -> Unit,
    onRemove: () -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Aperçu du dépôt : la ligne glisse pour ouvrir la place de la tâche saisie (ou refermer son
    // trou). Coupé net au dépôt (isSettling) : la vraie mise en page prend alors le relais pile à
    // l'endroit où la ligne est affichée.
    val shift by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = if (drag.isSettling) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "drop-shift",
    )

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
        modifier = Modifier
            // Bornes mesurées AVANT le décalage : le point d'insertion se calcule sur les positions
            // réelles, sinon les lignes qui s'écartent déplaceraient la cible (oscillation).
            .onGloballyPositioned { drag.itemBounds[task.id] = it.boundsInRoot() }
            .graphicsLayer {
                translationY = shift
                // Ligne saisie masquée : l'overlay la représente, son emplacement reste vide.
                alpha = if (hidden) 0f else 1f
            }
            .clip(RoundedCornerShape(12.dp)),
        onDismiss = onDismiss,
        // Mode sélection : pas de swipe (un tap coche, un glissement ne doit rien archiver).
        gesturesEnabled = !selecting,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection, accent) },
    ) {
        TaskRowContent(
            task, label, progress, accent, urgencyThresholdHours, now, drag,
            onOpen = if (selecting) onToggleSelect else onOpen,
            onComplete = onComplete,
            onMove = onMove,
            onReorder = onReorder,
            onRemove = onRemove,
            selecting = selecting,
            selected = selected,
            onToggleSelect = onToggleSelect,
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
    onMove: (Quadrant, Long?) -> Unit,
    onReorder: (Long?) -> Unit,
    onRemove: () -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val currentSelecting by rememberUpdatedState(selecting)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var rowHeight by remember { mutableStateOf(0f) }
    // Le pointerInput est keyé sur l'id seul (pour ne pas couper un geste en cours) : on lit la
    // tâche et les callbacks via rememberUpdatedState pour ne jamais agir sur une version périmée.
    val currentTask by rememberUpdatedState(task)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnReorder by rememberUpdatedState(onReorder)
    // Épinglage pendant le drag : dans une LazyColumn, l'auto-défilement peut faire sortir la ligne
    // saisie de l'écran ; épinglée, elle reste composée et son geste n'est pas annulé.
    val pinnable by rememberUpdatedState(LocalPinnableContainer.current)
    var pinHandle by remember { mutableStateOf<PinnableContainer.PinnedHandle?>(null) }
    val unpin = {
        pinHandle?.release()
        pinHandle = null
    }
    // Ligne sortie de la composition (défilement, filtre, retrait) : on retire ses bornes, sinon
    // son rectangle périmé fausserait le point d'insertion.
    DisposableEffect(task.id) {
        onDispose {
            drag.itemBounds.remove(task.id)
            unpin()
        }
    }
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
        when {
            selected -> accent.copy(alpha = if (darkTheme) 0.38f else 0.50f).compositeOver(surface)
            pulse -> accent.copy(alpha = pulseAlpha).compositeOver(surface)
            else -> surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "task-pulse",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                rowHeight = it.size.height.toFloat()
            }
            // Clip AVANT le clickable : le ripple/halo du long-press épouse les coins arrondis
            // (fini le carré d'ombre qui dénotait) et la ligne glisse proprement au-dessus du fond.
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onOpen)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { touch ->
                        // En mode sélection, l'appui long coche/décoche au lieu de saisir la ligne.
                        if (currentSelecting) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelect()
                        } else if (drag.start(currentTask, origin + touch, rowHeight)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pinHandle = pinnable?.pin()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // Position absolue (origine courante de la ligne + doigt en local) : reste
                        // juste même si la ligne a bougé sous le doigt pendant l'auto-défilement.
                        // Petit retour haptique à chaque changement de quadrant survolé.
                        if (drag.moveTo(task.id, origin + change.position)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        unpin()
                        when (val result = drag.end(task.id)) {
                            is DragResult.Move -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnMove(result.target, result.beforeId)
                            }
                            is DragResult.Reorder -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnReorder(result.beforeId)
                            }
                            null -> Unit
                        }
                    },
                    onDragCancel = {
                        unpin()
                        drag.cancel(task.id)
                    },
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
                (progress != null && progress.total > 0) || task.dueAt != null || task.blockStart != null
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
                    if (task.blockStart != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = stringResource(R.string.cd_planned),
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                formatBlock(task.blockStart, task.blockEnd ?: task.blockStart),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
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
        if (selecting) {
            IconButton(onClick = onToggleSelect, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(if (selected) R.string.cd_deselect else R.string.cd_select),
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else Box {
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
                            onMove(target, null)
                        },
                    )
                }
                HorizontalDivider()
                // Entrée du mode sélection multiple (cette tâche cochée d'office).
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select)) },
                    leadingIcon = { Icon(Icons.Rounded.LibraryAddCheck, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSelect()
                    },
                )
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
