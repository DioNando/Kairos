package com.kairos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.AppSettings
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.AppPalettes
import com.kairos.app.ui.theme.dynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetThemeMode: (Int) -> Unit,
    onSetLanguage: (Int) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetPalette: (Int) -> Unit,
    onSetCustomPalette: (doColor: String, schedule: String, delegate: String, eliminate: String) -> Unit,
    onSetUrgencyThreshold: (Int) -> Unit,
    onSetSwimLanes: (Boolean) -> Unit,
    onSetArchivePurgeDays: (Int) -> Unit,
    showGuide: Boolean,
    onSetShowGuide: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onSetPomodoroStyle: (Boolean) -> Unit,
    onSetPomodoroDurations: (focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onSetReminderLead: (Int) -> Unit,
    geminiKey: String,
    openRouterKey: String,
    onSetAiProvider: (Int) -> Unit,
    onSetAiKey: (provider: Int, key: String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Le contenu se décale au-dessus du clavier (champs de clé API en bas de page).
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Thème.
            SectionTitle(stringResource(R.string.settings_theme))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(
                    AppSettings.THEME_SYSTEM to R.string.theme_system,
                    AppSettings.THEME_LIGHT to R.string.theme_light,
                    AppSettings.THEME_DARK to R.string.theme_dark,
                )
                modes.forEach { (mode, res) ->
                    KairosFilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onSetThemeMode(mode) },
                        label = stringResource(res),
                    )
                }
            }

            // Langue de l'app.
            SectionTitle(stringResource(R.string.settings_language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val langs = listOf(
                    AppSettings.LANG_SYSTEM to R.string.lang_system,
                    AppSettings.LANG_FR to R.string.lang_fr,
                    AppSettings.LANG_EN to R.string.lang_en,
                )
                langs.forEach { (id, res) ->
                    KairosFilterChip(
                        selected = settings.language == id,
                        onClick = { onSetLanguage(id) },
                        label = stringResource(res),
                    )
                }
            }

            // Palette des quadrants : liste radio verticale avec aperçu des 4 accents.
            SectionTitle(stringResource(R.string.settings_palette))
            val customIndex = AppPalettes.size
            Column {
                AppPalettes.forEachIndexed { index, palette ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetPalette(index) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = settings.paletteId == index, onClick = { onSetPalette(index) })
                        Text(palette.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(palette.doColor, palette.scheduleColor, palette.delegateColor, palette.eliminateColor)
                                .forEach { c ->
                                    Box(modifier = Modifier.size(16.dp).background(c, CircleShape))
                                }
                        }
                    }
                }
                // Entrée « Personnalisée ».
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSetPalette(customIndex) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = settings.paletteId >= customIndex,
                        onClick = { onSetPalette(customIndex) },
                    )
                    Text(
                        stringResource(R.string.palette_custom),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            settings.customDoColor, settings.customScheduleColor,
                            settings.customDelegateColor, settings.customEliminateColor,
                        ).forEach { hex ->
                            Box(modifier = Modifier.size(16.dp).background(hex.toColor(), CircleShape))
                        }
                    }
                }
            }
            // Champs hex (visibles quand la palette personnalisée est sélectionnée).
            if (settings.paletteId >= customIndex) {
                CustomPaletteEditor(settings, onSetCustomPalette)
            }

            // Bascules avec icône tonale + sous-titre.
            SettingSwitchRow(
                icon = Icons.Rounded.ViewAgenda,
                title = stringResource(R.string.settings_swimlanes),
                subtitle = stringResource(R.string.settings_swimlanes_desc),
                checked = settings.swimLanes,
                onCheckedChange = onSetSwimLanes,
            )
            if (dynamicColorSupported) {
                SettingSwitchRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_dynamic),
                    subtitle = stringResource(R.string.settings_dynamic_desc),
                    checked = settings.dynamicColor,
                    onCheckedChange = onSetDynamicColor,
                )
            }

            // Seuil d'urgence.
            SectionTitle(stringResource(R.string.settings_urgency))
            Text(
                pluralStringResource(
                    R.plurals.settings_urgency_value,
                    settings.urgencyThresholdHours,
                    settings.urgencyThresholdHours,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.urgencyThresholdHours.toFloat(),
                onValueChange = { onSetUrgencyThreshold(it.toInt()) },
                valueRange = 0f..168f,
            )
            Text(
                stringResource(R.string.settings_urgency_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Purge automatique de l'archive.
            SectionTitle(stringResource(R.string.settings_archive_purge))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    0 to R.string.purge_never,
                    7 to R.string.purge_7,
                    30 to R.string.purge_30,
                    90 to R.string.purge_90,
                )
                options.forEach { (days, res) ->
                    KairosFilterChip(
                        selected = settings.archivePurgeDays == days,
                        onClick = { onSetArchivePurgeDays(days) },
                        label = stringResource(res),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_archive_purge_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Rappels d'échéance.
            SectionTitle(stringResource(R.string.settings_reminders))
            SettingSwitchRow(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.reminders_enable),
                subtitle = stringResource(R.string.reminders_enable_desc),
                checked = settings.remindersEnabled,
                onCheckedChange = onSetRemindersEnabled,
            )
            if (settings.remindersEnabled) {
                Text(
                    stringResource(R.string.reminders_lead),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val leads = listOf(
                        0 to R.string.reminder_lead_now,
                        5 to R.string.reminder_lead_5,
                        15 to R.string.reminder_lead_15,
                        30 to R.string.reminder_lead_30,
                        60 to R.string.reminder_lead_60,
                    )
                    leads.forEach { (minutes, res) ->
                        KairosFilterChip(
                            selected = settings.reminderLeadMinutes == minutes,
                            onClick = { onSetReminderLead(minutes) },
                            label = stringResource(res),
                        )
                    }
                }
            }

            // Pomodoro.
            SettingSwitchRow(
                icon = Icons.Rounded.WaterDrop,
                title = stringResource(R.string.settings_pomodoro_style),
                subtitle = stringResource(R.string.settings_pomodoro_style_sub),
                checked = settings.pomodoroLiquid,
                onCheckedChange = onSetPomodoroStyle,
            )
            SectionTitle(stringResource(R.string.pomo_durations))
            var focus by remember { mutableStateOf(settings.focusMinutes.toString()) }
            var shortBreak by remember { mutableStateOf(settings.shortBreakMinutes.toString()) }
            var longBreak by remember { mutableStateOf(settings.longBreakMinutes.toString()) }
            var cycles by remember { mutableStateOf(settings.cyclesBeforeLongBreak.toString()) }
            fun pushDurations() = onSetPomodoroDurations(
                focus.toIntOrNull() ?: settings.focusMinutes,
                shortBreak.toIntOrNull() ?: settings.shortBreakMinutes,
                longBreak.toIntOrNull() ?: settings.longBreakMinutes,
                cycles.toIntOrNull() ?: settings.cyclesBeforeLongBreak,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DurationTile(
                    label = stringResource(R.string.pomo_focus_label),
                    value = focus,
                    onValueChange = { focus = it.filter(Char::isDigit).take(3); pushDurations() },
                    modifier = Modifier.weight(1f),
                )
                DurationTile(
                    label = stringResource(R.string.pomo_short_label),
                    value = shortBreak,
                    onValueChange = { shortBreak = it.filter(Char::isDigit).take(3); pushDurations() },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DurationTile(
                    label = stringResource(R.string.pomo_long_label),
                    value = longBreak,
                    onValueChange = { longBreak = it.filter(Char::isDigit).take(3); pushDurations() },
                    modifier = Modifier.weight(1f),
                )
                DurationTile(
                    label = stringResource(R.string.pomo_cycles_label),
                    value = cycles,
                    onValueChange = { cycles = it.filter(Char::isDigit).take(2); pushDurations() },
                    modifier = Modifier.weight(1f),
                )
            }

            // Aide : guide consultable + masquage du raccourci dans la barre du haut.
            SectionTitle(stringResource(R.string.settings_help))
            FilledTonalButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.MenuBook, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.guide_open))
            }
            SettingSwitchRow(
                icon = Icons.Rounded.HelpOutline,
                title = stringResource(R.string.settings_show_guide),
                subtitle = stringResource(R.string.settings_show_guide_desc),
                checked = showGuide,
                onCheckedChange = onSetShowGuide,
            )

            // Assistant IA : reformulation des notes (optionnel, désactivé sans clé).
            SectionTitle(stringResource(R.string.settings_ai))
            Text(
                stringResource(R.string.settings_ai_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val providers = listOf(
                    AppSettings.AI_GEMINI to R.string.ai_provider_gemini,
                    AppSettings.AI_OPENROUTER to R.string.ai_provider_openrouter,
                )
                providers.forEach { (id, res) ->
                    KairosFilterChip(
                        selected = settings.aiProvider == id,
                        onClick = { onSetAiProvider(id) },
                        label = stringResource(res),
                    )
                }
            }
            AiKeyField(
                label = stringResource(R.string.ai_key_gemini),
                value = geminiKey,
                onValueChange = { onSetAiKey(AppSettings.AI_GEMINI, it) },
            )
            AiKeyField(
                label = stringResource(R.string.ai_key_openrouter),
                value = openRouterKey,
                onValueChange = { onSetAiKey(AppSettings.AI_OPENROUTER, it) },
            )

            // Sauvegarde.
            SectionTitle(stringResource(R.string.settings_backup))
            Text(
                stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_export))
                }
                FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_import))
                }
            }
        }
    }
}

/** Éditeur des 4 couleurs de la palette personnalisée : pastille + saisie hexadécimale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPaletteEditor(
    settings: AppSettings,
    onSetCustomPalette: (doColor: String, schedule: String, delegate: String, eliminate: String) -> Unit,
) {
    var doColor by remember(settings.paletteId) { mutableStateOf(settings.customDoColor) }
    var schedule by remember(settings.paletteId) { mutableStateOf(settings.customScheduleColor) }
    var delegate by remember(settings.paletteId) { mutableStateOf(settings.customDelegateColor) }
    var eliminate by remember(settings.paletteId) { mutableStateOf(settings.customEliminateColor) }

    fun push() = onSetCustomPalette(doColor, schedule, delegate, eliminate)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CustomColorRow(stringResource(R.string.quadrant_do), doColor) { doColor = it; push() }
        CustomColorRow(stringResource(R.string.quadrant_schedule), schedule) { schedule = it; push() }
        CustomColorRow(stringResource(R.string.quadrant_delegate), delegate) { delegate = it; push() }
        CustomColorRow(stringResource(R.string.quadrant_eliminate), eliminate) { eliminate = it; push() }
    }
}

/** Une ligne « verbe du quadrant + pastille + champ #RRGGBB ». Sanitise l'entrée hexadécimale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(22.dp).background(value.toColor(), CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextField(
            value = value,
            onValueChange = { raw ->
                val hex = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
                val normalized = ("#" + hex.removePrefix("#")).take(7)
                onValueChange(normalized)
            },
            singleLine = true,
            shape = KairosFieldShape,
            colors = kairosFieldColors(),
            modifier = Modifier.width(140.dp),
        )
    }
}

/** Champ de saisie d'une clé API : masqué par défaut, avec bascule d'affichage. Sauvegarde à la volée. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiKeyField(label: String, value: String, onValueChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var hidden by remember { mutableStateOf(true) }
    TextField(
        value = text,
        onValueChange = { text = it; onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(
                    if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = stringResource(
                        if (hidden) R.string.cd_key_show else R.string.cd_key_hide,
                    ),
                )
            }
        },
        shape = KairosFieldShape,
        colors = kairosFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Ligne de réglage : icône tonale + titre/sous-titre + interrupteur. */
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Tuile de durée : libellé (2 lignes réservées, pour aligner) + champ numérique tonal. */
@Composable
private fun DurationTile(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = KairosFieldShape,
            colors = kairosFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
