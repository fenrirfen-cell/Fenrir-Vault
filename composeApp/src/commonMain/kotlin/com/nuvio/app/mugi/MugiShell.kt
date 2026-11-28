package com.nuvio.app.mugi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_compass
import nuvio.composeapp.generated.resources.mugi_library_cabin
import nuvio.composeapp.generated.resources.mugi_logo
import nuvio.composeapp.generated.resources.mugi_mode_anidrama
import nuvio.composeapp.generated.resources.mugi_mode_read
import nuvio.composeapp.generated.resources.mugi_mode_watch
import nuvio.composeapp.generated.resources.mugi_parchment
import nuvio.composeapp.generated.resources.mugi_reading_frame
import nuvio.composeapp.generated.resources.mugi_rope_frame
import nuvio.composeapp.generated.resources.mugi_shelf_row
import org.jetbrains.compose.resources.painterResource

/**
 * MugiVerse look (on by default in the Android app; Settings → "MugiVerse look" turns it off and
 * brings back the Fenrir style). Only the look changes; every screen and feature stays.
 */
object MugiStyle {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var persist: ((Boolean) -> Unit)? = null
    var isAvailable: Boolean = false
        private set

    /** Set by the Android app: true when the phone's animations are turned off. */
    var reduceMotion: () -> Boolean = { false }

    fun install(enabled: Boolean, persist: (Boolean) -> Unit) {
        this.persist = persist
        isAvailable = true
        _enabled.value = enabled
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        persist?.invoke(enabled)
    }
}

@Composable
fun rememberMugiArt(): MugiArt = MugiArt(
    logo = painterResource(Res.drawable.mugi_logo),
    compass = painterResource(Res.drawable.mugi_compass),
    watch = painterResource(Res.drawable.mugi_mode_watch),
    aniDrama = painterResource(Res.drawable.mugi_mode_anidrama),
    read = painterResource(Res.drawable.mugi_mode_read),
    parchment = painterResource(Res.drawable.mugi_parchment),
    ropeFrame = painterResource(Res.drawable.mugi_rope_frame),
    readingFrame = painterResource(Res.drawable.mugi_reading_frame),
    libraryCabin = painterResource(Res.drawable.mugi_library_cabin),
    shelfRow = painterResource(Res.drawable.mugi_shelf_row),
)

/**
 * The header for Watch and Ani-Drama: the MugiVerse logo with the mode ribbon (tap it to choose
 * what Home shows, as the old wordmark did), then [trailing] and the bell (app updates live there now
 * that the header has no mode switch).
 */
@Composable
fun MugiTopBar(
    mode: MugiMode,
    art: MugiArt,
    modifier: Modifier = Modifier,
    onSourcePicked: () -> Unit = {},
    // Movies / TV Shows on Watch's Home, the category picker on Ani-Drama. Search is in the bottom bar.
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(66.dp).padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            // the logo opens the same source menu on Watch and Ani-Drama (▾ like READ ▾ on the Read
            // side); a source picked from Ani-Drama opens on Watch's Home, where sources show
            com.nuvio.app.readmode.FenrirWordmarkSourceButton(onPicked = onSourcePicked, label = { _ -> MugiLogoWithRibbon(mode, art.logo, dropdown = true) })
        }
        trailing()
        Spacer(Modifier.width(4.dp))
        MugiBell()
    }
}

/** What the Search tab in the bottom bar does: search the picked source if there is one, else Search. */
fun mugiSearch(onSearchTab: () -> Unit, inWatch: Boolean, onWatchHome: () -> Unit) {
    if (inWatch && com.nuvio.app.readmode.FenrirSourceHome.selected.value != null) {
        onWatchHome()
        com.nuvio.app.readmode.FenrirSourceHomeUi.searching.value = true
    } else {
        onSearchTab()
    }
}

@Composable
fun MugiLogoWithRibbon(mode: MugiMode, logo: Painter, dropdown: Boolean = false) {
    Box(Modifier.width(176.dp).height(60.dp)) {
        Image(logo, "MugiVerse", Modifier.width(168.dp).height(46.dp), contentScale = ContentScale.Fit)
        Text(
            mode.title.uppercase() + if (dropdown) " ▾" else "",
            color = MugiPalette.Ivory,
            fontFamily = MugiPlayfair,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 38.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(mode.accent, mode.accent.copy(red = mode.accent.red * 0.6f, green = mode.accent.green * 0.6f, blue = mode.accent.blue * 0.6f))))
                .padding(horizontal = 14.dp, vertical = 1.dp),
        )
    }
}

/** Bell: a red dot when a new app version is ready; tap for Update (moved from the old mode pill). */
@Composable
private fun MugiBell() {
    var open by remember { mutableStateOf(false) }
    val updater = com.nuvio.app.readmode.FenrirPullRefresh.updaterState?.collectAsStateWithLifecycle()?.value
    val update = updater?.update?.takeIf { updater.showDialog }
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Notifications") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = Color(0xFFD9B26A), modifier = Modifier.size(25.dp))
            if (update != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(9.dp).clip(CircleShape).background(Color(0xFFE44349)))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(240.dp).padding(horizontal = 6.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFF0E1822),
            border = BorderStroke(1.dp, MugiPalette.Gold),
        ) {
            if (update != null && updater != null) {
                val ready = updater.downloadedApkPath != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !updater.isDownloading) {
                            if (ready) {
                                open = false
                                com.nuvio.app.readmode.FenrirPullRefresh.installUpdate?.invoke()
                            } else {
                                com.nuvio.app.readmode.FenrirPullRefresh.downloadUpdate?.invoke()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = MugiMode.WATCH.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                updater.errorMessage != null -> "Update failed · tap to retry"
                                updater.isDownloading -> "Downloading ${((updater.downloadProgress ?: 0f) * 100).toInt().coerceIn(0, 100)}%"
                                ready -> "Install update"
                                else -> "Update to ${update.tag.substringAfterLast("-")}"
                            },
                            color = MugiPalette.Ivory, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                        Text(if (ready) "Ready to install" else "New version ready", color = MugiPalette.Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    if (!updater.isDownloading) {
                        Text(
                            "✕", color = MugiPalette.Muted, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape).clickable { open = false; com.nuvio.app.readmode.FenrirPullRefresh.dismissUpdate?.invoke() }.padding(6.dp),
                        )
                    }
                }
            } else {
                Text(
                    "You're all caught up",
                    color = MugiPalette.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** An extra button in the compass menu (e.g. Assistant), shown above the three modes. */
class MugiMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

class MugiNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
)

/**
 * The bottom bar: the existing tabs split around the compass (2 left, the rest right). The
 * compass only opens the mode menu; while it's open the other tabs don't react.
 */
@Composable
fun MugiBottomBar(
    mode: MugiMode,
    art: MugiArt,
    items: List<MugiNavItem>,
    menuOpen: Boolean,
    onCompass: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val left = items.take(2)
    val right = items.drop(2)
    Box(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().height(86.dp), contentAlignment = Alignment.BottomCenter) {
            MugiNavSurface(Modifier.height(74.dp)) {
                left.forEach { MugiTab(it, mode, enabled = !menuOpen) }
                Spacer(Modifier.width(74.dp))
                right.forEach { MugiTab(it, mode, enabled = !menuOpen) }
            }
            MugiCompassButton(art, menuOpen, onCompass, Modifier.align(Alignment.TopCenter).size(74.dp))
        }
    }
}

@Composable
private fun RowScope.MugiTab(item: MugiNavItem, mode: MugiMode, enabled: Boolean) {
    val tint = if (item.selected) mode.accent else MugiPalette.Ivory.copy(alpha = 0.85f)
    Column(
        Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = item.label, onClick = item.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            item.icon != null -> Icon(item.icon, null, tint = tint, modifier = Modifier.size(24.dp))
            item.painter != null -> Icon(item.painter, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(item.label, color = tint, fontSize = 11.5.sp, fontFamily = MugiPlayfair, maxLines = 1)
    }
}

/**
 * The compass menu over the whole screen (dim, three medallions with lines, parchment labels).
 * [onPick] runs once for a different mode; Back, the compass or a tap outside closes it.
 */
@Composable
fun MugiModeMenu(
    mode: MugiMode,
    art: MugiArt,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (MugiMode) -> Unit,
    available: (MugiMode) -> Boolean = { true },
    extras: List<MugiMenuAction> = emptyList(),
) {
    MugiModeShell(
        mode = mode,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onModeChange = { if (available(it)) onPick(it) },
        art = art,
        extras = extras,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        navigation = { _, _ -> },
        content = {},
    )
}
