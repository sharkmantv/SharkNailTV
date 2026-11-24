from pathlib import Path
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected preview source: "+a[:130]
    return s.replace(a,b,1)
p=root/"PlaybackSettings.kt"
s=p.read_text()
s=swap(s,'.setBufferDurationsMs(4_000, 8_000, 500, 2_000)', '.setBufferDurationsMs(6_000, 15_000, 1_000, 2_500)')
s=swap(s,'.setTargetBufferBytes(8 * 1024 * 1024)', '.setTargetBufferBytes((if((context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice) 12 else 24) * 1024 * 1024)')
s=swap(s,'setVideoChangeFrameRateStrategy(if(p.getString("refreshRate","Auto")=="Auto")', 'setVideoChangeFrameRateStrategy(if(!preview && p.getString("refreshRate","Auto")=="Auto")')
s=swap(s,'.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!p.getBoolean("subtitles",true)).build()', '.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,preview || !p.getBoolean("subtitles",true)).build()')
p.write_text(s)
p=root/"MainActivity.kt"
s=p.read_text()
s=swap(s,'val player=remember { configuredPlayer(context, preview = true).apply { volume=0f } }','val player=remember { configuredPlayer(context, preview = true, live = true).apply { volume=0f; setPlaybackSpeed(1f) } }')
s=swap(s,'    LaunchedEffect(player,url) { failed=false; player.setMediaItem(sharkMediaItem(context,url,inferFormat=true));player.prepare();player.playWhenReady=true }','''    LaunchedEffect(player,url) {
        failed=false
        val item=sharkMediaItem(context,url,inferFormat=true)
        val fixedSpeed=item.liveConfiguration.buildUpon().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()
        player.setPlaybackSpeed(1f)
        player.setMediaItem(item.buildUpon().setLiveConfiguration(fixedSpeed).build())
        player.prepare()
        player.playWhenReady=true
    }''')
p.write_text(s)
print("Preview-only buffering, fixed playback speed and frame-rate settings applied. SDR surface unchanged.")

p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'orElse("40004")','orElse("40005")')
s=swap(s,'orElse("4.0.4")','orElse("4.0.5")')
p.write_text(s)
