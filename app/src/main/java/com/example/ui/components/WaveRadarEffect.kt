package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun WaveRadarEffect(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFF00E5FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_radar")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, delayMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, delayMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave4"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = minOf(size.width, size.height) * 0.48f

        val waves = listOf(wave1, wave2, wave3, wave4)

        // Draw soft ambient gradient glow at center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.22f),
                    waveColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius * 0.65f
            ),
            center = center,
            radius = maxRadius * 0.65f
        )

        // Draw expanding pulse wave rings
        waves.forEach { progress ->
            val radius = maxRadius * progress
            val alpha = (1f - progress).coerceIn(0f, 1f) * 0.75f

            // Fill subtle wave disk
            drawCircle(
                color = waveColor.copy(alpha = alpha * 0.15f),
                radius = radius,
                center = center
            )

            // Crisp wave outline stroke
            drawCircle(
                color = waveColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx() * (1f - progress * 0.4f))
            )
        }

        // Draw subtle radar orbit dashes
        val orbitCount = 3
        for (i in 1..orbitCount) {
            val orbitRadius = (maxRadius / (orbitCount + 1)) * i
            drawCircle(
                color = waveColor.copy(alpha = 0.12f),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
