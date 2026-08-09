package dev.lina.core.llm

/** Ergebnis von [ConversationEngine.readDocument] – Ansagetext plus optional ein erkannter Termin/Frist. */
data class DocumentReadResult(
    val reply: LinaReply,
    val suggestedEvent: SuggestedCalendarEvent?,
)

data class SuggestedCalendarEvent(
    val title: String,
    /** ISO-Datum, yyyy-MM-dd. */
    val date: String,
    /** HH:mm, optional. */
    val time: String?,
)
