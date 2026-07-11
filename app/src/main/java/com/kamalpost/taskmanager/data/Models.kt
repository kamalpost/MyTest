package com.kamalpost.taskmanager.data

import androidx.compose.ui.graphics.Color
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentPurple
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import kotlinx.serialization.Serializable

/**
 * Field names intentionally match the web app's JSON
 * so backups exported from the HTML version import here unchanged.
 */
@Serializable
data class Task(
    val id: String,
    val name: String,
    val category: String = "",
    val priority: String = "Medium",
    val notes: String = "",
    val completed: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val dueAt: String = ""
)

@Serializable
data class AppData(
    val exportedAt: String = "",
    val categories: List<String> = emptyList(),
    val tasks: List<Task> = emptyList()
)

enum class Priority(val label: String, val color: Color) {
    Low("Low", AccentGreen),
    Medium("Medium", AccentYellow),
    High("High", AccentRed),
    Critical("Critical", AccentPurple);

    companion object {
        fun from(label: String): Priority =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: Medium
    }
}
