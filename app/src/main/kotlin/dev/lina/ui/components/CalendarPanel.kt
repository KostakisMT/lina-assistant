package dev.lina.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lina.core.text.GermanCalendarNames
import dev.lina.feature.calendar.CalendarEvent
import dev.lina.feature.calendar.CalendarManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

/**
 * Sichtbare Wochenansicht für Angehörige/Pflegepersonal – reine Anzeige, kein
 * Touch-Ziel nötig. Immer Wochenansicht (heute..+6 Tage), große Schrift,
 * Schwarz/Weiß/Gold. Pollt CalendarManager.currentWeek() wie AudiobookPlayerPanel
 * das Hörbuch pollt – kein ViewModel/Flow nötig für diese Aktualisierungsrate.
 */
@Composable
fun CalendarPanel(calendarManager: CalendarManager, modifier: Modifier = Modifier) {
    var week by remember { mutableStateOf(calendarManager.currentWeek()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            week = calendarManager.currentWeek()
            delay(5000)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Kalender – diese Woche",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))

        for (offset in 0..6) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            val iso = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            )
            val dayLabel = when (offset) {
                0 -> "Heute"
                1 -> "Morgen"
                else -> GermanCalendarNames.weekdayName(cal)
            }
            val dayEvents = week.filter { it.date == iso }.sortedBy { it.time ?: "24:00" }

            Text(
                "$dayLabel, ${cal.get(Calendar.DAY_OF_MONTH)}. ${GermanCalendarNames.monthName(cal)}",
                style = MaterialTheme.typography.titleLarge,
            )
            if (dayEvents.isEmpty()) {
                Text("Keine Termine", style = MaterialTheme.typography.bodyLarge)
            } else {
                dayEvents.forEach { event -> Text(eventLine(event), style = MaterialTheme.typography.bodyLarge) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun eventLine(event: CalendarEvent): String =
    if (event.time != null) "${event.title}, ${event.time} Uhr" else event.title
