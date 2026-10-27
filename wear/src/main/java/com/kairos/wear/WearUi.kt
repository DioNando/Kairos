package com.kairos.wear

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Ressources et helpers d'affichage partagés par les écrans de la montre. */
object WearUi {
    /** Icône par quadrant (ordinal 0..3 = DO/SCHEDULE/DELEGATE/ELIMINATE). */
    val quadrantIcon = intArrayOf(
        R.drawable.wear_do, R.drawable.wear_schedule, R.drawable.wear_delegate, R.drawable.wear_eliminate,
    )
    val quadrantVerb = intArrayOf(
        R.string.verb_do, R.string.verb_schedule, R.string.verb_delegate, R.string.verb_eliminate,
    )

    /** Parse une couleur hex `#RRGGBB` ; replie sur [fallback] si vide ou invalide. */
    fun parseColor(hex: String, fallback: Int): Int = runCatching { Color.parseColor(hex) }.getOrDefault(fallback)

    /** Applique un canal alpha [alpha] (0..255) à une couleur opaque. */
    fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Encre lisible sur un accent : sombre si l'accent est clair, blanche sinon (luminance perçue). */
    fun inkFor(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (luminance > 0.6) 0xFF1D1D1B.toInt() else Color.WHITE
    }

    /**
     * Ligne de tâche (item_upnext) : icône du quadrant teintée, titre, pastille d'étiquette éventuelle
     * et bouton ✓ qui appelle [onComplete].
     */
    fun taskRow(
        activity: Activity,
        parent: LinearLayout,
        title: String,
        quadrant: Int,
        labelColor: String,
        palette: IntArray,
        onComplete: () -> Unit,
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.item_upnext, parent, false)
        val q = quadrant.coerceIn(0, 3)
        row.findViewById<ImageView>(R.id.upnext_icon).apply {
            setImageResource(quadrantIcon[q])
            imageTintList = ColorStateList.valueOf(palette[q])
        }
        row.findViewById<TextView>(R.id.upnext_title).text = title
        val labelDot = row.findViewById<View>(R.id.upnext_label_dot)
        if (labelColor.isNotBlank()) {
            labelDot.visibility = View.VISIBLE
            labelDot.backgroundTintList = ColorStateList.valueOf(parseColor(labelColor, palette[q]))
        } else {
            labelDot.visibility = View.GONE
        }
        row.findViewById<ImageButton>(R.id.upnext_done).apply {
            imageTintList = ColorStateList.valueOf(palette[q])
            setOnClickListener { onComplete() }
        }
        return row
    }

    /**
     * Envoie « terminer [taskId] » au téléphone hors du thread principal ; en cas de succès applique
     * le retrait optimiste via [onSent] (sur le thread principal), sinon prévient que le téléphone est
     * injoignable.
     */
    fun completeOnPhone(activity: Activity, taskId: Long, onSent: () -> Unit) {
        if (taskId <= 0L) return
        Thread {
            val ok = PhoneActions.complete(activity, taskId)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (ok) {
                    onSent()
                    Toast.makeText(activity, R.string.done_sent, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, R.string.phone_unreachable, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
