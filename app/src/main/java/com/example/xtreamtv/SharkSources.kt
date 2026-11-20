package com.example.xtreamtv

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Built-in, opt-in multi-addon discovery. Only configured HTTPS Stremio
 * manifests are queried. No third-party provider or credential is shipped.
 */
internal object SharkSources {
    suspend fun lookup(
        penguManifest: String,
        additionalManifests: String,
        entry: Entry,
        tmdbKey: String,
        season: Int,
        episode: Int
    ): List<HomeStream> = coroutineScope {
        val extras = additionalManifests.lines()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(6)
        val sources = (listOf(penguManifest.takeIf { it.isNotBlank() }?.let { it to false })
            + extras.map { it to true }).filterNotNull()
        sources.map { (manifest, extra) ->
            async {
                try {
                    PenguPlay.lookup(manifest, entry, tmdbKey, season, episode, configuredAddon = extra)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }
}
