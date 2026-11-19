package com.example.xtreamtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Stremio-compatible addon resolver; deliberately accepts direct HTTP(S) media only. */
internal object Torrentio {
    suspend fun lookup(manifest: String, entry: Entry, tmdbKey: String, season: Int, episode: Int): List<HomeStream> = withContext(Dispatchers.IO) {
        val base = URL(manifest)
        require(base.protocol == "https" && base.host.equals("torrentio.strem.fun", true) && base.path.endsWith("/manifest.json")) { "Invalid Torrentio manifest" }
        val series = entry.genre.equals("TV Series", ignoreCase = true) || entry.kind.equals("series", ignoreCase = true)
        val imdb = entry.id.takeIf { it.matches(Regex("tt[0-9]+")) } ?: run {
            if (!entry.id.all(Char::isDigit) || tmdbKey.isBlank()) return@withContext emptyList()
            val token = tmdbKey.trim().removePrefix("Bearer ").trim()
            val query = if (token.length < 80) "?api_key=" + URLEncoder.encode(token,"UTF-8") else ""
            val conn = URL("https://api.themoviedb.org/3/${if(series) "tv" else "movie"}/${entry.id}/external_ids$query").openConnection() as HttpURLConnection
            conn.connectTimeout=10000;conn.readTimeout=10000
            if(token.length>=80) conn.setRequestProperty("Authorization","Bearer $token")
            try { if(conn.responseCode!=200) throw java.io.IOException("TMDB external ID lookup HTTP ${conn.responseCode}");JSONObject(conn.inputStream.bufferedReader().use{it.readText()}).optString("imdb_id") }
            finally { conn.disconnect() }
        }
        if(!imdb.matches(Regex("tt[0-9]+"))) return@withContext emptyList()
        val id = if(series) "$imdb:$season:$episode" else imdb
        val url = base.toString().removeSuffix("manifest.json")+"stream/${if(series) "series" else "movie"}/"+URLEncoder.encode(id,"UTF-8")+".json"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout=10000;conn.readTimeout=20000
        try {
            if(conn.responseCode!=200) throw java.io.IOException("Torrentio stream lookup HTTP ${conn.responseCode}")
            val streams = JSONObject(conn.inputStream.bufferedReader().use{it.readText().take(1_000_000)}).optJSONArray("streams") ?: return@withContext emptyList()
            (0 until minOf(streams.length(),100)).mapNotNull { i ->
                val item=streams.optJSONObject(i) ?: return@mapNotNull null
                val direct=item.optString("url")
                if(!direct.startsWith("https://") && !direct.startsWith("http://")) return@mapNotNull null
                HomeStream("Torrentio · "+listOf(item.optString("name"),item.optString("title")).filter{it.isNotBlank()}.joinToString(" · ").take(190),direct)
            }
        } finally { conn.disconnect() }
    }
}
