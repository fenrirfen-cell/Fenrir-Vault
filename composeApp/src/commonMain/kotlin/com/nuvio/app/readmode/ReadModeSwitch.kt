package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Link between Nuvio and the Read mode (Tsundoku) that the Android app embeds.
 *
 * [openRead] is set by the Android app at startup. It stays null on iOS, desktop and any build
 * without Read mode, and then the switch is not shown.
 */
object ReadModeBridge {
    var openRead: (() -> Unit)? = null
    var watchLabel: String = "Watch"
    var readLabel: String = "Read"

    val isAvailable: Boolean get() = openRead != null
}

/**
 * The Watch / Read pill. Floats over the top of the Home tab; Watch is always the selected side
 * here (Tsundoku draws its own copy with Read selected).
 */
@Composable
fun ReadModeSwitch(modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    val openRead = ReadModeBridge.openRead ?: return
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(
                // Same frosted glass as the bottom navigation bar.
                if (hazeState != null) Modifier.hazeEffect(state = hazeState) { blurRadius = 24.dp } else Modifier,
            )
            .background(Color(0xFF1C1C1E).copy(alpha = if (hazeState != null) 0.55f else 0.82f))
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeSegment(
            label = ReadModeBridge.watchLabel,
            icon = Icons.Rounded.PlayArrow,
            selected = true,
            onClick = {},
        )
        ModeSegment(
            label = ReadModeBridge.readLabel,
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            selected = false,
            onClick = openRead,
        )
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onPrimary else Color.White.copy(alpha = 0.85f)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .semantics { this.selected = selected }
            .clickable(enabled = !selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}
