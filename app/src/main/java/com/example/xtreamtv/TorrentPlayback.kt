package com.example.xtreamtv

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Native libtorrent4j playback preparation. Downloads the selected video completely
 * before handing its local URI to Media3; this is not progressive torrent streaming.
 * Only use with media you are authorized to download.
 */
internal object TorrentPlayback {
    private val session = SessionManager()
    private var started = false
    private val videoExtensions = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts")

    suspend fun prepare(context: Context, magnet: String, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            require(magnet.startsWith("magnet:?xt=urn:btih:", true)) { "Invalid magnet URI" }
            synchronized(session) {
                if (!started) { session.start(); started = true }
            }
            onProgress("Connecting to torrent peers and retrieving metadata…")
            val root = File(context.filesDir, "torrent-video").apply { mkdirs() }
            val data = session.fetchMagnet(magnet, 60, context.cacheDir)
                ?: error("No torrent metadata received within 60 seconds")
            val info = TorrentInfo(data)
            val files = info.files()
            val chosen = (0 until files.numFiles()).filter { index ->
                files.filePath(index).substringAfterLast('.').lowercase() in videoExtensions
            }.maxByOrNull { files.fileSize(it) } ?: error("No playable video in torrent")
            val relative = files.filePath(chosen)
            val target = File(root, relative).canonicalFile
            require(target.path.startsWith(root.canonicalPath + File.separator)) { "Unsafe torrent file path" }
            val expectedSize = files.fileSize(chosen)
            require(expectedSize > 0 && expectedSize < 100L * 1024 * 1024 * 1024) { "Unsupported video size" }
            val priorities = Array(files.numFiles()) { index ->
                if (index == chosen) Priority.NORMAL else Priority.IGNORE
            }
            session.download(info, root, null, priorities, null, torrent_flags_t())
            // File length alone is not sufficient: wait for libtorrent to report all
            // selected bytes downloaded and verified.
            val deadline = System.currentTimeMillis() + 4L * 60 * 60 * 1000
            while (System.currentTimeMillis() < deadline) {
                val handle = session.find(info.infoHash())
                if (handle != null && handle.isValid) {
                    val status = handle.status()
                    val percentage = (status.progress() * 100).toInt().coerceIn(0, 100)
                    onProgress("Downloading video from peers: $percentage%")
                    if (status.isFinished && target.isFile && target.length() == expectedSize) {
                        return@withContext Uri.fromFile(target).toString()
                    }
                }
                delay(1500)
            }
            error("Torrent download timed out")
        }
}
