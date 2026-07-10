package com.kamalpost.taskmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamalpost.taskmanager.data.AppData
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.data.TaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
data class ToastMsg(val text: String, val type: ToastType = ToastType.Info)

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
    val draftCategory: String = "",
    val draftPriority: String = "Medium",
    val draftNotes: String = ""
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

data class TimerState(
    val secondsLeft: Int = 25 * 60,
    val running: Boolean = false,
    val presetMinutes: Int = 25
)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 8)
    val toasts: SharedFlow<ToastMsg> = _toasts.asSharedFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val data = repo.load()
            _state.update {
                it.copy(loaded = true, tasks = data.tasks, categories = data.categories)
            }
        }
    }

    private fun toast(text: String, type: ToastType = ToastType.Info) {
        _toasts.tryEmit(ToastMsg(text, type))
    }

    /** Every mutation persists immediately — stronger than the web app's 5-minute autosave. */
    private fun persist() {
        val s = _state.value
        viewModelScope.launch { repo.save(s.tasks, s.categories) }
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
                draftCategory = "", draftPriority = "Medium", draftNotes = ""
            )
            else s.copy(
                selectedTaskId = id,
                draftCategory = t.category,
                draftPriority = t.priority,
                draftNotes = t.notes
            )
        }
    }

    fun setDraft(category: String? = null, priority: String? = null, notes: String? = null) {
        _state.update {
            it.copy(
                draftCategory = category ?: it.draftCategory,
                draftPriority = priority ?: it.draftPriority,
                draftNotes = notes ?: it.draftNotes
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
        _state.update { st ->
            st.copy(tasks = st.tasks.map {
                if (it.id == id) it.copy(
                    category = st.draftCategory,
                    priority = st.draftPriority,
                    notes = st.draftNotes,
                    updatedAt = Instant.now().toString()
                ) else it
            })
        }
        persist()
        toast("Task updated & saved!", ToastType.Success)
    }

    fun toggleCompleteSelected() {
        val s = _state.value
        val t = s.selectedTask
        if (t == null) {
            toast("Select a task first.", ToastType.Error)
            return
        }
        val nowCompleted = !t.completed
        _state.update { st ->
            st.copy(tasks = st.tasks.map {
                if (it.id == t.id) it.copy(
                    completed = nowCompleted,
                    updatedAt = Instant.now().toString()
                ) else it
            })
        }
        if (nowCompleted) clearSelection()
        persist()
        toast(if (nowCompleted) "✓ Marked complete!" else "Task reopened.", ToastType.Success)
    }

    fun deleteSelected() {
        val s = _state.value
        if (s.selectedTaskId == null) {
            toast("Select a task first.", ToastType.Error)
            return
        }
        _state.update { st ->
            st.copy(tasks = st.tasks.filterNot { it.id == st.selectedTaskId })
        }
        clearSelection()
        persist()
        toast("Task deleted.", ToastType.Info)
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
                draftCategory = "", draftPriority = "Medium", draftNotes = ""
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
        repo.exportJson(_state.value.tasks, _state.value.categories)

    fun importBackup(text: String) {
        val data: AppData? = repo.parseBackup(text)
        if (data == null) {
            toast("Invalid JSON file.", ToastType.Error)
            return
        }
        _state.update {
            it.copy(tasks = data.tasks, categories = data.categories)
        }
        resetFilters()
        clearSelection()
        persist()
        toast("Imported ${data.tasks.size} tasks.", ToastType.Success)
    }

    fun notifyExported() = toast("Exported!", ToastType.Success)

    // ---------- Pomodoro timer ----------

    fun toggleTimer() {
        if (_timer.value.running) stopTimer() else startTimer()
    }

    private fun startTimer() {
        if (_timer.value.secondsLeft <= 0) {
            _timer.update { it.copy(secondsLeft = it.presetMinutes * 60) }
        }
        _timer.update { it.copy(running = true) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timer.value.secondsLeft > 0) {
                delay(1000)
                _timer.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            _timer.update { it.copy(running = false) }
            toast("⏰ Timer complete!", ToastType.Warning)
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _timer.update { it.copy(running = false) }
    }

    fun setTimerPreset(minutes: Int) {
        timerJob?.cancel()
        _timer.value = TimerState(
            secondsLeft = minutes * 60,
            running = false,
            presetMinutes = minutes
        )
    }
}
