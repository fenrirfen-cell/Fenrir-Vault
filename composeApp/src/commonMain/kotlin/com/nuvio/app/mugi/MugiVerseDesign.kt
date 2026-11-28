// MugiVerse design kit (adapted to this app: package, back handler, compose resources).
package com.nuvio.app.mugi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

enum class MugiMode(val title: String, val description: String, val accent: Color) {
    WATCH("Watch", "Movies & TV", Color(0xFFE44349)),
    ANI_DRAMA("Ani-Drama", "Anime & drama", Color(0xFF9C53E9)),
    READ("Read", "Manga & novels", Color(0xFFF3B43F))
}

enum class WatchKind(val label: String) { MOVIES("Movies"), TV_SHOWS("TV Shows") }

object MugiPalette {
    val Navy = Color(0xFF071720)
    val PurpleNavy = Color(0xFF130D21)
    val Wood = Color(0xFF28170F)
    val Gold = Color(0xFFC9954D)
    val Ivory = Color(0xFFF4E3C1)
    val Muted = Color(0xFFB9B3A8)
    fun background(mode: MugiMode) = when (mode) {
        MugiMode.WATCH -> Navy
        MugiMode.ANI_DRAMA -> PurpleNavy
        MugiMode.READ -> Wood
    }
}

@Immutable
data class MugiArt(
    val logo: Painter,
    val compass: Painter,
    val watch: Painter,
    val aniDrama: Painter,
    val read: Painter,
    val parchment: Painter,
    val ropeFrame: Painter,
    val readingFrame: Painter,
    val libraryCabin: Painter,
    val shelfRow: Painter
) {
    fun mode(mode: MugiMode): Painter = when (mode) {
        MugiMode.WATCH -> watch
        MugiMode.ANI_DRAMA -> aniDrama
        MugiMode.READ -> read
    }
}

/** Use a shared root content slot, NOT a separate mock app; caller owns navigation state. */
@Composable
fun MugiModeShell(
    mode: MugiMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModeChange: (MugiMode) -> Unit,
    art: MugiArt,
    modifier: Modifier = Modifier,
    // Existing navigation receives compass tap callback. Keep original tabs and routes.
    navigation: @Composable (menuExpanded: Boolean, onCompassClick: () -> Unit) -> Unit,
    extras: List<MugiMenuAction> = emptyList(),
    content: @Composable () -> Unit
) {
    com.nuvio.app.core.ui.PlatformBackHandler(enabled = expanded) { onExpandedChange(false) }
    val reveal = remember { Animatable(0f) }
    // pulses along the lines, only while the menu is open
    val flow = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) while (true) {
            flow.snapTo(0f)
            flow.animateTo(1f, tween(1700, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    LaunchedEffect(expanded) {
        reveal.animateTo(if (expanded) 1f else 0f, tween(if (expanded) 320 else 190, easing = FastOutSlowInEasing))
    }
    Box(modifier.fillMaxSize()) {
        content()
        if (expanded || reveal.value > 0.001f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .64f * reveal.value))
                .clickable(enabled = expanded, onClickLabel = "Close mode selector") { onExpandedChange(false) })
            // Align to the navigation compass. Root should handle system insets once.
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter).padding(bottom = 49.dp)
                .fillMaxWidth().height(282.dp)) {
                val menuWidth = minOf(maxWidth, 520.dp)
                val badgeSize = if (menuWidth < 360.dp) 60.dp else 72.dp
                val cardWidth = minOf(120.dp, menuWidth * .25f)
                val targets = listOf(MugiMode.WATCH to .23f, MugiMode.ANI_DRAMA to .50f, MugiMode.READ to .77f)
                val topOffsets = listOf(97.dp, 18.dp, 97.dp)
                Box(Modifier.align(Alignment.BottomCenter).width(menuWidth).height(282.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        // Fenrir: every line starts at the compass centre and ends under its medallion,
                        // following it while the menu opens or closes, so they always stay connected.
                        val origin = Offset(size.width * .5f, size.height)
                        val p = reveal.value.coerceIn(0f,1f)
                        targets.forEachIndexed { index, item ->
                            val cx = size.width * .5f + (size.width * item.second - size.width * .5f) * p
                            val top = topOffsets[index] * p + 210.dp * (1 - p)
                            // into the symbol itself (the medallion is drawn over the line's end)
                            val end = Offset(cx, (top + (badgeSize + 8.dp) / 2).toPx())
                            val rise = origin.y - end.y
                            val p1 = Offset(origin.x + (end.x-origin.x)*.10f, origin.y - rise * .38f)
                            val p2 = Offset(end.x, end.y + rise * .32f)
                            val curve = Path().apply {
                                moveTo(origin.x, origin.y)
                                cubicTo(p1.x, p1.y, p2.x, p2.y, end.x, end.y)
                            }
                            val c = item.first.accent
                            drawPath(curve,c.copy(alpha=.10f*p),style=Stroke(9.dp.toPx()))
                            drawPath(curve,c.copy(alpha=.25f*p),style=Stroke(4.dp.toPx()))
                            drawPath(curve,c.copy(alpha=.9f*p),style=Stroke(1.3.dp.toPx()))
                            // light pulses running from the compass up into the symbol
                            for (j in 0 until 3) {
                                val tt = ((flow.value + j / 3f + index * .11f) % 1f)
                                val v = 1f - tt
                                val pt = Offset(
                                    v*v*v*origin.x+3*v*v*tt*p1.x+3*v*tt*tt*p2.x+tt*tt*tt*end.x,
                                    v*v*v*origin.y+3*v*v*tt*p1.y+3*v*tt*tt*p2.y+tt*tt*tt*end.y,
                                )
                                drawCircle(c.copy(alpha=.28f*p), 7.dp.toPx(), pt)
                                drawCircle(c.copy(alpha=.6f*p), 3.5.dp.toPx(), pt)
                                drawCircle(Color(0xFFFFF5E1).copy(alpha=p), 1.8.dp.toPx(), pt)
                            }
                        }
                    }
                    targets.forEachIndexed { index, item ->
                        val p = reveal.value.coerceIn(0f,1f)
                        val x = (menuWidth * item.second - cardWidth / 2) * p + (menuWidth / 2 - cardWidth / 2) * (1-p)
                        val y = topOffsets[index]*p + (210.dp)*(1-p)
                        if (p > .05f) {
                            Column(Modifier.offset(x=x,y=y).width(cardWidth)
                                .clickable(enabled=expanded,role=Role.Button,onClickLabel="Switch to ${item.first.title}") {
                                    onExpandedChange(false)
                                    if(item.first != mode) onModeChange(item.first)
                                },horizontalAlignment=Alignment.CenterHorizontally) {
                                Box(Modifier.size(badgeSize + 8.dp),contentAlignment=Alignment.Center) {
                                    Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(item.first.accent.copy(alpha=.65f*p),Color.Transparent)),CircleShape))
                                    Image(art.mode(item.first),contentDescription=null,modifier=Modifier.size(badgeSize))
                                }
                                ParchmentLabel(item.first.title,art.parchment,Modifier.width(cardWidth).height(25.dp),if(menuWidth<360.dp) 11 else 13)
                                Text(item.first.description,color=MugiPalette.Ivory,fontFamily=MugiPlayfair,fontSize=10.sp,
                                    maxLines=1,textAlign=TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
            // extra actions (Assistant) as gold-rimmed pills above the Ani-Drama medallion; the three-mode
            // animation underneath is unchanged
            if (extras.isNotEmpty() && (expanded || reveal.value > 0.001f)) {
                val p = reveal.value.coerceIn(0f,1f)
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 49.dp + 282.dp + 10.dp)
                    .graphicsLayer { alpha = p; translationY = (1f - p) * 24.dp.toPx() },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    extras.forEach { action ->
                        val shape = RoundedCornerShape(50)
                        Row(Modifier.heightIn(min=44.dp).clip(shape).background(Color(0xF00E1822))
                            .border(1.dp, MugiPalette.Gold, shape)
                            .clickable(enabled = expanded, role = Role.Button, onClickLabel = action.label) {
                                onExpandedChange(false)
                                action.onClick()
                            }
                            .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(action.icon, null, tint = MugiPalette.Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(action.label, color = MugiPalette.Ivory, fontFamily = MugiPlayfair, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
        Box(Modifier.align(Alignment.BottomCenter)) { navigation(expanded) { onExpandedChange(!expanded) } }
    }
}

/** Render the existing nav destinations using their existing icons/actions; insert Compass as a slot. */
@Composable
fun MugiCompassButton(art: MugiArt, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(62.dp).clip(CircleShape).clickable(role=Role.Button,onClickLabel=if(expanded) "Close mode selector" else "Open mode selector",onClick=onClick), contentAlignment=Alignment.Center) {
        Image(art.compass,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    }
}

@Composable
fun MugiNavSurface(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp)
        .clip(RoundedCornerShape(34.dp)).background(Color(0xEE071720))
        .border(1.dp,MugiPalette.Gold,RoundedCornerShape(34.dp)).padding(horizontal=8.dp,vertical=3.dp),
        horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically,content=content)
}

/** Use actual page safe-area handling, not hard-coded camera dimensions. */
@Composable
fun MugiHeader(mode: MugiMode, art: MugiArt, modifier: Modifier = Modifier,
               actions: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start) {
            Image(art.logo,"MugiVerse",Modifier.widthIn(max=168.dp).height(48.dp),contentScale=ContentScale.Fit)
            Text(mode.title.uppercase(),color=MugiPalette.Ivory, fontFamily=MugiPlayfair,fontWeight=FontWeight.Bold,
                fontSize=11.sp,modifier=Modifier.padding(start=35.dp).clip(RoundedCornerShape(3.dp))
                    .background(mode.accent.copy(alpha=.83f)).padding(horizontal=14.dp,vertical=2.dp))
        }
        Row(verticalAlignment=Alignment.CenterVertically,content=actions)
    }
}

@Composable
fun WatchKindSelector(value: WatchKind,onChange:(WatchKind)->Unit,modifier:Modifier=Modifier,compact:Boolean=false) {
    // See-through pill so the art behind shows through. [compact] is the small one in the header.
    val shape=RoundedCornerShape(50)
    val pill = @Composable { m: Modifier ->
        Row(m.clip(shape)
            .background(MugiPalette.Navy.copy(alpha=if(compact) .7f else .55f)).border(1.dp,MugiPalette.Gold.copy(alpha=.6f),shape)
            .padding(if(compact) 2.5.dp else 3.dp)) {
            WatchKind.values().forEach { kind ->
                val selected=kind==value
                Box(Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .then(if(selected) Modifier.background(Brush.verticalGradient(listOf(Color(0xFFDE3038),Color(0xFF8B1019)))) else Modifier)
                    .clickable(role=Role.Tab,onClickLabel=kind.label){onChange(kind)},contentAlignment=Alignment.Center) {
                    Text(kind.label,color=if(selected) MugiPalette.Ivory else MugiPalette.Ivory.copy(alpha=.85f),
                        fontFamily=MugiPlayfair,fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize=if(compact) 12.sp else 14.sp,maxLines=1)
                }
            }
        }
    }
    if (compact) {
        // 48dp tall touch area around the 32dp pill
        Box(modifier.width(164.dp).heightIn(min=48.dp),contentAlignment=Alignment.Center) { pill(Modifier.fillMaxWidth().height(32.dp)) }
    } else {
        Box(modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { pill(Modifier.widthIn(max=220.dp).fillMaxWidth().height(34.dp)) }
    }
}

@Composable
fun ParchmentLabel(text:String, art:Painter, modifier:Modifier=Modifier, textSize:Int=16) {
    Box(modifier,contentAlignment=Alignment.Center) {
        Image(art,null,Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
        Text(text,color=Color(0xFF2E180A),fontFamily=MugiPlayfair,fontWeight=FontWeight.Bold,
            fontSize=textSize.sp, maxLines=1,overflow=TextOverflow.Ellipsis, modifier=Modifier.padding(horizontal=8.dp))
    }
}

/** A miniature ship is positioned at the true playback progress; don't fake saved progress. */
@Composable
fun SailingProgress(progress:Float,accent:Color,modifier:Modifier=Modifier) {
    val v=progress.takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f
    Canvas(modifier.fillMaxWidth().height(23.dp)) {
        val inset=10.dp.toPx();val y=size.height*.72f;val w=(size.width-inset*2).coerceAtLeast(0f)
        val x=inset+w*v
        drawLine(Color(0xFF343944),Offset(inset,y),Offset(inset+w,y),strokeWidth=5.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accent,Offset(inset,y),Offset(x,y),strokeWidth=5.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
        val d=1.dp.toPx();val gold=Color(0xFFFFD472)
        val sail=Path().apply {moveTo(x,y-18*d);lineTo(x-6*d,y-4*d);lineTo(x,y-4*d);close()}
        val sail2=Path().apply {moveTo(x+1*d,y-17*d);lineTo(x+7*d,y-5*d);lineTo(x+1*d,y-5*d);close()}
        val hull=Path().apply {moveTo(x-8*d,y-3*d);lineTo(x+8*d,y-3*d);lineTo(x+4*d,y+1*d);lineTo(x-4*d,y+1*d);close()}
        drawPath(sail,gold);drawPath(sail2,Color(0xFFE6A545));drawPath(hull,Color(0xFFC58634));drawLine(gold,Offset(x,y-18*d),Offset(x,y-2*d),strokeWidth=d)
    }
}

/** Put one rope frame on EACH list item, not a frame around an entire LazyRow. */
@Composable
fun RopeResumeCard(art:MugiArt, modifier:Modifier=Modifier, content:@Composable RowScope.()->Unit) {
    Box(modifier.heightIn(min=112.dp)) {
        Box(Modifier.matchParentSize().padding(6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xEE0A1018)))
        Image(art.ropeFrame,null,Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

/** Slot-in decorated panel: keep current book renderer, opening animation and reader callback. */
@Composable
fun CaptainReadingPanel(art:MugiArt,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit) {
    Box(modifier.fillMaxWidth().heightIn(min=190.dp)) {
        Box(Modifier.matchParentSize().padding(16.dp).background(Color(0xCC28170F),RoundedCornerShape(10.dp)))
        Image(art.readingFrame,null,Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
        ParchmentLabel("Continue Reading",art.parchment,Modifier.align(Alignment.TopCenter).padding(top=25.dp).width(165.dp).height(28.dp),14)
        Row(Modifier.fillMaxWidth().padding(start=29.dp,end=29.dp,top=58.dp,bottom=28.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

/** Repeat this row using real books; do not stretch a single static screenshot. */
@Composable
fun CaptainShelfRow(art:MugiArt,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit) {
    Box(modifier.fillMaxWidth().height(176.dp)) {
        Image(art.shelfRow,null,Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
        Row(Modifier.fillMaxSize().padding(start=15.dp,end=15.dp,bottom=23.dp,top=8.dp),
            verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

/** Native version of approved Three Tides. Parent runs this once after Android system splash. */
@Composable
fun ThreeTidesIntro(logo:Painter,onFinished:()->Unit,modifier:Modifier=Modifier,reduceMotion:Boolean=false) {
    val time=remember { Animatable(0f) };val done by rememberUpdatedState(onFinished)
    LaunchedEffect(reduceMotion) {
        if(reduceMotion) { time.snapTo(3.33f);done() }
        else { time.animateTo(3.33f,tween(3330,easing=androidx.compose.animation.core.LinearEasing));done() }
    }
    fun smooth(v:Float):Float { val x=v.coerceIn(0f,1f);return x*x*(3f-2f*x) }
    val u=smooth((time.value-.94f)/.66f)
    Box(modifier.fillMaxSize().background(Color(0xFF090F17)),contentAlignment=Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=600.dp).aspectRatio(720f/1140f)) {
            Canvas(Modifier.matchParentSize()) {
                val c=Offset(size.width*.5f,size.height*.489f)
                val paths=listOf(listOf(Offset(-.125f,.69f),Offset(.174f,.64f),Offset(.319f,.28f)),
                    listOf(Offset(1.12f,.28f),Offset(.90f,.31f),Offset(.64f,.72f)),
                    listOf(Offset(.46f,.114f),Offset(.63f,.267f),Offset(.146f,.509f)))
                val colors=listOf(MugiMode.WATCH.accent,MugiMode.ANI_DRAMA.accent,MugiMode.READ.accent)
                fun cubic(p:List<Offset>,t:Float):Offset {val v=1-t;val a=Offset(p[0].x*size.width,p[0].y*size.height);val b=Offset(p[1].x*size.width,p[1].y*size.height);val d=Offset(p[2].x*size.width,p[2].y*size.height);return a*(v*v*v)+b*(3*v*v*t)+d*(3*v*t*t)+c*(t*t*t)}
                paths.forEachIndexed { index,path ->
                    val p=((time.value-index*.075f)/.79f).coerceIn(0f,1f)
                    if(p>0f&&p<1f){ val start=(p-.38f).coerceAtLeast(0f);var prev=cubic(path,start)
                        for(step in 1..50){val fraction=step/50f;val next=cubic(path,start+(p-start)*fraction);val width=(if(index==2) 18f else 15f)*sin(PI*fraction).toFloat().coerceAtLeast(.02f)*size.width/720f
                            drawLine(colors[index],prev,next,width,cap=androidx.compose.ui.graphics.StrokeCap.Round);drawLine(Color.White.copy(alpha=.20f),prev,next,width*.12f);prev=next}
                    }
                }
                val q=smooth((time.value-.67f)/.35f);val fade=1-smooth((time.value-1.05f)/.36f)
                if(q>0f&&fade>0f){val r=size.width*.21f*q;val w=size.width*.018f*q
                    listOf(Offset(0f,-1f),Offset(1f,0f),Offset(0f,1f),Offset(-1f,0f)).forEach { unit ->
                        val normal=Offset(-unit.y,unit.x);val star=Path().apply {moveTo(c.x+unit.x*r,c.y+unit.y*r);lineTo(c.x+normal.x*w,c.y+normal.y*w);lineTo(c.x-unit.x*r*.09f,c.y-unit.y*r*.09f);lineTo(c.x-normal.x*w,c.y-normal.y*w);close()};drawPath(star,MugiPalette.Ivory.copy(alpha=fade)) }
                }
            }
            val logoWidth = maxWidth * .86f
            Box(Modifier.align(Alignment.Center).fillMaxWidth(.86f).height(110.dp).clipToBounds()) {
                // Center-out wipe; clip descendants without changing logo aspect ratio.
                Box(Modifier.align(Alignment.Center).fillMaxWidth(u).fillMaxHeight().clipToBounds()) {
                    Image(logo,"MugiVerse",Modifier.requiredWidth(logoWidth).fillMaxHeight().align(Alignment.Center),contentScale=ContentScale.Fit)
                }
            }
        }
    }
}
