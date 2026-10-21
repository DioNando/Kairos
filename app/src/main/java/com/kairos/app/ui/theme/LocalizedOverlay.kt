package com.kairos.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

/**
 * Contexte **localisé** (langue in-app) fourni au niveau racine par `MainActivity`.
 *
 * Pourquoi un local dédié plutôt que de relire `LocalContext` : les fenêtres détachées
 * (`Dialog`, `Popup`, `DropdownMenu`, `ModalBottomSheet`) sont rendues dans leur propre fenêtre qui
 * **re-fournit** `LocalContext`/`LocalConfiguration` depuis le contexte système — l'override de
 * langue posé autour du NavHost n'y arrive donc pas (texte qui retombe en locale système). Les
 * `CompositionLocal` **custom**, eux, traversent bien ces sous-compositions. On transporte donc le
 * contexte localisé via ce local, et [LocalizedOverlay] le ré-applique à l'intérieur de l'overlay.
 *
 * `null` = pas d'override (langue = système) → [LocalizedOverlay] est alors un simple passe-plat.
 *
 * ⚠️ Depuis Compose UI 1.12, `stringResource` lit `LocalResources.current` (et non plus
 * `LocalContext.current.resources`). `LocalResources` n'est jamais fourni explicitement : il repose
 * sur un *default calculé* qui relit `LocalContext`/`LocalConfiguration`. Cette recomputation n'est
 * pas fiable à travers la frontière d'une fenêtre détachée (dialog/sheet) → on **fournit
 * explicitement `LocalResources`** ici, en plus de `LocalContext`/`LocalConfiguration`.
 */
val LocalLocalizedContext = compositionLocalOf<Context?> { null }

/**
 * À poser **autour du contenu de tout overlay** (dialog, menu, bottom sheet) pour lui rendre la
 * langue in-app. Sans override actif, ne fait rien.
 *
 * ```
 * ModalBottomSheet(onDismissRequest = …) {
 *     LocalizedOverlay {
 *         // stringResource() résout ici dans la langue choisie dans les réglages
 *     }
 * }
 * ```
 */
@Composable
fun LocalizedOverlay(content: @Composable () -> Unit) {
    val localized = LocalLocalizedContext.current
    if (localized == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalResources provides localized.resources,
            content = content,
        )
    }
}
