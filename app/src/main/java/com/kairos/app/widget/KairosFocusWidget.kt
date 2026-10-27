package com.kairos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kairos.app.KairosApp
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.reminder.ReminderNotifications
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.ui.components.quadrantVerbRes
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.AppPalettes
import com.kairos.app.ui.theme.toAccentPalette
import kotlinx.coroutines.flow.first

/** Une tâche affichée par le widget : id (✓ / ouverture), titre, quadrant, couleur d'accent, étiquette. */
private data class FocusItem(
    val id: Long,
    val title: String,
    val quadrant: Quadrant,
    val accent: Color,
    val labelName: String?,
    val labelColor: Color?,
)

/** Nombre de tâches affichées (la prioritaire + « À suivre »), au-delà la liste défile. */
private const val FOCUS_SIZE = 6

/**
 * File « À faire en priorité » : même classement que l'écran Focus (score pondéré des réglages),
 * couleurs de la palette choisie dans l'app.
 */
private suspend fun loadFocus(context: Context): List<FocusItem> {
    val db = KairosDatabase.getInstance(context)
    val tasks = db.taskDao().getAll()
    val labels = db.labelDao().getAll().associateBy { it.id }
    val settings = (context.applicationContext as? KairosApp)
        ?.let { runCatching { it.settingsRepository.settings.first() }.getOrNull() }
    val palette = settings?.toAccentPalette() ?: AppPalettes[0]
    val weight = settings?.focusDueWeight ?: FocusSelector.DEFAULT_DUE_WEIGHT
    return FocusSelector.rank(tasks, weight).take(FOCUS_SIZE).map { task ->
        val label = task.labelId?.let { labels[it] }
        FocusItem(
            id = task.id,
            title = task.title,
            quadrant = task.quadrant,
            accent = when (task.quadrant) {
                Quadrant.DO -> palette.doColor
                Quadrant.SCHEDULE -> palette.scheduleColor
                Quadrant.DELEGATE -> palette.delegateColor
                Quadrant.ELIMINATE -> palette.eliminateColor
            },
            labelName = label?.name,
            labelColor = label?.colorHex?.toColor(),
        )
    }
}

/**
 * Widget écran d'accueil **« À faire en priorité »** : la tâche du moment (carte teintée par son
 * quadrant) puis « À suivre », comme l'écran Focus. Chaque tâche a un **✓** qui la termine sans
 * ouvrir l'app ; un appui sur une tâche l'ouvre dans l'éditeur, sur l'en-tête ouvre le mode Focus.
 */
class KairosFocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadFocus(context)
        provideContent { GlanceTheme { FocusContent(items) } }
    }
}

/** Récepteur d'App Widget du widget « À faire en priorité ». */
class KairosFocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairosFocusWidget()
}

/**
 * Widget **2×1 « Tâche prioritaire »** : la seule tâche du moment, sur la couleur de son quadrant,
 * avec son ✓. Même classement et mêmes actions que la version complète.
 */
class KairosFocusCompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val top = loadFocus(context).firstOrNull()
        provideContent { GlanceTheme { CompactContent(top) } }
    }
}

/** Récepteur d'App Widget de la version fine 2×1. */
class KairosFocusCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairosFocusCompactWidget()
}

/** ✓ d'une tâche : la marque terminée (récurrente → avance sur place) puis rafraîchit les widgets. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as? KairosApp ?: return
        app.repository.setCompleted(taskId, System.currentTimeMillis())
        KairosFocusWidget().updateAll(context)
        KairosFocusCompactWidget().updateAll(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
    }
}

@Composable
private fun FocusContent(items: List<FocusItem>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        // En-tête : logo + titre → ouvre le mode Focus.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_FOCUS))),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                context.getString(R.string.widget_focus_title),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    context.getString(R.string.widget_focus_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            return@Column
        }

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(items.take(1), itemId = { it.id }) { item -> PriorityCard(item) }
            if (items.size > 1) {
                item {
                    Text(
                        context.getString(R.string.widget_up_next),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                items(items.drop(1), itemId = { it.id }) { item -> UpNextRow(item) }
            }
        }
    }
}

/** Rayon extérieur du widget 2×1, marge intérieure, et rayon de la carte (concentrique : 26 − 8). */
private val CompactOuterRadius = 26.dp
private val CompactInset = 8.dp
private val CompactCardRadius = 18.dp

/**
 * Contenu 2×1 : carte pleine couleur du quadrant — icône + verbe sur une ligne, titre dessous sur
 * toute la largeur (2 lignes) — et ✓ translucide ; un appui ouvre la tâche. Sans tâche : message
 * vide, un appui ouvre l'app.
 */
@Composable
private fun CompactContent(item: FocusItem?) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(CompactOuterRadius)
            .padding(CompactInset),
    ) {
        if (item == null) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    context.getString(R.string.widget_focus_compact_empty),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            return@Box
        }
        val ink = inkOn(item.accent)
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(item.accent))
                .cornerRadius(CompactCardRadius)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .clickable(actionStartActivity(openTaskIntent(context, item.id))),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Icône à côté du verbe : le titre récupère toute la largeur de la carte.
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(quadrantIcon(item.quadrant)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(ink.copy(alpha = 0.85f))),
                        modifier = GlanceModifier.size(12.dp),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        context.getString(quadrantVerbRes(item.quadrant)),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(ink.copy(alpha = 0.85f)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    item.title,
                    maxLines = 2,
                    style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .background(ColorProvider(ink.copy(alpha = 0.2f)))
                    .cornerRadius(15.dp)
                    .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to item.id))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_check),
                    contentDescription = context.getString(R.string.cd_mark_done),
                    colorFilter = ColorFilter.tint(ColorProvider(ink)),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}

/** Icône du quadrant (mêmes pictogrammes que les widgets matrice). */
private fun quadrantIcon(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.drawable.widget_do
    Quadrant.SCHEDULE -> R.drawable.widget_schedule
    Quadrant.DELEGATE -> R.drawable.widget_delegate
    Quadrant.ELIMINATE -> R.drawable.widget_eliminate
}

/** Encre lisible posée sur une couleur d'accent. */
private fun inkOn(color: Color): Color = if (color.luminance() > 0.5f) Color(0xFF1D1D1B) else Color.White

private fun openTaskIntent(context: Context, taskId: Long): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ReminderNotifications.ACTION_OPEN_TASK)
        .putExtra(ReminderNotifications.EXTRA_TASK_ID, taskId)

/** Tâche prioritaire : carte teintée (accent ~18 %), verbe du quadrant, titre, étiquette, ✓ plein. */
@Composable
private fun PriorityCard(item: FocusItem) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(item.accent.copy(alpha = 0.18f)))
            .cornerRadius(18.dp)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            .clickable(actionStartActivity(openTaskIntent(context, item.id))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                context.getString(quadrantVerbRes(item.quadrant)),
                maxLines = 1,
                style = TextStyle(color = ColorProvider(item.accent), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                item.title,
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            if (item.labelName != null) {
                Spacer(GlanceModifier.height(4.dp))
                LabelTag(item.labelName, item.labelColor ?: item.accent)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        CheckButton(item, filled = true)
    }
}

/** Ligne « À suivre » : pastille du quadrant, titre, point d'étiquette, ✓ tonal. */
@Composable
private fun UpNextRow(item: FocusItem) {
    val context = LocalContext.current
    Column {
        Spacer(GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(14.dp)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .clickable(actionStartActivity(openTaskIntent(context, item.id))),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Box(GlanceModifier.size(8.dp).background(ColorProvider(item.accent)).cornerRadius(4.dp)) {}
            Spacer(GlanceModifier.width(10.dp))
            Text(
                item.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (item.labelColor != null) {
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.size(8.dp).background(ColorProvider(item.labelColor)).cornerRadius(4.dp)) {}
            }
            Spacer(GlanceModifier.width(6.dp))
            CheckButton(item, filled = false)
        }
    }
}

@Composable
private fun LabelTag(name: String, color: Color) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(GlanceModifier.size(8.dp).background(ColorProvider(color)).cornerRadius(4.dp)) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
    }
}

/** Bouton rond ✓ : plein (accent) pour la prioritaire, tonal pour les suivantes. */
@Composable
private fun CheckButton(item: FocusItem, filled: Boolean) {
    val context = LocalContext.current
    val size = if (filled) 36.dp else 30.dp
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(if (filled) ColorProvider(item.accent) else GlanceTheme.colors.surface)
            .cornerRadius(size / 2)
            .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to item.id))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_check),
            contentDescription = context.getString(R.string.cd_mark_done),
            colorFilter = ColorFilter.tint(if (filled) ColorProvider(inkOn(item.accent)) else ColorProvider(item.accent)),
            modifier = GlanceModifier.size(if (filled) 18.dp else 16.dp),
        )
    }
}
