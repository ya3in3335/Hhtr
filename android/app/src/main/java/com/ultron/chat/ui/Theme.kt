package com.ultron.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ultron.chat.R

// Brand palette
val UltronRed = Color(0xFFE11D2A)
val UltronRedSoft = Color(0xFFFF4D5A)
val UltronBg = Color(0xFF120202)
val UltronSurface = Color(0xFF1C0608)
val UltronSurface2 = Color(0xFF270A0D)
val UltronLine = Color(0xFF4E161B)
val UltronText = Color(0xFFF4E9EA)
val UltronMuted = Color(0xFFC9969B)

// Extended palette for the "legendary" UI
val UltronBgTop = Color(0xFF1B0407)
val UltronBgMid = Color(0xFF120203)
val UltronBgBottom = Color(0xFF070101)
val UltronGlow = Color(0xFFE11D2A)
val UltronUserTop = Color(0xFFFF3B47)
val UltronUserBottom = Color(0xFFB3121C)
val UltronGlass = Color(0x0FFFFFFF)
val UltronGlassStrong = Color(0x1AFFFFFF)
val UltronGlassBorder = Color(0x2EFF6B74)
val UltronInk = Color(0xFF15181C)

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

private fun ultronTypography(): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.f() = copy(fontFamily = Tajawal)
    return Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(),
        headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}

private val UltronScheme = darkColorScheme(
    primary = UltronRed,
    onPrimary = Color.White,
    secondary = UltronRedSoft,
    background = UltronBg,
    onBackground = UltronText,
    surface = UltronSurface,
    onSurface = UltronText,
    surfaceVariant = UltronSurface2,
    onSurfaceVariant = UltronMuted,
    outline = UltronLine,
    error = UltronRedSoft,
)

@Composable
fun UltronTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // app is always dark-themed
    MaterialTheme(
        colorScheme = UltronScheme,
        typography = ultronTypography(),
        content = content,
    )
}
