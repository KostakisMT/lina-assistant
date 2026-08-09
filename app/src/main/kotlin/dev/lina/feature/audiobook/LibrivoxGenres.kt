package dev.lina.feature.audiobook

/**
 * LibriVox hat eine feste, englischsprachige Genre-Taxonomie (`?genre=<Name>`
 * in der API). Ein nicht existierender Name liefert von der API **kein**
 * leeres Ergebnis, sondern HTTP 500 – live gegen die echte API geprüft. Jeder
 * Wert hier ist deshalb Pflicht-Vorprüfung, bevor überhaupt ein Netzwerkruf
 * stattfindet.
 *
 * Bewusst nur die Top-Level-Fiction-Genres und die erste Ebene unter
 * "*Non-fiction" (nicht die volle, mehrfach verschachtelte Baumstruktur mit
 * ~140 Einträgen und mehrdeutigen Doppel-Namen wie "Religion" oder "General",
 * die in mehreren Zweigen gleich heißen) – das deckt alles ab, was die
 * Synonymtabelle unten braucht, ohne eine kaum wartbare Vollkopie von
 * librivox.org/search's Genre-Dropdown zu pflegen.
 *
 * Echte freie Themensuche ("Bücher über Marxismus") kann LibriVox grundsätzlich
 * nicht leisten – nur diese feste Taxonomie plus Stichwort-Fallback
 * (siehe [AudiobookLibrary.searchByTopic]).
 */
object LibrivoxGenres {

    val TAXONOMY: Set<String> = setOf(
        // Fiction (Top-Level)
        "Action & Adventure Fiction",
        "Classics (Greek & Latin Antiquity)",
        "Crime & Mystery Fiction",
        "Culture & Heritage Fiction",
        "Dramatic Readings",
        "Epistolary Fiction",
        "Erotica",
        "Travel Fiction",
        "Family Life",
        "Fantastic Fiction",
        "Fictional Biographies & Memoirs",
        "General Fiction",
        "Historical Fiction",
        "Humorous Fiction",
        "Literary Fiction",
        "Nature & Animal Fiction",
        "Nautical & Marine Fiction",
        "Plays",
        "Poetry",
        "Religious Fiction",
        "Romance",
        "Sagas",
        "Satire",
        "Short Stories",
        "Sports Fiction",
        "Suspense, Espionage, Political & Thrillers",
        "War & Military Fiction",
        "Westerns",
        "Children's Fiction",
        // Non-fiction (erste Ebene unter "*Non-fiction")
        "Art, Design & Architecture",
        "Biography & Autobiography",
        "Business & Economics",
        "Crafts & Hobbies",
        "Education",
        "Essays & Short Works",
        "Family & Relationships",
        "Health & Fitness",
        "History",
        "House & Home",
        "Humor",
        "Law",
        "Literary Collections",
        "Literary Criticism",
        "Mathematics",
        "Medical",
        "Music",
        "Nature",
        "Performing Arts",
        "Philosophy",
        "Political Science",
        "Psychology",
        "Reference",
        "Religion",
        "Science",
        "Self-Help",
        "Social Science (Culture & Anthropology)",
        "Sports & Recreation",
        "Technology & Engineering",
        "Travel & Geography",
        "True Crime",
        "Writing & Linguistics",
        // Sonstige Top-Level-Einträge
        "Asian Antiquity",
        "Periodicals & Magazines",
    )

    /** Deutsche Stichwörter -> exakter LibriVox-Taxonomie-Name. */
    private val SYNONYMS: Map<String, String> = mapOf(
        "marxismus" to "Political Science",
        "marxistisch" to "Political Science",
        "kommunismus" to "Political Science",
        "sozialismus" to "Political Science",
        "politische theorie" to "Political Science",
        "politikwissenschaft" to "Political Science",
        "politik" to "Political Science",
        "segeln" to "Nautical & Marine Fiction",
        "segelbücher" to "Nautical & Marine Fiction",
        "segelboote" to "Nautical & Marine Fiction",
        "nautik" to "Nautical & Marine Fiction",
        "seefahrt" to "Nautical & Marine Fiction",
        "schifffahrt" to "Nautical & Marine Fiction",
        "wissenschaft" to "Science",
        "naturwissenschaft" to "Science",
        "wirtschaft" to "Business & Economics",
        "ökonomie" to "Business & Economics",
        "oekonomie" to "Business & Economics",
        "geschichte" to "History",
        "philosophie" to "Philosophy",
        "abenteuer" to "Action & Adventure Fiction",
        "krimi" to "Crime & Mystery Fiction",
        "kriminalroman" to "Crime & Mystery Fiction",
        "gedichte" to "Poetry",
        "lyrik" to "Poetry",
        "poesie" to "Poetry",
        "kinderbücher" to "Children's Fiction",
        "kinderbuch" to "Children's Fiction",
        "reisen" to "Travel & Geography",
        "psychologie" to "Psychology",
        "recht" to "Law",
        "technik" to "Technology & Engineering",
        "theaterstücke" to "Plays",
        "theater" to "Plays",
    )

    /**
     * Normalisiert [topic], sucht zuerst in der Synonymtabelle, sonst als
     * Teilstring gegen die rohe Taxonomie (falls der Nutzer z.B. direkt
     * "Philosophy" sagt) – sonst `null`.
     */
    fun findGenre(topic: String): String? {
        val normalized = topic.trim().lowercase()
        if (normalized.isEmpty()) return null

        SYNONYMS[normalized]?.let { return it }

        return TAXONOMY.firstOrNull { genre ->
            genre.lowercase().contains(normalized) || normalized.contains(genre.lowercase())
        }
    }
}
