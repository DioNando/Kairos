package com.kairos.app.data.model

/**
 * Les 4 quadrants de la matrice d'Eisenhower, dérivés des deux axes (important × urgent).
 * Chaque quadrant porte le **verbe d'action** qui en découle.
 */
enum class Quadrant(val important: Boolean, val urgent: Boolean) {
    DO(important = true, urgent = true),          // Faire — urgent + important
    SCHEDULE(important = true, urgent = false),   // Planifier — important, pas urgent
    DELEGATE(important = false, urgent = true),   // Déléguer — urgent, pas important
    ELIMINATE(important = false, urgent = false); // Supprimer — ni l'un ni l'autre

    companion object {
        /** Quadrant correspondant à une combinaison des deux axes. */
        fun of(important: Boolean, urgent: Boolean): Quadrant =
            entries.first { it.important == important && it.urgent == urgent }
    }
}
