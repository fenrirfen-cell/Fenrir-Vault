package com.nuvio.app.features.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.poster.withCustomPosterUrl
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.flow.MutableStateFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_compass
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** MugiVerse "My List": tokens from the approved nautical design. */
internal object MugiList {
    val Background = Color(0xFF04121D)
    val Surface = Color(0xFF0C202B)
    val SurfaceElevated = Color(0xFF102631)
    val Gold = Color(0xFFD9AE62)
    val GoldLight = Color(0xFFF5D891)
    val GoldDark = Color(0xFF8C602B)
    val Ivory = Color(0xFFF5E8CE)
    val Secondary = Color(0xFFAAA49A)
    val MutedBorder = Color(0xFF796343)
    val Burgundy = Color(0xFF641F1C)
    val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFE4BC77), Color(0xFF956029), Color(0xFF694018)))
    val SavedGradient = Brush.verticalGradient(listOf(Color(0xFF792D28), Color(0xFF421715)))
    val CloudGradient = Brush.verticalGradient(listOf(Color(0xFF1F4A5E), Color(0xFF0E2633)))
    val Serif = FontFamily.Serif
}

internal enum class MugiKind(val label: String) { MOVIES("Movies"), SERIES("Series"), ANIME("Anime"), OTHER("More") }

internal fun LibraryItem.mugiKind(): MugiKind {
    val anime = mediaCategory.equals("anime", ignoreCase = true) ||
        genres.any { it.equals("anime", ignoreCase = true) } ||
        id.startsWith("kitsu:") || id.startsWith("mal:") || id.startsWith("anilist:")
    return when {
        anime -> MugiKind.ANIME
        type.equals("movie", ignoreCase = true) -> MugiKind.MOVIES
        type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true) -> MugiKind.SERIES
        else -> MugiKind.OTHER
    }
}

internal enum class MugiSort(val label: String, val legacy: LibrarySortOption?) {
    RECENTLY_ADDED("Recently Added", LibrarySortOption.ADDED_DESC),
    OLDEST_ADDED("Oldest Added", LibrarySortOption.ADDED_ASC),
    TITLE_AZ("Title (A–Z)", LibrarySortOption.TITLE_ASC),
    TITLE_ZA("Title (Z–A)", LibrarySortOption.TITLE_DESC),
    HIGHEST_RATED("Highest Rated", null),
    RELEASE_DATE("Release Date", LibrarySortOption.RELEASED_DESC),
    RECENTLY_WATCHED("Recently Watched", null),
    ;

    companion object {
        fun from(option: LibrarySortOption): MugiSort = when (option) {
            LibrarySortOption.ADDED_ASC -> OLDEST_ADDED
            LibrarySortOption.TITLE_ASC -> TITLE_AZ
            LibrarySortOption.TITLE_DESC -> TITLE_ZA
            LibrarySortOption.RELEASED_DESC, LibrarySortOption.RELEASED_ASC -> RELEASE_DATE
            else -> RECENTLY_ADDED
        }
    }
}

/** The two sorts with no saved-settings equivalent are kept here for the session. */
internal object MugiListState {
    val extraSort = MutableStateFlow<MugiSort?>(null)
}

internal data class MugiListEntry(val item: LibraryItem, val section: LibrarySection)

internal data class MugiListGroup(val kind: MugiKind?, val title: String, val entries: List<MugiListEntry>)

private val leadingArticle = Regex("^(the|a|an)\\s+")
private val nonWord = Regex("[^\\p{L}\\p{N} ]")
private val yearRegex = Regex("(\\d{4})")

private fun normalizedTitle(name: String): String =
    name.lowercase().replace(nonWord, "").trim().replace(leadingArticle, "")

internal fun LibraryItem.mugiRating(): Double? = imdbRating?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 }

internal fun LibraryItem.mugiYear(): Int? = releaseInfo?.let { yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }

/** Saved items: one list (or all lists), filtered by kind and search, sorted, grouped by kind. */
internal fun buildMugiListGroups(
    sections: List<LibrarySection>,
    listKey: String?,
    kind: MugiKind?,
    query: String,
    sort: MugiSort,
    lastWatched: Map<String, Long>,
): List<MugiListGroup> {
    val seen = HashSet<String>()
    val entries = sections
        .filter { listKey == null || it.type == listKey }
        .flatMap { section -> section.items.map { MugiListEntry(it, section) } }
        .filter { seen.add("${it.item.type}|${it.item.id}") }
        .filter { kind == null || it.item.mugiKind() == kind }
        .filter { query.isBlank() || it.item.name.contains(query.trim(), ignoreCase = true) }
    val sorted = when (sort) {
        MugiSort.RECENTLY_ADDED -> entries.sortedByDescending { it.item.savedAtEpochMs }
        MugiSort.OLDEST_ADDED -> entries.sortedBy { it.item.savedAtEpochMs }
        MugiSort.TITLE_AZ -> entries.sortedBy { normalizedTitle(it.item.name) }
        MugiSort.TITLE_ZA -> entries.sortedByDescending { normalizedTitle(it.item.name) }
        MugiSort.HIGHEST_RATED -> entries.sortedWith(compareBy(nullsLast()) { e: MugiListEntry -> e.item.mugiRating()?.let { -it } })
        MugiSort.RELEASE_DATE -> entries.sortedWith(compareBy(nullsLast()) { e: MugiListEntry -> e.item.mugiYear()?.let { -it } })
        MugiSort.RECENTLY_WATCHED -> entries.sortedWith(compareBy(nullsLast()) { e: MugiListEntry -> lastWatched[e.item.id]?.let { -it } })
    }
    if (sorted.isEmpty()) return emptyList()
    if (kind != null) return listOf(MugiListGroup(kind, kind.label, sorted))
    return MugiKind.entries.mapNotNull { k ->
        sorted.filter { it.item.mugiKind() == k }.takeIf { it.isNotEmpty() }?.let { MugiListGroup(k, k.label, it) }
    }
}

/** Deep navy, faint rhumb lines from two opposite corners (old sea-chart style) and a compass watermark. */
@Composable
internal fun MugiListBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(MugiList.Background, Color(0xFF03101A), Color(0xFF020B12))))
                val line = MugiList.Gold.copy(alpha = 0.05f)
                listOf(Offset(0f, size.height * 0.12f), Offset(size.width, size.height * 0.88f)).forEach { c ->
                    for (i in 0 until 16) {
                        val a = i * PI / 8
                        drawLine(line, c, Offset(c.x + cos(a).toFloat() * size.maxDimension, c.y + sin(a).toFloat() * size.maxDimension), 1f)
                    }
                    drawCircle(MugiList.Gold.copy(alpha = 0.06f), radius = 34.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                }
            },
    ) {
        Image(
            painterResource(Res.drawable.mugi_compass),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 60.dp, y = (-40).dp).size(300.dp).graphicsLayer { alpha = 0.06f },
        )
    }
}

/** Four-point compass star, used beside headings. */
@Composable
internal fun MugiStar(modifier: Modifier = Modifier, color: Color = MugiList.Gold) {
    Box(
        modifier.drawBehind {
            val c = center
            val r = size.minDimension / 2
            val k = r * 0.22f
            val p = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + k, c.y - k); lineTo(c.x + r, c.y); lineTo(c.x + k, c.y + k)
                lineTo(c.x, c.y + r); lineTo(c.x - k, c.y + k); lineTo(c.x - r, c.y); lineTo(c.x - k, c.y - k); close()
            }
            drawPath(p, color)
        },
    )
}

/** A gold ship's wheel. */
@Composable
internal fun MugiShipWheel(modifier: Modifier = Modifier, color: Color = MugiList.Gold) {
    Box(
        modifier.drawBehind {
            val c = center
            val r = size.minDimension / 2
            val ring = r * 0.62f
            val stroke = r * 0.12f
            drawCircle(color, radius = ring, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawCircle(color, radius = r * 0.18f, center = c)
            for (i in 0 until 8) {
                val a = i * PI / 4
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(color, c, c + dir * (r * 0.92f), stroke * 0.8f)
                drawCircle(color, radius = r * 0.1f, center = c + dir * (r * 0.92f))
            }
        },
    )
}

@Composable
internal fun MugiGoldIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MugiList.Gold, modifier = Modifier.size(27.dp))
    }
}

/** "My List" with its star, or the inline search field while searching. */
@Composable
internal fun MugiListHeader(
    title: String,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (searchOpen) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(MugiList.Surface.copy(alpha = 0.85f))
                    .border(1.dp, MugiList.Gold.copy(alpha = 0.8f), RoundedCornerShape(23.dp))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MugiList.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search my list", color = MugiList.Secondary, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = MugiList.Ivory, fontSize = 15.sp),
                        cursorBrush = SolidColor(MugiList.Gold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                MugiGoldIconButton(Icons.Rounded.Close, if (query.isEmpty()) "Close search" else "Clear search") {
                    if (query.isEmpty()) onSearchOpenChange(false) else onQueryChange("")
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Done",
                color = MugiList.Gold,
                fontFamily = MugiList.Serif,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    onQueryChange("")
                    onSearchOpenChange(false)
                }.padding(horizontal = 8.dp, vertical = 14.dp),
            )
        } else {
            Box(Modifier.width(2.dp).height(48.dp).background(Brush.verticalGradient(listOf(Color.Transparent, MugiList.Gold, Color.Transparent))))
            MugiStar(Modifier.offset(x = (-7).dp, y = 18.dp).size(12.dp))
            Text(
                title,
                color = MugiList.Ivory,
                fontFamily = MugiList.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            MugiGoldIconButton(Icons.Rounded.Search, "Search my list") { onSearchOpenChange(true) }
            actions()
        }
    }
}

/** Saved | Cloud (| Servers): 42dp pill, thin gold outline, the picked half filled. */
@Composable
internal fun MugiSourceSwitch(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(MugiList.Surface.copy(alpha = 0.6f))
            .border(1.dp, MugiList.Gold.copy(alpha = 0.75f), shape)
            .padding(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "mugiSwitch")
            Box(
                Modifier
                    .widthIn(min = 104.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind {
                        if (a > 0f) {
                            drawRoundRect(
                                if (i == 0) MugiList.SavedGradient else MugiList.CloudGradient,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                alpha = a,
                            )
                            drawRoundRect(
                                MugiList.Gold.copy(alpha = 0.55f * a),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        }
                    }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) MugiList.Ivory else MugiList.Secondary,
                    fontFamily = MugiList.Serif,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A 36dp chip: antique gold when picked, dark see-through otherwise. */
@Composable
internal fun MugiChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "mugiChip")
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(MugiList.Surface.copy(alpha = 0.55f))
            .drawBehind { if (a > 0f) drawRect(MugiList.GoldGradient, alpha = a) }
            .border(1.dp, if (selected) MugiList.GoldLight.copy(alpha = 0.8f) else MugiList.MutedBorder.copy(alpha = 0.8f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = MugiList.Ivory,
            fontFamily = MugiList.Serif,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MugiChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, l -> MugiChip(l, i == selected) { onSelect(i) } }
    }
}

/** "Recently Added ▾" with its menu right below it. */
@Composable
internal fun MugiSortDropdown(selected: MugiSort, onSelect: (MugiSort) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(if (open) 200 else 150), label = "mugiSortChevron")
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Row(
            Modifier
                .height(42.dp)
                .clip(shape)
                .background(MugiList.Surface.copy(alpha = 0.6f))
                .border(1.dp, MugiList.Gold.copy(alpha = 0.85f), shape)
                .clickable(onClickLabel = "Sort") { open = true }
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, color = MugiList.Ivory, fontFamily = MugiList.Serif, fontSize = 16.sp, maxLines = 1)
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MugiList.Ivory, modifier = Modifier.size(24.dp).rotate(turn))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(240.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MugiList.SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, MugiList.Gold.copy(alpha = 0.8f)),
        ) {
            MugiSort.entries.forEachIndexed { i, option ->
                val on = option == selected
                DropdownMenuItem(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .then(
                            if (on) {
                                Modifier.background(
                                    Brush.horizontalGradient(listOf(MugiList.Gold.copy(alpha = 0.42f), MugiList.GoldDark.copy(alpha = 0.22f))),
                                )
                            } else {
                                Modifier
                            },
                        ),
                    text = {
                        Text(option.label, color = MugiList.Ivory, fontFamily = MugiList.Serif, fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    trailingIcon = if (on) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MugiList.Gold) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
                if (i < MugiSort.entries.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(MugiList.MutedBorder.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

/** Gold icon, ivory serif title, thin gold line to the edge. */
@Composable
internal fun MugiSectionHeading(title: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit = { MugiShipWheel(Modifier.size(28.dp)) }) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(title, color = MugiList.Ivory, fontFamily = MugiList.Serif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MugiList.Gold.copy(alpha = 0.7f), MugiList.Gold.copy(alpha = 0.1f)))))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MugiPosterCard(
    entry: MugiListEntry,
    preview: MetaPreview,
    watched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "mugiPress")
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MugiList.Surface)
                .border(1.dp, MugiList.Gold.copy(alpha = 0.9f), shape)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        ) {
            val url = preview.poster ?: preview.rawPosterUrl
            if (url != null) {
                AsyncImage(model = url, contentDescription = entry.item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    entry.item.name,
                    color = MugiList.Secondary,
                    fontFamily = MugiList.Serif,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            entry.item.mugiRating()?.let { r ->
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD9081219))
                        .border(1.dp, MugiList.Gold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MugiList.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(((r * 10).toInt() / 10.0).toString(), color = MugiList.Ivory, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xD9081219)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Watched", tint = MugiList.Gold, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.item.name,
            color = MugiList.Ivory,
            fontFamily = MugiList.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.item.releaseInfo?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MugiList.Secondary, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MugiListRow(
    entry: MugiListEntry,
    preview: MetaPreview,
    watched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MugiList.Surface.copy(alpha = 0.7f))
            .border(1.dp, MugiList.MutedBorder.copy(alpha = 0.6f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(58.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MugiList.SurfaceElevated).border(1.dp, MugiList.Gold.copy(alpha = 0.8f), RoundedCornerShape(8.dp))) {
            (preview.poster ?: preview.rawPosterUrl)?.let {
                AsyncImage(model = it, contentDescription = entry.item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.item.name, color = MugiList.Ivory, fontFamily = MugiList.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.item.releaseInfo?.takeIf { it.isNotBlank() }?.let { Text(it, color = MugiList.Secondary, fontSize = 12.sp) }
                entry.item.mugiRating()?.let { r ->
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MugiList.Gold, modifier = Modifier.size(13.dp))
                    Text(((r * 10).toInt() / 10.0).toString(), color = MugiList.Ivory, fontSize = 12.sp)
                }
                if (watched) {
                    Spacer(Modifier.width(10.dp))
                    Text("Watched", color = MugiList.Gold, fontSize = 12.sp)
                }
            }
        }
    }
}

/** The saved items in sections, as a poster grid or a list. */
internal fun LazyListScope.mugiLibraryContent(
    groups: List<MugiListGroup>,
    query: String,
    grid: Boolean,
    columns: Int,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    posterPattern: String,
    onPosterClick: ((LibraryItem) -> Unit)?,
    onPosterLongClick: ((LibraryItem, LibrarySection) -> Unit)?,
) {
    if (groups.isEmpty()) {
        item(key = "mugi-list-empty") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MugiShipWheel(Modifier.size(40.dp), MugiList.Gold.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (query.isNotBlank()) "Nothing here matches “${query.trim()}”" else "Nothing here yet",
                    color = MugiList.Ivory,
                    fontFamily = MugiList.Serif,
                    fontSize = 18.sp,
                )
                Text(
                    if (query.isNotBlank()) "Try another title or clear the filters." else "Try another filter, or save something to watch later.",
                    color = MugiList.Secondary,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }
    groups.forEach { group ->
        item(key = "mugi-head-${group.title}") {
            MugiSectionHeading(group.title, Modifier.padding(horizontal = 16.dp).animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(150)))
        }
        val rows = if (grid) group.entries.chunked(columns) else group.entries.map { listOf(it) }
        items(rows, key = { row -> "mugi-${if (grid) "g" else "l"}-${group.title}-${row.first().item.type}-${row.first().item.id}" }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (grid) 14.dp else 8.dp)
                    .animateItem(fadeInSpec = tween(220), placementSpec = tween(220), fadeOutSpec = tween(150)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { entry ->
                    val preview = remember(entry, posterPattern) {
                        entry.item.toMetaPreview().let { if (posterPattern.isNotBlank()) it.withCustomPosterUrl(posterPattern) else it }
                    }
                    val watched = WatchingState.isPosterWatched(watchedKeys = watchedKeys, item = preview, fullyWatchedSeriesKeys = fullyWatchedSeriesKeys)
                    val click = { onPosterClick?.invoke(entry.item); Unit }
                    val long = { onPosterLongClick?.invoke(entry.item, entry.section); Unit }
                    if (grid) {
                        MugiPosterCard(entry, preview, watched, click, long, Modifier.weight(1f))
                    } else {
                        MugiListRow(entry, preview, watched, click, long, Modifier.weight(1f))
                    }
                }
                if (grid) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
