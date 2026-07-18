package dev.lina.feature.audiobook

import org.json.JSONObject
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
            val url = "$BASE_URL/audiobooks/?title=^$encoded" +
                "&format=json&fields={id,title,authors,description,totaltime,totaltimesecs,url_rss}" +
                "&limit=5"
            val json = fetchJson(url)
            parseBooks(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun searchByAuthor(author: String): List<LibrivoxBook> {
        return try {
            val encoded = URLEncoder.encode(author, "UTF-8")
            val url = "$BASE_URL/audiobooks/?author=$encoded" +
                "&format=json&fields={id,title,authors,description,totaltime,totaltimesecs,url_rss}" +
                "&limit=5"
            val json = fetchJson(url)
            parseBooks(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

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

    private fun parseRssChapters(input: java.io.InputStream): List<LibrivoxChapter> {
        val chapters = mutableListOf<LibrivoxChapter>()
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)
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

    private fun parseBooks(json: String): List<LibrivoxBook> {
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
    }
}
