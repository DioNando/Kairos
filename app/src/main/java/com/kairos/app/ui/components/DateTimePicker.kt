package com.kairos.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.kairos.app.R
import com.kairos.app.ui.theme.LocalizedOverlay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sélection d'une date **puis** d'une heure (deux dialogs enchaînés). [initial] préremplit les deux
 * étapes (sinon aujourd'hui à 9:00) ; [onPicked] reçoit l'instant choisi (epoch millis).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialogs(
    initial: Long?,
    now: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    var pendingDate by remember { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()
    val okLabel = stringResource(R.string.action_ok)
    val nextLabel = stringResource(R.string.action_next)
    val cancelLabel = stringResource(R.string.action_cancel)

    val date = pendingDate
    if (date == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initial ?: now)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pendingDate = dateState.selectedDateMillis ?: now }) { Text(nextLabel) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
        ) {
            // Les libellés internes du DatePicker (mois, jours) suivent aussi la langue in-app.
            LocalizedOverlay { DatePicker(state = dateState) }
        }
    } else {
        val existing = initial?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        val timeState = rememberTimePickerState(
            initialHour = existing?.hour ?: 9,
            initialMinute = existing?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    // Le DatePicker renvoie minuit **UTC** du jour choisi : on relit le jour en UTC.
                    val day = Instant.ofEpochMilli(date).atZone(ZoneId.of("UTC")).toLocalDate()
                    val picked = LocalDateTime.of(day, LocalTime.of(timeState.hour, timeState.minute))
                    onPicked(picked.atZone(zone).toInstant().toEpochMilli())
                }) { Text(okLabel) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
            text = { LocalizedOverlay { TimePicker(state = timeState) } },
        )
    }
}
