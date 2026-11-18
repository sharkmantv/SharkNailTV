package com.example.xtreamtv

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val IJK_CLASS = "tv.danmaku.ijk.media.player.IjkMediaPlayer"
internal fun ijkAvailable(): Boolean = runCatching { Class.forName(IJK_CLASS, false, Thread.currentThread().contextClassLoader ?: object {}.javaClass.classLoader); true }.getOrDefault(false)

@Composable internal fun IjkPlayback(url:String,onExit:()->Unit,onChannel:(Int)->Unit,title:String,live:Boolean) {
    var error by remember(url) { mutableStateOf("") }
    var playing by remember(url) { mutableStateOf(false) }
    val instance = remember(url) { runCatching { Class.forName(IJK_CLASS).getDeclaredConstructor().newInstance() }.getOrNull() }
    fun invoke(name:String) { runCatching { instance?.javaClass?.getMethod(name)?.invoke(instance) }.onFailure { error="IJKPlayer failed. Select Media3 in settings." } }
    DisposableEffect(instance) { onDispose { runCatching { instance?.javaClass?.getMethod("release")?.invoke(instance) } } }
    BackHandler { onExit() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            AndroidView(factory={ctx -> SurfaceView(ctx).apply {
                holder.addCallback(object:SurfaceHolder.Callback {
                    override fun surfaceCreated(h:SurfaceHolder) {
                        runCatching {
                            val p=instance ?: throw IllegalStateException("IJK library unavailable")
                            val c=p.javaClass
                            c.getMethod("setDisplay",SurfaceHolder::class.java).invoke(p,h)
                            c.getMethod("setDataSource",String::class.java).invoke(p,url)
                            val listener=Class.forName("tv.danmaku.ijk.media.player.IMediaPlayer\$OnPreparedListener")
                            val callback=java.lang.reflect.Proxy.newProxyInstance(listener.classLoader,arrayOf(listener)) { _,method,_ -> if(method.name=="onPrepared") { invoke("start");playing=true };null }
                            c.getMethod("setOnPreparedListener",listener).invoke(p,callback)
                            c.getMethod("prepareAsync").invoke(p)
                        }.onFailure { error="IJKPlayer could not start. Return to Media3 in settings." }
                    }
                    override fun surfaceChanged(h:SurfaceHolder,format:Int,width:Int,height:Int) {}
                    override fun surfaceDestroyed(h:SurfaceHolder) { runCatching { instance?.javaClass?.getMethod("reset")?.invoke(instance) } }
                })
            }},modifier=Modifier.fillMaxSize())
            if(error.isNotEmpty()) Text(error,color=Color.White)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,color=Color.White,modifier=Modifier.weight(1f))
            if(live) PremiumButton(onClick={onChannel(-1)}) { Text("Previous") }
            PremiumButton(onClick={invoke(if(playing) "pause" else "start");playing=!playing}) { Text(if(playing) "Pause" else "Play") }
            if(live) PremiumButton(onClick={onChannel(1)}) { Text("Next") }
            PremiumButton(onClick=onExit) { Text("Back") }
        }
    }
}
