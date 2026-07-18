package dev.lina.feature.audiobook

import android.content.Context
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudiobookManager(
    context: Context,
    private val ttsEngine: TtsEngine,
) {

    private val player = AudiobookPlayer(context)
    private val stateStore = PlaybackStateStore(context)
    private val library = AudiobookLibrary(context)
    private val sleepTimer = SleepTimer(
        onFadeStep = { volume -> player.setVolume(volume) },
        onFinished = { pause() },
    )

    private var currentBook: Audiobook? = null

    fun play() {
        val savedState = stateStore.load()
        if (savedState != null) {
            val book = library.findByQuery(savedState.title)
                ?: Audiobook(savedState.bookId, savedState.title, savedState.author, savedState.uri, true)
            playBook(book, savedState.positionMs)
            return
        }

        listBooks()
    }

    fun playBook(book: Audiobook, startPositionMs: Long = 0L) {
        currentBook = book
        ttsEngine.speak("${book.title} von ${book.author}.", TtsPriority.HIGH)

        player.setOnPositionUpdate { positionMs, durationMs ->
            saveProgress(positionMs, durationMs)
        }
        player.play(book.uri, startPositionMs)

        saveProgress(startPositionMs, 0L)
    }

    fun pause() {
        if (!player.isPlaying) {
            ttsEngine.speak("Kein Hörbuch läuft gerade.", TtsPriority.NORMAL)
            return
        }
        player.pause()
        saveCurrentProgress()
        ttsEngine.speak("Pausiert.", TtsPriority.HIGH)
    }

    fun resume() {
        if (player.isPlaying) {
            ttsEngine.speak("Läuft bereits.", TtsPriority.NORMAL)
            return
        }
        if (currentBook == null) {
            play()
            return
        }
        player.resume()
        ttsEngine.speak("Weiter.", TtsPriority.HIGH)
    }

    fun rewind(seconds: Int) {
        player.seekBack(seconds)
        ttsEngine.speak("$seconds Sekunden zurück.", TtsPriority.NORMAL)
    }

    fun info() {
        val book = currentBook
        if (book == null) {
            ttsEngine.speak("Kein Hörbuch ausgewählt.", TtsPriority.NORMAL)
            return
        }
        val posMinutes = (player.currentPositionMs / 60_000).toInt()
        val durMinutes = (player.durationMs / 60_000).toInt()
        val status = if (player.isPlaying) "läuft" else "pausiert"
        ttsEngine.speak(
            "${book.title} von ${book.author}. $status. Minute $posMinutes von $durMinutes.",
            TtsPriority.NORMAL,
        )
    }

    fun listBooks() {
        val books = library.listAvailable()
        if (books.isEmpty()) {
            ttsEngine.speak(
                "Du hast noch keine Hörbücher. Sag zum Beispiel: Suche Tolstoi.",
                TtsPriority.HIGH,
            )
            return
        }
        val intro = if (books.size == 1) "Du hast ein Hörbuch." else "Du hast ${books.size} Hörbücher."
        ttsEngine.speak(intro, TtsPriority.HIGH)
        books.forEachIndexed { i, book ->
            ttsEngine.speak("${i + 1}. ${book.title} von ${book.author}.", TtsPriority.NORMAL)
        }
        ttsEngine.speak("Welches möchtest du hören?", TtsPriority.NORMAL)
    }

    fun searchAndPlay(query: String) {
        ttsEngine.speak("Ich suche nach $query.", TtsPriority.HIGH)

        val localMatch = library.findByQuery(query)
        if (localMatch != null) {
            playBook(localMatch)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            library.searchLibrivox(query) { results ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (results.isEmpty()) {
                        ttsEngine.speak(
                            "Ich habe nichts zu $query gefunden.",
                            TtsPriority.HIGH,
                        )
                        return@launch
                    }

                    val intro = if (results.size == 1) "Ein Ergebnis." else "${results.size} Ergebnisse."
                    ttsEngine.speak(intro, TtsPriority.HIGH)
                    results.forEachIndexed { i, book ->
                        ttsEngine.speak(
                            "${i + 1}. ${book.title} von ${book.author}. ${book.durationDescription}.",
                            TtsPriority.NORMAL,
                        )
                    }

                    // Erstes Ergebnis automatisch vorbereiten
                    val first = results.first()
                    CoroutineScope(Dispatchers.IO).launch {
                        library.resolveLibrivoxStreamUrl(first) { streamUrl ->
                            if (streamUrl != null) {
                                val audiobook = Audiobook(
                                    id = "librivox_${first.id}",
                                    title = first.title,
                                    author = first.author,
                                    uri = streamUrl,
                                    isLocal = false,
                                )
                                CoroutineScope(Dispatchers.Main).launch {
                                    ttsEngine.speak(
                                        "Sag 'Spiel Hörbuch ab' um ${first.title} zu starten.",
                                        TtsPriority.NORMAL,
                                    )
                                    currentBook = audiobook
                                    stateStore.save(PlaybackState(
                                        bookId = audiobook.id,
                                        title = audiobook.title,
                                        author = audiobook.author,
                                        uri = audiobook.uri,
                                        positionMs = 0L,
                                        durationMs = first.totalDurationSecs * 1000L,
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
        ttsEngine.speak("Schlaf-Timer: $minutes Minuten.", TtsPriority.HIGH)
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
        ttsEngine.speak("Schlaf-Timer deaktiviert.", TtsPriority.NORMAL)
    }

    fun release() {
        saveCurrentProgress()
        sleepTimer.cancel()
        player.release()
    }

    private fun saveCurrentProgress() {
        val book = currentBook ?: return
        saveProgress(player.currentPositionMs, player.durationMs)
    }

    private fun saveProgress(positionMs: Long, durationMs: Long) {
        val book = currentBook ?: return
        stateStore.save(PlaybackState(
            bookId = book.id,
            title = book.title,
            author = book.author,
            uri = book.uri,
            positionMs = positionMs,
            durationMs = durationMs,
        ))
    }
}
