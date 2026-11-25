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
s=swap(s,"GlassHeader(132.dp)",'''if(section=="Home" && selected==null) {
                    Image(painterResource(R.drawable.shark_home_premium),
                        contentDescription="Shark holding Sky Premium", modifier=Modifier.fillMaxWidth().height(126.dp),
                        contentScale=ContentScale.Fit)
                }''')
s=swap(s,'Box(Modifier.fillMaxSize().hudBackdrop())','Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF082F88),Color(0xFF061E55),Color(0xFF071B43)))))')
# Home uses the original navigation destinations with independent click
# targets, not a flattened screenshot. Non-Home pages keep the old nav.
# Header image and icon crops are prepared from the approved mockup.
a=s.index('Column(Modifier.weight(1f).height(64.dp).onFocusChanged')
b=s.index('Text(item,color=Color.White,fontSize=13.sp',a)
s=s[:b]+'''if(section=="Home") {
                                Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally) {
                                    val artwork = when(item) {
                                        "Shark TV" -> R.drawable.shark_icon_sky
                                        "Movies" -> R.drawable.shark_icon_movies
                                        "TV Series" -> R.drawable.shark_icon_series
                                        "Settings" -> R.drawable.shark_icon_settings
                                        "Home" -> R.drawable.shark_icon_sky
                                        "Shark Stream" -> R.drawable.shark_icon_live
                                        else -> 0
                                    }
                                    if(artwork!=0) Image(painterResource(artwork),null,
                                        Modifier.weight(1f).fillMaxWidth().padding(horizontal=4.dp),contentScale=ContentScale.Fit)
                                    else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                        Icon(Icons.Filled.SportsSoccer,null,tint=SharkFocusGold,modifier=Modifier.size(30.dp))
                                    }
                                    Text(if(item=="Shark TV") "Live TV" else item,color=Color.White,fontSize=12.sp,
                                        maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            } else '''+s[b:]
s=swap(s,'Column(Modifier.weight(1f).height(64.dp).onFocusChanged','Column(Modifier.weight(1f).height(if(section=="Home") 82.dp else 48.dp).onFocusChanged')
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
