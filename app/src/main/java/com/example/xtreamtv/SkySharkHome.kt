package com.example.xtreamtv

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

// Import only the public artwork entries from the user's installed Sky Shark
// v4.0 APK. No credentials, code, private data or other packages are read.
private data class LegacyArt(val entry:String,val hash:String)
private val legacyArt=mapOf(
    "background" to LegacyArt("0-1.png","c77ac1a72e49c06e35911a77ef5e88bbfbde4b8b0a3c04cc07f72cbdab162f04"),
    "shark" to LegacyArt("-M1.gif","af12e64e4faca9118539db006bb2da0286ed340a85361d5c8abac644f4ebe27a"),
    "live" to LegacyArt("tt.png","43d858d71951519eb9c3b3981a11cfa7af6e3477be2e36360db2214e672eab77"),
    "movies" to LegacyArt("vR1.png","110fa26d4fe1f22ea5a11f94433e1145067cffa087b20ff2e921a66d536b2b18"),
    "series" to LegacyArt("Rj.png","56c386c8895cfc7171466a3df805fa02c813fad19825957f003aff500b9f5b5b"),
    "account" to LegacyArt("gM1.png","cd72933f2580efeafbda0d2fdcbfc7d65c2748f87bcad7ce038d003d0a95a86f"),
    "playlist" to LegacyArt("GR.png","fbedd83a1fae30ff963aa36470e543fcaad6817bac32237c843e7a6e7b88e5d4"),
    "settings" to LegacyArt("-D.png","55b29477eb7bf91a6833b94ffcb0f56fb45d4b26cc53c0a731eb36344b2f46ba"),
    "reload" to LegacyArt("xX.png","fcb7d7e80b66b4f52f1f6f594ea62c643fd9951e56459bbfd1d41c6066d378d8"),
    "exit" to LegacyArt("yB.png","095702f448171de3cd8a1e4edb44e8b9d0f32f23cd6a6f6eac73d30750338cc4"),
    "premium" to LegacyArt("sky_home_logo40.gif","273f68664cfb5ad0ad253ce2b9bb0ba7d5ccdef7394f7bb65893e524dcb17b80"),
    "intro" to LegacyArt("sky_intro_fullcolor40.png","bcb54ed266f709e3bc12be906782ca350c599986136a839074980b369767e6eb"),
    "intro39" to LegacyArt("sky_shark_intro37.gif","c28800383f56487a4c83ba4b888182c5f4d4534724b3a2a3d04484951fdcc1fd")
)
private fun sha256(file:File):String {
    val md=MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer=ByteArray(16384)
        while(true){val n=input.read(buffer);if(n<0)break;md.update(buffer,0,n)}
    }
    return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
private fun importArt(context:Context):Map<String,File> {
    val dir=File(context.filesDir,"sky_shark_v40_art").apply { mkdirs() }
    val source=runCatching { context.packageManager.getApplicationInfo("app.shamel.tv",0).sourceDir }.getOrNull()
    val result=linkedMapOf<String,File>()
    val zip=source?.let { runCatching { ZipFile(it) }.getOrNull() }
    try {
        for((key,art) in legacyArt) {
            val file=File(dir,key+"."+art.entry.substringAfterLast('.'))
            if(!file.isFile || sha256(file)!=art.hash) {
                file.delete()
                val entry=zip?.getEntry("res/"+art.entry) ?: continue
                if(entry.size !in 1L..8_000_000L) continue
                val temp=File(dir,key+".tmp")
                try {
                    zip.getInputStream(entry).use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
                    if(sha256(temp)==art.hash) temp.renameTo(file)
                } finally { temp.delete() }
            }
            if(file.isFile && sha256(file)==art.hash) result[key]=file
        }
    } finally { zip?.close() }
    return result
}
@Composable internal fun rememberSkyArt():Map<String,File>? {
    val context=LocalContext.current
    val result by produceState<Map<String,File>?>(null,context) {
        value=withContext(Dispatchers.IO) { runCatching { importArt(context) }.getOrDefault(emptyMap()) }
    }
    return result
}
@Composable internal fun SkyArtImage(file:File?, modifier:Modifier=Modifier, fill:Boolean=false, animate:Boolean=true) {
    if(file==null) return
    key(file.absolutePath) {
        AndroidView(
            modifier=modifier,
            factory={ context ->
                ImageView(context).apply {
                    scaleType=if(fill) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    isFocusable=false
                    isClickable=false
                    val drawable=runCatching {
                        if(Build.VERSION.SDK_INT>=28) ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                        else BitmapDrawable(resources,BitmapFactory.decodeFile(file.absolutePath))
                    }.getOrNull()
                    setImageDrawable(drawable)
                    if(Build.VERSION.SDK_INT>=28 && animate && drawable is AnimatedImageDrawable) {
                        drawable.repeatCount=AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                }
            },
            onRelease={ view ->
                if(Build.VERSION.SDK_INT>=28) (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(null)
            }
        )
    }
}
@Composable internal fun SkySharkHome(
    art:Map<String,File>?, accountLine:String, onAction:(String)->Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617),Color(0xFF071C68))))) {
        val w=maxWidth
        val h=maxHeight
        SkyArtImage(art?.get("background"),Modifier.fillMaxSize(),fill=true)
        if(art?.get("premium")!=null) {
            SkyArtImage(art["premium"],Modifier.offset(w*.045f,h*.045f).size(w*.115f,h*.10f))
        } else {
            val pulse=rememberInfiniteTransition(label="Sky Premium glow").animateFloat(
                .72f,1f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="Logo pulse")
            SkyArtImage(art?.get("live"),Modifier.offset(w*.045f,h*.045f).size(w*.115f,h*.10f)
                .graphicsLayer { alpha=pulse.value })
        }
        SkyArtImage(art?.get("shark"),Modifier.align(Alignment.TopCenter).offset(y=h*.055f).size(w*.27f,h*.27f))
        Text(accountLine,color=Color.White,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.align(Alignment.TopEnd).padding(end=w*.045f,top=h*.06f))
        Row(Modifier.offset(w*.07f,h*.36f).width(w*.86f).height(h*.36f),
            horizontalArrangement=Arrangement.spacedBy(w*.018f)) {
            SkyHomeTile("Live",art?.get("live"),Modifier.weight(1.6f).fillMaxHeight(),false,onAction)
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(h*.022f)) {
                SkyHomeTile("Movies",art?.get("movies"),Modifier.weight(1f).fillMaxWidth(),false,onAction)
                SkyHomeTile("Account",art?.get("account"),Modifier.weight(1f).fillMaxWidth(),false,onAction)
            }
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(h*.022f)) {
                SkyHomeTile("Series",art?.get("series"),Modifier.weight(1f).fillMaxWidth(),false,onAction)
                SkyHomeTile("Change Playlist",art?.get("playlist"),Modifier.weight(1f).fillMaxWidth(),false,onAction)
            }
            Column(Modifier.weight(2f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(h*.018f)) {
                SkyHomeTile("Settings",art?.get("settings"),Modifier.weight(1f).fillMaxWidth(),true,onAction)
                SkyHomeTile("Reload",art?.get("reload"),Modifier.weight(1f).fillMaxWidth(),true,onAction)
                SkyHomeTile("EXIT",art?.get("exit"),Modifier.weight(1f).fillMaxWidth(),true,onAction)
            }
        }
        if(art!=null && listOf("background","shark","live","movies","series","account","playlist","settings","reload","exit").any { it !in art }) Text(
            "Original artwork unavailable. Keep your Sky Shark v3.9 or v4.0 installed.",
            color=Color.White,fontSize=12.sp,modifier=Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}
@Composable private fun SkyHomeTile(label:String,art:File?,modifier:Modifier,horizontal:Boolean,onAction:(String)->Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused=it.isFocused }
        .glassFrame(2.dp,if(focused) SharkFocusGold else Color(0xFF00BFFF),focused=focused,repeatPulse=true)
        .background(Color(0xFF082A80)).clickable { onAction(label) }.padding(5.dp)) {
        if(horizontal) Row(Modifier.fillMaxSize().padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            SkyArtImage(art,Modifier.weight(.34f).fillMaxHeight())
            Text(label,color=Color.White,fontWeight=FontWeight.Medium,fontSize=15.sp,maxLines=1,
                modifier=Modifier.weight(.66f))
        } else Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
            SkyArtImage(art,Modifier.weight(1f).fillMaxWidth().padding(horizontal=if(label=="Live") 18.dp else 5.dp))
            Text(label,color=Color.White,fontWeight=FontWeight.Medium,fontSize=if(label=="Live") 19.sp else 14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
