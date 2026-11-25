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


# Catalogue crash mitigation: keep the established layout and Paging 3, but
# don't pin disposed lazy items or let a live PagingSource outlive a Room DB.
p=root/"MainActivity.kt"
s=p.read_text()
start=s.index('@Composable private fun TvCatalogueBrowser(')
end=s.index('@Composable private fun V4PosterRow(',start)
ui=s[start:end]
assert ui.count('.focusRestorer()')==2
ui=ui.replace('.focusRestorer()','')
# Official Paging Compose key helper reads a consistent snapshot and gives
# placeholders distinct keys without directly peeking beyond itemCount.
ui=swap(ui,'key={ index -> paged.peek(index)?.let { it.kind+":"+it.id } ?: "placeholder:"+index }','key=paged.itemKey { it.kind+":"+it.id }')
s=s[:start]+ui+s[end:]
if 'import androidx.paging.compose.itemKey' not in s:
    s=s.replace('import androidx.compose.ui.focus.focusRestorer','import androidx.paging.compose.itemKey',1)
p.write_text(s)

p=root/"CataloguePaging.kt"
s=p.read_text()
marker='    @Query("SELECT * FROM catalogue_rows ORDER BY rowid")'
s=swap(s,marker,'''    @Query("SELECT * FROM catalogue_rows WHERE rowid IN (SELECT rowid FROM catalogue_fts WHERE catalogue_fts MATCH :term) ORDER BY rowid")
    suspend fun boundedSearch(term:String):List<CatalogueRow>
'''+marker)
start=s.index('@Composable internal fun cataloguePages(')
s=s[:start]+'''@Composable internal fun cataloguePages(entries:List<Entry>, query:String, alphabetical:Boolean):LazyPagingItems<Entry> {
    val context=LocalContext.current.applicationContext
    // Browse from the existing account-scoped cached list. No second complete
    // catalogue database is allocated during normal navigation or refresh.
    var visible by remember(entries,query,alphabetical) {
        mutableStateOf(if(query.isBlank()) entries else emptyList())
    }
    LaunchedEffect(entries,query,alphabetical,context) {
        if(query.isBlank() && !alphabetical) return@LaunchedEffect
        val result=withContext(Dispatchers.Default) {
            val term=query.split(Regex("[^\\\\p{L}\\\\p{N}]+")).filter { it.isNotBlank() }.joinToString(" ") { it+"*" }
            val lowRam=(context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
            // For modest searches only, keep Room fully scoped to one IO operation.
            // Large catalogues use cancellable background filtering, with no duplicate DB.
            val indexed=if(query.isNotBlank() && term.isNotEmpty() && !lowRam && entries.size in 1..1500) {
                withContext(Dispatchers.IO) {
                    var db:CatalogueDatabase?=null
                    try {
                        val opened=Room.inMemoryDatabaseBuilder(context,CatalogueDatabase::class.java).build()
                        db=opened
                        opened.catalogue().replace(entries)
                        opened.catalogue().boundedSearch(term).map { it.entry() }
                    } catch(e:kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch(e:Exception) {
                        null
                    } finally {
                        db?.close()
                    }
                }
            } else null
            // Retain substring matching in addition to FTS prefix matches.
            val matches=if(query.isBlank()) entries else {
                val ids=indexed.orEmpty().mapTo(HashSet()) { it.kind+":"+it.id }
                entries.filter { it.title.contains(query,true) || it.kind+":"+it.id in ids }
            }
            if(alphabetical) matches.sortedBy { it.title.lowercase(java.util.Locale.ROOT) } else matches
        }
        visible=result
    }
    val flow=remember(visible) { flowOf(PagingData.from(visible)) }
    return flow.collectAsLazyPagingItems()
}
'''
p.write_text(s)
print("Catalogue stability: standard focus, safe paging keys, cached browsing and bounded search indexing.")


# Remote Back must always be handled in-app, even while the composable
# catalogue or player screen is being replaced. Never default to Activity finish.
p=root/"MainActivity.kt"
s=p.read_text()
anchor='class MainActivity:ComponentActivity(){'
s=swap(s,anchor,anchor+'''
    override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {
        if(event.keyCode==android.view.KeyEvent.KEYCODE_BACK) {
            if(event.action==android.view.KeyEvent.ACTION_UP && !event.isCanceled) {
                if(onBackPressedDispatcher.hasEnabledCallbacks()) onBackPressedDispatcher.onBackPressed()
            }
            // One navigation action per completed remote key press. Consume repeats
            // and transient gaps between Compose destinations.
            return true
        }
        return super.dispatchKeyEvent(event)
    }
''')
# Keep one app-level callback mounted across Home, Movies, Series and playback.
# SafePlayer's own later BackHandler retains priority for player controls.
anchor='        if (!introFinished) {'
s=swap(s,anchor,'''        BackHandler {
            when {
                exitPrompt -> exitPrompt=false
                !introFinished -> introFinished=true
                homePlaying!=null -> homePlaying=null
                selected?.kind in listOf("live","movie","episode") && api!=null -> {
                    selected=episodeParent
                    episodeParent=null
                }
                selected!=null -> {
                    homeLookupJob?.cancel()
                    homePlaying=null
                    selected=null
                }
                section.endsWith(" Catalogue") -> section="Shark Streaming"
                section=="EPG / TV Guide" -> section="Shark TV"
                section in listOf("Sharks Toolkit","Connection Status","API Configuration","Playback & Display","Xtream Codes","System Health","Shark Performance Hub") -> section="Settings"
                section!="Home" -> section="Home"
                else -> exitPrompt=true
            }
        }
'''+anchor)
# Remove branch-scoped duplicate callbacks, not SafePlayer's child callback.
s=swap(s,'            BackHandler { homePlaying=null }\n','')
s=swap(s,'            BackHandler { selected=episodeParent; episodeParent=null }\n','')
matches=re.findall(r'(?m)^ *BackHandler \{ if\(selected!=null\) \{homeLookupJob\?\.cancel\(\);homePlaying=null;selected=null\}[^\n]*\}\n',s)
assert len(matches)==1,"Expected original branch navigation handler"
s=swap(s,matches[0],'')
# Older source versions also register a separate Home exit handler.
s=re.sub(r'(?m)^ *BackHandler \{ exitPrompt\s*=\s*true \}\n','',s)
p.write_text(s)
print("Remote Back now routes through a permanent app-level handler; explicit Exit confirmation remains.")


# Fixed catalogue geometry: loading/empty messages are overlays inside the
# existing grid, not extra siblings that resize the weighted browser.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index('@Composable private fun TvCatalogueBrowser(')
b=s.index('@Composable private fun V4PosterRow(',a)
ui=s[a:b]
ui=swap(ui,'    loading:Boolean,','    loading:Boolean,\n    catalogueError:String,')
ui,n=re.subn(r'(?m)^ *CompactLiveAction\("Retry",Modifier\.fillMaxWidth\(\)\)\{onRetry\(\)\}\n','',ui)
assert n==1,"Bottom Retry anchor missing"
ui,n=re.subn(r'(?m)^([ ]*)query=""\n[ ]*onCategory\(cat.first\)',lambda m: m[1]+'if(cat.first==categoryId) {\n'+m[1]+'    if(!loading && paged.itemCount==0) onRetry()\n'+m[1]+'} else {\n'+m[1]+'    query=""\n'+m[1]+'    onCategory(cat.first)\n'+m[1]+'}',ui)
assert n==1,"Category OK anchor missing"
ui=swap(ui,'Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {','Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {')
ui=swap(ui,'modifier=Modifier.fillMaxWidth().padding(top=3.dp))','modifier=Modifier.fillMaxWidth().height(22.dp).padding(top=3.dp))')
# Avoid resetting the grid for the same category confirmation or for empty
# transient snapshots; no animation or changing row sizes on selection.
ui=swap(ui,'LaunchedEffect(categoryId,appliedQuery,alphabetical) { gridState.scrollToItem(0) }','LaunchedEffect(categoryId,appliedQuery,alphabetical) { if(gridState.firstVisibleItemIndex!=0 || gridState.firstVisibleItemScrollOffset!=0) gridState.scrollToItem(0) }')
ui=swap(ui,'if(paged.itemCount==0) Text(if(loading) "Loading catalogue…" else "No matching titles",','if(paged.itemCount==0) Text(if(loading) "Loading catalogue…" else if(catalogueError.isNotBlank()) "Catalogue unavailable — press OK on category to retry" else "No matching titles",')
s=s[:a]+ui+s[b:]
s=swap(s,'loading=catalogueLoading, onCategory={ categoryId=it },','loading=catalogueLoading, catalogueError=error, onCategory={ categoryId=it },')
# Guard the original conditional footer; the connected catalogue browser
# already renders loading, error and empty states in its fixed-size grid.
anchor='if((if(section=="Home")'
assert s.count(anchor)==1,"Original external empty-state anchor missing"
s=s.replace(anchor,'if((section=="Home" || !connected) && (if(section=="Home")',1)
p.write_text(s)
print("Static Movies/Series layout: fixed header and footer heights, in-grid status, no bottom Retry button.")
