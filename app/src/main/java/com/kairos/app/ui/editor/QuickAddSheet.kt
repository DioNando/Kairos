package com.kairos.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.kairos.app.R
import com.kairos.app.data.model.Quadrant
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.MorphingIconPill
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.quadrantVisual
import com.kairos.app.ui.theme.LocalizedOverlay

/**
 * Saisie rapide sans friction : un champ titre + le choix du quadrant (grille 2×2 de cards), avec le
 * clavier ouvert d'emblée. « Plus d'options » bascule vers l'éditeur complet en conservant le titre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    startQuadrant: Quadrant,
    onDismiss: () -> Unit,
    onQuickAdd: (String, Quadrant) -> Unit,
    onMoreOptions: (String, Quadrant) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var quadrant by remember { mutableStateOf(startQuadrant) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    fun submit() {
        if (title.isNotBlank()) {
            onQuickAdd(title.trim(), quadrant)
            keyboard?.hide()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
      LocalizedOverlay {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.quickadd_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                shape = KairosFieldShape,
                colors = kairosFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            // Choix du quadrant : 4 cards sur 2 lignes (plus harmonieux qu'une rangée de chips).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuadrantCard(Quadrant.DO, quadrant == Quadrant.DO, { quadrant = Quadrant.DO }, Modifier.weight(1f))
                    QuadrantCard(Quadrant.SCHEDULE, quadrant == Quadrant.SCHEDULE, { quadrant = Quadrant.SCHEDULE }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuadrantCard(Quadrant.DELEGATE, quadrant == Quadrant.DELEGATE, { quadrant = Quadrant.DELEGATE }, Modifier.weight(1f))
                    QuadrantCard(Quadrant.ELIMINATE, quadrant == Quadrant.ELIMINATE, { quadrant = Quadrant.ELIMINATE }, Modifier.weight(1f))
                }
            }
            // « Plus d'options » à gauche, « Enregistrer » compact à droite (pas pleine largeur).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onMoreOptions(title.trim(), quadrant) }) {
                    Text(stringResource(R.string.quickadd_more))
                }
                Button(
                    onClick = { submit() },
                    enabled = title.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.size(2.dp))
        }
      }
    }
}

/**
 * Card de quadrant sélectionnable (tonal, sans bordure). La pastille d'icône **morphe** de forme
 * (carré → « cookie ») à la sélection — touche Material 3 Expressive.
 */
@Composable
private fun QuadrantCard(
    quadrant: Quadrant,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = quadrantVisual(quadrant)
    val cardBackground = if (selected) {
        visual.accent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MorphingIconPill(icon = visual.icon, accent = visual.accent, selected = selected)
        Text(
            visual.verb,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
