package dev.lina.core.intent

class LocalCommandResolver : IntentResolver {

    override fun resolve(input: String): ResolvedIntent? {
        val normalized = input.trim().lowercase()

        return resolveCall(normalized)
            ?: resolveSms(normalized)
            ?: resolveCallControl(normalized)
            ?: resolveNews(normalized)
            ?: resolveAudiobook(normalized)
            ?: resolveStop(normalized)
    }

    private fun resolveCall(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""(?:ruf|rufe)\s+(.+?)\s+an"""),
            Regex("""(?:anrufen|anruf)\s+(.+)"""),
            Regex("""ruf\s+(?:mal\s+)?(.+?)\s+an"""),
            Regex("""kannst du (?:bitte )?(.+?) anrufen"""),
            Regex("""bitte (?:ruf|rufe)\s+(.+?)\s+an"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                return ResolvedIntent.Call(match.groupValues[1].trim())
            }
        }
        return null
    }

    private fun resolveSms(input: String): ResolvedIntent? {
        val sendPatterns = listOf(
            Regex("""(?:schreib|schreibe|sende|send)\s+(.+?)[\s:]+(.+)"""),
            Regex("""(?:sms|nachricht)\s+an\s+(.+?)[\s:]+(.+)"""),
        )
        for (pattern in sendPatterns) {
            pattern.find(input)?.let { match ->
                return ResolvedIntent.SendSms(
                    match.groupValues[1].trim(),
                    match.groupValues[2].trim(),
                )
            }
        }

        if (input.matches(Regex(""".*(?:lies|lese|liest|zeig).*(?:nachricht|sms|nachrichten).*"""))) {
            return ResolvedIntent.ReadSms
        }

        Regex("""(?:antwort|antworte)[\s:]+(.+)""").find(input)?.let { match ->
            return ResolvedIntent.ReplySms(match.groupValues[1].trim())
        }

        return null
    }

    private fun resolveCallControl(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:annehmen|rangehen|abnehmen|drangehen).*""")) ->
            ResolvedIntent.AcceptCall
        input.matches(Regex(""".*(?:ablehnen|wegdrücken|nicht rangehen|ignorieren).*""")) ->
            ResolvedIntent.RejectCall
        input.matches(Regex(""".*(?:auflegen|beenden|schluss|tschüss).*""")) ->
            ResolvedIntent.HangUp
        else -> null
    }

    private fun resolveNews(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:was gibt es neues|nachrichten|news|neuigkeiten|was ist passiert).*""")) ->
            ResolvedIntent.ReadNews
        input.matches(Regex(""".*(?:mehr dazu|ausführlich|ganzer artikel|vollständig|detail).*""")) ->
            ResolvedIntent.NewsDetail
        input.matches(Regex(""".*(?:nächste|weiter|nächste meldung|skip).*""")) ->
            ResolvedIntent.NextNews
        else -> null
    }

    private fun resolveAudiobook(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:spiel|starte?).*(?:hörbuch|buch|vorlesen).*""")) ->
            ResolvedIntent.PlayAudiobook
        input.matches(Regex(""".*(?:pause|anhalten|halt an).*""")) ->
            ResolvedIntent.PauseAudiobook
        input.matches(Regex(""".*(?:weiter|fortsetzen|weiterspielen|resume).*""")) ->
            ResolvedIntent.ResumeAudiobook
        input.matches(Regex(""".*(?:zurück|zurückspulen|\d+\s*sekunden?\s*zurück).*""")) -> {
            val seconds = Regex("""(\d+)\s*sekunden?""").find(input)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 30
            ResolvedIntent.RewindAudiobook(seconds)
        }
        input.matches(Regex(""".*(?:was höre ich|welches buch|was läuft|was spielt).*""")) ->
            ResolvedIntent.AudiobookInfo
        input.matches(Regex(""".*(?:welche hörbücher|meine hörbücher|hörbuch(?:liste|er)|bibliothek).*""")) ->
            ResolvedIntent.ListAudiobooks
        else -> resolveSleepTimer(input) ?: resolveAudiobookSearch(input)
    }

    private fun resolveSleepTimer(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""(?:stopp?|aufhören|ende)\s+in\s+(\d+)\s*min"""),
            Regex("""(?:schlaf|sleep)\s*(?:timer|modus)?\s*(\d+)\s*min"""),
            Regex("""in\s+(\d+)\s*min(?:uten?)?\s+(?:stopp?en?|aufhören|aus)"""),
            Regex("""timer\s+(\d+)\s*min"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: return null
                return ResolvedIntent.SleepTimer(minutes)
            }
        }
        return null
    }

    private fun resolveAudiobookSearch(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""(?:such|suche|finde?)\s+(?:hörbuch\s+)?(.+)"""),
            Regex("""(?:hörbuch|buch)\s+(?:von|über)\s+(.+)"""),
            Regex("""(?:gibt es|hast du)\s+(?:etwas |was )?(?:von\s+)?(.+?)(?:\s+als hörbuch)?$"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                val query = match.groupValues[1].trim()
                if (query.isNotBlank() && query.length >= 2) {
                    return ResolvedIntent.SearchAudiobook(query)
                }
            }
        }
        return null
    }

    private fun resolveStop(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:stopp|stop|sei still|ruhe|halt|aufhören|still).*""")) ->
            ResolvedIntent.Stop
        else -> null
    }
}
