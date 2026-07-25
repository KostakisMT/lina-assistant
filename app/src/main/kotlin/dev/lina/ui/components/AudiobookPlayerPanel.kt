package dev.lina.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lina.feature.audiobook.AudiobookManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Sichtbare Steuerung fürs Hörbuch – für Angehörige/Besucher, die sehen oder
 * bedienen möchten, was gerade läuft. Reine UI-Verdrahtung: jeder Button ruft
 * eine bestehende [AudiobookManager]-Methode direkt auf, keine neue Logik.
 * Pollt [AudiobookManager.currentStatus] einmal pro Sekunde, solange das Panel
 * komponiert ist (d.h. solange ein Buch geladen ist) – kein Flow/ViewModel
 * nötig für diese bescheidene Aktualisierungsrate.
 */
@Composable
fun AudiobookPlayerPanel(audiobookManager: AudiobookManager, modifier: Modifier = Modifier) {
    var status by remember { mutableStateOf(audiobookManager.currentStatus()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            status = audiobookManager.currentStatus()
            delay(1000)
        }
    }
    val current = status ?: return

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(current.title, style = MaterialTheme.typography.titleLarge)
        Text(current.author, style = MaterialTheme.typography.bodyLarge)
        if (current.chapterCount > 1) {
            val chapterLine = "Kapitel ${current.chapterIndex + 1} von ${current.chapterCount}" +
                (current.chapterTitle?.let { ": $it" } ?: "")
            Text(chapterLine, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(16.dp))

        val progressFraction = if (current.durationMs > 0) {
            (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f)
        } else 0f
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.background,
        )
        Text(
            "${formatTime(current.positionMs)} / ${formatTime(current.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerButton("Zurück") { audiobookManager.previousChapter() }
            PlayerButton("-30s") { audiobookManager.rewind(30) }
            PlayerButton(if (current.isPlaying) "Pause" else "Weiter") {
                if (current.isPlaying) audiobookManager.pause() else audiobookManager.resume()
            }
            PlayerButton("Vor") { audiobookManager.nextChapter() }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerButton("Leiser") { audiobookManager.decreaseVolume() }
            PlayerButton("Lauter") { audiobookManager.increaseVolume() }
        }
    }
}

@Composable
private fun PlayerButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
