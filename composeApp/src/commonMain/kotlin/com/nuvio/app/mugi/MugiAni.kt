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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/** Anime · K-Drama · C-Drama · J-Drama on parchment; the chosen one violet with a gold rim. */
@Composable
fun MugiAniRegions(art: MugiArt, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(role = Role.Tab, onClickLabel = label) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color(0xFF552081), RoundedCornerShape(7.dp))
                            .border(1.5.dp, MugiPalette.Gold, RoundedCornerShape(7.dp)),
                    )
                    Text(label, color = MugiPalette.Ivory, fontSize = 13.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, maxLines = 1)
                } else {
                    ParchmentLabel(label, art.parchment, Modifier.matchParentSize(), 12)
                }
            }
        }
    }
}

/**
 * The Ani-Drama menus as boxed buttons: the first five with the icon above the name, the rest
 * three to a row with the icon beside it. Same menus and order as before.
 */
@Composable
fun MugiAniChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val top = labels.take(5)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            top.forEachIndexed { i, label ->
                MugiAniBox(label, i == selected, tall = true, modifier = Modifier.weight(1f)) { onSelect(i) }
            }
        }
        labels.drop(5).chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { j, label ->
                    val index = 5 + r * 3 + j
                    MugiAniBox(label, index == selected, tall = false, modifier = Modifier.weight(1f)) { onSelect(index) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Dub · Sub · Both as three equal boxes. */
@Composable
fun MugiAniAudio(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            MugiAniBox(label, i == selected, tall = false, round = true, modifier = Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun MugiAniBox(
    label: String,
    selected: Boolean,
    tall: Boolean,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (round) 18.dp else 8.dp)
    val tint = if (selected) Color.White else MugiPalette.Ivory.copy(alpha = 0.85f)
    Box(
        modifier
            .height(if (tall) 54.dp else 34.dp)
            .clip(shape)
            .background(if (selected) Violet.copy(alpha = 0.42f) else BoxFill)
            .border(1.dp, if (selected) Color(0xFFC79BFF) else Edge, shape)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tall) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(chipIcon(label), null, tint = tint, modifier = Modifier.size(19.dp))
                Text(label, color = tint, fontSize = 11.sp, fontFamily = FontFamily.Serif, maxLines = 1)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(chipIcon(label), null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = tint, fontSize = 12.sp, fontFamily = FontFamily.Serif, maxLines = 1)
            }
        }
    }
}

/** Row title in gold italic serif, its source in small muted serif, and a gold "See all ›". */
@Composable
fun MugiRowHeader(title: String, subtitle: String?, horizontalPadding: androidx.compose.ui.unit.Dp, onSeeAll: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color(0xFFE9D3A0),
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = MugiPalette.Muted, fontSize = 11.5.sp, fontFamily = FontFamily.Serif, maxLines = 1)
            }
        }
        if (onSeeAll != null) {
            Text(
                "See all ›",
                color = Color(0xFFD9B26A),
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "See all", onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
