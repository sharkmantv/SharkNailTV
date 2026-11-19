package com.example.xtreamtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Stremio-compatible addon protocol client, based on the addon model
 * supported by Nuvio. Does not use Nuvio accounts or ship any addon credentials.
 * Only connect sources you are authorised to use.
 */
object NuvioCompatibleAddon {
    private val bitTorrentV1Hash = Regex("[0-9a-fA-F]{40}")

    internal data class TorrentSource(
        val title: String,
        val infoHash: String,
        val fileIndex: Int?,
        val trackers: List<String>
    )

    internal fun torrentSource(item: JSONObject, title: String): TorrentSource? {
        val rawHash = item.opt("infoHash") as? String ?: return null
        if (!bitTorrentV1Hash.matches(rawHash)) return null

        val rawIndex = item.opt("fileIdx")
        val fileIndex = if (rawIndex == null || rawIndex == JSONObject.NULL) {
            null
        } else {
            rawIndex.toString().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }

        val sources = item.optJSONArray("sources")
        val trackers = buildList {
            if (sources != null) {
                for (i in 0 until minOf(sources.length(), 24)) {
                    val source = sources.opt(i) as? String ?: continue
                    if (!source.startsWith("tracker:")) continue
                    val tracker = source.removePrefix("tracker:")
                    if (tracker.length !in 1..2048) continue
                    val uri = runCatching { java.net.URI(tracker) }.getOrNull() ?: continue
                    if (uri.host.isNullOrBlank() || uri.scheme?.lowercase(java.util.Locale.ROOT) !in
                        setOf("udp", "http", "https")
                    ) continue
                    add(tracker)
                }
            }
        }.distinct()
        return TorrentSource(title, rawHash.lowercase(java.util.Locale.ROOT), fileIndex, trackers)
    }

    suspend fun streamsFromAddons(
        addonUrls: List<String>,
        id: String,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): List<HomeStream> {
        return coroutineScope {
            val jobs = addonUrls.distinct().take(12).map { url ->
                async {
                    try {
                        withTimeout(9000L) {
                            streams(url, id, isSeries, season, episode)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        if (!currentCoroutineContext().isActive) throw e
                        emptyList<HomeStream>()
                    } catch (_: Exception) {
                        emptyList<HomeStream>()
                    }
                }
            }
            jobs.awaitAll().flatten().distinctBy { it.url }.take(80)
        }
    }

    suspend fun streams(baseUrl: String, id: String, isSeries: Boolean, season: Int, episode: Int): List<HomeStream> =
        streamItems(baseUrl, id, isSeries, season, episode) { item, title ->
            val url = item.optString("url")
            val parsed = android.net.Uri.parse(url)
            if (parsed.scheme?.lowercase() in listOf("https", "http", "rtsp") && !parsed.host.isNullOrBlank()) {
                HomeStream(title, url)
            } else {
                null
            }
        }

    internal suspend fun torrentStreams(
        baseUrl: String,
        id: String,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): List<TorrentSource> =
        streamItems(baseUrl, id, isSeries, season, episode, ::torrentSource)

    private suspend fun <T : Any> streamItems(
        baseUrl: String,
        id: String,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        parse: (JSONObject, String) -> T?
    ): List<T> =
        withContext(Dispatchers.IO) {
            val root = baseUrl.trim().removeSuffix("/manifest.json").trimEnd('/')
            require(root.startsWith("https://")) { "Addon URL must use HTTPS" }
            require(id.matches(Regex("[A-Za-z0-9:_-]{1,100}"))) { "Invalid media identifier" }
            val mediaId = if (isSeries) "$id:$season:$episode" else id
            val type = if (isSeries) "series" else "movie"
            val endpoint = "$root/stream/$type/${URLEncoder.encode(mediaId, "UTF-8")}.json"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = false
            try {
                if (conn.responseCode != 200) return@withContext emptyList()
                val json = conn.inputStream.bufferedReader().use { it.readText().take(1024 * 1024) }
                val items = JSONObject(json).optJSONArray("streams") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until minOf(items.length(), 40)) {
                        val item = items.optJSONObject(i) ?: continue
                        val title = item.optString("title").ifBlank {
                            item.optString("name").ifBlank { "Stream ${i + 1}" }
                        }
                        parse(item, title)?.let { add(it) }
                    }
                }
            } finally { conn.disconnect() }
        }
}
