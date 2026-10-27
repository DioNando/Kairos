package com.kairos.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.protolayout.TimelineBuilders.TimeInterval
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Tuile « Prochain créneau » : le créneau planifié en cours ou à venir aujourd'hui (horaire + titre,
 * teinté par la couleur du quadrant), ou « Rien de planifié aujourd'hui ». Un appui ouvre l'app
 * montre.
 *
 * La tuile publie une **timeline** : une entrée par créneau restant, chacune valable jusqu'à la fin
 * du créneau précédent → l'affichage passe au créneau suivant à l'heure dite, sans réveil réseau.
 */
class NextBlockTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        val dev = requestParams.deviceConfiguration
        val side = minOf(dev.screenWidthDp, dev.screenHeightDp).toFloat()
        val pad = if (dev.screenShape == DeviceParametersBuilders.SCREEN_SHAPE_ROUND) side * 0.12f else 8f
        return Futures.submit(
            Callable {
                CountsSync.pull(this)
                val palette = PaletteStore.load(this)
                val blocks = PlanningStore.remainingToday(this)
                val zone = ZoneId.systemDefault()
                val endOfDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                val timeline = Timeline.Builder()
                var validFrom = 0L
                blocks.forEach { block ->
                    timeline.addTimelineEntry(
                        TimelineEntry.Builder()
                            .setValidity(TimeInterval.Builder().setStartMillis(validFrom).setEndMillis(block.end).build())
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(blockLayout(block, palette, pad)).build())
                            .build(),
                    )
                    validFrom = block.end
                }
                // Après le dernier créneau (ou s'il n'y en a aucun) : « Rien de planifié ».
                timeline.addTimelineEntry(
                    TimelineEntry.Builder()
                        .setValidity(TimeInterval.Builder().setStartMillis(validFrom).setEndMillis(endOfDay).build())
                        .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(emptyLayout(pad)).build())
                        .build(),
                )
                Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    // Relecture à minuit passé (nouveau jour) ; les changements arrivent par push.
                    .setFreshnessIntervalMillis((endOfDay - System.currentTimeMillis()).coerceAtLeast(60_000L))
                    .setTileTimeline(timeline.build())
                    .build()
            },
            executor,
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun root(pad: Float, content: LayoutElement): LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(BG)).build())
                    .setPadding(Padding.Builder().setAll(dp(pad)).build())
                    .setClickable(
                        Clickable.Builder()
                            .setId("open_watch_app")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName("com.kairos.app")
                                            .setClassName("com.kairos.wear.KairosLauncherActivity")
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(content)
            .build()

    private fun blockLayout(block: PlannedBlock, palette: IntArray, pad: Float): LayoutElement {
        val accent = palette[block.quadrant.coerceIn(0, 3)]
        val ink = WearUi.inkFor(accent)
        val zone = ZoneId.systemDefault()
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val range = time.format(Instant.ofEpochMilli(block.start).atZone(zone)) + "–" +
            time.format(Instant.ofEpochMilli(block.end).atZone(zone))
        val card = Box.Builder()
            .setWidth(expand())
            .setHeight(wrap())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(accent))
                            .setCorner(Corner.Builder().setRadius(dp(22f)).build())
                            .build(),
                    )
                    .setPadding(Padding.Builder().setAll(dp(12f)).build())
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(range, 13f, ink, bold = true))
                    .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(text(block.title, 15f, ink, bold = true, maxLines = 2))
                    .build(),
            )
            .build()
        return root(
            pad,
            Column.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(text(getString(R.string.next_block_title), 12f, MUTED))
                .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                .addContent(card)
                .build(),
        )
    }

    private fun emptyLayout(pad: Float): LayoutElement =
        root(
            pad,
            Column.Builder()
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(text(getString(R.string.next_block_title), 12f, MUTED))
                .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                .addContent(text(getString(R.string.next_block_none), 14f, WHITE, bold = true, maxLines = 2))
                .build(),
        )

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, maxLines: Int = 1): LayoutElement =
        Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private const val RESOURCES_VERSION = "1"
        private const val BG = 0xFF201A18.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFB0AFB6.toInt()
    }
}
