package dev.lina.feature.news

import android.content.Context
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewsReader(
    private val context: Context,
    private val ttsEngine: TtsEngine,
) {

    private val cache = NewsCache(context)
    private val repo = RssFeedRepository()
    private var items: List<NewsItem> = emptyList()
    private var currentIndex = -1

    fun readNews() {
        items = cache.load()

        if (items.isEmpty()) {
            ttsEngine.speak("Ich lade die Nachrichten. Einen Moment.", TtsPriority.HIGH)
            CoroutineScope(Dispatchers.IO).launch {
                val fetched = repo.fetchAll()
                if (fetched.isNotEmpty()) {
                    cache.save(fetched)
                    items = fetched
                    CoroutineScope(Dispatchers.Main).launch { readSummaries() }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        ttsEngine.speak("Leider konnte ich keine Nachrichten laden.", TtsPriority.HIGH)
                    }
                }
            }
            return
        }

        readSummaries()
    }

    private fun readSummaries() {
        if (items.isEmpty()) {
            ttsEngine.speak("Keine Nachrichten vorhanden.", TtsPriority.HIGH)
            return
        }

        currentIndex = 0
        val count = minOf(items.size, MAX_SUMMARY_COUNT)
        val intro = if (count == 1) "Eine Meldung." else "$count Meldungen."
        ttsEngine.speak(intro, TtsPriority.HIGH)

        readCurrentSummary()
    }

    fun nextNews() {
        if (items.isEmpty()) {
            ttsEngine.speak("Keine Nachrichten geladen.", TtsPriority.NORMAL)
            return
        }
        currentIndex++
        if (currentIndex >= items.size) {
            ttsEngine.speak("Das waren alle Meldungen.", TtsPriority.NORMAL)
            currentIndex = items.size - 1
            return
        }
        readCurrentSummary()
    }

    fun readDetail() {
        if (currentIndex < 0 || currentIndex >= items.size) {
            ttsEngine.speak("Keine Meldung ausgewählt.", TtsPriority.NORMAL)
            return
        }
        val item = items[currentIndex]
        val detail = item.description.ifBlank { item.title }
        ttsEngine.speak("${item.source}: $detail", TtsPriority.NORMAL)
    }

    fun stop() {
        ttsEngine.stop()
    }

    private fun readCurrentSummary() {
        if (currentIndex < 0 || currentIndex >= items.size) return
        val item = items[currentIndex]
        val number = currentIndex + 1
        val summary = buildSummary(item)
        ttsEngine.speak("$number. $summary", TtsPriority.NORMAL)
    }

    private fun buildSummary(item: NewsItem): String {
        val title = item.title
        val desc = item.description
        val firstSentence = if (desc.isNotBlank()) {
            val dotIndex = desc.indexOf('.')
            if (dotIndex in 1..150) desc.substring(0, dotIndex + 1) else ""
        } else ""

        return if (firstSentence.isNotBlank()) {
            "${item.source}: $title. $firstSentence"
        } else {
            "${item.source}: $title."
        }
    }

    companion object {
        private const val MAX_SUMMARY_COUNT = 10
    }
}
