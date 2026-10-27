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
    private const val KEY_ID = "id_"
    private const val KEY_TITLE = "title_"
    private const val KEY_QUADRANT = "quadrant_"
    private const val KEY_LABEL_NAME = "label_name_"
    private const val KEY_LABEL_COLOR = "label_color_"

    /**
     * Une tâche de la file : son titre, son quadrant (ordinal 0..3 = DO/SCHEDULE/DELEGATE/ELIMINATE)
     * et son étiquette éventuelle ([labelName] + [labelColor] hex `#RRGGBB`, vides si aucune).
     */
    data class Item(
        val id: Long,
        val title: String,
        val quadrant: Int,
        val labelName: String,
        val labelColor: String,
    )

    fun load(context: Context): List<Item> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = p.getInt(KEY_COUNT, 0)
        return (0 until n).mapNotNull { i ->
            val title = p.getString(KEY_TITLE + i, null) ?: return@mapNotNull null
            Item(
                id = p.getLong(KEY_ID + i, 0L),
                title = title,
                quadrant = p.getInt(KEY_QUADRANT + i, 0),
                labelName = p.getString(KEY_LABEL_NAME + i, "").orEmpty(),
                labelColor = p.getString(KEY_LABEL_COLOR + i, "").orEmpty(),
            )
        }
    }

    fun save(
        context: Context,
        ids: LongArray?,
        titles: Array<String>?,
        quadrants: List<Int>?,
        labelNames: Array<String>?,
        labelColors: Array<String>?,
    ) {
        val safeTitles = titles ?: emptyArray()
        val safeQuadrants = quadrants ?: emptyList()
        val n = minOf(safeTitles.size, safeQuadrants.size)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        editor.putInt(KEY_COUNT, n)
        for (i in 0 until n) {
            editor.putLong(KEY_ID + i, ids?.getOrNull(i) ?: 0L)
            editor.putString(KEY_TITLE + i, safeTitles[i])
            editor.putInt(KEY_QUADRANT + i, safeQuadrants[i])
            editor.putString(KEY_LABEL_NAME + i, labelNames?.getOrNull(i).orEmpty())
            editor.putString(KEY_LABEL_COLOR + i, labelColors?.getOrNull(i).orEmpty())
        }
        editor.apply()
    }

    /** Retrait optimiste d'une tâche terminée depuis la montre (le téléphone repoussera la file). */
    fun remove(context: Context, id: Long) {
        val items = load(context).filter { it.id != id }
        save(
            context,
            items.map { it.id }.toLongArray(),
            items.map { it.title }.toTypedArray(),
            items.map { it.quadrant },
            items.map { it.labelName }.toTypedArray(),
            items.map { it.labelColor }.toTypedArray(),
        )
    }
}
