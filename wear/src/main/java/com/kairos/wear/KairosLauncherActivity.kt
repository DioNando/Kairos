package com.kairos.wear

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper

/**
 * App Kairós sur la montre : **« À faire en priorité »** — la tâche prioritaire + un aperçu « À suivre »,
 * reflet de l'écran Focus du téléphone (classement identique, calculé côté téléphone et poussé via le
 * Data Layer dans [FocusStore]). Deux **boutons ronds** : *ajout rapide* (ouvre l'éditeur de tâche sur
 * le téléphone, `kairos://new`) et *ouvrir l'app* (`kairos://open`).
 *
 * La saisie reste sur le téléphone (la montre n'a pas la base Room) ; l'écran est une façade de lecture
 * + télécommande. La **tuile** reste la matrice 2×2 des compteurs ([KairosTileService]), inchangée.
 */
class KairosLauncherActivity : Activity() {

    // Ressources par quadrant (ordinal 0..3 = DO/SCHEDULE/DELEGATE/ELIMINATE). Les couleurs ne sont
    // PAS ici : elles viennent de [PaletteStore] (poussées par le téléphone) pour suivre la palette.
    private val quadrantIcon = intArrayOf(
        R.drawable.wear_do, R.drawable.wear_schedule, R.drawable.wear_delegate, R.drawable.wear_eliminate,
    )
    private val quadrantVerb = intArrayOf(
        R.string.verb_do, R.string.verb_schedule, R.string.verb_delegate, R.string.verb_eliminate,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        findViewById<ImageButton>(R.id.btn_add).setOnClickListener { openOnPhone("kairos://new") }
        findViewById<ImageButton>(R.id.btn_open).setOnClickListener { openOnPhone("kairos://open") }
    }

    override fun onResume() {
        super.onResume()
        // 1) Affiche tout de suite le dernier état persisté (instantané, éventuellement vide).
        render()
        // 2) Va lire le DataItem courant sur le Data Layer (hors thread principal), puis rafraîchit :
        //    indispensable car onDataChanged ne reçoit que les CHANGEMENTS, pas l'état déjà publié.
        Thread {
            CountsSync.pull(this)
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }.start()
    }

    private fun render() {
        val items = FocusStore.load(this)
        val card = findViewById<LinearLayout>(R.id.priority_card)
        val empty = findViewById<TextView>(R.id.empty_view)
        val upNextLabel = findViewById<TextView>(R.id.upnext_label)
        val upNextContainer = findViewById<LinearLayout>(R.id.upnext_container)
        upNextContainer.removeAllViews()

        if (items.isEmpty()) {
            card.visibility = View.GONE
            upNextLabel.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        card.visibility = View.VISIBLE

        // Palette courante (poussée par le téléphone, repli « Douce » si rien n'a encore été reçu).
        val palette = PaletteStore.load(this)

        // Tâche prioritaire : accent du quadrant (icône + verbe + fond teinté).
        val top = items.first()
        val q = top.quadrant.coerceIn(0, 3)
        val accent = palette[q]
        findViewById<ImageView>(R.id.priority_icon).apply {
            setImageResource(quadrantIcon[q])
            imageTintList = ColorStateList.valueOf(accent)
        }
        findViewById<TextView>(R.id.priority_verb).apply {
            text = getString(quadrantVerb[q])
            setTextColor(accent)
        }
        findViewById<TextView>(R.id.priority_title).text = top.title
        card.background = GradientDrawable().apply {
            cornerRadius = dp(18f)
            // Accent du quadrant en fond translucide (~18 %), comme la carte de l'écran Focus.
            setColor(withAlpha(accent, 0x2E))
        }

        // Aperçu « À suivre » : les tâches suivantes, sans les sortir de la file.
        val upNext = items.drop(1)
        if (upNext.isEmpty()) {
            upNextLabel.visibility = View.GONE
        } else {
            upNextLabel.visibility = View.VISIBLE
            upNext.forEach { item -> upNextContainer.addView(upNextRow(item, palette, upNextContainer)) }
        }
    }

    /** Une ligne « à suivre » : puce colorée au quadrant + titre. */
    private fun upNextRow(item: FocusStore.Item, palette: IntArray, parent: LinearLayout): View {
        val row = layoutInflater.inflate(R.layout.item_upnext, parent, false)
        val q = item.quadrant.coerceIn(0, 3)
        row.findViewById<View>(R.id.dot).backgroundTintList =
            ColorStateList.valueOf(palette[q])
        row.findViewById<TextView>(R.id.upnext_title).text = item.title
        return row
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /** Applique un canal alpha [alpha] (0..255) à une couleur opaque. */
    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Demande au téléphone d'ouvrir le deep link voulu (ajout rapide ou app) via [RemoteActivityHelper]. */
    private fun openOnPhone(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(uri))
        runCatching { RemoteActivityHelper(this).startRemoteActivity(intent) }
        Toast.makeText(this, R.string.open_on_phone, Toast.LENGTH_SHORT).show()
    }
}
