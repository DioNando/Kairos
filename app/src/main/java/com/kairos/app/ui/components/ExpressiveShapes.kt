package com.kairos.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/**
 * Pastille d'icône **morphante** (M3 Expressive) : forme carré → « cookie » à la sélection, fond
 * accent plein si sélectionnée, sinon accent très clair. Sans bordure (charte tonale).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingIconPill(
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pill-morph",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(MorphShape(morph, progress))
            .background(if (selected) accent else accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) onAccent(accent) else accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Case à cocher **morphante** (M3 Expressive) : cercle (décochée, accent clair) → « cookie » plein
 * (cochée) avec une coche qui apparaît en fondu. Sans bordure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingCheckbox(
    checked: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    boxSize: Dp = 24.dp,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "check-morph",
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(MorphShape(morph, progress))
                .background(if (checked) accent else accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = contentDescription,
                tint = onAccent(accent),
                modifier = Modifier
                    .size(boxSize * 0.62f)
                    .graphicsLayer { alpha = progress },
            )
        }
    }
}

/**
 * Pastille de **couleur** sélectionnable qui morphe (cercle → « cookie ») à la sélection, avec une
 * coche en fondu. La forme **est** la couleur (pas de pastille d'accent séparée). Sans bordure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "swatch-morph",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(MorphShape(morph, progress))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = onAccent(color),
            modifier = Modifier
                .size(size * 0.55f)
                .graphicsLayer { alpha = progress },
        )
    }
}
