package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Update notifications: the phone checks GitHub for a new MugiVerse in the background (even
 * with the app closed) and posts "Update available" → "Downloading" → "Ready to install".
 * The Android side stores these and does the work; Watch and Read both show the same settings.
 */
object FenrirUpdateNotify {
    val enabled = MutableStateFlow(true)
    val hours = MutableStateFlow(6)
    val prefetch = MutableStateFlow(false)
    /** Fenrir: also offer beta builds (published first, before they go to everyone). Off by default. */
    val beta = MutableStateFlow(false)

    /** Set by the Android side: saves the settings and re-plans the background check. */
    var onChange: (() -> Unit)? = null
    /** Opens the phone's battery settings for the app. */
    var openBatterySettings: (() -> Unit)? = null

    val isAvailable: Boolean get() = onChange != null
    val hourChoices = listOf(3, 6, 12, 24)

    fun setEnabled(on: Boolean) { enabled.value = on; onChange?.invoke() }
    fun setHours(h: Int) { hours.value = h; onChange?.invoke() }
    fun setPrefetch(on: Boolean) { prefetch.value = on; onChange?.invoke() }
    fun setBeta(on: Boolean) { beta.value = on; onChange?.invoke() }
}

private val Purple = Color(0xFF9650E6)

/** The four update rows, in a plain dark card (used on the Read side, which has its own look). */
@Composable
fun FenrirUpdateSettingsCard(modifier: Modifier = Modifier) {
    val on by FenrirUpdateNotify.enabled.collectAsState()
    val hours by FenrirUpdateNotify.hours.collectAsState()
    val prefetch by FenrirUpdateNotify.prefetch.collectAsState()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E24))
            .padding(vertical = 6.dp),
    ) {
        CardRow("Notify me about updates", "Checks in the background, even when the app is closed") {
            Switch(checked = on, onCheckedChange = FenrirUpdateNotify::setEnabled, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
        HoursRow(hours, enabled = on)
        CardRow(
            "Download before notifying",
            if (prefetch) "On: you're told when it's ready to install" else "Off: downloads only after you tap Update",
            enabled = on,
        ) {
            Switch(checked = prefetch, enabled = on, onCheckedChange = FenrirUpdateNotify::setPrefetch, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
        CardRow(
            "Allow background checks",
            "Only if notifications come late: battery settings",
            onClick = { FenrirUpdateNotify.openBatterySettings?.invoke() },
        ) { Text("›", color = Color(0xFFC8C8D2), fontSize = 26.sp) }
    }
}

@Composable
private fun HoursRow(hours: Int, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        CardRow("How often", "Every $hours hours", enabled = enabled, onClick = { if (enabled) open = true }) {
            Text("$hours h ▾", color = Color(0xFFC8AAFF), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FenrirUpdateNotify.hourChoices.forEach { h ->
                DropdownMenuItem(text = { Text("Every $h hours") }, onClick = { open = false; FenrirUpdateNotify.setHours(h) })
            }
        }
    }
}

@Composable
private fun CardRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color(0xFFF0F0F5) else Color(0xFF7A7A85), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color(0xFFAAAAB4), fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
