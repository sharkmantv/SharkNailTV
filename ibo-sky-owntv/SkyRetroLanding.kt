package tv.own.owntv.features.shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.R

private val skyGold = Color(0xFFFFD02D)
private val skyNavy = Color(0xFF030C28)

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
    val context = LocalContext.current
    // Crops each card from the user's original 4.0 SKY RETRO eight-card artwork.
    // Entire poster (including its blue title bar) is rendered within the card: no separate overlays.
    val posterBitmaps: List<ImageBitmap> = remember(context) {
        val sheet = BitmapFactory.decodeResource(context.resources, R.drawable.sky_retro_cards)
        require(sheet.width == 1680 && sheet.height == 588)
        List(8) { i ->
            Bitmap.createBitmap(sheet, (i % 4) * 420, (i / 4) * 294, 420, 294).asImageBitmap()
        }.also { sheet.recycle() }
    }
    val titles = listOf(
        stringResource(R.string.sky_retro_live),
        stringResource(R.string.sky_retro_movies),
        stringResource(R.string.sky_retro_series),
        stringResource(R.string.sky_retro_playlists),
        stringResource(R.string.sky_retro_settings),
        stringResource(R.string.sky_retro_reload),
        stringResource(R.string.sky_retro_account),
        stringResource(R.string.sky_retro_exit)
    )
    val routes = listOf(onLive, onMovies, onSeries, onPlaylists, onSettings, onReload, onAccount, onExit)
    BoxWithConstraints(modifier.fillMaxSize().background(skyNavy), contentAlignment = Alignment.Center) {
        val tileWidth = (maxWidth - contentStart - 97.dp) / 4
        val naturalTileHeight = tileWidth * (294f / 420f)
        val heightLimit = (maxHeight - 80.dp) / 2
        val tileHeight = if (naturalTileHeight <= heightLimit) naturalTileHeight else heightLimit
        Column(
            Modifier.fillMaxSize().padding(start = contentStart, end = 22.dp, top = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)
        ) {
            repeat(2) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(tileHeight),
                    horizontalArrangement = Arrangement.spacedBy(17.dp)
                ) {
                    repeat(4) { col ->
                        val index = row * 4 + col
                        SkyPosterCard(
                            title = titles[index],
                            poster = posterBitmaps[index],
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
private fun SkyPosterCard(
    title: String,
    poster: ImageBitmap,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = title)
    val pulseAlpha by transition.animateFloat(
        initialValue = .57f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = title,
    )
    val borderColor = if (focused) Color.White else skyGold.copy(alpha = pulseAlpha)
    Box(
        modifier
            .scale(if (focused) 1.018f else 1f)
            .border(if (focused) 3.dp else 2.dp, borderColor, RectangleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = poster,
            contentDescription = title,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().padding(2.dp)
        )
    }
}
