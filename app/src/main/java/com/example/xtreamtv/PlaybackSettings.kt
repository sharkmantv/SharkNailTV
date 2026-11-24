package com.example.xtreamtv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

private fun credentialKey(): SecretKey {
    val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("shark_login",null) as? SecretKey)?.let { return it }
    val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder("shark_login",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    return generator.generateKey()
}
internal fun savePassword(context:Context,password:String) {
    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE,credentialKey())
    context.getSharedPreferences("shark_credentials",0).edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("password",Base64.encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)) .commit().also { check(it) { "Credential storage failed" } }
}
internal fun readSavedPassword(context:Context):String = runCatching {
    val p=context.getSharedPreferences("shark_credentials",0)
    val iv=Base64.decode(p.getString("iv", "").orEmpty(),Base64.NO_WRAP)
    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE,credentialKey(),GCMParameterSpec(128,iv))
    String(cipher.doFinal(Base64.decode(p.getString("password", "").orEmpty(),Base64.NO_WRAP)),Charsets.UTF_8)
}.getOrDefault("")
internal fun playbackPrefs(context:Context)=context.getSharedPreferences("playback_config",0)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun configuredPlayer(
    context: Context,
    preview: Boolean = false,
    sharkMega: Boolean = false,
    liveStream: Boolean = false
): ExoPlayer {
    val p=playbackPrefs(context)
    val factory=object : DefaultRenderersFactory(context.applicationContext) {
        override fun getCodecAdapterFactory(): androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory {
            val delegate = super.getCodecAdapterFactory()
            return object : androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory by delegate {
                override fun createAdapter(configuration: androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration): androidx.media3.exoplayer.mediacodec.MediaCodecAdapter {
                    if (p.getString("dynamicRange","Auto") == "Prefer SDR" &&
                        configuration.format.sampleMimeType?.startsWith("video/") == true) {
                        val transfer = configuration.format.colorInfo?.colorTransfer
                        val hdr = transfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 ||
                            transfer == androidx.media3.common.C.COLOR_TRANSFER_HLG ||
                            configuration.format.sampleMimeType == "video/dolby-vision" ||
                            configuration.format.colorInfo?.hdrStaticInfo != null
                        // Do not let a known HDR decoder configure a surface in strict SDR mode.
                        // Home playback can recover using its existing source failover.
                        if (hdr) throw java.io.IOException("HDR source blocked by SDR mode; select an SDR source")
                    }
                    return delegate.createAdapter(configuration)
                }
            }
        }
    }.setEnableDecoderFallback(p.getBoolean("fallback",true))
    val decoder=p.getString("decoder","Hardware preferred")
    if(decoder!="Automatic") factory.setMediaCodecSelector(MediaCodecSelector { mime,secure,tunnel ->
        val all=MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunnel)
        if(!mime.startsWith("video/")) all else {
            val preferred=all.filter { if(decoder=="Software preferred") it.softwareOnly else it.hardwareAccelerated }
            if(p.getBoolean("fallback",true)) preferred + all.filterNot { it in preferred } else preferred
        }
    })
    val min=when(p.getString("buffer","Normal")){"Small"->10000;"Large"->50000;else->25000}
    val load = if (preview) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(4_000, 8_000, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setTargetBufferBytes(8 * 1024 * 1024)
            .build()
    } else {
        // A fixed 128 MB allocation on Shark Mega can pressure lower-memory
        // Android TV boxes. Let Media3 size the target to the selected tracks.
        // Keep enough forward buffer for jitter without delaying first playback.
        val max = when(p.getString("buffer","Normal")) {
            "Small" -> 30_000
            "Large" -> 90_000
            else -> 60_000
        }
        // Shark Mega is a fast-start profile, not a larger memory allocation.
        // Preserve the user's chosen forward-buffer size for stability.
        val startMs = when { liveStream -> 750; sharkMega -> 1_000; else -> 1_500 }
        val rebufferMs = if (sharkMega) 2_500 else 3_500
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, startMs, rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    return ExoPlayer.Builder(context.applicationContext,factory).setLoadControl(load).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context.applicationContext).setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context.applicationContext, androidx.media3.datasource.DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(20000)))).build().apply {
        setVideoChangeFrameRateStrategy(if(p.getString("refreshRate","Auto")=="Auto") androidx.media3.common.C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else androidx.media3.common.C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        trackSelectionParameters=trackSelectionParameters.buildUpon().apply {
            // Prefer AVC for SDR compatibility, or HEVC for HDR-capable alternatives.
            // A codec preference is not HDR-to-SDR tone mapping; streams with one
            // video track will continue to use that track.
            when(p.getString("dynamicRange","Auto")) {
                "Prefer SDR" -> setPreferredVideoMimeTypes(androidx.media3.common.MimeTypes.VIDEO_H264, androidx.media3.common.MimeTypes.VIDEO_H265)
                "Prefer HDR" -> setPreferredVideoMimeTypes(androidx.media3.common.MimeTypes.VIDEO_H265, androidx.media3.common.MimeTypes.VIDEO_H264)
            }
            if (preview) {
                setMaxVideoSize(960, 540)
                setMaxVideoBitrate(1_500_000)
                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
            }
        }.setPreferredAudioLanguage(p.getString("audioLanguage",null)).setPreferredTextLanguage(p.getString("textLanguage",null)).setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!p.getBoolean("subtitles",true)).build()

    }
}
internal fun applyDisplaySettings(activity:android.app.Activity) {
    val p=playbackPrefs(activity)
    val display=activity.windowManager.defaultDisplay
    val current=display.mode
    val requested=p.getString("resolution","Automatic")
    val height=resolutionHeight(requested.orEmpty())
    val rate=p.getString("refreshRate","Auto").orEmpty()
    val candidates=display.supportedModes.filter { if(height==0) it.physicalHeight==current.physicalHeight && it.physicalWidth==current.physicalWidth else it.physicalHeight==height }
    val chosen=when(rate) {
        "Adaptive" -> candidates.maxByOrNull { it.refreshRate }
        "Auto" -> if(height==0) null else candidates.minByOrNull { kotlin.math.abs(it.refreshRate-current.refreshRate) }
        else -> rate.toFloatOrNull()?.let { hz -> candidates.filter { kotlin.math.abs(it.refreshRate-hz)<0.6f }.maxByOrNull { it.physicalWidth } }
    }
    val attrs=activity.window.attributes
    attrs.preferredDisplayModeId=chosen?.modeId ?: 0
    attrs.preferredRefreshRate=0f
    activity.window.attributes=attrs

}
private fun resolutionHeight(value:String)=when(value){"720p"->720;"1080p"->1080;"1440p"->1440;"4K"->2160;"8K"->4320;else->0}
@Composable internal fun PlaybackSettings(onBack:()->Unit) {
    val context=LocalContext.current
    val p=playbackPrefs(context)
    var dynamicRange by remember { mutableStateOf(p.getString("dynamicRange","Auto").orEmpty()) }
    var uniformBarHeight by remember { mutableIntStateOf(p.getInt("uniformBarHeight",76)) }
    var resolution by remember { mutableStateOf(p.getString("resolution","Automatic").orEmpty()) }
    var refresh by remember { mutableStateOf(p.getString("refreshRate","Auto").orEmpty()) }
    var audioLanguage by remember { mutableStateOf(p.getString("audioLanguage",null)) }
    var textLanguage by remember { mutableStateOf(p.getString("textLanguage",null)) }
    val modes=remember { (context as? android.app.Activity)?.windowManager?.defaultDisplay?.supportedModes?.toList().orEmpty() }
    var streamFormat by remember { mutableStateOf(p.getString("streamFormat","Auto").orEmpty()) }
    var clockFormat by remember { mutableStateOf(p.getString("clockFormat","24-hour").orEmpty()) }
    var decoder by remember { mutableStateOf(p.getString("decoder","Hardware preferred").orEmpty()) }
    var smartMode by remember { mutableStateOf(p.getString("smartMode","Shark Smart").orEmpty()) }
    var smartAuto by remember { mutableStateOf(p.getBoolean("smartAuto",true)) }
    var buffer by remember { mutableStateOf(p.getString("buffer","Normal").orEmpty()) }
    var fallback by remember { mutableStateOf(p.getBoolean("fallback",true)) }
    var subtitles by remember { mutableStateOf(p.getBoolean("subtitles",true)) }
    var resize by remember { mutableStateOf(p.getInt("resize",0)) }
    Column(Modifier.fillMaxSize().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Playback & Display",color=Color.White,fontSize=20.sp)
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SettingsPanel("Display",Modifier.weight(1f)) {
                Text("Video dynamic range",color=Color.White)
                listOf("Auto","Prefer SDR","Prefer HDR").forEach { v ->
                    PremiumButton(compact=true,onClick={dynamicRange=v;p.edit().putString("dynamicRange",v).apply()},modifier=Modifier.fillMaxWidth()) {
                        Text((if(dynamicRange==v) "✓ " else "")+v)
                    }
                }
                Text("Auto follows the stream. Prefer SDR blocks detected HDR and Dolby Vision video instead of silently switching the TV to HDR. HDR-only titles may not play in SDR mode. Restart playback after changing.",color=Color.LightGray,fontSize=12.sp)
                Text("Resolution",color=Color.White)
                listOf("Automatic","720p","1080p","1440p","4K","8K").forEach { v ->
                    val supported=v=="Automatic" || modes.any { it.physicalHeight==resolutionHeight(v) }
                    PremiumButton(compact=true,enabled=supported,onClick={resolution=v;refresh="Auto";p.edit().putString("resolution",v).putString("refreshRate","Auto").apply()},modifier=Modifier.fillMaxWidth()) { Text((if(resolution==v) "✓ " else "")+v+(if(supported) "" else " · unavailable")) }
                }
                Text("Adaptive Hz · display mode",color=Color.White)
                val rates=adaptiveHzRates(context)
                (listOf("Auto","Adaptive")+rates.map { it.toString() }).forEach { v ->
                    PremiumButton(compact=true,onClick={refresh=v;p.edit().putString("refreshRate",v).apply()},modifier=Modifier.fillMaxWidth()) { Text((if(refresh==v) "✓ " else "")+when(v){"Auto"->"Auto (system)";"Adaptive"->"Adaptive (highest supported)";else->"$v Hz"}) }
                }
                Text("Reported modes: "+rates.joinToString { "$it Hz" },color=Color.White,fontSize=12.sp)
                val currentMode=(context as? android.app.Activity)?.windowManager?.defaultDisplay?.mode
                Text(currentMode?.let { "Current: ${it.physicalWidth} × ${it.physicalHeight} · ${it.refreshRate} Hz" }.orEmpty(),color=Color.White,fontSize=12.sp)

            }
            SettingsPanel("Playback",Modifier.weight(1f)) {
                Text("One Source Player · automatic failover",color=Color.White)
                PremiumButton(compact=true,onClick={smartAuto=!smartAuto;p.edit().putBoolean("smartAuto",smartAuto).apply()}) { Text("Try next source on failure: ${if(smartAuto) "On" else "Off"}") }
                Text("Tries one link at a time; switches on playback failure or sustained buffering. Source quality and server stability vary; no setting can guarantee buffer-free playback.",color=Color.LightGray,fontSize=12.sp)
                SmartPlayback.modes.forEach { mode -> PremiumButton(compact=true,onClick={smartMode=mode;p.edit().putString("smartMode",mode).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(smartMode==mode) "✓ $mode" else mode) } }
                Text("Shark Player",color=Color.White)
                Text("Built-in Media3 playback engine",color = Color.White,fontSize=14.sp)
                Text("HLS, DASH, RTSP and progressive playback. Audio and subtitle controls are available during playback.",color=Color.LightGray,fontSize=12.sp)
                Text("Stream format",color=Color.White)
                listOf("Auto","HLS (.m3u8)","MPEG-TS (.ts)","DASH (.mpd)","SmoothStreaming","RTSP","MP4 / progressive").forEach { v ->
                    PremiumButton(compact=true,onClick={streamFormat=v;p.edit().putString("streamFormat",v).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(streamFormat==v) "✓ $v" else v) }
                }
                Text("Auto is recommended; forced formats require matching streams.",color=Color.LightGray,fontSize=11.sp)
                listOf("Automatic","Hardware preferred","Software preferred").forEach { v -> PremiumButton(compact=true,onClick={decoder=v;p.edit().putString("decoder",v).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(decoder==v) "✓ $v" else v) } }
                Text("Buffer",color=Color.White)
                listOf("Small","Normal","Large").forEach { v -> PremiumButton(compact=true,onClick={buffer=v;p.edit().putString("buffer",v).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(buffer==v) "✓ $v" else v) } }
                PremiumButton(compact=true,onClick={fallback=!fallback;p.edit().putBoolean("fallback",fallback).apply()}) { Text("Fallback: ${if(fallback) "On" else "Off"}") }
            }
            SettingsPanel("Screen & audio",Modifier.weight(1f)) {
                Text("Advanced audio · compatibility",color=Color.White)
                Text("Media3 detects the audio tracks in each stream. Atmos, AC-3, E-AC-3, DTS, AAC, FLAC and PCM require compatible source, decoder and output device.",color=Color.LightGray,fontSize=12.sp)
                Text("Audio output (device managed)",color=Color.White)
                Text("HDMI passthrough and surround output are controlled by Android TV and your connected receiver. No forced Atmos switch is available.",color=Color.LightGray,fontSize=12.sp)
                Text("Clock format",color=Color.White)
                listOf("12-hour","24-hour").forEach { v -> PremiumButton(compact=true,onClick={clockFormat=v;p.edit().putString("clockFormat",v).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(clockFormat==v) "✓ $v" else v) } }
                PremiumButton(compact=true,onClick={subtitles=!subtitles;p.edit().putBoolean("subtitles",subtitles).apply()}) { Text("Subtitles: ${if(subtitles) "On" else "Off"}") }
                listOf("Fit" to 0,"Stretch" to 3,"Zoom" to 4).forEach { (name,mode) -> PremiumButton(compact=true,onClick={resize=mode;p.edit().putInt("resize",mode).apply()},modifier=Modifier.fillMaxWidth()) { Text(if(resize==mode) "✓ $name" else name) } }
                Text("Preferred audio language",color=Color.White)
                listOf(null,"en","es","fr","de","it").forEach { v -> PremiumButton(compact=true,onClick={audioLanguage=v;p.edit().putString("audioLanguage",v).apply()},modifier=Modifier.fillMaxWidth()) { Text((if(audioLanguage==v) "✓ " else "")+(v?:"Automatic")) } }
                Text("Preferred subtitle language",color=Color.White)
                listOf(null,"en","es","fr","de","it").forEach { v -> PremiumButton(compact=true,onClick={textLanguage=v;p.edit().putString("textLanguage",v).apply()},modifier=Modifier.fillMaxWidth()) { Text((if(textLanguage==v) "✓ " else "")+(v?:"Automatic")) } }
                PremiumButton(compact=true,onClick={p.edit().clear().apply();streamFormat="Auto";clockFormat="24-hour";decoder="Hardware preferred";smartMode="Shark Smart";smartAuto=true;buffer="Normal";fallback=true;subtitles=true;resize=0;dynamicRange="Auto";resolution="Automatic";refresh="Auto";audioLanguage=null;textLanguage=null}) { Text("Reset settings") }
                PremiumButton(compact=true,onClick=onBack){Text("Back to Settings")}
            }
        }
    }
}
@Composable internal fun SettingsPanel(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxHeight()
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF142544),Color(0xFF080F25))),RoundedCornerShape(0.dp))
        .hudSurface().glassFrame(1.dp, HudAccent).padding(10.dp)) {
        Text(title,modifier=Modifier.fillMaxWidth().background(Color(0xFF16425C),RoundedCornerShape(0.dp)).padding(8.dp),color=Color.White,fontSize=17.sp)
        Column(Modifier.weight(1f).padding(top=5.dp,bottom=5.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(3.dp),content=content)
    }
}

internal fun streamMimeType(context:Context):String? = when(playbackPrefs(context).getString("streamFormat","Auto")) {
    "HLS (.m3u8)" -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
    "MPEG-TS (.ts)" -> androidx.media3.common.MimeTypes.VIDEO_MP2T
    "DASH (.mpd)" -> androidx.media3.common.MimeTypes.APPLICATION_MPD
    "SmoothStreaming" -> androidx.media3.common.MimeTypes.APPLICATION_SS
    "RTSP" -> null // RTSP is selected by its rtsp:// URI scheme
    "MP4 / progressive" -> androidx.media3.common.MimeTypes.VIDEO_MP4
    else -> null
}
internal fun sharkMediaItem(context:Context,url:String,inferFormat:Boolean=false):androidx.media3.common.MediaItem =
    androidx.media3.common.MediaItem.Builder().setUri(url).apply { if (!inferFormat) streamMimeType(context)?.let { setMimeType(it) } }.build()
