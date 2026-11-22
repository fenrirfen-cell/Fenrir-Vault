package com.nuvio.app.readmode

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeInputScale
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Download
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
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

/**
 * iPhone "Liquid Glass" look for the top buttons and the bottom island: clear glass that lets
 * the colours behind show through (smooth strong blur, light tint), a soft sheen across the top
 * and a glowing rim that is brightest at the top-left and bottom-right corners, plus a soft
 * shadow so it floats. Phones without blur get a dark see-through fill.
 */
fun Modifier.fenrirGlass(hazeState: HazeState?, shape: Shape = CircleShape): Modifier = this
    .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(hazeState) {
                // Strong, full-quality blur: smooth frosted colours instead of smudged posters.
                blurRadius = 30.dp
                inputScale = HazeInputScale.None
                backgroundColor = Color(0xFF0B0B0F)
                tints = listOf(
                    HazeTint(Color(0xFF15151B).copy(alpha = 0.28f)),
                    HazeTint(Color.White.copy(alpha = 0.05f)),
                )
                fallbackTint = HazeTint(Color(0xFF2A2B33).copy(alpha = 0.88f))
                noiseFactor = 0f
            }
        } else {
            Modifier.background(Color(0xFF2A2B33).copy(alpha = 0.88f))
        },
    )
    // Sheen: light catching the top half of the glass.
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.14f),
            0.5f to Color.White.copy(alpha = 0.03f),
            1f to Color.Transparent,
        ),
    )
    // Glowing rim: bright at top-left and bottom-right, fading along the sides.
    .border(
        1.2.dp,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.75f),
            0.35f to Color.White.copy(alpha = 0.10f),
            0.65f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.45f),
        ),
        shape,
    )

/** "FENRIR" wordmark on the left and the Watch / Ani-drama / Read pill on the right. */
@Composable
fun FenrirTopBar(
    selected: FenrirMode,
    onWatch: () -> Unit,
    onAniDrama: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val openRead = ReadModeBridge.openRead
    Row(
        // Fixed height so the wordmark and the subtitle under it stay put whatever the buttons' size.
        modifier = modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tap FENRIR READ to pick what Home shows: Nuvio's home or a CloudStream source's home.
        Box(modifier = Modifier.weight(1f)) {
            FenrirWordmarkSourceButton(onPicked = { if (selected != FenrirMode.Watch) onWatch() })
        }
        // New version: a round download button with a red dot (Update, then Install).
        if (FenrirUpdateButton(hazeState)) Spacer(Modifier.width(10.dp))
        // Search, then the Watch / Ani-drama / Read switch at the far right (For you is in the island).
        if (onSearch != null) {
            FenrirSearchButton(onSearch, hazeState)
            Spacer(Modifier.width(10.dp))
        }
        FenrirModePill(selected, onWatch, onAniDrama, openRead, hazeState)
    }
}

/**
 * Round Search button beside the mode pill. With a CloudStream source picked on Home it searches
 * only that source; otherwise it opens the normal Search screen ([onSearch]).
 */
@Composable
fun FenrirSearchButton(onSearch: () -> Unit, hazeState: HazeState? = null) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                if (FenrirSourceHome.selected.value != null) {
                    FenrirSourceHomeUi.searching.value = true
                } else {
                    onSearch()
                }
            }
            .semantics { contentDescription = "Search" },
        contentAlignment = Alignment.Center,
    ) {
        FenrirLiquidGlassBackground(hazeState, Modifier.matchParentSize())
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

/** ✨ opens the For you panel (titles like the ones you watched). */
@Composable
fun FenrirForYouButton(hazeState: HazeState? = null) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape)
            .clickable(role = Role.Button) { FenrirForYou.show() }
            .semantics { contentDescription = "For you" },
        contentAlignment = Alignment.Center,
    ) {
        FenrirLiquidGlassBackground(hazeState, Modifier.matchParentSize())
        Icon(
            androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFF6B73),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "FENRIR" with a smaller red "READ" beside it, both sitting on the same baseline. */
@Composable
fun FenrirWordmark(modifier: Modifier = Modifier, scale: Float = 1f) {
    Row(modifier = modifier) {
        Text(
            text = "FENRIR",
            color = Color.White,
            fontSize = (22 * scale).sp,
            letterSpacing = (5 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width((6 * scale).dp))
        Text(
            text = "READ",
            color = Color(0xFFFF4D57),
            fontSize = (13 * scale).sp,
            letterSpacing = (4 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
fun FenrirModePill(
    selected: FenrirMode,
    onWatch: () -> Unit,
    onAniDrama: (() -> Unit)?,
    onRead: (() -> Unit)?,
    hazeState: HazeState? = null,
) {
    var open by remember { mutableStateOf(false) }
    val label = when (selected) {
        FenrirMode.Watch -> ReadModeBridge.watchLabel
        FenrirMode.AniDrama -> "Ani-drama"
        FenrirMode.Read -> ReadModeBridge.readLabel
    }
    val modeIcon = when (selected) {
        FenrirMode.Watch -> Icons.Rounded.PlayArrow
        FenrirMode.AniDrama -> Icons.Rounded.Tv
        FenrirMode.Read -> Icons.AutoMirrored.Rounded.MenuBook
    }
    Box {
        // Same liquid glass as the bottom bar.
        Box(
            modifier = Modifier
                .height(42.dp)
                .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true },
        ) {
            FenrirLiquidGlassBackground(hazeState, Modifier.matchParentSize())
            Row(
                modifier = Modifier.height(42.dp).padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(modeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Switch side", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        // Zangetsu-style glass menu: dark see-through panel, rounded, light rim.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(224.dp).padding(horizontal = 6.dp),
            shape = RoundedCornerShape(26.dp),
            containerColor = Color(0xFF1C1C22).copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.12f))),
            ),
        ) {
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
    val accent = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = { Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (selected) accent else Color.White) },
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, tint = accent) }
        } else null,
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent),
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
fun FenrirNavigationBar(items: List<FenrirNavItem>, modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // A floating rounded "island" above the content, not a full-width bar.
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp + bottomInset)
            .fillMaxWidth()
            .fenrirGlass(hazeState)
            .padding(horizontal = 6.dp, vertical = 7.dp),
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


/**
 * Round button beside Search when a new Fenrir Read version is out: a red dot, a progress ring
 * while downloading, and a small menu (Update / Not now). Returns true when it's shown.
 */
@Composable
fun FenrirUpdateButton(hazeState: HazeState? = null): Boolean {
    val flow = FenrirPullRefresh.updaterState ?: return false
    val state by flow.collectAsStateWithLifecycle()
    val update = state.update
    if (!state.showDialog || update == null) return false
    val red = Color(0xFFFF4D57)
    var menu by remember { mutableStateOf(false) }
    val ready = state.downloadedApkPath != null
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    when {
                        state.isDownloading -> menu = true
                        ready -> FenrirPullRefresh.installUpdate?.invoke()
                        else -> menu = true
                    }
                }
                .semantics { contentDescription = "Update available" },
            contentAlignment = Alignment.Center,
        ) {
            FenrirLiquidGlassBackground(hazeState, Modifier.matchParentSize())
            if (state.isDownloading) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { (state.downloadProgress ?: 0f).coerceIn(0f, 1f) },
                    color = red,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp),
                )
            }
            Icon(
                imageVector = if (ready) Icons.Rounded.Check else Icons.Rounded.Download,
                contentDescription = null,
                tint = red,
                modifier = Modifier.size(20.dp),
            )
            if (!state.isDownloading) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(red),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                text = when {
                    state.errorMessage != null -> "Update failed"
                    state.isDownloading -> "Downloading ${((state.downloadProgress ?: 0f) * 100).toInt()}%"
                    else -> "New version"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Text(
                text = state.errorMessage ?: update.tag,
                color = Color(0xFF9A9AA6),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            if (!state.isDownloading) {
                DropdownMenuItem(
                    text = { Text(if (state.errorMessage != null) "Try again" else "Update now", color = red, fontWeight = FontWeight.SemiBold) },
                    onClick = { menu = false; FenrirPullRefresh.downloadUpdate?.invoke() },
                )
                DropdownMenuItem(
                    text = { Text("Not now") },
                    onClick = { menu = false; FenrirPullRefresh.dismissUpdate?.invoke() },
                )
            }
        }
    }
    return true
}
