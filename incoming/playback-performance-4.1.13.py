from pathlib import Path
import re

root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected performance source: "+a[:150]
    return s.replace(a,b,1)

# Keep the existing account-scoped EPG cache and coalescing lock. Back off
# transient failures rather than hammering the provider on repeated focus.
p=root/"MainActivity.kt"
s=p.read_text()
s=swap(s,'    private val epgRequestLocks=Array(16) { kotlinx.coroutines.sync.Mutex() }','''    private val epgFailureRetry=android.util.LruCache<String,Long>(128)
    private val epgRequestLocks=Array(16) { kotlinx.coroutines.sync.Mutex() }''')
s=swap(s,'        fetchEpgUncoalesced(streamId)','''        val now=android.os.SystemClock.elapsedRealtime()
        val old=programmeCache.get(streamId)
        val stale=old?.takeIf {
            val age=System.currentTimeMillis()-it.first
            age in 0L..600_000L
        }?.second.orEmpty()
        if((epgFailureRetry.get(streamId) ?: 0L)>now) {
            stale
        } else {
            try {
                fetchEpgUncoalesced(streamId).also { epgFailureRetry.remove(streamId) }
            } catch(e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch(e:Exception) {
                // Limit the failure cooldown to 20 seconds. Never log provider URLs.
                epgFailureRetry.put(streamId,android.os.SystemClock.elapsedRealtime()+20_000L)
                stale
            }
        }''')
# Explicit cache refresh and memory-pressure eviction clear the retry cache too.
s=swap(s,'api.programmeCache.evictAll()','api.programmeCache.evictAll(); api.epgFailureRetry.evictAll()')
s=re.sub(r'(?<![.\w])programmeCache\.evictAll\(\)', 'programmeCache.evictAll(); epgFailureRetry.evictAll()', s)
p.write_text(s)

# Avoid a whole-catalogue temporary array of Room entities. Construct only one
# batch per transaction on IO. Preserve existing index, paging and search.
p=root/"CataloguePaging.kt"
s=p.read_text()
s=swap(s,'''    @Transaction suspend fun replace(rows:List<CatalogueRow>) {
        clear()
        rows.chunked(500).forEach { insert(it) }
    }''','''    @Transaction suspend fun replace(entries:List<Entry>) {
        clear()
        for(start in entries.indices step 256) {
            val end=minOf(start+256,entries.size)
            val rows=ArrayList<CatalogueRow>(end-start)
            for(i in start until end) {
                val e=entries[i]
                rows.add(CatalogueRow(i+1,e.id,e.title,e.image,e.category,e.kind,e.container,e.backdrop,e.year,e.rating,e.genre,e.overview))
            }
            insert(rows)
        }
    }''')
s=swap(s,'db.catalogue().replace(entries.mapIndexed { i,e -> CatalogueRow(i+1,e.id,e.title,e.image,e.category,e.kind,e.container,e.backdrop,e.year,e.rating,e.genre,e.overview) })','db.catalogue().replace(entries)')
s=swap(s,'''else entries.filter { query.isBlank() || it.title.contains(query,true) }.let {''','''else (if(query.isBlank()) entries else entries.filter { it.title.contains(query,true) }).let {''')
p.write_text(s)

# Detailed timing, without the URL, title, username, token or media ID.
# This is in addition to the existing aggregate PlaybackStatsListener.
p=root/"PlaybackSettings.kt"
s=p.read_text()
s=swap(s,'return player.apply {','''return player.apply {
        addAnalyticsListener(SharkPlaybackTiming(preview,live))''')
# Preloading remains opt-in, and avoid opening a second live stream on low-RAM hardware.
s=swap(s,'if(live && !preview && !experimentalTunneling && p.getBoolean("sharkChannelPreload",false))','''if(live && !preview && !experimentalTunneling &&
            !(context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice &&
            p.getBoolean("sharkChannelPreload",false))''')
p.write_text(s)
(root/"SharkPlaybackTiming.kt").write_text('''package com.example.xtreamtv

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SharkPlaybackTiming(private val preview:Boolean,private val live:Boolean):AnalyticsListener {
    private var started=SystemClock.elapsedRealtime()
    private var firstFrame=false
    private var bufferStarted=0L
    private var dropped=0L
    private var sequence=0
    private val mode=if(preview) "preview" else if(live) "live" else "vod"
    private fun metric(name:String,value:Long) {
        Log.d("SharkPlaybackTiming","mode="+mode+" item="+sequence+" "+name+"="+value)
    }
    override fun onMediaItemTransition(eventTime:EventTime,mediaItem:MediaItem?,reason:Int) {
        if(mediaItem==null) return
        started=SystemClock.elapsedRealtime()
        firstFrame=false
        bufferStarted=0L
        dropped=0L
        sequence++
    }
    override fun onRenderedFirstFrame(eventTime:EventTime,output:Any,renderTimeMs:Long) {
        if(!firstFrame) {
            firstFrame=true
            metric("firstFrameMs",(SystemClock.elapsedRealtime()-started).coerceAtLeast(0L))
        }
    }
    override fun onVideoDecoderInitialized(eventTime:EventTime,decoderName:String,
        initializedTimestampMs:Long,initializationDurationMs:Long) {
        metric("decoderInitMs",initializationDurationMs)
    }
    override fun onPlaybackStateChanged(eventTime:EventTime,state:Int) {
        if(state==Player.STATE_BUFFERING && firstFrame && bufferStarted==0L) {
            bufferStarted=SystemClock.elapsedRealtime()
        } else if(state!=Player.STATE_BUFFERING && bufferStarted>0L) {
            metric("rebufferMs",SystemClock.elapsedRealtime()-bufferStarted)
            bufferStarted=0L
        }
    }
    override fun onDroppedVideoFrames(eventTime:EventTime,droppedFrames:Int,elapsedMs:Long) {
        dropped+=droppedFrames
        metric("droppedFramesTotal",dropped)
    }
    override fun onPlayerError(eventTime:EventTime,error:PlaybackException) {
        metric("errorCode",error.errorCode.toLong())
    }
}
''')
print("Bounded EPG failure backoff, batch catalogue indexing and per-item playback timing applied.")
