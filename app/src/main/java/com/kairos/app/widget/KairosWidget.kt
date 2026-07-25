package com.kairos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.local.KairosDatabase
import com.kairos.app.data.model.Quadrant

/** Icône d'un quadrant + sa couleur + le nombre de tâches actives (données du widget). */
private data class WidgetQuadrant(val iconRes: Int, val count: Int, val color: Long)

/**
 * Widget écran d'accueil : la matrice 2×2 avec le nombre de tâches actives par quadrant. Un appui
 * ouvre l'app. Les données sont relues à chaque rafraîchissement (déclenché par l'app quand les
 * tâches changent, cf. `KairosApp`). Couleurs = palette « Douce » par défaut (un widget ne lit pas
 * la palette personnalisée choisie dans l'app).
 */
class KairosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quadrants = loadQuadrants(context)
        provideContent { WidgetContent(quadrants) }
    }

    private suspend fun loadQuadrants(context: Context): List<WidgetQuadrant> {
        val active = KairosDatabase.getInstance(context).taskDao().getAll().filter { it.isActive }
        fun count(q: Quadrant) = active.count { it.quadrant == q }
        // Ordre = grille de l'app : haut (Faire, Déléguer) puis bas (Planifier, Supprimer). Icônes
        // plutôt que libellés (gain de place + indépendant de la langue).
        return listOf(
            WidgetQuadrant(R.drawable.widget_do, count(Quadrant.DO), 0xFFE5645A),
            WidgetQuadrant(R.drawable.widget_delegate, count(Quadrant.DELEGATE), 0xFFE0A24E),
            WidgetQuadrant(R.drawable.widget_schedule, count(Quadrant.SCHEDULE), 0xFF4FA5A0),
            WidgetQuadrant(R.drawable.widget_eliminate, count(Quadrant.ELIMINATE), 0xFF9C9AA6),
        )
    }
}

@Composable
private fun WidgetContent(quadrants: List<WidgetQuadrant>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFBEFE9)))
            .cornerRadius(20.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Tile(quadrants[0], GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(6.dp))
            Tile(quadrants[1], GlanceModifier.defaultWeight().fillMaxHeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Tile(quadrants[2], GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(6.dp))
            Tile(quadrants[3], GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun Tile(quadrant: WidgetQuadrant, modifier: GlanceModifier) {
    val accent = Color(quadrant.color)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF1D1D1B) else Color.White
    // Icône (à gauche) + compteur (à droite) : compact, sans libellé texte → gain de place et
    // rendu identique quelle que soit la langue.
    Row(
        modifier = modifier
            .background(ColorProvider(accent))
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(quadrant.iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(onAccent)),
            modifier = GlanceModifier.size(22.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            // Plafonne l'affichage à « 99+ » pour ne pas déborder de la tuile sur un grand nombre.
            text = if (quadrant.count > 99) "99+" else quadrant.count.toString(),
            maxLines = 1,
            style = TextStyle(color = ColorProvider(onAccent), fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
    }
}
