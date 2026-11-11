package ru.minutes.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Фирменный красный — только для записи и акцентов. Остальной интерфейс нейтральный. */
val Accent = Color(0xFFE5484D)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF111113),
    secondary = Accent,
    onSecondary = Color.White,
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1C1C20),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0xFF151518),
    surfaceContainerHigh = Color(0xFF1C1C20),
    surfaceContainerHighest = Color(0xFF232328),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF2A2A30),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF111113),
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF6B6B73),
    surfaceContainer = Color(0xFFEFEFEC),
    surfaceContainerHigh = Color(0xFFEAEAE6),
    surfaceContainerHighest = Color(0xFFE5E5E0),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFD92D20),
)

@Composable
fun ProtokolistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
