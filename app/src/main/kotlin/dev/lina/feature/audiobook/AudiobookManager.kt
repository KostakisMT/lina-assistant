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

    init {
        player.setOnPositionUpdate { positionMs, durationMs ->
            saveProgress(positionMs, durationMs)
        }
        // Beim automatischen Weiterlaufen ansagen, wo wir sind – ohne Bild
        // ist der Kapitelwechsel sonst nicht wahrnehmbar.
        player.setOnChapterChanged { index ->
            val chapter = currentBook?.chapters?.getOrNull(index) ?: return@setOnChapterChanged
            ttsEngine.speak(chapter.title, TtsPriority.NORMAL)
            saveProgress(0L, player.durationMs)
        }
    }

    fun play() {
        val savedState = stateStore.load() ?: run { listBooks(); return }

        val local = library.findByQuery(savedState.title)
        if (local != null) {
            playBook(local, savedState.positionMs, savedState.chapterIndex)
            return
        }

        // Gestreamtes Buch: Kapitelliste ist nicht gespeichert, also neu holen
        if (savedState.rssUrl.isNotBlank()) {
            ttsEngine.speak("Ich setze ${savedState.title} fort.", TtsPriority.HIGH)
            CoroutineScope(Dispatchers.IO).launch {
                val chapters = library.fetchChaptersFromRss(savedState.rssUrl)
                CoroutineScope(Dispatchers.Main).launch {
                    val book = Audiobook(
                        id = savedState.bookId,
                        title = savedState.title,
                        author = savedState.author,
                        uri = chapters.firstOrNull()?.uri ?: savedState.uri,
                        isLocal = false,
                        chapters = chapters,
                        rssUrl = savedState.rssUrl,
                    )
                    playBook(book, savedState.positionMs, savedState.chapterIndex)
                }
            }
            return
        }

        playBook(
            Audiobook(savedState.bookId, savedState.title, savedState.author, savedState.uri, true),
            savedState.positionMs,
        )
    }

    fun playBook(book: Audiobook, startPositionMs: Long = 0L, startChapter: Int = 0) {
        currentBook = book

        if (book.chapters.isNotEmpty()) {
            val index = startChapter.coerceIn(0, book.chapters.lastIndex)
            val chapter = book.chapters[index]
            val intro = if (book.hasChapters) {
                "${book.title} von ${book.author}. ${book.chapters.size} Kapitel. ${chapter.title}."
            } else {
                "${book.title} von ${book.author}."
            }
            ttsEngine.speak(intro, TtsPriority.HIGH)
            player.playChapters(book.chapters, index, startPositionMs)
            saveProgress(startPositionMs, 0L)
            return
        }

        ttsEngine.speak("${book.title} von ${book.author}.", TtsPriority.HIGH)
        player.play(book.uri, startPositionMs)
        saveProgress(startPositionMs, 0L)
    }

    // ------------------------------------------------------------- Kapitel

    fun nextChapter() {
        if (!requireChapters()) return
        if (player.nextChapter()) {
            saveProgress(0L, player.durationMs)
        } else {
            ttsEngine.speak("Das war das letzte Kapitel.", TtsPriority.HIGH)
        }
    }

    fun previousChapter() {
        if (!requireChapters()) return
        if (player.previousChapter()) {
            saveProgress(0L, player.durationMs)
        } else {
            ttsEngine.speak("Du bist im ersten Kapitel.", TtsPriority.HIGH)
        }
    }

    /** @param number 1-basiert, wie gesprochen ("Kapitel drei"). */
    fun goToChapter(number: Int) {
        if (!requireChapters()) return
        val book = currentBook ?: return
        if (number < 1 || number > book.chapters.size) {
            ttsEngine.speak(
                "Das Buch hat ${book.chapters.size} Kapitel.",
                TtsPriority.HIGH,
            )
            return
        }
        if (player.seekToChapter(number - 1)) {
            saveProgress(0L, player.durationMs)
        }
    }

    fun listChapters() {
        if (!requireChapters()) return
        val chapters = currentBook?.chapters ?: return
        ttsEngine.speak("${chapters.size} Kapitel.", TtsPriority.HIGH)
        // Lange Bücher nicht komplett vorlesen – das dauert sonst Minuten
        chapters.take(MAX_SPOKEN_CHAPTERS).forEachIndexed { i, chapter ->
            ttsEngine.speak("${i + 1}. ${chapter.title}.", TtsPriority.NORMAL)
        }
        if (chapters.size > MAX_SPOKEN_CHAPTERS) {
            ttsEngine.speak(
                "Und ${chapters.size - MAX_SPOKEN_CHAPTERS} weitere. " +
                    "Sag zum Beispiel: Kapitel zwanzig.",
                TtsPriority.NORMAL,
            )
        }
    }

    /** Sagt an, wenn kein Buch mit Kapiteln läuft. */
    private fun requireChapters(): Boolean {
        val book = currentBook
        if (book == null) {
            ttsEngine.speak("Kein Hörbuch ausgewählt.", TtsPriority.NORMAL)
            return false
        }
        if (book.chapters.isEmpty()) {
            ttsEngine.speak("Dieses Hörbuch hat keine Kapitel.", TtsPriority.NORMAL)
            return false
        }
        return true
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
        val kapitel = if (book.hasChapters) {
            val nr = player.currentChapterIndex + 1
            " Kapitel $nr von ${book.chapters.size}: ${player.currentChapter?.title.orEmpty()}."
        } else ""
        ttsEngine.speak(
            "${book.title} von ${book.author}. $status.$kapitel Minute $posMinutes von $durMinutes.",
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
                        library.resolveLibrivoxChapters(first) { chapters ->
                            if (chapters.isEmpty()) return@resolveLibrivoxChapters
                            val audiobook = Audiobook(
                                id = "librivox_${first.id}",
                                title = first.title,
                                author = first.author,
                                uri = chapters.first().uri,
                                isLocal = false,
                                chapters = chapters,
                                rssUrl = first.rssUrl,
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
                                    chapterTitle = chapters.first().title,
                                    rssUrl = first.rssUrl,
                                ))
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
        saveProgress(player.currentPositionMs, player.durationMs)
    }

    private fun saveProgress(positionMs: Long, durationMs: Long) {
        val book = currentBook ?: return
        val index = if (book.chapters.isEmpty()) 0 else player.currentChapterIndex
        stateStore.save(PlaybackState(
            bookId = book.id,
            title = book.title,
            author = book.author,
            uri = book.uri,
            positionMs = positionMs,
            durationMs = durationMs,
            chapterIndex = index,
            chapterTitle = book.chapters.getOrNull(index)?.title.orEmpty(),
            rssUrl = book.rssUrl,
        ))
    }

    private companion object {
        /** Mehr Kapitel am Stück vorzulesen überfordert beim Zuhören. */
        const val MAX_SPOKEN_CHAPTERS = 10
    }
}
