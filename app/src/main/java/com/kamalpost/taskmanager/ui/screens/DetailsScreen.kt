package com.kamalpost.taskmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.ui.AppDropdown
import com.kamalpost.taskmanager.ui.SectionLabel
import com.kamalpost.taskmanager.ui.extractLinks
import com.kamalpost.taskmanager.ui.fmtDateFull
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted

private val PRIORITIES = listOf("Low", "Medium", "High", "Critical")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: UiState,
    vm: TaskViewModel
) {
    val task = state.selectedTask
    val uriHandler = LocalUriHandler.current

    if (task == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp)
        ) {
            Text("🔍", fontSize = 36.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "No task selected.\nTap a task in the Tasks tab.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            task.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentBlue,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        SectionLabel("Category")
        AppDropdown(
            value = state.draftCategory,
            placeholder = "— None —",
            options = state.categories,
            onSelect = { vm.setDraft(category = it) },
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Priority")
        AppDropdown(
            value = state.draftPriority,
            placeholder = "Medium",
            options = PRIORITIES,
            onSelect = { vm.setDraft(priority = it.ifEmpty { "Medium" }) },
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Notes / Details")
        OutlinedTextField(
            value = state.draftNotes,
            onValueChange = { vm.setDraft(notes = it) },
            placeholder = {
                Text("Enter notes, links, details for the selected task…", color = TextMuted, fontSize = 13.sp)
            },
            minLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2
            ),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Due Date & Reminder")
        var showDatePicker by remember { mutableStateOf(false) }
        var showTimePicker by remember { mutableStateOf(false) }
        var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (state.draftDueAt.isEmpty()) TextMuted else AccentBlue
                ),
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (state.draftDueAt.isEmpty()) "⏰ Set due date"
                    else "⏰ ${fmtDateFull(state.draftDueAt)}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.draftDueAt.isNotEmpty()) {
                OutlinedButton(
                    onClick = { vm.setDraft(dueAt = "") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                    border = BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(7.dp)
                ) { Text("✕", fontSize = 12.sp) }
            }
        }
        Text(
            "A notification fires at the due time. Remember to press Update to save.",
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (showDatePicker) {
            val dpState = rememberDatePickerState(
                initialSelectedDateMillis = runCatching {
                    java.time.Instant.parse(state.draftDueAt).toEpochMilli()
                }.getOrNull() ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickedDateMillis = dpState.selectedDateMillis
                        showDatePicker = false
                        if (pickedDateMillis != null) showTimePicker = true
                    }) { Text("Next", color = AccentBlue, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            ) {
                DatePicker(state = dpState)
            }
        }

        if (showTimePicker) {
            val existing = runCatching {
                java.time.Instant.parse(state.draftDueAt)
                    .atZone(java.time.ZoneId.systemDefault())
            }.getOrNull()
            val tpState = rememberTimePickerState(
                initialHour = existing?.hour ?: 9,
                initialMinute = existing?.minute ?: 0,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = Surface2,
                title = { Text("Due time", fontSize = 17.sp) },
                text = { TimePicker(state = tpState) },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = pickedDateMillis
                        if (millis != null) {
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            val due = date.atTime(tpState.hour, tpState.minute)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toString()
                            vm.setDraft(dueAt = due)
                        }
                        showTimePicker = false
                    }) { Text("Set", color = AccentBlue, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }

        SectionLabel("Links in Details")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(7.dp))
                .background(Surface2, RoundedCornerShape(7.dp))
                .padding(11.dp)
        ) {
            val links = extractLinks(state.draftNotes)
            if (links.isEmpty()) {
                Text(
                    "No links detected.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            } else {
                links.forEach { url ->
                    Text(
                        url,
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { uriHandler.openUri(url) } }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        SectionLabel("Timestamps")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(7.dp))
                .background(Surface2, RoundedCornerShape(7.dp))
                .padding(11.dp)
        ) {
            Text(
                "Created  ${fmtDateFull(task.createdAt)}",
                color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                "Updated  ${fmtDateFull(task.updatedAt)}",
                color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Button(
                onClick = { vm.updateSelectedTask() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) { Text("💾 Update", fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick = { vm.clearSelection() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) { Text("✕ Clear", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(24.dp))
    }
}
