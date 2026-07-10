package com.kamalpost.taskmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.ui.AppDropdown
import com.kamalpost.taskmanager.ui.SectionLabel
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentPurple
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted

private val PRIORITIES = listOf("Low", "Medium", "High", "Critical")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(
    state: UiState,
    vm: TaskViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var newCategory by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (vm.addTask(name, category, priority)) name = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel("Task Name")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("e.g. Prepare weekly report", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2
            ),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Category")
        AppDropdown(
            value = category,
            placeholder = "— Select Category —",
            options = state.categories,
            onSelect = { category = it },
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Add New Category")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                placeholder = { Text("e.g. #MYTEAM or !backlog", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(
                onClick = { if (vm.addCategory(newCategory)) newCategory = "" },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
            ) {
                Text("+ Add", fontWeight = FontWeight.Bold)
            }
        }

        SectionLabel("Priority")
        AppDropdown(
            value = priority,
            placeholder = "Medium",
            options = PRIORITIES,
            onSelect = { priority = it.ifEmpty { "Medium" } },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { submit() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .height(48.dp)
        ) {
            Text("＋ Add Task", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 18.dp))

        SectionLabel("Categories")
        if (state.categories.isEmpty()) {
            Text(
                "No categories yet.",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.categories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, Border, RoundedCornerShape(6.dp))
                            .background(Surface2, RoundedCornerShape(6.dp))
                            .padding(start = 10.dp, end = 2.dp)
                    ) {
                        Text(
                            cat,
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        TextButton(
                            onClick = { vm.removeCategory(cat) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)
                        ) {
                            Text("×", color = TextMuted, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 18.dp))

        SectionLabel("Backup & Restore")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExport,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("⬇ Export JSON", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onImport,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellow.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("⬆ Upload Backup", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
