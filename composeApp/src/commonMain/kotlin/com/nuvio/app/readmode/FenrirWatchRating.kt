package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Tap a ★ badge on a Watch poster: the score, where it's from, and reviews from TMDB. */
object FenrirWatchRating {
    val showing = MutableStateFlow<MetaPreview?>(null)
    fun show(item: MetaPreview) { showing.value = item }

    data class Review(val author: String, val rating: Double?, val text: String, val url: String?)
    data class Reviews(val top: Review?, val low: Review?, val total: Int, val tmdbUrl: String?)

    suspend fun reviews(item: MetaPreview): Reviews? {
        val type = if (item.type == "movie") "movie" else "tv"
        val tmdbId = runCatching {
            com.nuvio.app.features.tmdb.TmdbService.ensureTmdbId(item.id, if (type == "movie") "movie" else "series")
        }.getOrNull() ?: return null
        val key = runCatching { com.nuvio.app.features.tmdb.TmdbSettingsRepository.effectiveApiKey() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val text = runCatching {
            com.nuvio.app.features.addons.httpGetText("https://api.themoviedb.org/3/$type/$tmdbId/reviews?api_key=$key&language=en-US&page=1")
        }.getOrNull() ?: return null
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val list = (root["results"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { o ->
            val details = o["author_details"] as? JsonObject
            Review(
                author = (o["author"] as? JsonPrimitive)?.content ?: "a viewer",
                rating = (details?.get("rating") as? JsonPrimitive)?.content?.toDoubleOrNull(),
                text = ((o["content"] as? JsonPrimitive)?.content ?: "").replace(Regex("\\s+"), " ").trim(),
                url = (o["url"] as? JsonPrimitive)?.content,
            )
        }.orEmpty().filter { it.text.isNotBlank() }
        val rated = list.filter { it.rating != null }
        val top = rated.maxByOrNull { it.rating!! } ?: list.firstOrNull()
        val low = rated.minByOrNull { it.rating!! }?.takeIf { it != top && (it.rating ?: 10.0) < (top?.rating ?: 0.0) }
        val total = (root["total_results"] as? JsonPrimitive)?.content?.toIntOrNull() ?: list.size
        return Reviews(top, low, total, "https://www.themoviedb.org/$type/$tmdbId/reviews")
    }
}

private fun short(t: String) = if (t.length <= 260) t else t.take(257).trimEnd() + "…"
private fun one(d: Double) = ((d * 10).toInt() / 10.0).toString()
private fun colorFor(s: Double) = when {
    s >= 8.0 -> Color(0xFF4CD787)
    s >= 6.5 -> Color(0xFFFFB547)
    else -> Color(0xFFFF8A91)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenrirWatchRatingHost() {
    val item by FenrirWatchRating.showing.collectAsStateWithLifecycle()
    val it = item ?: return
    val score = it.imdbRating?.toDoubleOrNull() ?: return
    val uri = LocalUriHandler.current
    var spoilers by remember { mutableStateOf(false) }
    val reviews by produceState<FenrirWatchRating.Reviews?>(null, it.id) { value = FenrirWatchRating.reviews(it) }
    ModalBottomSheet(onDismissRequest = { FenrirWatchRating.showing.value = null }, containerColor = Color(0xFF15151B)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(colorFor(score).copy(alpha = 0.14f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text("★", color = colorFor(score), fontSize = 15.sp)
                    Text(one(score), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("IMDb" + (it.voteCount?.let { v -> " · $v ratings" } ?: ""), color = Color(0xFFB8B8C2), fontSize = 12.5.sp)
                    if (it.genres.isNotEmpty()) Text(it.genres.take(3).joinToString(" · "), color = Color(0xFF9A9AA6), fontSize = 12.sp)
                }
            }
            if (it.id.startsWith("tt")) {
                Text(
                    "Open on IMDb ›",
                    color = Color(0xFFFF4D57),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp).clickable { uri.openUri("https://www.imdb.com/title/${it.id.substringBefore(':')}/") },
                )
            }
            val r = reviews
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("REVIEWS", color = Color(0xFF8E8E99), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Spoilers", color = Color(0xFFC9C9D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Switch(checked = spoilers, onCheckedChange = { s -> spoilers = s }, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF4D57)))
            }
            when {
                r == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFFF4D57))
                }
                r.top == null -> Text("No reviews on TMDB yet.", color = Color(0xFF9A9AA6), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                else -> {
                    WatchReview("MOST POSITIVE REVIEW", Color(0xFF4CD787), r.top, spoilers) { u -> uri.openUri(u) }
                    r.low?.let { low -> WatchReview("LOWEST-RATED REVIEW", Color(0xFFFF8A91), low, spoilers) { u -> uri.openUri(u) } }
                    r.tmdbUrl?.let { u ->
                        Text(
                            "See all ${r.total} reviews on TMDB ›",
                            color = Color(0xFFFF4D57),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp).clickable { uri.openUri(u) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchReview(kind: String, color: Color, rv: FenrirWatchRating.Review, spoilers: Boolean, open: (String) -> Unit) {
    // TMDB doesn't mark spoilers, so reviews stay blurred until you tap or switch Spoilers on.
    var reveal by remember(rv) { mutableStateOf(false) }
    val hide = !spoilers && !reveal
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1B22))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            rv.rating?.let { Text("★ ${one(it)}", color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Box(Modifier.padding(top = 6.dp)) {
            Text("“${short(rv.text)}”", color = Color(0xFFE4E4EA), fontSize = 13.5.sp, modifier = if (hide) Modifier.blur(6.dp) else Modifier)
            if (hide) {
                Text(
                    "Tap to show (may have spoilers)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF26262E))
                        .clickable { reveal = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("— ${rv.author}", color = Color(0xFF8E8E99), fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            rv.url?.let { u -> Text("Read full review ↗", color = Color(0xFFFF4D57), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open(u) }) }
        }
    }
}
