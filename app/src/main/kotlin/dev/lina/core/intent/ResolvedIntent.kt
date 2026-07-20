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
    data object NextChapter : ResolvedIntent()
    data object PreviousChapter : ResolvedIntent()
    /** 1-basiert, wie gesprochen ("Kapitel drei"). */
    data class GoToChapter(val number: Int) : ResolvedIntent()
    data object ListChapters : ResolvedIntent()
    data class SleepTimer(val minutes: Int) : ResolvedIntent()
    data object AcceptCall : ResolvedIntent()
    data object RejectCall : ResolvedIntent()
    data object HangUp : ResolvedIntent()
    data object ReadDocument : ResolvedIntent()
    data class SetReminder(val rawInput: String) : ResolvedIntent()
    /** Von Claude aufgelöste Erinnerung (ISO-Zeitpunkt statt Rohtext). */
    data class SetReminderAt(
        val text: String,
        val isoZeit: String,
        val daily: Boolean,
    ) : ResolvedIntent()
    data object ListReminders : ResolvedIntent()
    data object ClearReminders : ResolvedIntent()
    data object Time : ResolvedIntent()
    data object Stop : ResolvedIntent()
    data class Unknown(val rawInput: String) : ResolvedIntent()
}
