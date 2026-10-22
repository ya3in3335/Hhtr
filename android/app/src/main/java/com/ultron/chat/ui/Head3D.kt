package com.ultron.chat.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.ultron.chat.R

/**
 * The Ultron head with a pseudo-3D feel: it tilts with the phone (accelerometer
 * parallax + perspective), floats idly, and a red glow shifts behind it.
 */
@Composable
fun Head3D(size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var rotY by remember { mutableStateOf(0f) }
    var rotX by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val ax = e.values[0]
                val ay = e.values[1]
                val targetY = (ax / 9.81f * 16f).coerceIn(-16f, 16f)
                val targetX = ((ay / 9.81f) - 0.45f) * 16f
                val clampedX = targetX.coerceIn(-16f, 16f)
                // smooth toward the target
                rotY += (targetY - rotY) * 0.12f
                rotX += (clampedX - rotX) * 0.12f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }

    val t = rememberInfiniteTransition(label = "head3d")
    val floatY by t.animateFloat(-7f, 7f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fy")
    val glow by t.animateFloat(0.22f, 0.46f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gl")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(UltronGlow.copy(alpha = glow), Color.Transparent),
                    center = Offset(this.size.width / 2f + rotY * 2.2f, this.size.height / 2f - rotX * 2.2f + floatY),
                ),
                radius = this.size.minDimension / 2f * 1.05f,
            )
        }
        Image(
            painter = painterResource(R.drawable.ultron),
            contentDescription = "Ultron",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationY = rotY
                    rotationX = -rotX
                    cameraDistance = 14f * density
                    translationY = floatY
                },
        )
    }
}
