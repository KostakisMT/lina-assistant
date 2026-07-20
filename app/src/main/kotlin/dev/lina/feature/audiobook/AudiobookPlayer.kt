package dev.lina.feature.audiobook

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AudiobookPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var onPositionUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    private var onChapterChanged: ((index: Int) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null

    private var chapters: List<Chapter> = emptyList()

    val isPlaying: Boolean get() = player.isPlaying
    val currentPositionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration.coerceAtLeast(0)

    /** Index innerhalb der Kapitelliste; 0 wenn ohne Kapitel abgespielt wird. */
    val currentChapterIndex: Int get() = player.currentMediaItemIndex
    val chapterCount: Int get() = chapters.size
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPositionUpdate?.invoke(player.currentPosition, durationMs)
                    onFinished?.invoke()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Auch beim automatischen Weiterlaufen ans Kapitelende ansagen,
                // sonst weiß der Nutzer nicht, wo er ist.
                if (chapters.isNotEmpty()) {
                    onChapterChanged?.invoke(player.currentMediaItemIndex)
                }
            }
        })
    }

    /** Einzeldatei ohne Kapitelstruktur. */
    fun play(uri: String, startPositionMs: Long = 0L) {
        chapters = emptyList()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    /**
     * Spielt ein Buch als Kapitel-Playlist. ExoPlayer läuft dadurch von selbst
     * ins nächste Kapitel weiter – vorher endete ein LibriVox-Buch nach der
     * ersten MP3.
     */
    fun playChapters(
        chapters: List<Chapter>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    ) {
        if (chapters.isEmpty()) return
        this.chapters = chapters

        val items = chapters.map { chapter ->
            val builder = MediaItem.Builder().setUri(chapter.uri)
            if (chapter.isClipped) {
                // DAISY: mehrere Kapitel teilen sich eine Audiodatei
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(chapter.clipStartMs)
                        .apply { if (chapter.clipEndMs > 0) setEndPositionMs(chapter.clipEndMs) }
                        .build()
                )
            }
            builder.build()
        }

        val safeIndex = startIndex.coerceIn(0, chapters.lastIndex)
        player.setMediaItems(items, safeIndex, startPositionMs)
        player.prepare()
        player.play()
    }

    /** @return false, wenn es kein nächstes Kapitel gibt. */
    fun nextChapter(): Boolean {
        if (currentChapterIndex >= chapters.lastIndex) return false
        player.seekTo(currentChapterIndex + 1, 0L)
        player.play()
        return true
    }

    /** @return false, wenn bereits das erste Kapitel läuft. */
    fun previousChapter(): Boolean {
        if (currentChapterIndex <= 0) return false
        player.seekTo(currentChapterIndex - 1, 0L)
        player.play()
        return true
    }

    /** @param index 0-basiert. @return false bei ungültigem Index. */
    fun seekToChapter(index: Int): Boolean {
        if (index !in chapters.indices) return false
        player.seekTo(index, 0L)
        player.play()
        return true
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

    fun setOnChapterChanged(listener: (index: Int) -> Unit) {
        onChapterChanged = listener
    }

    fun setOnFinished(listener: () -> Unit) {
        onFinished = listener
    }
}
