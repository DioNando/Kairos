package com.kairos.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/**
 * Lecture **active** de l'état poussé par le téléphone depuis le Data Layer.
 *
 * [WearDataListenerService] ne reçoit que les **changements** (`onDataChanged`) : à l'installation ou à
 * l'ouverture de l'app/tuile, les `DataItem` **déjà publiés** par le téléphone ne sont jamais livrés
 * (aucun événement de changement). On lit donc explicitement le dernier état présent sur le Data Layer
 * et on le persiste : les compteurs ([CountsStore], pour la tuile) **et** la file de focus
 * ([FocusStore], pour l'app montre). Bloquant (`Tasks.await`) → appeler **hors du thread principal**.
 */
object CountsSync {
    private const val COUNTS_PATH = "/kairos/counts"
    private const val KEY_DO = "do"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_DELEGATE = "delegate"
    private const val KEY_ELIMINATE = "eliminate"

    private const val FOCUS_PATH = "/kairos/focus"
    private const val KEY_TITLES = "titles"
    private const val KEY_QUADRANTS = "quadrants"

    private const val PALETTE_PATH = "/kairos/palette"

    /** Va chercher le dernier état (compteurs + focus + palette) et le persiste. true si les compteurs ont été lus. */
    fun pull(context: Context): Boolean = runCatching {
        val buffer = Tasks.await(Wearable.getDataClient(context).dataItems, 5, TimeUnit.SECONDS)
        try {
            var foundCounts = false
            for (item in buffer) {
                when (item.uri.path) {
                    COUNTS_PATH -> {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        CountsStore.save(
                            context = context,
                            doCount = map.getInt(KEY_DO),
                            schedule = map.getInt(KEY_SCHEDULE),
                            delegate = map.getInt(KEY_DELEGATE),
                            eliminate = map.getInt(KEY_ELIMINATE),
                        )
                        foundCounts = true
                    }
                    FOCUS_PATH -> {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        FocusStore.save(
                            context = context,
                            titles = map.getStringArray(KEY_TITLES),
                            quadrants = map.getIntegerArrayList(KEY_QUADRANTS),
                        )
                    }
                    PALETTE_PATH -> {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        PaletteStore.save(
                            context = context,
                            doColor = map.getInt(KEY_DO),
                            schedule = map.getInt(KEY_SCHEDULE),
                            delegate = map.getInt(KEY_DELEGATE),
                            eliminate = map.getInt(KEY_ELIMINATE),
                        )
                    }
                }
            }
            foundCounts
        } finally {
            buffer.release()
        }
    }.getOrDefault(false)
}
