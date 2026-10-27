package com.kairos.app.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.components.quadrantVisual

/** Thèmes du guide, chacun ouvrant une sous-page détaillée. */
private enum class GuideTopic(val icon: ImageVector, val titleRes: Int, val subRes: Int) {
    MATRIX(Icons.Rounded.GridView, R.string.guide_matrix_title, R.string.guide_matrix_sub),
    CREATE(Icons.Rounded.EditNote, R.string.guide_create_title, R.string.guide_create_sub),
    DAILY(Icons.Rounded.Swipe, R.string.guide_daily_title, R.string.guide_daily_sub),
    FOCUS(Icons.Rounded.CenterFocusStrong, R.string.guide_focus_title, R.string.guide_focus_sub),
    PLANNING(Icons.Rounded.CalendarMonth, R.string.guide_planning_title, R.string.guide_planning_sub),
    WEAR(Icons.Rounded.Watch, R.string.guide_wear_title, R.string.guide_wear_sub),
    AI(Icons.Rounded.AutoAwesome, R.string.guide_ai_title, R.string.guide_ai_sub),
    SETTINGS(Icons.Rounded.Palette, R.string.guide_settings_title, R.string.guide_settings_sub),
}

/**
 * Guide de l'app : une page d'accueil (hub) qui liste des thèmes, chacun ouvrant une **sous-page**
 * détaillée. Version consultable à tout moment de l'onboarding. Le retour revient au hub avant de
 * quitter l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    var topic by remember { mutableStateOf<GuideTopic?>(null) }

    // Sur une sous-page, le retour système revient d'abord au hub.
    BackHandler(enabled = topic != null) { topic = null }

    val title = topic?.let { stringResource(it.titleRes) } ?: stringResource(R.string.guide_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (topic != null) topic = null else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (topic) {
                null -> GuideHub(onSelect = { topic = it })
                GuideTopic.MATRIX -> MatrixDetail()
                GuideTopic.CREATE -> CreateDetail()
                GuideTopic.DAILY -> DailyDetail()
                GuideTopic.FOCUS -> FocusDetail()
                GuideTopic.PLANNING -> PlanningDetail()
                GuideTopic.WEAR -> WearDetail()
                GuideTopic.AI -> AiDetail()
                GuideTopic.SETTINGS -> SettingsDetail()
            }
        }
    }
}

// --- Hub -------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.GuideHub(onSelect: (GuideTopic) -> Unit) {
    Spacer(Modifier.height(2.dp))
    Text(
        stringResource(R.string.guide_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GuideTopic.entries.forEach { topic ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(18.dp))
                .clickable { onSelect(topic) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(topic.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(topic.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(topic.subRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Blocs réutilisables ---------------------------------------------------------------------------

/** Paragraphe de texte courant. */
@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Point détaillé : icône tonale + titre + corps. */
@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Encart d'astuce (fond tonal primary). */
@Composable
private fun Tip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("💡", style = MaterialTheme.typography.titleMedium)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// --- Sous-pages ------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.MatrixDetail() {
    Para(stringResource(R.string.guide_matrix_p1))
    Para(stringResource(R.string.guide_matrix_p2))
    // Mini-matrice 2×2 avec, pour chaque quadrant, quoi en faire.
    listOf(
        Quadrant.DO to R.string.guide_matrix_do,
        Quadrant.SCHEDULE to R.string.guide_matrix_schedule,
        Quadrant.DELEGATE to R.string.guide_matrix_delegate,
        Quadrant.ELIMINATE to R.string.guide_matrix_eliminate,
    ).forEach { (quadrant, bodyRes) ->
        QuadrantExplain(quadrant, stringResource(bodyRes))
    }
    Tip(stringResource(R.string.guide_matrix_tip))
}

@Composable
private fun QuadrantExplain(quadrant: Quadrant, body: String) {
    val visual = quadrantVisual(quadrant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${visual.verb} · ${visual.subtitle}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = visual.accent,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ColumnScope.CreateDetail() {
    Para(stringResource(R.string.guide_create_p1))
    Point(Icons.Rounded.PriorityHigh, stringResource(R.string.guide_create_classify_t), stringResource(R.string.guide_create_classify_b))
    Point(Icons.Rounded.Event, stringResource(R.string.guide_create_due_t), stringResource(R.string.guide_create_due_b))
    Point(Icons.Rounded.Label, stringResource(R.string.guide_create_label_t), stringResource(R.string.guide_create_label_b))
    Point(Icons.Rounded.Repeat, stringResource(R.string.guide_create_rec_t), stringResource(R.string.guide_create_rec_b))
    Point(Icons.Rounded.Checklist, stringResource(R.string.guide_create_sub_t), stringResource(R.string.guide_create_sub_b))
    Point(Icons.Rounded.CheckBox, stringResource(R.string.guide_create_note_t), stringResource(R.string.guide_create_note_b))
    Point(Icons.Rounded.AttachFile, stringResource(R.string.guide_create_attach_t), stringResource(R.string.guide_create_attach_b))
    Point(Icons.Rounded.AutoAwesome, stringResource(R.string.guide_create_ai_t), stringResource(R.string.guide_create_ai_b))
}

@Composable
private fun ColumnScope.DailyDetail() {
    Point(Icons.Rounded.ViewAgenda, stringResource(R.string.guide_daily_views_t), stringResource(R.string.guide_daily_views_b))
    Point(Icons.Rounded.Search, stringResource(R.string.guide_daily_search_t), stringResource(R.string.guide_daily_search_b))
    Point(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.guide_daily_sort_t), stringResource(R.string.guide_daily_sort_b))
    Point(Icons.Rounded.LibraryAddCheck, stringResource(R.string.guide_daily_select_t), stringResource(R.string.guide_daily_select_b))
    Point(Icons.Rounded.PushPin, stringResource(R.string.guide_daily_labels_t), stringResource(R.string.guide_daily_labels_b))
    Point(Icons.Rounded.DragIndicator, stringResource(R.string.guide_daily_move_t), stringResource(R.string.guide_daily_move_b))
    Point(Icons.Rounded.Swipe, stringResource(R.string.guide_daily_swipe_t), stringResource(R.string.guide_daily_swipe_b))
    Point(Icons.Rounded.Notifications, stringResource(R.string.guide_daily_remind_t), stringResource(R.string.guide_daily_remind_b))
    Point(Icons.Rounded.Widgets, stringResource(R.string.guide_daily_widget_t), stringResource(R.string.guide_daily_widget_b))
    Point(Icons.Rounded.Inventory2, stringResource(R.string.guide_daily_archive_t), stringResource(R.string.guide_daily_archive_b))
}

@Composable
private fun ColumnScope.FocusDetail() {
    Point(Icons.Rounded.CenterFocusStrong, stringResource(R.string.guide_focus_focus_t), stringResource(R.string.guide_focus_focus_b))
    Point(Icons.Rounded.Timer, stringResource(R.string.guide_focus_pomo_t), stringResource(R.string.guide_focus_pomo_b))
}

@Composable
private fun ColumnScope.PlanningDetail() {
    Para(stringResource(R.string.guide_planning_p1))
    Point(Icons.Rounded.DragIndicator, stringResource(R.string.guide_planning_drag_t), stringResource(R.string.guide_planning_drag_b))
    Point(Icons.Rounded.Schedule, stringResource(R.string.guide_planning_block_t), stringResource(R.string.guide_planning_block_b))
    Point(Icons.Rounded.CalendarMonth, stringResource(R.string.guide_planning_cal_t), stringResource(R.string.guide_planning_cal_b))
    Tip(stringResource(R.string.guide_planning_tip))
}

@Composable
private fun ColumnScope.WearDetail() {
    Para(stringResource(R.string.guide_wear_p1))
    Point(Icons.Rounded.CheckCircle, stringResource(R.string.guide_wear_focus_t), stringResource(R.string.guide_wear_focus_b))
    Point(Icons.Rounded.Timer, stringResource(R.string.guide_wear_pomo_t), stringResource(R.string.guide_wear_pomo_b))
    Point(Icons.Rounded.GridView, stringResource(R.string.guide_wear_today_t), stringResource(R.string.guide_wear_today_b))
    Point(Icons.Rounded.Widgets, stringResource(R.string.guide_wear_tiles_t), stringResource(R.string.guide_wear_tiles_b))
}

@Composable
private fun ColumnScope.AiDetail() {
    Para(stringResource(R.string.guide_ai_p1))
    Point(Icons.Rounded.AutoAwesome, stringResource(R.string.guide_ai_reformulate_t), stringResource(R.string.guide_ai_reformulate_b))
    Point(Icons.Rounded.Category, stringResource(R.string.guide_ai_classify_t), stringResource(R.string.guide_ai_classify_b))
    Point(Icons.Rounded.Checklist, stringResource(R.string.guide_ai_decompose_t), stringResource(R.string.guide_ai_decompose_b))
    Point(Icons.Rounded.Key, stringResource(R.string.guide_ai_keys_t), stringResource(R.string.guide_ai_keys_b))
    Point(Icons.Rounded.Lock, stringResource(R.string.guide_ai_privacy_t), stringResource(R.string.guide_ai_privacy_b))
}

@Composable
private fun ColumnScope.SettingsDetail() {
    Point(Icons.Rounded.DarkMode, stringResource(R.string.guide_set_theme_t), stringResource(R.string.guide_set_theme_b))
    Point(Icons.Rounded.Palette, stringResource(R.string.guide_set_palette_t), stringResource(R.string.guide_set_palette_b))
    Point(Icons.Rounded.Backup, stringResource(R.string.guide_set_backup_t), stringResource(R.string.guide_set_backup_b))
    Point(Icons.Rounded.Bolt, stringResource(R.string.guide_set_guide_t), stringResource(R.string.guide_set_guide_b))
}
