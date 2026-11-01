package com.kairos.app.ui.planning

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.R
import com.kairos.app.data.calendar.BusySlot
import com.kairos.app.domain.planning.DayPlan
import com.kairos.app.domain.planning.Interval
import com.kairos.app.domain.planning.Placement
import com.kairos.app.ui.components.formatDuration
import com.kairos.app.ui.components.quadrantVisual
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Marge au-dessus et au-dessous de la frise : les heures extrêmes ne sont pas rognées. */
private val TimelineEdge = 10.dp

/** Hauteur minimale d'une heure : en deçà, un quart d'heure devient impossible à viser. */
private val MinHourHeight = 40.dp

private val BlockShape = RoundedCornerShape(12.dp)

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE

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

/** Boucle d'auto-défilement (annulée à la fin du drag), vitesse croissante vers le bord. */
private suspend fun autoScroll(scroll: ScrollState, pointer: () -> Offset, viewport: () -> Rect, edge: Float) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val seconds = (now - last) / 1_000_000_000f
        last = now
        val r = viewport()
        val y = pointer().y
        val depth = when {
            y < r.top + edge && y > r.top - edge -> -(r.top + edge - y) / edge
            y > r.bottom - edge && y < r.bottom + edge -> (y - (r.bottom - edge)) / edge
            else -> 0f
        }.coerceIn(-1f, 1f)
        if (depth != 0f) scroll.scrollBy(1400f * depth * abs(depth) * seconds)
    }
}

/**
 * Vue **frise** : la journée entière tient à l'écran (l'échelle s'adapte à la hauteur disponible). On
 * y voit les proportions et on y ajuste une heure au quart d'heure près : appui long pour déplacer une
 * session. Le remplissage passe, comme dans l'agenda, par un tap sur un créneau libre.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlanningTimeline(
    day: Interval,
    window: Interval,
    blocks: List<PlannedBlock>,
    busy: List<BusySlot>,
    free: List<Interval>,
    proposals: List<Placement>,
    conflicts: Set<Long>,
    now: Long,
    isToday: Boolean,
    editable: Boolean,
    onOpenBlock: (PlannedBlock) -> Unit,
    onOpenSlot: (Interval) -> Unit,
    onMove: (PlannedBlock, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // La frise couvre la journée de travail, élargie si un élément en déborde.
    val starts = blocks.map { it.block.start } + busy.map { it.start } + proposals.map { it.start } + window.start
    val ends = blocks.map { it.block.end } + busy.map { it.end } + proposals.map { it.interval.end } + window.end
    val firstHour = ((starts.min().coerceAtLeast(day.start) - day.start) / MILLIS_PER_HOUR).toInt()
    val lastHour = ((ends.max().coerceAtMost(day.end) - day.start + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR).toInt()
    val rangeStart = day.start + firstHour * MILLIS_PER_HOUR
    val rangeEnd = day.start + lastHour * MILLIS_PER_HOUR

    val scroll = rememberScrollState()
    var dragged by remember { mutableStateOf<PlannedBlock?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabMinutes by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var timelineTop by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val zone = ZoneId.systemDefault()

    BoxWithConstraints(modifier.onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val hours = (lastHour - firstHour).coerceAtLeast(1)
        val hourHeight = maxOf((maxHeight - TimelineEdge * 2) / hours, MinHourHeight)
        val hourPx = with(density) { hourHeight.toPx() }
        // 12 dp libres à droite : les créneaux ne collent pas au bord de l'écran.
        // Gouttière des heures : suit la taille de police, pour ne jamais couper « 08:00 ».
        val gutter = with(density) { 38.sp.toDp() } + 8.dp
        val laneWidth = maxWidth - gutter - 12.dp
        // Une ligne de texte, quelle que soit la taille de police du téléphone, même pour 15 min.
        val minBlock = with(density) { MaterialTheme.typography.labelLarge.lineHeight.toDp() } + 2.dp
        val minBlockMillis = (MILLIS_PER_HOUR * (minBlock / hourHeight)).toLong()

        /** Élément de la frise, avec la place qu'il occupe à l'écran (au moins [minBlock]). */
        fun laneItem(key: String, start: Long, end: Long) = LaneItem(key, start, maxOf(end, start + minBlockMillis))

        fun yOf(millis: Long): Dp = hourHeight * ((millis - rangeStart) / MILLIS_PER_HOUR.toFloat())

        fun minutesAt(y: Float): Float = (y - timelineTop) / hourPx * 60f

        /** Début visé par le drag en cours (null si le doigt a quitté la frise). */
        fun dropStart(): Long? {
            val block = dragged?.block ?: return null
            if (!viewport.contains(pointer)) return null
            val target = rangeStart + ((minutesAt(pointer.y) - grabMinutes) * MILLIS_PER_MINUTE).toLong()
            return DayPlan.snapDrop(target, block.minutes, maxOf(rangeStart, now), rangeEnd)
        }

        if (dragged != null) {
            LaunchedEffect(Unit) { autoScroll(scroll, { pointer }, { viewport }, with(density) { 56.dp.toPx() }) }
        }
        // Frise plus haute que l'écran (journée très longue) : on s'ouvre sur l'heure courante.
        LaunchedEffect(day.start, hourPx) {
            if (isToday) scroll.scrollTo((((now - rangeStart) / MILLIS_PER_HOUR.toFloat() - 1f) * hourPx).roundToInt().coerceAtLeast(0))
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = TimelineEdge)
                    .height(hourHeight * hours)
                    .onGloballyPositioned { timelineTop = it.positionInRoot().y },
            ) {
                HourGrid(firstHour, hours, hourHeight, gutter)
                free.forEach { slot ->
                    FreeZone(
                        slot = slot,
                        top = yOf(slot.start),
                        height = hourHeight * (slot.minutes / 60f),
                        width = laneWidth,
                        gutter = gutter,
                        onOpen = { onOpenSlot(slot) },
                    )
                }

                // Deux créneaux courts qui se suivent se recouvriraient à l'écran : ils passent côte à côte.
                val lanes = remember(blocks, busy, proposals, minBlockMillis) {
                    layoutLanes(
                        busy.map { laneItem("busy-${it.start}-${it.title}", it.start, it.end) } +
                            blocks.map { laneItem("block-${it.block.id}", it.block.start, it.block.end) } +
                            proposals.map { laneItem("proposal-${it.task.id}", it.start, it.interval.end) },
                    )
                }
                // Sous les créneaux : tracée par-dessus, elle barrerait le titre de la session en cours.
                if (isToday && now in rangeStart..rangeEnd) NowLine(yOf(now), gutter)
                busy.forEach { slot ->
                    val lane = lanes["busy-${slot.start}-${slot.title}"] ?: return@forEach
                    val width = laneWidth / lane.lanes
                    Box(
                        Modifier
                            .offset(x = gutter + width * lane.lane, y = yOf(slot.start.coerceAtLeast(rangeStart)))
                            .width(width - 2.dp)
                            .height(blockHeight(hourHeight, slot.end - slot.start, minBlock))
                            .clip(BlockShape)
                            .background(busyColor(slot)),
                    ) {
                        BlockLabel(
                            title = slot.title.ifBlank { stringResource(R.string.planning_busy) },
                            time = rangeLabel(slot.start, slot.end, zone),
                            showTime = lane.lanes == 1,
                        )
                    }
                }
                blocks.forEach { item ->
                    val lane = lanes["block-${item.block.id}"] ?: return@forEach
                    val width = laneWidth / lane.lanes
                    val canDrag = editable && !item.done
                    key(item.block.id) {
                    val y by animateDpAsState(yOf(item.block.start), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "block-y")
                    val h by animateDpAsState(
                        blockHeight(hourHeight, item.block.minutes * MILLIS_PER_MINUTE, minBlock),
                        MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "block-h",
                    )
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    val currentItem by rememberUpdatedState(item)
                    val currentCanDrag by rememberUpdatedState(canDrag)
                    val finish by rememberUpdatedState {
                        val target = dropStart()
                        if (target != null && target != dragged?.block?.start) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMove(currentItem, target)
                        }
                        dragged = null
                    }
                    Box(
                        Modifier
                            .offset(x = gutter + width * lane.lane, y = y)
                            .width(width - 2.dp)
                            .height(h)
                            .alpha(if (dragged?.block?.id == item.block.id) 0.35f else if (item.done) 0.6f else 1f)
                            .onGloballyPositioned { origin = it.positionInRoot() }
                            .clip(BlockShape)
                            .background(quadrantTint(quadrantVisual(item.task.quadrant).accent))
                            .clickable { onOpenBlock(item) }
                            .pointerInput(item.block.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        if (!currentCanDrag) return@detectDragGesturesAfterLongPress
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pointer = origin + offset
                                        grabMinutes = minutesAt(pointer.y) -
                                            (currentItem.block.start - rangeStart) / MILLIS_PER_MINUTE.toFloat()
                                        dragged = currentItem
                                    },
                                    onDrag = { change, _ ->
                                        if (dragged != null) {
                                            change.consume()
                                            pointer = origin + change.position
                                        }
                                    },
                                    onDragEnd = { finish() },
                                    onDragCancel = { dragged = null },
                                )
                            },
                    ) {
                        BlockLabel(
                            title = item.task.title,
                            time = rangeLabel(item.block.start, item.block.end, zone),
                            showTime = lane.lanes == 1,
                            conflict = item.block.id in conflicts,
                        )
                    }
                    }
                }
                proposals.forEach { placement ->
                    val lane = lanes["proposal-${placement.task.id}"] ?: return@forEach
                    val width = laneWidth / lane.lanes
                    Box(
                        Modifier
                            .offset(x = gutter + width * lane.lane, y = yOf(placement.start))
                            .width(width - 2.dp)
                            .height(blockHeight(hourHeight, placement.minutes * MILLIS_PER_MINUTE, minBlock))
                            .dashedOutline(quadrantVisual(placement.task.quadrant).accent, 12.dp),
                    ) {
                        BlockLabel(
                            title = placement.task.title,
                            time = rangeLabel(placement.start, placement.interval.end, zone),
                            showTime = lane.lanes == 1,
                        )
                    }
                }

                // Fantôme du dépôt : l'heure exacte reste lisible, au-dessus du doigt. Il vire au rouge
                // si le dépôt créerait un chevauchement.
                val ghostItem = dragged
                val ghost = dropStart()
                if (ghostItem != null && ghost != null) {
                    val range = Interval(ghost, ghost + ghostItem.block.minutes * MILLIS_PER_MINUTE)
                    val clash = busy.any { range.overlaps(Interval(it.start, it.end)) } ||
                        blocks.any { !it.done && it.block.id != ghostItem.block.id && range.overlaps(DayPlan.interval(it.block)) }
                    Box(
                        Modifier
                            .offset(x = gutter, y = yOf(ghost))
                            .width(laneWidth)
                            .height(blockHeight(hourHeight, range.end - range.start, minBlock))
                            .clip(BlockShape)
                            .background(
                                if (clash) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        BlockLabel(title = ghostItem.task.title, time = rangeLabel(range.start, range.end, zone), conflict = clash)
                    }
                }
            }
        }
    }
}

private fun blockHeight(hourHeight: Dp, millis: Long, min: Dp): Dp =
    maxOf(hourHeight * (millis / MILLIS_PER_HOUR.toFloat()) - 2.dp, min)

/** [showTime] = false sur un créneau étroit : le titre passe avant l'horaire, que la grille indique déjà. */
@Composable
private fun BlockLabel(title: String, time: String, showTime: Boolean = true, conflict: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (conflict) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = stringResource(R.string.cd_planning_conflict),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showTime) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FreeZone(slot: Interval, top: Dp, height: Dp, width: Dp, gutter: Dp, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .offset(x = gutter, y = top + 2.dp)
            .width(width)
            .height(height - 4.dp)
            .dashedOutline(MaterialTheme.colorScheme.outlineVariant, 12.dp)
            .clip(BlockShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Un créneau trop bas pour une ligne de texte reste touchable, sans libellé.
        if (height >= 28.dp) {
            Text(
                stringResource(R.string.planning_free_row, formatDuration(slot.minutes)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.planning_fill_slot),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Grille horaire : libellé + ligne à chaque heure. */
@Composable
private fun HourGrid(firstHour: Int, hours: Int, hourHeight: Dp, gutter: Dp) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column {
        repeat(hours) { index ->
            Box(modifier = Modifier.fillMaxWidth().height(hourHeight)) {
                Text(
                    "%02d:00".format(firstHour + index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(gutter).offset(y = (-7).dp).padding(start = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(start = gutter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor),
                )
            }
        }
    }
}

@Composable
private fun NowLine(y: Dp, gutter: Dp) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.offset(x = gutter - 5.dp, y = y - 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(0.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
    }
}
