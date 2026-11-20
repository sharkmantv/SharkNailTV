package com.example.xtreamtv
internal object SmartPlayback {
    private val codecNames = listOf(
        Regex("""(?i)\b(?:av1|av01)\b""") to "video/av01",
        Regex("""(?i)\b(?:hevc|h[. ]?265|x265)\b""") to "video/hevc",
        Regex("""(?i)\b(?:vp9|vp09)\b""") to "video/x-vnd.on2.vp9",
        Regex("""(?i)\b(?:avc|h[. ]?264|x264)\b""") to "video/avc"
    )
    private val hardwareDecoders by lazy {
        if (android.os.Build.VERSION.SDK_INT < 29) emptySet() else runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.isHardwareAccelerated }
                .flatMap { it.supportedTypes.asList() }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun codecScore(stream: HomeStream, supported: Set<String>): Int {
        if (supported.isEmpty()) return 1
        val mime = codecNames.firstOrNull { it.first.containsMatchIn(stream.title) }?.second
        return when (mime) {
            null -> 1
            in supported -> 2
            else -> 0
        }
    }

    fun preferHardwareDecoders(streams: List<HomeStream>): List<HomeStream> {
        val supported = hardwareDecoders
        if (supported.isEmpty()) return streams
        return streams.sortedByDescending { stream ->
            codecScore(stream, supported)
        }
    }

    val modes = listOf("Shark Turbo", "Shark Smart", "Shark Ultra 4K")
    fun height(title:String):Int = when {
        Regex("2160|4k|uhd", RegexOption.IGNORE_CASE).containsMatchIn(title) -> 2160
        Regex("1080|fhd", RegexOption.IGNORE_CASE).containsMatchIn(title) -> 1080
        title.contains("720") -> 720
        title.contains("480") -> 480
        else -> 0
    }
    /**
     * Prefer the highest advertised resolution likely to play smoothly.
     * ConnectivityManager reports a network link estimate, NOT measured internet
     * throughput; never treat it as a guaranteed source-server bitrate.
     * Unknown-resolution links are retained for fallback after labelled sources.
     */
    fun rank(streams:List<HomeStream>, mode:String, kbps:Int, screen:Int, displayPreference:String = "Auto"):List<HomeStream> {
        val displayHeight=screen.coerceAtLeast(480)
        val cap = when {
            mode == "Shark Turbo" -> 1080
            mode == "Shark Ultra 4K" -> 2160
            // Very weak link estimates are a useful warning, but Wi-Fi link
            // capacity alone cannot prove that a 4K source will be reliable.
            kbps in 1..5999 -> 720
            else -> 2160
        }.coerceAtMost(displayHeight)
        // Favor direct streams and compatible hardware codecs before resolution.
        val supported = hardwareDecoders
        return streams.distinctBy { it.url }.sortedWith(
            compareByDescending<HomeStream> { stream ->
                stream.url.startsWith("https://", true) || stream.url.startsWith("http://", true)
            }.thenByDescending { stream ->
                val hdr = Regex("(?i)\\b(?:HDR10\\+?|HDR|HLG|Dolby[ ._-]?Vision|DV|DOVI)\\b").containsMatchIn(stream.title)
                val sdr = Regex("(?i)\\bSDR\\b").containsMatchIn(stream.title)
                when(displayPreference) {
                    "Prefer SDR" -> if(hdr) 0 else if(sdr) 2 else 1
                    "Prefer HDR" -> if(hdr) 2 else if(sdr) 0 else 1
                    else -> 1
                }
            }.thenByDescending { stream ->
                codecScore(stream, supported)
            }.thenByDescending { stream ->
                val h=height(stream.title)
                when {
                    h == 0 -> 0
                    h <= cap -> h
                    else -> -h
                }
            }.thenByDescending { stream ->
                // An unlabelled stream should not outrank a known 1080p/4K source.
                // Keep source order when metadata cannot distinguish two links.
                height(stream.title) > 0
            }
        )
    }
}
