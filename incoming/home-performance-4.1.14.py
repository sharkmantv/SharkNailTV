from pathlib import Path
import re
import os
from zipfile import ZipFile

root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected 4.1.14 source: "+a[:150]
    return s.replace(a,b,1)

# Preview-only: match the stable fullscreen load control and adaptive live
# playback. Keep preview muted, text disabled and display-mode switching off.
p=root/"PlaybackSettings.kt"
s=p.read_text()
a=s.index("    val load = if (preview) {")
b=s.index("    val experimentalTunneling",a)
block=s[a:b]
assert ".setBufferDurationsMs(6_000, 15_000, 1_000, 2_500)" in block
assert "DefaultLoadControl.Builder().build()" in block
s=s[:a]+"    val load = DefaultLoadControl.Builder().build()\n"+s[b:]
# The old hard 540p / 1.5 Mbps target selects a different rendition from
# fullscreen, and the small fixed byte target can starve high-bitrate TS.
s=swap(s,"                setMaxVideoSize(960, 540)\n","")
s=swap(s,"                setMaxVideoBitrate(1_500_000)\n","")
assert "setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)" in s
assert "preview || !p.getBoolean" in s
p.write_text(s)

p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun LivePreview(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
ui=swap(ui,".apply { volume=0f; setPlaybackSpeed(1f) }",".apply { volume=0f }")
ui=swap(ui,'''        val fixedSpeed=item.liveConfiguration.buildUpon().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()
        player.setPlaybackSpeed(1f)
        player.setMediaItem(item.buildUpon().setLiveConfiguration(fixedSpeed).build())''','''        player.setMediaItem(item)''')
s=s[:a]+ui+s[b:]

# Keep lazy list/grid states alive across screen changes, but never share
# saved catalogue state between different IPTV account instances.
s=swap(s,'    var section by remember { mutableStateOf("Home") }','''    var section by remember { mutableStateOf("Home") }
    val navigationState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(api) {
        listOf("Home","Shark TV","Movies","TV Series","EPG / TV Guide","Settings","Sports Guide").forEach {
            navigationState.removeState(it)
        }
    }''')
if "import androidx.compose.runtime.saveable.rememberSaveable" not in s:
    s=swap(s,"import androidx.compose.activity.compose.setContent\n","import androidx.compose.activity.compose.setContent\nimport androidx.compose.runtime.saveable.rememberSaveable\n") if "import androidx.compose.activity.compose.setContent\n" in s else swap(s,"import androidx.activity.compose.setContent\n","import androidx.activity.compose.setContent\nimport androidx.compose.runtime.saveable.rememberSaveable\n")
a=s.index('                        "Movies", "TV Series" -> {')
start=s.rfind("when(section) {",0,a)
assert start>=0
end=s.index('if(!serviceCatalogue && section!="Settings" && section!="Shark TV"',a) if 'if(!serviceCatalogue && section!="Settings" && section!="Shark TV"' in s[a:] else -1
if end<0:
    # Original archive has no service-catalogue special case.
    m=re.search(r'if\s*\([^\n]*loading \|\| catalogueLoading[^\n]*\)\s*LinearProgressIndicator',s[a:])
    assert m,"Could not locate screen footer"
    end=a+m.start()
close=s.rfind("}",start,end)
assert close>start
s=s[:close+1]+"\n                    }"+s[close+1:]
s=s[:start]+'navigationState.SaveableStateProvider(section) {\n                    '+s[start:]
# Prevent dynamic footer rows from resizing the browser during requests.
s=re.sub(r'(?m)^([ ]*)if\(([^\n]*loading \|\| catalogueLoading[^\n]*)\) LinearProgressIndicator\(Modifier\.fillMaxWidth\(\)\)',r'\1if(section !in listOf("Movies","TV Series","Shark TV") && (\2)) LinearProgressIndicator(Modifier.fillMaxWidth())',s)
a=s.index("@Composable private fun TvCatalogueBrowser(")
b=s.index("@Composable private fun V4PosterRow(",a)
ui=s[a:b]
for old,new in [
('var query by remember { mutableStateOf("") }','var query by rememberSaveable { mutableStateOf("") }'),
('var searching by remember { mutableStateOf(false) }','var searching by rememberSaveable { mutableStateOf(false) }'),
('var alphabetical by remember { mutableStateOf(false) }','var alphabetical by rememberSaveable { mutableStateOf(false) }'),
('var appliedQuery by remember { mutableStateOf("") }','var appliedQuery by rememberSaveable { mutableStateOf(query) }')]:
    ui=swap(ui,old,new)
# Retain a restored grid offset. Reset only for a real category/search/sort change.
a0='LaunchedEffect(categoryId,appliedQuery,alphabetical) { if(gridState.firstVisibleItemIndex!=0 || gridState.firstVisibleItemScrollOffset!=0) gridState.scrollToItem(0) }'
ui=swap(ui,a0,'''var lastGridKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(categoryId,appliedQuery,alphabetical) {
        val gridKey=(categoryId.orEmpty()+"|"+appliedQuery+"|"+alphabetical)
        if(lastGridKey.isNotEmpty() && lastGridKey!=gridKey &&
           (gridState.firstVisibleItemIndex!=0 || gridState.firstVisibleItemScrollOffset!=0)) gridState.scrollToItem(0)
        lastGridKey=gridKey
    }''')
s=s[:a]+ui+s[b:]
# Remove the old triple animated logo on content pages. Keep the familiar
# blue Sky background. The approved new Home mascot is an isolated asset,
# not a baked-in screenshot or a clock.
s=swap(s,"GlassHeader(132.dp)","// Full-screen content: branding is confined to the dedicated Home menu.")
# The approved archive can have multiple uses of hudBackdrop; scope to the Home background.
a=s.index('@Composable private fun HomeHeroBackground(')
b=s.find('\n@Composable',a+1)
if b<0: b=len(s)
ui=s[a:b]
if 'Box(Modifier.fillMaxSize().hudBackdrop())' in ui:
    ui=swap(ui,'Box(Modifier.fillMaxSize().hudBackdrop())','Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF082F88),Color(0xFF061E55),Color(0xFF071B43)))))')
else:
    # Preserve the existing Sky-blue background if this revision already uses a custom fill.
    print("Existing Home background retained")
s=s[:a]+ui+s[b:]
# The actual 4.1.13 archive uses a dedicated five-tile Home menu.
# Keep the original click targets, focus requester and sizing.
a=s.index('val labels = listOf("Shark TV", "Movies", "TV Series", "Sports Guide", "Settings")') if 'val labels = listOf("Shark TV", "Movies", "TV Series", "Sports Guide", "Settings")' in s else s.index('listOf("Shark TV", "Movies", "TV Series", "Sports Guide", "Settings")')
b=s.find('\n@Composable',a)
if b<0: b=len(s)
ui=s[a:b]
ui=swap(ui,'''            Box(Modifier.fillMaxWidth().height(16.dp),contentAlignment=Alignment.CenterEnd) {
                val clockContext=LocalContext.current
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(15_000L) } }
                Text(sharkClock(clockContext,now),color=Color.White,fontSize=12.sp,maxLines=1)
            }
''','')
ui=swap(ui,'''                AsyncImage(model=R.drawable.shark_brand, contentDescription=null, modifier=Modifier.weight(1f).fillMaxHeight(), contentScale=ContentScale.Fit)
                AsyncImage(model=R.drawable.sky_stream_design, contentDescription="Sky Stream", modifier=Modifier.weight(1.2f).fillMaxHeight(), contentScale=ContentScale.Fit)
                AsyncImage(model=R.drawable.shark_brand_right, contentDescription=null, modifier=Modifier.weight(1f).fillMaxHeight(), contentScale=ContentScale.Fit)''','''                AsyncImage(model=R.drawable.shark_home_premium, contentDescription="Shark holding Sky Premium", modifier=Modifier.fillMaxSize(), contentScale=ContentScale.Fit)''')
ui=swap(ui,'BoxWithConstraints(Modifier.fillMaxSize().hudBackdrop().padding(horizontal=18.dp, vertical=10.dp))','BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF082F88),Color(0xFF061E55),Color(0xFF071B43)))).padding(horizontal=18.dp, vertical=10.dp))')
ui=swap(ui,'ChromeIcon(symbols[index],iconSize)','''val artwork=when(label) {
                            "Shark TV" -> R.drawable.shark_icon_sky
                            "Movies" -> R.drawable.shark_icon_movies
                            "TV Series" -> R.drawable.shark_icon_series
                            "Settings" -> R.drawable.shark_icon_settings
                            else -> 0
                        }
                        if(artwork!=0) AsyncImage(model=artwork,contentDescription=null,
                            modifier=Modifier.fillMaxWidth().height(tileHeight*0.62f),contentScale=ContentScale.Fit)
                        else ChromeIcon(symbols[index],iconSize)''')
s=s[:a]+ui+s[b:]
# Keep Home branding static; no header animation or duplicated clock.
# Release fullscreen player and detach surface before returning; no forced
# software rendering, display-mode switch, sleep or added animation.
a=s.index("@Composable private fun SafePlayer(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
anchor='    BackHandler { when { panel.isNotEmpty()'
assert ui.count(anchor)==1
ui=ui.replace(anchor,'''    val exitPlayerView = remember(player) { arrayOfNulls<androidx.media3.ui.PlayerView>(1) }
    BackHandler { when { panel.isNotEmpty()''',1)
ui=swap(ui,'else -> onExit() } }','''else -> {
        player.pause()
        exitPlayerView[0]?.player=null
        onExit()
    } } }''')
# Reuse the reviewed PlayerView factory and SDR surface handling.
m=re.search(r'AndroidView\(factory=\{ctx->[^\n]*?onRelease=\{it\.player=null\},modifier=Modifier\.fillMaxSize\(\)\)',ui)
assert m,"Fullscreen AndroidView anchor not found"
old=m.group(0)
new=old.replace('update={it.player=player;', 'update={exitPlayerView[0]=it;it.player=player;').replace('onRelease={it.player=null}', 'onRelease={it.player=null;exitPlayerView[0]=null}')
assert new!=old and 'exitPlayerView[0]=it' in new
ui=swap(ui,old,new)
# Live fullscreen can enter READY with progressing audio but no video frame.
# A bounded video-specific watchdog rebinds the surface and reinitializes
# playback once, without changing ordinary VOD or healthy live playback.
ui=swap(ui,'    BackHandler { when { panel.isNotEmpty()', '''    var videoRetry by remember(url) { mutableStateOf(0) }
    LaunchedEffect(player,url,liveStream) {
        if(!liveStream) return@LaunchedEffect
        var readyWithoutFrameMs=0L
        while(!firstFrame && !failureSent) {
            delay(500L)
            val videoSelected=player.currentTracks.isTypeSelected(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            if(player.isPlaying && videoSelected && !firstFrame) {
                readyWithoutFrameMs+=500L
            } else {
                readyWithoutFrameMs=0L
            }
            if(readyWithoutFrameMs>=5_000L) {
                if(videoRetry==0) {
                    videoRetry=1
                    readyWithoutFrameMs=0L
                    playerError="Recovering video…"
                    android.util.Log.w("SharkPlayback","Live audio playing without first video frame; retrying surface")
                    val view=exitPlayerView[0]
                    player.pause()
                    player.stop()
                    view?.player=null
                    player.clearVideoSurface()
                    view?.player=player
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.playWhenReady=true
                } else {
                    playerError="Video unavailable — return to channels and retry"
                    player.pause()
                    android.util.Log.w("SharkPlayback","Live first video frame still absent after recovery")
                    break
                }
            }
        }
        if(firstFrame && videoRetry>0) playerError=""
    }
    BackHandler { when { panel.isNotEmpty()''')
s=s[:a]+ui+s[b:]
p.write_text(s)

# Assets must be present; never substitute an unrelated picture or release
# an incomplete redesign. No old APKs are used as build inputs.
assets=Path(os.environ["GITHUB_WORKSPACE"])/"incoming/SharkNailTV-4.1.14-home-assets.zip"
assert assets.is_file(),"Approved Home artwork ZIP missing"
res=Path("app/src/main/res/drawable-nodpi")
res.mkdir(parents=True,exist_ok=True)
names=("shark_home_premium.webp","shark_icon_movies.webp","shark_icon_settings.webp",
       "shark_icon_sky.webp","shark_icon_live.webp","shark_icon_series.webp")
with ZipFile(assets) as z:
    assert set(z.namelist())==set(names),"Unexpected approved artwork archive"
    for name in names:
        data=z.read(name)
        assert data[:4]==b"RIFF" and data[8:12]==b"WEBP"
        (res/name).write_bytes(data)
print("Preview now uses native buffer and adaptive stream selection; saved navigation and full-screen catalogue layout applied.")


# Upgrade the account-scoped SQLite disk cache. One bounded row per provider
# record avoids the Android CursorWindow size limit of the previous JSON blob.
# The cache is derived data, not the user's credentials. Import old v1 data
# lazily on successful read; keep the original cache intact for rollback.
p=root/"ProviderCache.kt"
s=p.read_text()
a=s.index("    private val file =")
b=s.index("    companion object {",a)
s=s[:a]+'''    private val context=context.applicationContext
    private val file = File(this.context.noBackupFilesDir, "provider-catalogue-v2.db")
    private val legacyFile=File(this.context.cacheDir,"provider-catalogue-v1.db")
    private val account = MessageDigest.getInstance("SHA-256")
        .digest(listOf(server.trim(), user, pass).joinToString("\\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun <T> database(fallback:T,block:(SQLiteDatabase)->T):T=synchronized(lock) {
        try {
            SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
                db.execSQL("PRAGMA foreign_keys=ON")
                db.execSQL("CREATE TABLE IF NOT EXISTS snapshots (account TEXT NOT NULL,item TEXT NOT NULL,stamp INTEGER NOT NULL,bytes INTEGER NOT NULL,count INTEGER NOT NULL,PRIMARY KEY(account,item))")
                db.execSQL("CREATE INDEX IF NOT EXISTS snapshots_stamp ON snapshots(stamp)")
                db.execSQL("CREATE TABLE IF NOT EXISTS records (account TEXT NOT NULL,item TEXT NOT NULL,position INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(account,item,position),FOREIGN KEY(account,item) REFERENCES snapshots(account,item) ON DELETE CASCADE)")
                db.execSQL("PRAGMA user_version=2")
                block(db)
            }
        } catch(_:Exception) { fallback }
    }

    fun read(key:String,ttl:Long):Pair<Long,JSONArray>? {
        val stored=database<Pair<Long,JSONArray>?>(null) { db ->
            val header=db.query("snapshots",arrayOf("stamp","count"),"account=? AND item=?",arrayOf(account,key),null,null,null).use { c ->
                if(c.moveToFirst()) c.getLong(0) to c.getInt(1) else null
            } ?: return@database null
            if(System.currentTimeMillis()-header.first !in 0 until ttl) return@database null
            val rows=JSONArray()
            db.query("records",arrayOf("payload"),"account=? AND item=?",arrayOf(account,key),null,null,"position ASC").use { cursor ->
                while(cursor.moveToNext()) rows.put(org.json.JSONTokener(cursor.getString(0)).nextValue())
            }
            if(rows.length()!=header.second) null else header.first to rows
        }
        if(stored!=null) return stored
        val legacy=synchronized(lock) {
            if(!legacyFile.exists()) null else try {
                SQLiteDatabase.openDatabase(legacyFile.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.query("cache",arrayOf("stamp","payload"),"account=? AND item=?",arrayOf(account,key),null,null,null).use { c ->
                        if(!c.moveToFirst()) null else {
                            val stamp=c.getLong(0)
                            if(System.currentTimeMillis()-stamp in 0 until ttl) stamp to JSONArray(c.getString(1)) else null
                        }
                    }
                }
            } catch(_:Exception) { null }
        }
        if(legacy!=null) writeSnapshot(key,legacy.second,legacy.first)
        return legacy
    }

    fun write(key:String,array:JSONArray) = writeSnapshot(key,array,System.currentTimeMillis())

    private fun writeSnapshot(key:String,array:JSONArray,stamp:Long) {
        if(array.length()>100_000) return
        // Avoid allocating another full serialized catalogue. Each record is
        // encoded once during insertion; abort atomically if a limit is hit.
        database(Unit) { db ->
            val now=System.currentTimeMillis()
            db.beginTransaction()
            try {
                db.delete("snapshots","stamp<? OR stamp>?",arrayOf((now-86_400_000L).toString(),now.toString()))
                db.delete("snapshots","account=? AND item=?",arrayOf(account,key))
                db.insertOrThrow("snapshots",null,ContentValues().apply {
                    put("account",account);put("item",key);put("stamp",stamp);put("bytes",0L);put("count",array.length())
                })
                var total=0L
                val insert=db.compileStatement("INSERT INTO records(account,item,position,payload) VALUES(?,?,?,?)")
                try {
                    for(i in 0 until array.length()) {
                        val payload=array.get(i).toString()
                        val bytes=payload.toByteArray(Charsets.UTF_8).size
                        if(bytes>256*1024) throw IllegalArgumentException("Oversized cache record")
                        total+=bytes
                        if(total>16L*1024L*1024L) throw IllegalArgumentException("Oversized cache snapshot")
                        insert.clearBindings()
                        insert.bindString(1,account);insert.bindString(2,key);insert.bindLong(3,i.toLong());insert.bindString(4,payload)
                        insert.executeInsert()
                    }
                } finally { insert.close() }
                db.update("snapshots",ContentValues().apply { put("bytes",total) },"account=? AND item=?",arrayOf(account,key))
                var used=db.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM snapshots",null).use { c -> c.moveToFirst(); c.getLong(0) }
                if(used>MAX_BYTES) {
                    val oldest=db.rawQuery("SELECT rowid,bytes FROM snapshots ORDER BY stamp ASC,rowid ASC",null).use { c ->
                        buildList { while(c.moveToNext() && used>MAX_BYTES) { add(c.getLong(0));used-=c.getLong(1) } }
                    }
                    oldest.forEach { db.delete("snapshots","rowid=?",arrayOf(it.toString())) }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    fun clear() {
        database(Unit) { db -> db.delete("snapshots","account=?",arrayOf(account));Unit }
        synchronized(lock) {
            if(legacyFile.exists()) try {
                SQLiteDatabase.openDatabase(legacyFile.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.delete("cache","account=?",arrayOf(account))
                }
            } catch(_:Exception) {}
        }
    }

'''+s[b:]
s=swap(s,"private const val MAX_BYTES = 16L * 1024L * 1024L","private const val MAX_BYTES = 48L * 1024L * 1024L")
p.write_text(s)
print("Persistent SQLite v2 cache: account isolation, atomic row-based snapshots, lazy legacy migration and bounded storage.")
