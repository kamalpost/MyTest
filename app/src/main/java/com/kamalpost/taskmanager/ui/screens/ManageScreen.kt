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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TaskViewModel
import com.kamalpost.taskmanager.UiState
import com.kamalpost.taskmanager.ui.SectionLabel
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentPurple
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted

/** Categories, backup & restore, auto-backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageScreen(
    state: UiState,
    vm: TaskViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    autoBackupOn: Boolean,
    onChooseBackupFolder: () -> Unit,
    onDisableAutoBackup: () -> Unit
) {
    var newCategory by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionLabel("Categories")
        if (state.categories.isEmpty()) {
            Text("No categories yet.", color = TextMuted, fontSize = 12.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.categories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                            .background(Surface2, RoundedCornerShape(8.dp))
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            cat,
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = { vm.removeCategory(cat) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $cat",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                placeholder = { Text("e.g. #MYTEAM or !backlog", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (vm.addCategory(newCategory)) newCategory = ""
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { if (vm.addCategory(newCategory)) newCategory = "" },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Names get a # prefix automatically; start with ! for special lists.",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        SectionLabel("Backup & Restore")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExport,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onImport,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellow.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "The JSON format matches the original web app — backups are interchangeable.",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        SectionLabel("Auto-Backup")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .background(Surface2, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (autoBackupOn) "Auto-backup is on" else "Auto-backup is off",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Writes a backup JSON to your chosen folder on every change. " +
                        "Pick a Drive/Dropbox-synced folder for cross-device backup.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = autoBackupOn,
                onCheckedChange = { checked ->
                    if (checked) onChooseBackupFolder() else onDisableAutoBackup()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
            )
        }
        if (autoBackupOn) {
            OutlinedButton(
                onClick = onChooseBackupFolder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change folder")
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
