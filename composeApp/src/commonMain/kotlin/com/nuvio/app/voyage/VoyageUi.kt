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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_chest_closed
import nuvio.composeapp.generated.resources.mugi_chest_half_open
import nuvio.composeapp.generated.resources.mugi_chest_icon
import nuvio.composeapp.generated.resources.mugi_chest_open
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.mugi_parchment
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.imageResource
import nuvio.composeapp.generated.resources.sailing_ship
import nuvio.composeapp.generated.resources.mugi_ship_wheel
import nuvio.composeapp.generated.resources.mugi_lantern
import nuvio.composeapp.generated.resources.sailing_ship_small
import nuvio.composeapp.generated.resources.rope_frame
import nuvio.composeapp.generated.resources.wood_panel
import nuvio.composeapp.generated.resources.parchment_chart
import nuvio.composeapp.generated.resources.bounty_books
import nuvio.composeapp.generated.resources.bounty_library
import nuvio.composeapp.generated.resources.bounty_film
import nuvio.composeapp.generated.resources.bounty_ship
import nuvio.composeapp.generated.resources.bounty_anime
import nuvio.composeapp.generated.resources.bounty_globe
import nuvio.composeapp.generated.resources.bounty_compass
import nuvio.composeapp.generated.resources.bounty_calendar
import nuvio.composeapp.generated.resources.bounty_chest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Playfair Display for section headings and labels; Cinzel for big titles. */
val VoyageSerif: FontFamily @Composable get() = com.nuvio.app.mugi.MugiPlayfair
val VoyageDisplay: FontFamily @Composable get() = com.nuvio.app.mugi.MugiCinzel

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

/**
 * The sailing ship artwork. Small sizes (progress bars, under ~40dp) use the reduced copy so it stays crisp;
 * larger ones (page headings) use the full illustration.
 */
@Composable
fun VoyageShip(modifier: Modifier = Modifier, small: Boolean = false) {
    Image(
        painterResource(if (small) Res.drawable.sailing_ship_small else Res.drawable.sailing_ship),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** The ship's wheel artwork (a heading decoration). */
@Composable
fun VoyageWheel(modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.mugi_ship_wheel), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
}

/** The brass lantern artwork (a heading decoration). */
@Composable
fun VoyageLantern(modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.mugi_lantern), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
}

/**
 * Draws [image] as a nine-slice: the four corners ([srcCorner] source pixels, drawn at [dstCorner] px) stay as they are.
 * With [repeatEdges] the straight edges are repeated at their natural scale (never stretched); otherwise they and the
 * centre stretch. [drawCenter] fills the middle as well.
 */
fun DrawScope.drawNineSlice(image: ImageBitmap, srcCorner: Int, dstCorner: Float, repeatEdges: Boolean, drawCenter: Boolean, alpha: Float = 1f) {
    val sw = image.width
    val sh = image.height
    val dw = size.width
    val dh = size.height
    val c = dstCorner.coerceAtMost(minOf(dw, dh) / 2f)
    val sc = srcCorner
    val scale = c / sc
    fun piece(sx: Int, sy: Int, sW: Int, sH: Int, dx: Float, dy: Float, dW: Float, dH: Float) {
        if (sW <= 0 || sH <= 0 || dW <= 0.5f || dH <= 0.5f) return
        drawImage(
            image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sW, sH),
            dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
            dstSize = IntSize((dx + dW).roundToInt() - dx.roundToInt(), (dy + dH).roundToInt() - dy.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.High,
        )
    }
    val midSW = sw - 2 * sc
    val midSH = sh - 2 * sc
    val midDW = dw - 2 * c
    val midDH = dh - 2 * c
    // corners
    piece(0, 0, sc, sc, 0f, 0f, c, c)
    piece(sw - sc, 0, sc, sc, dw - c, 0f, c, c)
    piece(0, sh - sc, sc, sc, 0f, dh - c, c, c)
    piece(sw - sc, sh - sc, sc, sc, dw - c, dh - c, c, c)
    if (repeatEdges) {
        // horizontal edges: whole tiles at natural scale, the count rounded so they meet the corners exactly
        val tileW = midSW * scale
        val nx = maxOf(1, (midDW / tileW).roundToInt())
        val stepX = midDW / nx
        for (k in 0 until nx) {
            piece(sc, 0, midSW, sc, c + k * stepX, 0f, stepX, c)
            piece(sc, sh - sc, midSW, sc, c + k * stepX, dh - c, stepX, c)
        }
        val tileH = midSH * scale
        val ny = maxOf(1, (midDH / tileH).roundToInt())
        val stepY = midDH / ny
        for (k in 0 until ny) {
            piece(0, sc, sc, midSH, 0f, c + k * stepY, c, stepY)
            piece(sw - sc, sc, sc, midSH, dw - c, c + k * stepY, c, stepY)
        }
    } else {
        piece(sc, 0, midSW, sc, c, 0f, midDW, c)
        piece(sc, sh - sc, midSW, sc, c, dh - c, midDW, c)
        piece(0, sc, sc, midSH, 0f, c, c, midDH)
        piece(sw - sc, sc, sc, midSH, dw - c, c, c, midDH)
    }
    if (drawCenter) piece(sc, sc, midSW, midSH, c, c, midDW, midDH)
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
                fontFamily = VoyageDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(Modifier.padding(start = 8.dp, top = 2.dp).width(110.dp).height(1.dp).background(Brush.horizontalGradient(listOf(VoyageColors.Gold, Color.Transparent))))
            subtitle?.let {
                Text(it, color = VoyageColors.Secondary, fontFamily = VoyageSerif, fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
            }
        }
    }
}

/** Italic serif heading with a fading gold line. */
@Composable
fun VoyageSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(VoyageColors.Gold, Color.Transparent))))
    }
}

/** The rope frame artwork as a nine-slice: corners kept (72px of the art), straight runs repeated, never stretched. */
@Composable
fun Modifier.ropeFrame(radius: Dp = 14.dp): Modifier {
    val rope = imageResource(Res.drawable.rope_frame)
    val corner = radius + 8.dp
    return drawWithContent {
        drawContent()
        drawNineSlice(rope, srcCorner = 72, dstCorner = corner.toPx(), repeatEdges = true, drawCenter = false)
    }
}

/** The wooden panel artwork behind a block (corners kept, wood stretched to fit). */
@Composable
fun Modifier.woodPanel(radius: Dp = 12.dp): Modifier {
    val wood = imageResource(Res.drawable.wood_panel)
    return this
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            drawRect(Color(0xFF1C110A))
            drawNineSlice(wood, srcCorner = 48, dstCorner = 14.dp.toPx(), repeatEdges = false, drawCenter = true)
        }
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
        val shipWidth = 26.dp
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
        VoyageShip(Modifier.align(Alignment.BottomStart).offset(x = travel * value, y = (-2).dp).size(shipWidth), small = true)
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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected && accent == VoyageColors.Gold) Color(0xFF2B1A0C) else VoyageColors.Ivory,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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

/** Parchment chart panel (the parchment chart artwork, stretched to fit). */
@Composable
fun ParchmentPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        Image(painterResource(Res.drawable.parchment_chart), null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        content()
    }
}

@Composable
fun VoyageEmpty(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VoyageShip(Modifier.size(width = 96.dp, height = 88.dp))
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
    val paper = if (earned) listOf(Color(0xFFF0DCAE), Color(0xFFCDAA6C)) else listOf(Color(0xFFD6C7A6), Color(0xFFB09C7C))
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(paper))
            .border(if (earned) 2.dp else 1.dp, if (earned) VoyageColors.Gold else Color(0xFF5E4A30), RoundedCornerShape(4.dp))
            .clickable(onClickLabel = progress.definition.name, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("WANTED", color = Color(0xFF5A1A14), fontFamily = VoyageDisplay, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.5.sp)
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
            BountyEmblemArt(progress.definition.emblem, earned, Modifier.size(66.dp))
            if (!earned) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(Color(0xE62A1C10)).border(1.dp, VoyageColors.Gold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = VoyageColors.Gold,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(progress.definition.name, color = Color(0xFF2E180A), fontFamily = VoyageDisplay, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
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
            Text("${progress.current.coerceAtMost(progress.definition.target)} / ${progress.definition.target}", color = Color(0xFF2E180A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x553A2210))) {
                Box(Modifier.fillMaxWidth(progress.progress).fillMaxHeight().background(Color(0xFF8A5A1E)))
            }
        }
    }
}

/** Sepia, slightly dimmed: how a bounty looks until it is earned. */
private val LockedSepia = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.33f, 0.64f, 0.16f, 0f, 0f,
            0.29f, 0.57f, 0.14f, 0f, 0f,
            0.23f, 0.45f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 0.92f, 0f,
        ),
    ),
)

/** The bounty artwork for each emblem (sepia while still locked). */
@Composable
fun BountyEmblemArt(emblem: BountyEmblem, earned: Boolean, modifier: Modifier = Modifier) {
    val res = when (emblem) {
        BountyEmblem.BOOKS -> Res.drawable.bounty_books
        BountyEmblem.LIBRARY -> Res.drawable.bounty_library
        BountyEmblem.FILM -> Res.drawable.bounty_film
        BountyEmblem.SHIP -> Res.drawable.bounty_ship
        BountyEmblem.ANIME -> Res.drawable.bounty_anime
        BountyEmblem.GLOBE -> Res.drawable.bounty_globe
        BountyEmblem.COMPASS -> Res.drawable.bounty_compass
        BountyEmblem.CALENDAR -> Res.drawable.bounty_calendar
        BountyEmblem.CHEST -> Res.drawable.bounty_chest
    }
    Image(
        painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = if (earned) null else LockedSepia,
        modifier = modifier,
    )
}

/* ───────────── treasure chest ───────────── */

enum class ChestAnimationState { CLOSED, OPENING, OPEN }

/**
 * The Surprise Me chest as three matching frames of the approved artwork (closed, half open,
 * open), all on the same 724 × 724 canvas so the body never moves; the lid stays attached behind
 * it. [opening] plays closed → half open → open once per [animationKey], then calls [onOpened]
 * and keeps the open frame. With [reduceMotion] it shows the open frame straight away.
 */
@Composable
fun MugiTreasureChest(
    opening: Boolean,
    animationKey: Long,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onOpened: () -> Unit,
) {
    var frame by remember { mutableIntStateOf(0) }
    val latestOnOpened by rememberUpdatedState(onOpened)
    LaunchedEffect(opening, animationKey) {
        if (!opening) {
            frame = 0
            return@LaunchedEffect
        }
        if (reduceMotion) {
            frame = 2
        } else {
            frame = 0
            delay(120)
            frame = 1
            delay(220)
            frame = 2
            delay(220)
        }
        latestOnOpened()
    }
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open the chest", role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = frame, animationSpec = tween(if (reduceMotion) 0 else 100), label = "Treasure chest opening") { current ->
            Image(
                painterResource(
                    when (current) {
                        0 -> Res.drawable.mugi_chest_closed
                        1 -> Res.drawable.mugi_chest_half_open
                        else -> Res.drawable.mugi_chest_open
                    },
                ),
                contentDescription = if (current == 2) "Open treasure chest" else "Treasure chest",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
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
        Image(painterResource(Res.drawable.mugi_chest_icon), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.width(size))
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
