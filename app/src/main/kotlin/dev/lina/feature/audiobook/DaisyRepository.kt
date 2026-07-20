package dev.lina.feature.audiobook

import java.io.File

/**
 * Liest DAISY-2.02-Bücher von der SD-Karte / aus dem App-Ordner.
 *
 * Die Blindenhörbüchereien liefern pro Buch einen Ordner mit `ncc.html`.
 * Erkannt wird ein Buch genau daran.
 */
class DaisyRepository {

    data class DaisyBook(
        val directory: File,
        val title: String,
        val author: String,
        val totalTimeSecs: Int,
        val chapters: List<Chapter>,
    )

    /** Alle DAISY-Bücher unterhalb der übergebenen Ordner (eine Ebene tief). */
    fun scan(roots: List<File>): List<DaisyBook> {
        val found = mutableListOf<DaisyBook>()
        for (root in roots) {
            if (!root.isDirectory) continue
            // Der Wurzelordner selbst kann schon ein Buch sein
            load(root)?.let { found.add(it) }
            root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
                load(dir)?.let { found.add(it) }
            }
        }
        return found.distinctBy { it.directory.absolutePath }
    }

    /** @return null, wenn der Ordner kein lesbares DAISY-Buch ist. */
    fun load(directory: File): DaisyBook? {
        val ncc = findNcc(directory) ?: return null
        return try {
            val doc = DaisyParser.parseNcc(ncc.readText(Charsets.UTF_8))
            val chapters = buildChapters(directory, doc)
            if (chapters.isEmpty()) return null
            DaisyBook(
                directory = directory,
                title = doc.title,
                author = doc.author,
                totalTimeSecs = doc.totalTimeSecs,
                chapters = chapters,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Löst jeden ncc-Eintrag über seine SMIL-Datei zur konkreten Audiodatei
     * samt Zeitbereich auf. SMIL-Dateien werden zwischengespeichert, weil
     * sich mehrere Kapitel eine teilen können.
     */
    private fun buildChapters(dir: File, doc: DaisyParser.NccDocument): List<Chapter> {
        val smilCache = mutableMapOf<String, List<DaisyParser.SmilAudio>>()
        val chapters = mutableListOf<Chapter>()

        for (entry in doc.entries) {
            if (entry.smilFile.isBlank()) continue
            val audios = smilCache.getOrPut(entry.smilFile) {
                val smilFile = resolve(dir, entry.smilFile) ?: return@getOrPut emptyList()
                try {
                    DaisyParser.parseSmil(smilFile.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (audios.isEmpty()) continue

            // Das Fragment zeigt auf die Stelle in der SMIL; ohne Treffer
            // gilt der erste Audio-Verweis der Datei.
            val audio = audios.firstOrNull { it.id == entry.fragmentId } ?: audios.first()
            val audioFile = resolve(dir, audio.src) ?: continue

            chapters.add(
                Chapter(
                    title = entry.title,
                    uri = audioFile.toURI().toString(),
                    clipStartMs = audio.clipBeginMs,
                    clipEndMs = audio.clipEndMs,
                    durationSecs = if (audio.clipEndMs > audio.clipBeginMs) {
                        ((audio.clipEndMs - audio.clipBeginMs) / 1000).toInt()
                    } else 0,
                )
            )
        }
        return chapters
    }

    /** DAISY-CDs sind oft in Großbuchstaben gebrannt – Namen tolerant suchen. */
    private fun resolve(dir: File, name: String): File? {
        val direct = File(dir, name)
        if (direct.isFile) return direct
        return dir.listFiles()?.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun findNcc(dir: File): File? =
        dir.listFiles()?.firstOrNull { it.isFile && it.name.equals("ncc.html", ignoreCase = true) }
}
