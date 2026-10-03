package com.xbox123z.zlib.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF81C784)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF00695C)
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    secondary = Color(0xFFAED581),
    tertiary = Color(0xFF4DB6AC)
)

@Composable
fun ZLibTheme(theme: String = "auto", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
