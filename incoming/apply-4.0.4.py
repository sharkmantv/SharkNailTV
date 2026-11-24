from pathlib import Path
import re

root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected source: "+a[:150]
    return s.replace(a,b,1)

p=root/"PlaybackSettings.kt"
s=p.read_text()
assert 'R.layout.shark_sdr_player' in s
# The same-version FFmpeg extension is an audio fallback only.
s=swap(s,'    }.setEnableDecoderFallback(p.getBoolean("fallback",true))','''    }.setEnableDecoderFallback(p.getBoolean("fallback",true))
    factory.setExtensionRendererMode(
        if (!preview && p.getBoolean("fallback",true))
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
    )''')
# Fast startup without increasing RAM allocation on smaller Android devices.
s=swap(s,'    val min=when(p.getString("buffer","Normal")){"Small"->10000;"Large"->50000;else->25000}','''    val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val lowMemory = memory.isLowRamDevice || memory.memoryClass <= 128
    val min=when(p.getString("buffer","Normal")){"Small"->10000;"Large"->50000;else->if (live && lowMemory) 12_000 else 25_000}''')
s=swap(s,'            else -> 60_000\n        }','            else -> if (live && lowMemory) 30_000 else 60_000\n        }')
s=swap(s,'        val startMs = if (live && sharkMega) 650 else if (sharkMega) 1_000 else 1_500','        val startMs = if (live && sharkMega) 500 else if (sharkMega) 1_000 else 1_500')
s=swap(s,'            .setPrioritizeTimeOverSizeThresholds(true)\n            .build()','''            .setPrioritizeTimeOverSizeThresholds(!(live && lowMemory && p.getString("buffer","Normal") != "Large"))
            .build()''')
s=swap(s,'Text("Built-in Media3 playback engine",color = Color.White,fontSize=14.sp)','''Text("Built-in Media3 playback engine · FFmpeg audio fallback",color = Color.White,fontSize=14.sp)''')
p.write_text(s)

p=root/"MainActivity.kt"
s=p.read_text()
s=swap(s,'class MainActivity:ComponentActivity(){ override fun onCreate','''class MainActivity:ComponentActivity(){
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        if(level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            coil.Coil.imageLoader(this).memoryCache?.clear()
            HomePlayback.clearCaches()
            XtreamApi.trimAllMemory()
        }
    }
    override fun onCreate''')
s=swap(s,'    private val entryCache=object:android.util.LruCache<String,Pair<Long,List<Entry>>>(20_000) {','''    private val memory = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    private val entryCache=object:android.util.LruCache<String,Pair<Long,List<Entry>>>(if(memory.isLowRamDevice || memory.memoryClass <= 128) 8_000 else 20_000) {''')
s=swap(s,'    private companion object { val sharedClient=OkHttpClient.Builder().callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build() }','''    init { synchronized(activeCaches) { activeCaches.add(this) } }
    companion object {
        private val activeCaches = java.util.Collections.newSetFromMap(java.util.WeakHashMap<XtreamApi,Boolean>())
        private val sharedClient=OkHttpClient.Builder().callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build()
        internal fun trimAllMemory() {
            val caches = synchronized(activeCaches) { activeCaches.toList() }
            caches.forEach { api ->
                api.categoryCache.evictAll()
                api.entryCache.evictAll()
                api.programmeCache.evictAll()
            }
        }
    }''')
assert s.count('.size(coil.size.Size.ORIGINAL)')==2
s=s.replace('.size(coil.size.Size.ORIGINAL)','') # Coil decodes to the actual display/view bounds.
# Cancel stale callbacks on channel changes, and retry only transient network errors.
s=swap(s,'''                }else {playerError="Playback failed ('''+chr(36)+'''{error.errorCodeName})";reportFailure()}''','''                } else if(liveStream && player.playWhenReady && recoveryCount<3 &&
                    (error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                     error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)) {
                    recoveryCount++;lastRecovery=now
                    playerError="Connection interrupted — reconnecting"
                    val waitMs=500L*recoveryCount
                    handler.postDelayed({
                        if(player.playWhenReady && player.playerError!=null) {
                            playerError=""
                            player.seekToDefaultPosition()
                            player.prepare()
                        }
                    },waitMs)
                } else {playerError="Playback failed ('''+chr(36)+'''{error.errorCodeName})";reportFailure()}''')
s=swap(s,'            override fun onPlaybackStateChanged(state:Int){android.util.Log.d("SharkPlayback","State '+chr(36)+'state after '+chr(36)+'{android.os.SystemClock.elapsedRealtime()-started} ms")}','''            override fun onPlaybackStateChanged(state:Int){
                if(state==androidx.media3.common.Player.STATE_READY) playerError=""
                android.util.Log.d("SharkPlayback","State "+state+" after "+(android.os.SystemClock.elapsedRealtime()-started)+" ms")
            }''')
s=swap(s,'playing=player.isPlaying;delay(500) } }','playing=player.isPlaying;delay(if(liveStream) 1000L else 500L) } }')
# Keep the preview player reused; use a single scoped retry handler.
s=swap(s,'''    DisposableEffect(player,url) {
        var recoveryCount=0
        var lastRecovery=0L
        val listener=object:androidx.media3.common.Player.Listener {''','''    DisposableEffect(player,url) {
        val handler=android.os.Handler(player.applicationLooper)
        var recoveryCount=0
        var lastRecovery=0L
        val listener=object:androidx.media3.common.Player.Listener {''')
s=swap(s,'''                }else failed=true
            }
        };player.addListener(listener);onDispose {player.removeListener(listener)}''','''                } else if(player.playWhenReady && recoveryCount<2 &&
                    (error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                     error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)) {
                    recoveryCount++;lastRecovery=now
                    handler.postDelayed({
                        if(player.playWhenReady && player.playerError!=null) {
                            failed=false
                            player.seekToDefaultPosition()
                            player.prepare()
                        }
                    },750L*recoveryCount)
                } else failed=true
            }
            override fun onPlaybackStateChanged(state:Int) {
                if(state==androidx.media3.common.Player.STATE_READY) failed=false
            }
        };player.addListener(listener);onDispose {handler.removeCallbacksAndMessages(null);player.removeListener(listener)}''')
p.write_text(s)

p=root/"HomePlayback.kt"
s=p.read_text()
# Bound large, expensive full-catalogue memory caches without discarding requested results.
s=swap(s,'            synchronized(catalogues) { catalogues.getOrPut(api){mutableMapOf()}[tv]=android.os.SystemClock.elapsedRealtime() to fresh }','''            synchronized(catalogues) {
                if(fresh.size<=20_000) catalogues.getOrPut(api){mutableMapOf()}[tv]=android.os.SystemClock.elapsedRealtime() to fresh
                else catalogues[api]?.remove(tv)
            }''')
p.write_text(s)

# Remove only known obsolete resources that have no reference in the actual source tree.
# Do not remove current UI, launcher, branding, or any dynamically named assets.
main=Path("app/src/main")
source="\n".join(f.read_text(errors="ignore") for f in main.rglob("*") if f.suffix in (".kt",".java",".xml"))
assert "getIdentifier(" not in source, "Dynamic resource lookup requires manual cleanup review"
obsolete={
"shark_glass_impact","shark_intro_sound","shark_intro_150","shark_intro_149",
"launcher_banner_v2","shark_tv_banner_v3","heading_sports","cinema_uhd",
"metal48_movies","cinema_live","style3_apple","launcher_icon_v2","style3_disney",
"volcanic_ocean_47","shark_tv_banner_v4","fresh_hero","tv_banner","cinema_movies",
"channel_shark","style3_max","style3_netflix","cinema_series","metal48_exit",
"premium_disney","tv_banner_image","premium_max","style3_prime","marquee_disney",
"metal48_sports","service_apple_tile","style3_hulu","metal48_live","ocean_clean",
"heading_movies","marquee_netflix","metal48_home","themed_marquee_hulu","heading_series",
"marquee_prime","themed_marquee_disney","metal48_guide","cinema_exit","themed_marquee_apple",
"premium_netflix","marquee_max","metal48_codes","service_hulu_tile","cinema_settings",
"glass_centered","cinema_home","premium_apple","marquee_apple","marquee_hulu",
"metal48_series","premium_hulu","cinema_sports","metal48_settings","premium_prime",
"service_netflix_tile","shark_logo","themed_marquee_netflix","service_prime_tile",
"themed_marquee_prime","service_hbo_tile","themed_marquee_max","style3_nuvio"
}
reclaimed=0
removed=0
for f in (main/"res").rglob("*"):
    if f.is_file() and f.stem in obsolete and f.suffix in (".png",".jpg",".webp",".wav") and f.stem not in source:
        reclaimed+=f.stat().st_size
        removed+=1
        f.unlink()
print("Removed",removed,"unreferenced legacy media files;",reclaimed,"bytes")
p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'orElse("40003")','orElse("40004")')
s=swap(s,'orElse("4.0.3")','orElse("4.0.4")')
s=swap(s,'    implementation("androidx.media3:media3-ui:'+chr(36)+'media3Version")','''    implementation("androidx.media3:media3-ui:'''+chr(36)+'''media3Version")
    implementation("io.put:media3-ffmpeg-decoder:1.11.1+1")''')
p.write_text(s)
assets=main/"assets"
assets.mkdir(exist_ok=True)
(assets/"ffmpeg-source-notice.txt").write_text("""SharkNailTV includes Media3's FFmpeg audio decoder extension via io.put:media3-ffmpeg-decoder:1.11.1+1.
Media3 wrapper: Apache License 2.0. FFmpeg: LGPL-2.1-or-later.
Source, patches, license notices and build instructions: https://github.com/putdotio/media3-ffmpeg-decoder
Media3 source: https://github.com/androidx/media/tree/1.11.1
FFmpeg source: https://github.com/FFmpeg/FFmpeg
Users may modify or replace the LGPL component for their own use.
""")
print("4.0.4 source patch complete: preserved SDR surface, Time menu and existing Home artwork.")
