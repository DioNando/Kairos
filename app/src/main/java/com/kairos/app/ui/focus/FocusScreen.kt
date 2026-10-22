package com.kairos.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.ui.components.formatDue
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.components.toColor

/** Mode « Et maintenant ? » : sort LA tâche à faire en premier, indépendamment des quadrants. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    tasks: List<Task>,
    labels: List<Label>,
    onBack: () -> Unit,
    onComplete: (Task) -> Unit,
    onOpenTask: (Task) -> Unit,
) {
    val ranked = remember(tasks) { FocusSelector.rank(tasks) }
    val next = ranked.firstOrNull()
    val upNext = remember(ranked) { ranked.drop(1).take(3) }
    val labelsById = remember(labels) { labels.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        if (next == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.focus_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Contenu ancré en haut (plutôt que flottant au centre) : LA tâche prioritaire, puis
            // un aperçu « À suivre ». Le mode Focus ne montre qu'une tâche à la fois, volontairement.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val visual = quadrantVisual(next.quadrant)
                val label = next.labelId?.let { labelsById[it] }
                Text(
                    stringResource(R.string.focus_prompt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = visual.accent.copy(alpha = 0.14f)),
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(visual.icon, contentDescription = null, tint = visual.accent)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    visual.verb,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = visual.accent,
                                )
                            }
                            Text(
                                next.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (next.note.isNotBlank()) {
                                Text(next.note, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (label != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(label.colorHex.toColor(), CircleShape),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(label.name, style = MaterialTheme.typography.labelMedium)
                                    if (next.dueAt != null) Spacer(Modifier.size(12.dp))
                                }
                                if (next.dueAt != null) {
                                    Text(
                                        formatDue(next.dueAt),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onComplete(next) }) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.focus_done))
                    }
                    FilledTonalButton(onClick = { onOpenTask(next) }) {
                        Text(stringResource(R.string.focus_open))
                    }
                }

                // Aperçu « À suivre » : les prochaines tâches, sans les sortir de la file.
                if (upNext.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.focus_up_next),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        upNext.forEach { task ->
                            val v = quadrantVisual(task.quadrant)
                            val taskLabel = task.labelId?.let { labelsById[it] }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onOpenTask(task) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Icône du quadrant (comme la carte prioritaire) au lieu d'une pastille.
                                Icon(v.icon, contentDescription = null, tint = v.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                // Étiquette éventuelle : juste la puce colorée (pas le nom).
                                if (taskLabel != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Box(modifier = Modifier.size(8.dp).background(taskLabel.colorHex.toColor(), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
