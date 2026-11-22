package tv.own.owntv.features.shell

import android.app.Activity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private val SkyGold = Color(0xFFFFD83B)
private val SkyNavy = Color(0xFF030C28)
private val SkyBlue = Color(0xFF0557CC)

private data class SkyTileData(
    val title: String,
    val symbol: String,
    val from: Color,
    val to: Color,
)

@Composable
fun SkyRetroLanding(
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onPlaylists: () -> Unit,
    onSettings: () -> Unit,
    onReload: () -> Unit,
    onAccount: () -> Unit,
    onExit: () -> Unit,
    contentStart: Dp,
    modifier: Modifier = Modifier,
) {
    val tiles = remember {
        listOf(
            SkyTileData("LIVE TV", "★", Color(0xFF0058AE), Color(0xFF010925)),
            SkyTileData("MOVIES", "▶", Color(0xFF6E3D48), Color(0xFF031A3E)),
            SkyTileData("SERIES", "♛", Color(0xFF293F75), Color(0xFF071331)),
            SkyTileData("PLAYLISTS", "▣", Color(0xFF2357BA), Color(0xFF070A32)),
            SkyTileData("SETTINGS", "⚙", Color(0xFF545D6A), Color(0xFF1D2545)),
            SkyTileData("RELOAD", "↻", Color(0xFF073A83), Color(0xFF020B35)),
            SkyTileData("ACCOUNT", "●", Color(0xFF064A8A), Color(0xFF2F1259)),
            SkyTileData("EXIT", "⇥", Color(0xFF331659), Color(0xFF05083A)),
        )
    }
    val routes = listOf(onLive, onMovies, onSeries, onPlaylists, onSettings, onReload, onAccount, onExit)
    Box(modifier.fillMaxSize().background(SkyNavy)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF114C9F).copy(alpha = .24f), radius = size.width * .55f,
                center = Offset(size.width * .78f, size.height * .2f))
            drawCircle(Color(0xFF351E78).copy(alpha = .17f), radius = size.width * .42f,
                center = Offset(size.width * .02f, size.height * .92f))
        }
        Column(
            Modifier.fillMaxSize()
                .padding(start = contentStart, end = 27.dp, top = 23.dp, bottom = 21.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp),
        ) {
            repeat(2) { r ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    repeat(4) { c ->
                        val index = r * 4 + c
                        SkyRetroTile(
                            data = tiles[index],
                            onClick = routes[index],
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyRetroTile(
    data: SkyTileData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val infinite = rememberInfiniteTransition(label = "yellow-border")
    val alpha by infinite.animateFloat(
        initialValue = .65f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "glow",
    )
    val edge = if (focused) Color.White else SkyGold.copy(alpha = alpha)
    Column(
        modifier
            .scale(if (focused) 1.025f else 1f)
            .border(if (focused) 3.dp else 2.dp, edge, RectangleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(Brush.linearGradient(listOf(data.from, data.to))),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Cinematic arcs, cool-white spotlights and the glossy SKY neon ribbon.
                drawCircle(
                    color = Color(0xFF42B6FF).copy(alpha = .21f),
                    radius = size.minDimension * .57f,
                    center = Offset(size.width * .53f, size.height * .68f),
                    style = Stroke(width = size.minDimension * .055f),
                )
                drawLine(
                    brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFF33A6FF), Color.Transparent)),
                    start = Offset(0f, size.height * .16f),
                    end = Offset(size.width, size.height * .78f),
                    strokeWidth = size.height * .022f,
                )
            }
            Text(
                text = data.symbol,
                fontSize = 86.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFD0EEFF),
                textAlign = TextAlign.Center,
            )
            if (focused) {
                Box(
                    Modifier.fillMaxSize()
                        .border(3.dp, SkyGold, RectangleShape)
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(54.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0067CE), Color(0xFF032C78), Color(0xFF020C30))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = data.title,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
