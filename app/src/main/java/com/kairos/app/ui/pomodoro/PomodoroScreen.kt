@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kairos.app.ui.pomodoro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.ui.components.KairosAssistChip
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.theme.LocalizedOverlay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    tasks: List<Task> = emptyList(),
    autoStart: Boolean = false,
    liquid: Boolean = true,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linkedTaskId by viewModel.linkedTaskId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Demande la permission notifications (Android 13+) à l'ouverture, pour les alertes de fin de cycle.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Démarrage immédiat depuis la notification « Démarrer un Pomodoro » : lance une phase Focus.
    // Ne perturbe pas une session déjà en cours, et ne se déclenche qu'une fois.
    var autoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(autoStart) {
        if (autoStart && !autoStarted && !state.active) {
            viewModel.selectPhase(PomodoroPhase.FOCUS)
            viewModel.start()
            autoStarted = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loggedMinutes.collect { minutes ->
            snackbarHostState.showSnackbar(context.getString(R.string.pomo_session_logged, minutes))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pomodoro_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Sélecteur de phase : **ButtonGroup connecté** (M3 Expressive) — les segments partagent
            // une forme continue, le sélectionné passe en primary et « squish » au press. Verrouillé
            // pendant une session active pour ne pas réinitialiser le timer.
            PhaseSelector(
                selected = state.phase,
                enabled = !state.active,
                onSelect = { viewModel.selectPhase(it) },
                modifier = Modifier.padding(top = 12.dp),
            )

            // Lien vers une tâche : le temps des focus terminés lui est crédité.
            LinkedTaskSelector(
                tasks = tasks.filter { it.isActive },
                linkedTaskId = linkedTaskId,
                onSelect = { viewModel.setLinkedTask(it) },
            )

            if (liquid) {
                TimerLiquid(
                    progress = state.progress,
                    timeText = formatClock(state.remainingSeconds),
                    running = state.isRunning,
                )
            } else {
                TimerWavy(
                    progress = state.progress,
                    timeText = formatClock(state.remainingSeconds),
                    running = state.isRunning,
                )
            }

            Text(
                text = stringResource(R.string.pomo_cycles) + " : ${state.completedFocus}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Contrôles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (state.isRunning) viewModel.pause() else viewModel.start() },
                ) {
                    Icon(
                        if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                    Text(
                        text = if (state.isRunning) stringResource(R.string.pomo_pause)
                        else stringResource(R.string.pomo_start),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                FilledTonalButton(onClick = viewModel::reset) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text(stringResource(R.string.pomo_reset), modifier = Modifier.padding(start = 6.dp))
                }
            }

            Text(
                text = stringResource(R.string.pomo_focus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sélecteur de tâche liée : une puce ouvre un **bottom sheet** listant les tâches actives
 * **groupées par quadrant** (chacune préfixée d'une barre à l'accent de son quadrant). « Aucune »
 * délie la session. La tâche liée est surlignée par un **fond tonal à l'accent du quadrant** (jamais
 * un assombrissement) + une coche — visible et coloré en clair comme en sombre. Le temps de chaque
 * focus terminé est ensuite crédité à la tâche choisie (cumul dans son temps de concentration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedTaskSelector(
    tasks: List<Task>,
    linkedTaskId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val linked = tasks.firstOrNull { it.id == linkedTaskId }
    KairosAssistChip(
        onClick = { expanded = true },
        label = linked?.title ?: stringResource(R.string.pomo_link_none),
        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
    )
    if (expanded) {
        LinkedTaskSheet(
            tasks = tasks,
            linkedTaskId = linkedTaskId,
            onSelect = { onSelect(it); expanded = false },
            onDismiss = { expanded = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedTaskSheet(
    tasks: List<Task>,
    linkedTaskId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    // Groupé par quadrant dans l'ordre de la matrice (Faire → Planifier → Déléguer → Supprimer) :
    // les tâches les plus prioritaires remontent en tête, naturel pour choisir sur quoi se concentrer.
    val groups = Quadrant.entries
        .map { q -> q to tasks.filter { it.quadrant == q } }
        .filter { it.second.isNotEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Le sheet est rendu dans une fenêtre détachée qui perd l'override de langue in-app :
        // LocalizedOverlay le ré-applique (cf. LocalizedOverlay.kt).
        LocalizedOverlay {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 16.dp,
            ),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.pomo_link_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "none") {
                NoneRow(
                    selected = linkedTaskId == null,
                    onClick = { onSelect(null) },
                )
            }
            groups.forEach { (quadrant, quadrantTasks) ->
                item(key = "header-${quadrant.name}") {
                    QuadrantHeader(quadrant)
                }
                items(quadrantTasks, key = { it.id }) { task ->
                    LinkedTaskRow(
                        task = task,
                        selected = task.id == linkedTaskId,
                        onClick = { onSelect(task.id) },
                    )
                }
            }
        }
        }
    }
}

/** En-tête de groupe : pastille d'icône + verbe du quadrant, à son accent. */
@Composable
private fun QuadrantHeader(quadrant: Quadrant) {
    val visual = quadrantVisual(quadrant)
    Row(
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(18.dp))
        Text(
            text = visual.verb.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = visual.accent,
        )
    }
}

/** Ligne « Aucune tâche liée » : icône lien-coupé, surlignée quand la session n'est liée à rien. */
@Composable
private fun NoneRow(selected: Boolean, onClick: () -> Unit) {
    val highlight = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) highlight.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.LinkOff,
            contentDescription = null,
            tint = if (selected) highlight else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.pomo_link_none),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) highlight else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = highlight)
    }
}

/**
 * Ligne de tâche : barre verticale à l'accent du quadrant + titre. Sélectionnée = fond tonal
 * (accent alpha) + coche à l'accent, jamais un assombrissement.
 */
@Composable
private fun LinkedTaskRow(task: Task, selected: Boolean, onClick: () -> Unit) {
    val accent = quadrantVisual(task.quadrant).accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 26.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
    }
}

/**
 * Sélecteur de phase en **ButtonGroup connecté** (M3 Expressive) : trois `ToggleButton` aux formes
 * connectées (coins externes arrondis, jonctions internes serrées) qui se compressent au press. Le
 * segment actif passe en `primary`. Tout le groupe est grisé pendant une session active.
 */
@Composable
private fun PhaseSelector(
    selected: PomodoroPhase,
    enabled: Boolean,
    onSelect: (PomodoroPhase) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Libellés courts : les 3 segments sont étroits, « Pause courte/longue » y serait tronqué à
    // « Pause » (indistinguable). On utilise des libellés compacts dédiés au sélecteur.
    val options = listOf(
        PomodoroPhase.FOCUS to R.string.pomo_seg_focus,
        PomodoroPhase.SHORT_BREAK to R.string.pomo_seg_short,
        PomodoroPhase.LONG_BREAK to R.string.pomo_seg_long,
    )
    val colors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, pair ->
            val (phase, res) = pair
            ToggleButton(
                checked = selected == phase,
                onCheckedChange = { if (it) onSelect(phase) },
                enabled = enabled,
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(res), maxLines = 1)
            }
        }
    }
}

/**
 * Minuteur « cercle liquide » : un disque qui se vide d'un liquide ondulé à mesure que le temps
 * s'écoule (niveau = `1 - progress`). Deux vagues déphasées donnent de la profondeur, et l'ondulation
 * s'anime en continu (plus vive quand la session tourne). Le temps reste au centre.
 */
@Composable
private fun TimerLiquid(progress: Float, timeText: String, running: Boolean) {
    val liquidColor = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.surfaceVariant
    val frame = MaterialTheme.colorScheme.outlineVariant

    val level by animateFloatAsState(
        targetValue = (1f - progress).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 950, easing = LinearEasing),
        label = "liquid-level",
    )
    val transition = rememberInfiniteTransition(label = "liquid-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (running) 1600 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquid-phase",
    )

    val timeColor = MaterialTheme.colorScheme.onSurface
    val timeColorInLiquid = MaterialTheme.colorScheme.onPrimary
    val timeStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(240.dp),
    ) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val circle = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }

        drawCircle(color = glass, radius = r, center = center)

        val surfaceY = cy + r - level * (2f * r)
        val amp = if (level <= 0.001f || level >= 0.999f) 0f else 7.dp.toPx()

        fun wave(shift: Float, extraAmp: Float): Path = Path().apply {
            val left = cx - r
            val right = cx + r
            val steps = 28
            moveTo(left, cy + r + 2f)
            lineTo(left, surfaceY)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = left + (right - left) * t
                val y = surfaceY + (amp + extraAmp) * sin(phase + shift + t * 2f * PI.toFloat() * 1.5f)
                lineTo(x, y)
            }
            lineTo(right, cy + r + 2f)
            close()
        }

        val frontWave = wave(0f, 0f)
        clipPath(circle) {
            drawPath(wave(PI.toFloat(), 2.dp.toPx()), liquidColor.copy(alpha = 0.35f))
            drawPath(frontWave, liquidColor)
        }

        drawCircle(color = frame, radius = r, center = center, style = Stroke(width = 4.dp.toPx()))

        val layout = measurer.measure(timeText, timeStyle)
        val textTopLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
        drawText(layout, color = timeColor, topLeft = textTopLeft)
        clipPath(frontWave) {
            drawText(layout, color = timeColorInLiquid, topLeft = textTopLeft)
        }
    }
}

/**
 * Variante « anneau » en **CircularWavyProgressIndicator** (M3 Expressive) : l'anneau de progression
 * est **ondulé**, l'amplitude de la vague monte quand la session tourne et retombe à plat en pause.
 * Le temps reste au centre.
 */
@Composable
private fun TimerWavy(progress: Float, timeText: String, running: Boolean) {
    val anim by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 950, easing = LinearEasing),
        label = "wavy-progress",
    )
    val amplitude by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "wavy-amplitude",
    )
    val stroke = with(LocalDensity.current) {
        Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
    }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { anim },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { amplitude },
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Formate des secondes restantes en mm:ss. */
private fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
