package com.ultron.chat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A clean, native voice orb that replaces the old 3D head. A glowing core with
 * breathing rings and orbiting sparks, whose colour and energy react to the
 * assistant's state: idle, listening, thinking, speaking. Pure Compose — no
 * WebView, no 3D engine.
 */
@Composable
fun VoiceOrb(state: String, size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(if (state == "speaking") 650 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (state == "thinking") 2600 else 9000, easing = LinearEasing)),
        label = "spin",
    )
    val ripple by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (state == "listening") 1500 else 3200, easing = FastOutSlowInEasing)),
        label = "ripple",
    )
    val glowPulse by t.animateFloat(
        0.35f, 0.7f,
        infiniteRepeatable(tween(if (state == "speaking") 500 else 2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    val accent = when (state) {
        "listening" -> Color(0xFF53E0A0)
        "thinking" -> Color(0xFFFFB64D)
        "speaking" -> UltronRedSoft
        else -> UltronRed
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f

            // Outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glowPulse), Color.Transparent),
                    center = c, radius = r,
                ),
                radius = r,
            )
            // Expanding ripple ring (strongest while listening)
            val rr = 0.5f + ripple * 0.48f
            drawCircle(
                color = accent.copy(alpha = (1f - ripple) * 0.35f),
                radius = r * rr, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.012f),
            )
            // Core
            val core = r * 0.42f * breathe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), accent, accent.copy(alpha = 0.05f)),
                    center = Offset(c.x - core * 0.25f, c.y - core * 0.25f), radius = core * 1.3f,
                ),
                radius = core, center = c,
            )
            // Thin bright rim
            drawCircle(
                color = accent.copy(alpha = 0.9f), radius = core, center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.01f),
            )
            // Orbiting sparks
            val orbit = r * 0.72f
            for (i in 0 until 3) {
                val ang = Math.toRadians((spin + i * 120f).toDouble())
                val p = Offset(c.x + orbit * cos(ang).toFloat(), c.y + orbit * sin(ang).toFloat())
                drawCircle(accent.copy(alpha = 0.85f), radius = r * 0.03f, center = p)
            }
        }
    }
}
