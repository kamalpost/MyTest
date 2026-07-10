package com.kamalpost.taskmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.data.Priority
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.ui.AppDropdown
import com.kamalpost.taskmanager.ui.Tag
import com.kamalpost.taskmanager.ui.fmtDate
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted
import com.kamalpost.taskmanager.ui.theme.TextPrimary

private val PRIORITIES = listOf("Low", "Medium", "High", "Critical")

@Composable
fun TasksScreen(
    state: UiState,
    vm: TaskViewModel,
    onOpenDetails: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<Task?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // Search row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Search tasks…", color = TextMuted, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.setSearch("") }) {
                Text("✕", color = TextMuted, fontSize = 16.sp)
            }
        }

        // Filter dropdowns
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            AppDropdown(
                value = state.filterCategory,
                placeholder = "All Categories",
                options = state.categories,
                onSelect = vm::setFilterCategory,
                modifier = Modifier.weight(1f)
            )
            AppDropdown(
                value = state.filterPriority,
                placeholder = "All Priorities",
                options = PRIORITIES,
                onSelect = vm::setFilterPriority,
                modifier = Modifier.weight(1f)
            )
        }

        // Toggle chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            FilterChip(
                selected = state.notUpdatedToday,
                onClick = { vm.setNotUpdatedToday(!state.notUpdatedToday) },
                label = { Text("Not updated today", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                    selectedLabelColor = AccentBlue
                )
            )
            FilterChip(
                selected = state.showCompleted,
                onClick = { vm.setShowCompleted(!state.showCompleted) },
                label = { Text("Show completed", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                    selectedLabelColor = AccentBlue
                )
            )
        }

        // Task list
        val filtered = state.filteredTasks
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (state.tasks.isEmpty()) "📭" else "🔍", fontSize = 36.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (state.tasks.isEmpty()) "No tasks yet.\nAdd one from the Add tab."
                    else "No tasks match your filters.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        selected = task.id == state.selectedTaskId,
                        onClick = {
                            vm.selectTask(task.id)
                            onOpenDetails()
                        },
                        onLongClick = { renameTarget = task }
                    )
                }
            }
        }

        // Bottom action bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedButton(
                onClick = { vm.toggleCompleteSelected() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) { Text("✓ Complete", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

            OutlinedButton(
                onClick = {
                    if (state.selectedTaskId != null) confirmDelete = true
                    else vm.deleteSelected() // triggers the "select first" toast
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) { Text("✕ Delete", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

            Text(
                text = if (filtered.isNotEmpty())
                    "${filtered.size} shown · ${state.completedCount} done" else "",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // Rename dialog (long-press replaces the web app's double-click rename)
    renameTarget?.let { task ->
        var newName by remember(task.id) { mutableStateOf(task.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Surface2,
            title = { Text("Rename task", color = TextPrimary, fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameTask(task.id, newName)
                    renameTarget = null
                }) { Text("Save", color = AccentBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Delete confirmation, like the web app's confirm()
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface2,
            title = { Text("Delete this task?", color = TextPrimary, fontSize = 17.sp) },
            text = { Text("This cannot be undone.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    confirmDelete = false
                }) { Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskItem(
    task: Task,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val priority = Priority.from(task.priority)
    val borderColor = if (selected) AccentBlue else Border
    val bg = if (selected) AccentBlue.copy(alpha = 0.06f) else Surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (task.completed) 0.5f else 1f)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Priority color bar, like the web app's ::before stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(priority.color)
        )
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                text = task.name,
                color = if (task.completed) TextMuted else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                if (task.category.isNotEmpty()) Tag(task.category, AccentBlue)
                Tag(priority.label, priority.color)
                Spacer(Modifier.weight(1f))
                Text(
                    text = fmtDate(task.updatedAt),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
