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
)

class AudiobookLibrary(private val context: Context) {

    private val librivox = LibrivoxRepository()

    fun listAvailable(): List<Audiobook> {
        return curatedBooks() + scanLocalFiles()
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

    fun resolveLibrivoxStreamUrl(book: LibrivoxBook, callback: (String?) -> Unit) {
        val chapters = librivox.fetchChapters(book.rssUrl)
        callback(chapters.firstOrNull()?.url)
    }

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
        val dirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "Audiobooks") },
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { File(it, "Audiobooks") },
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS),
        )

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
