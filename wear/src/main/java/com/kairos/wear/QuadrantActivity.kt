package com.kairos.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Parcours des tâches d'un quadrant sur la montre (lecture + ✓ terminer), ouvert depuis l'app montre
 * ou directement depuis une case de la tuile matrice. Flèches ‹ › pour passer
 * d'un quadrant à l'autre ; **glisser vers la gauche** = quadrant suivant (le glisser vers la droite
 * reste le geste système « retour » de Wear OS). Données : [QuadrantsStore] (poussées par le téléphone,
 * dans l'ordre de tri choisi dans l'app).
 */
class QuadrantActivity : Activity() {

    private var quadrant = 0

    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                val dy = e2.y - (e1?.y ?: e2.y)
                if (dx < -SWIPE_DISTANCE && abs(dx) > abs(dy) * 1.5f) {
                    show((quadrant + 1) % 4)
                    return true
                }
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quadrant)
        quadrant = (savedInstanceState?.getInt(EXTRA_QUADRANT) ?: intent.getIntExtra(EXTRA_QUADRANT, 0)).coerceIn(0, 3)
        findViewById<ImageButton>(R.id.quad_prev).setOnClickListener { show((quadrant + 3) % 4) }
        findViewById<ImageButton>(R.id.quad_next).setOnClickListener { show((quadrant + 1) % 4) }
    }

    override fun onResume() {
        super.onResume()
        render()
        Thread {
            CountsSync.pull(this)
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_QUADRANT, quadrant)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Le geste est observé avant le défilement : un glissement horizontal net change de quadrant.
        if (gestures.onTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun show(q: Int) {
        quadrant = q
        render()
        findViewById<View>(R.id.quadrant_scroll).scrollTo(0, 0)
    }

    private fun render() {
        val palette = PaletteStore.load(this)
        val accent = palette[quadrant]
        val all = QuadrantsStore.load(this).filter { it.quadrant == quadrant }
        val total = CountsStore.load(this).getOrElse(quadrant) { all.size }

        // Icône seule (le verbe tronqué dépassait) : le nom du quadrant reste lu par TalkBack.
        findViewById<ImageView>(R.id.quad_icon).apply {
            setImageResource(WearUi.quadrantIcon[quadrant])
            imageTintList = ColorStateList.valueOf(accent)
            contentDescription = getString(WearUi.quadrantVerb[quadrant])
        }
        findViewById<TextView>(R.id.quad_count).text = total.toString()

        val container = findViewById<LinearLayout>(R.id.quad_container)
        container.removeAllViews()
        findViewById<TextView>(R.id.quad_empty).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        all.forEach { task ->
            container.addView(
                WearUi.taskRow(this, container, task.title, task.quadrant, task.labelColor, palette) {
                    WearUi.completeOnPhone(this, task.id) {
                        QuadrantsStore.remove(this, task.id)
                        FocusStore.remove(this, task.id)
                        render()
                    }
                },
            )
        }
        // Le téléphone ne pousse qu'un nombre limité de tâches par quadrant.
        val more = total - all.size
        findViewById<TextView>(R.id.quad_more).apply {
            visibility = if (more > 0) View.VISIBLE else View.GONE
            text = getString(R.string.quadrant_more, more)
        }
    }

    companion object {
        const val EXTRA_QUADRANT = "quadrant"
        private const val SWIPE_DISTANCE = 80f

        fun intent(context: Context, quadrant: Int): Intent =
            Intent(context, QuadrantActivity::class.java).putExtra(EXTRA_QUADRANT, quadrant)
    }
}
