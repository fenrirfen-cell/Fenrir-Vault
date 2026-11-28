package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
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

/** Which side of MugiVerse the switch shows as selected. */
enum class FenrirMode { Watch, AniDrama, Read }

/**
 * The Watch / Ani-drama / Read pill at the top of Home and Ani-drama (Tsundoku draws its own copy
 * with Read selected). Without [onAniDrama] it falls back to the two-way Watch / Read pill.
 */
@Composable
fun ReadModeSwitch(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    selected: FenrirMode = FenrirMode.Watch,
    onWatch: () -> Unit = {},
    onAniDrama: (() -> Unit)? = null,
) {
    val openRead = ReadModeBridge.openRead ?: return
    val threeWay = onAniDrama != null
    Row(
        modifier = modifier
            .then(if (threeWay) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier)
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
        val segmentModifier = if (threeWay) Modifier.weight(1f) else Modifier
        ModeSegment(
            label = ReadModeBridge.watchLabel,
            icon = Icons.Rounded.PlayArrow,
            selected = selected == FenrirMode.Watch,
            onClick = onWatch,
            compact = threeWay,
            modifier = segmentModifier,
        )
        if (onAniDrama != null) {
            ModeSegment(
                label = "Ani-drama",
                icon = Icons.Rounded.Tv,
                selected = selected == FenrirMode.AniDrama,
                onClick = onAniDrama,
                compact = true,
                modifier = segmentModifier,
            )
        }
        ModeSegment(
            label = ReadModeBridge.readLabel,
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            selected = selected == FenrirMode.Read,
            onClick = openRead,
            compact = threeWay,
            modifier = segmentModifier,
        )
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onPrimary else Color.White.copy(alpha = 0.85f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .semantics { this.selected = selected }
            .clickable(enabled = !selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
