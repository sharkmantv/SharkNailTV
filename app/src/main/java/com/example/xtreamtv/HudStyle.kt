package com.example.xtreamtv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val HudBackground = Color(0xFF09132F)
internal val HudSurface = Color(0xFF17264A)
internal val HudAccent = Color(0xFFA8E8FF)
internal val SharkFrameShape = RoundedCornerShape(10.dp)

private val hudFill = Brush.verticalGradient(
    listOf(Color(0xD93E5484), Color(0xDF1C2B52), Color(0xE9141E3D))
)
private val hudFocusedFill = Brush.verticalGradient(
    listOf(Color(0xEF527FAB), Color(0xEF2B527F), Color(0xF01F355F))
)

internal fun Modifier.hudSurface(highlighted: Boolean = false, shape: Shape = SharkFrameShape): Modifier =
    background(if (highlighted) hudFocusedFill else hudFill, shape)

internal fun Modifier.hudBackdrop(): Modifier = drawWithCache {
    val base = Brush.verticalGradient(listOf(Color(0xFF142450), HudBackground))
    val blueLight = Brush.radialGradient(
        listOf(Color(0xB32E66AE), Color.Transparent),
        center = Offset(size.width * .83f, size.height * .10f),
        radius = (size.width * .68f).coerceAtLeast(1f)
    )
    val violetLight = Brush.radialGradient(
        listOf(Color(0x664B4294), Color.Transparent),
        center = Offset(size.width * .12f, size.height * .80f),
        radius = (size.width * .55f).coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(base)
        drawRect(blueLight)
        drawRect(violetLight)
    }
}

internal fun Modifier.glassFrame(width: Dp, color: Color, shape: Shape = SharkFrameShape): Modifier {
    val highlight = color.red > .65f && color.green > .55f
    val edge = Brush.verticalGradient(
        if (highlight) listOf(Color(0xFFECF8FF), HudAccent, Color(0xFF688BCD))
        else listOf(Color(0x8296BAEA), Color(0x3B809DDF), Color(0x566888C5))
    )
    val stroke = if (highlight) width.coerceIn(1.dp, 2.dp) else width.coerceAtMost(1.dp)
    return clip(shape).border(stroke, edge, shape)
}
