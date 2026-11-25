from pathlib import Path
import re
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected 4.1.15 source: "+a[:150]
    return s.replace(a,b,1)

# Main-thread handoff. Only an explicitly requested same-URL preview that has
# actually rendered video may transfer ownership. No credentials are logged.
(root/"SharkPreviewHandoff.kt").write_text('''package com.example.xtreamtv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object SharkPreviewHandoff {
    private class Slot(val player:ExoPlayer,val url:String) {
        var rendered=false
        var disposed=false
        var claimed=false
        var view:PlayerView?=null
    }
    private var active:Slot?=null
    private var pending:Slot?=null
    private val handler=Handler(Looper.getMainLooper())

    fun register(player:ExoPlayer,url:String) {
        val previous=active
        if(previous!=null && previous.player===player && previous.url==url) return
        active=Slot(player,url).also { if(previous!=null && previous.player===player) it.view=previous.view }
    }
    fun bind(player:ExoPlayer,view:PlayerView):Boolean {
        val slot=active?.takeIf { it.player===player } ?: return true
        if(slot.claimed || pending===slot) return false
        slot.view=view
        return true
    }
    fun rendered(player:ExoPlayer,url:String) {
        active?.takeIf { it.player===player && it.url==url }?.rendered=true
    }
    fun arm(context:Context,url:String) {
        val prefs=playbackPrefs(context)
        // These modes have different renderer, connection or track-selector
        // configuration. Preserve them via the normal fullscreen path.
        if(prefs.getBoolean("sharkTunneling",false) ||
            prefs.getBoolean("sharkCronet",false) ||
            prefs.getBoolean("sharkChannelPreload",false)) return
        val slot=active ?: return
        if(slot.url!=url || !slot.rendered || slot.disposed || slot.claimed ||
           slot.player.playerError!=null || slot.player.playbackState!=Player.STATE_READY ||
           slot.player.currentMediaItem?.localConfiguration?.uri?.toString()!=url ||
           slot.view==null || pending!=null) return
        pending=slot
        // Detach the old view before the new view is attached. The player,
        // buffered stream and decoder remain owned by the handoff.
        slot.view?.player=null
        handler.postDelayed({
            if(pending===slot) {
                pending=null
                if(slot.disposed) sharkReleasePlayer(slot.player)
                else if(active===slot) {
                    // The original composition did not leave; its AndroidView
                    // may be rebound by the next normal update.
                    slot.rendered=false
                    slot.view?.player=slot.player
                }
            }
        },4000L)
    }
    fun take(context:Context,url:String):ExoPlayer? {
        val slot=pending ?: return null
        if(slot.url!=url || slot.player.playerError!=null) return null
        pending=null
        slot.claimed=true
        val p=slot.player
        return try {
            val prefs=playbackPrefs(context)
            p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!prefs.getBoolean("subtitles",true))
                .build()
            p.setVideoChangeFrameRateStrategy(if(prefs.getString("refreshRate","Auto")=="Auto")
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            p.volume=1f
            android.util.Log.d("SharkPlayback","Same-channel preview player transferred")
            p
        } catch(e:Exception) {
            // If ownership changed but transfer setup fails, release exactly
            // once and let the caller create its ordinary fullscreen player.
            sharkReleasePlayer(p)
            null
        }
    }
    fun dispose(player:ExoPlayer) {
        val slot=active?.takeIf { it.player===player }
        if(slot!=null) {
            active=null
            slot.disposed=true
            slot.view=null
            if(slot.claimed || pending===slot) return
        }
        sharkReleasePlayer(player)
    }
}
''')

p=root/"MainActivity.kt"
s=p.read_text()
# Explicit play action arms the handoff before Compose removes the preview.
s=swap(s,'lastLiveId=it.id;section="Shark TV";selected=it','''lastLiveId=it.id
                                    api?.let { provider -> SharkPreviewHandoff.arm(context,provider.url(it,sharkLiveFormat(context))) }
                                    section="Shark TV";selected=it''')
a=s.index("@Composable private fun LivePreview(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
ui=swap(ui,'    DisposableEffect(player,url) {','''    DisposableEffect(player,url) {
        SharkPreviewHandoff.register(player,url)''')
ui=swap(ui,'                renderedFrame=true','''                renderedFrame=true
                SharkPreviewHandoff.rendered(player,url)''')
# Register the native view when it is attached. Retain ordinary release for
# failed/dead/changed channels; only the armed slot survives disposal.
ui=swap(ui,'DisposableEffect(player){onDispose{player.release()}}','DisposableEffect(player){onDispose{SharkPreviewHandoff.dispose(player)}}')
ui=swap(ui,'update={it.player=player},onRelease={it.player=null}','update={SharkPreviewHandoff.bind(player,it);it.player=player},onRelease={it.player=null}')
# AndroidView update may precede the effect; bind from the effect as well.
ui=swap(ui,'    DisposableEffect(player,url) {','''    val previewNativeView=remember(player) { arrayOfNulls<androidx.media3.ui.PlayerView>(1) }
    DisposableEffect(player,url) {''')
ui=swap(ui,'        SharkPreviewHandoff.register(player,url)','''        SharkPreviewHandoff.register(player,url)
        previewNativeView[0]?.let { SharkPreviewHandoff.bind(player,it) }''')
ui=swap(ui,'update={SharkPreviewHandoff.bind(player,it);it.player=player}', 'update={previewNativeView[0]=it;if(SharkPreviewHandoff.bind(player,it)) it.player=player}')
s=s[:a]+ui+s[b:]
a=s.index("@Composable private fun SafePlayer(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
# Match the actual player creation call inside SafePlayer, preserving all
# existing arguments and the surrounding exception/fallback handling.
calls=list(re.finditer(r'\bconfiguredPlayer\(\s*context(?:\s*,[^()\n]*)?\)',ui))
assert len(calls)==1,"Expected one SafePlayer creation call; found "+str(len(calls))
m=calls[0]
original=m.group(0)
ui=ui[:m.start()]+'((if(liveStream) SharkPreviewHandoff.take(context,url) else null) ?: '+original+')'+ui[m.end():]
# Do not reset the media item for a transferred, still-prepared stream.
anchor='sharkSetChannelItem(player,sharkMediaItem(context,url,inferFormat));if(resumeMs>0L) player.seekTo(resumeMs);player.prepare();player.playWhenReady=true'
ui=swap(ui,anchor,'''val alreadyPrepared=liveStream &&
            player.currentMediaItem?.localConfiguration?.uri?.toString()==url &&
            player.playbackState!=androidx.media3.common.Player.STATE_IDLE &&
            player.playbackState!=androidx.media3.common.Player.STATE_ENDED &&
            player.playerError==null
        if(!alreadyPrepared) {
            sharkSetChannelItem(player,sharkMediaItem(context,url,inferFormat))
            if(resumeMs>0L) player.seekTo(resumeMs)
            player.prepare()
        }
        player.playWhenReady=true''')
# Recovery also covers an advertised video track that could not be selected.
ui=swap(ui,'val videoSelected=player.currentTracks.isTypeSelected(androidx.media3.common.C.TRACK_TYPE_VIDEO)',
    'val videoSelected=player.currentTracks.groups.any { it.type==androidx.media3.common.C.TRACK_TYPE_VIDEO }')
# Clear stale error and track state on URL change; do not wait on unrelated
# audio-only streams, which can legitimately have no advertised video.
s=s[:a]+ui+s[b:]

# Keep search fields through a fullscreen round trip in the live browser.
a=s.index("@Composable private fun LiveBrowser(")
b=s.index("@Composable private fun CompactLiveAction(",a)
ui=s[a:b]
ui=swap(ui,'var query by remember { mutableStateOf("") }','var query by rememberSaveable { mutableStateOf("") }')
ui=swap(ui,'var search by remember { mutableStateOf(false) }','var search by rememberSaveable { mutableStateOf(false) }')
s=s[:a]+ui+s[b:]

# Draw-only focus pulse. Neither composition nor layout reads the animation
# phase; border width, padding and all card dimensions remain constant.
a=s.rfind("@Composable",0,s.index("fun ElectricFocus(")) if "fun ElectricFocus(" in s else -1
assert a>=0
b=s.find("\n@Composable",s.index("fun ElectricFocus(",a)+1)
if b<0: b=len(s)
ui=s[a:b]
start=ui.index("    val firePhase = if (focused || selected) {")
end=ui.index("    Box(modifier.padding(2.dp)",start)
ui=ui[:start]+'''    val highlighted=focused || selected
    val phaseState=if(highlighted) {
        val motion=rememberInfiniteTransition(label="Draw-only gold focus")
        motion.animateFloat(0.35f,1f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="Gold opacity")
    } else null
    val frameColor=if(highlighted) SharkFocusGold else Color(0xFF56BFFF)
    val frameWidth=2.dp
'''+ui[end:]
ui=swap(ui,'color = frameColor,\n                    cornerRadius','color = frameColor.copy(alpha=phaseState?.value ?: 1f),\n                    cornerRadius')
s=s[:a]+ui+s[b:]
# Avoid allocating full-resolution background bitmaps for a display-sized
# image. Coil resolves the target dimensions from the actual measured view.
s,n=re.subn(r'\.size\(coil\.size\.Size\.ORIGINAL\)(?=\.build\(\))','',s)
print("Removed original-size image overrides:",n)
p.write_text(s)

# Cache duplicate snapshots efficiently. Keep v2 account/position indexes,
# atomic writes and original rollback cache. Add a persistent title index
# only for new records; migrate existing databases without clearing data.
# Indexed browsing can be introduced separately after provider tests.
p=root/"ProviderCache.kt"
s=p.read_text()
s=swap(s,'db.execSQL("PRAGMA user_version=2")','''if(db.version<3) {
                    val hasTitle=db.rawQuery("PRAGMA table_info(records)",null).use { c ->
                        var found=false
                        while(c.moveToNext()) if(c.getString(1)=="title_sort") found=true
                        found
                    }
                    if(!hasTitle) db.execSQL("ALTER TABLE records ADD COLUMN title_sort TEXT NOT NULL DEFAULT ''")
                    db.execSQL("CREATE INDEX IF NOT EXISTS records_title ON records(account,item,title_sort,position)")
                    db.version=3
                }''')
s=swap(s,'INSERT INTO records(account,item,position,payload) VALUES(?,?,?,?)',
    'INSERT INTO records(account,item,position,payload,title_sort) VALUES(?,?,?,?,?)')
s=swap(s,'insert.executeInsert()','''val obj=array.optJSONObject(i)
                        val title=obj?.optString("title").orEmpty().ifBlank { obj?.optString("name").orEmpty() }
                        insert.bindString(5,title.lowercase(java.util.Locale.ROOT))
                        insert.executeInsert()''')
# Index data on refresh; avoid rewriting the same snapshot for reads. The
# old on-disk data is still valid and no provider credentials are migrated.
p.write_text(s)
print("4.1.15: same-channel preview transfer, bounded video recovery, saved Live search, draw-only focus, target-size image loading, persistent title index.")


# Fullscreen rendering regression: both Live and VOD use SafePlayer, while
# the working preview uses a separate native view. Start fullscreen with a
# TextureView to avoid a SurfaceView/Compose compositor attachment issue.
# Fall back to SurfaceView once if a video track does not produce a frame.
# Do not touch the working preview or the current buffer/decoder libraries.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun SafePlayer(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
a0=ui.index('    var videoRetry by remember(url)')
b0=ui.index('    BackHandler { when { panel.isNotEmpty()',a0)
ui=ui[:a0]+'''    var videoRetry by remember(url) { mutableStateOf(0) }
    var fullscreenTexture by remember(player) { mutableStateOf(true) }
    LaunchedEffect(player,url,fullscreenTexture) {
        // Do not ask Android TV to switch its video frame-rate hint while
        // establishing the output surface. Keep the chosen display mode.
        player.setVideoChangeFrameRateStrategy(androidx.media3.common.C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
    }
    LaunchedEffect(player,url) {
        var readyWithoutFrameMs=0L
        while(!firstFrame && !failureSent) {
            delay(500L)
            val videoAdvertised=player.currentTracks.groups.any {
                it.type==androidx.media3.common.C.TRACK_TYPE_VIDEO
            }
            if(player.isPlaying && !firstFrame && videoAdvertised) readyWithoutFrameMs+=500L
            else readyWithoutFrameMs=0L
            if(readyWithoutFrameMs>=4_000L) {
                if(videoRetry==0) {
                    videoRetry=1
                    readyWithoutFrameMs=0L
                    playerError="Recovering video…"
                    android.util.Log.w("SharkPlayback","Fullscreen first frame missing; switching native video surface")
                    val resume=player.currentPosition.coerceAtLeast(0L)
                    val wasPlaying=player.playWhenReady
                    val oldView=exitPlayerView[0]
                    player.pause()
                    player.stop()
                    oldView?.player=null
                    exitPlayerView[0]=null
                    player.clearVideoSurface()
                    fullscreenTexture=!fullscreenTexture
                    // Wait only for the replacement AndroidView to be attached.
                    var attempts=0
                    while(exitPlayerView[0]==null && attempts++<40) delay(50L)
                    if(exitPlayerView[0]==null) {
                        playerError="Video surface unavailable — return and retry"
                        break
                    }
                    if(liveStream) player.seekToDefaultPosition() else player.seekTo(resume)
                    player.prepare()
                    player.playWhenReady=wasPlaying
                } else {
                    playerError="Video unavailable — return and retry"
                    player.pause()
                    android.util.Log.w("SharkPlayback","No fullscreen first frame after surface retry")
                    break
                }
            }
        }
        if(firstFrame && videoRetry>0) playerError=""
    }
'''+ui[b0:]
m=re.search(r'AndroidView\(factory=\{ctx->[^\n]*?onRelease=\{it\.player=null;exitPlayerView\[0\]=null\},modifier=Modifier\.fillMaxSize\(\)\)',ui)
assert m,"Fullscreen native view not found"
old=m.group(0)
# Use the already-shipped TextureView layout. Default surface fallback uses
# a plain PlayerView rather than selecting the same TextureView again.
new=old.replace('sharkPlayerView(ctx)', '(if(fullscreenTexture) android.view.LayoutInflater.from(ctx).inflate(R.layout.shark_sdr_player,null,false) as androidx.media3.ui.PlayerView else androidx.media3.ui.PlayerView(ctx))')
assert new!=old,"Unexpected fullscreen view factory"
new=new.replace('this.player=player;','exitPlayerView[0]=this;this.player=player;')
new=new.replace('onRelease={it.player=null;exitPlayerView[0]=null}', 'onRelease={it.player=null;if(exitPlayerView[0]===it) exitPlayerView[0]=null}')
ui=swap(ui,old,'key(fullscreenTexture) { '+new+' }')
s=s[:a]+ui+s[b:]
p.write_text(s)
print("All fullscreen modes now use independent TextureView rendering with bounded SurfaceView recovery and VOD position preservation.")


# 4K/HDR regression: retain the direct hardware SurfaceView path by default.
# Prefer SDR retains its original TextureView selection on Android 13+.
# Runtime decode failure uses the same bounded alternate-surface recovery
# as a missing first frame. No forced software decoding of HEVC/4K.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun SafePlayer(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
ui=swap(ui,'var fullscreenTexture by remember(player) { mutableStateOf(true) }','''var fullscreenTexture by remember(player) {
        mutableStateOf(android.os.Build.VERSION.SDK_INT>=33 &&
            playbackPrefs(context).getString("dynamicRange","Auto")=="Prefer SDR")
    }''')
# Declare retry state before the error listener so actual renderer failures can
# request recovery instead of immediately terminating the session.
ui=swap(ui,'    var videoRetry by remember(url) { mutableStateOf(0) }\n','')
ui=swap(ui,'    var failureSent by remember(url) { mutableStateOf(false) }','''    var failureSent by remember(url) { mutableStateOf(false) }
    var videoRetry by remember(url) { mutableStateOf(0) }
    var decoderRecoveryRequested by remember(url) { mutableStateOf(false) }''')
# Match the existing terminal error branch while preserving audio-track
# recovery and behind-live-window handling above it.
error=re.search(r'else\s*\{\s*playerError\s*=\s*"Playback failed \(\$\{error\.errorCodeName\}\)"\s*;\s*reportFailure\(\)\s*\}',ui)
assert error,"Decoder terminal error branch not found"
ui=swap(ui,error.group(0),'''else if(error.errorCode==androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED && videoRetry==0 &&
                    (renderer?.rendererFormat?.sampleMimeType?.startsWith("video/")==true ||
                     player.currentTracks.groups.any { it.type==androidx.media3.common.C.TRACK_TYPE_VIDEO })) {
                    firstFrame=false
                    decoderRecoveryRequested=true
                    playerError="Recovering video decoder…"
                    android.util.Log.w("SharkPlayback","Video decoder failed; requesting one alternate-surface recovery")
                } else {playerError="Playback failed ("+error.errorCodeName+")";reportFailure()}''')
ui=swap(ui,'while(!firstFrame && !failureSent) {\n            delay(500L)','''while(!failureSent) {
            delay(500L)
            if(firstFrame && !decoderRecoveryRequested) continue''')
ui=swap(ui,'if(readyWithoutFrameMs>=4_000L) {','if(decoderRecoveryRequested || readyWithoutFrameMs>=4_000L) {')
ui=swap(ui,'videoRetry=1\n                    readyWithoutFrameMs=0L','videoRetry=1\n                    decoderRecoveryRequested=false\n                    readyWithoutFrameMs=0L')
ui=swap(ui,'if(firstFrame && !decoderRecoveryRequested) continue','''if(firstFrame && !decoderRecoveryRequested) {
                if(videoRetry>0 && playerError=="Recovering video…") playerError=""
                readyWithoutFrameMs=0L
                continue
            }''')
s=s[:a]+ui+s[b:]
p.write_text(s)
print("4K/HDR compatibility: direct native output by default, bounded decoder-error recovery, no forced software HEVC.")

# Return-to-menu stability: SaveableStateHolder already restores lazy list
# positions. Do not immediately overwrite restored positions on re-entry.
# Delay the initial live preview as well as subsequent focus changes so
# the fullscreen decoder can dispose before a new preview starts.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun LiveBrowser(")
b=s.index("@Composable private fun CompactLiveAction(",a)
ui=s[a:b]
old='LaunchedEffect(categoryId, channels.isNotEmpty()) { if(channels.isNotEmpty()) { val i=filtered.indexOfFirst { it.id==lastId }; channelState.scrollToItem(i.coerceAtLeast(0)) } }'
if old not in ui:
    matches=re.findall(r'LaunchedEffect\(categoryId,\s*channels\.isNotEmpty\(\)\)[^\n]*',ui)
    print("Live channel scroll anchors:",matches)
    assert len(matches)==1,"Live channel scroll anchor not found"
    old=matches[0]
ui=swap(ui,old,'''var restoredLiveCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(categoryId,channels.isNotEmpty(),lastId) {
        if(channels.isEmpty()) return@LaunchedEffect
        val changed=restoredLiveCategory!=categoryId
        restoredLiveCategory=categoryId
        val index=filtered.indexOfFirst { it.id==lastId }
        androidx.compose.runtime.withFrameNanos { }
        val visible=channelState.layoutInfo.visibleItemsInfo.any { it.index==index }
        if(index>=0 && !visible) channelState.scrollToItem(index)
        else if(changed && index<0) channelState.scrollToItem(0)
    }''')
old='LaunchedEffect(categories) { val i=categories.indexOfFirst { it.first==categoryId }; if(i>=0 && categories.isNotEmpty())categoryState.scrollToItem(i) }'
if old not in ui:
    matches=re.findall(r'LaunchedEffect\(categories\)[^\n]*categoryState\.scrollToItem[^\n]*',ui)
    print("Live category scroll anchors:",matches)
    assert len(matches)==1,"Live category scroll anchor not found"
    old=matches[0]
ui=swap(ui,old,'''LaunchedEffect(categories,categoryId) {
        val index=uniqueCategories.indexOfFirst { it.first==categoryId }
        if(index>=0) {
            androidx.compose.runtime.withFrameNanos { }
            if(categoryState.layoutInfo.visibleItemsInfo.none { it.index==index }) categoryState.scrollToItem(index)
        }
    }''')
s=s[:a]+ui+s[b:]
p.write_text(s)
print("Live menu return: preserved restored positions; initial preview decoder start debounced.")


# Debounce initial player creation independently of the existing browser
# focus debounce. This only applies on composition entry, not every zap.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun LivePreview(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
start=ui.index("    val context=LocalContext.current")
ui=ui[:start]+'''    var initialPreviewReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400L); initialPreviewReady=true }
    if(!initialPreviewReady) return
'''+ui[start:]
s=s[:a]+ui+s[b:]
p.write_text(s)

# UK broadcast schedules: use source-provided station names only. No
# extra paid service, invented rights mapping or provider credentials.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("private object PublicSports")
b=s.index("@Composable private fun PublicGuide(",a)
ui=s[a:b]
ui=swap(ui,'val result=mutableListOf<PublicFixture>()','''val result=mutableListOf<PublicFixture>()
        val eventIds=mutableMapOf<String,String>()
        fun fixtureKey(name:String,date:String)=date+"|"+name.lowercase(Locale.ROOT)
            .replace(Regex(" (vs|versus|v|at) ")," ").replace(Regex("[^a-z0-9]"),"")
        val tvByEvent=mutableMapOf<String,MutableSet<String>>()
        val tvByName=mutableMapOf<String,MutableSet<String>>()''')
anchor='result.add(PublicFixture(name,date,time,"$sport · $league · broadcaster unverified"))'
assert ui.count(anchor)==2,"Unexpected fixture feed constructors"
ui=ui.replace(anchor,'''val id=o.optString("idEvent")
                if(id.isNotBlank() && id!="null") eventIds[fixtureKey(name,date)]=id
                '''+anchor,1)
# Fetch only seven days per existing two-hour guide refresh, off main thread.
# The free endpoint may return only one record or no coverage.
anchor='        if(successful==0)'
assert ui.count(anchor)==1
ui=ui.replace(anchor,'''        for(date in dates.take(7)) {
            val data=fetch("$base/eventstv.php?d=$date&a=United_Kingdom") ?: continue
            val listings=data.optJSONArray("tvevents") ?: data.optJSONArray("events") ?: continue
            for(i in 0 until listings.length()) {
                val item=listings.optJSONObject(i) ?: continue
                val country=item.optString("strCountry").replace("_"," ").trim()
                if(!country.equals("United Kingdom",true) && !country.equals("UK",true) && !country.equals("Great Britain",true)) continue
                val channel=item.optString("strChannel").trim()
                if(channel.isBlank() || channel=="null") continue
                val id=item.optString("idEvent")
                val name=item.optString("strEvent")
                val day=item.optString("dateEvent").ifBlank { date }
                if(day!=date) continue
                if(id.isNotBlank() && id!="null") tvByEvent.getOrPut(id) { linkedSetOf() }.add(channel)
                if(name.isNotBlank()) tvByName.getOrPut(fixtureKey(name,day)) { linkedSetOf() }.add(channel)
            }
        }
        val enriched=result.map { fixture ->
            val eventKey=fixtureKey(fixture.name,fixture.date)
            val channels=(eventIds[eventKey]?.let { tvByEvent[it] }.orEmpty()+tvByName[eventKey].orEmpty()).distinct()
            if(channels.isEmpty()) fixture
            else fixture.copy(channel=fixture.channel.substringBeforeLast(" · ")+" · UK TV: "+channels.joinToString(", "))
        }
'''+anchor,1)
ui=swap(ui,'val merged=result.distinctBy','val merged=enriched.sortedByDescending { it.channel.contains(" · UK TV: ") }.distinctBy')
s=s[:a]+ui+s[b:]
s=swap(s,'Text(event.channel.substringAfter(" · "),color = Color.White,fontSize=10.sp,maxLines=2)','''Text(event.channel.substringAfter(" · ").substringBeforeLast(" · "),color=Color.White,fontSize=10.sp,maxLines=2)
                            Text(event.channel.substringAfterLast(" · ").let { if(it=="broadcaster unverified") "UK TV: not listed" else it },
                                color=Color(0xFFFFD66B),fontSize=11.sp,maxLines=3)''')
p.write_text(s)
print("Sports Guide: UK broadcaster listings matched by event ID or exact normalized event/date; unavailable listings remain explicitly unknown.")

# Approved original shark and individual alpha PNG icons are mandatory.
# Never crop a screenshot or silently use old artwork.
import os, hashlib
from zipfile import ZipFile
assets=Path(os.environ["GITHUB_WORKSPACE"])/"incoming/SharkNailTV-4.1.15-home-png.zip"
assert assets.is_file(),"Approved 4.1.15 transparent artwork ZIP missing"
assert hashlib.sha256(assets.read_bytes()).hexdigest()=="2fce8ef45b164a4d4cd988ec422d77ed466a936e7df1bd0b84a84b372fb13b51","Artwork archive SHA-256 mismatch"
res=Path("app/src/main/res/drawable-nodpi")
names=["shark15_"+x+".png" for x in ("hero","sky","movies","series","sports","settings")]
with ZipFile(assets) as z:
    assert set(z.namelist())==set(names) and z.testzip() is None
    for name in names:
        data=z.read(name)
        assert data[:8]==b"\x89PNG\r\n\x1a\n" and data[25]==6,"Not an RGBA PNG: "+name
        (res/name).write_bytes(data)
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index('listOf("Shark TV", "Movies", "TV Series", "Sports Guide", "Settings")')
b=s.find("\n@Composable",a)
if b<0: b=len(s)
ui=s[a:b]
for old,new in (("shark_home_premium","shark15_hero"),("shark_icon_sky","shark15_sky"),("shark_icon_movies","shark15_movies"),("shark_icon_series","shark15_series"),("shark_icon_settings","shark15_settings")):
    ui=swap(ui,"R.drawable."+old,"R.drawable."+new)
ui=swap(ui,'"Settings" -> R.drawable.shark15_settings','"Sports Guide" -> R.drawable.shark15_sports\n                            "Settings" -> R.drawable.shark15_settings')
ui=swap(ui,'Color(0xFF082F88),Color(0xFF061E55),Color(0xFF071B43)','Color(0xFF050C3D),Color(0xFF06143E),Color(0xFF051740)')
ui=swap(ui,'modifier=Modifier.fillMaxSize(), contentScale=ContentScale.Fit)','modifier=Modifier.fillMaxHeight(0.85f).fillMaxWidth(0.6f), contentScale=ContentScale.Fit)')
ui=swap(ui,'Row(Modifier.fillMaxWidth().weight(1f).padding(vertical=spacing), verticalAlignment=Alignment.CenterVertically)','Row(Modifier.fillMaxWidth().weight(1f).padding(vertical=spacing), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically)')
ui=swap(ui,'height(tileHeight*0.62f)','height(tileHeight*0.72f)')
ui=swap(ui,'.border(if(focused) 2.dp else 1.dp,edge,shape)','.border(2.dp,edge,shape)')
s=s[:a]+ui+s[b:]
p.write_text(s)
print("Approved 4.1.15 alpha PNGs installed; original shark and five matched icons, fixed tile sizes and gold pulse.")


# Align the preview top edge with the first channel row rather than the
# Categories/Channels heading. Reuse the channel heading height.
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun LiveBrowser(")
b=s.index("@Composable private fun CompactLiveAction(",a)
ui=s[a:b]
head=re.search(r'Text\([^\n]*"Channels[^\n]*modifier=Modifier\.height\((\d+)\.dp\)',ui)
assert head,"Live channel header height not found"
height=head.group(1)
m=re.search(r'BoxWithConstraints\(Modifier\.weight\(0\.40f\)\.fillMaxHeight\(\)\)',ui)
assert m,"Live preview column not found"
ui=swap(ui,m.group(0),m.group(0)[:-1]+'.padding(top='+height+'.dp))')
s=s[:a]+ui+s[b:]
p.write_text(s)
print("Live preview top aligned to first channel row using shared header height "+height+"dp.")
