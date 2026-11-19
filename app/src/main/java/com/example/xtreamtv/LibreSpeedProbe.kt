package com.example.xtreamtv

import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object LibreSpeedProbe {
    private const val SAMPLE_BYTES = 4 * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /** One-button public download test; no custom LibreSpeed server required. */
    fun publicDownloadReport(): String {
        val url = "https://speed.cloudflare.com/__down?bytes=4000000".toHttpUrlOrNull()
            ?: throw IOException("Speed test endpoint unavailable")
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache").build()
        val started = SystemClock.elapsedRealtime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Speed test server returned ${response.code}")
            val body = response.body ?: throw IOException("Empty speed test response")
            val input = body.byteStream()
            val buffer = ByteArray(32 * 1024)
            var bytes = 0L
            while (bytes < 4_000_000L) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), 4_000_000L - bytes).toInt())
                if (count < 0) break
                bytes += count
            }
            if (bytes < 1_000_000L) throw IOException("Incomplete speed test sample")
            val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
            return String.format(Locale.UK, "%.1f Mbps download · Cloudflare", bytes * 8.0 / (elapsed * 1000.0))
        }
    }

    fun endpoint(text: String): HttpUrl? {
        val server = text.trim().toHttpUrlOrNull() ?: return null
        if (!server.isHttps || server.username.isNotEmpty() || server.password.isNotEmpty() ||
            server.query != null || server.fragment != null
        ) return null
        if (server.encodedPath.endsWith("/garbage.php")) return server
        return ("${server.toString().trimEnd('/')}/backend/garbage.php").toHttpUrlOrNull()
    }

    fun downloadReport(endpoint: HttpUrl): String {
        val url = endpoint.newBuilder().addQueryParameter("ckSize", "4").build()
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache").build()
        val started = SystemClock.elapsedRealtime()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Missing speed test response")
            if (!response.isSuccessful || body.contentType()?.let {
                    it.type == "application" && it.subtype == "octet-stream"
                } != true
            ) throw IOException("Invalid LibreSpeed response")
            val input = body.byteStream()
            val sample = ByteArray(32 * 1024)
            var bytes = 0
            while (bytes < SAMPLE_BYTES) {
                val count = input.read(sample, 0, minOf(sample.size, SAMPLE_BYTES - bytes))
                if (count < 0) break
                bytes += count
            }
            if (bytes < SAMPLE_BYTES) throw IOException("Incomplete LibreSpeed sample")
            val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
            return String.format(Locale.UK, "%.1f Mbps download · %s", bytes * 8.0 / (elapsed * 1000), url.host)
        }
    }
}
