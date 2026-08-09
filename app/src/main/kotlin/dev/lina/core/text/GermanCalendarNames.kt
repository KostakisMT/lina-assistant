package dev.lina.core.text

import java.util.Calendar

/**
 * Deutsche Wochentag-/Monatsnamen für Sprachausgabe und -erkennung. War
 * früher privat in `Reminder.kt` dupliziert – jetzt geteilt, da der Kalender
 * (`GermanDateParser`, `CalendarEvent`) denselben Wortschatz braucht.
 */
object GermanCalendarNames {

    /** Index über `Calendar.DAY_OF_WEEK - 1` (Sonntag = 1 in `Calendar`). */
    val wochentage: List<String> = listOf(
        "Sonntag", "Montag", "Dienstag", "Mittwoch",
        "Donnerstag", "Freitag", "Samstag",
    )

    /** Index über `Calendar.MONTH` (Januar = 0). */
    val monate: List<String> = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
        "August", "September", "Oktober", "November", "Dezember",
    )

    fun weekdayName(cal: Calendar): String = wochentage[cal.get(Calendar.DAY_OF_WEEK) - 1]

    fun monthName(cal: Calendar): String = monate[cal.get(Calendar.MONTH)]
}
