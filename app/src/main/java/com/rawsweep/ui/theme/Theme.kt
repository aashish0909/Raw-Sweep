package com.rawsweep.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF6750A4)
private val PurpleContainer = Color(0xFFEADDFF)
private val OnPurpleContainer = Color(0xFF21005D)
private val Error = Color(0xFFBA1A1A)

private val LightColors = lightColorScheme(
    primary = Purple,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = OnPurpleContainer,
    error = Error,
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFF3EDF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF49454F),
)

@Composable
fun RawSweepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
