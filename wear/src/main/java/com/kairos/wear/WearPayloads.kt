package com.kairos.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap

/**
 * Applique un `DataItem` poussé par le téléphone aux stockages locaux. Point unique partagé par la
 * réception passive ([WearDataListenerService]) et la lecture active ([CountsSync]) : un seul endroit
 * connaît les chemins et les clés (miroir de `WearSync` côté téléphone).
 */
object WearPayloads {

    /** Ce qu'un DataItem a modifié : les tuiles concernées sont à rafraîchir. */
    data class Changes(val matrixTile: Boolean = false, val planningTile: Boolean = false)

    fun apply(context: Context, path: String?, map: DataMap): Changes = when (path) {
        COUNTS_PATH -> {
            CountsStore.save(
                context = context,
                doCount = map.getInt(KEY_DO),
                schedule = map.getInt(KEY_SCHEDULE),
                delegate = map.getInt(KEY_DELEGATE),
                eliminate = map.getInt(KEY_ELIMINATE),
            )
            Changes(matrixTile = true)
        }
        FOCUS_PATH -> {
            FocusStore.save(
                context = context,
                ids = map.getLongArray(KEY_IDS),
                titles = map.getStringArray(KEY_TITLES),
                quadrants = map.getIntegerArrayList(KEY_QUADRANTS),
                labelNames = map.getStringArray(KEY_LABEL_NAMES),
                labelColors = map.getStringArray(KEY_LABEL_COLORS),
            )
            Changes()
        }
        PALETTE_PATH -> {
            PaletteStore.save(
                context = context,
                doColor = map.getInt(KEY_DO),
                schedule = map.getInt(KEY_SCHEDULE),
                delegate = map.getInt(KEY_DELEGATE),
                eliminate = map.getInt(KEY_ELIMINATE),
            )
            Changes(matrixTile = true, planningTile = true)
        }
        QUADRANTS_PATH -> {
            val ids = map.getLongArray(KEY_IDS) ?: LongArray(0)
            val titles = map.getStringArray(KEY_TITLES) ?: emptyArray()
            val quadrants = map.getIntegerArrayList(KEY_QUADRANTS) ?: arrayListOf()
            val colors = map.getStringArray(KEY_LABEL_COLORS)
            val n = minOf(ids.size, titles.size, quadrants.size)
            QuadrantsStore.save(
                context,
                (0 until n).map { QuadrantTask(ids[it], titles[it], quadrants[it], colors?.getOrNull(it).orEmpty()) },
            )
            Changes()
        }
        PLANNING_PATH -> {
            val ids = map.getLongArray(KEY_IDS) ?: LongArray(0)
            val titles = map.getStringArray(KEY_TITLES) ?: emptyArray()
            val quadrants = map.getIntegerArrayList(KEY_QUADRANTS) ?: arrayListOf()
            val starts = map.getLongArray(KEY_STARTS) ?: LongArray(0)
            val ends = map.getLongArray(KEY_ENDS) ?: LongArray(0)
            val n = minOf(ids.size, titles.size, quadrants.size, starts.size, ends.size)
            PlanningStore.save(
                context,
                (0 until n).map { PlannedBlock(ids[it], titles[it], quadrants[it], starts[it], ends[it]) },
            )
            Changes(planningTile = true)
        }
        POMODORO_PATH -> {
            PomodoroStore.save(
                context,
                PomodoroSnapshot(
                    active = map.getBoolean(KEY_ACTIVE),
                    running = map.getBoolean(KEY_RUNNING),
                    phase = map.getInt(KEY_PHASE),
                    endAt = map.getLong(KEY_END_AT),
                    remaining = map.getInt(KEY_REMAINING),
                    total = map.getInt(KEY_TOTAL),
                ),
            )
            Changes()
        }
        else -> Changes()
    }

    const val COUNTS_PATH = "/kairos/counts"
    private const val FOCUS_PATH = "/kairos/focus"
    private const val PALETTE_PATH = "/kairos/palette"
    private const val QUADRANTS_PATH = "/kairos/quadrants"
    private const val PLANNING_PATH = "/kairos/planning"
    private const val POMODORO_PATH = "/kairos/pomodoro"

    private const val KEY_DO = "do"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_DELEGATE = "delegate"
    private const val KEY_ELIMINATE = "eliminate"
    private const val KEY_IDS = "ids"
    private const val KEY_TITLES = "titles"
    private const val KEY_QUADRANTS = "quadrants"
    private const val KEY_LABEL_NAMES = "labelNames"
    private const val KEY_LABEL_COLORS = "labelColors"
    private const val KEY_STARTS = "starts"
    private const val KEY_ENDS = "ends"
    private const val KEY_ACTIVE = "active"
    private const val KEY_RUNNING = "running"
    private const val KEY_PHASE = "phase"
    private const val KEY_END_AT = "endAt"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_TOTAL = "total"
}
