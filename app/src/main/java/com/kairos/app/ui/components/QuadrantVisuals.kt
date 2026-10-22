package com.kairos.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.theme.LocalAccentPalette

/** Attributs visuels d'un quadrant : verbe, icône, couleur d'accent. */
data class QuadrantVisual(
    val verb: String,
    val subtitle: String,
    val icon: Painter,
    val accent: Color,
)

/** Couleur d'accent du quadrant, issue de la palette courante. */
@Composable
fun quadrantAccent(quadrant: Quadrant): Color {
    val palette = LocalAccentPalette.current
    return when (quadrant) {
        Quadrant.DO -> palette.doColor
        Quadrant.SCHEDULE -> palette.scheduleColor
        Quadrant.DELEGATE -> palette.delegateColor
        Quadrant.ELIMINATE -> palette.eliminateColor
    }
}

@Composable
fun quadrantVisual(quadrant: Quadrant): QuadrantVisual = QuadrantVisual(
    verb = stringResource(quadrantVerbRes(quadrant)),
    subtitle = stringResource(quadrantSubtitleRes(quadrant)),
    icon = painterResource(quadrantIconRes(quadrant)),
    accent = quadrantAccent(quadrant),
)

/**
 * Icône du quadrant — **exactement la même ressource que le widget et la tuile Wear** (éclair /
 * calendrier / personne / corbeille). Source unique `drawable/widget_*` : les 3 surfaces (app,
 * widget, montre) restent harmonisées et ne peuvent plus diverger.
 */
@DrawableRes
fun quadrantIconRes(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.drawable.widget_do
    Quadrant.SCHEDULE -> R.drawable.widget_schedule
    Quadrant.DELEGATE -> R.drawable.widget_delegate
    Quadrant.ELIMINATE -> R.drawable.widget_eliminate
}

fun quadrantVerbRes(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.string.quadrant_do
    Quadrant.SCHEDULE -> R.string.quadrant_schedule
    Quadrant.DELEGATE -> R.string.quadrant_delegate
    Quadrant.ELIMINATE -> R.string.quadrant_eliminate
}

fun quadrantSubtitleRes(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.string.quadrant_do_sub
    Quadrant.SCHEDULE -> R.string.quadrant_schedule_sub
    Quadrant.DELEGATE -> R.string.quadrant_delegate_sub
    Quadrant.ELIMINATE -> R.string.quadrant_eliminate_sub
}

/** Message d'encouragement affiché quand un quadrant est vide (par quadrant). */
fun quadrantEmptyRes(quadrant: Quadrant): Int = when (quadrant) {
    Quadrant.DO -> R.string.quadrant_empty_do
    Quadrant.SCHEDULE -> R.string.quadrant_empty_schedule
    Quadrant.DELEGATE -> R.string.quadrant_empty_delegate
    Quadrant.ELIMINATE -> R.string.quadrant_empty_eliminate
}

/** Couleur de premier plan contrastée sur un accent (noir sur clair, blanc sur sombre). */
fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.5f) Color(0xFF1D1D1B) else Color.White
