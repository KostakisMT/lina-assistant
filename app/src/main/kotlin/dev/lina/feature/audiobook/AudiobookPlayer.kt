package dev.lina.feature.audiobook

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AudiobookPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var onPositionUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null

    val isPlaying: Boolean get() = player.isPlaying
    val currentPositionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration.coerceAtLeast(0)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPositionUpdate?.invoke(player.currentPosition, durationMs)
                }
            }
        })
    }

    fun play(uri: String, startPositionMs: Long = 0L) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun seekBack(seconds: Int) {
        val target = (player.currentPosition - seconds * 1000L).coerceAtLeast(0)
        player.seekTo(target)
    }

    fun seekForward(seconds: Int) {
        val target = (player.currentPosition + seconds * 1000L).coerceAtMost(durationMs)
        player.seekTo(target)
    }

    fun stop() {
        player.stop()
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun release() {
        player.release()
    }

    fun setOnPositionUpdate(listener: (positionMs: Long, durationMs: Long) -> Unit) {
        onPositionUpdate = listener
    }
}
