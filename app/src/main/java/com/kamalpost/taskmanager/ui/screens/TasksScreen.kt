package com.kamalpost.taskmanager.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.data.Priority
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.isPast
import com.kamalpost.taskmanager.ui.AppDropdown
import com.kamalpost.taskmanager.ui.PrioritySelector
import com.kamalpost.taskmanager.ui.SectionLabel
import com.kamalpost.taskmanager.ui.Tag
import com.kamalpost.taskmanager.ui.fmtDate
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import com.kamalpost.taskmanager.ui.theme.Bg
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted
import com.kamalpost.taskmanager.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: UiState,
    vm: TaskViewModel,
    onOpenDetails: () -> Unit
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Search
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Search tasks and notes", color = TextMuted, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted)
                },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // Filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                MenuFilterChip(
                    label = state.filterCategory.ifEmpty { "Category" },
                    active = state.filterCategory.isNotEmpty(),
                    options = state.categories,
                    allLabel = "All categories",
                    onSelect = vm::setFilterCategory
                )
                MenuFilterChip(
                    label = state.filterPriority.ifEmpty { "Priority" },
                    active = state.filterPriority.isNotEmpty(),
                    options = Priority.entries.map { it.label },
                    allLabel = "All priorities",
                    onSelect = vm::setFilterPriority
                )
                FilterChip(
                    selected = state.notUpdatedToday,
                    onClick = { vm.setNotUpdatedToday(!state.notUpdatedToday) },
                    label = { Text("Not updated today", fontSize = 12.sp) },
                    colors = filterChipColors()
                )
                FilterChip(
                    selected = state.showCompleted,
                    onClick = { vm.setShowCompleted(!state.showCompleted) },
                    label = { Text("Completed", fontSize = 12.sp) },
                    colors = filterChipColors()
                )
            }

            // Counts caption
            val filtered = state.filteredTasks
            Text(
                text = if (state.tasks.isEmpty()) ""
                else "${filtered.size} shown · ${state.updatedTodayCount}/${state.tasks.size} updated today · ${state.completedCount} done",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )

            if (filtered.isEmpty()) {
                EmptyState(anyTasks = state.tasks.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { task ->
                        Box(modifier = Modifier.animateItem()) {
                            SwipeableTaskItem(
                                task = task,
                                selected = task.id == state.selectedTaskId,
                                onClick = {
                                    vm.selectTask(task.id)
                                    onOpenDetails()
                                },
                                onSwipeComplete = { vm.toggleComplete(task.id) },
                                onSwipeDelete = { vm.deleteTask(task.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            containerColor = AccentBlue,
            contentColor = TextPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New task")
        }
    }

    if (showAddSheet) {
        NewTaskSheet(
            categories = state.categories,
            onAdd = { name, category, priority ->
                if (vm.addTask(name, category, priority)) showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
    selectedLabelColor = AccentBlue,
    labelColor = TextMuted
)

/** Filter chip that opens a dropdown of options. */
@Composable
private fun MenuFilterChip(
    label: String,
    active: Boolean,
    options: List<String>,
    allLabel: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = {
                if (active) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear filter",
                        modifier = Modifier
                            .size(14.dp)
                    )
                }
            },
            colors = filterChipColors()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel, color = TextMuted) },
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

@Composable
private fun EmptyState(anyTasks: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (anyTasks) Icons.Filled.SearchOff else Icons.Filled.Inbox,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (anyTasks) "No tasks match your filters."
            else "No tasks yet.\nTap + to add your first task.",
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskSheet(
    categories: List<String>,
    onAdd: (name: String, category: String, priority: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("Medium") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                "New task",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            SectionLabel("Task name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Prepare weekly report", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd(name, category, priority) }),
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
                value = category,
                placeholder = "No category",
                options = categories,
                onSelect = { category = it }
            )

            SectionLabel("Priority")
            PrioritySelector(selected = priority, onSelect = { priority = it })

            Button(
                onClick = { onAdd(name, category, priority) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add task", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SwipeableTaskItem(
    task: Task,
    selected: Boolean,
    onClick: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeComplete()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false // list updates handle the visual change; row snaps back
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val target = dismissState.dismissDirection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when (target) {
                            SwipeToDismissBoxValue.StartToEnd -> AccentGreen.copy(alpha = 0.16f)
                            SwipeToDismissBoxValue.EndToStart -> AccentRed.copy(alpha = 0.16f)
                            else -> Bg
                        }
                    )
                    .padding(horizontal = 24.dp)
            ) {
                if (target == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        if (task.completed) Icons.Filled.Replay else Icons.Filled.Check,
                        contentDescription = null,
                        tint = AccentGreen
                    )
                }
                Spacer(Modifier.weight(1f))
                if (target == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = AccentRed)
                }
            }
        }
    ) {
        TaskCard(task = task, selected = selected, onClick = onClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: Task,
    selected: Boolean,
    onClick: () -> Unit
) {
    val priority = Priority.from(task.priority)
    val borderColor = if (selected) AccentBlue.copy(alpha = 0.6f) else Border

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (task.completed) 0.55f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(priority.color)
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = task.name,
                color = if (task.completed) TextMuted else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (task.category.isNotEmpty()) Tag(task.category, AccentBlue)
                Tag(priority.label, priority.color)
                if (task.dueAt.isNotEmpty()) {
                    val overdue = !task.completed && isPast(task.dueAt)
                    Tag(
                        "due ${fmtDate(task.dueAt)}",
                        if (overdue) AccentRed else AccentYellow
                    )
                }
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
