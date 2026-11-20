package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fenrir style: the Zangetsu-like layout (wordmark + mode pill on top, pill bottom bar, big hero,
 * white Play buttons). Colors come from the theme (Fenrir theme by default). Off on iOS/desktop;
 * the Android app turns it on at startup and keeps the user's choice.
 */
object FenrirStyle {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var persist: ((Boolean) -> Unit)? = null

    /** True once the Android app has installed the style (so the Settings switch is shown). */
    var isAvailable: Boolean = false
        private set

    fun install(enabled: Boolean, persist: (Boolean) -> Unit) {
        this.persist = persist
        isAvailable = true
        _enabled.value = enabled
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        persist?.invoke(enabled)
    }

    val surface = Color(0xFF16161C)
    val pill = Color(0xFF1C1D26)
    val muted = Color(0xFF8E8E99)
}

/** "FENRIR" wordmark on the left and the Watch / Ani-drama / Read pill on the right. */
@Composable
fun FenrirTopBar(
    selected: FenrirMode,
    onWatch: () -> Unit,
    onAniDrama: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val openRead = ReadModeBridge.openRead
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FENRIR",
            color = Color.White,
            fontSize = 22.sp,
            letterSpacing = 6.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        FenrirModePill(selected, onWatch, onAniDrama, openRead)
    }
}

@Composable
fun FenrirModePill(
    selected: FenrirMode,
    onWatch: () -> Unit,
    onAniDrama: (() -> Unit)?,
    onRead: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    val label = when (selected) {
        FenrirMode.Watch -> ReadModeBridge.watchLabel
        FenrirMode.AniDrama -> "Ani-drama"
        FenrirMode.Read -> ReadModeBridge.readLabel
    }
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(FenrirStyle.pill)
                .clickable(role = Role.Button) { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Switch side", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ModeMenuItem(ReadModeBridge.watchLabel, Icons.Rounded.PlayArrow, selected == FenrirMode.Watch) { open = false; onWatch() }
            if (onAniDrama != null) {
                ModeMenuItem("Ani-drama", Icons.Rounded.Tv, selected == FenrirMode.AniDrama) { open = false; onAniDrama() }
            }
            if (onRead != null) {
                ModeMenuItem(ReadModeBridge.readLabel, Icons.AutoMirrored.Rounded.MenuBook, selected == FenrirMode.Read) { open = false; onRead() }
            }
        }
    }
}

@Composable
private fun ModeMenuItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        onClick = onClick,
    )
}

/** One tab of the Fenrir bottom bar. */
class FenrirNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
)

/** Bottom bar: icons with labels; the current tab is red with a soft red pill behind the icon. */
@Composable
fun FenrirNavigationBar(items: List<FenrirNavItem>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FenrirStyle.surface)
            .padding(top = 8.dp, bottom = 8.dp + bottomInset),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { item ->
            val tint = if (item.selected) accent else FenrirStyle.muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Tab, onClick = item.onClick)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (item.selected) accent.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        item.icon != null -> Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        item.painter != null -> Icon(item.painter, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
