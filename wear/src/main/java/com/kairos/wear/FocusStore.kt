package com.kairos.wear

import android.content.Context

/**
 * Dernière file « À faire en priorité » connue (tâche prioritaire + « à suivre »), persistée sur la
 * montre. Alimentée par [WearDataListenerService] / [CountsSync] à réception du `DataItem`
 * `/kairos/focus` poussé par le téléphone, relue par [KairosLauncherActivity] à chaque affichage.
 *
 * Stockage indexé (`title_i` / `quadrant_i`) plutôt qu'un `StringSet` : l'ordre du classement doit
 * être préservé (le premier est la tâche prioritaire).
 */
object FocusStore {
    private const val PREFS = "kairos_wear_focus"
    private const val KEY_COUNT = "count"
    private const val KEY_TITLE = "title_"
    private const val KEY_QUADRANT = "quadrant_"

    /** Une tâche de la file : son titre et son quadrant (ordinal 0..3 = DO/SCHEDULE/DELEGATE/ELIMINATE). */
    data class Item(val title: String, val quadrant: Int)

    fun load(context: Context): List<Item> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = p.getInt(KEY_COUNT, 0)
        return (0 until n).mapNotNull { i ->
            val title = p.getString(KEY_TITLE + i, null) ?: return@mapNotNull null
            Item(title, p.getInt(KEY_QUADRANT + i, 0))
        }
    }

    fun save(context: Context, titles: Array<String>?, quadrants: List<Int>?) {
        val safeTitles = titles ?: emptyArray()
        val safeQuadrants = quadrants ?: emptyList()
        val n = minOf(safeTitles.size, safeQuadrants.size)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        editor.putInt(KEY_COUNT, n)
        for (i in 0 until n) {
            editor.putString(KEY_TITLE + i, safeTitles[i])
            editor.putInt(KEY_QUADRANT + i, safeQuadrants[i])
        }
        editor.apply()
    }
}
