package com.nuvio.app.features.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fenrir style Home banner: a big rounded card per featured title with a white Play button,
 * round add and info buttons, and a dot strip below. Swipe or wait to move on.
 */
@Composable
fun FenrirHeroCard(
    items: List<MetaPreview>,
    height: Dp,
    onOpen: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
    topBarRoom: Dp = 0.dp,
) {
    if (items.isEmpty()) return
    val scope = rememberCoroutineScope()
    val library by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val pages = remember(items, library) {
        items.mapIndexed { i, item ->
            val saved = LibraryRepository.isSaved(item.id, item.type)
            com.nuvio.app.readmode.FenrirHeroPage(
                key = "${item.type}:${item.id}",
                image = item.banner ?: item.landscapePoster ?: item.poster,
                logo = item.logo?.takeIf { it.isNotBlank() },
                title = item.name,
                tag = "#${i + 1} TRENDING",
                meta = listOfNotNull(
                    item.releaseInfo?.take(4)?.takeIf { it.isNotBlank() },
                    when (item.type) { "series" -> "TV"; "movie" -> "Movie"; "" -> null; else -> item.type.replaceFirstChar { it.uppercase() } },
                    item.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString("  ·  "),
                rating = item.imdbRating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" },
                audio = null,
                onPlay = { onOpen(item) },
                onInfo = { onOpen(item) },
                saved = saved,
                onToggleSave = {
                    scope.launch { runCatching { LibraryRepository.toggleSaved(item.toLibraryItem(savedAtEpochMs = 0L)) } }
                },
            )
        }
    }
    com.nuvio.app.readmode.FenrirBleedHero(pages = pages, height = height, topBarRoom = topBarRoom, modifier = modifier)
}

@Composable
private fun HeroPage(item: MetaPreview, onOpen: (MetaPreview) -> Unit) {
    val scope = rememberCoroutineScope()
    val library by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val saved = remember(library, item.id) { LibraryRepository.isSaved(item.id, item.type) }
    val image = item.poster ?: item.banner ?: item.landscapePoster
    val meta = listOfNotNull(
        item.type.takeIf { it.isNotBlank() }?.let { if (it == "series") "SERIES" else it.uppercase() },
        item.releaseInfo?.take(4),
        item.genres.firstOrNull()?.uppercase(),
    ).joinToString("  ·  ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF20212A))
            .clickable { onOpen(item) },
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        ) {
            val logo = item.logo?.takeIf { it.isNotBlank() }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth(0.72f).height(72.dp),
                )
            } else {
                val mugiTitle = com.nuvio.app.mugi.mugiOn()
                Text(
                    text = item.name.uppercase(),
                    color = if (mugiTitle) Color(0xFFF4E3C1) else Color.White,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    fontWeight = if (mugiTitle) FontWeight.Normal else FontWeight.Black,
                    fontStyle = if (mugiTitle) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    fontFamily = if (mugiTitle) com.nuvio.app.mugi.MugiPlayfair else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = meta,
                    color = Color(0xFFE6E6EA),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            val mugi = com.nuvio.app.mugi.mugiOn()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .width(if (mugi) 132.dp else 160.dp)
                        .clip(RoundedCornerShape(if (mugi) 24.dp else 16.dp))
                        .then(
                            // MugiVerse: red pill with a light rim
                            if (mugi) {
                                Modifier
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFDE3038), Color(0xFF8B1019))))
                                    .border(1.dp, Color(0xFFE8A0A0), RoundedCornerShape(24.dp))
                            } else {
                                Modifier.background(Color.White)
                            },
                        )
                        .clickable { onOpen(item) }
                        .padding(vertical = if (mugi) 10.dp else 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (mugi) Color.White else Color(0xFF0B0B0F)
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Play",
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        fontFamily = if (mugi) com.nuvio.app.mugi.MugiPlayfair else null,
                    )
                }
                Spacer(Modifier.width(10.dp))
                RoundButton(
                    icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    description = if (saved) "Remove from My list" else "Add to My list",
                ) {
                    scope.launch {
                        runCatching { LibraryRepository.toggleSaved(item.toLibraryItem(savedAtEpochMs = 0L)) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                RoundButton(icon = Icons.Rounded.Info, description = "Details") { onOpen(item) }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
