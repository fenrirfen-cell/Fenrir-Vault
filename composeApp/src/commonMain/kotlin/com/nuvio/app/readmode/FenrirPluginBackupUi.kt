package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val BackupMuted = Color(0xFF8E8E99)

/**
 * Fenrir: Settings → Sources → "Plugin backup". Saved by itself to Downloads/Fenrir Vault whenever
 * plugins change; "Save a copy" puts one anywhere (Drive, another folder); "Restore" reads one back.
 */
@Composable
internal fun FenrirPluginBackupCard() {
    val files = FenrirPluginBackup.files ?: return
    val scope = rememberCoroutineScope()
    val lastSaved by FenrirPluginBackup.lastSaved.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val saveAs = files.rememberSaveAs { ok -> message = if (ok) "Copy saved." else "Couldn't save the copy." }
    val open = files.rememberOpen { text ->
        when {
            text == null -> message = "Couldn't read that file."
            !FenrirPluginBackup.isBackup(text) -> message = "That file isn't a Fenrir backup."
            else -> pendingRestore = text
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Card).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Backup", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    files.autoSaveLocation?.let { where ->
                        "Your Stremio add-ons, Home rows, CloudStream plugins and Aniyomi extensions are saved to $where by themselves, " +
                            (if (lastSaved != null) "just now. " else "whenever they change. ") +
                            "The file stays there if you delete the app. If something goes missing, the bigger list before it is kept as the \"previous\" file."
                    } ?: "Save a copy to keep your Stremio add-ons, CloudStream plugins and Aniyomi extensions if you delete the app.",
                    color = BackupMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (restoring) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Restore", filled = true) { if (!restoring) open() }
            PillButton("Save a copy", filled = false) { saveAs(FenrirPluginBackup.build()) }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "Adds back the Stremio add-ons that are missing (and their Home rows), the repositories, " +
                        "downloads the CloudStream plugins and Aniyomi extensions again, and trusts the extensions " +
                        "you trusted before (only the exact same ones). Nothing you have now is removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    restoring = true
                    message = "Restoring… this can take a minute."
                    scope.launch {
                        message = FenrirPluginBackup.restore(text)
                        restoring = false
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

/** Fenrir: "N extensions need trusting · Trust all", above the Installed list. */
@Composable
internal fun FenrirTrustAllCard(host: FenrirPluginHost, waiting: List<FenrirPluginItem>) {
    if (waiting.isEmpty()) return
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (waiting.size == 1) "1 extension needs trusting" else "${waiting.size} extensions need trusting",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text("They won't run until you trust them.", color = BackupMuted, style = MaterialTheme.typography.bodySmall)
        }
        PillButton("Trust all", filled = true) { confirm = true }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Trust ${waiting.size} extension(s)?") },
            text = {
                Text(
                    "Extensions run code inside MugiVerse. Only trust ones you installed yourself:\n\n" +
                        waiting.joinToString("\n") { "• ${it.name}" },
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; host.trustAll() }) { Text("Trust all") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
