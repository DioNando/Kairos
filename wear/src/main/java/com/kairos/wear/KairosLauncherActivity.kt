package com.kairos.wear

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * App Kairós sur la montre :
 * - **« À faire en priorité »** : la tâche prioritaire + « À suivre », reflet de l'écran Focus du
 *   téléphone, chacune avec un **✓** qui la termine (action envoyée au téléphone) ;
 * - le **Pomodoro** en cours : décompte local (heure de fin poussée par le téléphone), marche/pause
 *   et remise à zéro à distance ;
 * - les **créneaux du jour** (time-blocking) ;
 * - les **4 quadrants** (compteurs) → parcours d'un quadrant ([QuadrantActivity]) ;
 * - deux boutons ronds : ajout rapide (`kairos://new`) et ouvrir l'app (`kairos://open`).
 *
 * La base reste sur le téléphone : la montre affiche l'état poussé par le Data Layer et renvoie des
 * actions ([PhoneActions]).
 */
class KairosLauncherActivity : Activity() {

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderPomodoro()
            ticker.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        findViewById<ImageButton>(R.id.btn_add).setOnClickListener { openOnPhone("kairos://new") }
        findViewById<ImageButton>(R.id.btn_open).setOnClickListener { openOnPhone("kairos://open") }
        findViewById<ImageButton>(R.id.pomo_toggle).setOnClickListener { sendPomodoro(toggle = true) }
        findViewById<ImageButton>(R.id.pomo_reset).setOnClickListener { sendPomodoro(toggle = false) }
        listOf(R.id.quad_0, R.id.quad_1, R.id.quad_2, R.id.quad_3).forEachIndexed { q, id ->
            findViewById<View>(id).setOnClickListener { startActivity(QuadrantActivity.intent(this, q)) }
        }
    }

    override fun onResume() {
        super.onResume()
        // 1) Affiche tout de suite le dernier état persisté (instantané, éventuellement vide).
        render()
        ticker.post(tick)
        // 2) Va lire les DataItems courants sur le Data Layer (hors thread principal), puis rafraîchit :
        //    indispensable car onDataChanged ne reçoit que les CHANGEMENTS, pas l'état déjà publié.
        Thread {
            CountsSync.pull(this)
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        ticker.removeCallbacks(tick)
    }

    private fun render() {
        val palette = PaletteStore.load(this)
        renderFocus(palette)
        renderPomodoro()
        renderToday(palette)
        renderQuadrants(palette)
    }

    private fun renderFocus(palette: IntArray) {
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

        // Tâche prioritaire : accent du quadrant (icône + verbe + fond teinté).
        val top = items.first()
        val q = top.quadrant.coerceIn(0, 3)
        val accent = palette[q]
        findViewById<ImageView>(R.id.priority_icon).apply {
            setImageResource(WearUi.quadrantIcon[q])
            imageTintList = ColorStateList.valueOf(accent)
        }
        findViewById<TextView>(R.id.priority_verb).apply {
            text = getString(WearUi.quadrantVerb[q])
            setTextColor(accent)
        }
        findViewById<TextView>(R.id.priority_title).text = top.title
        card.background = GradientDrawable().apply {
            cornerRadius = dp(18f)
            // Accent du quadrant en fond translucide (~18 %), comme la carte de l'écran Focus.
            setColor(WearUi.withAlpha(accent, 0x2E))
        }
        findViewById<ImageButton>(R.id.priority_done).apply {
            backgroundTintList = ColorStateList.valueOf(accent)
            imageTintList = ColorStateList.valueOf(WearUi.inkFor(accent))
            setOnClickListener { complete(top.id) }
        }

        // Pastille d'étiquette (point coloré + nom), affichée seulement si la tâche en a une.
        val labelRow = findViewById<LinearLayout>(R.id.priority_label_row)
        if (top.labelName.isNotBlank()) {
            labelRow.visibility = View.VISIBLE
            findViewById<View>(R.id.priority_label_dot).backgroundTintList =
                ColorStateList.valueOf(WearUi.parseColor(top.labelColor, accent))
            findViewById<TextView>(R.id.priority_label_name).text = top.labelName
        } else {
            labelRow.visibility = View.GONE
        }

        // Aperçu « À suivre » : les tâches suivantes, chacune terminable.
        val upNext = items.drop(1)
        upNextLabel.visibility = if (upNext.isEmpty()) View.GONE else View.VISIBLE
        upNext.forEach { item ->
            upNextContainer.addView(
                WearUi.taskRow(this, upNextContainer, item.title, item.quadrant, item.labelColor, palette) {
                    complete(item.id)
                },
            )
        }
    }

    /** Carte Pomodoro : visible pendant une session, décompte recalculé chaque seconde (sans réseau). */
    private fun renderPomodoro() {
        val pomo = PomodoroStore.load(this)
        val card = findViewById<LinearLayout>(R.id.pomo_card)
        if (!pomo.active) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val phase = getString(
            when (pomo.phase) {
                1 -> R.string.pomo_short
                2 -> R.string.pomo_long
                else -> R.string.pomo_focus
            },
        )
        findViewById<TextView>(R.id.pomo_phase).text = if (pomo.running) phase else getString(R.string.pomo_paused, phase)
        val remaining = pomo.remainingSeconds()
        findViewById<TextView>(R.id.pomo_time).text = "%02d:%02d".format(remaining / 60, remaining % 60)
        findViewById<ImageButton>(R.id.pomo_toggle).setImageResource(if (pomo.running) R.drawable.ic_pause else R.drawable.ic_play)
    }

    /** Créneaux planifiés restants aujourd'hui (le créneau en cours en tête). */
    private fun renderToday(palette: IntArray) {
        val blocks = PlanningStore.remainingToday(this)
        val label = findViewById<TextView>(R.id.today_label)
        val container = findViewById<LinearLayout>(R.id.today_container)
        container.removeAllViews()
        label.visibility = if (blocks.isEmpty()) View.GONE else View.VISIBLE
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val zone = ZoneId.systemDefault()
        blocks.forEach { block ->
            val row = layoutInflater.inflate(R.layout.item_block, container, false)
            val accent = palette[block.quadrant.coerceIn(0, 3)]
            row.findViewById<View>(R.id.block_bar).backgroundTintList = ColorStateList.valueOf(accent)
            row.findViewById<TextView>(R.id.block_time).text =
                time.format(Instant.ofEpochMilli(block.start).atZone(zone)) + "–" +
                time.format(Instant.ofEpochMilli(block.end).atZone(zone))
            row.findViewById<TextView>(R.id.block_title).text = block.title
            container.addView(row)
        }
    }

    /** Puces des 4 quadrants : couleur de la palette, icône + compteur. */
    private fun renderQuadrants(palette: IntArray) {
        val counts = CountsStore.load(this)
        listOf(R.id.quad_0, R.id.quad_1, R.id.quad_2, R.id.quad_3).forEachIndexed { q, id ->
            val chip = findViewById<View>(id)
            val ink = WearUi.inkFor(palette[q])
            chip.backgroundTintList = ColorStateList.valueOf(palette[q])
            chip.contentDescription = getString(WearUi.quadrantVerb[q]) + " : " + counts[q]
            chip.findViewById<ImageView>(R.id.chip_icon).apply {
                setImageResource(WearUi.quadrantIcon[q])
                imageTintList = ColorStateList.valueOf(ink)
            }
            chip.findViewById<TextView>(R.id.chip_count).apply {
                text = if (counts[q] > 99) "99+" else counts[q].toString()
                setTextColor(ink)
            }
        }
    }

    /** Termine une tâche (téléphone) puis la retire tout de suite de l'affichage (optimiste). */
    private fun complete(taskId: Long) {
        WearUi.completeOnPhone(this, taskId) {
            FocusStore.remove(this, taskId)
            QuadrantsStore.remove(this, taskId)
            render()
        }
    }

    /** Marche/pause ([toggle]) ou remise à zéro du Pomodoro, exécutée par le téléphone. */
    private fun sendPomodoro(toggle: Boolean) {
        Thread {
            val ok = if (toggle) PhoneActions.togglePomodoro(this) else PhoneActions.resetPomodoro(this)
            if (!ok) runOnUiThread { Toast.makeText(this, R.string.phone_unreachable, Toast.LENGTH_SHORT).show() }
        }.start()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /** Demande au téléphone d'ouvrir le deep link voulu (ajout rapide ou app) via [RemoteActivityHelper]. */
    private fun openOnPhone(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(uri))
        runCatching { RemoteActivityHelper(this).startRemoteActivity(intent) }
        Toast.makeText(this, R.string.open_on_phone, Toast.LENGTH_SHORT).show()
    }
}
