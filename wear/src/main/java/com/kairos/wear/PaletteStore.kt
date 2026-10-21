package com.kairos.wear

import android.content.Context

/**
 * Dernière palette de quadrants connue (4 couleurs ARGB), persistée sur la montre. Poussée par le
 * téléphone (`/kairos/palette`) à chaque changement de palette dans les réglages, lue par
 * [KairosLauncherActivity] (cartes de l'app) et [KairosTileService] (tuile) → montre et téléphone
 * restent accordés.
 *
 * Repli = palette « Douce » (défaut de l'app) tant que rien n'a été poussé (1re install, téléphone
 * absent) → jamais de carte/tuile sans couleur.
 */
object PaletteStore {
    private const val PREFS = "kairos_wear_palette"
    private const val KEY_DO = "do"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_DELEGATE = "delegate"
    private const val KEY_ELIMINATE = "eliminate"

    // Défauts « Douce » (miroir de AppPalettes[0] côté app / @color/quadrant_* côté montre).
    private const val DEF_DO = 0xFFE5645A.toInt()
    private const val DEF_SCHEDULE = 0xFF4FA5A0.toInt()
    private const val DEF_DELEGATE = 0xFFE0A24E.toInt()
    private const val DEF_ELIMINATE = 0xFF9C9AA6.toInt()

    /** [do, schedule, delegate, eliminate] — ordre de Quadrant (ordinal 0..3). */
    fun load(context: Context): IntArray {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return intArrayOf(
            p.getInt(KEY_DO, DEF_DO),
            p.getInt(KEY_SCHEDULE, DEF_SCHEDULE),
            p.getInt(KEY_DELEGATE, DEF_DELEGATE),
            p.getInt(KEY_ELIMINATE, DEF_ELIMINATE),
        )
    }

    fun save(context: Context, doColor: Int, schedule: Int, delegate: Int, eliminate: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DO, doColor)
            .putInt(KEY_SCHEDULE, schedule)
            .putInt(KEY_DELEGATE, delegate)
            .putInt(KEY_ELIMINATE, eliminate)
            .apply()
    }
}
