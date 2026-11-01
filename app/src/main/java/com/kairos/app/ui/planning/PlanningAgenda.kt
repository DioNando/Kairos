package com.kairos.app.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.calendar.BusySlot
import com.kairos.app.domain.planning.Interval
import com.kairos.app.domain.planning.Placement
import com.kairos.app.ui.components.formatBlock
import com.kairos.app.ui.components.formatDuration
import com.kairos.app.ui.components.quadrantVisual
import java.time.ZoneId

private val RowShape = RoundedCornerShape(20.dp)

/** Sessions manquées visibles sans déplier. */
private const val MissedPreview = 2

/** Ligne de l'agenda, dans l'ordre de la journée. */
internal sealed interface AgendaEntry {
    val start: Long

    data class Block(val item: PlannedBlock, val conflict: Boolean) : AgendaEntry {
        override val start: Long get() = item.block.start
    }

    data class Busy(val slot: BusySlot) : AgendaEntry {
        override val start: Long get() = slot.start
    }

    data class Free(val slot: Interval) : AgendaEntry {
        override val start: Long get() = slot.start
    }

    data class Proposal(val placement: Placement) : AgendaEntry {
        override val start: Long get() = placement.start
    }
}

/**
 * Vue **agenda** : la journée comme une suite de sessions et de **temps libres**. Un temps libre est
 * une ligne à part entière : un tap ouvre les tâches à y placer, sans glisser-déposer ni visée.
 */
@Composable
internal fun PlanningAgenda(
    entries: List<AgendaEntry>,
    missed: List<PlannedBlock>,
    done: List<PlannedBlock>,
    showDone: Boolean,
    now: Long?,
    calendarConfigured: Boolean,
    onToggleDone: () -> Unit,
    onOpenBlock: (PlannedBlock) -> Unit,
    onCompleteBlock: (PlannedBlock) -> Unit,
    onOpenSlot: (Interval) -> Unit,
    onReplaceToday: (PlannedBlock) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (missed.isNotEmpty()) {
            item(key = "missed") {
                MissedGroup(
                    missed = missed,
                    onOpen = onOpenBlock,
                    onComplete = onCompleteBlock,
                    onReplace = onReplaceToday,
                )
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-summary") { DoneSummary(done.size, showDone, onToggleDone) }
            if (showDone) {
                items(done, key = { "done-${it.block.id}" }) { item ->
                    Row(
                        modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            rangeLabel(item.block.start, item.block.end, zone),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(92.dp),
                        )
                        Text(
                            item.task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (now != null) item(key = "now") { NowRow(hm(now, zone)) }
        items(
            entries,
            key = { entry ->
                when (entry) {
                    is AgendaEntry.Block -> "block-${entry.item.block.id}"
                    is AgendaEntry.Busy -> "busy-${entry.slot.start}-${entry.slot.title}"
                    is AgendaEntry.Free -> "free-${entry.slot.start}"
                    is AgendaEntry.Proposal -> "proposal-${entry.placement.task.id}"
                }
            },
        ) { entry ->
            when (entry) {
                is AgendaEntry.Block -> BlockRow(
                    item = entry.item,
                    conflict = entry.conflict,
                    onOpen = { onOpenBlock(entry.item) },
                    onComplete = { onCompleteBlock(entry.item) },
                )
                is AgendaEntry.Busy -> BusyRow(entry.slot)
                is AgendaEntry.Free -> FreeRow(entry.slot) { onOpenSlot(entry.slot) }
                is AgendaEntry.Proposal -> ProposalRow(entry.placement)
            }
        }
        if (!calendarConfigured) {
            item(key = "connect") { ConnectCalendar(onOpenSettings) }
        }
    }
}

/**
 * Sessions manquées, regroupées : au-delà de [MissedPreview], le reste se déplie, pour que l'agenda du
 * jour reste à l'écran. Le ✓ est direct : une session manquée a souvent été faite sans être cochée.
 */
@Composable
private fun MissedGroup(
    missed: List<PlannedBlock>,
    onOpen: (PlannedBlock) -> Unit,
    onComplete: (PlannedBlock) -> Unit,
    onReplace: (PlannedBlock) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Le primaire par défaut manque de contraste sur ce conteneur.
    val onContainerButton = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp).heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.planning_missed_title, missed.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (missed.size > MissedPreview) {
                    TextButton(onClick = { expanded = !expanded }, colors = onContainerButton) {
                        Text(
                            if (expanded) stringResource(R.string.planning_show_less)
                            else stringResource(R.string.planning_show_all, missed.size),
                        )
                    }
                }
            }
            (if (expanded) missed else missed.take(MissedPreview)).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onComplete(item) }) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_planning_complete, item.task.title),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpen(item) }
                            .heightIn(min = 48.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            item.task.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatBlock(item.block.start, item.block.end),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    TextButton(onClick = { onReplace(item) }, colors = onContainerButton) {
                        Text(stringResource(R.string.planning_replace))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneSummary(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.planning_done_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(if (expanded) R.string.planning_done_hide else R.string.planning_done_show),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NowRow(time: String) {
    val color = MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.planning_now, time),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.size(8.dp))
        HorizontalDivider(thickness = 2.dp, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FreeRow(slot: Interval, onOpen: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val range = rangeLabel(slot.start, slot.end, zone)
    val description = stringResource(R.string.cd_planning_fill_slot, range)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(MaterialTheme.colorScheme.outlineVariant, 20.dp)
            .clip(RowShape)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = description }
            // Plus un temps libre est long, plus sa ligne se voit (sans recréer une échelle horaire).
            .heightIn(min = (60 + slot.minutes / 6).coerceAtMost(92).dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.planning_free_row, formatDuration(slot.minutes)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.planning_fill_slot),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BlockRow(item: PlannedBlock, conflict: Boolean, onOpen: () -> Unit, onComplete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val visual = quadrantVisual(item.task.quadrant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(quadrantTint(visual.accent))
            .heightIn(min = (64 + item.block.minutes / 3).coerceAtMost(112).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EntryMeta(visual.icon, visual.accent, visual.verb + " · " + rangeLabel(item.block.start, item.block.end, zone), conflict)
            Text(
                item.task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onComplete, modifier = Modifier.padding(end = 2.dp)) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_planning_complete, item.task.title),
            )
        }
    }
}

@Composable
private fun ProposalRow(placement: Placement) {
    val zone = ZoneId.systemDefault()
    val visual = quadrantVisual(placement.task.quadrant)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(visual.accent, 20.dp)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EntryMeta(visual.icon, visual.accent, visual.verb + " · " + rangeLabel(placement.start, placement.interval.end, zone), false)
        Text(placement.task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.planning_proposed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyRow(slot: BusySlot) {
    val zone = ZoneId.systemDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(busyColor(slot))
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                rangeLabel(slot.start, slot.end, zone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                slot.title.ifBlank { stringResource(R.string.planning_busy) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EntryMeta(icon: androidx.compose.ui.graphics.painter.Painter, accent: Color, label: String, conflict: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // L'icône double la couleur : le quadrant reste identifiable sans distinguer les teintes.
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (conflict) {
            Spacer(Modifier.size(6.dp))
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = stringResource(R.string.cd_planning_conflict),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ConnectCalendar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.planning_connect_calendar),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.planning_connect_action)) }
    }
    Spacer(Modifier.height(4.dp))
}
