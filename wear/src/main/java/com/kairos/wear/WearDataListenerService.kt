package com.kairos.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Reçoit l'état poussé par le téléphone : compteurs (`/kairos/counts` → [CountsStore]), file de focus
 * (`/kairos/focus` → [FocusStore], lue par l'app montre à sa prochaine ouverture) et palette de
 * quadrants (`/kairos/palette` → [PaletteStore]). La tuile est rafraîchie si les compteurs **ou** la
 * palette changent (les deux affectent son rendu).
 */
class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var tileChanged = false
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                COUNTS_PATH -> {
                    CountsStore.save(
                        context = this,
                        doCount = map.getInt(KEY_DO),
                        schedule = map.getInt(KEY_SCHEDULE),
                        delegate = map.getInt(KEY_DELEGATE),
                        eliminate = map.getInt(KEY_ELIMINATE),
                    )
                    tileChanged = true
                }
                FOCUS_PATH -> FocusStore.save(
                    context = this,
                    titles = map.getStringArray(KEY_TITLES),
                    quadrants = map.getIntegerArrayList(KEY_QUADRANTS),
                )
                PALETTE_PATH -> {
                    PaletteStore.save(
                        context = this,
                        doColor = map.getInt(KEY_DO),
                        schedule = map.getInt(KEY_SCHEDULE),
                        delegate = map.getInt(KEY_DELEGATE),
                        eliminate = map.getInt(KEY_ELIMINATE),
                    )
                    tileChanged = true
                }
            }
        }
        if (tileChanged) {
            TileService.getUpdater(this).requestUpdate(KairosTileService::class.java)
        }
    }

    companion object {
        private const val COUNTS_PATH = "/kairos/counts"
        private const val KEY_DO = "do"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_DELEGATE = "delegate"
        private const val KEY_ELIMINATE = "eliminate"

        private const val FOCUS_PATH = "/kairos/focus"
        private const val KEY_TITLES = "titles"
        private const val KEY_QUADRANTS = "quadrants"

        private const val PALETTE_PATH = "/kairos/palette"
    }
}
