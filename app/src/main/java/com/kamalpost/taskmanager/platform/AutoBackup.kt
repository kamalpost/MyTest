package com.kamalpost.taskmanager.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes a backup JSON into a user-chosen folder on every data change.
 * Pointing it at a folder synced by Drive/Dropbox/etc. gives cross-device
 * backup without the app needing any cloud credentials of its own.
 */
object AutoBackup {

    private const val PREFS = "autobackup"
    private const val KEY_FOLDER = "folder_uri"
    private const val FILE_NAME = "taskmanager-autobackup.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun folder(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun setFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    fun clearFolder(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_FOLDER).apply()
    }

    /** Fire-and-forget: never blocks or crashes the caller on storage errors. */
    fun write(context: Context, json: String) {
        val folderUri = folder(context) ?: return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val dir = DocumentFile.fromTreeUri(appContext, folderUri) ?: return@launch
                val file = dir.findFile(FILE_NAME)
                    ?: dir.createFile("application/json", FILE_NAME)
                    ?: return@launch
                appContext.contentResolver.openOutputStream(file.uri, "wt")?.use { os ->
                    os.write(json.toByteArray())
                }
            }
        }
    }
}
