from pathlib import Path
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected source: "+a[:100]
    return s.replace(a,b,1)
p=root/"MainActivity.kt"
s=p.read_text()
a=s.index('                        "Sharks Toolkit" -> {')
b=s.index('                        "API Configuration" ->',a)
old=s[a:b]
start=old.index('                            FocusAction("Playback profile')
end=old.index('                            FocusAction("Back to Settings")')
tools=old[start:end]
# Move the performance controls into their existing hub.
anchor='                            FocusAction("Back to Settings") { section="Settings" }\n                        }\n                        "System Health" -> {'
s=swap(s,anchor,tools+anchor)
a=s.index('                        "Sharks Toolkit" -> {')
b=s.index('                        "API Configuration" ->',a)
s=s[:a]+'''                        "Time" -> {
                            val timePrefs = playbackPrefs(context)
                            var clockFormat by remember { mutableStateOf(timePrefs.getString("clockFormat","24-hour").orEmpty()) }
                            Text("Time",color=Color.White,fontSize=26.sp)
                            listOf("12-hour" to "12 Hour", "24-hour" to "24 Hour").forEach { (value,label) ->
                                FocusAction((if(clockFormat==value) "✓ " else "")+label) {
                                    clockFormat=value
                                    timePrefs.edit().putString("clockFormat",value).apply()
                                }
                            }
                            FocusAction("Back to Settings") { section="Settings" }
                        }
'''+s[b:]
s=s.replace('"Sharks Toolkit"','"Time"')
# Allow the moved controls to scroll within Performance Hub.
s=swap(s,'"Sports Guide","Playback & Display","Settings","Shark Performance Hub"','"Sports Guide","Playback & Display","Settings"')
# Enable the user's fast-start setting in ordinary IPTV playback too.
s=swap(s,'liveStream=entry.kind=="live", programmes=epg, inferFormat=true,','liveStream=entry.kind=="live", programmes=epg, inferFormat=true, sharkMega=sharkMega,')
s=swap(s,'remember(context, sharkMega) { configuredPlayer(context, sharkMega = sharkMega) }','remember(context, sharkMega, liveStream) { configuredPlayer(context, sharkMega = sharkMega, live = liveStream) }')
s=swap(s,'PlayerView(ctx).apply {this.player=player;','sharkPlayerView(ctx).apply {this.player=player;')
s=swap(s,'factory={PlayerView(it).apply {useController=false;','factory={sharkPlayerView(it).apply {useController=false;')
# Existing 16dp header space now shows the selected clock; no tiles or logos change.
s=swap(s,'            Spacer(Modifier.height(16.dp))\n            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical=spacing)', '''            Box(Modifier.fillMaxWidth().height(16.dp),contentAlignment=Alignment.CenterEnd) {
                val clockContext=LocalContext.current
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(15_000L) } }
                Text(sharkClock(clockContext,now),color=Color.White,fontSize=12.sp,maxLines=1)
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical=spacing)''')
s=swap(s,'private fun displayTime(time:Long):String = SimpleDateFormat("EEE d MMM  •  HH:mm",Locale.getDefault()).format(Date(time))','''private fun displayTime(context:android.content.Context,time:Long):String =
    SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(Date(time)) + "  •  " + sharkClock(context,time)''')
s=s.replace('displayTime(event.startMillis)','displayTime(LocalContext.current,event.startMillis)')
s=swap(s,'private fun fixtureDisplayTime(event:PublicFixture):String {','private fun fixtureDisplayTime(context:android.content.Context,event:PublicFixture):String {')
s=swap(s,'    return "$dateLabel · '+chr(36)+'{event.time.take(5)} ('+chr(36)+'{if(event.time.endsWith(" UK")) "UK time" else "provider time"})"','''    val raw=event.time.take(5)
    val clock=if(playbackPrefs(context).getString("clockFormat","24-hour")=="12-hour") runCatching {
        val parsed=SimpleDateFormat("HH:mm",Locale.UK).apply { isLenient=false }.parse(raw)
        SimpleDateFormat("h:mm a",Locale.UK).format(checkNotNull(parsed))
    }.getOrDefault(raw) else raw
    return dateLabel+" · "+clock+" ("+(if(event.time.endsWith(" UK")) "UK time" else "provider time")+")"''')
s=swap(s,'Text(fixtureDisplayTime(event),','Text(fixtureDisplayTime(LocalContext.current,event),')
p.write_text(s)
p=root/"PlaybackSettings.kt"
s=p.read_text()
s=swap(s,'    sharkMega: Boolean = false\n','    sharkMega: Boolean = false,\n    live: Boolean = false\n')
s=swap(s,'if (hdr && android.os.Build.VERSION.SDK_INT >= 31) {','if (hdr && android.os.Build.VERSION.SDK_INT in 31..32) {')
s=swap(s,'            .setBufferDurationsMs(4_000, 8_000, 750, 2_000)','            .setBufferDurationsMs(4_000, 8_000, 500, 2_000)')
s=swap(s,'        val startMs = if (sharkMega) 1_000 else 1_500','        val startMs = if (live && sharkMega) 650 else if (sharkMega) 1_000 else 1_500')
s=swap(s,'androidx.media3.datasource.DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(20000)','androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(sharkPlaybackHttp)')
s=swap(s,'internal fun playbackPrefs(context:Context)=context.getSharedPreferences("playback_config",0)','''internal fun playbackPrefs(context:Context)=context.getSharedPreferences("playback_config",0)
private val sharkPlaybackHttp by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
internal fun sharkClock(context:Context,time:Long):String =
    java.text.SimpleDateFormat(if(playbackPrefs(context).getString("clockFormat","24-hour")=="12-hour") "h:mm a" else "HH:mm",java.util.Locale.UK).format(java.util.Date(time))

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun sharkPlayerView(context:Context):androidx.media3.ui.PlayerView {
    // TextureView uses Android compositor tone mapping on Android 13+.
    // Normal HDR output retains SurfaceView and its efficient direct video path.
    return if(android.os.Build.VERSION.SDK_INT >= 33 && playbackPrefs(context).getString("dynamicRange","Auto")=="Prefer SDR")
        android.view.LayoutInflater.from(context).inflate(R.layout.shark_sdr_player,null,false) as androidx.media3.ui.PlayerView
    else androidx.media3.ui.PlayerView(context)
}''')
s=swap(s,'Auto follows the stream. Prefer SDR requests HDR-to-SDR conversion on supported Android 12+ decoders and prefers SDR alternatives. Unsupported devices may still display HDR. Prefer HDR favours HDR sources where available. Restart playback after changing.','Auto follows the stream. Prefer SDR uses Android compositor tone mapping on Android 13+; Android 12 requests decoder conversion. Device support varies. Prefer HDR favours HDR sources when available. Restart playback after changing.')
# Move the duplicate clock selection out of playback settings; keep reset from changing the separate time preference.
s=swap(s,'    var clockFormat by remember { mutableStateOf(p.getString("clockFormat","24-hour").orEmpty()) }\n','')
a=s.index('                Text("Clock format",color=Color.White)\n')
b=s.index('                PremiumButton(compact=true,onClick={subtitles=',a)
s=s[:a]+s[b:]
s=swap(s,'p.edit().clear().apply();streamFormat="Auto";clockFormat="24-hour";','p.edit().clear().putString("clockFormat",p.getString("clockFormat","24-hour")).apply();streamFormat="Auto";')
p.write_text(s)
layout=Path("app/src/main/res/layout/shark_sdr_player.xml")
layout.parent.mkdir(parents=True,exist_ok=True)
layout.write_text('''<?xml version="1.0" encoding="utf-8"?>
<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:surface_type="texture_view"
    app:use_controller="false" />
''')
p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'orElse("40002")','orElse("40003")')
s=swap(s,'orElse("4.0.2")','orElse("4.0.3")')
s=swap(s,'    implementation("androidx.media3:media3-ui:$media3Version")','    implementation("androidx.media3:media3-ui:$media3Version")\n    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")')
p.write_text(s)
print("4.0.3 patch applied; original Home graphics retained.")
