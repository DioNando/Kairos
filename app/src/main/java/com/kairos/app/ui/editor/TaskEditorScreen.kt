package com.kairos.app.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Attachment
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.data.repository.AttachmentStore
import com.kairos.app.domain.ai.Classification
import com.kairos.app.domain.ai.EnhancedTask
import com.kairos.app.domain.recurrence.Recurrence
import com.kairos.app.ui.components.DateTimePickerDialogs
import com.kairos.app.ui.components.DueStatus
import com.kairos.app.ui.components.KairosAssistChip
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.MorphingCheckbox
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.NOTE_CHECKBOX_PREFIX
import com.kairos.app.ui.components.RichNoteView
import com.kairos.app.ui.components.dueStatus
import com.kairos.app.ui.components.formatBlock
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.onAccent
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.components.toggleNoteCheckbox
import com.kairos.app.ui.theme.LocalizedOverlay
import kotlinx.coroutines.launch

/**
 * Ligne de checklist en cours d'édition : état Compose local (titre, coché, échéance propre) avec une
 * clé stable pour préserver le focus/champ lors des recompositions et des suppressions.
 */
private class SubtaskField(val key: Int, title: String, done: Boolean, dueAt: Long?) {
    var title by mutableStateOf(title)
    var done by mutableStateOf(done)
    var dueAt by mutableStateOf(dueAt)

    fun toSubtask(taskId: Long) = Subtask(taskId = taskId, title = title.trim(), done = done, dueAt = dueAt)
}

/** Cible du sélecteur date/heure : l'échéance de la tâche, ou celle d'une sous-tâche. */
private sealed interface DueTarget {
    data object Task : DueTarget
    data class Sub(val field: SubtaskField) : DueTarget
}

/**
 * Page (plein écran, avec retour) de création/édition d'une tâche. `task = null` → création (dans
 * [defaultQuadrant]). Contenu en **LazyColumn** (+ imePadding) : scroll fluide, clavier ouvert.
 * Une page dédiée (plutôt qu'un ModalBottomSheet) évite le saccadement du volet en plein écran.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    task: Task?,
    defaultQuadrant: Quadrant,
    labels: List<Label>,
    urgencyThresholdHours: Int,
    now: Long,
    initialTitle: String = "",
    initialSubtasks: List<Subtask> = emptyList(),
    initialAttachments: List<Attachment> = emptyList(),
    aiEnabled: Boolean = false,
    onReformulateTask: suspend (title: String, note: String, subtasks: List<String>) -> Result<EnhancedTask> =
        { title, note, subs -> Result.success(EnhancedTask(title, note, subs)) },
    onSuggestClassify: suspend (title: String, note: String) -> Result<Classification> =
        { _, _ -> Result.failure(IllegalStateException()) },
    onSuggestSubtasks: suspend (title: String, note: String) -> Result<List<String>> =
        { _, _ -> Result.failure(IllegalStateException()) },
    onPickAttachment: suspend (Uri) -> Result<Attachment> = { Result.failure(IllegalStateException()) },
    onOpenAttachment: (Attachment) -> Unit = {},
    onBack: () -> Unit,
    onSave: (task: Task, subtasks: List<Subtask>, promoted: List<Subtask>, attachments: List<Attachment>) -> Unit,
    onDelete: (Task) -> Unit,
) {
    var title by remember { mutableStateOf(task?.title ?: initialTitle) }
    // Note en TextFieldValue : l'insertion d'une case à cocher se fait au curseur.
    var noteValue by remember { mutableStateOf(TextFieldValue(task?.note ?: "")) }
    val note = noteValue.text
    // Une note existante s'ouvre en **aperçu** (cases cochables, liens cliquables) ; vide → saisie.
    var editingNote by remember { mutableStateOf(task?.note.isNullOrBlank()) }
    var important by remember { mutableStateOf(task?.important ?: defaultQuadrant.important) }
    var urgent by remember { mutableStateOf(task?.urgent ?: defaultQuadrant.urgent) }
    var dueAt by remember { mutableStateOf(task?.dueAt) }
    var blockStart by remember { mutableStateOf(task?.blockStart) }
    var labelId by remember { mutableStateOf(task?.labelId) }
    var recurrence by remember { mutableStateOf(task?.recurrence ?: 0) }

    // Checklist : lignes éditables locales (clé stable pour Compose, indépendante des ids DB).
    val subtaskFields = remember {
        initialSubtasks.mapIndexed { index, s -> SubtaskField(index, s.title, s.done, s.dueAt) }.toMutableStateList()
    }
    var nextSubtaskKey by remember { mutableIntStateOf(initialSubtasks.size) }
    // Sous-tâches promues : retirées de la checklist, créées comme tâches à l'enregistrement.
    val promoted = remember { mutableListOf<SubtaskField>().toMutableStateList() }

    // Pièces jointes : liste complète voulue (existantes + nouvellement copiées, id 0).
    val attachments = remember { initialAttachments.toMutableStateList() }
    var attaching by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    var dueTarget by remember { mutableStateOf<DueTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Reformulation IA de la note (optionnelle).
    val scope = rememberCoroutineScope()
    var reformulating by remember { mutableStateOf(false) }
    var beforeAi by remember { mutableStateOf<EnhancedTask?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    // Suggestions IA : classement (axes) et découpage en sous-tâches.
    var classifying by remember { mutableStateOf(false) }
    var decomposing by remember { mutableStateOf(false) }

    val quadrant = Quadrant.of(important, urgent)
    val visual = quadrantVisual(quadrant)
    val status = dueStatus(dueAt, now, urgencyThresholdHours, completed = false)

    val tooLargeMessage = stringResource(R.string.attachment_too_large)
    val attachErrorMessage = stringResource(R.string.attachment_error)
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                attaching = true
                attachmentError = null
                onPickAttachment(uri).fold(
                    onSuccess = { attachments.add(it) },
                    onFailure = {
                        attachmentError = if (it is AttachmentStore.TooLargeException) tooLargeMessage else attachErrorMessage
                    },
                )
                attaching = false
            }
        }
    }

    // Enregistrement **automatique** à la sortie : plus besoin de penser à « Enregistrer » (le
    // bouton était loin en bas). En création, un titre vide n'enregistre rien (pas de tâche fantôme).
    fun commitAndExit() {
        if (title.isBlank()) {
            onBack()
            return
        }
        val base = task ?: Task(title = "")
        val subtasks = subtaskFields.filter { it.title.isNotBlank() }.map { it.toSubtask(base.id) }
        onSave(
            base.copy(
                title = title.trim(),
                note = note.trim(),
                important = important,
                urgent = urgent,
                dueAt = dueAt,
                labelId = labelId,
                recurrence = recurrence,
                blockStart = blockStart,
                blockMinutes = if (blockStart == null) 0 else base.blockMinutes,
            ),
            subtasks,
            promoted.filter { it.title.isNotBlank() }.map { it.toSubtask(0) },
            attachments.toList(),
        )
    }

    // Geste retour système → même chemin (enregistre puis quitte).
    BackHandler { commitAndExit() }

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
                    IconButton(onClick = { commitAndExit() }) {
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
                    shape = KairosFieldShape,
                    colors = kairosFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Note enrichie : saisie libre (cases `- [ ]`, liens) ou aperçu interactif.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingNote || note.isBlank()) {
                        TextField(
                            value = noteValue,
                            onValueChange = { noteValue = it },
                            label = { Text(stringResource(R.string.field_note)) },
                            shape = KairosFieldShape,
                            colors = kairosFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp),
                        )
                    } else {
                        NotePreview(
                            note = note,
                            accent = visual.accent,
                            onToggle = { line -> noteValue = TextFieldValue(toggleNoteCheckbox(note, line)) },
                            onEdit = { editingNote = true },
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (editingNote || note.isBlank()) {
                            KairosAssistChip(
                                onClick = {
                                    editingNote = true
                                    noteValue = noteValue.withCheckboxAtCursor()
                                },
                                label = stringResource(R.string.note_checkbox),
                                leadingIcon = { Icon(Icons.Rounded.CheckBox, contentDescription = null) },
                            )
                            if (note.isNotBlank()) {
                                KairosAssistChip(
                                    onClick = { editingNote = false },
                                    label = stringResource(R.string.note_preview),
                                    leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                                )
                            }
                        }
                        // Reformulation IA : visible seulement si un fournisseur est configuré.
                        if (aiEnabled) {
                            KairosAssistChip(
                                onClick = {
                                    if (!reformulating && (title.isNotBlank() || note.isNotBlank())) {
                                        val originalTitle = title
                                        val originalNote = note
                                        val originalSubs = subtaskFields.map { it.title }
                                        scope.launch {
                                            reformulating = true
                                            aiError = null
                                            onReformulateTask(originalTitle, originalNote, originalSubs).fold(
                                                onSuccess = { result ->
                                                    beforeAi = EnhancedTask(originalTitle, originalNote, originalSubs)
                                                    title = result.title
                                                    noteValue = TextFieldValue(result.note)
                                                    // Applique les libellés par index (état coché préservé).
                                                    result.subtasks.forEachIndexed { i, newTitle ->
                                                        subtaskFields.getOrNull(i)?.let { it.title = newTitle }
                                                    }
                                                },
                                                onFailure = { aiError = it.message ?: "Erreur" },
                                            )
                                            reformulating = false
                                        }
                                    }
                                },
                                label = stringResource(R.string.ai_reformulate),
                                leadingIcon = {
                                    if (reformulating) {
                                        // LoadingIndicator M3 Expressive : forme qui morphe (pas un
                                        // simple cercle qui tourne).
                                        LoadingIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                    }
                                },
                            )
                            beforeAi?.let { original ->
                                TextButton(onClick = {
                                    title = original.title
                                    noteValue = TextFieldValue(original.note)
                                    original.subtasks.forEachIndexed { i, t ->
                                        subtaskFields.getOrNull(i)?.let { it.title = t }
                                    }
                                    beforeAi = null
                                    aiError = null
                                }) { Text(stringResource(R.string.ai_revert)) }
                            }
                        }
                    }
                    aiError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Pièces jointes légères (copiées dans l'app, 10 Mo max chacune).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_attachments), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { attachment ->
                            AttachmentChip(
                                attachment = attachment,
                                onOpen = { onOpenAttachment(attachment) },
                                onRemove = { attachments.remove(attachment) },
                            )
                        }
                        KairosAssistChip(
                            onClick = { if (!attaching) pickLauncher.launch(arrayOf("*/*")) },
                            label = stringResource(R.string.attachment_add),
                            leadingIcon = {
                                if (attaching) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = null)
                                }
                            },
                        )
                    }
                    attachmentError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
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
                    // Suggestion de classement par l'IA (renseigne les deux axes).
                    if (aiEnabled) {
                        KairosAssistChip(
                            onClick = {
                                if (!classifying && title.isNotBlank()) {
                                    scope.launch {
                                        classifying = true
                                        aiError = null
                                        onSuggestClassify(title, note).fold(
                                            onSuccess = { important = it.important; urgent = it.urgent },
                                            onFailure = { aiError = it.message ?: "Erreur" },
                                        )
                                        classifying = false
                                    }
                                }
                            },
                            label = stringResource(R.string.ai_classify),
                            leadingIcon = {
                                if (classifying) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }

            // Échéance (+ créneau planifié depuis le Planning, le cas échéant).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.field_due), style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KairosAssistChip(
                            onClick = { dueTarget = DueTarget.Task },
                            label = dueAt?.let { formatDue(it) } ?: stringResource(R.string.due_none),
                            leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                        )
                        if (dueAt != null) {
                            TextButton(onClick = { dueAt = null }) { Text(stringResource(R.string.due_clear)) }
                        }
                    }
                    blockStart?.let { start ->
                        val end = start + (task?.blockDuration ?: Task.DEFAULT_BLOCK_MINUTES) * 60_000L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = visual.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                stringResource(R.string.block_planned, formatBlock(start, end)),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { blockStart = null }) { Text(stringResource(R.string.block_clear)) }
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
                            // Le chip prend la couleur de la pastille de l'étiquette ; la pastille
                            // reste visible en tête quand il n'est pas sélectionné.
                            val swatch = label.colorHex.toColor()
                            val selected = labelId == label.id
                            KairosFilterChip(
                                selected = selected,
                                onClick = { labelId = label.id },
                                label = label.name,
                                selectedContainerColor = swatch,
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(if (selected) onAccent(swatch) else swatch, CircleShape),
                                    )
                                },
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

            // Checklist (sous-tâches) : en-tête avec progression.
            item {
                val filled = subtaskFields.filter { it.title.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.field_subtasks),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (filled.isNotEmpty()) {
                            Text(
                                "${filled.count { it.done }}/${filled.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = visual.accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (filled.isNotEmpty()) {
                        // Barre ondulée M3 Expressive (comme le bandeau Pomodoro), progression animée
                        // au ressort quand on coche une sous-tâche.
                        val target = filled.count { it.done }.toFloat() / filled.size
                        val progress by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = target,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "subtask-progress",
                        )
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            color = visual.accent,
                            trackColor = visual.accent.copy(alpha = 0.18f),
                            amplitude = { 1f },
                            wavelength = 26.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(subtaskFields, key = { it.key }) { field ->
                SubtaskRow(
                    field = field,
                    accent = visual.accent,
                    now = now,
                    urgencyThresholdHours = urgencyThresholdHours,
                    onPickDue = { dueTarget = DueTarget.Sub(field) },
                    onPromote = {
                        subtaskFields.remove(field)
                        if (field.title.isNotBlank()) promoted.add(field)
                    },
                    onRemove = { subtaskFields.remove(field) },
                )
            }
            // Sous-tâches promues (en attente de l'enregistrement), annulables.
            items(promoted, key = { "promoted-${it.key}" }) { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Upgrade,
                        contentDescription = null,
                        tint = visual.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.subtask_promoted, field.title.trim()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        promoted.remove(field)
                        subtaskFields.add(field)
                    }) { Text(stringResource(R.string.action_undo)) }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { subtaskFields.add(SubtaskField(nextSubtaskKey++, "", false, null)) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.subtask_add))
                    }
                    // Découpage automatique par l'IA (ajoute les sous-tâches générées à la suite).
                    if (aiEnabled) {
                        KairosAssistChip(
                            onClick = {
                                if (!decomposing && title.isNotBlank()) {
                                    scope.launch {
                                        decomposing = true
                                        aiError = null
                                        onSuggestSubtasks(title, note).fold(
                                            onSuccess = { list ->
                                                list.forEach {
                                                    subtaskFields.add(SubtaskField(nextSubtaskKey++, it, false, null))
                                                }
                                            },
                                            onFailure = { aiError = it.message ?: "Erreur" },
                                        )
                                        decomposing = false
                                    }
                                }
                            },
                            label = stringResource(R.string.ai_decompose),
                            leadingIcon = {
                                if (decomposing) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                }
                            },
                        )
                    }
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

            // Enregistrement auto à la sortie → pas de bouton « Enregistrer ». En édition, l'action
            // « Retirer » range la tâche à l'archive (réversible) au lieu de la supprimer durement.
            if (task != null) {
                item {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.action_remove))
                    }
                }
            }
        }
    }

    // --- Confirmation de retrait (→ archive) ---------------------------------------------------

    if (confirmDelete && task != null) {
        // Strings résolues hors du dialog (fenêtre détachée qui perd l'override de langue in-app).
        val dialogTitle = stringResource(R.string.remove_task_title)
        val message = stringResource(R.string.remove_task_message, task.title)
        val confirm = stringResource(R.string.action_remove)
        val cancel = stringResource(R.string.action_cancel)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(dialogTitle) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(task) }) { Text(confirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(cancel) }
            },
        )
    }

    // --- Sélecteur date & heure (tâche ou sous-tâche) ------------------------------------------

    dueTarget?.let { target ->
        val initial = when (target) {
            DueTarget.Task -> dueAt
            is DueTarget.Sub -> target.field.dueAt
        }
        DateTimePickerDialogs(
            initial = initial,
            now = now,
            onDismiss = { dueTarget = null },
            onPicked = { picked ->
                when (target) {
                    DueTarget.Task -> dueAt = picked
                    is DueTarget.Sub -> target.field.dueAt = picked
                }
                dueTarget = null
            },
        )
    }
}

/** Marges des options du menu ⋮ d'une sous-tâche : un peu plus d'air à droite du texte. */
private val SubtaskMenuPadding = PaddingValues(start = 12.dp, end = 24.dp)

/** Insère une case à cocher vide au curseur : en début de ligne si elle est vide, sinon à la ligne. */
private fun TextFieldValue.withCheckboxAtCursor(): TextFieldValue {
    val cursor = selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)
    return if (currentLine.isBlank()) {
        val newText = text.substring(0, lineStart) + NOTE_CHECKBOX_PREFIX + text.substring(lineEnd)
        TextFieldValue(newText, TextRange(lineStart + NOTE_CHECKBOX_PREFIX.length))
    } else {
        val insert = "\n" + NOTE_CHECKBOX_PREFIX
        val newText = text.substring(0, lineEnd) + insert + text.substring(lineEnd)
        TextFieldValue(newText, TextRange(lineEnd + insert.length))
    }
}

/** Aperçu de la note (même gabarit que le champ) : cases cochables, liens cliquables, tap = éditer. */
@Composable
private fun NotePreview(note: String, accent: Color, onToggle: (Int) -> Unit, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        shape = KairosFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.field_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.note_edit),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            RichNoteView(note = note, accent = accent, onToggle = onToggle, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

/** Puce de pièce jointe : icône selon le type, nom + taille, tap = ouvrir, × = retirer. */
@Composable
private fun AttachmentChip(attachment: Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    KairosAssistChip(
        onClick = onOpen,
        label = "${attachment.displayName.ellipsize(22)} · ${formatSize(attachment.sizeBytes)}",
        leadingIcon = {
            Icon(
                if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description,
                contentDescription = null,
            )
        },
        trailingIcon = {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_remove_attachment),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
            )
        },
    )
}

private fun String.ellipsize(max: Int) = if (length <= max) this else take(max - 1) + "…"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f Mo".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024} Ko"
    else -> "$bytes o"
}

/**
 * Ligne de sous-tâche : case morphante, titre éditable, échéance propre (puce sous le titre) et menu
 * (échéance, promotion en tâche, retrait).
 */
@Composable
private fun SubtaskRow(
    field: SubtaskField,
    accent: Color,
    now: Long,
    urgencyThresholdHours: Int,
    onPickDue: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Case morphante M3 Expressive (cercle → « cookie »), teintée par l'accent du quadrant
        // résultant — cohérent avec les cards de classement plus haut.
        MorphingCheckbox(
            checked = field.done,
            accent = accent,
            onClick = { field.done = !field.done },
            modifier = Modifier.size(40.dp),
            contentDescription = stringResource(if (field.done) R.string.cd_mark_active else R.string.cd_mark_done),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextField(
                value = field.title,
                onValueChange = { field.title = it },
                placeholder = { Text(stringResource(R.string.subtask_hint)) },
                singleLine = true,
                shape = KairosFieldShape,
                colors = kairosFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            field.dueAt?.let { due ->
                val status = dueStatus(due, now, urgencyThresholdHours, field.done)
                Row(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPickDue)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = when (status) {
                        DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                        DueStatus.SOON -> accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(formatDue(due), style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_subtask_menu),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                LocalizedOverlay {
                    DropdownMenuItem(
                        contentPadding = SubtaskMenuPadding,
                        text = { Text(stringResource(R.string.subtask_due)) },
                        leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                        onClick = { menuOpen = false; onPickDue() },
                    )
                    if (field.dueAt != null) {
                        DropdownMenuItem(
                            contentPadding = SubtaskMenuPadding,
                            text = { Text(stringResource(R.string.due_clear)) },
                            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                            onClick = { menuOpen = false; field.dueAt = null },
                        )
                    }
                    DropdownMenuItem(
                        contentPadding = SubtaskMenuPadding,
                        text = { Text(stringResource(R.string.subtask_promote)) },
                        leadingIcon = { Icon(Icons.Rounded.Upgrade, contentDescription = null) },
                        enabled = field.title.isNotBlank(),
                        onClick = { menuOpen = false; onPromote() },
                    )
                    DropdownMenuItem(
                        contentPadding = SubtaskMenuPadding,
                        text = { Text(stringResource(R.string.cd_remove_subtask)) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
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
