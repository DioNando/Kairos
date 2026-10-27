package com.kairos.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Hauteur commune des chips : plus généreuse que les 32 dp M3 par défaut (cible tactile confortable). */
private val ChipHeight = 44.dp

/** Taille de l'icône de tête (alignée sur les chips M3). */
private val ChipIconSize = 18.dp

/**
 * Marges internes : 20 dp de part et d'autre du libellé seul ; avec icône, 16 dp avant l'icône et
 * 8 dp entre icône et libellé, pour que l'icône paraisse centrée optiquement dans son arrondi.
 */
private val ChipPadding = PaddingValues(horizontal = 20.dp)
private val ChipPaddingWithIcon = PaddingValues(start = 16.dp, end = 20.dp)
private val ChipIconGap = 8.dp

/**
 * Pilule tonale commune aux chips Kairos : forme [CircleShape], **aucune bordure**, transition de
 * couleur douce entre états.
 */
@Composable
private fun KairosPill(
    onClick: () -> Unit,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    selected: Boolean?,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
) {
    val containerColor by animateColorAsState(container, label = "chip-bg")
    val contentColor by animateColorAsState(content, label = "chip-fg")
    val pillModifier = modifier.height(ChipHeight).alpha(if (enabled) 1f else 0.38f)
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(if (leadingIcon != null) ChipPaddingWithIcon else ChipPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChipIconGap),
            ) {
                if (leadingIcon != null) {
                    Box(Modifier.size(ChipIconSize), contentAlignment = Alignment.Center) { leadingIcon() }
                }
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                if (trailingIcon != null) {
                    Box(Modifier.size(ChipIconSize), contentAlignment = Alignment.Center) { trailingIcon() }
                }
            }
        }
    }
    if (selected != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            modifier = pillModifier,
            content = { Box(contentAlignment = Alignment.Center) { body() } },
        )
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            modifier = pillModifier,
            content = { Box(contentAlignment = Alignment.Center) { body() } },
        )
    }
}

/**
 * Chips harmonisés à la **règle maître de la charte** : style tonal, **aucune bordure**, forme pilule.
 * - Non sélectionné : rempli `surfaceContainerHighest` (gris tonal).
 * - Sélectionné : rempli `primary` (terracotta de marque), libellé/icône contrastés.
 *
 * [selectedContainerColor] permet de teinter la sélection (ex. couleur de pastille d'une étiquette) ;
 * libellé et icône basculent alors sur [onAccent] pour rester lisibles.
 *
 * Le slot [leadingIcon] est libre : les appelants qui veulent une icône colorée (accent de quadrant)
 * passent leur propre `Icon(tint = …)` ; sinon l'icône suit la couleur tonale du chip.
 */
@Composable
fun KairosFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selectedContainerColor: Color? = null,
) {
    val selectedContainer = selectedContainerColor ?: MaterialTheme.colorScheme.primary
    val selectedContent = selectedContainerColor?.let { onAccent(it) } ?: MaterialTheme.colorScheme.onPrimary
    KairosPill(
        onClick = onClick,
        label = label,
        container = if (selected) selectedContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        content = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = null,
    )
}

/**
 * Chip d'assistance tonal (sélecteurs « pastille + libellé » type échéance / tâche liée) : rempli
 * `surfaceContainerHighest`, **aucune bordure**, forme pilule.
 */
@Composable
fun KairosAssistChip(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    KairosPill(
        onClick = onClick,
        label = label,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        selected = null,
        enabled = true,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}
