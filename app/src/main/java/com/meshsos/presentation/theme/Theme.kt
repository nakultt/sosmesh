package com.meshsos.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SosRed = Color(0xFFE53935)
val SosRedDark = Color(0xFFB71C1C)
val WarnAmber = Color(0xFFFFA000)
val SafeGreen = Color(0xFF43A047)
val MeshTeal = Color(0xFF00ACC1)
val SurfaceDark = Color(0xFF121212)
val SurfaceVariant = Color(0xFF1E1E1E)
val OnSurface = Color(0xFFE0E0E0)
val SubtleGray = Color(0xFF9E9E9E)

private val DarkColors = darkColorScheme(
    primary = MeshTeal,
    onPrimary = Color.Black,
    secondary = WarnAmber,
    onSecondary = Color.Black,
    error = SosRed,
    background = SurfaceDark,
    surface = SurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface
)

@Composable
fun MeshSosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
