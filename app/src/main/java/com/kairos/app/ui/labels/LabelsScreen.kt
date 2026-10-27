package com.kairos.app.ui.labels

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.SavedFilter
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.onAccent
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.LocalizedOverlay

private val LabelColors = listOf(
    // Rouges / oranges / jaunes
    "#E5645A", "#E07A5F", "#F28C38", "#E0A24E", "#E9C46A",
    // Verts / turquoises
    "#A3B86C", "#81B29A", "#4CAF7D", "#4FA5A0", "#3BA7C9",
    // Bleus / violets
    "#5C7CFA", "#4361EE", "#7E6B9E", "#9B5DE5", "#C77DFF",
    // Roses / neutres
    "#F06292", "#D6336C", "#A1887F", "#9C9AA6", "#607D8B",
)

/**
 * Gestion des étiquettes (catégories) : liste **regroupée** par groupe, épinglage (filtre rapide sur
 * la matrice), création/édition + suppression, et gestion des **filtres enregistrés**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    labels: List<Label>,
    onBack: () -> Unit,
    onSave: (Label) -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePin: (Label) -> Unit = {},
    savedFilters: List<SavedFilter> = emptyList(),
    onDeleteFilter: (Long) -> Unit = {},
) {
    var editing by remember { mutableStateOf<Label?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Label?>(null) }
    // Ordre de la base conservé (épinglées, sans groupe, puis par groupe) : on coupe en sections.
    val sections = remember(labels) { labels.groupBy { if (it.pinned) null else it.groupName } }
    val groups = remember(labels) { labels.map { it.groupName }.filter { it.isNotBlank() }.distinct().sorted() }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val pinnedHeader = stringResource(R.string.labels_pinned)
    val ungroupedHeader = stringResource(R.string.labels_ungrouped)

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.labels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { (group, sectionLabels) ->
                // En-têtes : « Épinglées », nom du groupe, « Sans groupe » (seulement s'il y a d'autres sections).
                val header = when {
                    group == null -> pinnedHeader
                    group.isBlank() -> if (sections.size > 1) ungroupedHeader else null
                    else -> group
                }
                if (header != null) {
                    item(key = "header-${group ?: "pinned"}") { SectionHeader(header) }
                }
                items(sectionLabels, key = { it.id }) { label ->
                    LabelRow(
                        label = label,
                        onEdit = { editing = label; showEditor = true },
                        onTogglePin = { onTogglePin(label) },
                        onDelete = { pendingDelete = label },
                    )
                }
            }
            // Ajout via un bouton compact centré en bas de liste (pas pleine largeur).
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.label_add))
                    }
                }
            }

            // Filtres enregistrés (créés depuis la recherche de la matrice).
            item(key = "filters-header") { SectionHeader(stringResource(R.string.saved_filters_title)) }
            if (savedFilters.isEmpty()) {
                item(key = "filters-empty") {
                    Text(
                        stringResource(R.string.saved_filters_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(savedFilters, key = { "filter-${it.id}" }) { filter ->
                val label = filter.labelId?.let { labelsById[it] }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, KairosFieldShape)
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(filter.name, style = MaterialTheme.typography.bodyLarge)
                        val details = listOfNotNull(
                            filter.query.takeIf { it.isNotBlank() }?.let { "« $it »" },
                            label?.name,
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onDeleteFilter(filter.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_delete_filter, filter.name))
                    }
                }
            }
            item { Spacer(Modifier.size(16.dp)) }
        }
    }

    if (showEditor) {
        LabelEditorDialog(
            label = editing,
            groups = groups,
            onDismiss = { showEditor = false },
            onConfirm = { showEditor = false; onSave(it) },
        )
    }

    pendingDelete?.let { label ->
        // Strings résolues hors du dialog (fenêtre détachée qui perd l'override de langue in-app).
        val title = stringResource(R.string.delete_label_title)
        val message = stringResource(R.string.delete_label_message, label.name)
        val confirm = stringResource(R.string.action_delete)
        val cancel = stringResource(R.string.action_cancel)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(label.id) }) { Text(confirm) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(cancel) }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun LabelRow(label: Label, onEdit: () -> Unit, onTogglePin: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, KairosFieldShape)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(label.colorHex.toColor(), CircleShape),
        )
        Spacer(Modifier.size(12.dp))
        Text(label.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onTogglePin) {
            Icon(
                if (label.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(if (label.pinned) R.string.cd_unpin_label else R.string.cd_pin_label),
                tint = if (label.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_delete_label, label.name))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelEditorDialog(
    label: Label?,
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Label) -> Unit,
) {
    var name by remember { mutableStateOf(label?.name ?: "") }
    var colorHex by remember { mutableStateOf(label?.colorHex ?: LabelColors.first()) }
    var group by remember { mutableStateOf(label?.groupName ?: "") }
    var pinned by remember { mutableStateOf(label?.pinned ?: false) }

    // Strings/contenu résolus hors du dialog (fenêtre détachée qui perd l'override de langue in-app).
    val titleText = stringResource(if (label == null) R.string.label_new else R.string.label_edit)
    val saveLabel = stringResource(R.string.action_save)
    val cancelLabel = stringResource(R.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
          LocalizedOverlay {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_label_name)) },
                    singleLine = true,
                    shape = KairosFieldShape,
                    colors = kairosFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(R.string.field_label_group)) },
                    singleLine = true,
                    shape = KairosFieldShape,
                    colors = kairosFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Groupes existants : un tap pour réutiliser (évite les doublons « Pro » / « pro »).
                if (groups.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEach { g ->
                            KairosFilterChip(
                                selected = group.trim().equals(g, ignoreCase = true),
                                onClick = { group = if (group.trim().equals(g, ignoreCase = true)) "" else g },
                                label = g,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_label_pinned), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.field_label_pinned_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                Text(stringResource(R.string.field_label_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabelColors.forEach { hex ->
                        val selected = hex == colorHex
                        val swatch = hex.toColor()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(swatch, CircleShape)
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Sélection matérialisée par une coche (pas de bordure — style tonal).
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = onAccent(swatch),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
          }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = label ?: Label(name = "", colorHex = colorHex)
                    onConfirm(base.copy(name = name.trim(), colorHex = colorHex, groupName = group.trim(), pinned = pinned))
                },
            ) { Text(saveLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}
