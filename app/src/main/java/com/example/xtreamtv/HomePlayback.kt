package com.example.xtreamtv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.Locale

data class HomeStream(val title: String, val url: String)
object HomePlayback {
    private val catalogues = java.util.WeakHashMap<XtreamApi, MutableMap<Boolean, Pair<Long,List<Entry>>>>()
    private val cache = java.util.WeakHashMap<XtreamApi, MutableMap<String, Pair<Long,List<HomeStream>>>>()
    private val prefix = Regex("(?i)^[\\s|:–_-]*(?:(?:UK|US|EN|ENG|MULTI|NF|AMZN|4K|FHD|HD|UHD|VOD|MOVIES|SERIES)[\\s:|–_-]+)+")
    private val suffix = Regex("(?i)\\s*[\\[(]?(?:2160p|1080p|720p|4K|UHD|FHD|HD|HDR|HEVC|H265|MULTI|DUAL)[\\])]?[\\s]*$")
    private val yearSuffix = Regex("\\s*[\\[(](?:19|20)\\d{2}[\\])]\\s*$")
    private fun normalize(value:String):String {
        var title=value.trim().replace(Regex("^\\[[^]]{1,12}\\]\\s*"), "").replace(prefix, "")
        repeat(3) { title=title.replace(suffix, "").replace(yearSuffix, "").trim() }
        return java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }
    internal fun clearCaches() {
        synchronized(cache) { cache.clear() }
        synchronized(catalogues) { catalogues.clear() }
    }

    suspend fun lookupXtream(api:XtreamApi, entry:Entry, season: Int, episode: Int):List<HomeStream> = withContext(Dispatchers.IO) {
        val tv=entry.genre.equals("TV Series",true) || entry.kind=="series" || entry.kind=="tv"
        val target=normalize(entry.title)
        if(target.isBlank()) return@withContext emptyList()
        val cacheKey="${entry.genre}:$target:${entry.year}:$season:$episode"
        synchronized(cache) { cache[api]?.get(cacheKey) }?.let { if(android.os.SystemClock.elapsedRealtime()-it.first<300000) return@withContext it.second }
        val cached=synchronized(catalogues) { catalogues[api]?.get(tv) }
        val catalogue=if(cached!=null && android.os.SystemClock.elapsedRealtime()-cached.first<300000) cached.second else {
            val fresh=api.entries(if(tv) "get_series" else "get_vod_streams",if(tv) "series" else "movie",titleFilter={true})
            synchronized(catalogues) { catalogues.getOrPut(api){mutableMapOf()}[tv]=android.os.SystemClock.elapsedRealtime() to fresh }
            fresh
        }
        val candidates=catalogue.filter { item ->
            val name=item.title
            val year=yearSuffix.find(name.trim().replace(suffix,""))?.value?.filter { it.isDigit() }.orEmpty()
            normalize(name)==target && (year.isBlank() || entry.year.isBlank() || year==entry.year)
        }
        val result = if (tv) candidates.take(5).map { match ->
            async {
                try {
                    api.episodes(match.id).filter { it.title.substringBefore(" ·") == "S$season E$episode" }
                        .map { HomeStream("${match.title} · ${it.title}", api.url(it)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: java.io.IOException) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
        else candidates.map { HomeStream(it.title,api.url(it)) }
        if(result.isNotEmpty()) synchronized(cache) { val map=cache.getOrPut(api){ mutableMapOf() }; if(map.size>=100) map.clear(); map[cacheKey]=android.os.SystemClock.elapsedRealtime() to result }
        result
    }
}
