package com.kairos.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.data.model.AppSettings
import com.kairos.app.ui.components.KairosFilterChip
import com.kairos.app.ui.components.KairosFieldShape
import com.kairos.app.ui.components.kairosFieldColors
import com.kairos.app.ui.components.toColor
import com.kairos.app.ui.theme.AppPalettes
import com.kairos.app.ui.theme.dynamicColorSupported
import kotlinx.coroutines.launch

/**
 * Écran Réglages : sections regroupées en **cartes** Material You (Apparence / Tâches & priorités /
 * Pomodoro / Assistant IA / Aide / Sauvegarde), chaque ligne avec une icône de tête tonale. Mise en
 * page reprise de Framely : en-têtes de groupe teintés marque + `SettingCard`/`SettingRow`/
 * `SettingBlock`, contenu borné et centré sur grand écran.
 */
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
    onTestAi: suspend (provider: Int) -> Result<Unit>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onSetQuadrantSort: (Int) -> Unit = {},
    onSetFocusDueWeight: (Int) -> Unit = {},
    onSetCalendarEnabled: (Boolean) -> Unit = {},
    onSetIcalUrl: (String) -> Unit = {},
    onTestIcal: suspend (String) -> Result<Int> = { Result.failure(IllegalStateException()) },
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
                // Le contenu se décale au-dessus du clavier (champs hex / clé API).
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Contenu borné + centré : sur grand écran, les lignes ne s'étirent pas démesurément.
          Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // --- Apparence ---------------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_group_appearance))
            SettingCard {
                SettingBlock(Icons.Rounded.Brightness6, stringResource(R.string.settings_theme)) {
                    ThemeButtonGroup(current = settings.themeMode, onSelect = onSetThemeMode)
                }
                if (dynamicColorSupported) {
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.settings_dynamic),
                        subtitle = stringResource(R.string.settings_dynamic_desc),
                        trailing = {
                            Switch(checked = settings.dynamicColor, onCheckedChange = onSetDynamicColor)
                        },
                    )
                }
                SettingDivider()
                SettingBlock(Icons.Rounded.Palette, stringResource(R.string.settings_palette)) {
                    PaletteList(settings = settings, onSetPalette = onSetPalette)
                    if (settings.paletteId >= AppPalettes.size) {
                        Spacer(Modifier.height(12.dp))
                        CustomPaletteEditor(settings, onSetCustomPalette)
                    }
                }
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(R.string.settings_swimlanes),
                    subtitle = stringResource(R.string.settings_swimlanes_desc),
                    trailing = {
                        Switch(checked = settings.swimLanes, onCheckedChange = onSetSwimLanes)
                    },
                )
                SettingDivider()
                SettingBlock(Icons.Rounded.Translate, stringResource(R.string.settings_language)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                }
            }

            // --- Tâches & priorités ------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_group_behavior))
            SettingCard {
                SettingBlock(Icons.Rounded.PriorityHigh, stringResource(R.string.settings_urgency)) {
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
                }
                SettingDivider()
                SettingBlock(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.settings_sort)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            AppSettings.SORT_MANUAL to R.string.sort_manual,
                            AppSettings.SORT_DUE to R.string.sort_due,
                            AppSettings.SORT_PRIORITY to R.string.sort_priority,
                            AppSettings.SORT_CREATED to R.string.sort_created,
                            AppSettings.SORT_ALPHA to R.string.sort_alpha,
                        ).forEach { (value, res) ->
                            KairosFilterChip(
                                selected = settings.quadrantSort == value,
                                onClick = { onSetQuadrantSort(value) },
                                label = stringResource(res),
                            )
                        }
                    }
                }
                SettingDivider()
                SettingBlock(Icons.Rounded.CenterFocusStrong, stringResource(R.string.settings_focus_weight)) {
                    Text(
                        stringResource(R.string.settings_focus_weight_value, settings.focusDueWeight),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = settings.focusDueWeight.toFloat(),
                        onValueChange = { onSetFocusDueWeight(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.settings_focus_weight_importance),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.settings_focus_weight_due),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_focus_weight_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.reminders_enable),
                    subtitle = stringResource(R.string.reminders_enable_desc),
                    trailing = {
                        Switch(checked = settings.remindersEnabled, onCheckedChange = onSetRemindersEnabled)
                    },
                )
                if (settings.remindersEnabled) {
                    SettingDivider()
                    SettingBlock(Icons.Rounded.Schedule, stringResource(R.string.reminders_lead)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                }
                SettingDivider()
                SettingBlock(Icons.Rounded.DeleteSweep, stringResource(R.string.settings_archive_purge)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_archive_purge_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Agenda (time-blocking) --------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_group_calendar))
            SettingCard {
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> onSetCalendarEnabled(granted) }
                SettingRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.settings_calendar_device),
                    subtitle = stringResource(R.string.settings_calendar_device_desc),
                    trailing = {
                        Switch(
                            checked = settings.calendarEnabled,
                            onCheckedChange = { enable ->
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CALENDAR,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (enable && !granted) {
                                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                } else {
                                    onSetCalendarEnabled(enable)
                                }
                            },
                        )
                    },
                )
                SettingDivider()
                SettingBlock(Icons.Rounded.Link, stringResource(R.string.settings_ical)) {
                    IcalField(url = settings.icalUrl, onSave = onSetIcalUrl, onTest = onTestIcal)
                }
            }

            // --- Pomodoro ----------------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_group_pomodoro))
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.WaterDrop,
                    title = stringResource(R.string.settings_pomodoro_style),
                    subtitle = stringResource(R.string.settings_pomodoro_style_sub),
                    trailing = {
                        Switch(checked = settings.pomodoroLiquid, onCheckedChange = onSetPomodoroStyle)
                    },
                )
                SettingDivider()
                SettingBlock(Icons.Rounded.Timer, stringResource(R.string.pomo_durations)) {
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
                    Spacer(Modifier.height(12.dp))
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
                }
            }

            // --- Assistant IA ------------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_ai))
            SettingCard {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_ai_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    Spacer(Modifier.height(12.dp))
                    AiKeyField(
                        label = stringResource(R.string.ai_key_gemini),
                        value = geminiKey,
                        provider = AppSettings.AI_GEMINI,
                        onValueChange = { onSetAiKey(AppSettings.AI_GEMINI, it) },
                        onTest = onTestAi,
                    )
                    Spacer(Modifier.height(12.dp))
                    AiKeyField(
                        label = stringResource(R.string.ai_key_openrouter),
                        value = openRouterKey,
                        provider = AppSettings.AI_OPENROUTER,
                        onValueChange = { onSetAiKey(AppSettings.AI_OPENROUTER, it) },
                        onTest = onTestAi,
                    )
                }
            }

            // --- Aide --------------------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_group_help))
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.MenuBook,
                    title = stringResource(R.string.guide_open),
                    onClick = onOpenGuide,
                    trailing = { ChevronIcon() },
                )
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.HelpOutline,
                    title = stringResource(R.string.settings_show_guide),
                    subtitle = stringResource(R.string.settings_show_guide_desc),
                    trailing = {
                        Switch(checked = showGuide, onCheckedChange = onSetShowGuide)
                    },
                )
            }

            // --- Sauvegarde --------------------------------------------------------------------
            GroupHeader(stringResource(R.string.settings_backup))
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.action_export),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    onClick = onExport,
                    trailing = { ChevronIcon() },
                )
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.FileUpload,
                    title = stringResource(R.string.action_import),
                    onClick = onImport,
                    trailing = { ChevronIcon() },
                )
            }

            Spacer(Modifier.height(8.dp))
          }
        }
    }
}

/** Sélecteur de thème en **ButtonGroup connecté** M3 Expressive (Système / Clair / Sombre). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeButtonGroup(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        AppSettings.THEME_SYSTEM to R.string.theme_system,
        AppSettings.THEME_LIGHT to R.string.theme_light,
        AppSettings.THEME_DARK to R.string.theme_dark,
    )
    val colors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, pair ->
            val (value, res) = pair
            ToggleButton(
                checked = current == value,
                onCheckedChange = { if (it) onSelect(value) },
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(res), maxLines = 1)
            }
        }
    }
}

/** Liste radio verticale des palettes de quadrants + entrée « Personnalisée », avec aperçu des 4 accents. */
@Composable
private fun PaletteList(settings: AppSettings, onSetPalette: (Int) -> Unit) {
    val customIndex = AppPalettes.size
    Column {
        AppPalettes.forEachIndexed { index, palette ->
            PaletteRow(
                name = palette.name,
                selected = settings.paletteId == index,
                swatches = listOf(palette.doColor, palette.scheduleColor, palette.delegateColor, palette.eliminateColor),
                onClick = { onSetPalette(index) },
            )
        }
        PaletteRow(
            name = stringResource(R.string.palette_custom),
            selected = settings.paletteId >= customIndex,
            swatches = listOf(
                settings.customDoColor.toColor(), settings.customScheduleColor.toColor(),
                settings.customDelegateColor.toColor(), settings.customEliminateColor.toColor(),
            ),
            onClick = { onSetPalette(customIndex) },
        )
    }
}

/** Une ligne de palette : radio + nom + 4 pastilles d'accent. */
@Composable
private fun PaletteRow(
    name: String,
    selected: Boolean,
    swatches: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.forEach { c ->
                Box(modifier = Modifier.size(16.dp).background(c, CircleShape))
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

/**
 * Champ de saisie d'une clé API : masqué par défaut, avec bascule d'affichage. Sauvegarde à la volée.
 * Un bouton **« Tester »** vérifie la connexion du fournisseur (clé valide + réseau) par un appel minimal ;
 * le résultat (succès / message d'erreur) s'affiche sous le champ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiKeyField(
    label: String,
    value: String,
    provider: Int,
    onValueChange: (String) -> Unit,
    onTest: suspend (provider: Int) -> Result<Unit>,
) {
    var text by remember { mutableStateOf(value) }
    var hidden by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    // Résultat du dernier test (null = aucun test depuis la dernière modification de la clé).
    var result by remember { mutableStateOf<Result<Unit>?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = text,
            // Modifier la clé invalide le dernier résultat de test.
            onValueChange = { text = it; onValueChange(it); result = null },
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = {
                    result = null
                    testing = true
                    scope.launch {
                        val r = onTest(provider)
                        result = r
                        testing = false
                    }
                },
                enabled = !testing,
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_test_running))
                } else {
                    Icon(Icons.Rounded.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_test))
                }
            }
            val r = result
            if (r != null && !testing) {
                if (r.isSuccess) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.ai_test_ok),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // Message d'erreur sur toute la largeur (peut être long : « Gemini : clé API invalide… »).
        val r = result
        if (r != null && !testing && r.isFailure) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = r.exceptionOrNull()?.message ?: stringResource(R.string.ai_test_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Tuile de durée : libellé (2 lignes réservées, pour aligner) + champ numérique tonal. */
@OptIn(ExperimentalMaterial3Api::class)
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

// --- Briques Material You (cartes groupées) --------------------------------------------------------

/**
 * Saisie du flux iCal : champ + « Tester » (télécharge et compte les événements) ; l'URL est
 * enregistrée quand le test réussit, ou effacée si le champ est vidé.
 */
@Composable
private fun IcalField(url: String, onSave: (String) -> Unit, onTest: suspend (String) -> Result<Int>) {
    var value by remember(url) { mutableStateOf(url) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<Int>?>(null) }
    val scope = rememberCoroutineScope()
    Text(
        stringResource(R.string.settings_ical_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    TextField(
        value = value,
        onValueChange = { value = it; result = null },
        placeholder = { Text("https://…/basic.ics") },
        singleLine = true,
        shape = KairosFieldShape,
        colors = kairosFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            enabled = !testing,
            onClick = {
                if (value.isBlank()) {
                    onSave("")
                    result = null
                } else {
                    scope.launch {
                        testing = true
                        val r = onTest(value.trim())
                        if (r.isSuccess) onSave(value.trim())
                        result = r
                        testing = false
                    }
                }
            },
        ) { Text(stringResource(if (value.isBlank()) R.string.action_save else R.string.settings_ical_test)) }
        if (testing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        result?.let { r ->
            Text(
                r.fold(
                    onSuccess = { stringResource(R.string.settings_ical_ok, it) },
                    onFailure = { stringResource(R.string.settings_ical_error, it.message ?: "") },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Carte arrondie regroupant plusieurs lignes de réglage. */
@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** En-tête d'un groupe de cartes (teinté marque). */
@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Icône de tête d'une ligne, dans une pastille tonale. */
@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Ligne de réglage : icône + titre (+ sous-titre) + contrôle à droite. Cliquable si [onClick] non nul. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Bloc de réglage : icône + titre en tête, puis un contrôle pleine largeur en dessous. */
@Composable
private fun SettingBlock(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingIcon(icon)
            Spacer(Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Séparateur fin entre deux lignes d'une carte, aligné sous le texte (pas sous l'icône). */
@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** Chevron « ouvrir » pour une ligne de navigation interne. */
@Composable
private fun ChevronIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}
