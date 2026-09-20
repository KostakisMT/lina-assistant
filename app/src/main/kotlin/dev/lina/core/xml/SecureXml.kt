package dev.lina.core.xml

import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gemeinsame Fabrik für alle DOM-Parser in Lina.
 *
 * Lina parst XML aus zwei fremden Quellen: DAISY-Bücher von Blinden-
 * hörbüchereien (lokal, aber fremd produziert) und RSS-Feeds von LibriVox
 * (über das Netz). Ein unkonfigurierter [DocumentBuilderFactory] löst externe
 * Entities auf – ein präparierter Feed könnte per
 * `<!ENTITY xxe SYSTEM "file:///...">` lokale Dateien auslesen (XXE) oder die
 * App per Entity-Expansion aufhängen ("Billion Laughs").
 *
 * Deshalb geht **jeder** DOM-Parser durch [newDocumentBuilder]. Wer hier
 * vorbei `DocumentBuilderFactory.newInstance()` selbst aufruft, baut die
 * Lücke neu ein.
 *
 * Die `setFeature`-Aufrufe sind einzeln in `runCatching` gekapselt: Androids
 * Expat-basierter Parser kennt nicht alle Xerces-Feature-URIs und wirft dann
 * `ParserConfigurationException` – er lädt allerdings von Haus aus nichts
 * nach. Unter den JVM-Unit-Tests (Xerces) greifen sie alle.
 */
object SecureXml {

    fun newDocumentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.isExpandEntityReferences = false
        factory.isXIncludeAware = false

        // Stärkste Sperre zuerst: ohne DOCTYPE gibt es weder externe Entities
        // noch Entity-Expansion. Beide Aufrufer brauchen keine DTD – DAISY-
        // Dokumente werden vorher entdoctyped (DaisyParser.sanitizeXhtml),
        // LibriVox-RSS enthält keine.
        factory.setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
        // Gürtel und Hosenträger, falls ein Parser das Feature oben nicht kennt.
        factory.setFeatureQuietly("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)

        return factory.newDocumentBuilder().apply {
            // Letzte Instanz: sollte doch ein Entity durchrutschen, wird es
            // leer aufgelöst statt aus Datei oder Netz geladen.
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            // Fehler nicht auf stderr ausgeben, wir behandeln sie selbst
            setErrorHandler(null)
        }
    }

    private fun DocumentBuilderFactory.setFeatureQuietly(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }
}
