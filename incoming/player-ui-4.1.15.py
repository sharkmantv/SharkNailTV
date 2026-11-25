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
