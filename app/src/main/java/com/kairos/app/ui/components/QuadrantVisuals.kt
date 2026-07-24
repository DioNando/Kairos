package com.kairos.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.theme.LocalAccentPalette

/** Attributs visuels d'un quadrant : verbe, icône, couleur d'accent. */
data class QuadrantVisual(
    val verb: String,
    val subtitle: String,
    val icon: ImageVector,
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
    icon = when (quadrant) {
        Quadrant.DO -> Icons.Rounded.Bolt
        Quadrant.SCHEDULE -> Icons.Rounded.EventAvailable
        Quadrant.DELEGATE -> Icons.Rounded.Groups
        Quadrant.ELIMINATE -> Icons.Rounded.DeleteOutline
    },
    accent = quadrantAccent(quadrant),
)

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

/** Couleur de premier plan contrastée sur un accent (noir sur clair, blanc sur sombre). */
fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.5f) Color(0xFF1D1D1B) else Color.White
