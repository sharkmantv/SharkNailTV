package com.example.xtreamtv
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
@Composable internal fun focusPulse(focused:Boolean):Float {
    if(!focused) return 0f
    val transition=rememberInfiniteTransition(label="premium focus")
    val alpha by transition.animateFloat(0.5f,1f,infiniteRepeatable(tween(950),RepeatMode.Reverse),label="focus alpha")
    return alpha
}
@Composable internal fun PremiumButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,compact:Boolean=false,content:@Composable RowScope.()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val pulse=focusPulse(focused && enabled)
    val shape=RoundedCornerShape(0.dp)
    Row(modifier.heightIn(min=if(compact) 32.dp else 42.dp).onFocusChanged { focused=it.isFocused }.background(Brush.verticalGradient(listOf(if(focused && enabled) Color(0xFF273B59) else Color(0xFF18263E),Color(0xFF0C172B))),shape).pulsingFrame(2.dp,if(focused && enabled) Color(0xFFFFD44D).copy(alpha=0.65f+0.35f*pulse) else Color(0xFF48A8E8),shape).clickable(enabled=enabled,onClick=onClick).padding(horizontal=if(compact) 4.dp else 12.dp,vertical=if(compact) 3.dp else 8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides (if(enabled) Color.White else Color(0xFF8291A8)),LocalTextStyle provides TextStyle(fontFamily=SkyLikeFamily,fontSize=14.sp,fontWeight=FontWeight.Normal,letterSpacing=0.18.sp)) { content() }
    }
}
