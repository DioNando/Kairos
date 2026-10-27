package com.kairos.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stockages locaux des données poussées par le téléphone pour les nouveaux écrans de la montre
 * (quadrants, planning du jour, Pomodoro). JSON dans des SharedPreferences : simple, et l'ordre des
 * listes est préservé.
 */

/** Tâche d'un quadrant : id (pour « Terminer »), titre, quadrant (0..3), couleur d'étiquette hex. */
data class QuadrantTask(val id: Long, val title: String, val quadrant: Int, val labelColor: String)

object QuadrantsStore {
    private const val PREFS = "kairos_wear_quadrants"
    private const val KEY = "items"

    fun save(context: Context, items: List<QuadrantTask>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.title).put("q", it.quadrant).put("c", it.labelColor))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<QuadrantTask> = parse(context) { o ->
        QuadrantTask(o.getLong("id"), o.getString("t"), o.getInt("q"), o.optString("c"))
    }

    /** Retrait optimiste (tâche terminée depuis la montre, en attendant la confirmation du téléphone). */
    fun remove(context: Context, id: Long) = save(context, load(context).filter { it.id != id })

    private fun <T> parse(context: Context, map: (JSONObject) -> T): List<T> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** Créneau planifié (time-blocking) : tâche [id] de [start] à [end] (epoch millis). */
data class PlannedBlock(val id: Long, val title: String, val quadrant: Int, val start: Long, val end: Long)

object PlanningStore {
    private const val PREFS = "kairos_wear_planning"
    private const val KEY = "blocks"

    fun save(context: Context, blocks: List<PlannedBlock>) {
        val arr = JSONArray()
        blocks.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("t", it.title).put("q", it.quadrant).put("s", it.start).put("e", it.end),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<PlannedBlock> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                PlannedBlock(o.getLong("id"), o.getString("t"), o.getInt("q"), o.getLong("s"), o.getLong("e"))
            }
        }.getOrDefault(emptyList())
    }

    /** Créneaux du jour courant non terminés (fin dans le futur), triés. */
    fun remainingToday(context: Context, now: Long = System.currentTimeMillis()): List<PlannedBlock> {
        val zone = java.time.ZoneId.systemDefault()
        val endOfDay = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return load(context).filter { it.end > now && it.start < endOfDay }
    }
}

/**
 * État Pomodoro : en marche, [endAt] donne l'heure de fin (décompte local) ; en pause, [remaining]
 * (secondes) est figé. [phase] : 0 = focus, 1 = pause courte, 2 = pause longue.
 */
data class PomodoroSnapshot(
    val active: Boolean,
    val running: Boolean,
    val phase: Int,
    val endAt: Long,
    val remaining: Int,
    val total: Int,
) {
    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int =
        if (running) ((endAt - now) / 1000L).toInt().coerceAtLeast(0) else remaining
}

object PomodoroStore {
    private const val PREFS = "kairos_wear_pomodoro"

    fun save(context: Context, s: PomodoroSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("active", s.active)
            .putBoolean("running", s.running)
            .putInt("phase", s.phase)
            .putLong("endAt", s.endAt)
            .putInt("remaining", s.remaining)
            .putInt("total", s.total)
            .apply()
    }

    fun load(context: Context): PomodoroSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PomodoroSnapshot(
            active = p.getBoolean("active", false),
            running = p.getBoolean("running", false),
            phase = p.getInt("phase", 0),
            endAt = p.getLong("endAt", 0L),
            remaining = p.getInt("remaining", 0),
            total = p.getInt("total", 0),
        )
    }
}
