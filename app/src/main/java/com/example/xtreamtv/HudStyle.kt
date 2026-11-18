package com.example.xtreamtv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val HudBackground = Color(0xFF060D23)
internal val HudSurface = Color(0xFF101D38)
internal val SharkFocusGold = Color(0xFFFFD44D)
internal val HudAccent = Color(0xFFA8E8FF)
internal val SharkFrameShape = RoundedCornerShape(0.dp)

private val hudFill = Brush.verticalGradient(
    listOf(Color(0xD92C406B), Color(0xDF152443), Color(0xE90E1933))
)
private val hudFocusedFill = Brush.verticalGradient(
    listOf(Color(0xEF355686), Color(0xEF203A64), Color(0xF017294C))
)

internal fun Modifier.hudSurface(highlighted: Boolean = false, shape: Shape = SharkFrameShape): Modifier =
    background(if (highlighted) hudFocusedFill else hudFill, shape)

internal fun Modifier.hudBackdrop(): Modifier = drawWithCache {
    val base = Brush.verticalGradient(listOf(Color(0xFF0C1B3D), HudBackground))
    val blueLight = Brush.radialGradient(
        listOf(Color(0x98244685), Color.Transparent),
        center = Offset(size.width * .83f, size.height * .10f),
        radius = (size.width * .68f).coerceAtLeast(1f)
    )
    val lowerBlueLight = Brush.radialGradient(
        listOf(Color(0x381C3373), Color.Transparent),
        center = Offset(size.width * .12f, size.height * .80f),
        radius = (size.width * .55f).coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(base)
        drawRect(blueLight)
        drawRect(lowerBlueLight)
    }
}

@Composable
internal fun Modifier.glassFrame(
    width: Dp,
    color: Color,
    shape: Shape = SharkFrameShape,
    focused: Boolean = false,
    repeatPulse: Boolean = false
): Modifier {
    val highlight = focused || (color != HudAccent && color.red > .65f && color.green > .55f)
    val edge = Brush.verticalGradient(
        if (focused || color == SharkFocusGold) listOf(SharkFocusGold, Color(0xFFD8A43D))
        else if (highlight) listOf(Color(0xFFECF8FF), HudAccent, Color(0xFF688BCD))
        else listOf(Color(0x8296BAEA), Color(0x3B809DDF), Color(0x566888C5))
    )
    val stroke = if (highlight) width.coerceIn(1.dp, 2.dp) else width.coerceAtMost(1.dp)
    val frame = Modifier.clip(shape).border(stroke, edge, shape)
    if (!focused) return then(frame)

    val pulse = if (repeatPulse) {
        val transition = rememberInfiniteTransition(label = "Navigation focus border")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "Gold outline opacity")

    } else {
        val opacity = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            opacity.animateTo(1f, tween(180))
            opacity.animateTo(0f, tween(420))
        }
        opacity.asState()
    }
    return drawWithCache {
        val line = stroke.toPx()
        val inset = line / 2
        val outlineSize = Size((size.width - line).coerceAtLeast(0f), (size.height - line).coerceAtLeast(0f))
        onDrawWithContent {
            drawContent()
            drawRect(Color(0xFFFFE990), Offset(inset, inset), outlineSize, alpha = pulse.value, style = Stroke(line))
        }
    }.then(frame)
}
