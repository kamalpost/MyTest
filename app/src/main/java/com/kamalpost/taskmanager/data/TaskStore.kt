package com.kamalpost.taskmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/** Persistence abstraction. The app uses the Room implementation;
 *  [JsonTaskStore] remains as the legacy-migration source and test double. */
interface TaskStore {
    suspend fun load(): AppData
    suspend fun save(tasks: List<Task>, categories: List<String>)
}

/** JSON (de)serialization shared by backups, export, and the legacy file store.
 *  The format is identical to the web app's export, so backups are interchangeable. */
object JsonBackup {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun exportJson(tasks: List<Task>, categories: List<String>): String =
        json.encodeToString(
            AppData.serializer(),
            AppData(exportedAt = Instant.now().toString(), categories = categories, tasks = tasks)
        )

    fun parseBackup(text: String): AppData? =
        runCatching { json.decodeFromString<AppData>(text) }.getOrNull()
}

/** Single-JSON-file store — the original v1.0 persistence (Android counterpart
 *  of the web app's localStorage). Still used to migrate v1.0 data into Room. */
class JsonTaskStore(context: Context) : TaskStore {

    val file: File = File(context.filesDir, "tasks.json")

    override suspend fun load(): AppData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AppData()
        runCatching { JsonBackup.json.decodeFromString<AppData>(file.readText()) }
            .getOrDefault(AppData())
    }

    override suspend fun save(tasks: List<Task>, categories: List<String>) =
        withContext(Dispatchers.IO) {
            val data = AppData(
                exportedAt = Instant.now().toString(),
                categories = categories,
                tasks = tasks
            )
            val tmp = File(file.parentFile, "tasks.json.tmp")
            tmp.writeText(JsonBackup.json.encodeToString(AppData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.writeText(JsonBackup.json.encodeToString(AppData.serializer(), data))
                tmp.delete()
            }
        }
}
