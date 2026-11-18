package com.example.xtreamtv

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.FileStorage
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

internal val torrentProfiles = listOf("Soft", "Balanced", "Fast", "Shark Mega Fast", "Custom")
internal val torrentCacheChoices = listOf(0, 2, 5, 10)
private val torrentSessionLock = Mutex()
private const val MIB = 1024L * 1024

private sealed interface TorrentInput {
    data class Magnet(val uri: String) : TorrentInput
    data class Document(val uri: Uri) : TorrentInput
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TorrentPlayback(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf<TorrentInput?>(null) }
    var magnet by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var playback by remember { mutableStateOf<TorrentVideo?>(null) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) input = TorrentInput.Document(uri)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && input != null) {
                playback = null
                input = null
                message = ""
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(input) {
        val source = input ?: return@LaunchedEffect
        val prefs = playbackPrefs(context)
        val (peers, startupMiB) = when (prefs.getString("torrentProfile", "Balanced")) {
            "Soft" -> 25 to 4
            "Fast" -> 100 to 12
            "Shark Mega Fast" -> 160 to 20
            "Custom" -> prefs.getInt("torrentPeers", 60).coerceIn(20, 200) to
                prefs.getInt("torrentStartupMiB", 8).coerceIn(4, 24)
            else -> 60 to 8
        }
        val cacheGiB = prefs.getInt("torrentCacheGiB", 0).takeIf { it in torrentCacheChoices } ?: 0
        try {
            torrentSessionLock.withLock {
                var engine: TorrentSession? = null
                try {
                    message = "Preparing torrent…"
                    val video = runInterruptible(Dispatchers.IO) {
                        TorrentSession(context.applicationContext, peers, cacheGiB).also { engine = it }
                            .open(source, startupMiB)
                    }
                    playback = video
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { engine?.close() }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: LinkageError) {
            message = "Torrent playback is unavailable on this device."
            input = null
        } catch (e: Exception) {
            message = if (e is IOException) e.message ?: "Torrent could not be opened."
                else "Torrent could not be opened. Check the link or file."
            input = null
        } finally {
            playback = null
        }
    }
    if (playback != null) {
        val video = playback!!
        val dataSource = remember(video) { DataSource.Factory { TorrentDataSource(video) } }
        SafePlayer(
            url = Uri.fromFile(video.file).toString(),
            title = video.file.nameWithoutExtension,
            inferFormat = true,
            sourceFactory = dataSource,
            onExit = { playback = null; input = null; message = "" }
        )
    } else {
        BackHandler {
            if (input == null) onBack()
            else { input = null; message = "" }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Torrent playback", color = Color.White)
            if (input == null) {
                OutlinedTextField(
                    value = magnet, onValueChange = { magnet = it }, label = { Text("Magnet link") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumButton(enabled = magnet.trim().startsWith("magnet:?", ignoreCase = true),
                        onClick = { input = TorrentInput.Magnet(magnet.trim()) }) { Text("Play magnet") }
                    PremiumButton(onClick = {
                        try { documents.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) }
                        catch (_: android.content.ActivityNotFoundException) { message = "No file picker is available on this TV." }
                    }) { Text("Open .torrent file") }
                    PremiumButton(onClick = onBack) { Text("Back") }
                }
            } else PremiumButton(onClick = { input = null; message = "" }) { Text("Cancel") }
            if (message.isNotBlank()) Text(message, color = Color.White)
        }
    }
}

private class TorrentSession(context: Context, private val peers: Int, private val cacheGiB: Int) {
    private val root = File(context.cacheDir, "torrent-playback")
    private val metadata = File(root, "metadata")
    private val appContext = context
    private val session = SessionManager()
    private val closed = AtomicBoolean(false)
    private var activeDirectory: File? = null

    fun open(source: TorrentInput, startupMiB: Int): TorrentVideo {
        check(root.isDirectory || root.mkdirs())
        pruneCache()
        check(metadata.isDirectory || metadata.mkdirs())
        session.start(SessionParams(SettingsPack().connectionsLimit(peers).activeDownloads(1)))
        val bytes = when (source) {
            is TorrentInput.Magnet -> {
                require(source.uri.length <= 16_384)
                session.fetchMagnet(source.uri, 30, metadata)
                    ?: throw IOException("No peers supplied torrent information. Try again.")
            }
            is TorrentInput.Document -> appContext.contentResolver.openInputStream(source.uri)?.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw IOException("The torrent file is too large.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IOException("Could not read the torrent file.")
        }
        if (bytes.size > 4 * 1024 * 1024) throw IOException("The torrent file is too large.")
        val info = TorrentInfo.bdecode(bytes)
        require(info.isValid)
        val files = info.files()
        for (index in 0 until files.numFiles()) {
            require(!files.fileFlags(index).and_(FileStorage.FLAG_SYMLINK).non_zero())
        }
        val videoIndex = (0 until files.numFiles())
            .filter { File(files.filePath(it)).extension.lowercase() in setOf("mp4", "mkv", "webm", "mov", "ts", "m4v", "avi") }
            .maxByOrNull { files.fileSize(it) }
            ?: throw IOException("The torrent contains no supported video file.")
        val directory = File(root, info.infoHash().toHex())
        activeDirectory = directory
        check(directory.isDirectory || directory.mkdirs())
        val canonicalRoot = directory.canonicalPath + File.separator
        for (index in 0 until files.numFiles()) {
            require(File(directory, files.filePath(index)).canonicalPath.startsWith(canonicalRoot))
        }
        val size = files.fileSize(videoIndex)
        require(size > 0)
        val target = File(directory, files.filePath(videoIndex))
        val existing = target.takeIf { it.isFile }?.length() ?: 0
        if (size - existing + 128 * MIB > directory.usableSpace) {
            throw IOException("There is insufficient storage for this torrent.")
        }
        val priorities = Array(files.numFiles()) { if (it == videoIndex) Priority.DEFAULT else Priority.IGNORE }
        session.download(info, directory, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
        val deadline = SystemClock.elapsedRealtime() + 8_000
        var handle: TorrentHandle? = null
        while (handle == null || !handle.isValid) {
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("The torrent engine could not start this download.")
            Thread.sleep(100)
            handle = session.find(info.infoHash())
        }
        handle.resume()
        val video = TorrentVideo(target, size, files.fileOffset(videoIndex), info.pieceLength(), handle, closed)
        video.awaitRange(0, minOf(size, startupMiB * MIB), 60_000)
        return video
    }

    fun close() {
        closed.set(true)
        try { session.stop() } finally {
            metadata.deleteRecursively()
            activeDirectory?.setLastModified(System.currentTimeMillis())
            pruneCache()
        }
    }

    private fun pruneCache() {
        val budget = cacheGiB * 1024 * MIB
        var used = 0L
        root.listFiles().orEmpty().filter { it.name != "metadata" }.sortedByDescending { it.lastModified() }.forEach { directory ->
            val size = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (budget == 0L || size > budget - used) directory.deleteRecursively()
            else used += size
        }
    }
}

private class TorrentVideo(
    val file: File,
    val length: Long,
    private val offset: Long,
    private val pieceSize: Int,
    private val handle: TorrentHandle,
    private val closed: AtomicBoolean
) {
    fun awaitRange(position: Long, bytes: Long, timeout: Long) {
        require(bytes > 0 && pieceSize > 0)
        val first = ((offset + position) / pieceSize).toInt()
        val last = ((offset + position + bytes - 1) / pieceSize).toInt()
        val deadline = SystemClock.elapsedRealtime() + timeout
        for (piece in first..last) {
            if (closed.get()) throw IOException("Torrent playback stopped.")
            if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 500)
        }
        try {
            while (true) {
                if (closed.get() || !handle.isValid) throw IOException("Torrent playback stopped.")
                if ((first..last).all { handle.havePiece(it) } && file.length() >= position + bytes) return
                if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Torrent peers did not send enough video data.")
                Thread.sleep(100)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException("Torrent read cancelled.")
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TorrentDataSource(private val video: TorrentVideo) : BaseDataSource(true) {
    private var file: RandomAccessFile? = null
    private var currentUri: Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (dataSpec.position > video.length) throw java.io.EOFException()
        position = dataSpec.position
        remaining = video.length - position
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) remaining = minOf(remaining, dataSpec.length)
        file = RandomAccessFile(video.file, "r").apply { seek(position) }
        currentUri = dataSpec.uri
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = minOf(length.toLong(), remaining).toInt()
        video.awaitRange(position, count.toLong(), 25_000)
        val input = file ?: throw IOException("Torrent video is not open.")
        input.readFully(buffer, offset, count)
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        try { file?.close() } finally {
            file = null
            if (currentUri != null) {
                currentUri = null
                transferEnded()
            }
        }
    }
}
