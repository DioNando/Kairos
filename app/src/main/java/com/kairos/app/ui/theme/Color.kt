package com.kairos.app.ui.theme

import androidx.compose.ui.graphics.Color

// Charte Kairós — palette « Douce » : marque terracotta chaleureuse, accents désaturés.
val SoftClay = Color(0xFFE5836B)      // terracotta doux (primary clair)
val SoftClayDeep = Color(0xFFC7654F)  // terracotta profond (pressé)
val SoftClaySoft = Color(0xFFFADFD5)  // terracotta clair (primary container)

// Thème clair : premier plan en encre chaude sur conteneurs pêche.
val PrimaryLight = SoftClay
val OnPrimaryContainerLight = Color(0xFF4A2415)
val SecondaryLight = Color(0xFF7E6B9E)          // mauve doux (secondaire)

// Thème sombre : accent clair sur conteneurs sombres chauds.
val PrimaryDark = Color(0xFFFFB59C)
val OnPrimaryDark = Color(0xFF5A2415)
val PrimaryContainerDark = Color(0xFF7A3E2C)
val OnPrimaryContainerDark = Color(0xFFFADFD5)

// Accents des quadrants de la palette par défaut (« Douce »).
val QuadrantDo = Color(0xFFE5645A)      // corail doux — Faire (urgent + important)
val QuadrantSchedule = Color(0xFF4FA5A0) // sarcelle apaisée — Planifier (important)
val QuadrantDelegate = Color(0xFFE0A24E) // ambre chaud — Déléguer (urgent)
val QuadrantEliminate = Color(0xFF9C9AA6) // gris-lavande — Supprimer (ni/ni)
