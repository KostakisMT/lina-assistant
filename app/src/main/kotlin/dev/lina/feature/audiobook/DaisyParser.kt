package dev.lina.feature.audiobook

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser für DAISY 2.02 – das Format der Blindenhörbüchereien.
 *
 * Aufbau eines DAISY-Buchs:
 * ```
 * Buchordner/
 *   ncc.html      Navigation Control Center: Metadaten + Kapitel als h1..h6
 *   kapitel1.smil SMIL: welches Kapitel liegt in welcher Datei, ab welcher Sekunde
 *   audio1.mp3
 * ```
 *
 * Bewusst nur JDK-XML (kein Android-`XmlPullParser`), damit der Parser
 * unter Unit-Tests ohne Gerät läuft.
 */
object DaisyParser {

    data class NccEntry(
        val title: String,
        /** Ziel wie in ncc.html: "kap1.smil#tx0001". */
        val smilFile: String,
        val fragmentId: String,
        /** Überschriftenebene 1..6 – Ebene 1 ist meist der Hauptteil. */
        val level: Int,
    )

    data class NccDocument(
        val title: String,
        val author: String,
        val totalTimeSecs: Int,
        val entries: List<NccEntry>,
    )

    /** Ein `<audio>`-Verweis aus einer SMIL-Datei. */
    data class SmilAudio(
        val id: String,
        val src: String,
        val clipBeginMs: Long,
        val clipEndMs: Long,
    )

    // ---------------------------------------------------------------- ncc.html

    fun parseNcc(xhtml: String): NccDocument {
        val doc = parseXml(xhtml)

        val meta = mutableMapOf<String, String>()
        val metaNodes = doc.getElementsByTagName("meta")
        for (i in 0 until metaNodes.length) {
            val el = metaNodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name").lowercase()
            if (name.isNotBlank()) meta[name] = el.getAttribute("content").trim()
        }

        // Dokumentreihenfolge zählt: h1 und h2 wechseln sich in ncc.html ab,
        // eine Sammlung je Ebene würde die Kapitel umsortieren.
        val entries = mutableListOf<NccEntry>()
        collectHeadings(doc.documentElement, entries)

        return NccDocument(
            title = meta["dc:title"] ?: meta["dc.title"] ?: "Unbekannter Titel",
            author = meta["dc:creator"] ?: meta["dc.creator"] ?: "Unbekannt",
            totalTimeSecs = (parseClockValue(meta["ncc:totaltime"] ?: "") / 1000).toInt(),
            entries = entries,
        )
    }

    // -------------------------------------------------------------------- SMIL

    fun parseSmil(xml: String): List<SmilAudio> {
        val doc = parseXml(xml)
        val result = mutableListOf<SmilAudio>()

        val audioNodes = doc.getElementsByTagName("audio")
        for (i in 0 until audioNodes.length) {
            val el = audioNodes.item(i) as? Element ?: continue
            val src = el.getAttribute("src").trim()
            if (src.isBlank()) continue

            // Die ID hängt je nach Erzeuger am <audio> selbst oder am <par>/<seq>
            val id = el.getAttribute("id").ifBlank { enclosingId(el) }

            result.add(
                SmilAudio(
                    id = id,
                    src = src,
                    clipBeginMs = parseClockValue(
                        el.getAttribute("clip-begin").ifBlank { el.getAttribute("clipBegin") }
                    ),
                    clipEndMs = parseClockValue(
                        el.getAttribute("clip-end").ifBlank { el.getAttribute("clipEnd") }
                    ),
                )
            )
        }
        return result
    }

    /**
     * SMIL-Clock-Values in Millisekunden:
     *   "npt=123.5s", "00:02:05.5", "02:05", "45s", "1.5min", "2h"
     * @return 0 bei leer oder unlesbar (0 heißt „keine Grenze").
     */
    fun parseClockValue(raw: String): Long {
        var v = raw.trim().lowercase()
        if (v.isBlank()) return 0L
        if (v.startsWith("npt=")) v = v.removePrefix("npt=").trim()

        if (v.contains(':')) {
            val parts = v.split(':')
            val nums = parts.map { it.trim().toDoubleOrNull() ?: return 0L }
            val secs = when (nums.size) {
                3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
                2 -> nums[0] * 60 + nums[1]
                else -> return 0L
            }
            return (secs * 1000).toLong()
        }

        val match = Regex("""^([\d.]+)\s*(ms|s|min|h)?$""").find(v) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val factor = when (match.groupValues[2]) {
            "ms" -> 1.0
            "min" -> 60_000.0
            "h" -> 3_600_000.0
            else -> 1000.0 // "s" und ohne Einheit
        }
        return (value * factor).toLong()
    }

    // ----------------------------------------------------------------- Hilfen

    /**
     * Entfernt DOCTYPE und übersetzt benannte HTML-Entities in numerische.
     *
     * Nötig, weil ncc.html auf die DAISY-DTD verweist: ein XML-Parser würde
     * sie nachladen wollen (offline nicht möglich) und ohne sie an jedem
     * `&auml;` scheitern.
     */
    fun sanitizeXhtml(input: String): String {
        // Interne Teilmenge (<!DOCTYPE ... [ ... ]>) muss mit erfasst werden
        var s = input.replace(
            Regex("""<!DOCTYPE[^>\[]*(?:\[[^\]]*\])?\s*>""", RegexOption.IGNORE_CASE),
            "",
        )
        for ((name, code) in HTML_ENTITIES) {
            s = s.replace("&$name;", "&#$code;")
        }
        return s
    }

    private fun parseXml(raw: String): org.w3c.dom.Document =
        newBuilder().parse(InputSource(StringReader(sanitizeXhtml(raw))))

    private fun newBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // Nichts aus dem Netz nachladen – das Tablet ist ggf. offline und
        // externe Entities wären zudem ein Einfallstor (XXE).
        runCatching {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.isValidating = false
        factory.isExpandEntityReferences = false

        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            // Fehler nicht auf stderr ausgeben, wir behandeln sie selbst
            setErrorHandler(null)
        }
    }

    private fun firstDescendant(root: Element, tag: String): Element? {
        val list = root.getElementsByTagName(tag)
        return list.item(0) as? Element
    }

    /** Tiefensuche in Dokumentreihenfolge; sammelt h1..h6 mit Sprungziel. */
    private fun collectHeadings(node: Node?, into: MutableList<NccEntry>) {
        if (node == null) return
        if (node is Element) {
            val level = HEADING.matchEntire(node.tagName.lowercase())
                ?.groupValues?.get(1)?.toIntOrNull()
            if (level != null) {
                val anchor = firstDescendant(node, "a")
                val href = anchor?.getAttribute("href")?.trim().orEmpty()
                if (href.isNotBlank()) {
                    val title = anchor?.textContent?.collapseWhitespace().orEmpty()
                    into.add(
                        NccEntry(
                            title = title.ifBlank { "Kapitel ${into.size + 1}" },
                            smilFile = href.substringBefore('#'),
                            fragmentId = href.substringAfter('#', ""),
                            level = level,
                        )
                    )
                }
                return // Überschriften verschachteln sich nicht
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectHeadings(children.item(i), into)
        }
    }

    private val HEADING = Regex("""h([1-6])""")

    private fun enclosingId(el: Element): String {
        var node: Node? = el.parentNode
        while (node is Element) {
            val id = node.getAttribute("id")
            if (id.isNotBlank()) return id
            node = node.parentNode
        }
        return ""
    }

    /**
     * Auch geschützte Leerzeichen (&nbsp; → U+00A0) einebnen – Javas `\s`
     * kennt sie nicht, und im Kapiteltitel haben sie nichts verloren.
     */
    private fun String.collapseWhitespace(): String =
        replace(Regex("[\\s\\u00A0]+"), " ").trim()

    /**
     * Benannte Entities, die in ncc.html real vorkommen. XML kennt von Haus aus
     * nur amp/lt/gt/quot/apos – alles andere muss übersetzt werden.
     */
    private val HTML_ENTITIES = mapOf(
        "nbsp" to 160, "auml" to 228, "ouml" to 246, "uuml" to 252,
        "Auml" to 196, "Ouml" to 214, "Uuml" to 220, "szlig" to 223,
        "eacute" to 233, "egrave" to 232, "ecirc" to 234, "agrave" to 224,
        "aacute" to 225, "acirc" to 226, "ccedil" to 231, "ntilde" to 241,
        "oacute" to 243, "iacute" to 237, "uacute" to 250,
        "ndash" to 8211, "mdash" to 8212, "hellip" to 8230,
        "lsquo" to 8216, "rsquo" to 8217, "ldquo" to 8220, "rdquo" to 8221,
        "sbquo" to 8218, "bdquo" to 8222, "laquo" to 171, "raquo" to 187,
        "deg" to 176, "sect" to 167, "para" to 182, "middot" to 183,
        "bull" to 8226, "copy" to 169, "reg" to 174, "trade" to 8482,
        "euro" to 8364, "pound" to 163, "times" to 215, "divide" to 247,
        "shy" to 173, "dagger" to 8224, "prime" to 8242,
    )
}
