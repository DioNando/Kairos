package com.kairos.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kairos.app.data.model.AppSettings
import com.kairos.app.ui.components.toColor

/**
 * Couleurs des 4 quadrants de la matrice d'Eisenhower :
 * - [doColor]       : Faire (urgent + important)
 * - [scheduleColor] : Planifier (important, pas urgent)
 * - [delegateColor] : Déléguer (urgent, pas important)
 * - [eliminateColor]: Supprimer (ni urgent ni important)
 *
 * Toutes les visualisations (cellules, chips, badges) s'y réfèrent.
 */
data class AccentPalette(
    val name: String,
    val doColor: Color,
    val scheduleColor: Color,
    val delegateColor: Color,
    val eliminateColor: Color,
)

/** Presets fixes sélectionnables dans les réglages. */
val AppPalettes: List<AccentPalette> = listOf(
    AccentPalette(
        name = "Douce",             // palette de marque (par défaut) — tons désaturés
        doColor = Color(0xFFE5645A),       // corail doux
        scheduleColor = Color(0xFF4FA5A0), // sarcelle apaisée
        delegateColor = Color(0xFFE0A24E), // ambre chaud
        eliminateColor = Color(0xFF9C9AA6), // gris-lavande
    ),
    AccentPalette(
        name = "Vive",              // plus saturée, contrastée
        doColor = Color(0xFFEC2F4B),       // rouge vif
        scheduleColor = Color(0xFF009FFF), // bleu vif
        delegateColor = Color(0xFFF2A65A), // orange
        eliminateColor = Color(0xFF9AA0A6), // gris neutre
    ),
    AccentPalette(
        name = "Nuit",              // adaptée au thème sombre
        doColor = Color(0xFFFF5470),       // rose-rouge
        scheduleColor = Color(0xFF4C8DFF), // bleu ciel
        delegateColor = Color(0xFFF6C453), // jaune doux
        eliminateColor = Color(0xFF8E8AA0), // gris-violet
    ),
    AccentPalette(
        name = "Jardin",
        doColor = Color(0xFFE07A5F),       // terre cuite
        scheduleColor = Color(0xFF81B29A), // sauge
        delegateColor = Color(0xFFF2CC8F), // sable
        eliminateColor = Color(0xFFA5A58D), // olive grisé
    ),
)

/** Palette d'accent courante, fournie au niveau racine selon les réglages. */
val LocalAccentPalette = compositionLocalOf { AppPalettes[0] }

/**
 * Palette d'accent effective pour ces [AppSettings] : un preset de [AppPalettes] si `paletteId` est dans
 * la plage, sinon la palette **personnalisée** construite depuis les 4 couleurs hex. Source unique de
 * vérité partagée par l'app ([com.kairos.app.MainActivity]) ET les widgets ([com.kairos.app.widget]).
 */
fun AppSettings.toAccentPalette(): AccentPalette =
    if (paletteId in AppPalettes.indices) {
        AppPalettes[paletteId]
    } else {
        AccentPalette(
            name = "Personnalisée",
            doColor = customDoColor.toColor(),
            scheduleColor = customScheduleColor.toColor(),
            delegateColor = customDelegateColor.toColor(),
            eliminateColor = customEliminateColor.toColor(),
        )
    }
