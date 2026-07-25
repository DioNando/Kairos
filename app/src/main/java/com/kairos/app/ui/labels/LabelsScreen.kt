package com.kairos.app.ui.labels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.onAccent
import com.kairos.app.ui.components.toColor

private val LabelColors = listOf(
    "#E5645A", "#4FA5A0", "#E0A24E", "#9C9AA6",
    "#7E6B9E", "#81B29A", "#E07A5F", "#5C7CFA",
)

/** Gestion des étiquettes (catégories) : liste + création/édition + suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    labels: List<Label>,
    onBack: () -> Unit,
    onSave: (Label) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<Label?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Label?>(null) }

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
            items(labels, key = { it.id }) { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, KairosFieldShape)
                        .clickable { editing = label; showEditor = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(label.colorHex.toColor(), CircleShape),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(label.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { pendingDelete = label }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_delete_label, label.name))
                    }
                }
            }
            // Ajout via un bouton compact centré en bas de liste (pas pleine largeur).
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.label_add))
                    }
                }
            }
        }
    }

    if (showEditor) {
        LabelEditorDialog(
            label = editing,
            onDismiss = { showEditor = false },
            onConfirm = { showEditor = false; onSave(it) },
        )
    }

    pendingDelete?.let { label ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_label_title)) },
            text = { Text(stringResource(R.string.delete_label_message, label.name)) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(label.id) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LabelEditorDialog(
    label: Label?,
    onDismiss: () -> Unit,
    onConfirm: (Label) -> Unit,
) {
    var name by remember { mutableStateOf(label?.name ?: "") }
    var colorHex by remember { mutableStateOf(label?.colorHex ?: LabelColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (label == null) R.string.label_new else R.string.label_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_label_name)) },
                    singleLine = true,
                    shape = KairosFieldShape,
                    colors = kairosFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_label_color), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabelColors.forEach { hex ->
                        val selected = hex == colorHex
                        val swatch = hex.toColor()
                        Box(
                            modifier = Modifier
                                .size(30.dp)
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
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = label ?: Label(name = "", colorHex = colorHex)
                    onConfirm(base.copy(name = name.trim(), colorHex = colorHex))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
