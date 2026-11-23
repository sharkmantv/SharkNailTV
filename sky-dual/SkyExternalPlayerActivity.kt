package com.ipvio.stream

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Optional IPVIO-engine Media3 1.11.1 external video player.
 * Receives regular ACTION_VIEW video intents from other IPTV apps.
 * Does not alter any existing IPTV credentials or native 8K player.
 */
class SkyExternalPlayerActivity : ComponentActivity() {
    private var skyPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = intent?.data ?: intent?.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)
        if (uri == null || uri.scheme !in listOf("http", "https", "content", "file")) {
            Toast.makeText(this, "No playable video stream was sent", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val player = ExoPlayer.Builder(this).build()
        skyPlayer = player
        val playerView = PlayerView(this).apply {
            this.player = player
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            controllerShowTimeoutMs = 4500
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        setContentView(playerView)
        player.addListener(object: Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@SkyExternalPlayerActivity,
                    "Unable to play stream: " + error.errorCodeName, Toast.LENGTH_LONG).show()
            }
        })
        // Allow Media3 to determine the stream format as IPVIO normally does.
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        skyPlayer?.pause()
    }

    override fun onDestroy() {
        skyPlayer?.release()
        skyPlayer = null
        super.onDestroy()
    }
}
