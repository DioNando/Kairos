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
 * et on le persiste via [WearPayloads] (compteurs, focus, palette, quadrants, planning, Pomodoro).
 * Bloquant (`Tasks.await`) → appeler **hors du thread principal**.
 */
object CountsSync {

    /** Va chercher le dernier état et le persiste. true si les compteurs ont été lus. */
    fun pull(context: Context): Boolean = runCatching {
        val buffer = Tasks.await(Wearable.getDataClient(context).dataItems, 5, TimeUnit.SECONDS)
        try {
            var foundCounts = false
            for (item in buffer) {
                val path = item.uri.path
                WearPayloads.apply(context, path, DataMapItem.fromDataItem(item).dataMap)
                if (path == WearPayloads.COUNTS_PATH) foundCounts = true
            }
            foundCounts
        } finally {
            buffer.release()
        }
    }.getOrDefault(false)
}
