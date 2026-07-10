package com.kamalpost.taskmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Persists all app data as a single JSON file in internal storage —
 * the Android counterpart of the web app's localStorage + Flask save file.
 * The on-disk format is identical to the web app's export format.
 */
class TaskRepository(context: Context) {

    private val file: File = File(context.filesDir, "tasks.json")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun load(): AppData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AppData()
        runCatching { json.decodeFromString<AppData>(file.readText()) }
            .getOrDefault(AppData())
    }

    suspend fun save(tasks: List<Task>, categories: List<String>) = withContext(Dispatchers.IO) {
        val data = AppData(
            exportedAt = Instant.now().toString(),
            categories = categories,
            tasks = tasks
        )
        val tmp = File(file.parentFile, "tasks.json.tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.writeText(json.encodeToString(AppData.serializer(), data))
            tmp.delete()
        }
    }

    fun exportJson(tasks: List<Task>, categories: List<String>): String =
        json.encodeToString(
            AppData.serializer(),
            AppData(exportedAt = Instant.now().toString(), categories = categories, tasks = tasks)
        )

    fun parseBackup(text: String): AppData? =
        runCatching { json.decodeFromString<AppData>(text) }.getOrNull()
}
