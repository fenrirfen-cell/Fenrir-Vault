package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val Gold = Color(0xFFF5C518)
private val HomeRed = Color(0xFFFF4D57)
private val MutedText = Color(0xFF8C8C98)

/** Fenrir Home: a row title, a small grey "where it's from" line, and a round › for See all. */
@Composable
fun FenrirRowHeader(title: String, subtitle: String?, horizontalPadding: Dp, onSeeAll: (() -> Unit)?) {
    if (com.nuvio.app.mugi.mugiOn()) {
        com.nuvio.app.mugi.MugiRowHeader(title, subtitle, horizontalPadding, onSeeAll)
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onSeeAll != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E1E26)).clickable(onClick = onSeeAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "See all", tint = Color(0xFFDCDCE4), modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Small gold "★ 8.7" chip for posters. */
@Composable
fun FenrirRatingChip(rating: String, modifier: Modifier = Modifier) {
    Text(
        "★ $rating",
        color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xD9000000)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** SUB / DUB chip for posters: red when dubbed. */
@Composable
fun FenrirAudioChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(if ("DUB" in text) HomeRed else Color(0xD9202028))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** One entry of the Top 10 row. */
class FenrirTopEntry(val key: String, val title: String, val image: String?, val onClick: () -> Unit)

/** "Top 10" row: posters with big outlined numbers beside them. */
@Composable
fun FenrirTop10Row(title: String, subtitle: String?, entries: List<FenrirTopEntry>, horizontalPadding: Dp) {
    if (entries.size < 3) return
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        FenrirRowHeader(title, subtitle, horizontalPadding, onSeeAll = null)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(start = (horizontalPadding - 6.dp).coerceAtLeast(0.dp), end = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(entries.take(10), key = { _, e -> e.key }) { index, e ->
                Row(Modifier.clickable(onClick = e.onClick), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${index + 1}",
                        style = TextStyle(
                            color = Color(0xFFA0A0B4),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black,
                            drawStyle = Stroke(width = 4f),
                            lineHeight = 96.sp,
                        ),
                        modifier = Modifier.offset(x = 14.dp, y = 14.dp),
                    )
                    Box(
                        Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C24)),
                    ) {
                        if (!e.image.isNullOrBlank()) {
                            AsyncImage(model = e.image, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lets a full-width banner start at the very top of the screen, behind the FENRIR WATCH bar,
 * while the rest of the page keeps its place.
 */
fun Modifier.fenrirUnderTopBar(shift: Dp): Modifier = this.layout { measurable, constraints ->
    val s = shift.roundToPx()
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - s).coerceAtLeast(0)) { p.place(0, -s) }
}

/** One page of the featured banner. */
class FenrirHeroPage(
    val key: String,
    val image: String?,
    val logo: String?,
    val title: String,
    val tag: String?,
    val meta: String?,
    val rating: String?,
    val audio: String?,
    val onPlay: () -> Unit,
    val onInfo: () -> Unit,
    val saved: Boolean? = null,
    val onToggleSave: (() -> Unit)? = null,
)

/**
 * Fenrir Home's featured banner: full width from the top of the screen, the picture fading into
 * the page, with rank, title, details, rating, a white Play button and round My list / Info.
 * Swipe or wait to move on.
 */
@Composable
fun FenrirBleedHero(pages: List<FenrirHeroPage>, height: Dp, topBarRoom: Dp, modifier: Modifier = Modifier) {
    if (pages.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
    androidx.compose.runtime.LaunchedEffect(pagerState, pages.size) {
        if (pages.size <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(7_000)
            if (!pagerState.isScrollInProgress) pagerState.animateScrollToPage((pagerState.currentPage + 1) % pages.size)
        }
    }
    val bg = androidx.compose.material3.MaterialTheme.colorScheme.background
    Column(modifier.fillMaxWidth().fenrirUnderTopBar(topBarRoom)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(height + topBarRoom),
            key = { pages[it].key },
        ) { i ->
            val p = pages[i]
            Box(Modifier.fillMaxSize().clickable(onClick = p.onInfo)) {
                if (!p.image.isNullOrBlank()) {
                    AsyncImage(model = p.image, contentDescription = p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                // dark at the top (behind the bar) and fading into the page at the bottom
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to bg.copy(alpha = 0.75f),
                            0.22f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.82f to bg.copy(alpha = 0.85f),
                            1f to bg,
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                    if (!p.tag.isNullOrBlank()) {
                        Text(
                            p.tag, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HomeRed).padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!p.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = p.logo, contentDescription = p.title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxWidth(0.7f).height(70.dp),
                        )
                    } else {
                        Text(p.title, color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (!p.meta.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(p.meta, color = Color(0xFFCDCDD7), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (p.rating != null || p.audio != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            p.rating?.let {
                                Text(
                                    "★ $it", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Gold).padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            p.audio?.let { Text(it, color = Color(0xFFCDCDD7), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.height(46.dp).width(180.dp).clip(RoundedCornerShape(23.dp)).background(Color.White).clickable(onClick = p.onPlay),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF0C0C10), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", color = Color(0xFF0C0C10), fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                        }
                        if (p.onToggleSave != null) {
                            Spacer(Modifier.width(10.dp))
                            HeroRoundButton(
                                if (p.saved == true) androidx.compose.material.icons.Icons.Rounded.Check else androidx.compose.material.icons.Icons.Rounded.Add,
                                if (p.saved == true) "Remove from My list" else "Add to My list",
                                p.onToggleSave,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        HeroRoundButton(androidx.compose.material.icons.Icons.Rounded.Info, "Details", p.onInfo)
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (on) 7.dp else 6.dp).clip(CircleShape)
                            .background(if (on) Color.White else Color(0xFF5A5A66)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0x99282832))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
