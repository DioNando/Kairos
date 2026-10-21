package com.kairos.wear

import android.content.Context

/**
 * Dernier état connu des 4 compteurs de quadrants, persisté sur la montre (SharedPreferences).
 * Alimenté par [WearDataListenerService] à réception d'un `DataItem` du téléphone, relu par
 * [KairosTileService] à chaque rendu de la tuile (survit au redémarrage / à l'absence temporaire du
 * téléphone).
 */
object CountsStore {
    private const val PREFS = "kairos_wear_counts"
    private const val KEY_DO = "do"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_DELEGATE = "delegate"
    private const val KEY_ELIMINATE = "eliminate"

    /** [do, schedule, delegate, eliminate] — ordre de [Quadrant]. */
    fun load(context: Context): IntArray {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return intArrayOf(
            p.getInt(KEY_DO, 0),
            p.getInt(KEY_SCHEDULE, 0),
            p.getInt(KEY_DELEGATE, 0),
            p.getInt(KEY_ELIMINATE, 0),
        )
    }

    fun save(context: Context, doCount: Int, schedule: Int, delegate: Int, eliminate: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DO, doCount)
            .putInt(KEY_SCHEDULE, schedule)
            .putInt(KEY_DELEGATE, delegate)
            .putInt(KEY_ELIMINATE, eliminate)
            .apply()
    }
}
