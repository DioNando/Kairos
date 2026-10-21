package com.kairos.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Récepteur d'App Widget de la variante fine 2×1 : relie le système à [KairosRowWidget]. */
class KairosRowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairosRowWidget()
}
