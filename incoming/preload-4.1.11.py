from pathlib import Path
import re
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected preload source: "+a[:130]
    return s.replace(a,b,1)

p=root/"PlaybackSettings.kt"
s=p.read_text()
anchor='return ExoPlayer.Builder(context.applicationContext,factory)'
s=swap(s,anchor,'val playerBuilder = ExoPlayer.Builder(context.applicationContext,factory)')
# Move configuration onto the player created with the same preload manager builder.
lines=s.splitlines(keepends=True)
matches=[i for i,l in enumerate(lines) if 'val playerBuilder = ExoPlayer.Builder' in l and '.build().apply {' in l]
assert len(matches)==1, "Unexpected configured player builder"
i=matches[0]
lines[i]=lines[i].replace('.build().apply {','')
lines.insert(i+1,'''        val player = if(live && !preview && !experimentalTunneling && p.getBoolean("sharkChannelPreload",false)) {
            sharkBuildPreloadPlayer(context,playerBuilder,factory,load,networkFactory)
        } else playerBuilder.build()
        return player.apply {
''')
s=''.join(lines)
s=swap(s,'"sharkCronet" to "Cronet networking"', '"sharkCronet" to "Cronet networking","sharkChannelPreload" to "Next channel preload (requires 2 streams)"')
p.write_text(s)

(root/"ChannelPreload.kt").write_text('''package com.example.xtreamtv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager

// Main-thread ownership. One adjacent channel maximum; disabled by default.
// Never preload in a preview, on focus, or on a single-connection account.
private class SharkChannelSession(val manager:DefaultPreloadManager) {
    var current:MediaItem?=null
    var next:MediaItem?=null
}
private val sharkPreloadSessions=java.util.WeakHashMap<ExoPlayer,SharkChannelSession>()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun sharkBuildPreloadPlayer(context:Context,playerBuilder:ExoPlayer.Builder,
    renderers:RenderersFactory,load:LoadControl,network:DataSource.Factory):ExoPlayer {
    val builder=DefaultPreloadManager.Builder(context.applicationContext) { _:Int ->
        DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(1000L)
    }.setRenderersFactory(renderers).setLoadControl(load)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(DefaultDataSource.Factory(context.applicationContext,network)))
    val manager=builder.build()
    return try {
        builder.buildExoPlayer(playerBuilder).also { sharkPreloadSessions[it]=SharkChannelSession(manager) }
    } catch(e:Exception) {
        manager.release()
        throw e
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun sharkSetChannelItem(player:ExoPlayer,item:MediaItem) {
    val session=sharkPreloadSessions[player]
    if(session==null) { player.setMediaItem(item); return }
    val source=if(session.next==item) session.manager.getMediaSource(item) else null
    if(source!=null) player.setMediaSource(source) else player.setMediaItem(item)
    session.current?.takeIf { it!=item }?.let { session.manager.remove(it) }
    session.next?.takeIf { it!=item }?.let { session.manager.remove(it) }
    session.current=if(source!=null) item else null
    session.next=null
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun sharkWarmChannel(player:ExoPlayer,item:MediaItem) {
    val session=sharkPreloadSessions[player] ?: return
    if(item==session.current || item==session.next) return
    session.next?.let { session.manager.remove(it) }
    session.next=item
    session.manager.setCurrentPlayingIndex(0)
    session.manager.add(item,1)
    session.manager.invalidate()
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun sharkReleasePlayer(player:ExoPlayer) {
    try { sharkPreloadSessions.remove(player)?.manager?.release() }
    finally { player.release() }
}
''')
p=root/"MainActivity.kt"
s=p.read_text()
# Fullscreen live channel navigation already reuses the ExoPlayer instance.
# Pass just the adjacent channel, not the entire provider catalogue.
anchor='liveStream=entry.kind=="live", programmes=epg, inferFormat=true,'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+''' nextChannelUrl=if(entry.kind=="live") live.getOrNull(live.indexOf(entry)+1)?.takeIf { it.id!=entry.id }?.let { api!!.url(it, playbackPrefs(context).getString("streamFormat", "Auto")) } else null,''',1)
anchor='inferFormat:Boolean=false,onFirstFrame:()->Unit={}'
s=swap(s,anchor,'inferFormat:Boolean=false,nextChannelUrl:String?=null,onFirstFrame:()->Unit={}')
s=swap(s,'player.setMediaItem(sharkMediaItem(context,url,inferFormat));','sharkSetChannelItem(player,sharkMediaItem(context,url,inferFormat));')
anchor='    var firstFrame by remember(url) { mutableStateOf(false) }'
s=swap(s,anchor,anchor+'''
    LaunchedEffect(player,url,nextChannelUrl,firstFrame) {
        if(liveStream && firstFrame && nextChannelUrl!=null && playbackPrefs(context).getBoolean("sharkChannelPreload",false)) {
            delay(1000L)
            if(player.isPlaying) sharkWarmChannel(player,sharkMediaItem(context,nextChannelUrl,inferFormat))
        }
    }''')
s=swap(s,'DisposableEffect(player) { onDispose {player.release()} }','DisposableEffect(player) { onDispose {sharkReleasePlayer(player)} }')
p.write_text(s)
print("Optional one-channel Media3 PreloadManager integrated with fullscreen player reuse.")
