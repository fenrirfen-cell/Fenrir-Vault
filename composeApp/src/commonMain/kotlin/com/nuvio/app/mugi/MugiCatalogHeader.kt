// MugiVerse catalog row heading (from the approved kit; package adapted).
package com.nuvio.app.mugi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CatalogGlyph { COMPASS, TELESCOPE }

@Composable
fun MugiCatalogHeader(
    title: String,
    subtitle: String? = null,
    glyph: CatalogGlyph = CatalogGlyph.COMPASS,
    accent: Color = Color(0xFFE44349),
    parchment: Painter? = null,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ivory = Color(0xFFF4E3C1)
    val gold = Color(0xFFD7AC6A)
    val bronze = Color(0xFFA66A36)
    val secondary = Color(0xFFB1A99A)

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        val titleLimit = maxWidth * if (onSeeAll == null) .92f else .69f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (parchment != null) {
                Box(Modifier.widthIn(max = titleLimit).heightIn(min = 38.dp), contentAlignment = Alignment.CenterStart) {
                    Image(parchment, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        NauticalGlyph(glyph, Color(0xFF503013), Modifier.size(24.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(title, color = Color(0xFF2D1A0D), fontFamily = MugiPlayfair,
                            fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Row(Modifier.widthIn(max = titleLimit), verticalAlignment = Alignment.CenterVertically) {
                    NauticalGlyph(glyph, gold, Modifier.size(25.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(title, color = ivory, fontFamily = MugiPlayfair, fontWeight = FontWeight.SemiBold,
                            fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrBlank()) Text(subtitle, color = secondary,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(
                listOf(bronze, accent.copy(alpha = .45f), Color.Transparent)
            )))
            if (onSeeAll != null) {
                Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button,
                    onClickLabel = "See all $title", onClick = onSeeAll).padding(start = 6.dp, end = 1.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("See All", color = gold, fontFamily = MugiPlayfair, fontSize = 13.sp, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text("›", color = gold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun NauticalGlyph(glyph: CatalogGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        val line = s * .055f
        when (glyph) {
            CatalogGlyph.COMPASS -> {
                drawCircle(color, s * .36f, c, style = Stroke(line))
                val p = Path().apply {
                    moveTo(c.x, c.y - s*.49f); lineTo(c.x+s*.085f,c.y-s*.085f)
                    lineTo(c.x+s*.49f,c.y); lineTo(c.x+s*.085f,c.y+s*.085f)
                    lineTo(c.x,c.y+s*.49f); lineTo(c.x-s*.085f,c.y+s*.085f)
                    lineTo(c.x-s*.49f,c.y); lineTo(c.x-s*.085f,c.y-s*.085f); close()
                }
                drawPath(p,color)
                drawCircle(Color(0xFF14212A),s*.045f,c)
            }
            CatalogGlyph.TELESCOPE -> rotate(-28f, c) {
                val strip = Path().apply {
                    moveTo(s*.12f,s*.39f);lineTo(s*.75f,s*.29f)
                    lineTo(s*.75f,s*.71f);lineTo(s*.12f,s*.61f);close()
                }
                drawPath(strip,color)
                drawRoundRect(color,Offset(s*.70f,s*.24f),Size(s*.20f,s*.52f),CornerRadius(s*.055f))
                drawRoundRect(color,Offset(s*.04f,s*.37f),Size(s*.12f,s*.26f),CornerRadius(s*.035f))
                drawLine(Color(0xFF17212A),Offset(s*.39f,s*.35f),Offset(s*.39f,s*.65f),strokeWidth=line)
                drawLine(color,Offset(s*.47f,s*.66f),Offset(s*.47f,s*.86f),strokeWidth=line)
                drawLine(color,Offset(s*.36f,s*.86f),Offset(s*.58f,s*.86f),strokeWidth=line)
            }
        }
    }
}
