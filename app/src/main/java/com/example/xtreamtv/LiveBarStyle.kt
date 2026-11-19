package com.example.xtreamtv

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal enum class LiveBarStyle(
    val label: String,
    val accent: Color,
    val haze: Color,
    val inset: Int,
    val glowX: Float
) {
    BLUE_SMOKE("Blue Smoke", Color(0xFF79BCFF), Color(0xAE163A75), 22, .22f),
    GLACIER("Glacier", Color(0xFFA4E6FF), Color(0xB0255670), 18, .82f),
    DEEP_CURRENT("Deep Current", Color(0xFF52C6FF), Color(0xA5084356), 29, .50f),
    ION_BLUE("Ion Blue", Color(0xFF8E9EFF), Color(0xB1374692), 24, .05f),
    MIDNIGHT_SIGNAL("Midnight Signal", Color(0xFF679FFF), Color(0x9C172D66), 34, .94f)
}

internal fun Modifier.smokyLiveBar(style: LiveBarStyle): Modifier =
    clip(SharkFrameShape).drawWithCache {
        val haze = Brush.radialGradient(
            listOf(style.haze, Color.Transparent),
            center = Offset(size.width * style.glowX, 0f),
            radius = (size.width * .72f).coerceAtLeast(1f)
        )
        val shadow = Brush.verticalGradient(listOf(Color(0xEC162948), Color(0xFA050C1B)))
        val edge = Brush.horizontalGradient(listOf(Color.Transparent, style.accent, Color.Transparent))
        onDrawBehind {
            drawRect(shadow)
            drawRect(haze)
            drawRect(edge, size = Size(size.width, 2.dp.toPx()))
        }
    }
