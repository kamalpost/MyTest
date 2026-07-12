package com.kamalpost.taskmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamalpost.taskmanager.data.AppData
import com.kamalpost.taskmanager.data.JsonBackup
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.data.provideTaskStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ToastType { Success, Error, Info, Warning }
data class ToastMsg(
    val text: String,
    val type: ToastType = ToastType.Info,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/** Callbacks into Android-framework land (reminders, auto-backup), wired by MainActivity.
 *  Kept as plain lambdas so the ViewModel stays free of framework imports. */
data class PlatformHooks(
    val onDataChanged: (exportJson: String) -> Unit = {},
    val onTaskScheduleChanged: (Task) -> Unit = {},
    val onAllRescheduled: (List<Task>) -> Unit = {}
)

data class UiState(
    val loaded: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedTaskId: String? = null,
    // Filters (Tasks screen)
    val search: String = "",
    val filterCategory: String = "",
    val filterPriority: String = "",
    val notUpdatedToday: Boolean = false,
    val showCompleted: Boolean = false,
    // Details screen draft — committed on "Update", like the web app
    val draftName: String = "",
    val draftCategory: String = "",
    val draftPriority: String = "Medium",
    val draftNotes: String = "",
    val draftDueAt: String = ""
) {
    val selectedTask: Task? get() = tasks.firstOrNull { it.id == selectedTaskId }

    val filteredTasks: List<Task>
        get() = tasks.filter { t ->
            if (!showCompleted && t.completed) return@filter false
            if (notUpdatedToday && isToday(t.updatedAt)) return@filter false
            if (search.isNotBlank() &&
                !t.name.contains(search, ignoreCase = true) &&
                !t.notes.contains(search, ignoreCase = true)
            ) return@filter false
            if (filterCategory.isNotEmpty() && t.category != filterCategory) return@filter false
            if (filterPriority.isNotEmpty() && t.priority != filterPriority) return@filter false
            true
        }

    val updatedTodayCount: Int get() = tasks.count { isToday(it.updatedAt) }
    val completedCount: Int get() = tasks.count { it.completed }
}

fun isToday(iso: String): Boolean = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
}.getOrDefault(false)

fun isPast(iso: String): Boolean = runCatching {
    Instant.parse(iso).isBefore(Instant.now())
}.getOrDefault(false)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val store = provideTaskStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val timer: StateFlow<TimerState> = TimerEngine.state

    private val _toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 8)
    val toasts: SharedFlow<ToastMsg> = _toasts.asSharedFlow()

    var hooks: PlatformHooks = PlatformHooks()

    private var lastDeleted: Pair<Int, Task>? = null

    init {
        viewModelScope.launch {
            val data = store.load()
            _state.update {
                it.copy(loaded = true, tasks = data.tasks, categories = data.categories)
            }
        }
        viewModelScope.launch {
            TimerEngine.finished.collect {
                toast("⏰ Timer complete!", ToastType.Warning)
            }
        }
    }

    private fun toast(
        text: String,
        type: ToastType = ToastType.Info,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        _toasts.tryEmit(ToastMsg(text, type, actionLabel, action))
    }

    /** Every mutation persists immediately — stronger than the web app's 5-minute autosave. */
    private fun persist() {
        val s = _state.value
        viewModelScope.launch {
            store.save(s.tasks, s.categories)
            hooks.onDataChanged(exportJson())
        }
    }

    // ---------- Tasks ----------

    fun addTask(name: String, category: String, priority: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            toast("Task name required.", ToastType.Error)
            return false
        }
        val now = Instant.now().toString()
        val task = Task(
            id = System.currentTimeMillis().toString(),
            name = trimmed,
            category = category,
            priority = priority,
            createdAt = now,
            updatedAt = now
        )
        _state.update { it.copy(tasks = listOf(task) + it.tasks) }
        selectTask(task.id)
        persist()
        toast("Task added!", ToastType.Success)
        return true
    }

    fun selectTask(id: String?) {
        _state.update { s ->
            val t = s.tasks.firstOrNull { it.id == id }
            if (t == null) s.copy(
                selectedTaskId = null,
                draftName = "", draftCategory = "", draftPriority = "Medium",
                draftNotes = "", draftDueAt = ""
            )
            else s.copy(
                selectedTaskId = id,
                draftName = t.name,
                draftCategory = t.category,
                draftPriority = t.priority,
                draftNotes = t.notes,
                draftDueAt = t.dueAt
            )
        }
    }

    fun setDraft(
        name: String? = null,
        category: String? = null,
        priority: String? = null,
        notes: String? = null,
        dueAt: String? = null
    ) {
        _state.update {
            it.copy(
                draftName = name ?: it.draftName,
                draftCategory = category ?: it.draftCategory,
                draftPriority = priority ?: it.draftPriority,
                draftNotes = notes ?: it.draftNotes,
                draftDueAt = dueAt ?: it.draftDueAt
            )
        }
    }

    fun updateSelectedTask() {
        val s = _state.value
        val id = s.selectedTaskId
        if (id == null || s.selectedTask == null) {
            toast("Select a task first.", ToastType.Error)
            return
        }
        val updated = s.selectedTask!!.copy(
            name = s.draftName.trim().ifEmpty { s.selectedTask!!.name },
            category = s.draftCategory,
            priority = s.draftPriority,
            notes = s.draftNotes,
            dueAt = s.draftDueAt,
            updatedAt = Instant.now().toString()
        )
        _state.update { st ->
            st.copy(tasks = st.tasks.map { if (it.id == id) updated else it })
        }
        hooks.onTaskScheduleChanged(updated)
        persist()
        toast("Task updated & saved!", ToastType.Success)
    }

    fun toggleComplete(id: String) {
        val t = _state.value.tasks.firstOrNull { it.id == id } ?: return
        val nowCompleted = !t.completed
        val updated = t.copy(completed = nowCompleted, updatedAt = Instant.now().toString())
        _state.update { st ->
            st.copy(tasks = st.tasks.map { if (it.id == id) updated else it })
        }
        if (nowCompleted && _state.value.selectedTaskId == id) clearSelection()
        hooks.onTaskScheduleChanged(updated)
        persist()
        toast(if (nowCompleted) "✓ Marked complete!" else "Task reopened.", ToastType.Success)
    }

    fun toggleCompleteSelected() {
        val id = _state.value.selectedTaskId
        if (id == null) {
            toast("Select a task first.", ToastType.Error)
            return
        }
        toggleComplete(id)
    }

    fun deleteTask(id: String) {
        val idx = _state.value.tasks.indexOfFirst { it.id == id }
        if (idx < 0) return
        val task = _state.value.tasks[idx]
        lastDeleted = idx to task
        _state.update { st -> st.copy(tasks = st.tasks.filterNot { it.id == id }) }
        if (_state.value.selectedTaskId == id) clearSelection()
        hooks.onTaskScheduleChanged(task.copy(completed = true)) // cancels any reminder
        persist()
        toast("Task deleted.", ToastType.Info, actionLabel = "UNDO") { undoDelete() }
    }

    fun deleteSelected() {
        val id = _state.value.selectedTaskId
        if (id == null) {
            toast("Select a task first.", ToastType.Error)
            return
        }
        deleteTask(id)
    }

    fun undoDelete() {
        val (idx, task) = lastDeleted ?: return
        lastDeleted = null
        _state.update { st ->
            val list = st.tasks.toMutableList()
            list.add(idx.coerceAtMost(list.size), task)
            st.copy(tasks = list)
        }
        hooks.onTaskScheduleChanged(task)
        persist()
        toast("Task restored.", ToastType.Success)
    }

    fun renameTask(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _state.update { st ->
            st.copy(tasks = st.tasks.map {
                if (it.id == id) it.copy(name = trimmed) else it
            })
        }
        persist()
        toast("Task renamed.", ToastType.Info)
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectedTaskId = null,
                draftName = "", draftCategory = "", draftPriority = "Medium",
                draftNotes = "", draftDueAt = ""
            )
        }
    }

    // ---------- Filters ----------

    fun setSearch(q: String) = _state.update { it.copy(search = q) }
    fun setFilterCategory(c: String) = _state.update { it.copy(filterCategory = c) }
    fun setFilterPriority(p: String) = _state.update { it.copy(filterPriority = p) }
    fun setNotUpdatedToday(v: Boolean) = _state.update { it.copy(notUpdatedToday = v) }
    fun setShowCompleted(v: Boolean) = _state.update { it.copy(showCompleted = v) }

    private fun resetFilters() {
        _state.update {
            it.copy(
                search = "", filterCategory = "", filterPriority = "",
                notUpdatedToday = false, showCompleted = false
            )
        }
    }

    // ---------- Categories ----------

    fun addCategory(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty()) {
            toast("Enter a category name.", ToastType.Error)
            return false
        }
        val formatted =
            if (value.startsWith("#") || value.startsWith("!")) value else "#$value"
        if (_state.value.categories.contains(formatted)) {
            toast("Already exists.", ToastType.Error)
            return false
        }
        _state.update { it.copy(categories = it.categories + formatted) }
        persist()
        toast("$formatted added.", ToastType.Success)
        return true
    }

    fun removeCategory(cat: String) {
        _state.update { it.copy(categories = it.categories.filterNot { c -> c == cat }) }
        persist()
    }

    // ---------- Backup / restore ----------

    fun exportJson(): String =
        JsonBackup.exportJson(_state.value.tasks, _state.value.categories)

    fun importBackup(text: String) {
        val data: AppData? = JsonBackup.parseBackup(text)
        if (data == null) {
            toast("Invalid JSON file.", ToastType.Error)
            return
        }
        _state.update {
            it.copy(tasks = data.tasks, categories = data.categories)
        }
        resetFilters()
        clearSelection()
        hooks.onAllRescheduled(data.tasks)
        persist()
        toast("Imported ${data.tasks.size} tasks.", ToastType.Success)
    }

    fun notifyExported() = toast("Exported!", ToastType.Success)

    fun notifyBackupFolderSet() =
        toast("Auto-backup folder set.", ToastType.Success)

    // ---------- Pomodoro timer (delegates to the process-wide engine) ----------

    fun toggleTimer() = TimerEngine.toggle()

    fun setTimerPreset(minutes: Int) = TimerEngine.setPreset(minutes)
}
