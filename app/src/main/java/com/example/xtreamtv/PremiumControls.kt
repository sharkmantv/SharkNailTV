package com.example.xtreamtv
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
@Composable internal fun PremiumButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,compact:Boolean=false,content:@Composable RowScope.()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=SharkFrameShape
    Row(modifier.heightIn(min=if(compact) 32.dp else 42.dp).onFocusChanged { focused=it.isFocused }.hudSurface(focused && enabled).pulsingFrame(2.dp,if(focused && enabled) Color(0xFFFFD44D) else Color(0xFF48A8E8),shape).clickable(enabled=enabled,onClick=onClick).padding(horizontal=if(compact) 4.dp else 12.dp,vertical=if(compact) 3.dp else 8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides (if(enabled) Color.White else Color(0xFF8291A8)),LocalTextStyle provides TextStyle(fontFamily=SkyLikeFamily,fontSize=14.sp,fontWeight=FontWeight.Normal,letterSpacing=0.18.sp)) { content() }
    }
}
