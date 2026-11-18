package com.example.xtreamtv

/**
 * Independent Home playback integration contract.
 * TMDB supplies metadata, not stream URLs. Implementations must use a licensed
 * streaming catalogue; do not connect Xtream Codes or undocumented private APIs.
 */
data class HomeMediaRequest(
    val tmdbId: Int?,
    val title: String,
    val year: String?,
    val mediaType: HomeMediaType,
    val season: Int? = null,
    val episode: Int? = null
)

enum class HomeMediaType { MOVIE, SERIES }

data class DirectPlayable(
    val uri: String,
    val mimeType: String? = null,
    val displayName: String,
    val providerName: String
)

sealed interface DirectPlaybackResult {
    data class Available(val streams: List<DirectPlayable>) : DirectPlaybackResult
    data class Unavailable(val reason: String) : DirectPlaybackResult
}

interface HomeDirectPlaybackProvider {
    val providerName: String
    suspend fun resolve(request: HomeMediaRequest): DirectPlaybackResult
}

/** No fictitious stream URLs or nonworking source selectors. */
object UnconfiguredHomeProvider : HomeDirectPlaybackProvider {
    override val providerName: String = "Not configured"
    override suspend fun resolve(request: HomeMediaRequest): DirectPlaybackResult =
        DirectPlaybackResult.Unavailable("Connect an authorised movie and series provider.")
}

/** Reject non-network, empty and malformed URLs before handing streams to Media3. */
fun DirectPlayable.isSupportedNetworkStream(): Boolean = runCatching {
    val parsed = android.net.Uri.parse(uri)
    parsed.scheme?.lowercase() in setOf("https", "http", "rtsp") && !parsed.host.isNullOrBlank()
}.getOrDefault(false)
