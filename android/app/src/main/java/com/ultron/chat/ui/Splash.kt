package com.ultron.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.chat.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val intro = rememberInfiniteTransition(label = "intro")

    val auraPulse by intro.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura",
    )
    val ring1 by intro.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "ring1",
    )
    val ring2 by intro.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "ring2",
    )

    LaunchedEffect(Unit) {
        scale.animateTo(1.06f, tween(650, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(260))
        delay(1500)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A0406), Color(0xFF160203), Color(0xFF000000)),
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // rotating rings
                Canvas(modifier = Modifier.size(300.dp).graphicsLayer { rotationZ = ring1 }) {
                    drawCircle(
                        color = Color(0x33E11D2A),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 3f),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0x00E11D2A), Color(0xCCE11D2A), Color(0x00E11D2A))),
                        startAngle = 0f, sweepAngle = 110f, useCenter = false,
                        style = Stroke(width = 5f),
                    )
                }
                Canvas(modifier = Modifier.size(360.dp).graphicsLayer { rotationZ = ring2 }) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0x00FF6B74), Color(0x99FF6B74), Color(0x00FF6B74))),
                        startAngle = 180f, sweepAngle = 80f, useCenter = false,
                        style = Stroke(width = 3f),
                    )
                }
                // red aura
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(auraPulse)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xAAE11D2A), Color(0x33E11D2A), Color(0x00E11D2A)),
                            )
                        )
                )
                // logo (image unchanged)
                Image(
                    painter = painterResource(R.drawable.ultron),
                    contentDescription = "Ultron",
                    modifier = Modifier.size(230.dp).scale(scale.value),
                )
            }
            Text(
                text = "ULTRON",
                color = Color.White,
                fontFamily = Tajawal,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 10.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = "SYSTEM ONLINE",
                color = Color(0xFFE39AA0),
                fontFamily = Tajawal,
                fontSize = 11.sp,
                letterSpacing = 6.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
