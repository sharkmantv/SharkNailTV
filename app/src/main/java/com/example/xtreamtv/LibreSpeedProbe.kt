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

    /**
     * M-Lab NDT7: open-source, sustained single-connection download measurement.
     * Uses the official Locate API for a healthy server and its access token.
     * No upload test is performed.
     */
    fun publicDownloadReport(): String {
        return try {
            ndtDownloadReport()
        } catch (e: Exception) {
            if (e is InterruptedException || e is java.util.concurrent.CancellationException) throw e
            httpsDownloadReport()
        }
    }

    private fun httpsDownloadReport(): String {
        val request = Request.Builder()
            .url("https://speed.cloudflare.com/__down?bytes=25000000")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .build()
        val fallback = client.newBuilder().followRedirects(true)
            .callTimeout(25, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
        fallback.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTPS test server returned ${response.code}")
            val body = response.body ?: throw IOException("Empty HTTPS test response")
            val buffer = ByteArray(64 * 1024)
            val input = body.byteStream()
            val start = SystemClock.elapsedRealtime()
            var bytes = 0L
            while (bytes < 25_000_000L && SystemClock.elapsedRealtime() - start < 8000L) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
            }
            if (bytes < 256_000L) throw IOException("Insufficient HTTPS download data")
            val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
            return String.format(Locale.UK, "%.1f Mbps download · Cloudflare HTTPS (%.1f s)",
                bytes * 8.0 / (elapsed * 1000.0), elapsed / 1000.0)
        }
    }

    private fun ndtDownloadReport(): String {
        val locate = Request.Builder()
            .url("https://locate.measurementlab.net/v2/nearest/ndt/ndt7")
            .header("User-Agent", "SharkNailTV-NDT7/1.0")
            .build()
        val endpoint = client.newCall(locate).execute().use { response ->
            if (!response.isSuccessful) throw IOException("M-Lab server discovery failed")
            val json = org.json.JSONObject(response.body?.string() ?: throw IOException("No server response"))
            val results = json.optJSONArray("results") ?: throw IOException("No M-Lab servers available")
            var found: String? = null
            for (i in 0 until results.length()) {
                val candidate = results.optJSONObject(i)?.optJSONObject("urls")
                    ?.optString("wss:///ndt/v7/download", "") ?: ""
                if (candidate.startsWith("wss://") && candidate.contains("/ndt/v7/download")) {
                    found = candidate
                    break
                }
            }
            found ?: throw IOException("No secure NDT7 server available")
        }
        val received = java.util.concurrent.atomic.AtomicLong(0)
        val done = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val started = java.util.concurrent.atomic.AtomicLong(0)
        val finished = java.util.concurrent.atomic.AtomicLong(0)
        val request = Request.Builder().url(endpoint)
            .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
            .header("User-Agent", "SharkNailTV-NDT7/1.0")
            .build()
        val socket = client.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
            .newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    started.set(SystemClock.elapsedRealtime())
                }
                override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                    received.addAndGet(bytes.size.toLong())
                }
                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    // NDT7 sends JSON measurement messages as well as binary data.
                }
                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    finished.set(SystemClock.elapsedRealtime())
                    webSocket.close(1000, null)
                    done.countDown()
                }
                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    finished.set(SystemClock.elapsedRealtime())
                    done.countDown()
                }
                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    failure.set(t.message ?: "Connection failed")
                    finished.set(SystemClock.elapsedRealtime())
                    done.countDown()
                }
            })
        try {
            if (!done.await(14, TimeUnit.SECONDS)) {
                if (received.get() < 256_000L) throw IOException("M-Lab speed test timed out")
                finished.set(SystemClock.elapsedRealtime())
            }
            if (received.get() < 256_000L) throw IOException(failure.get() ?: "Insufficient download data")
            val elapsed = (finished.get() - started.get()).coerceAtLeast(1L)
            return String.format(Locale.UK,
                "%.1f Mbps download · M-Lab NDT7 (%.1f s, single connection)",
                received.get() * 8.0 / (elapsed * 1000.0), elapsed / 1000.0)
        } finally {
            socket.cancel()
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
