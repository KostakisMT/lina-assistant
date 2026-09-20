package dev.lina.feature.audiobook

import dev.lina.core.xml.SecureXml
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LibrivoxBook(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val totalDurationSecs: Int,
    val rssUrl: String,
    val language: String = "",
) {
    val durationDescription: String
        get() {
            val hours = totalDurationSecs / 3600
            val minutes = (totalDurationSecs % 3600) / 60
            return when {
                hours > 0 && minutes > 0 -> "$hours Stunden und $minutes Minuten"
                hours > 0 -> "$hours Stunden"
                else -> "$minutes Minuten"
            }
        }
}

data class LibrivoxChapter(
    val title: String,
    val url: String,
    val durationSecs: Int,
)

class LibrivoxRepository {

    fun search(query: String, language: String = "German"): List<LibrivoxBook> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/audiobooks/?title=^$encoded&format=json&limit=$OVER_FETCH_LIMIT"
            filterByLanguage(parseBooks(fetchJson(url)), language)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun searchByAuthor(author: String, language: String = "German"): List<LibrivoxBook> {
        return try {
            val encoded = URLEncoder.encode(author, "UTF-8")
            val url = "$BASE_URL/audiobooks/?author=$encoded&format=json&limit=$OVER_FETCH_LIMIT"
            filterByLanguage(parseBooks(fetchJson(url)), language)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * [genre] muss ein exakter Wert aus [LibrivoxGenres.TAXONOMY] sein, nie
     * Rohtext vom Nutzer – ein unbekannter Wert liefert von der API kein
     * leeres Ergebnis, sondern HTTP 500 (live gegen die echte API geprüft).
     */
    fun searchByGenre(genre: String, language: String = "German"): List<LibrivoxBook> {
        return try {
            val encoded = URLEncoder.encode(genre, "UTF-8")
            val url = "$BASE_URL/audiobooks/?genre=$encoded&format=json&limit=$OVER_FETCH_LIMIT"
            filterByLanguage(parseBooks(fetchJson(url)), language)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Der `language`-Query-Parameter wird von LibriVox serverseitig ignoriert
     * (live geprüft: `language=English` liefert trotzdem deutschsprachige
     * Treffer mit) – Filterung muss hier passieren. Deshalb wird mit
     * [OVER_FETCH_LIMIT] statt der gewünschten Endgröße angefragt und danach
     * auf [RESULT_LIMIT] gekappt.
     */
    private fun filterByLanguage(books: List<LibrivoxBook>, language: String): List<LibrivoxBook> =
        books.filter { it.language.equals(language, ignoreCase = true) }.take(RESULT_LIMIT)

    fun fetchChapters(rssUrl: String): List<LibrivoxChapter> {
        return try {
            val connection = URL(rssUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.use { stream ->
                parseRssChapters(stream)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * internal statt private, damit ein reiner JVM-Test das Parsing und die
     * XXE-Härtung ohne Netzwerk prüfen kann.
     *
     * Der Feed kommt über das Netz von einer fremden Quelle – ein
     * unkonfigurierter Parser würde externe Entities auflösen (XXE). Deshalb
     * geht auch dieser Parser durch [SecureXml], genau wie [DaisyParser].
     */
    internal fun parseRssChapters(input: InputStream): List<LibrivoxChapter> {
        val chapters = mutableListOf<LibrivoxChapter>()
        val doc = SecureXml.newDocumentBuilder().parse(input)
        val items = doc.getElementsByTagName("item")

        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes
            var title = ""
            var url = ""
            var duration = 0

            for (j in 0 until children.length) {
                val child = children.item(j)
                when (child.nodeName) {
                    "title" -> title = child.textContent?.trim() ?: ""
                    "enclosure" -> {
                        url = child.attributes?.getNamedItem("url")?.nodeValue ?: ""
                    }
                    "itunes:duration" -> {
                        duration = parseDuration(child.textContent?.trim() ?: "")
                    }
                }
            }
            if (title.isNotBlank() && url.isNotBlank()) {
                chapters.add(LibrivoxChapter(title, url, duration))
            }
        }
        return chapters
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":")
        return when (parts.size) {
            3 -> parts[0].toIntOrNull()?.times(3600).orZero() +
                parts[1].toIntOrNull()?.times(60).orZero() +
                parts[2].toIntOrNull().orZero()
            2 -> parts[0].toIntOrNull()?.times(60).orZero() +
                parts[1].toIntOrNull().orZero()
            1 -> parts[0].toIntOrNull().orZero()
            else -> 0
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    /** internal statt private, damit ein reiner JVM-Test das Parsing ohne Netzwerk prüfen kann. */
    internal fun parseBooks(json: String): List<LibrivoxBook> {
        val result = mutableListOf<LibrivoxBook>()
        try {
            val root = JSONObject(json)
            val books = root.optJSONArray("books") ?: return emptyList()
            for (i in 0 until books.length()) {
                val book = books.getJSONObject(i)
                val authors = book.optJSONArray("authors")
                val authorName = if (authors != null && authors.length() > 0) {
                    val a = authors.getJSONObject(0)
                    "${a.optString("first_name", "")} ${a.optString("last_name", "")}".trim()
                } else "Unbekannt"

                result.add(LibrivoxBook(
                    id = book.optString("id", ""),
                    title = book.optString("title", ""),
                    author = authorName,
                    description = book.optString("description", ""),
                    totalDurationSecs = book.optInt("totaltimesecs", 0),
                    rssUrl = book.optString("url_rss", ""),
                    language = book.optString("language", ""),
                ))
            }
        } catch (_: Exception) {}
        return result
    }

    private fun fetchJson(urlStr: String): String {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Lina/1.0")
        return connection.inputStream.bufferedReader().readText()
    }

    companion object {
        private const val BASE_URL = "https://librivox.org/api/feed"
        // Serverseitiger language-Filter ist wirkungslos (live geprüft) – wir
        // fragen mehr an, als wir zeigen wollen, und filtern client-seitig.
        private const val OVER_FETCH_LIMIT = 20
        private const val RESULT_LIMIT = 5
    }
}
