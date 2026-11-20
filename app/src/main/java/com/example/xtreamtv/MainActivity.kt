package com.example.xtreamtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.annotation.ExperimentalCoilApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Text as MaterialText
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

@Composable private fun PlayerLifecycle(player:ExoPlayer) {
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,player) {
        var resume=false
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            when(event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { resume=player.playWhenReady; player.pause() }
                androidx.lifecycle.Lifecycle.Event.ON_START -> { if(resume) player.play(); resume=false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Runtime settings retained for Xtream catalogue and EPG requests. */
private object XtreamIntegrationConfig {
    var epgLimit: Int = 12
    var catalogueTimeout: Int = 25
}

private val navy = Color(0xFF061125)
private val blue = Color(0xFF168EF1)
private val gold = Color(0xFFD9B66C)
data class Entry(val id:String, val title:String, val image:String="", val category:String="", val kind:String="live", val container:String="m3u8", val backdrop:String="",val year:String="",val rating:String="",val genre:String="",val overview:String="")
private enum class ServiceGenre(val label: String, val movieId: Int, val seriesId: Int) {
    ACTION("Action", 28, 10759),
    ANIMATION("Animation", 16, 16),
    COMEDY("Comedy", 35, 35),
    CRIME("Crime", 80, 80),
    DOCUMENTARY("Documentary", 99, 99),
    DRAMA("Drama", 18, 18),
    SCI_FI("Sci-fi", 878, 10765)
}

data class Program(val title:String,val start:String,val end:String)
data class SportEvent(val channel:Entry,val program:Program,val startMillis:Long,val endMillis:Long)
class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); getSharedPreferences("xtream_integration",0).let { XtreamIntegrationConfig.epgLimit=it.getInt("epgLimit",12).coerceIn(1,24); XtreamIntegrationConfig.catalogueTimeout=it.getInt("catalogueTimeout",25).coerceIn(10,60) }; window.setFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED); applyDisplaySettings(this); SharkAppUpdater.check(this); setContent { App() } } }

/** Original full-screen shark artwork with cinematic glass impact. */
@Composable private fun SharkIntro(onFinished:()->Unit) {
    val emergence = remember { androidx.compose.animation.core.Animatable(0f) }
    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        emergence.animateTo(1f, tween(1250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(1050)
        fade.animateTo(0f, tween(450))
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF080A10)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.shark_intro_claws_background),
            "Original shark intro background",
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f + 0.04f * emergence.value
                scaleY = scaleX
                alpha = fade.value
            }, contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            val w = size.width
            val h = size.height
            val p = emergence.value
            val center = Offset(w*0.5f,h*0.45f)

            val impact = ((p-0.78f)/0.22f).coerceIn(0f,1f)
            for(i in 0 until 19) {
                val angle = i*2.39996f
                val length = h*(0.22f+(i%7)*0.075f)*impact
                val tip = Offset(center.x+kotlin.math.cos(angle)*length,center.y+kotlin.math.sin(angle)*length)
                drawLine(Color(0xFFBBCFE4),center,tip,strokeWidth=1.5f,alpha=impact*0.45f)
                val mid = Offset((center.x+tip.x)*0.5f,(center.y+tip.y)*0.5f)
                drawLine(Color(0xFFBBCFE4),mid,Offset(tip.x+h*0.035f,tip.y-h*0.05f),strokeWidth=1f,alpha=impact*0.35f)
            }
            drawCircle(Color(0xFFDAAF66),radius=h*0.38f,center=center,alpha=0.12f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2f))
        }
        Image(painterResource(R.drawable.shark_brand), "Original SharkNailTV logo",
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.48f).fillMaxHeight(0.30f).graphicsLayer {
                val p = emergence.value
                scaleX = 0.72f + 0.28f*p
                scaleY = scaleX
                alpha = fade.value
            }, contentScale = ContentScale.Fit)
    }
}

class XtreamApi(private val server:String,private val user:String,private val pass:String){
    private val client=sharedClient
    private val categoryCache=android.util.LruCache<String,Pair<Long,List<Pair<String,String>>>>(8)
    private val entryCache=android.util.LruCache<String,Pair<Long,List<Entry>>>(12)
    private val programmeCache=android.util.LruCache<String,Pair<Long,List<Program>>>(128)
    fun clearCatalogueCache() { categoryCache.evictAll(); entryCache.evictAll(); programmeCache.evictAll() }

    private companion object { val sharedClient=OkHttpClient.Builder().callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build() }
    // Accept a provider's server address or a complete get.php/player_api.php URL.
    // The caller's explicit username/password take precedence over URL query values.
    private val base=server.trim().let { if(it.startsWith("http://",true) || it.startsWith("https://",true)) it else "http://$it" }
        .substringBefore('?').trimEnd('/').removeSuffix("/player_api.php").removeSuffix("/get.php").trimEnd('/')
    private suspend fun <T> request(url: String, catalogue: Boolean = false, read: (okhttp3.Response) -> T): T {
        return try {
            execute(url, catalogue, read)
        } catch (error: java.io.IOException) {
            if (catalogue) throw error
            delay(700)
            execute(url, false, read)
        }
    }

    private suspend fun <T> execute(url: String, catalogue: Boolean, read: (okhttp3.Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            if (catalogue) call.timeout().timeout(XtreamIntegrationConfig.catalogueTimeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                    continuation.resumeWithException(error)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resumeWith(runCatching {
                        response.use {
                            continuation.context.ensureActive()
                            if (!it.isSuccessful) error("HTTP ${it.code}")
                            read(it)
                        }
                    })
                }
            })
        }

    private fun encoded(s:String)=URLEncoder.encode(s,"UTF-8")
    private suspend fun json(action:String,extra:String=""):Any {
        val url="$base/player_api.php?username=${encoded(user)}&password=${encoded(pass)}${if(action.isBlank()) "" else "&action=$action"}$extra"
        return request(url) { response ->
            val stream = response.body?.charStream() ?: error("Empty response")
            val body = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (body.length + count > 2_000_000) error("Response too large")
                body.append(buffer, 0, count)
            }
            JSONTokenerCompat.parse(body.toString())
        }
    }

    @Volatile private var liveFormats = emptySet<String>()

    suspend fun authenticate(): Boolean {
        val data = json("") as? JSONObject ?: return false
        val info = data.optJSONObject("user_info") ?: return false
        if (info.optString("auth") !in listOf("1", "true") || !info.optString("status", "Active").equals("Active", true)) return false
        liveFormats = info.optJSONArray("allowed_output_formats")?.let { formats ->
            (0 until formats.length()).map { formats.optString(it).trim().lowercase(Locale.ROOT) }.toSet()
        }.orEmpty()
        return true
    }
    suspend fun categories(action:String):List<Pair<String,String>> {
        categoryCache.get(action)?.takeIf { System.currentTimeMillis()-it.first<120000L }?.let { return it.second }
        val arr=json(action) as? JSONArray ?: error("Invalid categories")
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o -> o.optString("category_id").takeIf { it.isNotBlank() }?.let { it to o.optString("category_name",it) } } }.distinctBy { it.first }.also { categoryCache.put(action,System.currentTimeMillis() to it) }
    }
    suspend fun refreshProviderCategories() {
        val fresh=linkedMapOf<String,List<Pair<String,String>>>()
        for(action in listOf("get_live_categories","get_vod_categories","get_series_categories")) {
            val arr=json(action) as? JSONArray ?: error("Invalid categories")
            fresh[action]=(0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o -> o.optString("category_id").takeIf { it.isNotBlank() }?.let { it to o.optString("category_name",it) } } }.distinctBy { it.first }
        }
        clearCatalogueCache()
        fresh.forEach { (action,items) -> categoryCache.put(action,System.currentTimeMillis() to items) }
    }
    suspend fun entries(action:String,kind:String,categoryId:String?=null, titleFilter:((String)->Boolean)?=null):List<Entry> = withContext(Dispatchers.IO) {
        val url="$base/player_api.php?username=${encoded(user)}&password=${encoded(pass)}&action=$action${categoryId?.let { "&category_id=${encoded(it)}" }.orEmpty()}"
        val cacheKey="$action:$kind:$categoryId"
        if(titleFilter==null) entryCache.get(cacheKey)?.takeIf { System.currentTimeMillis()-it.first<120000L }?.let { return@withContext it.second }
        val requestContext = kotlinx.coroutines.currentCoroutineContext()
        val result = request(url, catalogue = true) { response ->
            val result = ArrayList<Entry>()
            val seen = HashSet<String>()
            val body=response.body ?: error("Empty response")
            android.util.JsonReader(body.charStream()).use { reader ->
                reader.beginArray()
                while(reader.hasNext()) {
                    requestContext.ensureActive()
                    if(reader.peek()!=android.util.JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
                    var id=""; var name="Untitled"; var image=""; var category=""; var extension="mp4"
                    reader.beginObject()
                    while(reader.hasNext()) {
                        val field=reader.nextName()
                        val textField = when (field) {
                            "stream_id", "series_id", "name", "stream_icon", "cover", "category_id", "container_extension" -> true
                            else -> false
                        }
                        if (textField && (reader.peek() == android.util.JsonToken.STRING || reader.peek() == android.util.JsonToken.NUMBER)) {
                            val value=reader.nextString()
                            when(field) { "stream_id","series_id" -> id=value; "name" -> name=value; "stream_icon","cover" -> image=value; "category_id" -> category=value; "container_extension" -> extension=value }
                        } else reader.skipValue()
                    }
                    reader.endObject()
                    if(id.isNotBlank() && (titleFilter==null || titleFilter(name)) && seen.add(id)) result.add(Entry(id,name,image.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),category,kind,extension))
                    if(result.size >= (if(titleFilter==null) 10000 else 100000)) break
                }
            }
            result
        }
        if(titleFilter==null) entryCache.put(cacheKey,System.currentTimeMillis() to result)
        result
    }
    suspend fun episodes(id:String):List<Entry> = withContext(Dispatchers.IO) {
        val data=json("get_series_info","&series_id=${encoded(id)}") as? JSONObject ?: error("Invalid series response")
        val seasons=data.optJSONObject("episodes") ?: return@withContext emptyList()
        val result=ArrayList<Entry>()
        for(season in seasons.keys().asSequence().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }) {
            val arr=seasons.optJSONArray(season) ?: continue
            for(i in 0 until arr.length()) { val e=arr.optJSONObject(i) ?: continue; val eid=e.optString("id"); if(eid.isNotBlank()) result.add(Entry(eid,"S$season E${e.optString("episode_num",(i+1).toString())} · ${e.optString("title","Episode")}",kind="episode",container=e.optString("container_extension","mp4"))) }
        }; result
    }
    suspend fun epg(streamId:String):List<Program>{ programmeCache.get(streamId)?.takeIf { System.currentTimeMillis()-it.first<120000L }?.let { return it.second }; val o=json("get_short_epg","&stream_id=${encoded(streamId)}&limit=${XtreamIntegrationConfig.epgLimit}") as? JSONObject?:return emptyList();val arr=o.optJSONArray("epg_listings")?:return emptyList();return (0 until arr.length()).mapNotNull { i-> val p=arr.optJSONObject(i)?:return@mapNotNull null;Program(decode(p.optString("title")),p.optString("start_timestamp").takeIf { it.toLongOrNull()?.let { n -> n>0 } == true } ?: p.optString("start"),p.optString("stop_timestamp").takeIf { it.toLongOrNull()?.let { n -> n>0 } == true } ?: p.optString("end")) }.also { programmeCache.put(streamId,System.currentTimeMillis() to it) } }
    private fun decode(s:String):String {
        // Xtream EPG titles may be Base64, but some providers return plain text.
        // Only decode canonical Base64 strings to avoid corrupting normal titles.
        val value=s.trim()
        if(value.length<4 || value.length%4!=0 || !value.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) return s
        return try {
            val decoded=android.util.Base64.decode(value,android.util.Base64.DEFAULT)
            val text=decoded.toString(Charsets.UTF_8)
            if(text.contains('\uFFFD') || text.any { it.isISOControl() && it!='\n' && it!='\t' }) s else text
        } catch(_:Exception) { s }
    }
    fun url(entry: Entry, format: String? = null): String {
        val folder = when (entry.kind) {
            "live" -> "live"
            "episode" -> "series"
            else -> "movie"
        }
        val formats = liveFormats
        val extension = when {
            entry.kind != "live" -> entry.container.ifBlank { "mp4" }
            format == "MPEG-TS (.ts)" -> "ts"
            format == "HLS (.m3u8)" -> "m3u8"
            "m3u8" !in formats && "hls" !in formats && ("ts" in formats || "mpegts" in formats) -> "ts"
            else -> "m3u8"
        }
        val baseUrl = base.toHttpUrl()
        return baseUrl.newBuilder().encodedPath(baseUrl.encodedPath.trimEnd('/') + "/")
            .addPathSegment(folder).addPathSegment(user).addPathSegment(pass)
            .addPathSegment("${entry.id}.$extension").build().toString()
    }

}
object JSONTokenerCompat {fun parse(s:String):Any {val t=s.trim();return if(t.startsWith("[")) JSONArray(t) else JSONObject(t)} }
@OptIn(ExperimentalCoilApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shark_settings", 0) }
    var exitPrompt by remember { mutableStateOf(false) }
    var introFinished by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(prefs.getString("server", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(readSavedPassword(context)) }
    var credentialStatus by remember { mutableStateOf("") }
    var tmdbKey by remember { mutableStateOf(prefs.getString("tmdb_key", "").orEmpty().trim().ifBlank { BuildConfig.TMDB_API_KEY.trim() }) }
    var sportsKey by remember { mutableStateOf(BuildConfig.SPORTS_API_KEY.ifBlank { prefs.getString("sports_key", "").orEmpty() }.trim()) }
    var tmdbDraft by remember { mutableStateOf(tmdbKey) }
    var tmdbStatus by remember { mutableStateOf("Loading posters…") }
    var fixtures by remember { mutableStateOf<List<PublicFixture>>(emptyList()) }
    var sportsRefresh by remember { mutableIntStateOf(0) }
    var posterRefresh by remember { mutableIntStateOf(0) }
    var refreshNotice by remember { mutableStateOf<String?>(null) }
    var fixtureStatus by remember { mutableStateOf("Loading sports schedule…") }
    LaunchedEffect(sportsKey,sportsRefresh) {
        var forceRefresh=sportsRefresh > 0
        while(true) {
            fixtureStatus = "Refreshing sports…"; try { fixtures = PublicSports.load(sportsKey, forceRefresh); fixtureStatus = if(fixtures.isEmpty()) "No fixtures returned" else "Football 7 days · UFC & Boxing 30 days"; if (sportsRefresh > 0) refreshNotice = "Sports refresh: $fixtureStatus" } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; fixtureStatus = "Sports listings unavailable: ${e.message ?: "connection error"}"; if (sportsRefresh > 0) refreshNotice = fixtureStatus }; forceRefresh=false; delay(7200000)
        }
    }
    var api by remember { mutableStateOf<XtreamApi?>(null) }
    var section by remember { mutableStateOf("Home") }
    val catalogueKind=when(section) { "Shark TV", "EPG / TV Guide" -> "live"; "Movies" -> "movie"; "TV Series" -> "series"; else -> "" }
    var catalogueRetry by remember { mutableIntStateOf(0) }
    var categories by remember(api, catalogueKind, catalogueRetry) { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var categoryId by remember(api, catalogueKind, catalogueRetry) { mutableStateOf<String?>(null) }
    var categoriesLoading by remember(api, catalogueKind, catalogueRetry) { mutableStateOf(api != null && catalogueKind.isNotBlank()) }
    var entriesLoading by remember(api, catalogueKind, catalogueRetry, categoryId) { mutableStateOf(categoryId != null) }
    val catalogueLoading = categoriesLoading || entriesLoading
    var categoryMenu by remember(api, catalogueKind, catalogueRetry) { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var serviceCategory by remember { mutableStateOf("Movies") }
    var serviceGenre by remember { mutableStateOf<ServiceGenre?>(null) }
    var serviceGenreMenu by remember { mutableStateOf(false) }
    var serviceEntries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var servicePageRequest by remember { mutableIntStateOf(0) }
    var serviceHasMore by remember { mutableStateOf(false) }
    var serviceLoading by remember { mutableStateOf(false) }
    var serviceError by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Entry?>(null) }
    var episodeParent by remember { mutableStateOf<Entry?>(null) }
    var lastLiveId by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var series by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var popularMovies by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var popularSeries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var epg by remember(api, selected) { mutableStateOf<List<Program>?>(null) }
    var sportEvents by remember { mutableStateOf<List<SportEvent>>(emptyList()) }
    var sportsLoading by remember { mutableStateOf(false) }
    var sportsFilter by remember { mutableStateOf("All Sports") }
    val sectionEntries = remember(section,trendingMovies,trendingSeries,popularMovies,popularSeries,movies,series,connected) {
        when(section) {
            "Movies" -> if(connected) movies else emptyList()
            "TV Series" -> if(connected) series else emptyList()
            else -> trendingMovies.zip(trendingSeries).flatMap { listOf(it.first,it.second) } + trendingMovies + trendingSeries
        }.distinctBy { "${it.kind}:${it.genre}:${it.id}" }
    }
    val heroArtworks = remember(sectionEntries) {
        sectionEntries.take(12).map { it.backdrop }.filter { it.isNotBlank() }.distinct()
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        context.deleteSharedPreferences("nuvio_tv_account_206")
        prefs.edit().remove("home_addon_urls").apply()
        val playback = playbackPrefs(context)
        if (playback.contains("nuvioSharkMega")) {
            val oldProfile = playback.getBoolean("nuvioSharkMega", true)
            val edit = playback.edit().remove("nuvioSharkMega")
            if (!playback.contains("sharkMega")) edit.putBoolean("sharkMega", oldProfile)
            edit.apply()
        }
        if(server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            loading=true
            try { val a=XtreamApi(server,username,password); if(a.authenticate()){api=a;connected=true}else error="Saved login rejected" }
            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException) throw e;error="Saved connection unavailable — retry in Xtream Codes"}
            finally {loading=false}
        }
    }
    val lastWorkingLinks = remember { android.util.LruCache<String, String>(100) }
    var reuseLastLink by remember {
        mutableStateOf(playbackPrefs(context).getBoolean("reuseLastLink", false))
    }
    var homeLookupStatus by remember { mutableStateOf("") }
    var homeSeason by remember { mutableIntStateOf(1) }
    var homeEpisode by remember { mutableIntStateOf(1) }
    var sharkMega by remember {
        val playback = playbackPrefs(context)
        mutableStateOf(playback.getBoolean("sharkMega", playback.getBoolean("nuvioSharkMega", true)))
    }
    fun homeLinkKey(entry: Entry): String =
        if (entry.genre.equals("TV Series", true)) "${entry.id}:$homeSeason:$homeEpisode"
        else "${entry.id}:movie"


    var homeStreams by remember { mutableStateOf<List<HomeStream>>(emptyList()) }
    var homePlaying by remember { mutableStateOf<HomeStream?>(null) }
    var homeLookupJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showHomeSources by remember { mutableStateOf(false) }
    var torrentPreparing by remember { mutableStateOf(false) }
    var autoSession by remember { mutableStateOf(false) }
    var autoResumeMs by remember { mutableStateOf(0L) }
    var failedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(homePlaying?.url) {
        val selectedSource = homePlaying ?: return@LaunchedEffect
        if (!selectedSource.url.startsWith("magnet:?")) return@LaunchedEffect
        torrentPreparing = true
        try {
            val ready = TorrentPlayback.prepare(context, selectedSource.url) { progress ->
                homeLookupStatus = progress
            }
            homePlaying = HomeStream(selectedSource.title, ready)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (homePlaying?.url == selectedSource.url && autoSession) {
                failedUrls = failedUrls + selectedSource.url
                homePlaying = homeStreams.firstOrNull { it.url !in failedUrls }
                showHomeSources = homePlaying == null
                homeLookupStatus = if (homePlaying == null) "All Home provider sources failed" else "Torrent failed — trying next Home provider source"
            } else {
                homePlaying = null
                showHomeSources = true
                homeLookupStatus = "Torrent failed: ${e.message ?: e.javaClass.simpleName}"
            }
        } finally {
            torrentPreparing = false
        }
    }

    fun fallbackHome(stream:HomeStream, resumeMs:Long) {
        if (homePlaying?.url != stream.url) return
        selected?.let { entry ->
            val key = homeLinkKey(entry)
            if (lastWorkingLinks.get(key) == stream.url) lastWorkingLinks.remove(key)
        }
        if (!autoSession) return
        autoResumeMs = maxOf(autoResumeMs, resumeMs)
        failedUrls = failedUrls + stream.url
        homePlaying = homeStreams.firstOrNull { it.url !in failedUrls }
        if(homePlaying==null) { showHomeSources=true; homeLookupStatus="All automatic sources failed" }
    }
    var instantMovieRequest by remember { mutableStateOf(0) }
    fun openTitle(entry: Entry) {
        homeLookupJob?.cancel()
        homePlaying = null
        homeStreams = emptyList()
        showHomeSources=false
        homeSeason = 1; homeEpisode = 1
        homeLookupStatus = ""
        selected = entry
        // Movies start directly on OK; series retain explicit episode selection.
        if (entry.kind == "tmdb" && entry.genre != "TV Series" && (section == "Home" || section.endsWith(" Catalogue")) && playbackPrefs(context).getBoolean("smartAuto",true)) instantMovieRequest++
        // Episode searches are explicit so the correct season/episode is always used.
        if (entry.kind == "tmdb" && (section == "Home" || section.endsWith(" Catalogue"))) homeLookupStatus = "Press Play to search your Home provider for this title."
    }
    fun searchHomeStreams(entry: Entry) {
        homeLookupJob?.cancel()
        homePlaying = null
        homeStreams = emptyList()
        failedUrls=emptySet(); autoResumeMs=0L; showHomeSources=false
        autoSession=playbackPrefs(context).getBoolean("smartAuto",true)
        val linkKey = homeLinkKey(entry)
        val season = homeSeason
        val episode = homeEpisode
        homeLookupStatus = "Searching Home providers for TMDB ${entry.id}…"
        homeLookupJob = scope.launch {
            try {
                val manifests = listOf(
                    prefs.getString("pengu_manifest", null)?.takeIf { it.isNotBlank() }
                        ?: BuildConfig.PENGUPLAY_MANIFEST_URL,
                    prefs.getString("aio_manifest", "").orEmpty()
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (manifests.isEmpty()) {
                    homeLookupStatus = "Add a Home provider in API Configuration."
                    return@launch
                }
                val penguplay = coroutineScope {
                    manifests.map { manifest ->
                        async {
                            try {
                                PenguPlay.lookup(manifest, entry, tmdbKey, season, episode)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                              catch (e: Exception) { emptyList<HomeStream>() }
                        }
                    }.awaitAll().flatten()
                }
                val matches = penguplay.distinctBy { it.url }
                val results = if (sharkMega) withContext(Dispatchers.Default) {
                    SmartPlayback.preferHardwareDecoders(matches)
                } else matches
                val cm=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val kbps=cm.getNetworkCapabilities(cm.activeNetwork)?.linkDownstreamBandwidthKbps ?: 0
                val rankingMode = playbackPrefs(context).getString("smartMode", "Shark Smart").orEmpty()
                // Direct HTTP(S) streams start in Media3 without torrent buffering.
                // Keep SmartPlayback's ordering within each group, but never select
                // a magnet ahead of an available direct stream for automatic play.
                val ranked = SmartPlayback.rank(
                    results, rankingMode, kbps,
                    (context as? android.app.Activity)?.windowManager?.defaultDisplay?.mode?.physicalHeight
                        ?: context.resources.displayMetrics.heightPixels,
                    playbackPrefs(context).getString("dynamicRange", "Auto").orEmpty()
                )
                homeStreams = ranked.filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) } +
                    ranked.filterNot { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
                // Always start from the current quality ranking. A previously working
                // link may be slower or lower quality than newly discovered sources.
                // Failed sources are excluded for the rest of this playback attempt.
                if (autoSession) {
                    homePlaying = homeStreams.firstOrNull()
                    if (homePlaying == null) showHomeSources = true
                } else {
                    showHomeSources = true
                }
                homeLookupStatus = if (results.isEmpty()) "No Home provider direct streams found for this title."
                    else "Home provider direct links: ${penguplay.size}"

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                homeLookupStatus = "Source lookup failed: ${e.javaClass.simpleName}"
            }
        }
    }
    LaunchedEffect(instantMovieRequest) {
        if (instantMovieRequest > 0) selected?.takeIf { it.kind == "tmdb" && it.genre != "TV Series" && (section == "Home" || section.endsWith(" Catalogue")) }?.let { searchHomeStreams(it) }
    }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var cacheTitle by remember { mutableStateOf("Cache cleaner") }
    var cacheBusy by remember { mutableStateOf(false) }
    var cacheStorageShortcut by remember { mutableStateOf(false) }
    fun cleanAppCache() {
        if (cacheBusy) return
        cacheTitle = "SharkNailTV cache cleanup"
        cacheStorageShortcut = false
        lastWorkingLinks.evictAll()
        cacheBusy = true
        val activeApi = api
        scope.launch {
            try {
                cacheMessage = withContext(Dispatchers.IO) {
                    val loader = coil.Coil.imageLoader(context)
                    val memory = loader.memoryCache
                    val disk = loader.diskCache
                    val memoryBefore = memory?.size?.toLong() ?: 0L
                    val diskBefore = disk?.size ?: 0L
                    memory?.clear()
                    disk?.clear()
                    activeApi?.clearCatalogueCache()
                    HomePlayback.clearCaches()
                    // Only remove known OTA cache filenames after a full day.
                    // Recent APKs may still be needed by Android's package installer.
                    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    val installCache = java.io.File(context.cacheDir, "install")
                    val staleOtaBytes = listOf("SharkNailTV.apk.part", "SharkNailTV.apk")
                        .sumOf { name ->
                            val file = java.io.File(installCache, name)
                            if (file.isFile && file.lastModified() in 1L until cutoff) {
                                val bytes = file.length()
                                if (file.delete()) bytes else 0L
                            } else 0L
                        }
                    val diskFreed = (diskBefore - (disk?.size ?: 0L)).coerceAtLeast(0L) + staleOtaBytes
                    val memoryFreed = (memoryBefore - (memory?.size?.toLong() ?: 0L)).coerceAtLeast(0L)
                    "Storage reclaimed: ${formatBytes(diskFreed)}\nImage memory cache cleared: ${formatBytes(memoryFreed)}"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                cacheMessage = "Cache cleanup failed. Please retry."
            } finally {
                cacheBusy = false
            }
        }
    }
    val livePreviewEnabled = playbackPrefs(context).getBoolean("livePreviewEnabled", true)
    var diagnostics by remember { mutableStateOf(false) }
    var testRun by remember { mutableIntStateOf(0) }
    var speedServer by remember { mutableStateOf(prefs.getString("libreSpeedServer", "").orEmpty()) }
    var speedReport by remember { mutableStateOf("") }
    var speedRunning by remember { mutableStateOf(false) }
    var speedJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun testDownload() {
        speedRunning = true
        speedReport = "Measuring download speed…"
        speedJob = scope.launch {
            try {
                speedReport = withContext(Dispatchers.IO) { LibreSpeedProbe.publicDownloadReport() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: java.io.IOException) {
                speedReport = "Speed test failed. Check internet access and retry."
            } finally {
                speedRunning = false
            }
        }
    }
    fun closeDiagnostics() {
        speedJob?.cancel()
        diagnostics = false
    }
    var networkReport by remember { mutableStateOf("Checking…") }
    LaunchedEffect(diagnostics,testRun) {
        if(diagnostics) {
            networkReport="Checking…"
            val activeApi=api
            networkReport=withContext(Dispatchers.IO) { connectionReport(context,activeApi) }
        }
    }
    LaunchedEffect(tmdbKey,posterRefresh) {
        tmdbStatus = "Loading posters…"
        if (tmdbKey.isBlank()) { tmdbStatus = "TMDB key missing. Open Settings."; if (posterRefresh > 0) refreshNotice = tmdbStatus; return@LaunchedEffect }
        coroutineScope {
            val movie = async { runCatching { TmdbApi.trending(tmdbKey, "movie") } }
            val tv = async { runCatching { TmdbApi.trending(tmdbKey, "tv") } }
            val pm = async { runCatching { TmdbApi.trending(tmdbKey, "movie", true) } }; val pt = async { runCatching { TmdbApi.trending(tmdbKey, "tv", true) } }
            val m = movie.await(); val t = tv.await()
            pm.await().getOrNull()?.let { popularMovies = it }; pt.await().getOrNull()?.let { popularSeries = it }
            m.getOrNull()?.let { trendingMovies = it }
            t.getOrNull()?.let { trendingSeries = it }
            tmdbStatus = listOfNotNull(m.exceptionOrNull()?.let { "Movies: ${it.javaClass.simpleName} ${it.message.orEmpty().substringBefore("https://")}" }, t.exceptionOrNull()?.let { "TV: ${it.javaClass.simpleName} ${it.message.orEmpty().substringBefore("https://")}" }).joinToString(" | ")
            if (posterRefresh > 0) refreshNotice = if (tmdbStatus.isNotBlank()) "Poster refresh: $tmdbStatus" else "Poster catalogue refreshed."
        }
    }
    LaunchedEffect(api,catalogueKind,catalogueRetry) {
        error=""
        live=emptyList(); movies=emptyList(); series=emptyList()
        val a=api ?: return@LaunchedEffect
        if(catalogueKind.isBlank()) return@LaunchedEffect
        categoriesLoading=true
        try { categories=a.categories(when(catalogueKind) { "live" -> "get_live_categories"; "movie" -> "get_vod_categories"; else -> "get_series_categories" }); categoryId=categories.firstOrNull()?.first ?: "" }
        catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error="Category request failed (${e.javaClass.simpleName})" } finally { categoriesLoading=false }
    }
    LaunchedEffect(api,catalogueKind,categoryId,catalogueRetry) {
        val a=api ?: return@LaunchedEffect
        val cat=categoryId ?: return@LaunchedEffect
        if(catalogueKind.isBlank()) return@LaunchedEffect
        error=""; entriesLoading=true
        when(catalogueKind) { "live" -> live=emptyList(); "movie" -> movies=emptyList(); else -> series=emptyList() }
        try {
            val entries=a.entries(when(catalogueKind) { "live" -> "get_live_streams"; "movie" -> "get_vod_streams"; else -> "get_series" },catalogueKind,cat.ifBlank { null })
            when(catalogueKind) { "live" -> live=entries; "movie" -> movies=entries; else -> series=entries }
        } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error="Catalogue request failed (${e.javaClass.simpleName})" } finally { entriesLoading=false }
    }
    // Use US catalogue availability for all streaming-service shelves.
    val serviceWatchRegion = "US"
    LaunchedEffect(section, serviceCategory, serviceGenre, tmdbKey) {
        if (!section.endsWith(" Catalogue")) return@LaunchedEffect
        serviceLoading = true
        serviceError = ""
        serviceEntries = emptyList()
        serviceHasMore = false
        if (tmdbKey.isBlank()) { serviceError = "Set a TMDB key in Settings"; serviceLoading = false; return@LaunchedEffect }
        val provider = when(section) {
            "Netflix Catalogue" -> 8
            "Apple TV Catalogue" -> 350
            "Prime Video Catalogue" -> 9
            "Disney+ Catalogue" -> 337
            "HBO Max Catalogue" -> 1899
            "Hulu Catalogue" -> 15
            else -> 0
        }
        if (provider == 0) { serviceLoading = false; return@LaunchedEffect }
        val series = serviceCategory == "Series"
        val type = if (series) "tv" else "movie"
        val sort = if (serviceCategory == "New Releases") "primary_release_date.desc" else "popularity.desc"
        val genre = if (series) serviceGenre?.seriesId else serviceGenre?.movieId
        val loadedIds = mutableSetOf<String>()
        var page = 1
        var request = servicePageRequest
        while (true) {
            serviceLoading = true
            serviceError = ""
            val results = try {
                TmdbApi.providerCatalogue(tmdbKey, type, provider, sort, genre, serviceWatchRegion, page)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                serviceError = e.message ?: "Catalogue unavailable"
                null
            } finally {
                serviceLoading = false
            }
            if (results != null) {
                val newEntries = results.entries.filter { loadedIds.add(it.id) }
                serviceEntries = serviceEntries + newEntries
                serviceHasMore = page < results.totalPages
                if (!serviceHasMore) break
                page++
                if (newEntries.isEmpty()) continue
            }
            request = snapshotFlow { servicePageRequest }.first { it != request }
        }
    }
    LaunchedEffect(api, selected) {
        val channel = selected?.takeIf { it.kind == "live" } ?: return@LaunchedEffect
        val provider = api ?: return@LaunchedEffect
        while (true) {
            try {
                epg = provider.epg(channel.id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (epg == null) epg = emptyList()
            }
            delay(60_000)
        }
    }
    CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollConfiguration provides null) {
    MaterialTheme(typography = skyLikeTypography(), shapes = Shapes(
        extraSmall = SharkFrameShape, small = SharkFrameShape, medium = SharkFrameShape,
        large = SharkFrameShape, extraLarge = SharkFrameShape
    ), colorScheme = darkColorScheme(primary = HudAccent, background = HudBackground, surface = HudSurface,
        surfaceVariant = Color(0xFF17294B), outline = Color(0xFF6989BB), onSurface = Color.White, onSurfaceVariant = Color.White)) {
        if (!introFinished) { SharkIntro { introFinished=true } } else if((section=="Home" || section.endsWith(" Catalogue")) && selected?.kind=="tmdb" && homePlaying!=null) {
            val stream=homePlaying!!
            val linkKey = homeLinkKey(checkNotNull(selected))
            BackHandler { homePlaying=null }
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment=Alignment.Center) {
                if (stream.url.startsWith("magnet:?", ignoreCase=true) || torrentPreparing) {
                    // A magnet is not a Media3 data source. Wait until the native engine
                    // returns a verified local file URI before constructing SafePlayer.
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(homeLookupStatus.ifBlank { "Preparing torrent playback…" }, color=Color.White)
                    }
                } else {
                    key(stream.url) { SafePlayer(stream.url,onExit={homePlaying=null},title=selected?.title.orEmpty(),onFailure={fallbackHome(stream,it)},resumeMs=if(autoSession) autoResumeMs else 0L,startupTimeoutMs=if(autoSession) 12000L else 0L,sharkMega=sharkMega,onFirstFrame={
                        if (reuseLastLink) lastWorkingLinks.put(linkKey, stream.url)
                    }) }
                }
            }
        } else if(selected?.kind in listOf("live","movie","episode") && api!=null) {
            val entry=selected!!
            BackHandler { selected=episodeParent; episodeParent=null }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                SafePlayer(api!!.url(entry, playbackPrefs(context).getString("streamFormat", "Auto")),title=entry.title, liveStream=entry.kind=="live", programmes=epg, inferFormat=true, onExit={selected=episodeParent;episodeParent=null}, onChannel={ direction -> val i=live.indexOf(entry); if(entry.kind=="live" && i>=0) live.getOrNull(i+direction)?.let { selected=it } })
            }
        } else Box(Modifier.fillMaxSize()) {
            BackHandler { if(selected!=null) {homeLookupJob?.cancel();homePlaying=null;selected=null} else if(section.endsWith(" Catalogue")) section="Shark Streaming" else if(section=="Shark Streaming") section="Home" else if(section=="EPG / TV Guide") section="Shark TV" else if(section in listOf("Sharks Toolkit","Connection Status","API Configuration","Playback & Display","Xtream Codes","System Health","Shark Performance Hub")) section="Settings" else if(section!="Home") section="Home" else exitPrompt=true }
            val defaultArtwork = trendingMovies.firstOrNull { it.backdrop.isNotBlank() }?.backdrop.orEmpty()
            if (section in listOf("Home","Movies","TV Series") && selected == null) {
                HomeHeroBackground(if(section=="Home") remember(section) { heroArtworks.getOrNull(0).orEmpty().ifBlank { defaultArtwork } } else "")
            } else HomeHeroBackground("", subdued=true)
            selected?.takeIf { it.kind=="tmdb" && it.backdrop.isNotBlank() }?.let { entry -> AsyncImage(model=remember(entry.backdrop,context) { coil.request.ImageRequest.Builder(context).data(entry.backdrop).size(coil.size.Size.ORIGINAL).build() },contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop); Box(Modifier.fillMaxSize().background(HudBackground.copy(alpha = .78f))) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0x10091A30),Color(0x55061538)))))
            Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Column(Modifier.fillMaxSize().padding(horizontal=22.dp).padding(bottom=if(section=="Shark TV") 12.dp else if(section=="Home") 4.dp else 0.dp)) {
                GlassHeader(132.dp)
                if (section == "API Configuration") Spacer(Modifier.height(18.dp))
                val featuredPage = selected == null && section in listOf("Home","Movies","TV Series")
                val serviceCatalogue = selected == null && (section == "Shark Streaming" || section.endsWith(" Catalogue"))
                Column(Modifier.weight(1f).fillMaxWidth().then(if(featuredPage || serviceCatalogue || (selected==null && section in listOf("Shark TV","EPG / TV Guide","Sports Guide","Playback & Display","Settings","Shark Performance Hub"))) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                if(selected!=null) {
                    val e=selected!!
                    Spacer(Modifier.height(16.dp))
                    FocusAction("← BACK",onClick={homeLookupJob?.cancel();homePlaying=null;selected=null})
                    if(e.kind!="tmdb") Text(e.title,color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))
                    when(e.kind) {
                        "tmdb" -> {
                            if ((section == "Home" || section.endsWith(" Catalogue")) && homePlaying != null && !torrentPreparing && homePlaying?.url?.startsWith("magnet:?") != true) {
                                val stream = homePlaying!!
                                key(stream.url) { SafePlayer(stream.url,  onExit={homePlaying=null}, title=e.title,onFailure={fallbackHome(stream,it)},resumeMs=if(autoSession) autoResumeMs else 0L,startupTimeoutMs=if(autoSession) 12000L else 0L,sharkMega=sharkMega,onFirstFrame={
                                    if (reuseLastLink) lastWorkingLinks.put(homeLinkKey(e), stream.url)
                                }) }
                            } else {
                                Text(e.title,color=Color.White,fontSize=28.sp)
                                Text(e.overview,color=Color.White,maxLines=5)
                                Text(homeLookupStatus.ifBlank { "Home playback source" },color = Color.White)
                                if (section == "Home" || section.endsWith(" Catalogue")) {
                                    if (e.genre == "TV Series") {
                                        Text("Season $homeSeason • Episode $homeEpisode", color = Color.White)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            FocusAction("− Season") { if(homeSeason>1) {homeLookupJob?.cancel();homeSeason--; homeStreams=emptyList();homeLookupStatus="Press Search episode"} }
                                            FocusAction("+ Season") { homeLookupJob?.cancel();homeSeason++; homeEpisode=1;homeStreams=emptyList();homeLookupStatus="Press Search episode" }
                                            FocusAction("− Episode") { if(homeEpisode>1) {homeLookupJob?.cancel();homeEpisode--;homeStreams=emptyList();homeLookupStatus="Press Search episode"} }
                                            FocusAction("+ Episode") { homeLookupJob?.cancel();homeEpisode++;homeStreams=emptyList();homeLookupStatus="Press Search episode" }
                                        }
                                    }
                                    FocusAction(if (e.genre == "TV Series") "Play episode" else "Play movie") { searchHomeStreams(e) }
                                    Text("Home TMDB posters search your Home provider only. Movies and TV Series tabs use IPTV.",color = Color.White)
                                    if(homeStreams.isNotEmpty()) { FocusAction(if(showHomeSources) "Hide sources" else "Choose source manually") {showHomeSources=!showHomeSources}; if(showHomeSources) homeStreams.forEach { stream -> FocusAction(stream.title){autoSession=false;homePlaying=stream} } }
                                    
                                }
                            }
                        }
                        "series" -> { var episodes by remember(e.id){mutableStateOf<List<Entry>>(emptyList())}; var status by remember(e.id){mutableStateOf("Loading episodes…")}; LaunchedEffect(e.id){try { episodes=api?.episodes(e.id).orEmpty(); status=if(episodes.isEmpty()) "No episodes returned" else "" } catch(ex:Exception){if(ex is kotlinx.coroutines.CancellationException) throw ex; status="Episode request failed (${ex.javaClass.simpleName})"}}; Text(status,color=Color.White); episodes.forEach { episode -> FocusAction(episode.title){episodeParent=e;selected=episode} } }
                        else -> {val a=api;if(a!=null)key(e.id){SafePlayer(a.url(e, playbackPrefs(context).getString("streamFormat", "Auto")), inferFormat=true)} else ConnectPrompt{showLogin=true};if(e.kind=="live") { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { FocusAction("Previous channel"){val i=live.indexOf(e);if(i>0)selected=live[i-1]}; FocusAction("TV Guide"){selected=null;section="EPG / TV Guide"}; FocusAction("Next channel"){val i=live.indexOf(e);if(i>=0 && i+1<live.size)selected=live[i+1]} }; epg.orEmpty().forEach {Text("${it.start} — ${it.title}",color=Color.White)} }}
                    }
                } else {
                    if(connected && catalogueKind.isNotBlank() && section!="Shark TV") { Row { FocusAction(categories.firstOrNull { it.first==categoryId }?.second ?: "Categories") { categoryMenu=true }; FocusAction("Retry") { api?.clearCatalogueCache(); catalogueRetry++ } } }
                    when(section) {
                        "Home", "Movies", "TV Series" -> {
                            if(section=="Home") {
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    // Fit both landscape TMDB rows inside the TV viewport.
                                    val horizontalWidth = ((maxWidth.value - 64f) / 4f).toInt()
                                    val verticalWidth = ((maxHeight.value - 90f) / 1.125f).toInt()
                                    val cardWidth = minOf(horizontalWidth, verticalWidth).coerceIn(1,340)
                                    Column(Modifier.fillMaxSize().padding(top=2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                        HomeTmdbRow(tmdbKey,"Movies",trendingMovies.ifEmpty { popularMovies },cardWidth){openTitle(it)}
                                        HomeTmdbRow(tmdbKey,"TV Series",trendingSeries.ifEmpty { popularSeries },cardWidth){openTitle(it)}
                                    }
                                }
                            } else {
                                if(sectionEntries.isNotEmpty()) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    val width=minOf(((maxHeight.value/2f-24f)/1.5f+14f).toInt(),((maxWidth.value-80f)/8f).toInt()).coerceIn(60,124)
                                    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                        V4PosterRow(tmdbKey,"",sectionEntries.filterIndexed { i,_ -> i%2==0 },cardWidth=width){openTitle(it)}
                                        if(sectionEntries.size>1) V4PosterRow(tmdbKey,"",sectionEntries.filterIndexed { i,_ -> i%2==1 },cardWidth=width){openTitle(it)}
                                    }
                                }
                            }
                            if(section=="Home" && trendingMovies.isEmpty() && trendingSeries.isEmpty() && popularMovies.isEmpty() && popularSeries.isEmpty() && tmdbStatus.isNotBlank()) Text(tmdbStatus,color=Color.White,fontSize=12.sp,maxLines=2)
                            if((if(section=="Home") trendingMovies.isEmpty() && trendingSeries.isEmpty() && popularMovies.isEmpty() && popularSeries.isEmpty() else sectionEntries.isEmpty())) { if(connected && section!="Home") Text(if(catalogueLoading) "Loading catalogue…" else if(error.isNotBlank()) "$error — press Retry" else "No titles returned",color=Color.White) else if(section=="Home") FocusAction("Retry posters"){posterRefresh++} else ConnectPrompt{showLogin=true} }
                        }
                        "Shark Streaming" -> {
                            Spacer(Modifier.height(8.dp))
                            val services = listOf(
                                "Netflix" to R.drawable.themed_marquee_netflix,
                                "Apple TV" to R.drawable.themed_marquee_apple,
                                "Prime Video" to R.drawable.themed_marquee_prime,
                                "Disney+" to R.drawable.themed_marquee_disney,
                                "HBO Max" to R.drawable.themed_marquee_max,
                                "Hulu" to R.drawable.themed_marquee_hulu
                            )
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val tileWidth = minOf((maxWidth - 62.dp) / 6, maxHeight - 20.dp).coerceIn(1.dp, 280.dp)
                                CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides KeepVisibleFocus) {
                                    LazyRow(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                    ) {
                                        items(services, key = { it.first }) { (provider, artwork) ->
                                            StreamingBrandCard(provider, artwork, Modifier.size(tileWidth)) {
                                                serviceCategory = "Movies"
                                                serviceGenre = null
                                                section = "$provider Catalogue"
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FocusAction("Back to Home") { section="Home" }
                        }
                        "Netflix Catalogue", "Apple TV Catalogue", "Prime Video Catalogue", "Disney+ Catalogue", "HBO Max Catalogue", "Hulu Catalogue" -> {
                            Text(section.removeSuffix(" Catalogue"),color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
                            val catalogueRegion = if (serviceWatchRegion == "US") "United States" else "United Kingdom"
                            Text("TMDB catalogue · $catalogueRegion", color = Color.White, fontSize = 12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("Movies","Series","New Releases").forEach { category ->
                                    FocusAction(if(serviceCategory==category) "● $category" else category) { serviceCategory=category }
                                }
                                FocusAction(serviceGenre?.let { "Category: ${it.label}" } ?: "Category") { serviceGenreMenu = true }
                                FocusAction("Back to Services") { section="Shark Streaming" }
                            }
                            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
                                when {
                                    serviceLoading -> Text("Loading provider catalogue…", color = Color.White, fontSize = 12.sp, maxLines = 1)
                                    serviceError.isNotBlank() -> Text(serviceError, color = Color(0xFFFFC66D), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val posterWidth = ((maxHeight - 12.dp) / 1.5f).value.toInt().coerceIn(1, 200)
                                if (serviceEntries.isNotEmpty()) {
                                    key(section, serviceCategory, serviceGenre) {
                                        HomeTmdbRow(
                                            tmdbKey, "", serviceEntries, posterWidth,
                                            landscape = false,
                                            onNearEnd = {
                                                if (serviceHasMore && !serviceLoading && serviceError.isBlank()) servicePageRequest++
                                            },
                                            endAction = if (serviceError.isNotBlank()) {
                                                { servicePageRequest++ }
                                            } else null,
                                            onSelect = { openTitle(it) }
                                        )
                                    }
                                } else if (!serviceLoading) {
                                    if (serviceError.isBlank()) {
                                        Text("No titles found for this category.", color = Color.White, fontSize = 15.sp)
                                    } else if (tmdbKey.isNotBlank()) {
                                        FocusAction("Retry catalogue") { servicePageRequest++ }
                                    }
                                }
                            }
                            if (serviceGenreMenu) PremiumDialog(
                                onDismissRequest = { serviceGenreMenu = false },
                                title = { Text("Category", color = Color.White, fontSize = 23.sp) },
                                text = {
                                    val genres = listOf<ServiceGenre?>(null) + ServiceGenre.entries
                                    Column(
                                        Modifier.heightIn(max = 184.dp).clipToBounds().verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        genres.chunked(2).forEach { row ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                row.forEach { genre ->
                                                    PremiumButton(
                                                        onClick = { serviceGenre = genre; serviceGenreMenu = false },
                                                        modifier = Modifier.weight(1f).height(34.dp),
                                                        compact = true
                                                    ) {
                                                        val name = genre?.label ?: "Any genre"
                                                        Text(if (genre == serviceGenre) "✓ $name" else name, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = { PremiumButton(onClick = { serviceGenreMenu = false }) { Text("Cancel") } }
                            )
                        }
                        "Sports Guide" -> { PublicGuide(fixtures,fixtureStatus, onRefresh={sportsRefresh++}) }
                        "Shark TV" -> {if(connected) LiveBrowser(categories,categoryId,live,api,lastLiveId,catalogueLoading,livePreviewEnabled,{categoryId=it},{api?.clearCatalogueCache(); catalogueRetry++}){lastLiveId=it.id;section="Shark TV";selected=it} else ConnectPrompt{showLogin=true}}
                        "EPG / TV Guide" -> { if(connected) BroadcastGuide(live,api){section="Shark TV";selected=it} else ConnectPrompt{showLogin=true} }
                        "Search" -> { var query by remember { mutableStateOf("") }; OutlinedTextField(query,{query=it},label={Text("Search titles")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White)); if(query.isNotBlank()) V4PosterRow(tmdbKey,"Results",(trendingMovies+trendingSeries+movies+series).filter{it.title.contains(query,true)}){selected=it} }
                        "My List" -> {Text("My List",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(vertical=18.dp));Text("Saved favourites will appear here when the feature is connected.",color = Color.White)}
                        "Xtream Codes" -> {Text("Xtream Codes",color=Color.White,fontSize=25.sp); FocusAction("Connect / Change IPTV Account"){if(password.isBlank()) password=readSavedPassword(context);showLogin=true}; FocusAction("Disconnect (keep login)"){api=null;connected=false}; Text(if(connected) "Connected" else "Not connected",color=Color.White); Text(credentialStatus,color=Color.White); FocusAction("Back to Settings"){section="Settings"}}
                        "Settings" -> {
                            Spacer(Modifier.height(4.dp))
                            val options=listOf("Xtream Codes","Software Update","Refresh IPTV Catalogue","Refresh Posters","Refresh Sports","Connection Status","Clear Memory Cache","Playback & Display","Sharks Toolkit","Exit","System Health","Shark Performance Hub")
                            val interfacePrefs = playbackPrefs(context)
                            val uniformBarHeight = interfacePrefs.getInt("uniformBarHeight",76).coerceIn(64,88).dp
                            var focusedSetting by remember { mutableStateOf<String?>(null) }
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                options.chunked(3).forEachIndexed { rowIndex, labels ->
                                    Row(
                                        Modifier.fillMaxWidth().height(uniformBarHeight)
                                            .zIndex(if (labels.any { it == focusedSetting }) 1f else 0f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Keep all three grid columns equally sized, even on the final partial row.
                                        repeat(3 - labels.size) { Spacer(Modifier.weight(1f)) }
                                        labels.forEachIndexed { columnIndex, label ->
                                            val focused = focusedSetting == label
                                            val focusScale by animateFloatAsState(
                                                if (focused) 1.10f else 1f,
                                                tween(150),
                                                label = "Settings tile focus scale"
                                            )
                                            val horizontalPivot = columnIndex / labels.lastIndex.coerceAtLeast(1).toFloat()
                                            val verticalPivot = if (rowIndex == 0) 0f else 0.5f
                                            ElectricFocus(
                                                Modifier.weight(1f).fillMaxHeight()
                                                    .onFocusChanged {
                                                        if (it.isFocused) focusedSetting = label
                                                        else if (focusedSetting == label) focusedSetting = null
                                                    }
                                                    .zIndex(if (focused) 1f else 0f)
                                                    .graphicsLayer {
                                                        transformOrigin = TransformOrigin(horizontalPivot, verticalPivot)
                                                        scaleX = focusScale
                                                        scaleY = focusScale
                                                    }
                                                    .clickable {
                                                        when(label) {
                                                            "Software Update" -> SharkAppUpdater.check(context as android.app.Activity, manual = true)
                                                            "Exit" -> exitPrompt=true
                                                            "Change Playlist" -> showLogin=true
                                                            "Refresh IPTV Catalogue" -> if(!loading) { loading=true; scope.launch {
                                                                cacheTitle="Refresh IPTV Catalogue"
                                                                try {
                                                                    val a=api ?: XtreamApi(server,username,password)
                                                                    if(api==null && !a.authenticate()) error("Login rejected")
                                                                    a.refreshProviderCategories()
                                                                    api=a; connected=true; catalogueRetry++
                                                                    cacheMessage="IPTV catalogue only — app software unchanged. Provider categories refreshed. Channels, movies, series and guide reload when opened."
                                                                } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; cacheMessage="Provider refresh failed (${if(e is java.io.IOException) "network error" else e.javaClass.simpleName}); saved login unchanged." } finally { loading=false }
                                                            } }
                                                            "Refresh Posters" -> { refreshNotice="Refreshing movie and TV posters…"; tmdbStatus="Refreshing posters…"; posterRefresh++ }
                                                            "Refresh Sports" -> { refreshNotice="Refreshing sports fixtures…"; fixtureStatus="Refreshing sports…"; sportsRefresh++ }
                                                            "Clear Memory Cache" -> cleanAppCache()
                                                            "Connection Status" -> { diagnostics=true; testRun++ }
                                                            else -> section=label
                                                        }
                                                    },
                                                focused = focused,
                                                repeatPulse = true
                                            ) {
                                                Row(
                                                    Modifier.fillMaxSize()
                                                        .hudSurface()
                                                        .padding(horizontal=10.dp),
                                                    verticalAlignment=Alignment.CenterVertically,
                                                    horizontalArrangement=Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(Modifier.width(44.dp).fillMaxHeight(),contentAlignment=Alignment.Center) { ChromeSettingsBadge(label) }
                                                    Text(label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Light)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "Playback & Display" -> { PlaybackSettings { section="Settings" } }
                        "Shark Performance Hub" -> {
                            var readingRefresh by remember { mutableStateOf(0) }
                            val refreshSnapshot = readingRefresh
                            val activity = context as? android.app.Activity
                            val displayMode = activity?.windowManager?.defaultDisplay?.mode
                            val displayWidth = displayMode?.physicalWidth ?: 0
                            val displayHeight = displayMode?.physicalHeight ?: 0
                            val refreshRate = displayMode?.refreshRate ?: 0f
                            val runtime = Runtime.getRuntime()
                            val usedHeap = runtime.totalMemory() - runtime.freeMemory()
                            val maxHeap = runtime.maxMemory()
                            val freeStorage = android.os.StatFs(context.cacheDir.absolutePath).availableBytes
                            Text("Shark Performance Hub", color=Color.White, fontSize=20.sp)
                            Text("4K Display Check", color=Color(0xFFFFD53D), fontSize=14.sp)
                            Text(if(displayWidth>0 && displayHeight>0) "Active display mode: ${displayWidth} × ${displayHeight} at ${"%.1f".format(java.util.Locale.US,refreshRate)} Hz" else "Display mode unavailable", color=Color.White, fontSize=12.sp)
                            Text("4K UHD reference: 3840 × 2160. Display mode does not guarantee artwork or video source resolution.", color=Color.LightGray, fontSize=12.sp)
                            Text("Memory Monitor", color=Color(0xFFFFD53D), fontSize=14.sp)
                            Text("App heap: ${formatBytes(usedHeap)} / ${formatBytes(maxHeap)}", color=Color.White, fontSize=12.sp)
                            Text("Free app storage: ${formatBytes(freeStorage)}", color=Color.White, fontSize=12.sp)
                            Text("Stream Health", color=Color(0xFFFFD53D), fontSize=14.sp)
                            Text("Media3 playback · Profile: ${if(sharkMega) "Shark Mega" else "Standard"} · Stream format: ${playbackPrefs(context).getString("streamFormat","Auto")}", color=Color.White, fontSize=12.sp)
                            Text("Live dropped-frame and buffering counters are not available on this screen yet.", color=Color.LightGray, fontSize=12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                FocusAction("Shark Speed Test") { diagnostics=true; testRun++; section="Settings" }
                                FocusAction("Playback Settings") { section="Playback & Display" }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                FocusAction("Shark Optimiser · Clear Cache") { cleanAppCache() }
                                FocusAction("Refresh Readings") { readingRefresh++ }
                            }
                            FocusAction("Back to Settings") { section="Settings" }
                        }
                        "System Health" -> {
                            val runtime=Runtime.getRuntime()
                            val used=runtime.totalMemory()-runtime.freeMemory()
                            val max=runtime.maxMemory()
                            val stat=android.os.StatFs(context.cacheDir.absolutePath)
                            val free=stat.availableBytes
                            Text("System Health · SHARK ${BuildConfig.VERSION_NAME}",color=Color.White,fontSize=22.sp)
                            Text("Installed build: ${BuildConfig.VERSION_CODE} · Check Software Update for the latest published release",color=Color(0xFFFFD53D),fontSize=13.sp)
                            Text("App heap used: ${formatBytes(used)} / ${formatBytes(max)}",color=Color.White)
                            Text("Available app storage: ${formatBytes(free)}",color=Color.White)
                            Text("Built-in player: Media3",color=Color.White)
                            Text("Stream format: ${playbackPrefs(context).getString("streamFormat","Auto")}",color=Color.White)
                            Text("These are live device readings, not a guarantee against buffering.",color=Color.LightGray)
                            FocusAction("Refresh readings"){section="Settings";section="System Health"}
                            FocusAction("Back to Settings"){section="Settings"}
                        }
                        "Sharks Toolkit" -> {
                            Text("Sharks Toolkit", color = Color.White, fontSize = 26.sp)
                            FocusAction("Playback profile · ${if (sharkMega) "Shark Mega" else "Standard"}") {
                                sharkMega = !sharkMega
                                playbackPrefs(context).edit().putBoolean("sharkMega", sharkMega).apply()
                            }
                            Text(
                                "Shark Mega starts playback with a short buffer and builds a larger buffer as you watch.",
                                color = Color.LightGray
                            )
                            Text("Stream selection", color = Color.White, fontSize = 18.sp)
                            FocusAction("Reuse Last Link · ${if (reuseLastLink) "On" else "Off"}") {
                                reuseLastLink = !reuseLastLink
                                playbackPrefs(context).edit().putBoolean("reuseLastLink", reuseLastLink).apply()
                                if (!reuseLastLink) lastWorkingLinks.evictAll()
                            }
                            Text(
                                "Auto-play the last working link for this movie or episode when the add-on still offers it. Links are remembered while the app is open.",
                                color = Color.LightGray
                            )
                            FocusAction("Back to Settings") { section = "Settings" }
                        }
                        "Connection Status" -> {Text(if(connected) "IPTV connected" else "IPTV not connected",color=Color.White);Text(fixtureStatus,color=Color.White);FocusAction("Back to Settings"){section="Settings"}}
                        "API Configuration" -> {var penguDraft by remember { mutableStateOf(prefs.getString("pengu_manifest", BuildConfig.PENGUPLAY_MANIFEST_URL).orEmpty()) }; var aioDraft by remember { mutableStateOf(prefs.getString("aio_manifest", "").orEmpty()) }; OutlinedTextField(penguDraft,{penguDraft=it},label={Text("AIOStreams / PenguPlay manifest (Home playback)")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),singleLine=true,visualTransformation=PasswordVisualTransformation()); OutlinedTextField(aioDraft,{aioDraft=it},label={Text("Additional AIOStreams manifest")},singleLine=true,visualTransformation=PasswordVisualTransformation()); FocusAction("Save Home providers") { prefs.edit().putString("pengu_manifest",penguDraft.trim()).putString("aio_manifest",aioDraft.trim()).apply(); section="Home" }; OutlinedTextField(tmdbDraft,{tmdbDraft=it},label={Text("TMDB API key or read access token")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),visualTransformation=PasswordVisualTransformation(),singleLine=true);OutlinedTextField(sportsKey,{sportsKey=it;prefs.edit().putString("sports_key",it).apply()},label={Text("Sports API key")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),visualTransformation=PasswordVisualTransformation(),singleLine=true);FocusAction("Save TMDB and refresh"){tmdbKey=tmdbDraft.trim();prefs.edit().putString("tmdb_key",tmdbKey).apply();posterRefresh++;section="Home"};FocusAction("Back to Settings"){section="Settings"}}
                    }
                    if(!serviceCatalogue && section!="Settings" && section!="Shark TV" && (loading || catalogueLoading)) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(section in listOf("Home","Movies","TV Series") && tmdbStatus.isNotBlank()) Text(tmdbStatus,color = Color.White,fontSize=12.sp)
                    if(!serviceCatalogue && section!="Settings" && section!="Shark TV" && error.isNotBlank())Text(error,color=Color(0xFFFFB8B8),fontSize=12.sp)
                    Spacer(Modifier.height(if(featuredPage || serviceCatalogue || section in listOf("Shark TV","Settings")) 6.dp else 30.dp))
                }
                }
            }
            }
            val navStripPulse = rememberInfiniteTransition(label = "Bottom navigation strips").animateFloat(
                initialValue = 0.20f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                label = "Colour strip opacity"
            )
            var focusedNav by remember { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Home","Shark TV","Movies","TV Series","Sports Guide","Shark Stream","Settings").forEach { item ->
                    val active=section==(if(item=="Shark Stream") "Shark Streaming" else item) || (item=="Shark Stream" && section.endsWith(" Catalogue")) || (item=="Settings" && section in listOf("Sharks Toolkit","Connection Status","API Configuration","Playback & Display","Xtream Codes","System Health"))
                    val focused=focusedNav==item
                    val emphasized=focused || (focusedNav==null && active)
                    val tileFill = if (active) listOf(Color(0xFF192D51), Color(0xFF101D38))
                        else listOf(Color(0xFF101C39), Color(0xFF0A132A))
                    val frameColor = if (emphasized) SharkFocusGold else HudAccent
                    val accent = when (item) {
                        "Home" -> Color(0xFFFFC34D)
                        "Shark TV" -> Color(0xFFFF8A35)
                        "Movies" -> Color(0xFFFF5260)
                        "TV Series" -> Color(0xFFED49A6)
                        "Sports Guide" -> Color(0xFFB66BFF)
                        "Shark Stream" -> Color(0xFF00D9F4)
                        else -> Color(0xFF5D9EFF)
                    }
                    Column(Modifier.weight(1f).height(64.dp).onFocusChanged { state ->
                        if(state.isFocused) focusedNav=item else if(focusedNav==item) focusedNav=null
                    }.drawWithCache {
                        val strip = Brush.horizontalGradient(listOf(accent, accent.copy(alpha = .7f)))
                        val inset = 3.dp.toPx()
                        val stripHeight = 3.dp.toPx()
                        val stripSize = Size((size.width - 2 * inset).coerceAtLeast(0f), stripHeight)
                        onDrawWithContent {
                            drawContent()
                            if (!emphasized) {
                                drawRect(strip, Offset(inset, -stripHeight / 2f), stripSize, alpha = navStripPulse.value)
                            }
                        }
                    }.background(Brush.verticalGradient(tileFill)).glassFrame(2.dp, frameColor, focused = focused, repeatPulse = true)
                    .clickable { if(item=="Exit") exitPrompt=true else {homeLookupJob?.cancel();homePlaying=null;selected=null;section=if(item=="Shark Stream") "Shark Streaming" else item} },horizontalAlignment=Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal=2.dp),contentAlignment=Alignment.Center) {
                            Text(item,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Normal,maxLines=1,style=TextStyle(textAlign=TextAlign.Center),overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            }
        }
        refreshNotice?.let { notice ->
            PremiumDialog(onDismissRequest={ refreshNotice=null },title={ Text("Refresh status") },text={ Text(notice) },confirmButton={ PremiumButton(onClick={ refreshNotice=null }) { Text("OK") } })
        }
        cacheMessage?.let { message ->
            PremiumDialog(
                onDismissRequest = { cacheMessage = null },
                title = { Text(cacheTitle) },
                text = { Text(message) },
                confirmButton = { PremiumButton(onClick = { cacheMessage = null }) { Text("OK") } },
                dismissButton = if (cacheStorageShortcut) {
                    {
                        PremiumButton(onClick = {
                            val opened = runCatching {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                            }.isSuccess
                            cacheMessage = if (opened) null else "Open Storage in your TV’s Android settings."
                        }) { Text("Android Storage") }
                    }
                } else null
            )
        }
        if (diagnostics) PremiumDialog(
            onDismissRequest = ::closeDiagnostics,
            title = { Text("Connection Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(networkReport)
                    val penguManifestConfigured = prefs.getString("pengu_manifest", null)?.isNotBlank() == true || BuildConfig.PENGUPLAY_MANIFEST_URL.isNotBlank()
                    Text(if (penguManifestConfigured) "Home provider: manifest configured (stream availability not yet tested)" else "Home provider: manifest missing — add manifest in API Configuration")
                    Text("M-Lab NDT7 open-source speed test measures sustained download over one connection (about 10 seconds). Results may differ from multi-connection broadband tests. This does not test IPTV or PenguPlay servers.",fontSize=12.sp)
                    if (speedReport.isNotBlank()) Text(speedReport)
                    FocusAction("Test download speed", enabled = !speedRunning, onClick = ::testDownload)
                }
            },
            confirmButton = { PremiumButton(onClick = ::closeDiagnostics) { Text("Close") } },
            dismissButton = { PremiumButton(onClick = { testRun++ }) { Text("Retest connection") } }
        )
        if(exitPrompt) PremiumDialog(onDismissRequest={exitPrompt=false},title={Text("Exit Shark Nail TV?")},confirmButton={PremiumButton(onClick={exitPrompt=false;(context as? android.app.Activity)?.finish()}){Text("Exit")}},dismissButton={PremiumButton(onClick={exitPrompt=false}){Text("Cancel")}})
        if(categoryMenu) PremiumDialog(onDismissRequest={categoryMenu=false},title={Text("Categories")},text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) { categories.forEach { (id,name) -> Box(Modifier.fillMaxWidth()) { FocusAction(name,modifier=Modifier.fillMaxWidth()) { categoryId=id; categoryMenu=false } } } }},confirmButton={PremiumButton(onClick={categoryMenu=false}){Text("Close")}})
        if(showLogin) {
            val serverFocus=remember { FocusRequester() }
            val userFocus=remember { FocusRequester() }
            val passwordFocus=remember { FocusRequester() }
            val cancelFocus=remember { FocusRequester() }
            val connectFocus=remember { FocusRequester() }
            PremiumDialog(onDismissRequest={if(!loading) showLogin=false},title={Text("Connect your IPTV provider")},text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(server,{server=it;error=""},modifier=Modifier.fillMaxWidth().focusRequester(serverFocus).focusProperties { down=userFocus },label={Text("Server URL")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),singleLine=true)
                    OutlinedTextField(username,{username=it;error=""},modifier=Modifier.fillMaxWidth().focusRequester(userFocus).focusProperties { up=serverFocus;down=passwordFocus },label={Text("Username")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),singleLine=true)
                    OutlinedTextField(password,{password=it;error=""},modifier=Modifier.fillMaxWidth().focusRequester(passwordFocus).focusProperties { up=userFocus;down=connectFocus },label={Text("Password")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    Text("Use an authorized Xtream account.",fontSize=12.sp)
                    if(loading) { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Connecting…") }
                    if(error.isNotBlank()) Text(error,color=Color(0xFFFFB8B8))
                }
            },confirmButton={PremiumButton(modifier=Modifier.focusRequester(connectFocus).focusProperties { left=cancelFocus;up=passwordFocus },onClick={
                if(!loading) {
                    val url=server.trim()
                    val parsed=android.net.Uri.parse(url)
                    when {
                        parsed.scheme !in listOf("http","https") || parsed.host.isNullOrBlank() -> {error="Enter a complete server URL (http:// or https://).";serverFocus.requestFocus()}
                        username.isBlank() -> {error="Enter your username.";userFocus.requestFocus()}
                        password.isBlank() -> {error="Enter your password.";passwordFocus.requestFocus()}
                        else -> {
                            server=url
                            username=username.trim()
            loading=true;error="";scope.launch {try {val a=XtreamApi(server,username,password);if(a.authenticate()){live=emptyList();movies=emptyList();series=emptyList();api=a;connected=true;prefs.edit().putString("server",server).putString("username",username).apply();credentialStatus=try { savePassword(context,password); "Login saved securely" } catch(e:Exception) { "Connected, but secure password storage failed" };showLogin=false}else error="Xtream login rejected"}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException) throw e;error=when(e){is java.io.IOException -> "Network connection failed — retry";is org.json.JSONException -> "Provider returned invalid JSON";else -> "Connection failed (${e.javaClass.simpleName})"}};loading=false}
                        }
                    }
                }
            }){Text(if(loading) "Connecting…" else "Connect")}},dismissButton={PremiumButton(modifier=Modifier.focusRequester(cancelFocus).focusProperties { right=connectFocus;up=passwordFocus },onClick={if(!loading) showLogin=false}){Text("Cancel")}})
        }
    }
}
}

@Composable private fun SidebarItem(label:String,active:Boolean,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val symbol=when(label) {
        "Home"->Icons.Filled.Home
        "Shark TV"->Icons.Filled.LiveTv
        "Movies"->Icons.Filled.Movie
        "TV Series"->Icons.Filled.Tv
        "Sports Guide"->Icons.Filled.SportsSoccer
        "Settings"->Icons.Filled.Settings
        else->Icons.Filled.Star
    }
    Row(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        .hudSurface(active)
        .glassFrame(1.5.dp, if (focused || active) Color.White else HudAccent, focused = focused)
        .clickable(onClick=onClick).padding(horizontal=7.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(symbol,contentDescription=null,tint=Color.White,modifier=Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(when(label){"Sports Guide"->"Sports";"EPG / TV Guide"->"TV Guide";"Xtream Login"->"Xtream";else->label},color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1)
    }
}

@Composable private fun V4Tab(label:String,active:Boolean,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val symbol = when(label) {
        "Home" -> Icons.Filled.Home
        "Movies" -> Icons.Filled.Movie
        "TV Shows" -> Icons.Filled.Tv
        "Sports" -> Icons.Filled.SportsSoccer
        "Shark TV" -> Icons.Filled.LiveTv
        "Settings" -> Icons.Filled.Settings
        else -> Icons.Filled.Cloud
    }
    Row(Modifier.padding(end=5.dp).onFocusChanged { focused=it.isFocused }
        .glassFrame(2.5.dp,if(focused)SharkFocusGold else if(active)Color(0xFF00C4FF) else Color(0xFF24476A), focused = focused)
        .background(if(focused||active)Brush.verticalGradient(listOf(Color(0xFF0089EF),Color(0xFF05316A))) else Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent)),RoundedCornerShape(0.dp))
        .clickable(onClick=onClick).padding(horizontal=11.dp,vertical=9.dp), verticalAlignment=Alignment.CenterVertically) {
        Icon(symbol,contentDescription=null,tint=if(active||focused)Color.White else Color(0xFFC5E4FF),modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label,color=Color.White,fontSize=14.sp,fontWeight=if(active||focused)FontWeight.Bold else FontWeight.Medium,maxLines=1)
    }
}
@Composable private fun GlassHeader(height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        Row(Modifier.fillMaxSize().padding(top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
        val context=LocalContext.current
        
        val motion=rememberInfiniteTransition(label="mascot arm")
        val phase = motion.animateFloat(0f,0.35f,infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="arm")
        val paint=remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
        val dripProgress = motion.animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
            label = "Shark mouth drip"
        )
        @Composable fun Mascot(modifier:Modifier, right:Boolean) {
        val vertices=remember { FloatArray(2*17*13) }
        val bitmap=remember(right) { android.graphics.BitmapFactory.decodeResource(context.resources,if(right) R.drawable.shark_brand_right else R.drawable.shark_brand,android.graphics.BitmapFactory.Options().apply { inSampleSize=2 }) }
        Canvas(modifier) {
            val scale=minOf(size.width/bitmap.width,size.height/bitmap.height)*0.96f
            val w=bitmap.width*scale; val h=bitmap.height*scale
            var k=0
            for(j in 0..12) for(i in 0..16) {
                val x=i/16f; val y=j/12f
                val arm=(1f-kotlin.math.abs(x-(if(right) 0.27f else 0.73f))/0.24f).coerceIn(0f,1f)*(1f-kotlin.math.abs(y-0.29f)/0.28f).coerceIn(0f,1f)
                vertices[k++]=(size.width-w)/2+x*w+arm*phase.value*w*(if(right) -0.015f else 0.015f)
                vertices[k++]=(size.height-h)/2+y*h-arm*phase.value*h*0.10f
            }
            drawContext.canvas.nativeCanvas.drawBitmapMesh(bitmap,16,12,vertices,0,null,0,paint)
            val mouthX = if (right) .416f else .584f
            val mouthY = .371f
            val mouth = Offset((size.width - w) / 2f + mouthX * w, (size.height - h) / 2f + mouthY * h)
            val direction = if (right) -1f else 1f
            for (drip in 0..1) {
                val x = mouth.x + direction * drip * w * .014f
                val y = mouth.y + drip * h * .008f
                val radius = h * if (drip == 0) .013f else .009f
                val life = (dripProgress.value + drip * .48f) % 1f
                val bottom = y + h * (.035f + .036f * life)
                val blood = Color(0xFFC71E36)
                drawLine(blood, Offset(x, y), Offset(x, bottom), radius, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(blood, radius, Offset(x, bottom))
                if (life > .48f) {
                    val fall = (life - .48f) / .52f
                    drawCircle(blood, radius * .72f, Offset(x, bottom + h * (.026f + .085f * fall * fall)), alpha = 1f - fall)
                }
            }

        }
        }
        Mascot(Modifier.weight(1.45f).height(136.dp),false)
        val skyMotion=rememberInfiniteTransition(label="sky diamond")
        val skyPulse = skyMotion.animateFloat(0.35f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="sky glow")
        val skySweep = skyMotion.animateFloat(0f,359.99f,infiniteRepeatable(tween(4200,easing=androidx.compose.animation.core.LinearEasing)),label="sky rainbow")
        Box(Modifier.weight(1.35f).height(134.dp),contentAlignment=Alignment.Center) {
            Image(painterResource(R.drawable.sky_stream_design),"Sky Stream",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val scale=minOf(size.width/480f,size.height/205f)
                val left=(size.width-480f*scale)/2; val top=(size.height-205f*scale)/2
                fun point(x:Float,y:Float)=Offset(left+x*scale,top+y*scale)
                val color=Color.hsv(skySweep.value,0.8f,1f)
                val lines=listOf(point(190f,68f) to point(256f,13f),point(256f,13f) to point(321f,68f),point(190f,135f) to point(256f,187f),point(256f,187f) to point(321f,135f))
                lines.forEach { (a,b) ->
                    drawLine(color.copy(alpha=0.12f*skyPulse.value),a,b,strokeWidth=15f*scale,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(color.copy(alpha=0.65f*skyPulse.value),a,b,strokeWidth=4f*scale,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
        Mascot(Modifier.weight(1.45f).height(136.dp),true)
        }
    }
}
@Composable private fun V4Hero() {
    // Space reserved for the floating header logo and unobstructed shark image.
    Spacer(Modifier.fillMaxWidth().height(80.dp))
}
@Composable private fun ElectricFocus(
    modifier: Modifier = Modifier, focused: Boolean, selected: Boolean = false,
    @Suppress("UNUSED_PARAMETER") shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    @Suppress("UNUSED_PARAMETER") metal: Boolean = false,
    @Suppress("UNUSED_PARAMETER") steady: Boolean = false,
    repeatPulse: Boolean = false,
    content: @Composable () -> Unit
) {
    val fireMotion = rememberInfiniteTransition(label = "Shark TV fire focus")
    val firePhase by fireMotion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "Yellow orange red focus pulse"
    )
    val fireColor = androidx.compose.ui.graphics.lerp(
        Color(0xFFFFE747), Color(0xFFFF4920), firePhase
    )
    val frameColor = if (focused || selected) fireColor else Color(0xFF56BFFF)
    val frameWidth = if (focused || selected) 3.3.dp else 2.dp
    Box(modifier.padding(2.dp)
        .graphicsLayer {
            val focusScale = if (focused) 1.10f else 1f
            scaleX = focusScale
            scaleY = focusScale
        }
        .glassFrame(frameWidth, frameColor, focused = focused, repeatPulse = false)
        .drawWithContent {
            drawContent()
            if (focused || selected) {
                drawRoundRect(
                    color = frameColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
        .padding(2.dp).clip(SharkFrameShape)) { content() }
}

@Composable private fun StreamingBrandCard(
    name: String,
    artwork: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "Marquee focus")
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(artwork),
            contentDescription = "$name — browse catalogue",
            modifier = Modifier.fillMaxSize().padding(5.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (focused) 1f else .79f
            },
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun HomeTmdbRow(
    tmdbKey: String,
    title: String,
    entries: List<Entry>,
    cardWidth: Int = 245,
    landscape: Boolean = true,
    onNearEnd: (() -> Unit)? = null,
    endAction: (() -> Unit)? = null,
    onSelect: (Entry) -> Unit
) {
    Column {
        if(title.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=3.dp)) {
            Box(Modifier.width(5.dp).height(16.dp).background(Color(0xFF00BFFF),RoundedCornerShape(0.dp)))
            Spacer(Modifier.width(9.dp))
            Text(title,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Medium)
        }
        if(entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(42.dp).background(Color.Transparent,RoundedCornerShape(0.dp)),contentAlignment=Alignment.CenterStart){Text("Posters unavailable. Retry after checking the device network and date.",color = Color.White,fontSize=13.sp,modifier=Modifier.padding(16.dp))}
        } else CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides KeepVisibleFocus) {
            val distinctEntries = entries.distinctBy { "${it.kind}:${it.genre}:${it.id}" }
            LazyRow(modifier=Modifier.height((cardWidth*(if(landscape) 9f/16f else 1.5f)+14f).dp),horizontalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(start=12.dp,end=14.dp,top=6.dp,bottom=6.dp)) {
                itemsIndexed(distinctEntries, key = { _, entry -> "${entry.kind}:${entry.genre}:${entry.id}" }) { index, entry ->
                    var focused by remember {mutableStateOf(false)}
                    val posterPulse = rememberInfiniteTransition(label = "Home poster fire")
                    val posterFire by posterPulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "Home poster fire colour")
                    val posterBorder = androidx.compose.ui.graphics.lerp(Color(0xFFFFE747), Color(0xFFFF4920), posterFire)

                    Box(Modifier.width(cardWidth.dp).onFocusChanged {
                        focused = it.isFocused
                        if (focused && index >= distinctEntries.lastIndex - 2) onNearEnd?.invoke()
                    }
                        .zIndex(if (focused) 2f else 0f)
                        
                        .glassFrame(if (focused) 3.3.dp else 2.5.dp, if (focused) posterBorder else Color(0xFF56BFFF), focused = focused, repeatPulse = false).clickable {onSelect(entry)}.padding(3.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if(landscape) 16f/9f else 2f/3f).background(Color(0xFF071424))) {
                            val logo by produceState("",entry.id,entry.genre,tmdbKey) {
                                if(landscape && entry.kind=="tmdb" && tmdbKey.isNotBlank()) value=TmdbApi.titleLogo(tmdbKey,entry)
                            }
                            var logoReady by remember(logo) { mutableStateOf(false) }
                            val posterUrl=if(landscape) entry.backdrop.ifBlank { entry.image } else entry.image.ifBlank { entry.backdrop }
                            AsyncImage(model=posterUrl,contentDescription=entry.title,
                                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            if(landscape) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((cardWidth*0.24f).dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=0.85f)))))
                            if(logo.isNotBlank()) AsyncImage(model=logo,contentDescription=entry.title,modifier=Modifier.align(Alignment.BottomStart).padding(7.dp).fillMaxWidth(0.82f).height((cardWidth*0.19f).dp),contentScale=ContentScale.Fit,alignment=Alignment.BottomStart,onSuccess={logoReady=true},onError={logoReady=false})
                            if(landscape && !logoReady) Text(entry.title,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,style=TextStyle(shadow=Shadow(Color.Black,Offset(1f,2f),5f)),modifier=Modifier.align(Alignment.BottomStart).padding(8.dp))
                            if(entry.rating.isNotBlank()) Text("★ ${entry.rating}/10",color=gold,fontSize=10.sp,fontWeight=FontWeight.Bold,style=TextStyle(shadow=Shadow(Color.Black,Offset(1f,1f),4f)),modifier=Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
                if (endAction != null) item { FocusAction("Retry loading more", onClick = endAction) }
            }
        }
    }
}

@Composable private fun V4PosterRow(tmdbKey:String,title:String,entries:List<Entry>,cardWidth:Int=245,landscape:Boolean=false,onSelect:(Entry)->Unit) {
    Column {
        if(title.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=3.dp)) {
            Box(Modifier.width(5.dp).height(23.dp).background(Color(0xFF00BFFF),RoundedCornerShape(0.dp)))
            Spacer(Modifier.width(9.dp))
            Text(title,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Medium)
        }
        if(entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(110.dp).background(Color.Transparent,RoundedCornerShape(0.dp)),contentAlignment=Alignment.CenterStart){Text("Posters unavailable. Retry after checking the device network and date.",color = Color.White,fontSize=13.sp,modifier=Modifier.padding(16.dp))}
        } else LazyRow(horizontalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(start=3.dp,end=8.dp,top=2.dp,bottom=3.dp)) {
            items(entries.distinctBy { "${it.kind}:${it.genre}:${it.id}" },key={"${it.kind}:${it.genre}:${it.id}"}) { entry ->
                var focused by remember {mutableStateOf(false)}
                // Fixed scale prevents poster rows jumping when D-pad focus changes.
                val matched by produceState("",entry.id,entry.kind,tmdbKey,entry.title) { if(entry.kind in listOf("movie","vod","series")) value=TmdbApi.catalogueArt(tmdbKey,entry) }
                val logo by produceState("",entry.id,entry.genre,tmdbKey) { if(landscape && entry.kind=="tmdb" && tmdbKey.isNotBlank()) value=TmdbApi.titleLogo(tmdbKey,entry) }
                ElectricFocus(Modifier.width(cardWidth.dp).onFocusChanged{ focused = it.isFocused }
                    .clickable {onSelect(entry)},focused=focused,selected=false,shape=RoundedCornerShape(0.dp)) {
                    Box(Modifier.fillMaxWidth().height((if(landscape) cardWidth*9f/16f else (cardWidth-14)*1.5f).dp).background(Color(0xFF071424))) {
                        var artworkReady by remember(entry.id,matched) { mutableStateOf(false) }
                        val posterUrl=if(entry.kind=="tmdb") (if(landscape) entry.backdrop.ifBlank { entry.image } else entry.image) else matched.ifBlank { entry.image }
                        AsyncImage(model=posterUrl,contentDescription=entry.title,
                            modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onSuccess={ artworkReady=true },onError={ artworkReady=false })
                        if(entry.rating.isNotBlank()) Text("★ ${entry.rating}/10",color=gold,fontSize=10.sp,fontWeight=FontWeight.Bold,style=TextStyle(shadow=Shadow(Color.Black,Offset(1f,1f),4f)),modifier=Modifier.align(Alignment.TopEnd).padding(6.dp))
                        var logoReady by remember(logo) { mutableStateOf(false) }
                        if(logo.isNotBlank()) AsyncImage(model=logo,contentDescription=entry.title,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth(0.78f).height(43.dp),contentScale=ContentScale.Fit,alignment=Alignment.BottomStart,onSuccess={logoReady=true},onError={logoReady=false})
                        if(!logoReady && (landscape || !artworkReady) && entry.kind=="tmdb") Text(entry.title,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,style=TextStyle(shadow=Shadow(Color.Black,Offset(1f,2f),5f)),modifier=Modifier.align(Alignment.BottomStart).padding(7.dp))
                    }
                }
            }
        }
    }
}
@Composable private fun V4SportsStrip(events:List<SportEvent>,connected:Boolean,onGuide:()->Unit,onConnect:()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("▏ Sports Guide",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            V4Tab("See All  ›",false,onGuide)
        }
        Spacer(Modifier.height(7.dp))
        if(events.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(75.dp).background(Color(0xFF0B1D36),RoundedCornerShape(0.dp)).clickable {if(connected)onGuide() else onConnect()},contentAlignment=Alignment.Center) {
                Text(if(connected)"No upcoming sports events found in your provider's EPG — open the guide" else "Connect IPTV to see live and upcoming matches, times and channels",color = Color.White,fontSize=13.sp)
            }
        } else LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(events.take(12)) { event ->
                var focused by remember {mutableStateOf(false)}
                Column(Modifier.width(260.dp).onFocusChanged{focused=it.isFocused}.glassFrame(2.dp,if(focused)SharkFocusGold else Color(0xFF1A588B),RoundedCornerShape(0.dp), focused = focused).background(Color(0xFF0B203B),RoundedCornerShape(0.dp)).clickable(onClick=onGuide).padding(10.dp)) {
                    Text(if(event.startMillis<=System.currentTimeMillis())"● LIVE" else displayTime(event.startMillis),color = Color.White,fontSize=11.sp,fontWeight=FontWeight.Medium)
                    Text(event.program.title,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(event.channel.title,color = Color.White,fontSize=11.sp,maxLines=1)
                }
            }
        }
    }
}
@Composable private fun ConnectPrompt(onClick:()->Unit) { Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Connect your IPTV provider to load channels and your library",color = Color.White);Spacer(Modifier.height(12.dp));PremiumButton(onClick=onClick){Text("Connect IPTV")}} }

@Composable private fun PremiumRow(title:String,entries:List<Entry>,onSelect:(Entry)->Unit) {
    Column(Modifier.padding(bottom=17.dp)) {
        Text(title,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(bottom=9.dp))
        if(entries.isEmpty()) { Box(Modifier.fillMaxWidth().height(68.dp).background(Color(0x330B1B34),RoundedCornerShape(0.dp)).padding(18.dp)){Text("No titles available yet",color = Color.White,fontSize=13.sp)} }
        else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) { items(entries,key={"${it.kind}-${it.id}"}) { e ->
            var cardFocused by remember { mutableStateOf(false) }
            Column(Modifier.width(148.dp)
                .onFocusChanged { cardFocused=it.isFocused }
                .graphicsLayer { scaleX=1f; scaleY=1f }
                .glassFrame(2.dp,if(cardFocused)gold else Color(0xFF195CA5),RoundedCornerShape(0.dp), focused = cardFocused)
                .clip(RoundedCornerShape(0.dp)).background(Color(0xFF0A1D39))
                .clickable {onSelect(e)}) {
                if(e.image.isNotBlank()) AsyncImage(model=e.image,contentDescription=e.title,modifier=Modifier.fillMaxWidth().height(182.dp),contentScale=ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().height(182.dp).background(Brush.verticalGradient(listOf(Color(0xFF123E79),Color(0xFF071429)))),contentAlignment=Alignment.Center){Text("SHARK NAIL TV",color = Color.White,fontWeight=FontWeight.Medium,fontSize=12.sp)}
                Text(e.title,color=Color.White,fontSize=12.sp,maxLines=2,minLines=2,modifier=Modifier.padding(8.dp))
            }
        } }
    }
}

private object TmdbApi {
    private val backdropCache=android.util.LruCache<String,String>(512)
    private val artCache=android.util.LruCache<String,String>(256)
    private val artGate=kotlinx.coroutines.sync.Semaphore(3)
    suspend fun titleLogo(key:String,entry:Entry):String = withContext(Dispatchers.IO) {
        val type=if(entry.genre=="TV Series") "tv" else "movie"
        val cacheKey="$type:${entry.id}:${key.hashCode()}"
        artCache[cacheKey]?.let { return@withContext it }
        artGate.acquire()
        try {
            artCache[cacheKey]?.let { return@withContext it }
            val token=key.trim().removePrefix("Bearer ").trim()
            val bearer=token.contains('.') || token.length>64
            val url="https://api.themoviedb.org/3/$type/${entry.id}/images?include_image_language=en,null"+if(bearer) "" else "&api_key=${URLEncoder.encode(token,"UTF-8")}"
            val request=Request.Builder().url(url)
            if(bearer) request.header("Authorization","Bearer $token")
            client.newCall(request.build()).execute().use { response ->
                if(!response.isSuccessful) return@withContext ""
                val obj=JSONObject(response.body?.string().orEmpty())
                fun find(name:String,englishOnly:Boolean):String {
                    val arr=obj.optJSONArray(name) ?: return ""
                    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optString("iso_639_1")=="en" || !englishOnly }.sortedWith(compareByDescending<JSONObject>{it.optString("iso_639_1")=="en"}.thenByDescending{it.optDouble("vote_average",0.0)}).map { it.optString("file_path") }.firstOrNull { it.startsWith("/") }.orEmpty()
                }
                val logo=find("logos",true)
                (if(logo.isNotBlank()) "https://image.tmdb.org/t/p/original$logo" else "").also { artCache.put(cacheKey,it) }
            }
        } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; "" } finally { artGate.release() }
    }


    private val client=OkHttpClient.Builder().callTimeout(18,java.util.concurrent.TimeUnit.SECONDS).build()
    private fun cleanTitle(entry:Entry):String = entry.title.trim()
        .replace(Regex("""(?i)^[\s|:–_-]*(?:(?:UK|US|EN|ENG|MULTI|NF|AMZN|IN|AR|FR|DE|ES|IT|TR|PK|CA|AU|4K|FHD|HD|UHD|TOP|VOD|MOVIES|SERIES)[\s:|–_-]+)+"""), "")
        .replace(Regex("""(?i)\s*[\[(]?(?:2160p|1080p|720p|4K|UHD|FHD|HD|HDR|HEVC|H265|MULTI|DUAL)[\])]?\s*$"""), "")
        .replace(Regex("""\s*[\[(]?(?:19|20)\d{2}[\])]?\s*$"""), "").trim(' ', '|', '-', ':')
    private fun normalizedTitle(value:String)=value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun catalogueKey(key:String,entry:Entry)="catalogue:${entry.kind}:${entry.title}:${key.hashCode()}"
    fun catalogueBackdrop(key:String,entry:Entry):String=backdropCache.get(catalogueKey(key,entry)).orEmpty()
    suspend fun catalogueArt(key:String,entry:Entry):String = withContext(Dispatchers.IO) {
        val type=if(entry.kind=="series") "tv" else "movie"
        val title=cleanTitle(entry).ifBlank { entry.title }
        val year=Regex("\\(((?:19|20)\\d{2})\\)\\s*$").find(entry.title)?.groupValues?.get(1).orEmpty()
        val cacheKey=catalogueKey(key,entry)
        artCache[cacheKey]?.let { return@withContext it }
        artGate.acquire()
        try {
            artCache[cacheKey]?.let { return@withContext it }
            ensureActive()
            var art=""
            if(key.isNotBlank()) {
                val token=key.trim().removePrefix("Bearer ").trim()
                val bearer=token.contains('.') || token.length>64
                val url="https://api.themoviedb.org/3/search/$type?query=${URLEncoder.encode(title,"UTF-8")}"+if(bearer) "" else "&api_key=${URLEncoder.encode(token,"UTF-8")}"
                val req=Request.Builder().url(url)
                if(bearer) req.header("Authorization","Bearer $token")
                client.newCall(req.build()).execute().use { response ->
                    if(response.isSuccessful) {
                        val arr=JSONObject(response.body?.string().orEmpty()).optJSONArray("results") ?: JSONArray()
                        val candidates=(0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optString("poster_path").startsWith("/") }
                        val exact=candidates.filter { candidate -> listOf(candidate.optString(if(type=="tv") "name" else "title"),candidate.optString(if(type=="tv") "original_name" else "original_title")).any { normalizedTitle(it)==normalizedTitle(title) } }
                        val match=exact.firstOrNull { year.isNotBlank() && it.optString(if(type=="tv") "first_air_date" else "release_date").startsWith(year) } ?: exact.singleOrNull() ?: exact.firstOrNull { year.isBlank() }
                        val path=match?.optString("poster_path").orEmpty()
                        val backdrop=match?.optString("backdrop_path").orEmpty()
                        if(path.startsWith("/")) art="https://image.tmdb.org/t/p/original$path"
                        if(backdrop.startsWith("/")) backdropCache.put(cacheKey,"https://image.tmdb.org/t/p/original$backdrop")
                    }
                }
            }
            ensureActive()
            if(art.isBlank() && type=="tv") {
                client.newCall(Request.Builder().url("https://api.tvmaze.com/search/shows?q=${URLEncoder.encode(title,"UTF-8")}").build()).execute().use { response ->
                    if(response.isSuccessful) {
                        val arr=JSONArray(response.body?.string().orEmpty())
                        val matches=(0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optJSONObject("show") }.filter { it.optString("name").equals(title,true) && (year.isBlank() || it.optString("premiered").startsWith(year)) }
                        art=matches.singleOrNull()?.optJSONObject("image")?.optString("original").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
                    }
                }
            }
            if(art.isNotBlank()) artCache.put(cacheKey,art)
            art
        } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; "" } finally { artGate.release() }
    }

    data class ProviderCataloguePage(val entries: List<Entry>, val totalPages: Int)

    suspend fun providerCatalogue(key:String,type:String,provider:Int,sort:String,genre:Int?,region:String,page:Int):ProviderCataloguePage = withContext(Dispatchers.IO) {
        val token=key.trim().removePrefix("Bearer ").trim()
        val bearer=token.contains('.') || token.length>64
        val genreFilter = genre?.let { "&with_genres=$it" }.orEmpty()
        val url="https://api.themoviedb.org/3/discover/$type?language=en-GB&watch_region=$region&with_watch_providers=$provider&sort_by=${URLEncoder.encode(sort,"UTF-8")}&page=$page$genreFilter" + if(bearer) "" else "&api_key=${URLEncoder.encode(token,"UTF-8")}"
        val request=Request.Builder().url(url)
        if(bearer) request.header("Authorization","Bearer $token")
        client.newCall(request.build()).execute().use { response ->
            if(!response.isSuccessful) throw IllegalStateException("TMDB HTTP ${response.code}")
            val body = JSONObject(response.body?.string().orEmpty())
            val array = body.getJSONArray("results")
            val entries = (0 until array.length()).mapNotNull { i ->
                val obj=array.optJSONObject(i) ?: return@mapNotNull null
                val poster=obj.optString("poster_path"); val backdrop=obj.optString("backdrop_path")
                Entry(obj.optString("id"),obj.optString(if(type=="tv") "name" else "title","Untitled"),
                    if(poster.startsWith("/")) "https://image.tmdb.org/t/p/original$poster" else "",
                    kind="tmdb",backdrop=if(backdrop.startsWith("/")) "https://image.tmdb.org/t/p/original$backdrop" else "",
                    year=obj.optString(if(type=="tv") "first_air_date" else "release_date").take(4),
                    rating=if(obj.optDouble("vote_average",0.0)>0) String.format(Locale.US,"%.1f",obj.optDouble("vote_average")) else "",
                    genre=if(type=="tv") "TV Series" else "Movie",overview=obj.optString("overview"))
            }
            ProviderCataloguePage(entries, body.optInt("total_pages", page).coerceAtMost(500))
        }
    }

    suspend fun trending(key:String,type:String,popular:Boolean=false):List<Entry> = withContext(Dispatchers.IO) {
        val token=key.trim().removePrefix("Bearer ").trim()
        val path=if(popular) "$type/popular" else "trending/$type/week"
        val bearer=token.contains('.') || token.length>64
        val url="https://api.themoviedb.org/3/$path?language=en-US" + if(bearer) "" else "&api_key=${URLEncoder.encode(token,"UTF-8")}"
        val request=Request.Builder().url(url)
        if(bearer) request.header("Authorization","Bearer $token")
        client.newCall(request.build()).execute().use { response ->
            if(!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} (check TMDB API key)")
            val array=JSONObject(response.body?.string().orEmpty()).optJSONArray("results")?:return@withContext emptyList()
            (0 until minOf(array.length(),20)).mapNotNull {i ->
                val obj=array.optJSONObject(i)?:return@mapNotNull null
                val poster=obj.optString("poster_path");val backdrop=obj.optString("backdrop_path")
                val image=if(poster.startsWith("/")) "https://image.tmdb.org/t/p/original$poster" else ""
                Entry(obj.optString("id"),obj.optString(if(type=="tv")"name" else "title","Untitled"),image,kind="tmdb",backdrop=if(backdrop.startsWith("/"))"https://image.tmdb.org/t/p/original$backdrop" else image,
                    year=obj.optString(if(type=="tv")"first_air_date" else "release_date").take(4),
                    rating=if(obj.optDouble("vote_average",0.0)>0) String.format(Locale.US,"%.1f",obj.optDouble("vote_average")) else "",
                    genre=if(type=="tv")"TV Series" else "Movie", overview=obj.optString("overview"))
            }
        }
    }
}

private fun metalPlateBrush() = Brush.verticalGradient(0f to Color(0xFFB0BAC4),0.18f to Color(0xFF45515D),0.45f to Color(0xFF222D38),0.66f to Color(0xFF77838E),0.85f to Color(0xFF313D48),1f to Color(0xFF151E27))
private fun chromeBrush() = Brush.verticalGradient(0f to Color(0xFFFDFEFF),0.24f to Color(0xFFF4F7FA),0.43f to Color(0xFF8794A0),0.49f to Color(0xFF101820),0.56f to Color(0xFF697989),0.73f to Color(0xFFD5DDE4),1f to Color(0xFF798894))
@Composable private fun Text(text:String, modifier:Modifier=Modifier, color:Color=Color.Unspecified, fontSize:TextUnit=TextUnit.Unspecified, fontWeight:FontWeight?=null, maxLines:Int=Int.MAX_VALUE, minLines:Int=1, overflow:TextOverflow=TextOverflow.Clip, style:TextStyle=LocalTextStyle.current) {
    MaterialText(text=text, modifier=modifier, color=color, fontSize=fontSize, fontWeight=when(fontWeight){FontWeight.Light -> FontWeight.Light; FontWeight.Bold, FontWeight.SemiBold, FontWeight.Medium -> FontWeight.Medium; else -> FontWeight.Normal}, maxLines=maxLines, minLines=minLines, overflow=overflow, style=style.copy(fontFamily=SkyLikeFamily,fontStyle=androidx.compose.ui.text.font.FontStyle.Normal,letterSpacing=0.08.sp,shadow=style.shadow))
}
@Composable private fun ChromeSettingsBadge(label: String) {
    // Transparent metallic fin, with a subtle blue pulse instead of a square badge.
    val motion = rememberInfiniteTransition(label = "Settings fin glow")
    val glow = motion.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "Fin halo")
    Canvas(Modifier.size(38.dp)) {
        val fin = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .14f, size.height * .77f)
            cubicTo(size.width * .31f, size.height * .34f, size.width * .63f, size.height * .09f, size.width * .85f, size.height * .08f)
            cubicTo(size.width * .65f, size.height * .40f, size.width * .66f, size.height * .58f, size.width * .86f, size.height * .76f)
            quadraticBezierTo(size.width * .47f, size.height * .92f, size.width * .14f, size.height * .77f)
            close()
        }
        drawOval(Color(0xFF00BFFF), topLeft=Offset(size.width*.08f,size.height*.73f),
            size=androidx.compose.ui.geometry.Size(size.width*.84f,size.height*.19f),
            alpha=glow.value, style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
        drawPath(fin, Brush.linearGradient(listOf(Color.White, Color(0xFF9EACBD), Color(0xFF243449), Color(0xFFECF5FF))))
        drawPath(fin, Color(0xFF71DBFF), style=androidx.compose.ui.graphics.drawscope.Stroke(width=0.8.dp.toPx()))
    }
}

@Composable private fun ChromeIcon(symbol: ImageVector, iconSize: Int) {
    val finish=chromeBrush()
    Box(Modifier.size((iconSize+2).dp),contentAlignment=Alignment.Center) {
        Icon(symbol,null,tint=Color(0xFF020916),modifier=Modifier.offset(x=1.dp,y=2.dp).size((iconSize+1).dp))
        Icon(symbol,null,tint=Color(0xFFB9D5EC),modifier=Modifier.offset(y=(-0.6).dp).size(iconSize.dp))
        Icon(symbol,null,tint=Color.White,modifier=Modifier.size(iconSize.dp).graphicsLayer { compositingStrategy=CompositingStrategy.Offscreen }.drawWithCache { onDrawWithContent { drawContent(); drawRect(finish,blendMode=BlendMode.SrcAtop) } })
    }
}
@Composable private fun MetallicText(value:String,size:Int) {
    Text(value,color = Color.White,fontSize=size.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
}
private data class AudioTrackSwitch(
    val previous: TrackSelectionParameters,
    val startedAt: Long
)

@Composable private fun SafePlayer(url:String,onExit:()->Unit={},onChannel:(Int)->Unit={},title:String="",liveStream:Boolean=false,onFailure:(Long)->Unit={},startupTimeoutMs:Long=0L,resumeMs:Long=0L,programmes:List<Program>?=null,inferFormat:Boolean=false,sharkMega:Boolean=false,onFirstFrame:()->Unit={}) {
    val context=LocalContext.current
    var guideTime by remember(url) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(url, liveStream) {
        if (liveStream) while (true) {
            guideTime = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val playerPreferences = remember(context) { playbackPrefs(context) }
    var liveBarStyle by remember {
        mutableStateOf(LiveBarStyle.entries.firstOrNull {
            it.name == playerPreferences.getString("liveBarStyle", null)
        } ?: LiveBarStyle.BLUE_SMOKE)
    }
    val barAccent = if (liveStream) liveBarStyle.accent else Color(0xFF327AFF)
    val schedule = remember(programmes) {
        programmes.orEmpty().mapNotNull { programme ->
            val start = parseEpgTime(programme.start) ?: return@mapNotNull null
            val end = parseEpgTime(programme.end) ?: return@mapNotNull null
            TimedProgramme(programme.title, start, end).takeIf { it.end > it.start && it.title.isNotBlank() }
        }.sortedBy { it.start }
    }
    val currentProgramme = schedule.lastOrNull { guideTime >= it.start && guideTime < it.end }
    val nextProgramme = schedule.firstOrNull { it.start > guideTime }
    // One embedded engine; legacy external-player preferences are ignored.
    var playerError by remember(url){mutableStateOf("")}
    var panel by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var resize by remember { mutableStateOf(playbackPrefs(context).getInt("resize",0)) }
    var position by remember(url) { mutableStateOf(0L) }
    var duration by remember(url) { mutableStateOf(0L) }
    var playing by remember(url) { mutableStateOf(false) }
    val rootFocus=remember { androidx.compose.ui.focus.FocusRequester() }
    val controlFocus=remember { androidx.compose.ui.focus.FocusRequester() }
    val player=remember(context, sharkMega) { configuredPlayer(context, sharkMega = sharkMega) }
    var audioSwitch by remember(player, url) { mutableStateOf<AudioTrackSwitch?>(null) }
    var trackNotice by remember(url) { mutableStateOf("") }
    var tracks by remember(player, url) { mutableStateOf(Tracks.EMPTY) }
    LaunchedEffect(trackNotice) {
        if (trackNotice.isNotEmpty()) {
            delay(5_000)
            trackNotice = ""
        }
    }
    fun selectTrack(type: Int, override: TrackSelectionOverride? = null) {
        val previous = player.trackSelectionParameters
        val next = previous.buildUpon().clearOverridesOfType(type)
            .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && override == null)
        if (override != null) next.setOverrideForType(override)
        val selection = next.build()
        if (selection != previous) {
            if (type == C.TRACK_TYPE_AUDIO) {
                audioSwitch = AudioTrackSwitch(previous, android.os.SystemClock.elapsedRealtime())
            }
            try {
                player.trackSelectionParameters = selection
            } catch (error: RuntimeException) {
                if (type == C.TRACK_TYPE_AUDIO) audioSwitch = null
                trackNotice = "Could not switch tracks. Playback settings were kept."
                android.util.Log.w("SharkPlayback", "Track selection failed", error)
            }
        }
        panel = ""
        interaction++
    }
    LaunchedEffect(player,url) {
        playerError=""
        android.util.Log.d("SharkPlayback","Preparing stream")
        player.setMediaItem(sharkMediaItem(context,url,inferFormat));if(resumeMs>0L) player.seekTo(resumeMs);player.prepare();player.playWhenReady=true
    }
    val latestFailure by rememberUpdatedState(onFailure)
    val latestFirstFrame by rememberUpdatedState(onFirstFrame)
    var firstFrame by remember(url) { mutableStateOf(false) }
    var showPlayerBrand by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, firstFrame) {
        if (firstFrame) {
            latestFirstFrame()
            showPlayerBrand = true
            delay(2_000)
            showPlayerBrand = false
        }
    }
    var failureSent by remember(url) { mutableStateOf(false) }
    fun reportFailure() {
        if(failureSent) return
        failureSent=true
        latestFailure(player.currentPosition.coerceAtLeast(0L))
    }
    LaunchedEffect(player,url,startupTimeoutMs) {
        if(startupTimeoutMs<=0L) return@LaunchedEffect
        var stalledMs=0L
        var lastPosition=player.currentPosition
        var recentBufferMs=0L
        var windowMs=0L
        while(!failureSent) {
            delay(1000L)
            val pos=player.currentPosition
            val active=player.playWhenReady && player.playbackSuppressionReason==androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE
            val state=player.playbackState
            if(!active || state==androidx.media3.common.Player.STATE_ENDED) {
                stalledMs=0L; recentBufferMs=0L; windowMs=0L
            } else {
                val buffering=state==androidx.media3.common.Player.STATE_BUFFERING
                val progressing=pos>lastPosition+100L && player.isPlaying
                // Only count genuine buffering or stalled playback, not a paused/ready player.
                stalledMs=if(progressing || (!buffering && state!=androidx.media3.common.Player.STATE_READY)) 0L else if(buffering || (state==androidx.media3.common.Player.STATE_READY && !progressing)) stalledMs+1000L else 0L
                if(firstFrame) {
                    windowMs+=1000L
                    if(buffering) recentBufferMs+=1000L
                }
                if(stalledMs >= (if(firstFrame) 25000L else startupTimeoutMs) || recentBufferMs>=30000L) {
                    playerError="Source too slow — switching link"
                    reportFailure()
                }
                if(windowMs>=60000L) { windowMs=0L; recentBufferMs=0L }
            }
            lastPosition=pos
        }
    }
    DisposableEffect(player,url) {
        val started=android.os.SystemClock.elapsedRealtime()
        var recoveryCount=0
        var lastRecovery=0L
        val handler = android.os.Handler(player.applicationLooper)
        val listener=object:androidx.media3.common.Player.Listener {
            override fun onPlayerError(error:androidx.media3.common.PlaybackException){
                val now=android.os.SystemClock.elapsedRealtime()
                val pending = audioSwitch
                val renderer = error as? ExoPlaybackException
                val audioError = renderer?.rendererFormat?.sampleMimeType?.startsWith("audio/") == true ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
                if (pending != null && now - pending.startedAt <= 10_000L && audioError) {
                    audioSwitch = null
                    handler.post {
                        player.trackSelectionParameters = pending.previous
                        player.prepare()
                        trackNotice = "This audio track couldn’t play. Restored the previous audio."
                    }
                    return
                }
                if(now-lastRecovery>60000L) recoveryCount=0
                if(liveStream && error.errorCode==androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && recoveryCount<3){
                    recoveryCount++;lastRecovery=now;playerError=""
                    player.seekToDefaultPosition();player.prepare();player.playWhenReady=true
                }else {playerError="Playback failed (${error.errorCodeName})";reportFailure()}
            }
            override fun onTracksChanged(current: Tracks) { tracks = current }
            override fun onPlaybackStateChanged(state:Int){android.util.Log.d("SharkPlayback","State $state after ${android.os.SystemClock.elapsedRealtime()-started} ms")}
            override fun onRenderedFirstFrame(){firstFrame=true;android.util.Log.d("SharkPlayback","First frame after ${android.os.SystemClock.elapsedRealtime()-started} ms")}
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            handler.removeCallbacksAndMessages(null)
        }
    }
    PlayerLifecycle(player)
    DisposableEffect(player) { onDispose {player.release()} }
    LaunchedEffect(player,visible,panel) { while(visible || panel.isNotEmpty()) { position=player.currentPosition.coerceAtLeast(0);duration=player.duration.coerceAtLeast(0);playing=player.isPlaying;delay(500) } }
    LaunchedEffect(visible,panel,interaction) { if(visible && panel.isEmpty()) { delay(7000);visible=false } }
    LaunchedEffect(visible,panel) { if(panel.isEmpty()) { runCatching { if(visible) controlFocus.requestFocus() else rootFocus.requestFocus() } } }
    BackHandler { when { panel.isNotEmpty() -> { panel="";visible=false;interaction++ }; visible -> { visible=false;interaction++ }; else -> onExit() } }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) false else {
            interaction++
            when(event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> if(liveStream && !visible){onChannel(1);true}else false
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if(liveStream && !visible){onChannel(-1);true}else false
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {onChannel(1);true}
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {onChannel(-1);true}
                // When the player overlay is hidden, remote left/right seeks VOD directly.
                // With the overlay visible, DPAD navigation stays with the focusable controls.
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> if(!liveStream && !visible && player.isCurrentMediaItemSeekable) {
                    player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L)); true
                } else false
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> if(!liveStream && !visible && player.isCurrentMediaItemSeekable) {
                    val target = player.currentPosition + 10000L
                    val end = player.duration
                    player.seekTo(if(end > 0L) target.coerceAtMost(end) else target); true
                } else false
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if(player.isPlaying) player.pause() else player.play(); true }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                android.view.KeyEvent.KEYCODE_GUIDE -> if (liveStream) { visible = true; panel = "TV Guide"; true } else false
                android.view.KeyEvent.KEYCODE_MENU -> {visible=true;panel="Settings";true}
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> if(!visible){visible=true;true}else false
                else -> false
            }
        }
    }.focusRequester(rootFocus).focusable()) {
        AndroidView(factory={ctx->PlayerView(ctx).apply {this.player=player;useController=false;isFocusable=false;descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS}},update={it.player=player;it.resizeMode=resize},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
        androidx.compose.animation.AnimatedVisibility(
            visible = showPlayerBrand,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = 22.dp),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Row(
                Modifier.clip(SharkFrameShape).background(Color(0xD9081126))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(width = 24.dp, height = 20.dp)) {
                    val fin = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height)
                        cubicTo(size.width * .23f, size.height * .44f, size.width * .60f, 0f, size.width, 0f)
                        cubicTo(size.width * .76f, size.height * .36f, size.width * .78f, size.height * .64f, size.width * .89f, size.height * .84f)
                        quadraticBezierTo(size.width * .52f, size.height * .64f, size.width * .34f, size.height)
                        close()
                    }
                    drawPath(fin, Color(0xFF327AFF))
                }
                Text("SharkNailTV", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Fade the playback controls instead of sliding the entire complex bar.
        // Sliding remeasures/repositions its contents every frame and can stutter on Android TV.
        androidx.compose.animation.AnimatedVisibility(visible,modifier=Modifier.align(Alignment.BottomCenter),enter=androidx.compose.animation.fadeIn(animationSpec=tween(120)),exit=androidx.compose.animation.fadeOut(animationSpec=tween(140))) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xC9082657), Color(0xB8061738))), androidx.compose.ui.graphics.RectangleShape)
                    .border(2.dp, Color(0xFF12B8FF), androidx.compose.ui.graphics.RectangleShape)
                    .padding(horizontal = if (liveStream) liveBarStyle.inset.dp else 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(25.dp).clip(SharkFrameShape).background(barAccent))
                    Text(title.ifBlank { "SharkNailTV" }, modifier = Modifier.weight(1f), color = Color.White,
                        fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (liveStream) Text("● LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (liveStream) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgrammeTile("Now", currentProgramme, if (programmes == null) "Loading guide…" else "No programme information", Modifier.weight(1f))
                        ProgrammeTile("Next", nextProgramme, "No upcoming listing", Modifier.weight(1f))
                    }
                    if (currentProgramme != null) {
                        val elapsed = (guideTime-currentProgramme.start).toFloat() / (currentProgramme.end-currentProgramme.start)
                        LinearProgressIndicator(progress = { elapsed.coerceIn(0f,1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(SharkFrameShape),
                            color = barAccent, trackColor = Color(0xFF2D3B59))
                    }
                } else {
                    LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(SharkFrameShape),
                        color = Color(0xFF327AFF), trackColor = Color(0xFF2D3B59))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(playTime(position), color = Color.White, fontSize = 12.sp)
                        Text(if (duration > 0) playTime(duration) else "", color = Color.White, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(!liveStream) PlayerTile("−30s", Icons.Filled.Replay30, Modifier.weight(1f)){if(player.isCurrentMediaItemSeekable)player.seekTo((player.currentPosition-30000L).coerceAtLeast(0))}
                    PlayerTile(if (playing) "Pause" else "Play", if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, Modifier.weight(1f).focusRequester(controlFocus)){if(player.isPlaying)player.pause() else player.play()}
                    if(!liveStream) PlayerTile("+30s", Icons.Filled.Forward30, Modifier.weight(1f)){if(player.isCurrentMediaItemSeekable)player.seekTo((player.currentPosition+30000L).coerceAtMost(if(duration>0)duration else Long.MAX_VALUE))}
                    if(liveStream) PlayerTile("TV Guide", Icons.Filled.List, Modifier.weight(1f)){ panel="TV Guide" }
                    listOf(
                        "Audio" to Icons.Filled.VolumeUp,
                        "Subtitles" to Icons.Filled.Subtitles,
                        "Screen" to Icons.Filled.Tv,
                        "Settings" to Icons.Filled.Settings,
                        "Info" to Icons.Filled.Info
                    ).forEach { (name, icon) -> PlayerTile(name, icon, Modifier.weight(1f)) { panel = name } }
                }
            }
        }
        if(panel.isNotEmpty()) PremiumDialog(onDismissRequest={panel="";interaction++},title={Text(panel,color=Color.White)},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            when(panel) {
                "TV Guide" -> {
                    val remaining = schedule.filter { it.end > guideTime }
                    if (remaining.isEmpty()) Text(if (programmes == null) "Loading guide…" else "No listings from your TV provider.", color = Color.White)
                    remaining.forEach { listing ->
                        ProgrammeTile(if (listing == currentProgramme) "Now" else "Upcoming", listing, "", Modifier.fillMaxWidth().padding(bottom = 6.dp))
                    }
                }
                "Audio","Subtitles" -> {
                    val type = if (panel == "Audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    val options = tracks.groups.filter { it.type == type }.flatMap { group ->
                        (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it }
                    }
                    FocusAction(if (type == C.TRACK_TYPE_TEXT) "Off" else "Automatic") {
                        selectTrack(type)
                    }
                    options.forEach { (group, index) ->
                        val format = group.getTrackFormat(index)
                        val language = format.language?.let {
                            Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).ifBlank { it }
                        }
                        val label = listOfNotNull(format.label, language, format.sampleMimeType)
                            .distinct().joinToString(" • ").ifBlank { "Track ${index + 1}" }
                        val selectedTrack = group.isTrackSelected(index)
                        FocusAction(label + if (selectedTrack) " ✓" else "") {
                            val current = player.currentTracks.groups.firstOrNull {
                                it.mediaTrackGroup == group.mediaTrackGroup
                            }
                            if (current == null || index >= current.length || !current.isTrackSupported(index)) {
                                trackNotice = "That track is no longer available."
                                panel = ""
                                interaction++
                            } else if (current.isTrackSelected(index)) {
                                panel = ""
                                interaction++
                            } else {
                                selectTrack(type, TrackSelectionOverride(current.mediaTrackGroup, index))
                            }
                        }
                    }
                    if (options.isEmpty()) Text("No supported tracks available", color = Color.White)
                }
                "Screen" -> listOf("Fit" to 0,"Fill" to 3,"Zoom" to 4).forEach { (name,mode) -> FocusAction(if(resize==mode) "$name (selected)" else name){resize=mode;panel="";interaction++} }
                "Settings" -> { if (liveStream) FocusAction("Channel bar style") { panel = "Channel bar style" }; listOf(0.75f,1f,1.25f,1.5f,2f).forEach { speed -> FocusAction("Speed ${speed}x"){player.setPlaybackSpeed(speed);panel="";interaction++} };FocusAction("Retry playback"){playerError="";player.prepare();player.playWhenReady=true;panel="";interaction++} }
                "Channel bar style" -> LiveBarStyle.entries.forEach { style ->
                    FocusAction(if (style == liveBarStyle) "${style.label} ✓" else style.label) {
                        liveBarStyle = style
                        playerPreferences.edit().putString("liveBarStyle", style.name).apply()
                        panel = ""
                        interaction++
                    }
                }
                "Info" -> {Text("Video: ${player.videoFormat?.width ?: 0} × ${player.videoFormat?.height ?: 0}\nCodec: ${player.videoFormat?.sampleMimeType ?: "Unknown"}\nAudio: ${player.audioFormat?.sampleMimeType ?: "Unknown"}\nFPS: ${player.videoFormat?.frameRate ?: 0}",color=Color.White)}
            }
        }},confirmButton={PremiumButton(onClick={panel="";interaction++}){Text("Close",color=Color.White)}})
        val playbackMessage = playerError.ifBlank { trackNotice }
        if (playbackMessage.isNotBlank()) {
            Text(playbackMessage, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).hudSurface().padding(10.dp))
        }
    }
}

private fun playTime(ms:Long):String { val sec=ms/1000;return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60) }
private data class TimedProgramme(val title: String, val start: Long, val end: Long)

@Composable private fun ProgrammeTile(label: String, programme: TimedProgramme?, fallback: String, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.background(Color(0xFF182844), SharkFrameShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
        val time = programme?.let { "  ·  ${guideClock(context,it.start)}–${guideClock(context,it.end)}" }.orEmpty()
        Text(label + time, color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(programme?.title ?: fallback, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun PlayerTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val yellowPulse = rememberInfiniteTransition(label = "Sky yellow focus pulse")
    val glow by yellowPulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "focused button glow")
    val focusYellow = Color(0xFFFFD53D)
    Column(
        modifier.height(54.dp).onFocusChanged { focused = it.isFocused }
            .background(
                Brush.verticalGradient(listOf(Color(0xB20C2854), Color(0xB9081737))),
                androidx.compose.ui.graphics.RectangleShape
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) focusYellow.copy(alpha = glow) else Color(0xFF2D83CF),
                androidx.compose.ui.graphics.RectangleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) focusYellow else Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = if (focused) focusYellow else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun parseEpgTime(raw:String):Long? {
    val value=raw.trim()
    value.toLongOrNull()?.let { return if(it < 100000000000L) it*1000 else it }
    for(pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss")) {
        try { return SimpleDateFormat(pattern,Locale.US).parse(value)?.time } catch (_:Exception) { }
    }
    return null
}
private fun sportCategory(event:SportEvent):String {
    val text="${event.program.title} ${event.channel.title}".lowercase(Locale.US)
    return when {
        listOf("formula 1","grand prix","f1 ","motorsport").any{text.contains(it)} -> "F1"
        listOf("boxing","fight night","ufc","mma").any{text.contains(it)} -> "Combat"
        listOf("cricket","ipl ").any{text.contains(it)} -> "Cricket"
        listOf("rugby","six nations").any{text.contains(it)} -> "Rugby"
        listOf("tennis","wimbledon","atp ").any{text.contains(it)} -> "Tennis"
        listOf("football","soccer","premier league","champions league","la liga","serie a","bundesliga"," vs "," v ").any{text.contains(it)} -> "Football"
        else -> "Other"
    }
}
private fun displayTime(time:Long):String = SimpleDateFormat("EEE d MMM  •  HH:mm",Locale.getDefault()).format(Date(time))
@Composable private fun PremiumShortcut(name:String,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val symbol=when(name){"Shark TV"->"◉";"Movies"->"▣";"TV Series"->"▥";else->"⚽"}
    Column(Modifier.width(148.dp).height(84.dp).onFocusChanged{focused=it.isFocused}
        .glassFrame(2.dp,if(focused)gold else Color(0xFF22528A),RoundedCornerShape(0.dp), focused = focused)
        .background(Brush.verticalGradient(listOf(Color(0xFF143F77),Color(0xFF081B37))),RoundedCornerShape(0.dp))
        .clickable(onClick=onClick).padding(12.dp),verticalArrangement=Arrangement.Center) {
        Text(symbol,color=gold,fontSize=24.sp,fontWeight=FontWeight.Medium)
        Text(name.uppercase(),color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Medium)
    }
}
@Composable private fun SportsHomePreview(events:List<SportEvent>,connected:Boolean,onOpen:()->Unit) {
    val now=System.currentTimeMillis()
    val active=events.filter { it.startMillis<=now && it.endMillis>now }.take(3)
    val upcoming=events.filter { it.startMillis>now }.take(4)
    Column(Modifier.fillMaxWidth().padding(bottom=20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("⚽  SPORTS",color=Color.White,fontWeight=FontWeight.Medium,fontSize=20.sp)
            Spacer(Modifier.width(20.dp))
            Text("LIVE NOW",color = Color.White,fontSize=12.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.width(18.dp))
            Text("UPCOMING",color = Color.White,fontSize=12.sp)
            Spacer(Modifier.weight(1f))
            PremiumButton(onClick=onOpen){Text("VIEW FULL GUIDE  ›",color = Color.White,fontSize=12.sp)}
        }
        Row(Modifier.fillMaxWidth().height(195.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1.75f),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                repeat(3) { index ->
                    val event=active.getOrNull(index)
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.weight(1f).fillMaxHeight().onFocusChanged{focused=it.isFocused}
                        .glassFrame(2.dp,if(focused)gold else Color(0xFF2667B2),RoundedCornerShape(0.dp), focused = focused)
                        .background(Brush.verticalGradient(listOf(Color(0xFF123568),Color(0xFF06142B))),RoundedCornerShape(0.dp))
                        .clickable(onClick=onOpen).padding(11.dp),verticalArrangement=Arrangement.SpaceBetween) {
                        Text(if(event!=null) "● LIVE" else "SPORTS",color=if(event!=null)Color(0xFFFF7777) else gold,fontSize=11.sp,fontWeight=FontWeight.Medium)
                        Text(event?.program?.title ?: if(connected) "No live event listed" else "Connect IPTV to see live matches",
                            color=Color.White,fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text(event?.channel?.title ?: "Provider EPG",color = Color.White,fontSize=11.sp,maxLines=2)
                        Box(Modifier.fillMaxWidth().background(Color(0xFF086BC6),RoundedCornerShape(0.dp)).padding(8.dp),contentAlignment=Alignment.Center) {
                            Text(if(event!=null) "▶ WATCH LIVE" else "SPORTS GUIDE",color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Medium)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().glassFrame(1.dp, Color.Cyan)
                .background(Color(0xFF071B38),RoundedCornerShape(0.dp)).padding(11.dp)) {
                Text("UPCOMING EVENTS",color=Color.White,fontWeight=FontWeight.Medium,fontSize=14.sp)
                Spacer(Modifier.height(8.dp))
                if(upcoming.isEmpty()) Text(if(connected) "No upcoming fixtures in your provider's EPG."
                    else "Connect Xtream Codes to see kick-off times and channels.",color = Color.White,fontSize=11.sp)
                upcoming.forEach { event ->
                    Text(event.program.title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=11.sp,maxLines=1)
                    Text("${displayTime(event.startMillis)}  •  ${event.channel.title}",color = Color.White,fontSize=10.sp,maxLines=1)
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
    }
}
@Composable private fun SportsGuide(events:List<SportEvent>,loading:Boolean,connected:Boolean,filter:String,onFilter:(String)->Unit,onSelect:(Entry)->Unit,onConnect:()->Unit) {
    val now=System.currentTimeMillis()
    Column(Modifier.fillMaxWidth()) {
        Text("SPORTS GUIDE",color=Color.White,fontSize=33.sp,fontWeight=FontWeight.Medium)
        Text("LIVE  •  UPCOMING  •  CHANNELS",color=gold,fontSize=12.sp,fontWeight=FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(listOf("All Sports","Football","F1","Combat","Cricket","Rugby","Tennis","Other")) { option ->
            var focused by remember {mutableStateOf(false)}
            Box(Modifier.onFocusChanged{focused=it.isFocused}.glassFrame(2.dp,if(focused)gold else Color(0xFF2474BD),RoundedCornerShape(0.dp), focused = focused).background(if(option==filter)Color(0xFF1268C4) else Color(0xFF0B2548),RoundedCornerShape(0.dp)).clickable{onFilter(option)}.padding(horizontal=16.dp,vertical=12.dp)) {Text(option,color=Color.White,fontWeight=FontWeight.SemiBold)}
        } }
        Spacer(Modifier.height(16.dp))
        if(!connected) {Text("Connect your IPTV account to display real fixtures, start times and channels.",color=Color.White);ConnectPrompt(onConnect)}
        else {
            if(loading) { LinearProgressIndicator(Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp)) }
            val visible=events.filter {filter=="All Sports" || sportCategory(it)==filter}
            val liveNow=visible.filter{it.startMillis<=now && it.endMillis>now}
            val upcoming=visible.filter{it.startMillis>now}
            Text("● LIVE NOW  (${liveNow.size})",color=Color(0xFFFF7575),fontWeight=FontWeight.Medium,fontSize=18.sp)
            Spacer(Modifier.height(8.dp))
            if(liveNow.isEmpty()) Text("No live events found in the available EPG.",color = Color.White,fontSize=13.sp)
            liveNow.take(12).forEach{ SportsEventCard(it,true,onSelect) }
            Spacer(Modifier.height(18.dp))
            Text("UP NEXT  (${upcoming.size})",color=Color.White,fontWeight=FontWeight.Medium,fontSize=20.sp)
            Spacer(Modifier.height(8.dp))
            if(upcoming.isEmpty() && !loading) Text("No upcoming programmes found. Your provider may not supply sports EPG data for these channels.",color = Color.White,fontSize=13.sp)
            upcoming.take(40).forEach{ SportsEventCard(it,false,onSelect) }
            Text("Schedules and channel assignments come from your IPTV provider. Check listings before relying on them.",color = Color.White,fontSize=11.sp,modifier=Modifier.padding(top=14.dp))
        }
    }
}
@Composable private fun SportsEventCard(event:SportEvent,isLive:Boolean,onSelect:(Entry)->Unit) {
    var focused by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp).onFocusChanged{focused=it.isFocused}.glassFrame(2.dp,if(focused)gold else Color(0xFF24588C),RoundedCornerShape(0.dp), focused = focused).background(Brush.horizontalGradient(listOf(Color(0xFF0E315E),Color(0xFF06182E))),RoundedCornerShape(0.dp)).clickable{onSelect(event.channel)}.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if(isLive)"● LIVE NOW" else sportCategory(event).uppercase(Locale.US),color=if(isLive)Color(0xFFFF7777) else gold,fontSize=11.sp,fontWeight=FontWeight.Medium)
            Text(event.program.title,color=Color.White,fontWeight=FontWeight.Medium,fontSize=17.sp,maxLines=2)
            Text("${displayTime(event.startMillis)}   •   ${event.channel.title}",color = Color.White,fontSize=12.sp,maxLines=2)
        }
        Spacer(Modifier.width(6.dp))
        Text(if(isLive)"▶ WATCH LIVE" else "VIEW CHANNEL  ›",color=Color.White,fontWeight=FontWeight.Medium,fontSize=12.sp,modifier=Modifier.background(Color(0xFF086BC8),RoundedCornerShape(0.dp)).padding(12.dp))
    }
}


@Composable private fun FocusAction(label:String, primary:Boolean=false, modifier:Modifier=Modifier, enabled:Boolean=true, onClick:()->Unit) {
    PremiumButton(onClick=onClick,modifier=modifier,enabled=enabled) { Text(label,color=Color.White,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis) }
}

@Composable private fun PremiumNavTile(label:String,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(45.dp).onFocusChanged { focused=it.isFocused }
        .glassFrame(if(focused) 3.dp else 2.dp,if(focused) SharkFocusGold else Color(0xFF159BFF),RoundedCornerShape(0.dp), focused = focused)
        .hudSurface()
        .clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Text(label,color=Color.White,fontWeight=FontWeight.Medium,fontSize=11.sp,maxLines=1)
    }
}

private data class PublicFixture(val name:String,val date:String,val time:String,val channel:String)
private fun fixtureDisplayTime(event:PublicFixture):String {
    val dateLabel=runCatching { SimpleDateFormat("yyyy-MM-dd",Locale.UK).apply { isLenient=false }.parse(event.date)?.let { SimpleDateFormat("EEE d MMM",Locale.UK).format(it) } }.getOrNull() ?: event.date
    if(event.time=="TBC") return "$dateLabel · Time TBC"
    return "$dateLabel · ${event.time.take(5)} (${if(event.time.endsWith(" UK")) "UK time" else "provider time"})"
}
private object PublicSports {
    private val client=OkHttpClient.Builder().callTimeout(12,java.util.concurrent.TimeUnit.SECONDS).build()
    private var cached = emptyList<PublicFixture>()
    private var cachedKey = ""
    private var cachedAt = 0L
    suspend fun load(key:String, force:Boolean=false):List<PublicFixture> = withContext(Dispatchers.IO) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(!force && key==cachedKey && cachedAt > 0L && now-cachedAt < 1800000L) return@withContext cached
        val zone=java.util.TimeZone.getTimeZone("Europe/London")
        val calendar=java.util.Calendar.getInstance(zone)
        val fmt=SimpleDateFormat("yyyy-MM-dd",Locale.UK).apply { timeZone=zone }
        val dates=(0..29).map { offset -> (calendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.DATE,offset) }.let { fmt.format(it.time) } }
        val result=mutableListOf<PublicFixture>()
        var successful=0
        suspend fun fetch(url:String):JSONObject? {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return try { client.newCall(Request.Builder().url(url).build()).execute().use { response -> if(response.isSuccessful) JSONObject(response.body?.string().orEmpty()) else null } } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; null }
        }
        fun addEvents(data:JSONObject, fallbackDate:String="") {
            val events=data.optJSONArray("events") ?: return
            for(i in 0 until events.length()) {
                val o=events.optJSONObject(i) ?: continue
                val name=o.optString("strEvent"); if(name.isBlank()) continue
                var date=o.optString("dateEvent",fallbackDate)
                var time=o.optString("strTime").takeUnless { it.isBlank() || it=="null" } ?: "TBC"
                val stamp=o.optString("strTimestamp")
                val utc=runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT).apply { timeZone=java.util.TimeZone.getTimeZone("UTC"); isLenient=false }.parse(stamp) }.getOrNull()
                if(utc!=null) { date=fmt.format(utc); time=SimpleDateFormat("HH:mm",Locale.UK).apply { timeZone=zone }.format(utc)+" UK" }
                val rawSport=o.optString("strSport"); val league=o.optString("strLeague")
                val sport=when { rawSport.equals("Soccer",true) || rawSport.equals("Football",true) -> "Football"; rawSport.equals("Boxing",true) -> "Boxing"; league.contains("UFC",true) -> "UFC"; else -> continue }
                if(date !in (if(sport=="Football") dates.take(7) else dates)) continue
                result.add(PublicFixture(name,date,time,"$sport · $league · broadcaster unverified"))
            }
        }
        val base="https://www.thesportsdb.com/api/v1/json/${URLEncoder.encode(key.ifBlank { "123" }, "UTF-8")}"
        for(date in dates.take(7)) {
            val data=fetch("$base/eventsday.php?d=$date") ?: continue
            successful++; addEvents(data,date)
        }
        // League-specific fallback avoids relying on the global daily result cap.
        for(id in listOf(4387,4388,4370,4407,4408,4424,4442,4430,4425,4391)) {
            val data=fetch("$base/eventsnextleague.php?id=$id") ?: continue
            successful++; addEvents(data)
        }
        // Additional public scoreboard feeds; availability is best-effort.
        val extraFeeds=listOf("soccer/eng.1" to "Football", "soccer/eng.2" to "Football", "soccer/uefa.champions" to "Football", "soccer/eng.fa" to "Football", "soccer/sco.1" to "Football", "mma/ufc" to "UFC", "boxing" to "Boxing")
        for((feed,sport) in extraFeeds) {
            val feedRange=dates.first().replace("-","")+"-"+(if(sport=="Football") dates[6] else dates.last()).replace("-","")
            val data=fetch("https://site.api.espn.com/apis/site/v2/sports/$feed/scoreboard?dates=$feedRange&limit=100") ?: continue
            val events=data.optJSONArray("events") ?: continue
            successful++
            val league=data.optJSONArray("leagues")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } ?: feed.substringAfter('/')
            for(i in 0 until events.length()) {
                val event=events.optJSONObject(i) ?: continue
                val name=event.optString("name").ifBlank { event.optString("shortName") }; if(name.isBlank()) continue
                val stamp=event.optString("date")
                val utc=runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.ROOT).apply { timeZone=java.util.TimeZone.getTimeZone("UTC"); isLenient=false }.parse(stamp) }.getOrNull() ?: continue
                val date=fmt.format(utc); if(date !in (if(sport=="Football") dates.take(7) else dates)) continue
                val time=SimpleDateFormat("HH:mm",Locale.UK).apply { timeZone=zone }.format(utc)+" UK"
                result.add(PublicFixture(name,date,time,"$sport · $league · broadcaster unverified"))
            }
        }
        val year=calendar.get(java.util.Calendar.YEAR).let { if(calendar.get(java.util.Calendar.MONTH)<6) it-1 else it }
        val season="$year-${(year+1)%100}"
        for(league in listOf("en.1","en.2","en.3","en.4","de.1","es.1","it.1","fr.1")) {
            val data=fetch("https://raw.githubusercontent.com/openfootball/football.json/master/$season/$league.json") ?: continue
            successful++
            val matches=data.optJSONArray("matches") ?: continue
            for(i in 0 until matches.length()) {
                val o=matches.optJSONObject(i) ?: continue
                val date=o.optString("date"); if(date !in dates.take(7)) continue
                val home=o.optString("team1"); val away=o.optString("team2"); if(home.isBlank() || away.isBlank()) continue
                val leagueName=if(league.startsWith("en.")) "English " + data.optString("name",league) else data.optString("name",league)
                result.add(PublicFixture("$home vs $away",date,"TBC","Football · $leagueName · broadcaster unverified"))
            }
        }
        // Dedicated public world-title schedule; dates only, never invent times or broadcasters.
        val boxing = try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            client.newCall(Request.Builder().url("https://openboxing.org/api/bouts/scheduled/index.json").build()).execute().use { response ->
                if(response.isSuccessful) JSONArray(response.body?.string().orEmpty()) else null
            }
        } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; null }
        if(boxing!=null) {
            successful++
            for(i in 0 until boxing.length()) {
                val bout=boxing.optJSONObject(i) ?: continue
                val date=bout.optString("date")
                if(date !in dates || bout.optString("status")!="SCHEDULED") continue
                val boxers=bout.optJSONObject("boxers") ?: continue
                fun boxer(side:String):String {
                    val n=boxers.optJSONObject(side)?.optJSONObject("name") ?: return ""
                    return listOf(n.optString("first"),n.optString("last")).filter { it.isNotBlank() && it!="null" }.joinToString(" ").ifBlank { n.optString("short") }
                }
                val a=boxer("boxerA"); val b=boxer("boxerB")
                if(a.isBlank() || b.isBlank()) continue
                val weight=bout.optJSONObject("weight")?.optString("class").orEmpty()
                result.add(PublicFixture("$a vs $b",date,"TBC","Boxing · World title $weight · broadcaster unverified"))
            }
        }
        if(successful==0) { if(cachedKey==key && cached.isNotEmpty()) return@withContext cached.filter { it.date in dates }; error("Sports feeds unavailable") }
        val merged=result.distinctBy { it.date+"|"+it.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"),"") }.sortedWith(compareBy({it.date},{it.time},{it.name}))
        cached=merged; cachedKey=key; cachedAt=now
        merged
    }
}
@Composable private fun PublicGuide(fixtures:List<PublicFixture>,status:String,onRefresh:()->Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val groups=remember(fixtures) { fixtures.groupBy { it.channel.substringBefore(" · ") } }
    val categories=listOf("Football","UFC","Boxing")
    Column(Modifier.fillMaxSize().padding(horizontal=8.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Sports Guide  |  $status",color=Color.White,fontSize=15.sp,modifier=Modifier.weight(1f))
            FocusAction("Refresh"){onRefresh()}
        }
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            categories.forEach { name ->
                var focused by remember { mutableStateOf(false) }
                val art=when(name){"Football"->R.drawable.sport_football;"UFC"->R.drawable.sport_ufc;else->R.drawable.sport_boxing}
                val events=groups[name].orEmpty()
                Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    ElectricFocus(Modifier.fillMaxWidth().height(85.dp).onFocusChanged{focused=it.isFocused}.clickable{expanded=if(expanded==name)null else name},focused,expanded==name,shape=RoundedCornerShape(0.dp),steady=true) {
                        Box(Modifier.fillMaxSize()) {
                            Image(painterResource(art),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xDD061125)))))
                            Text(name,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomStart).padding(10.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).clip(SharkFrameShape).background(Brush.verticalGradient(listOf(Color(0xF01D1B37),Color(0xF0091026)))).glassFrame(2.dp, Color.Cyan).padding(10.dp).verticalScroll(rememberScrollState())) {
                        Text("Upcoming $name",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Medium)
                        if(events.isEmpty()) Text("No events returned · coverage may be incomplete",color = Color.White,fontSize=12.sp)
                        events.take(if(expanded==name) 40 else 3).forEach { event ->
                            HorizontalDivider(color=Color(0xFF24476A))
                            Text(event.name,color=Color.White,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(fixtureDisplayTime(event),color = Color.White,fontSize=11.sp)
                            Text(event.channel.substringAfter(" · "),color = Color.White,fontSize=10.sp,maxLines=2)
                        }
                    }
                }
            }
        }
    }
}

private fun guideClock(context:android.content.Context,time:Long):String = SimpleDateFormat(
    if(playbackPrefs(context).getString("clockFormat","24-hour")=="12-hour") "h:mm a" else "HH:mm",Locale.UK
).apply { timeZone=java.util.TimeZone.getTimeZone("Europe/London") }.format(Date(time))

@Composable private fun BroadcastGuide(channels:List<Entry>,api:XtreamApi?,onSelect:(Entry)->Unit) {
    val context=LocalContext.current
    val uniqueChannels = remember(channels) { channels.distinctBy { it.id } }
    var focusedEntry by remember(channels) { mutableStateOf(uniqueChannels.firstOrNull()) }
    var programmes by remember(api) { mutableStateOf<Map<String,List<Program>>>(emptyMap()) }
    val cache=remember(api) { object:LinkedHashMap<String,Pair<Long,List<Program>>>(128,0.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<Long,List<Program>>>?):Boolean = size>128
    } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val first by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visible=remember(uniqueChannels,first) { uniqueChannels.drop(first).take(12) }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(60000) } }
    LaunchedEffect(api,first,uniqueChannels,refresh) {
        loading=true
        programmes=programmes.filterKeys { id -> visible.any { it.id==id } }
        try {
            for(group in visible.chunked(3)) {
                val results=coroutineScope { group.map { channel -> async {
                    val old=cache[channel.id]
                    val data=if(old!=null && System.currentTimeMillis()-old.first<300000) old.second else try {
                        api?.epg(channel.id).orEmpty().also { cache[channel.id]=System.currentTimeMillis() to it }
                    } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; old?.second.orEmpty() }
                    channel.id to data
                } }.awaitAll() }
                programmes=programmes+results
            }
        } finally { loading=false }
    }
    val slot=1800000L
    val start=(now/slot)*slot
    val end=start+4*slot
    Column(Modifier.fillMaxSize().hudSurface().padding(8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("TV Guide  •  UK time",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            Text(if(loading) "Loading…" else "${(first+1).coerceAtMost(uniqueChannels.size)} / ${uniqueChannels.size}",color = Color.White,fontSize=12.sp)
        }
        Text(focusedEntry?.title.orEmpty(),color=Color.White,fontSize=15.sp,maxLines=1)
        Row(Modifier.fillMaxWidth().hudSurface(highlighted=true).padding(4.dp)) {
            Text("Channels",color=Color.White,modifier=Modifier.width(150.dp),fontSize=12.sp)
            repeat(4) { i -> Text(guideClock(context,start+i*slot),color = Color.White,fontSize=12.sp,modifier=Modifier.weight(1f)) }
        }
        LazyColumn(state=listState,modifier=Modifier.weight(1f).clipToBounds(),verticalArrangement=Arrangement.spacedBy(3.dp),contentPadding=PaddingValues(bottom=3.dp)) { items(uniqueChannels,key={it.id}) { channel ->
            var focused by remember(channel.id){mutableStateOf(false)}
            Row(Modifier.fillMaxWidth().height(34.dp).onFocusChanged { focused=it.isFocused; if(focused)focusedEntry=channel }.hudSurface().glassFrame(2.dp,if(focused)Color.White else HudAccent, focused = focused).clickable { onSelect(channel) },verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.width(150.dp).padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    GlassChannelWord()
                    Text(channel.title,color=Color.White,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=4.dp))
                }
                val listing=programmes[channel.id].orEmpty().mapNotNull { p ->
                    val a=parseEpgTime(p.start); val b=parseEpgTime(p.end)
                    if(a!=null && b!=null && b>start && a<end && b>a) Triple(p,a,b) else null
                }.sortedBy { it.second }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val w=maxWidth
                    if(listing.isEmpty()) Text(if(loading && channel.id !in programmes) "Loading…" else "No listings in this window",color = Color.White,fontSize=11.sp,modifier=Modifier.padding(5.dp))
                    listing.forEach { (p,a,b) ->
                        val left=w*((a.coerceAtLeast(start)-start).toFloat()/(end-start))
                        val width=w*((b.coerceAtMost(end)-a.coerceAtLeast(start)).toFloat()/(end-start))
                        Box(Modifier.offset(x=left).width(width).fillMaxHeight().hudSurface(now>=a && now<b).glassFrame(0.5.dp, HudAccent).padding(3.dp)) {
                            Column { Text(p.title,color=Color.White,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis); if(now>=a && now<b) LinearProgressIndicator(progress={((now-a).toFloat()/(b-a)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(2.dp),color=Color(0xFF67DCFF)) }
                        }
                    }
                }
            }
        }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FocusAction("Refresh guide"){cache.clear();refresh++}
        }
    }
}

@Composable private fun ElectricCaption(value:String) {
    Text(value,color = Color.White,fontSize=11.sp,fontWeight=FontWeight.Normal,maxLines=2,overflow=TextOverflow.Ellipsis)
}
@Composable private fun HomeHeroBackground(artwork:String, subdued:Boolean=false) {
    val base = HudBackground
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().hudBackdrop()) {
        Crossfade(targetState=artwork, animationSpec=tween(120), label="homeHero") { url ->
            Box(Modifier.fillMaxSize()) {
                if(url.isNotBlank()) {
                    AsyncImage(model=remember(url,context) { coil.request.ImageRequest.Builder(context).data(url).size(coil.size.Size.ORIGINAL).build() },
                        contentDescription=null, modifier=Modifier.align(Alignment.TopEnd).fillMaxWidth(0.88f).fillMaxHeight(0.85f).graphicsLayer { alpha=if(subdued) 0.22f else 1f },
                        contentScale=ContentScale.Crop, alignment=Alignment.TopEnd)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to base, 0.28f to base, 0.48f to base.copy(alpha=0.85f), 0.76f to base.copy(alpha=0.40f), 1f to base.copy(alpha=0.24f))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to base.copy(alpha=0.24f), 0.30f to base.copy(alpha=0.08f), 0.58f to base.copy(alpha=0.55f), 0.83f to base, 1f to base)))
                }
            }
        }
    }
}

@Composable private fun CinemaBackground(artwork:String) {
    Box(Modifier.fillMaxSize().hudBackdrop()) {
        if(artwork.isNotBlank()) {
            AsyncImage(model=artwork,contentDescription=null,modifier=Modifier.fillMaxWidth().fillMaxHeight(0.72f).graphicsLayer { alpha=0.48f },contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HudBackground.copy(alpha=.2f),HudBackground.copy(alpha=.48f),HudBackground,HudBackground))))
        }
    }
}

private fun formatBytes(n:Long):String = if(n>=1048576) String.format(Locale.UK,"%.2f MB",n/1048576.0) else if(n>=1024) String.format(Locale.UK,"%.2f KB",n/1024.0) else "$n B"
private suspend fun connectionReport(context:android.content.Context, api:XtreamApi?):String {
    val cm=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps=cm.getNetworkCapabilities(cm.activeNetwork)
    val internet=caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
    val type=when { caps==null -> "None"; caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"; caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"; else -> "Other" }
    val serverStatus=if(api==null) "Not configured" else try { val start=android.os.SystemClock.elapsedRealtime(); val ok=api.authenticate(); if(ok) "Connected (${android.os.SystemClock.elapsedRealtime()-start} ms response)" else "Login rejected" } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; "Unreachable" }
    return "Internet: ${if(internet) "Connected" else "Not validated"}\nNetwork: $type\nIPTV server: $serverStatus\nEstimated link bandwidth: ${caps?.linkDownstreamBandwidthKbps ?: 0} kbps (not a speed test)"
}
@Composable private fun PremiumDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,text:(@Composable ()->Unit)?=null,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null) {
    val shape=SharkFrameShape
    Dialog(onDismissRequest=onDismissRequest,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=600.dp).fillMaxWidth(0.85f).shadow(16.dp,shape).hudSurface().glassFrame(2.dp, Color.Cyan).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(color = Color.White,fontSize=23.sp,fontWeight=FontWeight.Medium)) { title() }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFF55DFFF),Color.Transparent))))
            if(text!=null) CompositionLocalProvider(LocalTextStyle provides TextStyle(color = Color.White,fontSize=15.sp)) { text() }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) { dismissButton?.invoke(); Spacer(Modifier.width(8.dp)); confirmButton() }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val KeepVisibleFocus = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0f -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun LiveBrowser(categories:List<Pair<String,String>>, categoryId:String?, channels:List<Entry>, api:XtreamApi?, lastId:String, loading:Boolean, previewEnabled:Boolean, onCategory:(String)->Unit, onRetry:()->Unit, onPlay:(Entry)->Unit) {
    val context=LocalContext.current
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf(false) }
    val filtered=remember(channels,query){channels.distinctBy { it.id }.filter { it.title.contains(query,true) }}
    var current by remember(categoryId,channels){mutableStateOf(channels.firstOrNull { it.id==lastId } ?: channels.firstOrNull())}
    val preview by produceState(current, current) {
        delay(400)
        value = current
    }
    var listings by remember(api, current) { mutableStateOf<List<Program>>(emptyList()) }
    var favouriteIds by remember { mutableStateOf(context.getSharedPreferences("live_browser",0).getStringSet("favourites",emptySet()).orEmpty().toSet()) }
    val channelState=androidx.compose.foundation.lazy.rememberLazyListState()
    val categoryState=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(categoryId, channels.isNotEmpty()) { if(channels.isNotEmpty()) { val i=filtered.indexOfFirst { it.id==lastId }; channelState.scrollToItem(i.coerceAtLeast(0)) } }
    LaunchedEffect(categories) { val i=categories.indexOfFirst { it.first==categoryId }; if(i>=0 && categories.isNotEmpty())categoryState.scrollToItem(i) }
    LaunchedEffect(current,api) {
        val entry=current ?: return@LaunchedEffect
        delay(300)
        try {
            listings = api?.epg(entry.id).orEmpty()
        } catch (e: Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
        }
    }
    CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides KeepVisibleFocus) {
    Column(Modifier.fillMaxSize().clipToBounds().background(Color(0xF507152E),RoundedCornerShape(0.dp)).padding(horizontal=8.dp,vertical=3.dp)) {
        if(search) OutlinedTextField(query,{query=it},singleLine=true,label={Text("Find channel")},colors=OutlinedTextFieldDefaults.colors(focusedLabelColor=Color.White),modifier=Modifier.fillMaxWidth())
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(0.27f).fillMaxHeight()) {
                Text("Categories",color = Color.White,fontSize=13.sp,modifier=Modifier.height(22.dp))
                LazyColumn(state=categoryState,modifier=Modifier.weight(1f).clipToBounds(),verticalArrangement=Arrangement.spacedBy(3.dp),contentPadding=PaddingValues(bottom=3.dp)) {
                    items(categories.distinctBy { it.first },key={it.first}) { cat ->
                        BrowserRow(cat.second,cat.first==categoryId,"",{}, { query="";onCategory(cat.first) })
                    }
                }
            }
            Column(Modifier.weight(0.33f).fillMaxHeight()) {
                Text(if(loading) "Channels · Loading…" else "Channels · ${filtered.size}",color = Color.White,fontSize=13.sp,modifier=Modifier.height(22.dp))
                LazyColumn(state=channelState,modifier=Modifier.weight(1f).clipToBounds(),verticalArrangement=Arrangement.spacedBy(3.dp),contentPadding=PaddingValues(bottom=3.dp)) {
                    items(filtered,key={it.id}) { channel ->
                        BrowserRow((if(channel.id in favouriteIds) "★ " else "")+channel.title,current?.id==channel.id,"sky",{current=channel},{onPlay(channel)})
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(0.40f).fillMaxHeight()) {
                val previewHeight = minOf(maxWidth * (9f / 16f), maxHeight * .46f)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(previewHeight).background(Color.Black).glassFrame(2.dp,Color(0xFF48CFFF)).padding(3.dp),contentAlignment=Alignment.Center) {
                        val entry=preview
                        if(previewEnabled && entry!=null && api!=null) {
                            key(entry.id) { LivePreview(api.url(entry, playbackPrefs(context).getString("streamFormat", "Auto"))) }
                        } else Text("Press OK to play a channel",color=Color.White,fontSize=12.sp)
                    }
                    Text(current?.title.orEmpty(),modifier=Modifier.heightIn(min=22.dp),color=Color.White,fontSize=15.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    val now=System.currentTimeMillis()
                    val upcoming=listings.filter { (parseEpgTime(it.end) ?: Long.MAX_VALUE)>now }.sortedBy { parseEpgTime(it.start) ?: Long.MAX_VALUE }.take(8)
                    Text(if(upcoming.isEmpty()) "No programme information" else "Now / next · UK time",color = Color.White,fontSize=12.sp)
                    upcoming.take(2).forEach { programme -> Text("${parseEpgTime(programme.start)?.let { guideClock(context,it) }.orEmpty()}  ${programme.title}",color=Color.White,fontSize=12.sp,maxLines=2) }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        CompactLiveAction("Full screen",Modifier.weight(1f)){current?.let(onPlay)}
                        CompactLiveAction(if(current?.id in favouriteIds) "★ Saved" else "Favourite",Modifier.weight(1f)) {current?.let { e -> favouriteIds=if(e.id in favouriteIds) favouriteIds-e.id else favouriteIds+e.id;context.getSharedPreferences("live_browser",0).edit().putStringSet("favourites",favouriteIds).apply() }}
                        CompactLiveAction(if(search) "Close" else "Search",Modifier.weight(1f)){search=!search;if(!search)query=""}
                        CompactLiveAction("Retry",Modifier.weight(1f)){onRetry()}
                    }
                    upcoming.drop(2).forEach { programme -> Text("${parseEpgTime(programme.start)?.let { guideClock(context,it) }.orEmpty()}  ${programme.title}",color=Color.White,fontSize=12.sp,maxLines=2) }

                }
            }
        }
    }
}
}
@Composable private fun CompactLiveAction(label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    PremiumButton(onClick=onClick,modifier=modifier.height(32.dp),compact=true) {
        Text(label,color=Color.White,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun GlassChannelWord() {
    MaterialText("sky", modifier=Modifier.width(34.dp).padding(end=4.dp), style=TextStyle(fontSize=19.sp,fontWeight=FontWeight.Medium, shadow=Shadow(Color(0xFF71D7FF),Offset.Zero,8f), brush=Brush.horizontalGradient(listOf(Color(0xFFFFA54D),Color(0xFFFF609D),Color(0xFF8E86FF),Color(0xFF6DDCFF)))))
}
@Composable private fun BrowserRow(label:String,active:Boolean,image:String,onFocused:()->Unit,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(0.dp)
    Row(Modifier.fillMaxWidth().height(34.dp).onFocusChanged { focused=it.isFocused;if(focused)onFocused() }.hudSurface(active).glassFrame(2.5.dp,if(focused)SharkFocusGold else Color(0xFF304564),shape, focused = focused, repeatPulse = true).clickable(onClick=onClick).padding(horizontal=9.dp),verticalAlignment=Alignment.CenterVertically) {
        if(image=="sky") GlassChannelWord()
        Text(label,color=Color.White,fontWeight=FontWeight.Normal,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun LivePreview(url:String) {
    val context=LocalContext.current
    var failed by remember(url){mutableStateOf(false)}
    val player=remember { configuredPlayer(context, preview = true).apply { volume=0f } }
    LaunchedEffect(player,url) { failed=false; player.setMediaItem(sharkMediaItem(context,url,inferFormat=true));player.prepare();player.playWhenReady=true }
    DisposableEffect(player,url) {
        var recoveryCount=0
        var lastRecovery=0L
        val handler = android.os.Handler(player.applicationLooper)
        val listener=object:androidx.media3.common.Player.Listener {
            override fun onPlayerError(error:androidx.media3.common.PlaybackException){
                val now=android.os.SystemClock.elapsedRealtime()
                if(now-lastRecovery>60000L) recoveryCount=0
                if(error.errorCode==androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && recoveryCount<3){
                    recoveryCount++;lastRecovery=now;failed=false
                    player.seekToDefaultPosition();player.prepare();player.playWhenReady=true
                }else failed=true
            }
        };player.addListener(listener);onDispose {player.removeListener(listener)}
    }
    PlayerLifecycle(player)
    DisposableEffect(player){onDispose{player.release()}}
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory={PlayerView(it).apply {useController=false;isFocusable=false;descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS;this.player=player}},modifier=Modifier.fillMaxSize(),update={it.player=player},onRelease={it.player=null})
        if(failed)Text("Preview unavailable — try full screen",color=Color.White,fontSize=12.sp,modifier=Modifier.align(Alignment.Center))
    }
}


