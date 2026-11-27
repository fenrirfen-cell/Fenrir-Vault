package com.nuvio.app.features.anidrama

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val TileShape = RoundedCornerShape(8.dp)

/** A poster with a score/network badge, optional DUB badge, title and a short caption. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AniDramaPosterTile(
    item: AniDramaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDubBadge: Boolean = false,
) {
    // MugiVerse: each poster is a violet-edged card (poster, then score, title, caption and ⋮)
    val mugi = com.nuvio.app.mugi.mugiOn()
    val cardShape = RoundedCornerShape(9.dp)
    Column(
        modifier = modifier.then(
            if (mugi) {
                Modifier
                    .clip(cardShape)
                    .background(Color(0xFF1B1229))
                    .border(1.dp, Color(0xFF5B4385), cardShape)
            } else {
                Modifier
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(if (mugi) RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp) else TileShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            item.badge?.let { badge ->
                PosterBadge(text = badge, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
            }
            item.rank?.let { rank ->
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (showDubBadge && item.hasEnglishDub) {
                PosterBadge(
                    text = "DUB",
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                    accent = true,
                )
            }
        }
        if (mugi) {
            val serif = androidx.compose.ui.text.font.FontFamily.Serif
            Column(Modifier.padding(start = 7.dp, end = 2.dp, top = 5.dp, bottom = 4.dp)) {
                Text(
                    text = item.title,
                    fontFamily = serif,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = com.nuvio.app.mugi.MugiPalette.Ivory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.caption.orEmpty(),
                        fontSize = 10.5.sp,
                        color = Color(0xFFB9A9D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "⋮",
                        color = Color(0xFFB9A9D0),
                        fontSize = 15.sp,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = "Options", onClick = onLongClick).padding(horizontal = 6.dp),
                    )
                }
            }
            return@Column
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PosterBadge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (accent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A rounded, tappable chip. */
@Composable
internal fun AniDramaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Equal-width segmented switch (Anime | K-Drama | C-Drama | J-Drama, or Dub | Sub | Both). */
@Composable
internal fun AniDramaSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(if (compact) Modifier else Modifier.weight(1f))
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(colors.primary) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = if (compact) 10.dp else 4.dp, vertical = if (compact) 4.dp else 7.dp),
            ) {
                Text(
                    text = label,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A titled horizontal row of posters with an optional "See all". */
@Composable
internal fun AniDramaShelf(
    title: String,
    items: List<AniDramaItem>,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    showDubBadge: Boolean = false,
    onSeeAll: (() -> Unit)? = null,
    tileWidth: Dp = 112.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (com.nuvio.app.mugi.mugiOn()) com.nuvio.app.mugi.MugiRowHeader(title, null, 16.dp, onSeeAll) else Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            items.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    AniDramaPosterTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        showDubBadge = showDubBadge,
                        modifier = Modifier.width(tileWidth),
                    )
                }
            }
            loading -> AniDramaLoading(Modifier.fillMaxWidth().height(tileWidth * 1.5f))
            else -> AniDramaMessage("Nothing here right now.", Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
internal fun AniDramaLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
internal fun AniDramaMessage(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Text(
                text = "Try again",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp).clip(CircleShape).clickable(onClick = onRetry).padding(vertical = 4.dp),
            )
        }
    }
}
