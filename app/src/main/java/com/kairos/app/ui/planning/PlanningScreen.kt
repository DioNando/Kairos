package com.kairos.app.ui.planning

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.calendar.BusySlot
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.ui.components.onAccent
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.LocalizedOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Hauteur d'une heure sur la frise. 80 dp → un créneau de 15 min fait 20 dp : assez pour une ligne
 * de texte compacte ; 45 min laissent la place au titre **et** à l'horaire.
 */
private val HourHeight = 80.dp

/** Gouttière des libellés d'heure, à gauche de la frise. */
private val Gutter = 52.dp

/** Marge au-dessus et au-dessous de la frise : « 00:00 » et « 23:00 » ne sont plus rognés. */
private val TimelineEdge = 14.dp

/** Épaisseur des traits d'heure / de demi-heure de la frise. */
private val LineThickness = 1.dp

/**
 * Espace laissé entre un créneau et les traits qui l'encadrent, **identique en haut et en bas** : le
 * créneau commence juste sous le trait de début et s'arrête à la même distance du trait de fin.
 */
private val BlockGap = 1.5.dp

/** Décalage vertical du haut d'un créneau (sous le trait de début + espace). */
private val BlockTopInset = LineThickness + BlockGap

/** Hauteur affichée d'un créneau de [minutes] : sa durée, moins les espaces haut et bas. */
private fun blockHeight(minutes: Float): Dp = HourHeight * (minutes / 60f) - LineThickness - BlockGap * 2

/** Arrondi des créneaux (tâches, rendez-vous, fantôme de dépôt). */
private val BlockShape = RoundedCornerShape(12.dp)

/** Pas de calage des créneaux (minutes). */
private const val SNAP_MINUTES = 15

private const val MINUTES_PER_DAY = 24 * 60
private const val MILLIS_PER_MINUTE = 60_000L

/** Durées proposées dans le menu d'un créneau (minutes). */
private val Durations = listOf(15, 30, 45, 60, 90, 120)

/**
 * Glisser-déposer du planning : une tâche saisie depuis le bac « À planifier » ou depuis la frise.
 * [pointer] en repère racine ; [grabMinutes] = décalage (minutes) entre le haut du créneau et le
 * doigt, pour que le créneau ne « saute » pas sous le doigt.
 */
private class PlanningDrag {
    var task by mutableStateOf<Task?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grabMinutes by mutableFloatStateOf(0f)
}

/**
 * **Time-blocking** : vue jour en frise horaire. Les créneaux **occupés** des agendas (appareil /
 * iCal, lecture seule) apparaissent en gris ; on y **glisse** les tâches du bac « À planifier »
 * (appui long), on déplace un créneau de la même façon, on le renvoie au bac pour le déplanifier. Un
 * tap sur une tâche du bac la pose sur le **prochain créneau libre** (chemin accessible).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanningScreen(
    tasks: List<Task>,
    labels: List<Label>,
    focusDueWeight: Int,
    calendarConfigured: Boolean,
    loadBusy: suspend (from: Long, to: Long) -> List<BusySlot>,
    onSetBlock: (Task, Long?, Int) -> Unit,
    onOpenTask: (Task) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    val day = LocalDate.ofEpochDay(epochDay)
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val isToday = day == LocalDate.now(zone)

    var loadingBusy by remember { mutableStateOf(false) }
    val busy by produceState(initialValue = emptyList<BusySlot>(), dayStart, calendarConfigured) {
        loadingBusy = calendarConfigured
        value = runCatching { loadBusy(dayStart, dayEnd) }.getOrDefault(emptyList())
        loadingBusy = false
    }
    val timedBusy = remember(busy) { busy.filter { !it.allDay } }
    val allDayBusy = remember(busy) { busy.filter { it.allDay } }

    val active = remember(tasks) { tasks.filter { it.isActive } }
    val planned = remember(active, dayStart) {
        active.filter { t -> t.blockStart != null && t.blockStart in dayStart until dayEnd }
    }
    val tray = remember(active, focusDueWeight) {
        FocusSelector.rank(active.filter { it.blockStart == null }, focusDueWeight)
    }
    val labelsById = remember(labels) { labels.associateBy { it.id } }

    val drag = remember { PlanningDrag() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var timelineTop by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var trayBounds by remember { mutableStateOf(Rect.Zero) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val haptic = LocalHapticFeedback.current

    // Ouverture : on cale la frise sur l'heure courante (aujourd'hui) ou sur 8:00.
    LaunchedEffect(epochDay) {
        val minutes = if (isToday) {
            Instant.now().atZone(zone).let { it.hour * 60 + it.minute } - 60
        } else {
            8 * 60
        }
        scroll.scrollTo((minutes.coerceAtLeast(0) / 60f * hourPx).roundToInt())
    }

    fun minutesAt(y: Float): Float = (y - timelineTop) / hourPx * 60f

    fun snapped(minutes: Float, duration: Int): Int =
        ((minutes / SNAP_MINUTES).roundToInt() * SNAP_MINUTES).coerceIn(0, MINUTES_PER_DAY - duration)

    /** Minute de début visée par le drag en cours (null si le doigt n'est pas sur la frise). */
    fun dropMinutes(): Int? {
        val task = drag.task ?: return null
        if (!viewport.contains(drag.pointer)) return null
        return snapped(minutesAt(drag.pointer.y) - drag.grabMinutes, task.blockDuration)
    }

    fun endDrag() {
        val task = drag.task ?: return
        val target = dropMinutes()
        when {
            target != null -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetBlock(task, dayStart + target * MILLIS_PER_MINUTE, task.blockDuration)
            }
            task.blockStart != null && trayBounds.contains(drag.pointer) -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetBlock(task, null, 0)
            }
        }
        drag.task = null
    }

    /** Prochain créneau libre du jour (après maintenant si aujourd'hui, sinon dès 9:00). */
    fun nextFreeSlot(duration: Int): Int? {
        val from = if (isToday) {
            Instant.now().atZone(zone).let { it.hour * 60 + it.minute }
                .let { (it + SNAP_MINUTES - 1) / SNAP_MINUTES * SNAP_MINUTES }
        } else {
            9 * 60
        }
        val taken = timedBusy.map { ((it.start - dayStart) / MILLIS_PER_MINUTE).toInt() to ((it.end - dayStart) / MILLIS_PER_MINUTE).toInt() } +
            planned.map { ((it.blockStart!! - dayStart) / MILLIS_PER_MINUTE).toInt() to ((it.blockEnd!! - dayStart) / MILLIS_PER_MINUTE).toInt() }
        var start = from
        while (start + duration <= MINUTES_PER_DAY) {
            val end = start + duration
            val clash = taken.firstOrNull { (s, e) -> s < end && e > start }
            if (clash == null) return start
            start = ((clash.second + SNAP_MINUTES - 1) / SNAP_MINUTES * SNAP_MINUTES).coerceAtLeast(start + SNAP_MINUTES)
        }
        return null
    }

    // Auto-défilement de la frise quand le doigt approche de son bord pendant un drag.
    if (drag.task != null) {
        LaunchedEffect(Unit) { autoScroll(scroll, drag, { viewport }, with(density) { 56.dp.toPx() }) }
    }

    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.planning_title), fontWeight = FontWeight.Bold)
                        // Date courte (« ven. 30 oct. »), précédée de « Aujourd'hui » le jour même.
                        val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day)
                        Text(
                            if (isToday) "${stringResource(R.string.cd_today)} · $shortDate"
                            else shortDate.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Lecture des agendas en cours : indicateur M3 Expressive (forme qui morphe).
                    if (loadingBusy) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { epochDay-- }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_day))
                    }
                    IconButton(
                        onClick = { epochDay = LocalDate.now(zone).toEpochDay() },
                        enabled = !isToday,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.cd_today))
                    }
                    IconButton(onClick = { epochDay++ }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { rootOffset = it.positionInRoot() },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!calendarConfigured) {
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                stringResource(R.string.planning_connect_calendar),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (allDayBusy.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allDayBusy.forEach { slot ->
                            Text(
                                slot.title.ifBlank { stringResource(R.string.planning_busy) },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(busyColor(slot))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Frise horaire défilante.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { viewport = it.boundsInRoot() },
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll),
                    ) {
                        // 16 dp libres à droite : les créneaux ne collent plus au bord de l'écran.
                        val laneWidth = maxWidth - Gutter - 16.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = TimelineEdge)
                                .height(HourHeight * 24)
                                .onGloballyPositioned { timelineTop = it.positionInRoot().y },
                        ) {
                            HourGrid()
                            val items = remember(timedBusy, planned, dayStart) {
                                layoutLanes(
                                    timedBusy.map { LaneItem("busy-${it.start}-${it.title}", it.start, it.end) } +
                                        planned.map { LaneItem("task-${it.id}", it.blockStart!!, it.blockEnd!!) },
                                )
                            }
                            timedBusy.forEach { slot ->
                                val lane = items["busy-${slot.start}-${slot.title}"] ?: return@forEach
                                BusyBlock(slot, dayStart, lane, laneWidth)
                            }
                            planned.forEach { task ->
                                val lane = items["task-${task.id}"] ?: return@forEach
                                val conflict = timedBusy.any { it.start < task.blockEnd!! && it.end > task.blockStart!! }
                                TaskBlock(
                                    task = task,
                                    label = task.labelId?.let { labelsById[it] },
                                    dayStart = dayStart,
                                    lane = lane,
                                    laneWidth = laneWidth,
                                    conflict = conflict,
                                    dragging = drag.task?.id == task.id,
                                    onOpen = { onOpenTask(task) },
                                    onDuration = { onSetBlock(task, task.blockStart, it) },
                                    onUnschedule = { onSetBlock(task, null, 0) },
                                    onDragStart = { pointer ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        drag.task = task
                                        drag.pointer = pointer
                                        drag.grabMinutes = minutesAt(pointer.y) -
                                            (task.blockStart!! - dayStart) / MILLIS_PER_MINUTE.toFloat()
                                    },
                                    onDrag = { drag.pointer = it },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { drag.task = null },
                                )
                            }
                            if (isToday) NowLine(zone)
                            // Fantôme du dépôt : où la tâche saisie atterrira (calé au quart d'heure).
                            val ghostTask = drag.task
                            val ghost = dropMinutes()
                            if (ghostTask != null && ghost != null) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = Gutter, y = HourHeight * (ghost / 60f) + BlockTopInset)
                                        .width(laneWidth)
                                        .height(blockHeight(ghostTask.blockDuration.toFloat()))
                                        .clip(BlockShape)
                                        .background(quadrantVisual(ghostTask.quadrant).accent.copy(alpha = 0.25f)),
                                ) {
                                    Text(
                                        formatMinutes(ghost) + "–" + formatMinutes(ghost + ghostTask.blockDuration),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Bac « À planifier » : tâches actives sans créneau, par priorité (comme le Focus).
                // Feuille aux coins supérieurs arrondis, posée sur la frise.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { trayBounds = it.boundsInRoot() },
                ) {
                    Column(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)) {
                        Text(
                            stringResource(
                                if (drag.task?.blockStart != null) R.string.planning_drop_to_unschedule
                                else R.string.planning_tray,
                                tray.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        if (tray.isNotEmpty() && drag.task == null) {
                            Text(
                                stringResource(R.string.planning_tray_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (tray.isEmpty()) {
                            Text(
                                stringResource(R.string.planning_tray_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(tray, key = { it.id }) { task ->
                                    TrayTask(
                                        task = task,
                                        label = task.labelId?.let { labelsById[it] },
                                        dragging = drag.task?.id == task.id,
                                        onPlace = {
                                            nextFreeSlot(task.blockDuration)?.let { minutes ->
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSetBlock(task, dayStart + minutes * MILLIS_PER_MINUTE, task.blockDuration)
                                            }
                                        },
                                        onDragStart = { pointer ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            drag.task = task
                                            drag.pointer = pointer
                                            drag.grabMinutes = task.blockDuration / 2f
                                        },
                                        onDrag = { drag.pointer = it },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { drag.task = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Carte qui suit le doigt pendant un drag ; elle « se soulève » au ressort à la saisie.
            val dragLift by androidx.compose.animation.core.animateFloatAsState(
                if (drag.task != null) 1.06f else 1f,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "drag-lift",
            )
            drag.task?.let { task ->
                val accent = quadrantVisual(task.quadrant).accent
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .offset {
                            val local = drag.pointer - rootOffset
                            IntOffset((local.x - 60.dp.toPx()).roundToInt(), (local.y - 20.dp.toPx()).roundToInt())
                        }
                        .graphicsLayer { scaleX = dragLift; scaleY = dragLift },
                ) {
                    Text(
                        task.title,
                        color = onAccent(accent),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Boucle d'auto-défilement (annulée à la fin du drag), vitesse croissante vers le bord. */
private suspend fun autoScroll(scroll: ScrollState, drag: PlanningDrag, viewport: () -> Rect, edge: Float) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val seconds = (now - last) / 1_000_000_000f
        last = now
        val r = viewport()
        val y = drag.pointer.y
        val depth = when {
            y < r.top + edge && y > r.top - edge -> -(r.top + edge - y) / edge
            y > r.bottom - edge && y < r.bottom + edge -> (y - (r.bottom - edge)) / edge
            else -> 0f
        }.coerceIn(-1f, 1f)
        if (depth != 0f) scroll.scrollBy(1400f * depth * kotlin.math.abs(depth) * seconds)
    }
}

/** Emplacement d'un élément de la frise : colonne [lane] parmi [lanes] (chevauchements côte à côte). */
private data class Lane(val lane: Int, val lanes: Int)

private data class LaneItem(val key: String, val start: Long, val end: Long)

/**
 * Répartit les éléments qui se chevauchent en colonnes : chaque grappe d'éléments chevauchants
 * partage sa largeur entre autant de colonnes qu'il en faut au plus fort de la grappe.
 */
private fun layoutLanes(items: List<LaneItem>): Map<String, Lane> {
    val result = mutableMapOf<String, Lane>()
    val cluster = mutableListOf<Pair<LaneItem, Int>>()
    val columnEnds = mutableListOf<Long>()
    var clusterEnd = Long.MIN_VALUE
    fun flush() {
        cluster.forEach { (item, lane) -> result[item.key] = Lane(lane, columnEnds.size) }
        cluster.clear()
        columnEnds.clear()
    }
    items.sortedWith(compareBy<LaneItem> { it.start }.thenByDescending { it.end }).forEach { item ->
        if (item.start >= clusterEnd) flush()
        val free = columnEnds.indexOfFirst { it <= item.start }
        val lane = if (free >= 0) free else columnEnds.size.also { columnEnds.add(0) }
        columnEnds[lane] = item.end
        cluster += item to lane
        clusterEnd = maxOf(clusterEnd, item.end)
    }
    flush()
    return result
}

/** Grille horaire : libellé + ligne pleine à chaque heure, ligne plus discrète à la demi-heure. */
@Composable
private fun HourGrid() {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val halfLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    Column {
        repeat(24) { hour ->
            Box(modifier = Modifier.fillMaxWidth().height(HourHeight)) {
                Text(
                    formatMinutes(hour * 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(Gutter)
                        .offset(y = (-7).dp)
                        .padding(start = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(start = Gutter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor),
                )
                Box(
                    modifier = Modifier
                        .padding(start = Gutter + 8.dp, top = HourHeight / 2)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(halfLineColor),
                )
            }
        }
    }
}

@Composable
private fun NowLine(zone: ZoneId) {
    val now = Instant.now().atZone(zone)
    val minutes = now.hour * 60 + now.minute
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.offset(x = Gutter - 5.dp, y = HourHeight * (minutes / 60f) - 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
    }
}

@Composable
private fun busyColor(slot: BusySlot): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    return slot.color?.let { Color(it).copy(alpha = 0.28f) } ?: base
}

/** Créneau occupé d'un agenda externe (lecture seule). */
@Composable
private fun BusyBlock(slot: BusySlot, dayStart: Long, lane: Lane, laneWidth: Dp) {
    val startMin = ((slot.start - dayStart) / MILLIS_PER_MINUTE).coerceIn(0, MINUTES_PER_DAY.toLong())
    val endMin = ((slot.end - dayStart) / MILLIS_PER_MINUTE).coerceIn(startMin + 15, MINUTES_PER_DAY.toLong())
    val width = laneWidth / lane.lanes
    Row(
        modifier = Modifier
            .offset(x = Gutter + width * lane.lane, y = HourHeight * (startMin / 60f) + BlockTopInset)
            .width(width - 2.dp)
            .height(blockHeight((endMin - startMin).toFloat()))
            .clip(BlockShape)
            .background(busyColor(slot)),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(slot.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline),
        )
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    slot.title.ifBlank { stringResource(R.string.planning_busy) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Créneau d'une tâche planifiée : tap = menu (durée, ouvrir, déplanifier), appui long = déplacer. */
@Composable
private fun TaskBlock(
    task: Task,
    label: Label?,
    dayStart: Long,
    lane: Lane,
    laneWidth: Dp,
    conflict: Boolean,
    dragging: Boolean,
    onOpen: () -> Unit,
    onDuration: (Int) -> Unit,
    onUnschedule: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val accent = quadrantVisual(task.quadrant).accent
    val content = onAccent(accent)
    val startMin = (task.blockStart!! - dayStart) / MILLIS_PER_MINUTE
    val width = laneWidth / lane.lanes
    // Mouvement M3 Expressive : un créneau déplacé ou redimensionné glisse (et rebondit) jusqu'à sa
    // nouvelle place au lieu de sauter ; le décalage latéral (chevauchements) suit le même ressort.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val y by androidx.compose.animation.core.animateDpAsState(HourHeight * (startMin / 60f) + BlockTopInset, spatial, label = "block-y")
    val h by androidx.compose.animation.core.animateDpAsState(
        blockHeight(task.blockDuration.toFloat()),
        spatial,
        label = "block-h",
    )
    val x by androidx.compose.animation.core.animateDpAsState(Gutter + width * lane.lane, spatial, label = "block-x")
    val w by androidx.compose.animation.core.animateDpAsState(width - 2.dp, spatial, label = "block-w")
    var menuOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val callbacks by rememberUpdatedState(listOf(onDragStart, onDrag))
    val endCallbacks by rememberUpdatedState(onDragEnd to onDragCancel)
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .width(w)
            .height(h)
            .alpha(if (dragging) 0.35f else 1f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(BlockShape)
            .background(accent)
            .clickable { menuOpen = true }
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { callbacks[0](origin + it) },
                    onDrag = { change, _ ->
                        change.consume()
                        callbacks[1](origin + change.position)
                    },
                    onDragEnd = { endCallbacks.first() },
                    onDragCancel = { endCallbacks.second() },
                )
            }
            // Créneau de 15 min : aucune marge verticale, chaque dp compte.
            .padding(horizontal = 12.dp, vertical = if (task.blockDuration <= 15) 0.dp else 4.dp),
    ) {
        val range = formatMinutes(startMin.toInt()) + "–" + formatMinutes(startMin.toInt() + task.blockDuration)
        val conflictIcon: @Composable () -> Unit = {
            if (conflict) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(R.string.cd_planning_conflict),
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
        }
        if (task.blockDuration < 45) {
            // Créneau court : titre et horaire sur une seule ligne, centrés verticalement (texte
            // compact pour 15 min, qui n'a la hauteur que d'une petite ligne).
            val compact = task.blockDuration <= 15
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                conflictIcon()
                Text(
                    task.title,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Text(range, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f), maxLines = 1)
            }
        } else {
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    conflictIcon()
                    Text(
                        task.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        // Deux lignes de titre seulement quand le créneau a la hauteur de les montrer
                        // avec l'horaire dessous (≥ 1 h 15) ; sinon une ligne, l'horaire reste visible.
                        maxLines = if (task.blockDuration >= 75) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    range + (label?.let { " · ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            LocalizedOverlay {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.planning_open_task)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    onClick = { menuOpen = false; onOpen() },
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.planning_duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Durations.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(formatDuration(minutes)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Timelapse,
                                contentDescription = null,
                                tint = if (minutes == task.blockDuration) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { menuOpen = false; onDuration(minutes) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.block_clear)) },
                    leadingIcon = { Icon(Icons.Rounded.EventBusy, contentDescription = null) },
                    onClick = { menuOpen = false; onUnschedule() },
                )
            }
        }
    }
}

/** Tâche du bac : tap = prochain créneau libre, appui long = glisser sur la frise. */
@Composable
private fun TrayTask(
    task: Task,
    label: Label?,
    dragging: Boolean,
    onPlace: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val accent = quadrantVisual(task.quadrant).accent
    var origin by remember { mutableStateOf(Offset.Zero) }
    val callbacks by rememberUpdatedState(listOf(onDragStart, onDrag))
    val endCallbacks by rememberUpdatedState(onDragEnd to onDragCancel)
    // La puce saisie se tasse (ressort) pendant qu'elle est glissée sur la frise.
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (dragging) 0.92f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "tray-scale",
    )
    Row(
        modifier = Modifier
            .widthIn(max = 200.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (dragging) 0.35f else 1f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onPlace)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { callbacks[0](origin + it) },
                    onDrag = { change, _ ->
                        change.consume()
                        callbacks[1](origin + change.position)
                    },
                    onDragEnd = { endCallbacks.first() },
                    onDragCancel = { endCallbacks.second() },
                )
            }
            .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (label != null) {
            Spacer(Modifier.size(6.dp))
            Box(Modifier.size(8.dp).background(label.colorHex.toColor(), CircleShape))
        }
    }
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}
