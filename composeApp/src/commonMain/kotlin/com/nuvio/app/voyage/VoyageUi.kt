package com.nuvio.app.voyage

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_parchment
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val VoyageSerif = FontFamily.Serif

/** Navy sea or walnut cabin, with faint chart lines. */
fun Modifier.voyageBackground(walnut: Boolean): Modifier = drawBehind {
    val colors = if (walnut) {
        listOf(Color(0xFF2A190D), VoyageColors.Walnut, Color(0xFF120A05))
    } else {
        listOf(Color(0xFF0B1E2C), VoyageColors.Navy, Color(0xFF03090E))
    }
    drawRect(Brush.verticalGradient(colors))
    if (walnut) {
        // wood grain
        var y = 0f
        var i = 0
        while (y < size.height) {
            drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y + 6f), 2f)
            y += 22f + (i % 3) * 9f
            i++
        }
    }
    val line = VoyageColors.Gold.copy(alpha = 0.045f)
    listOf(Offset(size.width, size.height * 0.08f), Offset(0f, size.height * 0.92f)).forEach { c ->
        for (k in 0 until 16) {
            val a = k * PI / 8
            drawLine(line, c, Offset(c.x + cos(a).toFloat() * size.maxDimension, c.y + sin(a).toFloat() * size.maxDimension), 1f)
        }
    }
}

/** A small sailing ship (hull, mast, two sails, pennant), drawn so it scales cleanly. */
@Composable
fun VoyageShip(modifier: Modifier = Modifier, sail: Color = Color(0xFFF2E6CC), hull: Color = Color(0xFF6B3E1C)) {
    Box(modifier.drawBehind { drawShip(sail, hull) })
}

fun DrawScope.drawShip(sail: Color, hull: Color) {
    val w = size.width
    val h = size.height
    val hullPath = Path().apply {
        moveTo(w * 0.05f, h * 0.70f)
        lineTo(w * 0.95f, h * 0.70f)
        lineTo(w * 0.80f, h * 0.92f)
        lineTo(w * 0.20f, h * 0.92f)
        close()
    }
    drawPath(hullPath, hull)
    drawLine(Color(0xFFD4AF65), Offset(w * 0.12f, h * 0.76f), Offset(w * 0.88f, h * 0.76f), h * 0.03f)
    drawLine(Color(0xFF4A2A12), Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.70f), w * 0.04f)
    val big = Path().apply {
        moveTo(w * 0.53f, h * 0.14f)
        quadraticTo(w * 0.86f, h * 0.36f, w * 0.53f, h * 0.62f)
        close()
    }
    drawPath(big, sail)
    val small = Path().apply {
        moveTo(w * 0.47f, h * 0.22f)
        quadraticTo(w * 0.20f, h * 0.42f, w * 0.47f, h * 0.62f)
        close()
    }
    drawPath(small, sail.copy(alpha = 0.92f))
    val flag = Path().apply {
        moveTo(w * 0.5f, h * 0.06f)
        lineTo(w * 0.66f, h * 0.10f)
        lineTo(w * 0.5f, h * 0.14f)
        close()
    }
    drawPath(flag, Color(0xFFC8323A))
}

/** Gold serif screen title with a back arrow and an optional picture on the right. */
@Composable
fun VoyageHeader(title: String, onBack: () -> Unit, subtitle: String? = null, decoration: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        decoration?.let { Box(Modifier.align(Alignment.TopEnd).padding(end = 8.dp)) { it() } }
        Column(Modifier.padding(horizontal = 8.dp)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = VoyageColors.Gold)
            }
            Text(
                title,
                color = Color(0xFFF2D592),
                fontFamily = VoyageSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(Modifier.padding(start = 8.dp, top = 2.dp).width(110.dp).height(1.dp).background(Brush.horizontalGradient(listOf(VoyageColors.Gold, Color.Transparent))))
            subtitle?.let {
                Text(it, color = VoyageColors.Secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
            }
        }
    }
}

/** Italic serif heading with a fading gold line. */
@Composable
fun VoyageSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(VoyageColors.Gold, Color.Transparent))))
    }
}

/** A frame of twisted rope (brown strands with a gold inner line). */
fun Modifier.ropeFrame(radius: Dp = 14.dp): Modifier = drawBehind {
    val r = radius.toPx()
    val w = 5.dp.toPx()
    drawRoundRect(Color(0xFF8A6234), cornerRadius = CornerRadius(r), style = Stroke(w))
    drawRoundRect(
        Color(0xFF3E2610),
        cornerRadius = CornerRadius(r),
        style = Stroke(w * 0.55f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f)),
    )
    val inset = w * 0.9f
    drawRoundRect(
        VoyageColors.Gold.copy(alpha = 0.75f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((r - inset).coerceAtLeast(2f)),
        style = Stroke(1f),
    )
}

/** Wooden panel with brass corner studs. */
fun Modifier.woodPanel(radius: Dp = 12.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(Color(0xFF3A2412), Color(0xFF28190F), Color(0xFF1C110A))))
    .border(1.dp, Color(0xFFA87832), RoundedCornerShape(radius))
    .drawBehind {
        val s = 2.5.dp.toPx()
        val m = 7.dp.toPx()
        listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))
            .forEach { drawCircle(VoyageColors.Gold, s, it) }
    }

/** Progress line with a little ship sailing along it. */
@Composable
fun VoyageProgressBar(progress: Float, mode: VoyageMode, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = "${(value * 100).toInt()}% completed" },
    ) {
        val shipWidth = 24.dp
        val travel = (maxWidth - shipWidth).coerceAtLeast(0.dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(mode.accent()))
        }
        VoyageShip(Modifier.align(Alignment.BottomStart).offset(x = travel * value, y = (-3).dp).size(shipWidth))
    }
}

/** A row of coloured pills (All gold, Watch red, Ani-Drama violet, Read gold). */
@Composable
fun VoyageModeChips(selected: VoyageMode, onSelect: (VoyageMode) -> Unit, modifier: Modifier = Modifier, modes: List<VoyageMode> = VoyageMode.entries) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { m -> VoyagePill(m.label, m == selected, m.accent()) { onSelect(m) } }
    }
}

@Composable
fun VoyagePill(label: String, selected: Boolean, accent: Color = VoyageColors.Gold, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "voyagePill")
    Box(
        modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 56.dp)
            .clip(shape)
            .background(Color(0x66000000))
            .drawBehind { if (a > 0f) drawRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f))), alpha = a) }
            .border(1.dp, accent.copy(alpha = if (selected) 1f else 0.85f), shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected && accent == VoyageColors.Gold) Color(0xFF2B1A0C) else VoyageColors.Ivory,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** Gold-edged button (filled red, gold or violet). */
@Composable
fun VoyageButton(text: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.62f, green = color.green * 0.62f, blue = color.blue * 0.62f))))
            .border(1.dp, VoyageColors.Gold, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (color == VoyageColors.Gold || color == VoyageColors.Read) Color(0xFF2B1A0C) else VoyageColors.Ivory,
            fontFamily = VoyageSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/** Parchment panel (the approved parchment artwork, stretched). */
@Composable
fun ParchmentPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        Image(painterResource(Res.drawable.mugi_parchment), null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        content()
    }
}

@Composable
fun VoyageEmpty(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VoyageShip(Modifier.size(56.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, color = VoyageColors.Secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/* ───────────── wanted poster ───────────── */

@Composable
fun WantedPoster(progress: BountyProgress, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val earned = progress.unlocked
    val paper = if (earned) listOf(Color(0xFFF0DCAE), Color(0xFFCDAA6C)) else listOf(Color(0xFFB9A88A), Color(0xFF8E7D61))
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(paper))
            .border(if (earned) 2.dp else 1.dp, if (earned) VoyageColors.Gold else Color(0xFF5E4A30), RoundedCornerShape(4.dp))
            .clickable(onClickLabel = progress.definition.name, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("WANTED", color = Color(0xFF5A1A14), fontFamily = VoyageSerif, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.5.sp)
        Box(
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (earned) Color(0xFF2A1C10) else Color(0xFF5E513E))
                .border(1.dp, Color(0xFF4A3420), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BountyEmblemArt(progress.definition.emblem, earned, Modifier.size(54.dp))
            if (!earned) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(18.dp).clip(CircleShape).background(Color(0xCC2A1C10)), contentAlignment = Alignment.Center) {
                    Text("🔒", fontSize = 10.sp)
                }
            }
        }
        Text(progress.definition.name, color = Color(0xFF2E180A), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
        Text(progress.definition.description, color = Color(0xFF4A2E16), fontSize = 9.5.sp, lineHeight = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        if (earned) {
            Text(
                "EARNED",
                color = Color(0xFFFFE7B0),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(Color(0xFF8E1C22)).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        } else {
            Text("${progress.current.coerceAtMost(progress.definition.target)} / ${progress.definition.target}", color = Color(0xFF3A2210), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x553A2210))) {
                Box(Modifier.fillMaxWidth(progress.progress).fillMaxHeight().background(Color(0xFF8A5A1E)))
            }
        }
    }
}

/** The little picture on each poster, drawn in code (sepia when still locked). */
@Composable
fun BountyEmblemArt(emblem: BountyEmblem, earned: Boolean, modifier: Modifier = Modifier) {
    val gold = if (earned) Color(0xFFE7C06A) else Color(0xFFB8A27A)
    val red = if (earned) Color(0xFFB8333B) else Color(0xFF8E7A62)
    val blue = if (earned) Color(0xFF5B8FC0) else Color(0xFF8C826E)
    val ivory = if (earned) Color(0xFFF2E6CC) else Color(0xFFCFC3A8)
    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            when (emblem) {
                BountyEmblem.BOOKS, BountyEmblem.LIBRARY -> {
                    val n = if (emblem == BountyEmblem.LIBRARY) 5 else 3
                    val colors = listOf(red, blue, gold, red, blue)
                    for (i in 0 until n) {
                        val bw = w / (n + 1.2f)
                        val x = w * 0.08f + i * bw * 1.05f
                        val top = h * (0.18f + (i % 2) * 0.08f)
                        drawRoundRect(colors[i], Offset(x, top), Size(bw * 0.9f, h * 0.78f - top), CornerRadius(2f))
                        drawLine(gold, Offset(x, top + h * 0.1f), Offset(x + bw * 0.9f, top + h * 0.1f), 1.5f)
                    }
                    drawLine(gold, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), 3f)
                }
                BountyEmblem.FILM -> {
                    drawRoundRect(Color(0xFF2A2A2A), Offset(w * 0.12f, h * 0.38f), Size(w * 0.76f, h * 0.44f), CornerRadius(3f))
                    val clap = Path().apply {
                        moveTo(w * 0.12f, h * 0.36f); lineTo(w * 0.86f, h * 0.20f); lineTo(w * 0.88f, h * 0.30f); lineTo(w * 0.14f, h * 0.46f); close()
                    }
                    drawPath(clap, ivory)
                    for (i in 0 until 4) drawLine(Color(0xFF2A2A2A), Offset(w * (0.22f + i * 0.17f), h * 0.42f), Offset(w * (0.28f + i * 0.17f), h * 0.26f), 3f)
                    drawCircle(gold, w * 0.07f, Offset(w * 0.5f, h * 0.6f))
                }
                BountyEmblem.SHIP -> drawShip(ivory, red.copy(red = red.red * 0.6f))
                BountyEmblem.ANIME -> {
                    drawCircle(ivory, w * 0.3f, Offset(w * 0.5f, h * 0.42f))
                    drawCircle(blue, w * 0.07f, Offset(w * 0.4f, h * 0.42f))
                    drawCircle(blue, w * 0.07f, Offset(w * 0.6f, h * 0.42f))
                    val hair = Path().apply {
                        moveTo(w * 0.2f, h * 0.4f); lineTo(w * 0.3f, h * 0.08f); lineTo(w * 0.42f, h * 0.22f); lineTo(w * 0.55f, h * 0.04f)
                        lineTo(w * 0.62f, h * 0.2f); lineTo(w * 0.8f, h * 0.1f); lineTo(w * 0.8f, h * 0.4f); close()
                    }
                    drawPath(hair, red)
                    drawLine(red, Offset(w * 0.44f, h * 0.58f), Offset(w * 0.56f, h * 0.58f), 2f, cap = StrokeCap.Round)
                }
                BountyEmblem.GLOBE -> {
                    drawCircle(blue, w * 0.36f, Offset(w * 0.5f, h * 0.5f))
                    drawCircle(gold, w * 0.36f, Offset(w * 0.5f, h * 0.5f), style = Stroke(2f))
                    drawOval(gold, Offset(w * 0.32f, h * 0.14f), Size(w * 0.36f, h * 0.72f), style = Stroke(1.5f))
                    drawLine(gold, Offset(w * 0.14f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), 1.5f)
                }
                BountyEmblem.COMPASS -> {
                    val c = Offset(w / 2, h / 2)
                    drawCircle(gold, w * 0.38f, c, style = Stroke(2.5f))
                    val r = w * 0.34f
                    val k = r * 0.22f
                    val star = Path().apply {
                        moveTo(c.x, c.y - r); lineTo(c.x + k, c.y - k); lineTo(c.x + r, c.y); lineTo(c.x + k, c.y + k)
                        lineTo(c.x, c.y + r); lineTo(c.x - k, c.y + k); lineTo(c.x - r, c.y); lineTo(c.x - k, c.y - k); close()
                    }
                    drawPath(star, gold)
                    drawLine(red, c, Offset(c.x, c.y - r * 0.9f), 3f)
                }
                BountyEmblem.CALENDAR -> {
                    drawRoundRect(ivory, Offset(w * 0.16f, h * 0.2f), Size(w * 0.68f, h * 0.62f), CornerRadius(4f))
                    drawRect(red, Offset(w * 0.16f, h * 0.2f), Size(w * 0.68f, h * 0.15f))
                    for (r in 0 until 3) for (c in 0 until 4) {
                        drawCircle(if (r * 4 + c < 9) gold else Color(0xFF8E7A62), w * 0.035f, Offset(w * (0.27f + c * 0.15f), h * (0.47f + r * 0.11f)))
                    }
                }
                BountyEmblem.CHEST -> drawChest(this, gold, lidOpen = false)
            }
        },
    )
}

private fun drawChest(scope: DrawScope, gold: Color, lidOpen: Boolean) = with(scope) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF6B3E1C), Offset(w * 0.12f, h * 0.45f), Size(w * 0.76f, h * 0.38f), CornerRadius(3f))
    drawRoundRect(Color(0xFF7E4A22), Offset(w * 0.12f, h * 0.24f), Size(w * 0.76f, h * 0.22f), CornerRadius(8f))
    drawLine(gold, Offset(w * 0.12f, h * 0.46f), Offset(w * 0.88f, h * 0.46f), 2.5f)
    drawLine(gold, Offset(w * 0.3f, h * 0.24f), Offset(w * 0.3f, h * 0.83f), 2f)
    drawLine(gold, Offset(w * 0.7f, h * 0.24f), Offset(w * 0.7f, h * 0.83f), 2f)
    drawRoundRect(gold, Offset(w * 0.44f, h * 0.42f), Size(w * 0.12f, h * 0.14f), CornerRadius(2f))
}

/* ───────────── treasure chest ───────────── */

/**
 * The chest: body, then a lid that swings open on its back hinge (rotationX around the lid's
 * bottom edge), a warm glow inside. Taps are ignored while it is opening or open.
 */
@Composable
fun SurpriseChestAnimation(opened: Boolean, reduceMotion: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press by animateFloatAsState(if (opened) 1f else 0f, tween(if (reduceMotion) 0 else 150), label = "chestPress")
    val rotation by animateFloatAsState(
        if (opened) -75f else 0f,
        tween(if (reduceMotion) 0 else 300, delayMillis = if (reduceMotion) 0 else 150, easing = FastOutSlowInEasing),
        label = "chestLid",
    )
    val glow by animateFloatAsState(if (opened) 1f else 0f, tween(if (reduceMotion) 0 else 300, delayMillis = if (reduceMotion) 0 else 450), label = "chestGlow")
    val squash = if (opened && press < 1f) 1f - 0.04f * (1f - press) else 1f
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(enabled = !opened, onClickLabel = "Open the chest", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // warm light
        Box(
            Modifier
                .size(220.dp)
                .graphicsLayer { alpha = glow }
                .background(Brush.radialGradient(listOf(VoyageColors.BrightGold.copy(alpha = 0.45f), Color.Transparent)), CircleShape),
        )
        Box(Modifier.size(width = 210.dp, height = 170.dp).graphicsLayer { scaleY = squash; scaleX = 2f - squash }) {
            // body
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 200.dp, height = 96.dp)
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        // inside of the chest (seen when open)
                        drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE08A).copy(alpha = glow), Color(0xFF3A2210))), Offset(0f, 0f), Size(w, h * 0.25f))
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8A5228), Color(0xFF5A3214))), cornerRadius = CornerRadius(8f))
                        // planks
                        for (i in 1..3) drawLine(Color(0x55000000), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 2f)
                        // gold bands and corners
                        listOf(0.18f, 0.82f).forEach { x -> drawRect(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832))), Offset(w * x - 7f, 0f), Size(14f, h)) }
                        drawRect(Brush.horizontalGradient(listOf(Color(0xFFA87832), Color(0xFFF6D88E), Color(0xFFA87832))), Offset(0f, 0f), Size(w, 8f))
                        // anchor lock plate
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832))), Offset(w * 0.42f, -h * 0.05f), Size(w * 0.16f, h * 0.42f), CornerRadius(6f))
                        val c = Offset(w * 0.5f, h * 0.18f)
                        drawLine(Color(0xFF5A3214), Offset(c.x, c.y - 10f), Offset(c.x, c.y + 14f), 3f)
                        drawLine(Color(0xFF5A3214), Offset(c.x - 9f, c.y - 4f), Offset(c.x + 9f, c.y - 4f), 3f)
                        drawArc(Color(0xFF5A3214), 20f, 140f, false, Offset(c.x - 12f, c.y), Size(24f, 18f), style = Stroke(3f))
                    },
            )
            // lid, hinged at its bottom edge (the back of the chest)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-94).dp)
                    .size(width = 204.dp, height = 70.dp)
                    .graphicsLayer {
                        rotationX = rotation
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 24f * density
                    }
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val dome = Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, h * 0.45f)
                            quadraticTo(w * 0.5f, -h * 0.35f, w, h * 0.45f)
                            lineTo(w, h)
                            close()
                        }
                        drawPath(dome, Brush.verticalGradient(listOf(Color(0xFF9A5E2E), Color(0xFF6A3A18))))
                        listOf(0.18f, 0.82f).forEach { x -> drawRect(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832))), Offset(w * x - 7f, h * 0.12f), Size(14f, h * 0.88f)) }
                        drawRect(Brush.horizontalGradient(listOf(Color(0xFFA87832), Color(0xFFF6D88E), Color(0xFFA87832))), Offset(0f, h - 8f), Size(w, 8f))
                        drawPath(dome, Color(0xFFD4AF65), style = Stroke(2f))
                    },
            )
        }
    }
}

/** Small chest button for headers and the Discover banner. */
@Composable
fun SurpriseChestButton(modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: () -> Unit = { VoyageNav.open(VoyageScreen.SURPRISE) }) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Surprise me", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).drawBehind { drawChest(this, VoyageColors.Gold, false) })
    }
}

@Composable
fun RowScope.VoyageStatCell(value: String, label: String) {
    Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = VoyageColors.BrightGold, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
        Text(label, color = VoyageColors.Ivory, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

/** The parchment-and-wood entry to Captain's Log (Settings on Watch, More on Read). */
@Composable
fun CaptainsLogCard(modifier: Modifier = Modifier) {
    if (!com.nuvio.app.mugi.mugiOn()) return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .woodPanel(14.dp)
            .clickable(onClickLabel = "Open Captain's Log") { VoyageNav.open(VoyageScreen.LOG) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoyageShip(Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Captain's Log", color = Color(0xFFF2D592), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Your streaks, stats and bounties", color = VoyageColors.Secondary, fontSize = 12.5.sp)
        }
        Text("›", color = VoyageColors.Gold, fontSize = 24.sp)
    }
}

/** Settings → Privacy (reading privacy across Watch, Ani-Drama and Read). */
@Composable
fun PrivacySettingsEntry(modifier: Modifier = Modifier) {
    if (!com.nuvio.app.mugi.mugiOn()) return
    val privacy by ReadingPrivacy.state.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MugiPrivacyColors.Surface)
            .border(1.dp, MugiPrivacyColors.Gold, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Open Privacy") { VoyageNav.open(VoyageScreen.PRIVACY) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔒", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Privacy", color = Color(0xFFF2D592), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (privacy.settings.keepReadingPrivate) "Reading is private outside Read" else "Keep your reading private",
                color = VoyageColors.Secondary,
                fontSize = 12.5.sp,
            )
        }
        Text("›", color = VoyageColors.Gold, fontSize = 24.sp)
    }
}
