package com.nuvio.app.mugi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** True when the MugiVerse look is showing (Android app, Fenrir style on, MugiVerse on). */
@Composable
fun mugiOn(): Boolean {
    val mugi by MugiStyle.enabled.collectAsStateWithLifecycle()
    val fenrir by com.nuvio.app.readmode.FenrirStyle.enabled.collectAsStateWithLifecycle()
    return mugi && fenrir && com.nuvio.app.readmode.ReadModeBridge.isAvailable
}

/** The colours a MugiVerse mode paints the app with (accent + its background). */
fun ColorScheme.mugi(mode: MugiMode): ColorScheme {
    val bg = MugiPalette.background(mode)
    return copy(
        primary = mode.accent,
        onPrimary = Color.White,
        background = bg,
        surface = bg,
        secondaryContainer = mode.accent.copy(alpha = 0.28f),
        onSecondaryContainer = MugiPalette.Ivory,
    )
}

private val Violet = MugiMode.ANI_DRAMA.accent
private val BoxFill = Color(0xFF1C1430)
private val Edge = Color(0xFF3F3060)

private fun chipIcon(label: String): ImageVector = when (label.lowercase()) {
    "airing", "airing now" -> Icons.Rounded.LiveTv
    "trending" -> Icons.Rounded.Whatshot
    "popular" -> Icons.Rounded.BarChart
    "top 100" -> Icons.Rounded.EmojiEvents
    "top rated" -> Icons.Rounded.Star
    "seasons" -> Icons.Rounded.Layers
    "genres" -> Icons.Rounded.GridView
    "upcoming" -> Icons.Rounded.Event
    "calendar" -> Icons.Rounded.CalendarMonth
    "new" -> Icons.Rounded.FiberNew
    "networks" -> Icons.Rounded.Tv
    "dub" -> Icons.Rounded.RecordVoiceOver
    "sub" -> Icons.Rounded.Subtitles
    "both" -> Icons.Rounded.Check
    else -> Icons.Rounded.GraphicEq
}

/**
 * Every catalog row heading in MugiVerse: ivory serif title with a gold nautical glyph (telescope
 * for trending and streaming-provider rows, compass otherwise), the real source underneath, a
 * bronze line and a gold "See All ›" (only when the row had See all). Accent follows the mode.
 */
@Composable
fun MugiRowHeader(title: String, subtitle: String?, horizontalPadding: androidx.compose.ui.unit.Dp, onSeeAll: (() -> Unit)?) {
    val t = title.lowercase()
    val sub = subtitle.orEmpty().lowercase()
    val glyph = if ("trend" in t || "stream" in sub || "stream" in t) CatalogGlyph.TELESCOPE else CatalogGlyph.COMPASS
    val parchment = if (t.startsWith("top 10") || t.startsWith("top 100")) rememberMugiArt().parchment else null
    MugiCatalogHeader(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        parchment = parchment,
        onSeeAll = onSeeAll,
        modifier = Modifier.padding(horizontal = (horizontalPadding - 16.dp).coerceAtLeast(0.dp)),
    )
}

// ---------------------------------------------------------------- Ani-Drama polish

/** The Ani-Drama colours (purple near the header, near-black below). */
object AniDramaColors {
    val BackgroundTop = Color(0xFF21102F)
    val BackgroundBottom = Color(0xFF090710)
    val Surface = Color(0xFF21142E)
    val CardBackground = Color(0xFF190F24)
    val CardBorder = Color(0xFF59347A)
    val Purple = Color(0xFF992BE8)
    val PurpleDark = Color(0xFF59139E)
    val Gold = Color(0xFFD4AF65)
    val Ivory = Color(0xFFF5E8D0)
    val Secondary = Color(0xFFBDB0C8)
    val NavigationBackground = Color(0xFF110C19)
    val Divider = Color(0xFF382A43)
}

/** Which Ani-Drama category (index into AniDramaRegion) is showing; the header picker sets it. */
object MugiAniState {
    val region = kotlinx.coroutines.flow.MutableStateFlow(0)
}

fun Modifier.aniDramaPremiumBackground(): Modifier = background(
    androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to AniDramaColors.BackgroundTop,
        0.45f to Color(0xFF140C20),
        1f to AniDramaColors.BackgroundBottom,
    ),
)

/** "Anime ▾" in the header: opens Anime · K-Drama · C-Drama · J-Drama. */
@Composable
fun AniDramaCategorySelector(categories: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val selected = categories.getOrElse(selectedIndex) { categories.first() }
    Box {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(AniDramaColors.Surface)
                .border(1.dp, AniDramaColors.Gold, shape)
                .clickable(role = Role.DropdownList, onClickLabel = "Choose category") { expanded = true }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected, color = AniDramaColors.Ivory, fontFamily = MugiPlayfair, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Icon(
                if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = AniDramaColors.Gold,
                modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(180.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = AniDramaColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AniDramaColors.Gold),
        ) {
            categories.forEachIndexed { i, category ->
                val active = i == selectedIndex
                androidx.compose.material3.DropdownMenuItem(
                    modifier = Modifier.background(if (active) AniDramaColors.PurpleDark else Color.Transparent),
                    text = { Text(category, color = AniDramaColors.Ivory, fontFamily = MugiPlayfair, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (active) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = AniDramaColors.Ivory) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelected(i)
                    },
                )
            }
        }
    }
}

@Composable
fun AniDramaFilterChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, trailing: ImageVector? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                if (selected) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(AniDramaColors.Purple, AniDramaColors.PurpleDark))
                else androidx.compose.ui.graphics.SolidColor(AniDramaColors.Surface),
            )
            .border(1.dp, if (selected) Color(0xFFB86CFF) else AniDramaColors.Divider, shape)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = AniDramaColors.Ivory, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = AniDramaColors.Ivory, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        trailing?.let {
            Icon(it, contentDescription = null, tint = AniDramaColors.Gold, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * One swipeable row: the Dub / Sub / Both picker first (Anime only, a small dropdown), then every
 * menu (Airing … Calendar). The chosen menu scrolls into view by itself.
 */
@Composable
fun MugiAniFilterRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    audioLabels: List<String>?,
    audioSelected: Int,
    onAudioSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val offset = if (audioLabels != null) 1 else 0
    androidx.compose.runtime.LaunchedEffect(selected, offset) {
        val target = selected + offset
        val visible = state.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.index == target }
        val fullyVisible = item != null && item.offset >= 0 &&
            item.offset + item.size <= state.layoutInfo.viewportEndOffset
        if (!fullyVisible) state.animateScrollToItem((target - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (audioLabels != null) {
            item(key = "audio") {
                var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        val label = audioLabels.getOrElse(audioSelected) { audioLabels.last() }
                        AniDramaFilterChip(
                            label = label,
                            selected = false,
                            onClick = { open = true },
                            icon = chipIcon(label),
                            trailing = Icons.Rounded.ArrowDropDown,
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = open,
                            onDismissRequest = { open = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = AniDramaColors.Surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AniDramaColors.Gold),
                        ) {
                            audioLabels.forEachIndexed { i, l ->
                                val active = i == audioSelected
                                androidx.compose.material3.DropdownMenuItem(
                                    modifier = Modifier.background(if (active) AniDramaColors.PurpleDark else Color.Transparent),
                                    leadingIcon = { Icon(chipIcon(l), null, tint = AniDramaColors.Ivory, modifier = Modifier.size(18.dp)) },
                                    text = { Text(l, color = AniDramaColors.Ivory, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                                    trailingIcon = if (active) {
                                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = AniDramaColors.Ivory) }
                                    } else null,
                                    onClick = { open = false; onAudioSelect(i) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.width(1.dp).height(28.dp).background(AniDramaColors.Gold.copy(alpha = 0.45f)))
                }
            }
        }
        labels.forEachIndexed { i, label ->
            item(key = "chip-$label") {
                AniDramaFilterChip(label = label, selected = i == selected, onClick = { onSelect(i) }, icon = chipIcon(label))
            }
        }
    }
}

@Composable
fun EpisodeLanguageBadge(isDub: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (isDub) AniDramaColors.PurpleDark else Color(0xFF3F354B))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(if (isDub) "DUB" else "SUB", color = AniDramaColors.Ivory, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
