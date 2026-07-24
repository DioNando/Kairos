package com.kairos.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

/** Convertit un « #RRGGBB » (ou « #AARRGGBB ») en [Color], gris-lavande par défaut si invalide. */
fun String.toColor(): Color =
    runCatching { Color(toColorInt()) }.getOrDefault(Color(0xFF9C9AA6))
