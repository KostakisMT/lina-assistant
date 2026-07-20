package dev.lina.feature.audiobook

import android.content.Context
import android.os.Environment
import java.io.File

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val uri: String,
    val isLocal: Boolean,
    /** Leer = Einzeldatei ohne Kapitelstruktur. */
    val chapters: List<Chapter> = emptyList(),
    /** Nur bei LibriVox gesetzt – erlaubt das Nachladen der Kapitel. */
    val rssUrl: String = "",
) {
    val hasChapters: Boolean get() = chapters.size > 1
}

class AudiobookLibrary(private val context: Context) {

    private val librivox = LibrivoxRepository()
    private val daisy = DaisyRepository()

    fun listAvailable(): List<Audiobook> {
        return curatedBooks() + daisyBooks() + scanLocalFiles()
    }

    fun findByQuery(query: String): Audiobook? {
        val normalized = query.lowercase()
        return listAvailable().firstOrNull { book ->
            book.title.lowercase().contains(normalized) ||
                book.author.lowercase().contains(normalized)
        }
    }

    fun findByIndex(index: Int): Audiobook? {
        val books = listAvailable()
        return books.getOrNull(index)
    }

    fun searchLibrivox(query: String, callback: (List<LibrivoxBook>) -> Unit) {
        val results = librivox.search(query)
        if (results.isEmpty()) {
            callback(librivox.searchByAuthor(query))
        } else {
            callback(results)
        }
    }

    /**
     * Holt die vollständige Kapitelliste eines LibriVox-Buchs.
     *
     * Früher wurde hier nur `chapters.first().url` zurückgegeben – das Buch
     * war deshalb nach dem ersten Abschnitt zu Ende, ohne Hinweis an den
     * Nutzer. Ein LibriVox-Roman besteht aus dutzenden MP3s.
     */
    fun resolveLibrivoxChapters(book: LibrivoxBook, callback: (List<Chapter>) -> Unit) {
        callback(fetchChaptersFromRss(book.rssUrl))
    }

    /** Blockierend – nur aus einem IO-Kontext aufrufen. */
    fun fetchChaptersFromRss(rssUrl: String): List<Chapter> =
        librivox.fetchChapters(rssUrl).mapIndexed { i, ch ->
            Chapter(
                title = ch.title.ifBlank { "Kapitel ${i + 1}" },
                uri = ch.url,
                durationSecs = ch.durationSecs,
            )
        }

    /** DAISY-Bücher der Blindenhörbüchereien (Ordner mit ncc.html). */
    private fun daisyBooks(): List<Audiobook> {
        return daisy.scan(audioDirs()).map { book ->
            Audiobook(
                id = "daisy_${book.directory.name}",
                title = book.title,
                author = book.author,
                uri = book.chapters.first().uri,
                isLocal = true,
                chapters = book.chapters,
            )
        }
    }

    private fun audioDirs(): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let { File(it, "Audiobooks") },
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { File(it, "Audiobooks") },
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS),
    )

    private fun curatedBooks(): List<Audiobook> {
        return CURATED_TITLES.mapNotNull { (id, title, author) ->
            val localFile = findLocalFile(title)
            if (localFile != null) {
                Audiobook(id, title, author, localFile.toURI().toString(), isLocal = true)
            } else {
                null
            }
        }
    }

    private fun scanLocalFiles(): List<Audiobook> {
        val dirs = audioDirs()

        val curatedIds = CURATED_TITLES.map { it.first }.toSet()
        val found = mutableListOf<Audiobook>()

        for (dir in dirs) {
            if (!dir.exists()) continue
            dir.listFiles()?.filter { it.isAudioFile() }?.forEach { file ->
                val id = "local_${file.nameWithoutExtension}"
                if (id !in curatedIds && found.none { it.id == id }) {
                    found.add(Audiobook(
                        id = id,
                        title = file.nameWithoutExtension.replace("_", " "),
                        author = "Unbekannt",
                        uri = file.toURI().toString(),
                        isLocal = true,
                    ))
                }
            }
        }
        return found
    }

    private fun findLocalFile(title: String): File? {
        val normalized = title.lowercase().replace(" ", "_")
        val dirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "Audiobooks") },
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { File(it, "Audiobooks") },
        )
        for (dir in dirs) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { file ->
                if (file.isAudioFile() && file.nameWithoutExtension.lowercase().contains(normalized)) {
                    return file
                }
            }
        }
        return null
    }

    private fun File.isAudioFile(): Boolean {
        val ext = extension.lowercase()
        return ext in setOf("mp3", "m4a", "m4b", "ogg", "opus", "flac", "wav")
    }

    companion object {
        private val CURATED_TITLES = listOf(
            Triple("curated_djamilah", "Djamilah", "Tschingis Aitmatow"),
            Triple("curated_anna_karenina", "Anna Karenina", "Leo Tolstoi"),
            Triple("curated_die_mutter", "Die Mutter", "Maxim Gorki"),
            Triple("curated_dreigroschenoper", "Die Dreigroschenoper", "Bertolt Brecht"),
        )
    }
}
