package com.ipvio.stream

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** SKY RETRO home, image assets approved for this project. No ITV/player code changed. */
@Composable
fun SkyRetroHome(onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val posterBitmaps: List<ImageBitmap> = remember(context) {
        val sheet = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.sky_retro_cards))
        require(sheet.width == 1680 && sheet.height == 588)
        List(8) { i -> Bitmap.createBitmap(sheet, (i % 4)*420, (i/4)*294, 420, 294).asImageBitmap() }
            .also { sheet.recycle() }
    }
    val actions: List<() -> Unit> = listOf(
        { onTab(3) }, { onTab(1) }, { onTab(2) }, onAdd,
        { onTab(MenuLogic.SETTINGS) },
        { (context as? Activity)?.recreate() },
        { onTab(MenuLogic.SETTINGS) },
        { (context as? Activity)?.moveTaskToBack(true) },
    )
    val labels=listOf("Live TV","Movies","Series","Playlists","Settings","Reload","Account","Exit")
    val gold=Color(0xFFFFD028)
    val navy=Color(0xFF030B22)
    val pulse=rememberInfiniteTransition(label="skyPulse")
    val alpha by pulse.animateFloat(
        initialValue=.68f, targetValue=1f,
        animationSpec=infiniteRepeatable(tween(1100),RepeatMode.Reverse), label="skyAlpha"
    )
    val firstFocus=remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    BoxWithConstraints(Modifier.fillMaxSize().background(navy), contentAlignment=Alignment.Center) {
        val gap=14.dp
        val cardWidth=(maxWidth-72.dp-gap*3)/4
        val desired=cardWidth * (294f/420f)
        val cardHeight=minOf(desired,(maxHeight-64.dp-gap)/2)
        Column(
            Modifier.fillMaxSize().padding(horizontal=36.dp, vertical=24.dp),
            verticalArrangement=Arrangement.spacedBy(gap,Alignment.CenterVertically)
        ) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth().height(cardHeight), horizontalArrangement=Arrangement.spacedBy(gap)) {
                    repeat(4) { col ->
                        val i=row*4+col
                        var focused by remember { mutableStateOf(false) }
                        val focusModifier=if (i==0) Modifier.focusRequester(firstFocus) else Modifier
                        Box(
                            modifier=Modifier.weight(1f).fillMaxHeight()
                                .scale(if (focused) 1.018f else 1f)
                                .border(if(focused) 3.dp else 2.dp, if(focused) Color.White else gold.copy(alpha=alpha),RectangleShape)
                                .then(focusModifier)
                                .onFocusChanged { focused=it.isFocused }
                                .clickable(onClick=actions[i]),
                            contentAlignment=Alignment.Center
                        ) {
                            Image(
                                bitmap=posterBitmaps[i],
                                contentDescription=labels[i],
                                contentScale=ContentScale.FillBounds,
                                modifier=Modifier.fillMaxSize().padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
