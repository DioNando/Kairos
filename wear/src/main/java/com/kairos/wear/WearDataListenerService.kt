package com.kairos.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Reçoit l'état poussé par le téléphone (compteurs, file de focus, palette, quadrants, planning,
 * Pomodoro) et le persiste via [WearPayloads]. Les tuiles concernées sont rafraîchies : la matrice
 * (compteurs, palette) et « Prochain créneau » (planning, palette).
 */
class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var matrix = false
        var planning = false
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val changes = WearPayloads.apply(
                this,
                event.dataItem.uri.path,
                DataMapItem.fromDataItem(event.dataItem).dataMap,
            )
            matrix = matrix || changes.matrixTile
            planning = planning || changes.planningTile
        }
        if (matrix) TileService.getUpdater(this).requestUpdate(KairosTileService::class.java)
        if (planning) TileService.getUpdater(this).requestUpdate(NextBlockTileService::class.java)
    }
}
