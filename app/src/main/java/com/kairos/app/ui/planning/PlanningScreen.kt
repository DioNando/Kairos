package com.kairos.app.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.calendar.BusyResult
import com.kairos.app.data.calendar.BusySlot
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Task
import com.kairos.app.data.model.TimeBlock
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.domain.planning.DayPlan
import com.kairos.app.domain.planning.Interval
import com.kairos.app.domain.planning.Placement
import com.kairos.app.ui.components.formatDuration
import com.kairos.app.ui.components.rememberMinuteClock
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_MINUTE = 60_000L

/** Jours accessibles dans le bandeau, de part et d'autre d'aujourd'hui. */
private const val DAYS_BEHIND = 14L
private const val DAYS_AHEAD = 90L

/** Session et sa tâche ; [done] vaut aussi quand la tâche a été terminée ailleurs (matrice, montre). */
internal data class PlannedBlock(val block: TimeBlock, val task: Task) {
    val done: Boolean get() = block.isDone || task.isCompleted
}

internal fun hm(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun rangeLabel(start: Long, end: Long, zone: ZoneId): String = hm(start, zone) + "–" + hm(end, zone)

/** Fond d'un élément du quadrant [accent] : teinte légère, sur laquelle le texte standard reste lisible. */
@Composable
internal fun quadrantTint(accent: Color): Color {
    val surface = MaterialTheme.colorScheme.surface
    return accent.copy(alpha = if (surface.luminance() < 0.5f) 0.32f else 0.24f).compositeOver(surface)
}

@Composable
internal fun busyColor(slot: BusySlot): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    return slot.color?.let { Color(it).copy(alpha = 0.28f).compositeOver(base) } ?: base
}

/** Contour pointillé : ce qui n'est pas encore acquis (temps libre, proposition). */
internal fun Modifier.dashedOutline(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        ),
    )
}

/** Plage [startHour]–[endHour] de la journée [date]. */
private fun window(date: LocalDate, startHour: Int, endHour: Int, zone: ZoneId): Interval = Interval(
    date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli(),
    (if (endHour >= 24) date.plusDays(1).atStartOfDay() else date.atTime(endHour, 0))
        .atZone(zone).toInstant().toEpochMilli(),
)

private fun dayRange(date: LocalDate, zone: ZoneId): Interval = Interval(
    date.atStartOfDay(zone).toInstant().toEpochMilli(),
    date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/**
 * **Time-blocking** : que faire de son temps libre ? Le bandeau montre la charge de la semaine, puis
 * deux vues d'une même journée : l'**agenda** (sessions et temps libres, remplis d'un tap) et la
 * **frise** (journée entière à l'écran, pour ajuster une heure au doigt). Les rendez-vous des agendas
 * sont en lecture seule ; les sessions manquées remontent en tête pour être replacées.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanningScreen(
    tasks: List<Task>,
    timeBlocks: List<TimeBlock>,
    labels: List<Label>,
    focusDueWeight: Int,
    subtaskDues: Map<Long, Long>,
    startHour: Int,
    endHour: Int,
    calendarConfigured: Boolean,
    loadBusy: suspend (from: Long, to: Long) -> BusyResult,
    onAddBlock: (Task, Long, Int) -> Unit,
    onUpdateBlock: (TimeBlock) -> Unit,
    onRemoveBlock: (TimeBlock) -> Unit,
    onCompleteBlock: (TimeBlock) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onStartFocus: (Task) -> Unit,
    onOpenTask: (Task) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val clock = rememberMinuteClock()
    val today = Instant.ofEpochMilli(clock).atZone(zone).toLocalDate()
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    var timelineView by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var slotSheet by remember { mutableStateOf<Interval?>(null) }
    var blockSheetId by remember { mutableStateOf<Long?>(null) }
    var proposals by remember { mutableStateOf(emptyList<Placement>()) }

    val day = LocalDate.ofEpochDay(epochDay)
    val range = dayRange(day, zone)
    val dayWindow = window(day, startHour, endHour, zone)
    val isToday = day == today
    val isPast = day < today

    var loadingBusy by remember { mutableStateOf(false) }
    val busy by produceState(BusyResult(), range.start, calendarConfigured) {
        value = BusyResult()
        loadingBusy = calendarConfigured
        value = runCatching { loadBusy(range.start, range.end) }.getOrDefault(BusyResult(incomplete = true))
        loadingBusy = false
    }
    val timedBusy = remember(busy) { busy.slots.filter { !it.allDay } }
    val allDayBusy = remember(busy) { busy.slots.filter { it.allDay } }

    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    val blocksByTask = remember(timeBlocks) { timeBlocks.groupBy { it.taskId } }
    val dayItems = remember(timeBlocks, tasksById, range) {
        timeBlocks.filter { it.start in range.start until range.end }
            .mapNotNull { block -> tasksById[block.taskId]?.takeIf { !it.isRemoved }?.let { PlannedBlock(block, it) } }
    }
    val done = dayItems.filter { it.done }
    val missed = if (isToday) {
        DayPlan.missed(timeBlocks, tasksById, clock).map { PlannedBlock(it, tasksById.getValue(it.taskId)) }
    } else {
        emptyList()
    }
    val open = dayItems.filter { !it.done && (!isToday || it.block.end > clock) }
    val busyIntervals = timedBusy.map { Interval(it.start, it.end) }
    val occupied = open.map { DayPlan.interval(it.block) } + busyIntervals + proposals.map { it.interval }
    val from = if (isToday) clock else dayWindow.start
    val free = if (isPast) emptyList() else DayPlan.freeSlots(dayWindow, from, occupied)
    val conflicts = DayPlan.conflicts(open.map { it.block }, busyIntervals)

    fun remaining(task: Task): Int = DayPlan.remainingMinutes(task, blocksByTask[task.id].orEmpty())

    val ranked = remember(tasks, focusDueWeight, clock, subtaskDues) {
        FocusSelector.rank(tasks, focusDueWeight, clock, subtaskDues)
    }
    val unscheduled = ranked.filter { task -> remaining(task) > 0 && proposals.none { it.task.id == task.id } }

    val entries = (
        open.map { AgendaEntry.Block(it, it.block.id in conflicts) } +
            timedBusy.filter { !isToday || it.end > clock }.map { AgendaEntry.Busy(it) } +
            free.map { AgendaEntry.Free(it) } +
            proposals.map { AgendaEntry.Proposal(it) }
        ).sortedBy { it.start }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val movedMessage = stringResource(R.string.planning_moved)
    val nothingToPropose = stringResource(R.string.planning_fill_empty)

    /** Premier créneau de [date] pour [minutes], la session [ignore] étant considérée comme libérée. */
    suspend fun firstFitOn(date: LocalDate, minutes: Int, ignore: Long, after: Long = Long.MIN_VALUE): Long? {
        if (date < today) return null
        val target = dayRange(date, zone)
        val slots = if (date == day) timedBusy else loadBusy(target.start, target.end).slots.filter { !it.allDay }
        val taken = timeBlocks.filter {
            it.id != ignore && !it.isDone && it.start in target.start until target.end &&
                tasksById[it.taskId]?.isActive == true
        }.map(DayPlan::interval) + slots.map { Interval(it.start, it.end) }
        val freeThere = DayPlan.freeSlots(
            window(date, startHour, endHour, zone),
            if (date == today) clock else Long.MIN_VALUE,
            taken,
        )
        return DayPlan.firstFit(freeThere, minutes, after)
    }

    /** Replace [block] au premier créneau de [dates] ; sinon en début de la dernière journée essayée. */
    fun replace(block: TimeBlock, dates: List<LocalDate>, after: Long = Long.MIN_VALUE) {
        scope.launch {
            val start = dates.firstNotNullOfOrNull { firstFitOn(it, block.minutes, block.id, after) }
                ?: window(dates.last(), startHour, endHour, zone).start
            onUpdateBlock(block.copy(start = start))
            val whenText = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale).format(Instant.ofEpochMilli(start).atZone(zone))
            snackbar.showSnackbar(movedMessage.format(whenText))
        }
    }

    val stripState = rememberLazyListState()
    val dayCount = (DAYS_BEHIND + DAYS_AHEAD + 1).toInt()
    val days = remember(today) { List(dayCount) { today.plusDays(it - DAYS_BEHIND) } }
    LaunchedEffect(Unit) {
        stripState.scrollToItem(((epochDay - today.toEpochDay() + DAYS_BEHIND).toInt()).coerceIn(0, dayCount - 1))
    }
    val windowMinutes = (endHour - startHour) * 60
    val loads = remember(timeBlocks, tasksById, zone) {
        timeBlocks.filter { tasksById[it.taskId]?.isRemoved == false }
            .groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }
    }

    fun selectDay(date: LocalDate) {
        epochDay = date.toEpochDay()
        proposals = emptyList()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.planning_title), fontWeight = FontWeight.Bold)
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
                    if (!isToday) {
                        IconButton(
                            onClick = {
                                selectDay(today)
                                scope.launch { stripState.animateScrollToItem(DAYS_BEHIND.toInt()) }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.cd_today))
                        }
                    }
                    if (!isPast) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                            Text(
                                formatDuration(free.sumOf { it.minutes }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.planning_free_total, hm(dayWindow.end, zone)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            PlanningBottomBar(
                proposalCount = proposals.size,
                timelineView = timelineView,
                canFill = !isPast,
                onView = { timelineView = it },
                onFill = {
                    proposals = DayPlan.propose(unscheduled, dayWindow, from, occupied, ::remaining)
                    if (proposals.isEmpty()) scope.launch { snackbar.showSnackbar(nothingToPropose) }
                },
                onAccept = {
                    proposals.forEach { onAddBlock(it.task, it.start, it.minutes) }
                    proposals = emptyList()
                },
                onCancel = { proposals = emptyList() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekStrip(
                days = days,
                selected = day,
                loads = loads,
                dayMinutes = windowMinutes,
                state = stripState,
                onSelect = ::selectDay,
            )
            if (busy.incomplete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.planning_calendar_incomplete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
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
            if (timelineView) {
                PlanningTimeline(
                    day = range,
                    window = dayWindow,
                    // Terminée en avance, une session rend son créneau : il s'affiche libre.
                    blocks = dayItems.filter { !it.done || it.block.end <= clock },
                    busy = timedBusy,
                    free = free,
                    proposals = proposals,
                    conflicts = conflicts,
                    now = clock,
                    isToday = isToday,
                    editable = !isPast,
                    onOpenBlock = { item -> if (item.done) onOpenTask(item.task) else blockSheetId = item.block.id },
                    onOpenSlot = { slotSheet = it },
                    onMove = { item, start -> onUpdateBlock(item.block.copy(start = start)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                PlanningAgenda(
                    entries = entries,
                    missed = missed,
                    done = done,
                    showDone = showDone,
                    now = clock.takeIf { isToday },
                    calendarConfigured = calendarConfigured,
                    onToggleDone = { showDone = !showDone },
                    onOpenBlock = { blockSheetId = it.block.id },
                    onCompleteBlock = { onCompleteBlock(it.block) },
                    onOpenSlot = { slotSheet = it },
                    onReplaceToday = { replace(it.block, listOf(today, today.plusDays(1))) },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    slotSheet?.let { slot ->
        SlotSheet(
            slot = slot,
            tasks = unscheduled,
            remaining = ::remaining,
            onPick = { task, minutes ->
                onAddBlock(task, slot.start, minutes)
                slotSheet = null
            },
            onDismiss = { slotSheet = null },
        )
    }

    blockSheetId?.let { id ->
        val block = timeBlocks.firstOrNull { it.id == id }
        val task = block?.let { tasksById[it.taskId] }
        if (block != null && task != null) {
            val blockDay = Instant.ofEpochMilli(block.start).atZone(zone).toLocalDate()
            val nextDay = maxOf(blockDay, today).plusDays(1)
            BlockSheet(
                item = PlannedBlock(block, task),
                conflict = id in conflicts,
                canFinishTask = !DayPlan.finishesTask(task, blocksByTask[task.id].orEmpty(), id),
                onComplete = {
                    onCompleteBlock(block)
                    blockSheetId = null
                },
                onCompleteTask = {
                    onCompleteTask(task)
                    blockSheetId = null
                },
                onFocus = {
                    blockSheetId = null
                    onStartFocus(task)
                },
                onDuration = { onUpdateBlock(block.copy(minutes = it)) },
                onLater = {
                    blockSheetId = null
                    replace(block, listOf(maxOf(blockDay, today), nextDay), after = block.end)
                },
                onTomorrow = {
                    blockSheetId = null
                    replace(block, listOf(nextDay))
                },
                onOpenTask = {
                    blockSheetId = null
                    onOpenTask(task)
                },
                onRemove = {
                    blockSheetId = null
                    onRemoveBlock(block)
                },
                onDismiss = { blockSheetId = null },
            )
        }
    }
}

/** Sept jours à l'écran, avec la charge de chacun : les journées vides ou pleines se voient d'un coup d'œil. */
@Composable
private fun WeekStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    loads: Map<LocalDate, Int>,
    dayMinutes: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val longDate = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - 24.dp - 4.dp * 6) / 7
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(days, key = { it.toEpochDay() }) { date ->
                val on = date == selected
                val minutes = loads[date] ?: 0
                val load = (minutes.toFloat() / dayMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                val description = stringResource(
                    R.string.cd_planning_day,
                    longDate.format(date),
                    formatDuration(minutes),
                )
                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .heightIn(min = 58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) colors.primary else colors.surfaceContainer)
                        .clickable { onSelect(date) }
                        .semantics { contentDescription = description }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val content = if (on) colors.onPrimary else colors.onSurface
                    Text(
                        weekday.format(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        maxLines = 1,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = content,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(content.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            Modifier
                                .width(26.dp * load)
                                .height(4.dp)
                                .background(if (on) colors.onPrimary else colors.primary),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanningBottomBar(
    proposalCount: Int,
    timelineView: Boolean,
    canFill: Boolean,
    onView: (Boolean) -> Unit,
    onFill: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (proposalCount > 0) {
                FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = onAccept, modifier = Modifier.weight(2f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.planning_fill_accept, proposalCount), maxLines = 1)
                }
            } else {
                val colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ToggleButton(
                        checked = !timelineView,
                        onCheckedChange = { if (it) onView(false) },
                        colors = colors,
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    ) { Text(stringResource(R.string.planning_view_agenda), maxLines = 1) }
                    ToggleButton(
                        checked = timelineView,
                        onCheckedChange = { if (it) onView(true) },
                        colors = colors,
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    ) { Text(stringResource(R.string.planning_view_timeline), maxLines = 1) }
                }
                if (canFill) {
                    Button(onClick = onFill, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        // Deux lignes possibles : grande police, ou langue au libellé plus long.
                        Text(stringResource(R.string.planning_fill_day), textAlign = TextAlign.Center)
                    }
                } else {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}
