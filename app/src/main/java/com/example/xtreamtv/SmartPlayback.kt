package com.example.xtreamtv
internal object SmartPlayback {
    val modes = listOf("Shark Turbo", "Shark Smart", "Shark Ultra 4K")
    fun height(title:String):Int = when {
        Regex("2160|4k|uhd", RegexOption.IGNORE_CASE).containsMatchIn(title) -> 2160
        Regex("1080|fhd", RegexOption.IGNORE_CASE).containsMatchIn(title) -> 1080
        title.contains("720") -> 720
        title.contains("480") -> 480
        else -> 0
    }
    fun rank(streams:List<HomeStream>, mode:String, kbps:Int, screen:Int):List<HomeStream> {
        val budget = (when {
            mode == "Shark Ultra 4K" -> 2160
            kbps in 1..7999 -> 720
            mode == "Shark Turbo" -> 1080
            kbps >= 35000 -> 2160
            else -> 1080
        }).coerceAtMost(screen.coerceAtLeast(480))
        return streams.distinctBy { it.url }.sortedByDescending { val h=height(it.title); if(h>budget) -h else h }
    }
}
