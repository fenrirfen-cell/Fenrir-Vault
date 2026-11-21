package com.nuvio.app.readmode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

/**
 * The glass behind the bottom bar: on Android 13+ a clear "liquid glass" that bends what's
 * behind it near the rim (like Zangetsu); elsewhere a light blur.
 */
@Composable
internal expect fun FenrirLiquidGlassBackground(hazeState: HazeState?, modifier: Modifier)

private val SecondaryText = Color(0xFFA7A7B2)

/**
 * Zangetsu-style floating tab bar: a 68dp clear glass capsule, centred and a little narrower
 * than the screen; the selected tab sits in a red-tinted glass pill that slides between tabs,
 * with a red, slightly enlarged icon and a white label; other tabs have white icons and grey
 * labels.
 */
@Composable
fun FenrirLiquidNavBar(items: List<FenrirNavItem>, modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedIndex = items.indexOfFirst { it.selected }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, bottom = bottomInset + 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(CircleShape),
        ) {
            FenrirLiquidGlassBackground(hazeState, Modifier.fillMaxSize())

            // Sliding red glass pill behind the selected tab.
            val tabWidth = (maxWidth - 8.dp) / items.size.coerceAtLeast(1)
            val pillX by animateDpAsState(
                targetValue = 4.dp + tabWidth * selectedIndex.coerceAtLeast(0),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "fenrirPillX",
            )
            val pillAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "fenrirPillAlpha")
            Box(
                modifier = Modifier
                    .offset(x = pillX)
                    .padding(vertical = 4.dp)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f * pillAlpha))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f * pillAlpha), Color.Transparent),
                        ),
                    )
                    .border(
                        0.8.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.32f * pillAlpha),
                                Color.White.copy(alpha = 0.04f * pillAlpha),
                                Color.White.copy(alpha = 0.12f * pillAlpha),
                            ),
                        ),
                        CircleShape,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item -> LiquidTab(item, accent, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LiquidTab(item: FenrirNavItem, accent: Color, modifier: Modifier) {
    val iconColor by animateColorAsState(if (item.selected) accent else Color.White, label = "fenrirTabIcon")
    val labelColor by animateColorAsState(if (item.selected) Color.White else SecondaryText, label = "fenrirTabLabel")
    val grow by animateFloatAsState(if (item.selected) 1.08f else 1f, label = "fenrirTabGrow")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(27.dp).scale(grow), contentAlignment = Alignment.Center) {
            when {
                item.icon != null -> Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(25.dp))
                item.painter != null -> Icon(item.painter, contentDescription = null, tint = iconColor, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
