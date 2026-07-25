package com.kairos.app.ui.archive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Task
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor

/**
 * Archive : les tâches sorties du board, qu'elles soient **terminées** (accomplies ✓) ou
 * **retirées** (glissées hors sans être faites 🗄). Un badge distingue les deux. On peut les
 * **restaurer** (elles reviennent dans leur quadrant) ou les **supprimer définitivement** (avec
 * confirmation) — c'est le **seul** endroit où une suppression est irréversible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    tasks: List<Task>,
    labels: List<Label>,
    onBack: () -> Unit,
    onRestore: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onClearAll: () -> Unit,
) {
    val archived = remember(tasks) {
        tasks.filter { it.isArchived }.sortedByDescending { it.archivedAt ?: 0L }
    }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (archived.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.cd_clear_archive),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (archived.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.archive_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(archived, key = { it.id }) { task ->
                    val visual = quadrantVisual(task.quadrant)
                    val label = task.labelId?.let { labelsById[it] }
                    val done = task.isCompleted
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(visual.accent, CircleShape))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                // Barré uniquement si accomplie ; une tâche retirée n'a pas été faite.
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(done = done)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    visual.verb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = visual.accent,
                                )
                                if (label != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Box(modifier = Modifier.size(6.dp).background(label.colorHex.toColor(), CircleShape))
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        label.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onRestore(task) }) {
                            Icon(
                                Icons.Rounded.Restore,
                                contentDescription = stringResource(R.string.cd_restore_task, task.title),
                                tint = visual.accent,
                            )
                        }
                        IconButton(onClick = { pendingDelete = task }) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = stringResource(R.string.cd_delete_forever, task.title),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message, task.title)) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(task) }) {
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

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.clear_archive_title)) },
            text = { Text(stringResource(R.string.clear_archive_message)) },
            confirmButton = {
                TextButton(onClick = { confirmClearAll = false; onClearAll() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Petit badge distinguant une tâche **terminée** (coche, teinte primaire) d'une tâche **retirée**
 * (archive, teinte neutre) dans la liste de l'archive.
 */
@Composable
private fun StatusBadge(done: Boolean) {
    val (icon, tint, textRes) = if (done) {
        Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, R.string.archive_status_done)
    } else {
        Triple(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.onSurfaceVariant, R.string.archive_status_removed)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(stringResource(textRes), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
