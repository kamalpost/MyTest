package com.kamalpost.taskmanager.data.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.kamalpost.taskmanager.data.AppData
import com.kamalpost.taskmanager.data.JsonTaskStore
import com.kamalpost.taskmanager.data.Task
import com.kamalpost.taskmanager.data.TaskStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val priority: String,
    val notes: String,
    val completed: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val dueAt: String,
    val position: Int
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val position: Int
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY position")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun categories(): List<CategoryEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>, categories: List<CategoryEntity>) {
        clearTasks()
        clearCategories()
        insertTasks(tasks)
        insertCategories(categories)
    }
}

@Database(entities = [TaskEntity::class, CategoryEntity::class], version = 1, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun dao(): TaskDao

    companion object {
        @Volatile
        private var instance: TaskDatabase? = null

        fun get(context: Context): TaskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "taskmanager.db"
                ).build().also { instance = it }
            }
    }
}

/** Room-backed store. The list-level save keeps the exact semantics the app
 *  (and the web original) rely on: full snapshot, insertion order preserved. */
class RoomTaskStore(private val context: Context) : TaskStore {

    private val dao get() = TaskDatabase.get(context).dao()

    override suspend fun load(): AppData = withContext(Dispatchers.IO) {
        migrateLegacyJsonIfNeeded()
        AppData(
            categories = dao.categories().map { it.name },
            tasks = dao.tasks().map {
                Task(
                    id = it.id, name = it.name, category = it.category,
                    priority = it.priority, notes = it.notes, completed = it.completed,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, dueAt = it.dueAt
                )
            }
        )
    }

    override suspend fun save(tasks: List<Task>, categories: List<String>) =
        withContext(Dispatchers.IO) {
            dao.replaceAll(
                tasks.mapIndexed { i, t ->
                    TaskEntity(
                        id = t.id, name = t.name, category = t.category,
                        priority = t.priority, notes = t.notes, completed = t.completed,
                        createdAt = t.createdAt, updatedAt = t.updatedAt, dueAt = t.dueAt,
                        position = i
                    )
                },
                categories.mapIndexed { i, c -> CategoryEntity(name = c, position = i) }
            )
        }

    /** One-time import of v1.0's tasks.json; the file is kept, renamed, as a safety copy. */
    private suspend fun migrateLegacyJsonIfNeeded() {
        val legacy = JsonTaskStore(context)
        if (!legacy.file.exists()) return
        if (dao.tasks().isEmpty() && dao.categories().isEmpty()) {
            val data = legacy.load()
            if (data.tasks.isNotEmpty() || data.categories.isNotEmpty()) {
                save(data.tasks, data.categories)
            }
        }
        legacy.file.renameTo(
            java.io.File(legacy.file.parentFile, "tasks.json.migrated")
        )
    }
}
