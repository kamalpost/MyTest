package com.kamalpost.taskmanager.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.ui.AppDropdown
import com.kamalpost.taskmanager.ui.DueDatePickerFlow
import com.kamalpost.taskmanager.ui.PrioritySelector
import com.kamalpost.taskmanager.ui.SectionLabel
import com.kamalpost.taskmanager.ui.extractLinks
import com.kamalpost.taskmanager.ui.fmtDateFull
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.Bg
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted
import com.kamalpost.taskmanager.ui.theme.TextPrimary

/** Full-screen task editor, pushed over the home screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: UiState,
    vm: TaskViewModel,
    onBack: () -> Unit
) {
    val task = state.selectedTask
    if (task == null) {
        // Task was deleted or restored elsewhere — nothing to edit.
        onBack()
        return
    }
    val uriHandler = LocalUriHandler.current
    var showDuePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Text(
                        "Task details",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = { vm.toggleCompleteSelected() }) {
                        Icon(
                            if (task.completed) Icons.Filled.Replay else Icons.Filled.Check,
                            contentDescription = if (task.completed) "Reopen" else "Complete",
                            tint = AccentGreen
                        )
                    }
                    IconButton(onClick = { vm.deleteSelected(); onBack() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentRed)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionLabel("Name")
            OutlinedTextField(
                value = state.draftName,
                onValueChange = { vm.setDraft(name = it) },
                singleLine = false,
                maxLines = 2,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Category")
            AppDropdown(
                value = state.draftCategory,
                placeholder = "No category",
                options = state.categories,
                onSelect = { vm.setDraft(category = it) }
            )

            SectionLabel("Priority")
            PrioritySelector(
                selected = state.draftPriority,
                onSelect = { vm.setDraft(priority = it) }
            )

            SectionLabel("Due date & reminder")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showDuePicker = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (state.draftDueAt.isEmpty()) TextMuted else AccentBlue
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.draftDueAt.isEmpty()) "Set due date"
                        else fmtDateFull(state.draftDueAt),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.draftDueAt.isNotEmpty()) {
                    IconButton(onClick = { vm.setDraft(dueAt = "") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear due date", tint = TextMuted)
                    }
                }
            }
            Text(
                "A notification fires at the due time once you save.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            SectionLabel("Notes")
            OutlinedTextField(
                value = state.draftNotes,
                onValueChange = { vm.setDraft(notes = it) },
                placeholder = {
                    Text("Notes, links, details…", color = TextMuted, fontSize = 13.sp)
                },
                minLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val links = extractLinks(state.draftNotes)
            if (links.isNotEmpty()) {
                SectionLabel("Links")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .background(Surface2, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    links.forEach { url ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { runCatching { uriHandler.openUri(url) } }
                                .padding(vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.width(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                url,
                                color = AccentBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            SectionLabel("History")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .background(Surface2, RoundedCornerShape(12.dp))
                    .padding(12.dp)
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
                if (task.completed) {
                    Text(
                        "Status   completed",
                        color = AccentGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = { vm.updateSelectedTask() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 28.dp)
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    if (showDuePicker) {
        DueDatePickerFlow(
            currentIso = state.draftDueAt,
            onSet = {
                vm.setDraft(dueAt = it)
                showDuePicker = false
            },
            onDismiss = { showDuePicker = false }
        )
    }
}
