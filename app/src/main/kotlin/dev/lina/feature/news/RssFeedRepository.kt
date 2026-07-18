package dev.lina.feature.news

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class NewsItem(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String,
    val source: String,
)

data class RssFeed(
    val name: String,
    val url: String,
)

class RssFeedRepository {

    val feeds = listOf(
        RssFeed("Junge Welt", "https://www.jungewelt.de/rss.php"),
        RssFeed("unsere zeit", "https://www.unsere-zeit.de/feed/"),
        RssFeed("Spektrum der Wissenschaft", "https://www.spektrum.de/rss/news"),
        RssFeed("Yacht", "https://www.yacht.de/feed/"),
    )

    fun fetchFeed(feed: RssFeed): List<NewsItem> {
        return try {
            val connection = URL(feed.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Lina/1.0")
            connection.inputStream.use { stream ->
                parseRss(stream, feed.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchAll(): List<NewsItem> {
        return feeds.flatMap { fetchFeed(it) }
            .sortedByDescending { it.pubDate }
    }

    private fun parseRss(input: InputStream, source: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var inItem = false
        var title = ""
        var description = ""
        var link = ""
        var pubDate = ""
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "item" || currentTag == "entry") {
                        inItem = true
                        title = ""
                        description = ""
                        link = ""
                        pubDate = ""
                    }
                    if (inItem && currentTag == "link" && parser.attributeCount > 0) {
                        link = parser.getAttributeValue(null, "href") ?: link
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title = text
                            "description", "summary", "content:encoded" -> {
                                if (description.isEmpty()) description = text
                            }
                            "link" -> if (link.isEmpty()) link = text
                            "pubDate", "published", "updated" -> {
                                if (pubDate.isEmpty()) pubDate = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((parser.name == "item" || parser.name == "entry") && inItem) {
                        inItem = false
                        if (title.isNotBlank()) {
                            items.add(NewsItem(
                                title = stripHtml(title),
                                description = stripHtml(description),
                                link = link,
                                pubDate = pubDate,
                                source = source,
                            ))
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }
        return items
    }

    companion object {
        fun stripHtml(html: String): String {
            return html
                .replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
