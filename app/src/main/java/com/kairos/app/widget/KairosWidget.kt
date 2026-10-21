package com.kairos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import com.kairos.app.KairosApp
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.theme.AccentPalette
import com.kairos.app.ui.theme.AppPalettes
import com.kairos.app.ui.theme.toAccentPalette
import kotlinx.coroutines.flow.first

/** Icône d'un quadrant + sa couleur + le nombre de tâches actives (données du widget). */
private data class WidgetQuadrant(val iconRes: Int, val count: Int, val color: Color)

/** Couleur d'un quadrant selon la palette **choisie dans les réglages** (accord avec l'app). */
private fun quadrantColor(palette: AccentPalette, quadrant: Quadrant): Color = when (quadrant) {
    Quadrant.DO -> palette.doColor
    Quadrant.SCHEDULE -> palette.scheduleColor
    Quadrant.DELEGATE -> palette.delegateColor
    Quadrant.ELIMINATE -> palette.eliminateColor
}

private fun quadrantIcon(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.drawable.widget_do
    Quadrant.SCHEDULE -> R.drawable.widget_schedule
    Quadrant.DELEGATE -> R.drawable.widget_delegate
    Quadrant.ELIMINATE -> R.drawable.widget_eliminate
}

/**
 * Compte les tâches actives par quadrant. Icônes plutôt que libellés (gain de place + indépendant
 * de la langue). Relu à chaque rafraîchissement (déclenché par l'app quand les tâches changent).
 */
private suspend fun loadQuadrants(context: Context): Map<Quadrant, WidgetQuadrant> {
    val active = KairosDatabase.getInstance(context).taskDao().getAll().filter { it.isActive }
    val palette = resolvePalette(context)
    return Quadrant.entries.associateWith { q ->
        WidgetQuadrant(quadrantIcon(q), active.count { it.quadrant == q }, quadrantColor(palette, q))
    }
}

/** Palette d'accent effective (réglages de l'app) — repli « Douce » si l'app/les réglages sont indispos. */
private suspend fun resolvePalette(context: Context): AccentPalette {
    val app = context.applicationContext as? KairosApp ?: return AppPalettes[0]
    val settings = runCatching { app.settingsRepository.settings.first() }.getOrNull()
    return settings?.toAccentPalette() ?: AppPalettes[0]
}

/**
 * Widget écran d'accueil **2×2** : la matrice avec le nombre de tâches actives par quadrant. Un appui
 * ouvre l'app.
 */
class KairosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quadrants = loadQuadrants(context)
        provideContent { GlanceTheme { GridContent(quadrants) } }
    }
}

/**
 * Widget écran d'accueil **2×1** : les 4 compteurs de quadrant sur une seule rangée compacte, pour
 * ceux qui veulent un widget fin. Mêmes données et même appui que la version 2×2.
 */
class KairosRowWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quadrants = loadQuadrants(context)
        provideContent { GlanceTheme { RowContent(quadrants) } }
    }
}

@Composable
private fun GridContent(quadrants: Map<Quadrant, WidgetQuadrant>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        // Disposition de la grille de l'app : haut (Faire, Déléguer) puis bas (Planifier, Supprimer).
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Tile(quadrants.getValue(Quadrant.DO), GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(6.dp))
            Tile(quadrants.getValue(Quadrant.DELEGATE), GlanceModifier.defaultWeight().fillMaxHeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Tile(quadrants.getValue(Quadrant.SCHEDULE), GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(6.dp))
            Tile(quadrants.getValue(Quadrant.ELIMINATE), GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun RowContent(quadrants: Map<Quadrant, WidgetQuadrant>) {
    val context = LocalContext.current
    // Ordre des 4 verbes (Faire, Planifier, Déléguer, Supprimer) = Quadrant.entries.
    val order = Quadrant.entries
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(6.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        order.forEachIndexed { index, q ->
            // Version compacte : icône + compteur plus petits, tuiles fines côte à côte.
            Tile(
                quadrant = quadrants.getValue(q),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                iconSize = 16.dp,
                fontSize = 16.sp,
                horizontalPadding = 6.dp,
                gap = 4.dp,
            )
            if (index < order.lastIndex) Spacer(GlanceModifier.width(6.dp))
        }
    }
}

@Composable
private fun Tile(
    quadrant: WidgetQuadrant,
    modifier: GlanceModifier,
    iconSize: Dp = 28.dp,
    fontSize: TextUnit = 26.sp,
    horizontalPadding: Dp = 12.dp,
    gap: Dp = 6.dp,
) {
    val accent = quadrant.color
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF1D1D1B) else Color.White
    // Icône (au-dessus) + compteur (en dessous), empilés : centré, sans libellé texte → tient dans
    // une tuile étroite et reste identique quelle que soit la langue.
    Column(
        modifier = modifier
            .background(ColorProvider(accent))
            .cornerRadius(19.dp)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(quadrant.iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(onAccent)),
            modifier = GlanceModifier.size(iconSize),
        )
        Spacer(GlanceModifier.height(gap))
        Text(
            // Plafonne l'affichage à « 99+ » pour ne pas déborder de la tuile sur un grand nombre.
            text = if (quadrant.count > 99) "99+" else quadrant.count.toString(),
            maxLines = 1,
            style = TextStyle(color = ColorProvider(onAccent), fontSize = fontSize, fontWeight = FontWeight.Bold),
        )
    }
}
