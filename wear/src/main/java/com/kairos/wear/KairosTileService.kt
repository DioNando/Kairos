package com.kairos.wear

import android.graphics.Color as AndroidColor
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
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Tuile Wear OS : la matrice d'Eisenhower en 2×2, chaque quadrant coloré affichant son nombre de
 * tâches actives (icône + compteur). Les données proviennent de [CountsStore] (poussées par le
 * téléphone via le Data Layer). Un appui ouvre Kairós sur le téléphone via [OpenOnPhoneActivity].
 *
 * Rendu en ProtoLayout (déclaratif, pas de Compose). Couleurs = palette « Douce » codée en dur, comme
 * les widgets de l'app (une tuile ne lit pas la palette personnalisée).
 */
class KairosTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<Tile> {
        // Marge d'inset : sur écran ROND, le carré inscrit ne fait que ~0,707×diamètre → sans marge, les
        // coins des 4 cartes sont rognés par l'arrondi. On inset ~15 % de chaque côté (carré ≈ 0,70×côté).
        val dev = requestParams.deviceConfiguration
        val side = minOf(dev.screenWidthDp, dev.screenHeightDp).toFloat()
        val pad = if (dev.screenShape == DeviceParametersBuilders.SCREEN_SHAPE_ROUND) side * 0.15f else 8f

        // Lecture ACTIVE des compteurs (hors thread principal) avant de construire la tuile : onDataChanged
        // ne livre que les changements, pas l'état déjà publié par le téléphone (cf. CountsSync).
        return Futures.submit(
            Callable {
                CountsSync.pull(this)
                val counts = CountsStore.load(this)
                val palette = PaletteStore.load(this) // Suit la palette de l'app (repli « Douce »).
                Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(0) // Rafraîchie sur demande (push/pull), pas en périodique.
                    .setTileTimeline(Timeline.fromLayoutElement(layout(counts, palette, pad)))
                    .build()
            },
            executor,
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<Resources> {
        val res = Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(IC_DO, imageRes(R.drawable.wear_do))
            .addIdToImageMapping(IC_SCHEDULE, imageRes(R.drawable.wear_schedule))
            .addIdToImageMapping(IC_DELEGATE, imageRes(R.drawable.wear_delegate))
            .addIdToImageMapping(IC_ELIMINATE, imageRes(R.drawable.wear_eliminate))
            .build()
        return Futures.immediateFuture(res)
    }

    private fun imageRes(resId: Int): ImageResource =
        ImageResource.Builder()
            .setAndroidResourceByResId(
                AndroidImageResourceByResId.Builder().setResourceId(resId).build(),
            )
            .build()

    /** Racine cliquable (ouvre le téléphone) contenant la grille 2×2. [pad] = marge d'inset (écran rond). */
    private fun layout(counts: IntArray, palette: IntArray, pad: Float): LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(BG)).build())
                    .setPadding(Padding.Builder().setAll(dp(pad)).build())
                    .setClickable(
                        Clickable.Builder()
                            .setId("open")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName("com.kairos.app")
                                            .setClassName("com.kairos.wear.OpenOnPhoneActivity")
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(grid(counts, palette))
            .build()

    /**
     * Grille de l'app : haut (Faire, Planifier) puis bas (Déléguer, Supprimer). Couleurs = [palette]
     * (ordinal 0..3), encre déterminée par la luminance de chaque accent (lisible sur clair comme foncé).
     */
    private fun grid(counts: IntArray, palette: IntArray): LayoutElement =
        Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(cell(IC_DO, counts[0], palette[0], inkFor(palette[0])))
                    .addContent(gap())
                    .addContent(cell(IC_SCHEDULE, counts[1], palette[1], inkFor(palette[1])))
                    .build(),
            )
            .addContent(gap())
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(cell(IC_DELEGATE, counts[2], palette[2], inkFor(palette[2])))
                    .addContent(gap())
                    .addContent(cell(IC_ELIMINATE, counts[3], palette[3], inkFor(palette[3])))
                    .build(),
            )
            .build()

    /** Encre lisible sur un accent : sombre si l'accent est clair, blanche sinon (luminance perçue). */
    private fun inkFor(color: Int): Int {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.6) DARK else WHITE
    }

    /** Une tuile de quadrant : fond coloré arrondi, icône au-dessus du compteur. */
    private fun cell(iconId: String, count: Int, bg: Int, ink: Int): LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(bg))
                            .setCorner(Corner.Builder().setRadius(dp(20f)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setWidth(wrap())
                    .setHeight(wrap())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Image.Builder()
                            .setResourceId(iconId)
                            .setWidth(dp(20f))
                            .setHeight(dp(20f))
                            .setColorFilter(ColorFilter.Builder().setTint(argb(ink)).build())
                            .build(),
                    )
                    .addContent(Spacer.Builder().setHeight(dp(3f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(if (count > 99) "99+" else count.toString())
                            .setMaxLines(1)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(22f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(ink))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun gap(): LayoutElement = Spacer.Builder().setWidth(dp(6f)).setHeight(dp(6f)).build()

    companion object {
        /** Thread de fond pour la lecture Data Layer (Tasks.await bloquant) hors du thread principal. */
        private val executor = Executors.newSingleThreadExecutor()

        private const val RESOURCES_VERSION = "1"

        private const val IC_DO = "ic_do"
        private const val IC_SCHEDULE = "ic_schedule"
        private const val IC_DELEGATE = "ic_delegate"
        private const val IC_ELIMINATE = "ic_eliminate"

        // Fond de la tuile + encres candidates (choisies par luminance de l'accent, cf. inkFor).
        private const val BG = 0xFF201A18.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DARK = 0xFF1D1D1B.toInt()
    }
}
