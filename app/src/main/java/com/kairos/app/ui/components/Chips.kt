package com.kairos.app.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Chips harmonisés à la **règle maître de la charte** : style tonal, **aucune bordure**.
 * - Non sélectionné : rempli `surfaceContainerHighest` (gris tonal).
 * - Sélectionné : rempli `primary` (terracotta de marque), libellé/icône contrastés.
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
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = null,
    )
}

/**
 * Chip d'assistance tonal (sélecteurs « pastille + libellé » type échéance / tâche liée) : rempli
 * `surfaceContainerHighest`, **aucune bordure**.
 */
@Composable
fun KairosAssistChip(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        border = null,
    )
}
