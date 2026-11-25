package com.nuvio.app.readmode

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Fenrir: plugin backup files on Android. The automatic one goes to Downloads/Fenrir Vault, which
 * Android keeps when the app is deleted. After a reinstall the user picks it from there (Restore),
 * because a new install can't read files the old one wrote without being shown them.
 */
class FenrirBackupFilesAndroid(context: Context) : FenrirBackupFiles {
    private val app = context.applicationContext

    private companion object {
        const val TAG = "FenrirBackup"
        const val MIME = "application/json"
        const val FOLDER = "Fenrir Vault"
    }

    override val autoSaveLocation: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads › $FOLDER" else null

    override fun autoSave(text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = app.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
            // Only finds the file this install made (Android hides other apps' and older installs' files).
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(FenrirPluginBackup.FILE_NAME, relativePath),
                null,
            )?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null }
            val uri = existing ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FenrirPluginBackup.FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, MIME)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                },
            ) ?: error("couldn't create the backup file")
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("couldn't open the backup file")
            true
        }.onFailure { Log.w(TAG, "Automatic backup failed", it) }.getOrDefault(false)
    }

    @Composable
    override fun rememberSaveAs(onDone: (Boolean) -> Unit): (text: String) -> Unit {
        val context = LocalContext.current
        val done by rememberUpdatedState(onDone)
        val pending = remember { mutableStateOf<String?>(null) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
            val text = pending.value
            pending.value = null
            if (uri == null || text == null) return@rememberLauncherForActivityResult
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } != null
            }.getOrDefault(false)
            done(ok)
        }
        return { text ->
            pending.value = text
            launcher.launch(FenrirPluginBackup.FILE_NAME)
        }
    }

    @Composable
    override fun rememberOpen(onRead: (String?) -> Unit): () -> Unit {
        val context = LocalContext.current
        val read by rememberUpdatedState(onRead)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // a plugin backup is small; anything huge isn't one
                    val bytes = input.readBytes()
                    if (bytes.size > 2_000_000) null else bytes.decodeToString()
                }
            }.getOrNull()
            read(text)
        }
        return { launcher.launch(arrayOf(MIME, "text/plain", "application/octet-stream", "*/*")) }
    }
}
