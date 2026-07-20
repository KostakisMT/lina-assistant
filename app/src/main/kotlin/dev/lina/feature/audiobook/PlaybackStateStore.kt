package dev.lina.feature.audiobook

import android.content.Context
import android.content.SharedPreferences

data class PlaybackState(
    val bookId: String,
    val title: String,
    val author: String,
    val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    /** 0-basiert; Position gilt innerhalb dieses Kapitels. */
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    /**
     * LibriVox-Feed des Buchs, sofern gestreamt. Die Kapitelliste selbst wird
     * nicht gespeichert – nach einem Neustart wird sie hierüber neu geholt,
     * damit das Buch nicht wieder bei Kapitel 1 endet.
     */
    val rssUrl: String = "",
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt() else 0
}

class PlaybackStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lina_audiobook", Context.MODE_PRIVATE)

    fun save(state: PlaybackState) {
        prefs.edit()
            .putString(KEY_BOOK_ID, state.bookId)
            .putString(KEY_TITLE, state.title)
            .putString(KEY_AUTHOR, state.author)
            .putString(KEY_URI, state.uri)
            .putLong(KEY_POSITION, state.positionMs)
            .putLong(KEY_DURATION, state.durationMs)
            .putInt(KEY_CHAPTER_INDEX, state.chapterIndex)
            .putString(KEY_CHAPTER_TITLE, state.chapterTitle)
            .putString(KEY_RSS_URL, state.rssUrl)
            .apply()
    }

    fun load(): PlaybackState? {
        val bookId = prefs.getString(KEY_BOOK_ID, null) ?: return null
        return PlaybackState(
            bookId = bookId,
            title = prefs.getString(KEY_TITLE, "") ?: "",
            author = prefs.getString(KEY_AUTHOR, "") ?: "",
            uri = prefs.getString(KEY_URI, "") ?: "",
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L),
            chapterIndex = prefs.getInt(KEY_CHAPTER_INDEX, 0),
            chapterTitle = prefs.getString(KEY_CHAPTER_TITLE, "") ?: "",
            rssUrl = prefs.getString(KEY_RSS_URL, "") ?: "",
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BOOK_ID = "book_id"
        private const val KEY_TITLE = "title"
        private const val KEY_AUTHOR = "author"
        private const val KEY_URI = "uri"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_DURATION = "duration_ms"
        private const val KEY_CHAPTER_INDEX = "chapter_index"
        private const val KEY_CHAPTER_TITLE = "chapter_title"
        private const val KEY_RSS_URL = "rss_url"
    }
}
