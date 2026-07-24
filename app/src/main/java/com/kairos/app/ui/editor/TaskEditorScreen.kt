package com.kairos.app.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.domain.recurrence.Recurrence
import com.kairos.app.ui.components.KairosAssistChip
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.DueStatus
import com.kairos.app.ui.components.dueStatus
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.quadrantVisual
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ligne de checklist en cours d'édition : état Compose local (titre + coché) avec une clé stable
 * pour préserver le focus/champ lors des recompositions et des suppressions.
 */
private class SubtaskField(val key: Int, title: String, done: Boolean) {
    var title by mutableStateOf(title)
    var done by mutableStateOf(done)
}

/**
 * Page (plein écran, avec retour) de création/édition d'une tâche. `task = null` → création (dans
 * [defaultQuadrant]). Contenu en **LazyColumn** (+ imePadding) : scroll fluide, clavier ouvert.
 * Une page dédiée (plutôt qu'un ModalBottomSheet) évite le saccadement du volet en plein écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    task: Task?,
    defaultQuadrant: Quadrant,
    labels: List<Label>,
    urgencyThresholdHours: Int,
    now: Long,
    initialTitle: String = "",
    initialSubtasks: List<Subtask> = emptyList(),
    onBack: () -> Unit,
    onSave: (Task, List<Subtask>) -> Unit,
    onDelete: (Task) -> Unit,
) {
    var title by remember { mutableStateOf(task?.title ?: initialTitle) }
    var note by remember { mutableStateOf(task?.note ?: "") }
    var important by remember { mutableStateOf(task?.important ?: defaultQuadrant.important) }
    var urgent by remember { mutableStateOf(task?.urgent ?: defaultQuadrant.urgent) }
    var dueAt by remember { mutableStateOf(task?.dueAt) }
    var labelId by remember { mutableStateOf(task?.labelId) }
    var recurrence by remember { mutableStateOf(task?.recurrence ?: 0) }

    // Checklist : lignes éditables locales (clé stable pour Compose, indépendante des ids DB).
    val subtaskFields = remember {
        initialSubtasks.mapIndexed { index, s -> SubtaskField(index, s.title, s.done) }.toMutableStateList()
    }
    var nextSubtaskKey by remember { mutableIntStateOf(initialSubtasks.size) }

    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<Long?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val quadrant = Quadrant.of(important, urgent)
    val visual = quadrantVisual(quadrant)
    val status = dueStatus(dueAt, now, urgencyThresholdHours, completed = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (task == null) R.string.editor_new else R.string.editor_edit),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Consomme les insets déjà appliqués (barre de nav) pour que imePadding n'ajoute que
                // le surplus du clavier — sinon un gap blanc = hauteur de la barre de nav reste
                // entre le contenu et le clavier.
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    shape = com.kairos.app.ui.components.KairosFieldShape,
                    colors = com.kairos.app.ui.components.kairosFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    shape = com.kairos.app.ui.components.KairosFieldShape,
                    colors = com.kairos.app.ui.components.kairosFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                )
            }

            // Axes → quadrant vivant. Deux cards morphantes (style QuickAdd) dont la couleur suit le
            // quadrant résultant (Do / Schedule / Delegate / Eliminate) et bascule en direct.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_classify), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClassifyToggleCard(
                            label = stringResource(R.string.axis_important),
                            icon = Icons.Rounded.PriorityHigh,
                            selected = important,
                            accent = visual.accent,
                            onClick = { important = !important },
                            modifier = Modifier.weight(1f),
                        )
                        ClassifyToggleCard(
                            label = stringResource(R.string.axis_urgent),
                            icon = Icons.Rounded.Bolt,
                            selected = urgent,
                            accent = visual.accent,
                            onClick = { urgent = !urgent },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.editor_quadrant_hint, visual.verb, visual.subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Échéance.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_due), style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KairosAssistChip(
                            onClick = { showDatePicker = true },
                            label = dueAt?.let { formatDue(it) } ?: stringResource(R.string.due_none),
                            leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                        )
                        if (dueAt != null) {
                            TextButton(onClick = { dueAt = null }) { Text(stringResource(R.string.due_clear)) }
                        }
                    }
                    // Suggestion animée : échéance proche mais tâche non urgente.
                    AnimatedVisibility(visible = status != DueStatus.NONE && status != DueStatus.UPCOMING && !urgent) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.suggest_urgent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { urgent = true }) {
                                Text(stringResource(R.string.suggest_urgent_action))
                            }
                        }
                    }
                }
            }

            // Étiquette.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KairosFilterChip(
                            selected = labelId == null,
                            onClick = { labelId = null },
                            label = stringResource(R.string.label_none),
                        )
                        labels.forEach { label ->
                            KairosFilterChip(
                                selected = labelId == label.id,
                                onClick = { labelId = label.id },
                                label = label.name,
                            )
                        }
                    }
                }
            }

            // Récurrence : régénère une occurrence à la complétion.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_recurrence), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val options = listOf(
                            Recurrence.NONE to R.string.recurrence_none,
                            Recurrence.DAILY to R.string.recurrence_daily,
                            Recurrence.WEEKLY to R.string.recurrence_weekly,
                            Recurrence.MONTHLY to R.string.recurrence_monthly,
                        )
                        options.forEach { (option, res) ->
                            KairosFilterChip(
                                selected = recurrence == option.ordinal,
                                onClick = { recurrence = option.ordinal },
                                label = stringResource(res),
                            )
                        }
                    }
                }
            }

            // Checklist (sous-tâches).
            item { Text(stringResource(R.string.field_subtasks), style = MaterialTheme.typography.labelLarge) }
            items(subtaskFields, key = { it.key }) { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { field.done = !field.done }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (field.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = stringResource(
                                if (field.done) R.string.cd_mark_active else R.string.cd_mark_done,
                            ),
                            tint = if (field.done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    TextField(
                        value = field.title,
                        onValueChange = { field.title = it },
                        placeholder = { Text(stringResource(R.string.subtask_hint)) },
                        singleLine = true,
                        shape = com.kairos.app.ui.components.KairosFieldShape,
                        colors = com.kairos.app.ui.components.kairosFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { subtaskFields.remove(field) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_subtask),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { subtaskFields.add(SubtaskField(nextSubtaskKey++, "", false)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.subtask_add))
                }
            }

            // Temps de concentration cumulé (Pomodoro lié), en lecture seule.
            if (task != null && task.focusSeconds > 0) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.focus_time_total, task.focusSeconds / 60),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                // Supprimer à gauche (destructif), Enregistrer compact à droite.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (task != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }

                    Button(
                        onClick = {
                            val base = task ?: Task(title = "")
                            val subtasks = subtaskFields
                                .filter { it.title.isNotBlank() }
                                .map { Subtask(taskId = base.id, title = it.title.trim(), done = it.done) }
                            onSave(
                                base.copy(
                                    title = title.trim(),
                                    note = note.trim(),
                                    important = important,
                                    urgent = urgent,
                                    dueAt = dueAt,
                                    labelId = labelId,
                                    recurrence = recurrence,
                                ),
                                subtasks,
                            )
                        },
                        enabled = title.isNotBlank(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }

    // --- Confirmation de suppression -----------------------------------------------------------

    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message, task.title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(task) }) {
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

    // --- Sélecteurs date & heure ---------------------------------------------------------------

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dueAt ?: now,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDate = dateState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val existing = dueAt?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
        val timeState = rememberTimePickerState(
            initialHour = existing?.hour ?: 9,
            initialMinute = existing?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = pendingDate ?: now
                    val day = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val dt = LocalDateTime.of(
                        LocalDate.of(day.year, day.month, day.dayOfMonth),
                        LocalTime.of(timeState.hour, timeState.minute),
                    )
                    dueAt = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

/**
 * Card d'axe (Important / Urgent) sélectionnable, style **shape-morphing** repris du QuickAdd. La
 * pastille d'icône morphe (carré → « cookie ») à la sélection et **la couleur suit le quadrant
 * résultant** ([accent]) : bascule douce entre Do / Schedule / Delegate / Eliminate. Sans bordure.
 */
@Composable
private fun ClassifyToggleCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedAccent by animateColorAsState(accent, label = "classify-accent")
    val background by animateColorAsState(
        targetValue = if (selected) animatedAccent.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "classify-bg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MorphingIconPill(icon = icon, accent = animatedAccent, selected = selected)
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
