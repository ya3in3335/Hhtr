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

// Modern 2026 palette — neutral dark, one vivid accent, hairline borders.
val UltronRed = Color(0xFFFB2C47)          // vivid accent
val UltronRedSoft = Color(0xFFFF5A72)
val UltronBg = Color(0xFF0B0B0D)
val UltronSurface = Color(0xFF151518)
val UltronSurface2 = Color(0xFF1E1E22)
val UltronLine = Color(0xFF2A2A30)
val UltronText = Color(0xFFF3F3F5)
val UltronMuted = Color(0xFF9B9BA6)

val UltronBgTop = Color(0xFF131316)
val UltronBgMid = Color(0xFF0D0D10)
val UltronBgBottom = Color(0xFF090909)
val UltronGlow = Color(0xFFFB2C47)
val UltronUserTop = Color(0xFFFB2C47)
val UltronUserBottom = Color(0xFFDC1E38)
val UltronGlass = Color(0xFF16161A)        // neutral card/button fill
val UltronGlassStrong = Color(0xFF1E1E23)
val UltronGlassBorder = Color(0x14FFFFFF)  // hairline neutral border
val UltronInk = Color(0xFF151518)          // composer / sheet surface

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
