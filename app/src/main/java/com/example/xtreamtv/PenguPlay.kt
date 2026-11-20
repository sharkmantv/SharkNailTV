package com.example.xtreamtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Stremio addon stream resolver. The configured manifest is kept out of shipped source. */
internal object PenguPlay {
    // Avoid re-resolving the same title when the viewer backs out and presses Play.
    // Keep signed stream URLs in memory only and expire them quickly.
    private data class CachedStreams(val savedAtMs: Long, val streams: List<HomeStream>)
    private val cache = object : LinkedHashMap<String, CachedStreams>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreams>?): Boolean = size > 32
    }
    private const val CACHE_TTL_MS = 120_000L
    suspend fun lookup(manifest: String, entry: Entry, tmdbKey: String, season: Int = 1, episode: Int = 1, configuredAddon: Boolean = false): List<HomeStream> = withContext(Dispatchers.IO) {
        val base = URL(manifest)
        require(base.protocol == "https" && (configuredAddon || base.host.equals("pengu.uk", ignoreCase = true))) { "Invalid addon manifest" }
        require(base.userInfo == null && base.port in listOf(-1, 443) && base.query == null && base.ref == null) { "Unsupported manifest URL" }
        val host = base.host.lowercase()
        require(host.isNotBlank() && host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.matches(Regex("""\\d+\\.\\d+\\.\\d+\\.\\d+""")) &&
            !host.contains(':')) { "Private or numeric addon hosts are not supported" }
        require(base.path.endsWith("/manifest.json")) { "Expected Stremio manifest URL" }
        val isSeries = entry.genre == "TV Series"
        val imdbId = entry.id.takeIf { it.startsWith("tt") } ?: run {
            if (!entry.id.all(Char::isDigit) || tmdbKey.isBlank()) return@withContext emptyList()
            val token = tmdbKey.trim().removePrefix("Bearer ").trim()
            val authQuery = if (token.length < 80) "?api_key=" + URLEncoder.encode(token,"UTF-8") else ""
            val tmdb = URL("https://api.themoviedb.org/3/${if(isSeries) "tv" else "movie"}/${entry.id}/external_ids$authQuery").openConnection() as HttpURLConnection
            tmdb.connectTimeout = 10000; tmdb.readTimeout = 10000
            if (token.length >= 80) tmdb.setRequestProperty("Authorization", "Bearer $token")
            try {
                if (tmdb.responseCode != 200) return@withContext emptyList()
                JSONObject(tmdb.inputStream.bufferedReader().use { it.readText() }).optString("imdb_id")
            } finally { tmdb.disconnect() }
        }
        if (!imdbId.startsWith("tt")) return@withContext emptyList()
        val path = base.toString().removeSuffix("manifest.json") + "stream/${if(isSeries) "series" else "movie"}/" + URLEncoder.encode(if(isSeries) "$imdbId:$season:$episode" else imdbId,"UTF-8") + ".json"
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[path]?.takeIf { now - it.savedAtMs in 0 until CACHE_TTL_MS }?.let { return@withContext it.streams }
        }
        val conn = URL(path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 20000
        try {
            require(conn.responseCode == 200) { "Stream HTTP ${conn.responseCode}" }
            val payload = conn.inputStream.bufferedReader().use { it.readText().take(1_000_000) }
            val arr = JSONObject(payload).optJSONArray("streams") ?: return@withContext emptyList()
            val streams = (0 until minOf(arr.length(), 80)).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val link = item.optString("url")
                if (!link.startsWith("https://") && !link.startsWith("http://")) return@mapNotNull null
                HomeStream(listOf(item.optString("name"),item.optString("title")).filter { it.isNotBlank() }.joinToString(" · ").take(220), link)
            }
            synchronized(cache) { cache[path] = CachedStreams(System.currentTimeMillis(), streams) }
            streams
        } finally { conn.disconnect() }
    }
}
