package dev.lina.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.lina.ui.launcher.LinaActivity
import kotlin.math.PI
import kotlin.math.sin

private val Gold = Color(0xFFFFD700)
private val White = Color(0xFFFFFFFF)

/**
 * Rein dekorative, animierte Statuskugel für Angehörige/Besucher – zeigt, was
 * Lina gerade tut. Reine Funktion von [activity] auf Pixel, keine Kopplung an
 * LauncherActivity. Bewusst schlicht/flach (keine Glow-/Blur-Effekte) und
 * unterscheidet Zustände über Bewegungscharakter statt neuer Farbtöne
 * (bleibt bei Schwarz/Weiß/Gold – "keine Farbe als einziger Informationsträger").
 */
@Composable
fun LinaOrb(activity: LinaActivity, modifier: Modifier = Modifier) {
    val periodMs = when (activity) {
        is LinaActivity.Idle -> 3000
        is LinaActivity.Listening -> 1100
        is LinaActivity.Thinking, is LinaActivity.Loading -> 2400
        is LinaActivity.Speaking -> 450
        is LinaActivity.Error -> 600
    }
    // Eine durchlaufende Phase 0..1 treibt alle Bewegungen – kein separater
    // Animator je Zustand nötig, nur die Zeichenlogik pro Zustand unterscheidet.
    val infinite = rememberInfiniteTransition(label = "linaOrb")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = periodMs, easing = LinearEasing)),
        label = "phase",
    )

    // Fehler: einmaliges kurzes Wackeln, danach Ruhe (kein Dauerzustand).
    val shake = remember { Animatable(0f) }
    LaunchedEffect(activity) {
        if (activity is LinaActivity.Error) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(80))
            shake.animateTo(-1f, tween(80))
            shake.animateTo(1f, tween(80))
            shake.animateTo(0f, tween(120))
        } else {
            shake.snapTo(0f)
        }
    }

    Canvas(modifier = modifier.size(ORB_SIZE)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 4f
        val twoPi = (2 * PI).toFloat()

        when (activity) {
            is LinaActivity.Idle -> {
                val r = baseRadius * (1f + 0.08f * sin(phase * twoPi))
                drawCircle(color = Gold, radius = r, center = center)
            }
            is LinaActivity.Listening -> {
                val r = baseRadius * (1f + 0.12f * sin(phase * twoPi))
                drawCircle(color = Gold, radius = r, center = center)
                // Ein bis zwei auslaufende Ringe, phasenversetzt.
                for (i in 0..1) {
                    val ringPhase = (phase + i * 0.5f) % 1f
                    val ringRadius = baseRadius * (1f + ringPhase * 1.2f)
                    val alpha = (1f - ringPhase).coerceIn(0f, 1f)
                    drawCircle(
                        color = White.copy(alpha = alpha * 0.6f),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 4f),
                    )
                }
            }
            is LinaActivity.Thinking, is LinaActivity.Loading -> {
                drawCircle(color = Gold.copy(alpha = 0.5f), radius = baseRadius, center = center)
                val arcCount = 3
                repeat(arcCount) { i ->
                    val startAngle = phase * 360f + i * (360f / arcCount)
                    drawArc(
                        color = Gold,
                        startAngle = startAngle,
                        sweepAngle = 40f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.3f, center.y - baseRadius * 1.3f),
                        size = Size(baseRadius * 2.6f, baseRadius * 2.6f),
                        style = Stroke(width = 8f),
                    )
                }
            }
            is LinaActivity.Speaking -> {
                // Überlagerte Sinuskurven simulieren Amplitude ohne echte Audiodaten.
                val wobble = sin(phase * twoPi) * 0.5f +
                    sin(phase * twoPi * 2f + 1f) * 0.3f +
                    sin(phase * twoPi * 3f + 2f) * 0.2f
                val r = baseRadius * (1f + 0.22f * wobble)
                drawCircle(color = Gold, radius = r, center = center)
            }
            is LinaActivity.Error -> {
                val shakeOffset = shake.value * (size.minDimension * 0.04f)
                drawCircle(
                    color = Gold,
                    radius = baseRadius,
                    center = Offset(center.x + shakeOffset, center.y),
                )
            }
        }
    }
}

private val ORB_SIZE = 140.dp
