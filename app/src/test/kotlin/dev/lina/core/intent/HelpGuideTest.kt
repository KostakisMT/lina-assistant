package dev.lina.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Hilfe ist für einen blinden Nutzer die einzige Stelle, an der er
 * erfährt, was Lina kann. Ein Beispielsatz, den Lina vorliest und dann nicht
 * versteht, wäre schlimmer als gar keine Hilfe – deshalb prüft der erste Test
 * jedes Beispiel gegen den echten Resolver.
 */
class HelpGuideTest {

    private val resolver = LocalCommandResolver()

    @Test
    fun `jeder vorgelesene Beispielsatz wird lokal verstanden`() {
        HelpGuide.Thema.entries.forEach { thema ->
            thema.beispiele.forEach { satz ->
                val intent = resolver.resolve(satz)
                assertNotNull("Beispiel aus ${thema.titel} nicht erkannt: \"$satz\"", intent)
                assertFalse(
                    "Beispiel aus ${thema.titel} landet in der Hilfe statt beim Befehl: \"$satz\"",
                    intent is ResolvedIntent.Help,
                )
            }
        }
    }

    @Test
    fun `Fragen nach dem Ueberblick`() {
        listOf(
            "was kannst du",
            "Was kannst du alles?",
            "was kannst du denn eigentlich",
            "Womit kannst du mir helfen?",
            "wobei kannst du mir helfen",
            "was kann ich dich fragen",
            "was kann ich alles mit dir machen",
            "welche befehle gibt es",
            "was kannst du für mich tun",
            "Lina, was kannst du so?",
        ).forEach { frage ->
            assertEquals(frage, ResolvedIntent.Help(null), resolver.resolve(frage))
        }
    }

    @Test
    fun `Frage zu einem Thema springt direkt dorthin`() {
        assertEquals(
            ResolvedIntent.Help(HelpGuide.Thema.HOERBUECHER),
            resolver.resolve("was kannst du mit hörbüchern"),
        )
        assertEquals(
            ResolvedIntent.Help(HelpGuide.Thema.TERMINE),
            resolver.resolve("wie funktioniert das mit den terminen"),
        )
    }

    @Test
    fun `Befehle mit kannst du bleiben Befehle`() {
        assertTrue(resolver.resolve("kannst du boris anrufen") !is ResolvedIntent.Help)
        assertTrue(resolver.resolve("was kannst du mir über tolstoi erzählen") !is ResolvedIntent.Help)
        // Notruf-nahe Formulierungen dürfen nie ein Tutorial starten
        assertNull(HelpGuide.erkenne("ich brauche hilfe"))
        assertNull(HelpGuide.erkenne("hilfe"))
        // Be My Eyes bleibt Be My Eyes
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("hilfe beim sehen"))
    }

    @Test
    fun `Gespraech fuehrt vom Ueberblick zum Thema und endet auf nein`() {
        val guide = HelpGuide(mitCloud = true)
        val start = guide.start()
        assertTrue(start.weiterZuhoeren)
        assertTrue(start.text.contains("Hörbücher"))

        val hoerbuch = guide.antwort("Hörbücher bitte")
        assertTrue(hoerbuch.text.startsWith("Sag: Spiel Hörbuch ab"))
        assertTrue(hoerbuch.weiterZuhoeren)

        val ja = guide.antwort("ja")
        assertTrue(ja.text.startsWith("Worüber?"))
        assertTrue(ja.weiterZuhoeren)

        val ende = guide.antwort("nein danke")
        assertFalse(ende.weiterZuhoeren)
    }

    @Test
    fun `stopp beendet die Hilfe statt Lautstaerke zu erklaeren`() {
        val guide = HelpGuide(mitCloud = true)
        guide.start()
        assertFalse(guide.antwort("stopp").weiterZuhoeren)
    }

    @Test
    fun `nach zwei Missverstaendnissen gibt Lina auf statt endlos zu fragen`() {
        val guide = HelpGuide(mitCloud = true)
        guide.start()
        assertTrue(guide.antwort("blumenkohl").weiterZuhoeren)
        assertFalse(guide.antwort("gurkensalat").weiterZuhoeren)
    }

    @Test
    fun `ohne Cloud fehlen Nachrichten und freies Gespraech`() {
        val guide = HelpGuide(mitCloud = false)
        assertFalse(HelpGuide.Thema.NACHRICHTEN in guide.themen)
        assertFalse(HelpGuide.Thema.GESPRAECH in guide.themen)
        assertFalse(guide.start().text.contains("Nachrichten"))
        // Direkt nach einem Cloud-Thema gefragt → Überblick statt Versprechen
        assertTrue(guide.start(HelpGuide.Thema.NACHRICHTEN).text.startsWith("Ich kann dir"))
    }

    @Test
    fun `Texte bleiben kurz genug zum Zuhoeren`() {
        // ~25 Sekunden bei Rate 0.9 – länger hört niemand konzentriert zu
        HelpGuide.Thema.entries.forEach {
            assertTrue("${it.titel}: ${it.text.length} Zeichen", it.text.length <= 330)
        }
    }
}
