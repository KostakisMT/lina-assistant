package dev.lina.core.contacts

sealed class ContactMatchResult {
    data class SingleMatch(val contact: Contact) : ContactMatchResult()
    data class MultipleMatches(val contacts: List<Contact>, val query: String) : ContactMatchResult()
    data class NoMatch(val query: String) : ContactMatchResult()
}

class FuzzyContactMatcher(
    private val contactRepository: ContactSource,
) {

    fun findBestMatch(query: String): Contact? {
        return when (val result = findMatches(query)) {
            is ContactMatchResult.SingleMatch -> result.contact
            is ContactMatchResult.MultipleMatches -> result.contacts.first()
            is ContactMatchResult.NoMatch -> null
        }
    }

    fun findMatches(query: String): ContactMatchResult {
        val normalized = query.trim().lowercase()
        val contacts = contactRepository.loadAll()
        if (contacts.isEmpty()) return ContactMatchResult.NoMatch(normalized)

        // 1. Exakter vollständiger Name
        val exactMatch = contacts.filter {
            it.displayName.lowercase() == normalized
        }
        if (exactMatch.size == 1) return ContactMatchResult.SingleMatch(exactMatch.first())
        if (exactMatch.size > 1) return ContactMatchResult.MultipleMatches(exactMatch, normalized)

        // 2. Vorname oder Nachname exakt
        val namePartMatches = contacts.filter {
            it.displayName.lowercase().split(" ").any { part -> part == normalized }
        }
        if (namePartMatches.size == 1) return ContactMatchResult.SingleMatch(namePartMatches.first())
        if (namePartMatches.size > 1) return ContactMatchResult.MultipleMatches(namePartMatches, normalized)

        // 3. Name enthält Query
        val containsMatches = contacts.filter {
            it.displayName.lowercase().contains(normalized)
        }
        if (containsMatches.size == 1) return ContactMatchResult.SingleMatch(containsMatches.first())
        if (containsMatches.size > 1) return ContactMatchResult.MultipleMatches(containsMatches, normalized)

        // 4. Phonetische Ähnlichkeit
        val phoneticMatches = contacts
            .map { it to phoneticSimilarity(normalized, it.displayName.lowercase()) }
            .filter { it.second > 0.6 }
            .sortedByDescending { it.second }
        if (phoneticMatches.size == 1) return ContactMatchResult.SingleMatch(phoneticMatches.first().first)
        if (phoneticMatches.size > 1) {
            val best = phoneticMatches.first().second
            val topMatches = phoneticMatches.filter { it.second >= best - 0.1 }.map { it.first }
            if (topMatches.size == 1) return ContactMatchResult.SingleMatch(topMatches.first())
            return ContactMatchResult.MultipleMatches(topMatches, normalized)
        }

        // 5. Levenshtein Fuzzy
        val fuzzyMatches = contacts
            .map { it to bestPartialDistance(normalized, it.displayName.lowercase()) }
            .filter { it.second <= 2 }
            .sortedBy { it.second }
        if (fuzzyMatches.size == 1) return ContactMatchResult.SingleMatch(fuzzyMatches.first().first)
        if (fuzzyMatches.size > 1) {
            val best = fuzzyMatches.first().second
            val topMatches = fuzzyMatches.filter { it.second == best }.map { it.first }
            if (topMatches.size == 1) return ContactMatchResult.SingleMatch(topMatches.first())
            return ContactMatchResult.MultipleMatches(topMatches, normalized)
        }

        return ContactMatchResult.NoMatch(normalized)
    }

    private fun phoneticSimilarity(a: String, b: String): Double {
        val codeA = koelnerPhonetik(a)
        if (codeA.isEmpty()) return 0.0

        // Gegen jeden Namensteil UND den Gesamtnamen vergleichen –
        // exakte Code-Gleichheit ist für STT-Verhörer (z.B. "aroma"→"Arundhati") zu streng.
        // Phonetik + Buchstaben-Ähnlichkeit kombiniert, sonst kollidieren Codes
        // (völlig verschiedene Namen können denselben Kölner-Code haben)
        val parts = b.split(" ") + b
        return parts.filter { it.isNotBlank() }.maxOfOrNull { part ->
            val code = koelnerPhonetik(part)
            if (code.isEmpty()) return@maxOfOrNull 0.0
            val phonetic = if (code == codeA) {
                1.0
            } else {
                1.0 - levenshtein(codeA, code).toDouble() / maxOf(codeA.length, code.length)
            }
            val letters = 1.0 - levenshtein(a, part).toDouble() / maxOf(a.length, part.length)
            0.6 * phonetic + 0.4 * letters
        } ?: 0.0
    }

    private fun bestPartialDistance(query: String, fullName: String): Int {
        val parts = fullName.split(" ")
        return parts.minOfOrNull { levenshtein(query, it) } ?: levenshtein(query, fullName)
    }

    companion object {

        fun levenshtein(a: String, b: String): Int {
            val m = a.length
            val n = b.length
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    dp[i][j] = if (a[i - 1] == b[j - 1]) {
                        dp[i - 1][j - 1]
                    } else {
                        minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                    }
                }
            }
            return dp[m][n]
        }

        fun koelnerPhonetik(input: String): String {
            val s = input.lowercase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("ß", "ss").replace("ph", "f")
                .filter { it.isLetter() }

            if (s.isEmpty()) return ""

            val codes = StringBuilder()
            var prev = ' '
            for ((i, c) in s.withIndex()) {
                val next = if (i + 1 < s.length) s[i + 1] else ' '
                val code = when (c) {
                    'a', 'e', 'i', 'o', 'u' -> '0'
                    'b' -> '1'
                    'p' -> if (next == 'h') '3' else '1'
                    'd', 't' -> if (next in "csz") '8' else '2'
                    'f', 'v', 'w' -> '3'
                    'g', 'k', 'q' -> '4'
                    'l' -> '5'
                    'm', 'n' -> '6'
                    'r' -> '7'
                    's', 'z' -> '8'
                    'c' -> when {
                        i == 0 && next in "ahkloqrux" -> '4'
                        i == 0 -> '8'
                        prev in "sz" -> '8'
                        next in "ahkoqux" -> '4'
                        else -> '8'
                    }
                    'x' -> if (prev in "ckq") '8' else '4'
                    'h', 'j', 'y' -> null
                    else -> null
                }
                if (code != null) {
                    if (codes.isEmpty() || codes.last() != code) {
                        codes.append(code)
                    }
                }
                prev = c
            }

            return if (codes.length > 1) {
                codes[0].toString() + codes.substring(1).replace("0", "")
            } else {
                codes.toString()
            }
        }
    }
}
