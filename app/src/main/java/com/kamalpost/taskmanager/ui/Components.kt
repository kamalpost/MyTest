package com.kamalpost.taskmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.data.Priority
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Uppercase muted section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

/** Small monospace tag chip (category / priority / due). */
@Composable
fun Tag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    value: String,
    placeholder: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.ifEmpty { placeholder },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                unfocusedTextColor = if (value.isEmpty()) TextMuted else Color.Unspecified
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(placeholder, color = TextMuted) },
                onClick = { onSelect(""); expanded = false }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

/** Segmented priority picker — one colored chip per level. */
@Composable
fun PrioritySelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Priority.entries.forEach { p ->
            val active = p.label == selected
            FilterChip(
                selected = active,
                onClick = { onSelect(p.label) },
                label = {
                    Text(p.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = p.color.copy(alpha = 0.18f),
                    selectedLabelColor = p.color,
                    labelColor = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = active,
                    borderColor = Border,
                    selectedBorderColor = p.color.copy(alpha = 0.6f),
                    selectedBorderWidth = 1.dp
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Date-then-time picker flow. Renders nothing until shown; calls [onSet]
 * with an ISO instant string when both steps are confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerFlow(
    currentIso: String,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    if (step == 1) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                Instant.parse(currentIso).toEpochMilli()
            }.getOrNull() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val millis = dpState.selectedDateMillis
                    if (millis == null) onDismiss()
                    else {
                        pickedDateMillis = millis
                        step = 2
                    }
                }) { Text("Next", color = AccentBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    if (step == 2) {
        val existing = runCatching {
            Instant.parse(currentIso).atZone(ZoneId.systemDefault())
        }.getOrNull()
        val tpState = rememberTimePickerState(
            initialHour = existing?.hour ?: 9,
            initialMinute = existing?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface2,
            title = { Text("Due time", fontSize = 17.sp) },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickedDateMillis ?: return@TextButton onDismiss()
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onSet(
                        date.atTime(tpState.hour, tpState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toString()
                    )
                }) { Text("Set", color = AccentBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

/** "M/d HH:mm" — same short format as the web task list. */
fun fmtDate(iso: String): String = runCatching {
    val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
    dt.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}.getOrDefault("—")

/** Full local date-time for the details screen. */
fun fmtDateFull(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
}.getOrDefault("—")

/** URL extraction, same regex intent as the web app's renderLinks. */
fun extractLinks(text: String): List<String> =
    Regex("""https?://\S+""").findAll(text).map { it.value }.distinct().toList()
