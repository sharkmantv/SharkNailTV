from pathlib import Path
import re
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected 4.1.15 source: "+a[:150]
    return s.replace(a,b,1)

# Restore the simple 4.0.7-style playback policy while keeping all
# catalogue, graphics and navigation work. No preview ownership transfer.
p=root/"PlaybackSettings.kt"
s=p.read_text()
s=swap(s,'    val load = DefaultLoadControl.Builder().build()','''    val load = if(preview) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(6_000,15_000,1_000,2_500)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setTargetBufferBytes((if((context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice) 12 else 24)*1024*1024)
            .build()
    } else DefaultLoadControl.Builder().build()''')
s=swap(s,'val experimentalTunneling = !preview && p.getBoolean("sharkTunneling",false)','val experimentalTunneling = false')
s=swap(s,'if (!preview && !live && p.getBoolean("sharkPrewarm",false))','if (false)')
s=swap(s,'if (!preview && p.getBoolean("sharkCronet",false))','if (false)')
s=swap(s,'if(live && !preview && !experimentalTunneling && p.getBoolean("sharkChannelPreload",false))','if(false)')
s=swap(s,'                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)','''                setMaxVideoSize(960,540)
                setMaxVideoBitrate(1_500_000)
                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)''')
s=swap(s,'                SharkExperimentalPlaybackOptions(p)','                Text("Stable playback mode · experimental features disabled",color=Color.White,fontSize=12.sp)')
p.write_text(s)
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index("@Composable private fun LivePreview(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
ui=swap(ui,'        player.setMediaItem(item)','''        val fixedSpeed=item.liveConfiguration.buildUpon()
            .setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()
        player.setPlaybackSpeed(1f)
        player.setMediaItem(item.buildUpon().setLiveConfiguration(fixedSpeed).build())''')
s=s[:a]+ui+s[b:]
# Keep native SurfaceView (SDR tone mapping opt-in only), and remove the
# recently introduced repeated stop/prepare/surface switching watchdog.
a=s.index("@Composable private fun SafePlayer(")
b=s.find("\n@Composable",a+1)
if b<0: b=len(s)
ui=s[a:b]
start=ui.index("    var videoRetry by remember(url)")
end=ui.index("    BackHandler { when { panel.isNotEmpty()",start)
ui=ui[:start]+ui[end:]
# Do not request a new display rate while entering or leaving playback.
# Keep explicit display-mode settings and let the TV perform its own cadence.
s=s[:a]+ui+s[b:]
p.write_text(s)
p=root/"PlaybackSettings.kt"
s=p.read_text()
s,n=re.subn(r'setVideoChangeFrameRateStrategy\(if\([^\n]+\)', 'setVideoChangeFrameRateStrategy(androidx.media3.common.C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)',s,count=1)
assert n==1,"Frame rate strategy missing"
p.write_text(s)
print("Stable playback: fixed-speed preview, native fullscreen, disabled experimental playback paths.")

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
print("4.1.15: stable independent player paths, saved Live search, draw-only focus, target-size image loading, persistent title index.")


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
