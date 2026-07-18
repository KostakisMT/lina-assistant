package dev.lina.feature.news

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NewsCache(context: Context) {

    private val cacheFile = File(context.filesDir, "news_cache.json")

    fun save(items: List<NewsItem>) {
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().apply {
                put("title", item.title)
                put("description", item.description)
                put("link", item.link)
                put("pubDate", item.pubDate)
                put("source", item.source)
            })
        }
        cacheFile.writeText(array.toString())
    }

    fun load(): List<NewsItem> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val array = JSONArray(cacheFile.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NewsItem(
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    link = obj.getString("link"),
                    pubDate = obj.getString("pubDate"),
                    source = obj.getString("source"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun lastModified(): Long = if (cacheFile.exists()) cacheFile.lastModified() else 0L
}
