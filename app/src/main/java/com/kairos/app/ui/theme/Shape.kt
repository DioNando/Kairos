package com.kairos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Échelle de formes Kairós, alignée sur Material 3 Expressive (coins généreux) et la charte
 * (cartes 16–20.dp, champs 16.dp). Passée à [androidx.compose.material3.MaterialExpressiveTheme] :
 * tous les composants Material l'adoptent automatiquement.
 */
val KairosShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
