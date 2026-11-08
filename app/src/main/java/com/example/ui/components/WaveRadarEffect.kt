package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WaveRadarEffect(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFF00E5FF),
    centerPhotoRadiusDp: Dp = 72.dp,
    centerOffset: Offset? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_radar_around_photo")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, delayMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave4"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val startRadius = centerPhotoRadiusDp.toPx()
        val maxDistX = maxOf(center.x, size.width - center.x)
        val maxDistY = maxOf(center.y, size.height - center.y)
        val maxRadius = kotlin.math.hypot(maxDistX.toDouble(), maxDistY.toDouble()).toFloat() * 0.92f

        if (maxRadius <= startRadius) return@Canvas

        val waves = listOf(wave1, wave2, wave3, wave4)

        // Soft ambient aura right around the profile photo perimeter
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.28f),
                    waveColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = startRadius * 1.5f
            ),
            center = center,
            radius = startRadius * 1.5f
        )

        // Expanding waves radiating outward starting directly from around the user profile photo
        waves.forEach { progress ->
            val currentRadius = startRadius + (maxRadius - startRadius) * progress
            val alpha = (1f - progress).coerceIn(0f, 1f) * 0.75f

            // Faint ripple tint
            drawCircle(
                color = waveColor.copy(alpha = alpha * 0.12f),
                radius = currentRadius,
                center = center
            )

            // Primary wave ring line
            drawCircle(
                color = waveColor.copy(alpha = alpha),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx() * (1f - progress * 0.45f))
            )

            // Inner subtle concentric echo ring
            if (progress > 0.08f) {
                drawCircle(
                    color = waveColor.copy(alpha = alpha * 0.35f),
                    radius = currentRadius - 6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
