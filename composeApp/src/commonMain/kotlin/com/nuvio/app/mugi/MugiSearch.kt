package com.nuvio.app.mugi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_parchment
import org.jetbrains.compose.resources.painterResource

/** MugiVerse Search (Watch in red and navy, Ani-Drama in violet). */
object MugiScreenColors {
    val Gold = Color(0xFFD4AF65)
    val BrightGold = Color(0xFFFFD478)
    val DarkGold = Color(0xFFA87832)
    val Ivory = Color(0xFFF5E8D0)
    val Secondary = Color(0xFFBDAE98)
    val WatchBackground = Color(0xFF06141E)
    val WatchSurface = Color(0xFF101D27)
    val Red = Color(0xFFAB2028)
    val DarkRed = Color(0xFF591219)
    val Purple = Color(0xFF7732B5)
    val DarkPurple = Color(0xFF3A1659)
    val AniBackground = Color(0xFF21102F)
    val AniSurface = Color(0xFF1B1127)
}

/** True while Watch is in Ani-Drama mode (set by the main tabs), so shared screens pick violet. */
object MugiAniMode {
    val on = androidx.compose.runtime.mutableStateOf(false)
}

/** Navy (or violet) with a sail of red (or violet) cloth and a rope across the top right. */
@Composable
fun MugiSearchBackground(ani: Boolean, modifier: Modifier = Modifier) {
    val c = MugiScreenColors
    val top = if (ani) c.AniBackground else Color(0xFF0B1E2C)
    val mid = if (ani) Color(0xFF150B1F) else c.WatchBackground
    val cloth = if (ani) listOf(Color(0xCC7732B5), Color(0x663A1659)) else listOf(Color(0xCCAB2028), Color(0x66591219))
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(top, mid, Color(0xFF03090E))))
                val w = size.width
                val h = 190.dp.toPx()
                // a rope from the top edge down to the right
                drawLine(Color(0x66A87832), Offset(w * 0.48f, 0f), Offset(w, h * 0.95f), 2.dp.toPx(), cap = StrokeCap.Round)
                // the sailcloth, billowing from the rope
                val sail = Path().apply {
                    moveTo(w * 0.62f, h * 0.05f)
                    quadraticTo(w * 0.86f, h * 0.02f, w, h * 0.10f)
                    lineTo(w, h * 0.62f)
                    quadraticTo(w * 0.90f, h * 0.48f, w * 0.80f, h * 0.56f)
                    quadraticTo(w * 0.74f, h * 0.30f, w * 0.62f, h * 0.05f)
                    close()
                }
                drawPath(sail, Brush.linearGradient(cloth, start = Offset(w * 0.62f, 0f), end = Offset(w, h * 0.6f)), alpha = 0.55f)
                drawPath(sail, Color(0x33FFD478), style = Stroke(1f))
            },
    )
}

/** "Search" in gold serif. */
@Composable
fun MugiSearchTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        color = Color(0xFFF2D592),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Red (or violet) search field with a gold edge. */
@Composable
fun MugiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    ani: Boolean,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val c = MugiScreenColors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Brush.verticalGradient(if (ani) listOf(Color(0xFF4A1D70), c.DarkPurple) else listOf(Color(0xFF6E1820), c.DarkRed)))
            .border(1.5.dp, c.Gold, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.Gold, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = c.Secondary, fontFamily = FontFamily.Serif, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.Ivory, fontSize = 17.sp, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(c.BrightGold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Clear search") { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = c.Gold)
            }
        }
    }
}

/** "Discover" written on the parchment, with a rope running behind it. */
@Composable
fun MugiDiscoverBanner(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .drawBehind {
                    // twisted rope: a brown core with diagonal strands
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB48A52), Color(0xFF6E4A22))), cornerRadius = CornerRadius(4.dp.toPx()))
                    val step = 6.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(Color(0x88402A10), Offset(x, size.height), Offset(x + step * 0.8f, 0f), 1.2f)
                        x += step
                    }
                },
        )
        Box(Modifier.widthIn(min = 190.dp).height(54.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(Res.drawable.mugi_parchment),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                title,
                color = Color(0xFF38200F),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 34.dp),
            )
        }
    }
}

/** Movies | TV Shows (or the Ani-Drama types): one gold-edged capsule, the picked part filled. */
@Composable
fun MugiSegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    selectedColor: Color,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    surface: Color = MugiScreenColors.WatchSurface,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .height(44.dp)
            .clip(shape)
            .background(surface)
            .border(1.dp, MugiScreenColors.Gold, shape),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val a by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "mugiSeg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        if (a > 0f) {
                            drawRect(Brush.verticalGradient(listOf(selectedColor, selectedColor.copy(alpha = 0.75f))), alpha = a)
                        }
                    }
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelected(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = MugiScreenColors.Ivory, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/** "Cinemeta • Movies" in gold italic serif with a thin line. */
@Composable
fun MugiProviderLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = MugiScreenColors.Gold,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MugiScreenColors.Gold.copy(alpha = 0.8f), Color.Transparent))))
    }
}
