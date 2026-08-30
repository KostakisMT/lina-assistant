package dev.lina.core.intent

sealed class ResolvedIntent {
    data class Call(val contactQuery: String) : ResolvedIntent()
    data class SendSms(val contactQuery: String, val message: String) : ResolvedIntent()
    data object ReadSms : ResolvedIntent()
    data class ReplySms(val message: String) : ResolvedIntent()
    data object ReadNews : ResolvedIntent()
    data object NextNews : ResolvedIntent()
    data object NewsDetail : ResolvedIntent()
    data object PlayAudiobook : ResolvedIntent()
    data object PauseAudiobook : ResolvedIntent()
    data object ResumeAudiobook : ResolvedIntent()
    data class RewindAudiobook(val seconds: Int = 30) : ResolvedIntent()
    data object AudiobookInfo : ResolvedIntent()
    data object ListAudiobooks : ResolvedIntent()
    data class SearchAudiobook(val query: String) : ResolvedIntent()
    /** Suche nach Thema/Genre statt Titel/Autor (LibriVox-Taxonomie, siehe LibrivoxGenres). */
    data class SearchAudiobookByGenre(val topic: String) : ResolvedIntent()

    /**
     * Offene Suchbitte ohne konkreten Titel/Autor ("kannst du ein Hörbuch für
     * mich suchen"). Lina fragt zurück "Zu welchem Thema?", statt mit einem
     * sinnlosen Suchbegriff bei LibriVox loszulaufen.
     */
    data object AskAudiobookTopic : ResolvedIntent()
    data object NextChapter : ResolvedIntent()
    data object PreviousChapter : ResolvedIntent()
    /** 1-basiert, wie gesprochen ("Kapitel drei"). */
    data class GoToChapter(val number: Int) : ResolvedIntent()
    data object ListChapters : ResolvedIntent()
    data class SleepTimer(val minutes: Int) : ResolvedIntent()
    data object VolumeUp : ResolvedIntent()
    data object VolumeDown : ResolvedIntent()
    /** 0–100, direkter Sollwert ("Lautstärke fünf" = 50, "Lautstärke auf 70 Prozent" = 70). */
    data class SetVolume(val percent: Int) : ResolvedIntent()
    data object AcceptCall : ResolvedIntent()
    data object RejectCall : ResolvedIntent()
    data object HangUp : ResolvedIntent()
    data object ReadDocument : ResolvedIntent()
    /** Menschliche Sehhilfe per Be My Eyes (ADR-033) – öffnet nur die App, Lina kann den Anruf nicht selbst absetzen. */
    data object CallHelper : ResolvedIntent()
    data class SetReminder(val rawInput: String) : ResolvedIntent()
    /** Von Claude aufgelöste Erinnerung (ISO-Zeitpunkt statt Rohtext). */
    data class SetReminderAt(
        val text: String,
        val isoZeit: String,
        val daily: Boolean,
    ) : ResolvedIntent()
    data object ListReminders : ResolvedIntent()
    data object ClearReminders : ResolvedIntent()
    /** Dimmt den Bildschirm und senkt die Lautstärke fürs Einschlafen. */
    data object SleepMode : ResolvedIntent()
    data object SleepModeOff : ResolvedIntent()
    data object ImportSimContacts : ResolvedIntent()
    data object ImportVcardContacts : ResolvedIntent()
    data object Time : ResolvedIntent()
    data object Date : ResolvedIntent()
    data class SetCalendarEvent(val rawInput: String) : ResolvedIntent()
    /** Von Claude aufgelöster Termin (ISO-Datum statt Rohtext). */
    data class SetCalendarEventAt(
        val title: String,
        val isoDatum: String,
        val isoZeit: String?,
    ) : ResolvedIntent()
    data object ShowCalendar : ResolvedIntent()
    data object HideCalendar : ResolvedIntent()
    data object ClearCalendarEvents : ResolvedIntent()
    data object Stop : ResolvedIntent()
    data class Unknown(val rawInput: String) : ResolvedIntent()
}
