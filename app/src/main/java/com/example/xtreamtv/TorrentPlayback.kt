package com.example.xtreamtv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Progressive torrent playback for media the user is authorized to access.
 * A loopback HTTP range server exposes ONLY verified torrent pieces to Media3.
 * A sparse file's apparent length is never used as proof that bytes are available.
 */
internal object TorrentPlayback {
    private val session = SessionManager()
    private var started = false
    private val videoExtensions = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts")
    @Volatile private var activeServer: ProgressiveServer? = null

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
            val target = File(root, files.filePath(chosen)).canonicalFile
            require(target.path.startsWith(root.canonicalPath + File.separator)) { "Unsafe torrent file path" }
            val expectedSize = files.fileSize(chosen)
            require(expectedSize > 0 && expectedSize < 100L * 1024 * 1024 * 1024) { "Unsupported video size" }
            val priorities = Array(files.numFiles()) { index ->
                if (index == chosen) Priority.DEFAULT else Priority.IGNORE
            }
            session.download(info, root, null, priorities, null, torrent_flags_t())
            val deadline = System.currentTimeMillis() + 120_000L
            var handle: TorrentHandle? = null
            while (System.currentTimeMillis() < deadline) {
                val candidate = session.find(info.infoHash())
                if (candidate != null && candidate.isValid) {
                    handle = candidate
                    break
                }
                delay(250)
            }
            val torrent = handle ?: error("Torrent handle unavailable")
            // This libtorrent4j TorrentHandle version does not expose setSequentialDownload.
            // Rely on verified-piece checks for safe progressive playback.
            val offset = files.fileOffset(chosen)
            val pieceSize = info.pieceLength().toLong()
            require(pieceSize > 0L) { "Invalid torrent piece size" }
            val firstPiece = (offset / pieceSize).toInt()
            val lastPiece = ((offset + expectedSize - 1) / pieceSize).toInt()
            prioritizeWindow(torrent, firstPiece, lastPiece)
            onProgress("Buffering video from torrent peers…")
            val startupDeadline = System.currentTimeMillis() + 120_000L
            // The range server waits for verified pieces as Media3 requests them.
            // Requiring four complete pieces before opening the player causes an
            // unnecessary download screen, especially on slow torrents.
            // One verified initial piece is sufficient to hand off to Media3.
            while (System.currentTimeMillis() < startupDeadline) {
                if (torrent.havePiece(firstPiece) && target.isFile) break
                delay(250)
            }
            if (!torrent.havePiece(firstPiece) || !target.isFile) {
                error("Torrent could not buffer the first video piece within 2 minutes. Try a direct stream or a torrent with more peers.")
            }
            activeServer?.close()
            val server = ProgressiveServer(target, torrent, offset, expectedSize, pieceSize)
            activeServer = server
            server.url
        }

    private fun prioritizeWindow(torrent: TorrentHandle, first: Int, last: Int) {
        if (!torrent.isValid) return
        for (piece in first..minOf(last, first + 15)) {
            if (!torrent.havePiece(piece)) torrent.setPieceDeadline(piece, (piece - first + 1) * 1000)
        }
    }

    private class ProgressiveServer(
        private val file: File,
        private val torrent: TorrentHandle,
        private val fileOffset: Long,
        private val size: Long,
        private val pieceSize: Long
    ) {
        private val running = AtomicBoolean(true)
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url: String = "http://127.0.0.1:${socket.localPort}/video"
        init {
            thread(name = "torrent-http-accept", isDaemon = true) {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        thread(name = "torrent-http-client", isDaemon = true) { serve(client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
        }
        fun close() {
            running.set(false)
            try { socket.close() } catch (_: Exception) {}
        }
        private fun serve(client: Socket) {
            client.use { peer ->
                try {
                    peer.soTimeout = 15_000
                    val reader = peer.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val request = reader.readLine() ?: return
                    val method = request.substringBefore(' ')
                    var range: String? = null
                    while (true) {
                        val line = reader.readLine() ?: return
                        if (line.isEmpty()) break
                        if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                    }
                    if (method != "GET" && method != "HEAD") return
                    var start = 0L
                    var end = size - 1
                    var partial = false
                    if (range != null) {
                        val spec = range.removePrefix("bytes=").substringBefore(',')
                        val first = spec.substringBefore('-').toLongOrNull()
                        val last = spec.substringAfter('-', "").toLongOrNull()
                        if (first == null || first !in 0 until size) {
                            peer.getOutputStream().write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            return
                        }
                        start = first
                        if (last != null) end = minOf(last, end)
                        if (end < start) return
                        partial = true
                    }
                    val output = peer.getOutputStream()
                    val headers = buildString {
                        append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\n")
                        append("Content-Length: ${end - start + 1}\r\n")
                        if (partial) append("Content-Range: bytes $start-$end/$size\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(headers.toByteArray(Charsets.US_ASCII))
                    if (method == "HEAD") return
                    RandomAccessFile(file, "r").use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var position = start
                        var lastReceived = System.currentTimeMillis()
                        var lastPriorityUpdate = 0L
                        while (running.get() && position <= end) {
                            val absolute = fileOffset + position
                            val piece = (absolute / pieceSize).toInt()
                            if (!torrent.isValid || !torrent.havePiece(piece)) {
                                if (System.currentTimeMillis() - lastReceived > 120_000L) return
                                if (System.currentTimeMillis() - lastPriorityUpdate > 5_000L) {
                                    prioritizeWindow(torrent, piece, ((fileOffset + size - 1) / pieceSize).toInt())
                                    lastPriorityUpdate = System.currentTimeMillis()
                                }
                                Thread.sleep(150)
                                continue
                            }
                            val bytesToPieceEnd = pieceSize - absolute % pieceSize
                            val length = minOf(buffer.size.toLong(), end - position + 1, bytesToPieceEnd).toInt()
                            input.seek(position)
                            val count = input.read(buffer, 0, length)
                            if (count <= 0) return
                            output.write(buffer, 0, count)
                            position += count
                            lastReceived = System.currentTimeMillis()
                        }
                    }
                } catch (_: Exception) {
                    // A seek or player stop may close this HTTP connection at any time.
                }
            }
        }
    }
}
