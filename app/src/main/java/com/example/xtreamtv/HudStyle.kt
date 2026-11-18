package com.example.xtreamtv

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal val HudBackground = Color(0xFF0B0923)
internal val HudSurface = Color(0xFF080B18)
internal val HudAccent = Color(0xFF37C5FF)
private val hudFill = Brush.verticalGradient(listOf(Color(0xFF0F1229), HudSurface))
private val hudFocusedFill = Brush.verticalGradient(listOf(Color(0xFF172345), HudSurface))

internal val LocalFramePulse = compositionLocalOf<State<Float>> { mutableStateOf(1f) }

internal val SharkFrameShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cut = with(density) { 11.dp.toPx() }.coerceAtMost(size.minDimension / 4f)
        return Outline.Generic(chamferedPath(0f, 0f, size.width, size.height, cut))
    }
}

private fun chamferedPath(left: Float, top: Float, right: Float, bottom: Float, cut: Float) = Path().apply {
    moveTo(left + cut, top)
    lineTo(right - cut, top)
    lineTo(right, top + cut)
    lineTo(right, bottom - cut)
    lineTo(right - cut, bottom)
    lineTo(left + cut, bottom)
    lineTo(left, bottom - cut)
    lineTo(left, top + cut)
    close()
}

internal fun Modifier.hudSurface(highlighted: Boolean = false): Modifier =
    background(if (highlighted) hudFocusedFill else hudFill, SharkFrameShape)

internal fun Modifier.hudBackdrop(): Modifier = background(HudBackground).drawWithCache {
    val w = size.width
    val h = size.height
    val violetBands = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * .62f, 0f)
        lineTo(w * .11f, h)
        lineTo(0f, h)
        close()
        moveTo(w * .89f, 0f)
        lineTo(w, 0f)
        lineTo(w, h * .35f)
        lineTo(w * .67f, h)
        lineTo(w * .38f, h)
        close()
    }
    val violet = Brush.linearGradient(listOf(Color(0xFF19123C), Color(0xFF100C2D)), end = Offset(w, h))
    onDrawBehind { drawPath(violetBands, violet) }
}

@Composable
internal fun Modifier.pulsingFrame(
    width: Dp,
    color: Color,
    @Suppress("UNUSED_PARAMETER") shape: Shape = SharkFrameShape
): Modifier {
    val pulse = LocalFramePulse.current
    val focused = color.red > .7f && color.green > .55f
    return clip(SharkFrameShape).drawWithCache {
        val w = size.width
        val h = size.height
        val shortestEdge = size.minDimension
        val strokeWidth = width.toPx().coerceAtMost(shortestEdge / 12f)
        val edge = strokeWidth / 2f
        val cut = 11.dp.toPx().coerceAtMost(shortestEdge / 4f)
        val outline = chamferedPath(edge, edge, w - edge, h - edge, cut)
        val stroke = Stroke(strokeWidth)
        val tabStroke = Stroke(strokeWidth * .62f)
        val rim = Brush.verticalGradient(
            if (focused) listOf(HudAccent, Color(0xFF285C99), Color(0xFF559FFF))
            else listOf(Color(0xFF304873), Color(0xFF14223D), Color(0xFF294776)),
            endY = h.coerceAtLeast(1f)
        )

        val corners = Path().apply {
            val leg = cut * 1.35f
            for (x in listOf(1f, -1f)) {
                for (y in listOf(1f, -1f)) {
                    val originX = if (x > 0f) edge else w - edge
                    val originY = if (y > 0f) edge else h - edge
                    moveTo(originX, originY)
                    lineTo(originX + leg * x, originY)
                    lineTo(originX, originY + leg * y)
                    close()
                }
            }
        }
        val stripe = minOf(3.dp.toPx(), shortestEdge / 14f)
        val stripes = Path().apply {
            val topOffsets = listOf(w * .17f, w * .72f)
            for (i in 0..3) {
                for (start in topOffsets) {
                    val x = start + i * stripe * 1.55f
                    moveTo(x, edge)
                    lineTo(x + stripe, edge)
                    lineTo(x + stripe * 1.55f, edge + stripe)
                    lineTo(x + stripe * .55f, edge + stripe)
                    close()
                }
                val y = h * .38f + i * stripe * 1.55f
                moveTo(edge, y)
                lineTo(edge + stripe, y + stripe * .55f)
                lineTo(edge + stripe, y + stripe * 1.55f)
                lineTo(edge, y + stripe)
                close()
                moveTo(w - edge, y)
                lineTo(w - edge - stripe, y + stripe * .55f)
                lineTo(w - edge - stripe, y + stripe * 1.55f)
                lineTo(w - edge, y + stripe)
                close()
            }
        }
        val tabWidth = (w * .34f).coerceAtMost(128.dp.toPx())
        val tabHeight = (h * .095f).coerceAtMost(8.dp.toPx())
        val tab = chamferedPath((w - tabWidth) / 2f, h - edge - tabHeight, (w + tabWidth) / 2f, h - edge, tabHeight / 2f)
        onDrawWithContent {
            drawContent()
            if (strokeWidth > 0f) {
                val accent = if (focused) HudAccent else Color(0xFF2090DA)
                val opacity = if (focused) .8f + .2f * pulse.value else .78f
                drawPath(outline, rim, style = stroke)
                drawPath(corners, accent, alpha = opacity)
                drawPath(stripes, accent, alpha = opacity)
                drawPath(tab, HudSurface)
                drawPath(tab, accent, alpha = opacity, style = tabStroke)
            }
        }
    }
}
