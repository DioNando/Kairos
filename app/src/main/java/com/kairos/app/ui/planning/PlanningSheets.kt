package com.kairos.app.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Task
import com.kairos.app.domain.planning.Interval
import com.kairos.app.ui.components.KairosAssistChip
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.formatDuration
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.theme.LocalizedOverlay
import java.time.ZoneId

/** Durées proposées pour une session (minutes). */
internal val SessionDurations = listOf(15, 30, 45, 60, 90, 120, 180, 240)

@Composable
private fun SheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Remplir un créneau libre : tâches restant à planifier, par priorité. La durée proposée est le temps
 * restant de la tâche touchée, plafonné au créneau ; les boutons de durée permettent de l'imposer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotSheet(
    slot: Interval,
    tasks: List<Task>,
    remaining: (Task) -> Int,
    onPick: (Task, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    // 0 = pas de durée imposée : chaque tâche prend celle qui lui reste.
    var forced by remember(slot) { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LocalizedOverlay {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.planning_slot_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.planning_free_row, formatDuration(slot.minutes)) +
                            " · " + rangeLabel(slot.start, slot.end, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SessionDurations.filter { it <= slot.minutes }.forEach { minutes ->
                        KairosFilterChip(
                            selected = forced == minutes,
                            onClick = { forced = if (forced == minutes) 0 else minutes },
                            label = formatDuration(minutes),
                        )
                    }
                }
                if (tasks.isEmpty()) {
                    Text(
                        stringResource(R.string.planning_slot_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                } else {
                    Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
                        SheetSection(stringResource(R.string.planning_by_priority))
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            val left = remaining(task)
                            CandidateRow(
                                task = task,
                                remainingLabel = if (task.estimateMinutes > 0) formatDuration(left) else null,
                                onClick = {
                                    onPick(task, (if (forced > 0) forced else left).coerceAtMost(slot.minutes))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(task: Task, remainingLabel: String?, onClick: () -> Unit) {
    val visual = quadrantVisual(task.quadrant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(visual.accent, CircleShape))
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                visual.verb + " · " + (
                    task.dueAt?.let { stringResource(R.string.planning_due, formatDue(it)) }
                        ?: stringResource(R.string.planning_no_due)
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (remainingLabel != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                remainingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Agir sur une session : tout ce qu'il faut sans quitter l'écran ni passer par un glisser-déposer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BlockSheet(
    item: PlannedBlock,
    conflict: Boolean,
    canFinishTask: Boolean,
    onComplete: () -> Unit,
    onCompleteTask: () -> Unit,
    onFocus: () -> Unit,
    onDuration: (Int) -> Unit,
    onLater: () -> Unit,
    onTomorrow: () -> Unit,
    onOpenTask: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val visual = quadrantVisual(item.task.quadrant)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LocalizedOverlay {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(
                        visual.verb + " · " + rangeLabel(item.block.start, item.block.end, zone),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(item.task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (conflict) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                stringResource(R.string.cd_planning_conflict),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onComplete, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.focus_done))
                    }
                    FilledTonalButton(onClick = onFocus, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.planning_focus))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetSection(stringResource(R.string.planning_duration))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SessionDurations.forEach { minutes ->
                            KairosFilterChip(
                                selected = item.block.minutes == minutes,
                                onClick = { onDuration(minutes) },
                                label = formatDuration(minutes),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetSection(stringResource(R.string.planning_move))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KairosAssistChip(onClick = onLater, label = stringResource(R.string.planning_later))
                        KairosAssistChip(onClick = onTomorrow, label = stringResource(R.string.planning_tomorrow))
                        KairosAssistChip(onClick = onRemove, label = stringResource(R.string.planning_remove_session))
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KairosAssistChip(
                        onClick = onOpenTask,
                        label = stringResource(R.string.planning_open_task),
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    )
                    if (canFinishTask) {
                        KairosAssistChip(onClick = onCompleteTask, label = stringResource(R.string.planning_finish_task))
                    }
                }
            }
        }
    }
}
