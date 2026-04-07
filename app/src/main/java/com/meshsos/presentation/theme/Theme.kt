package com.meshsos.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Semantic accent colors (shared across both themes) ──────────────────────
val SosRed = Color(0xFFE53935)
val SosRedDark = Color(0xFFB71C1C)
val WarnAmber = Color(0xFFFFA000)
val SafeGreen = Color(0xFF43A047)
val MeshTeal = Color(0xFF00897B)

// ── Light palette ───────────────────────────────────────────────────────────
val LightBackground = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F1F3)
val LightOnBackground = Color(0xFF1A1C1E)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSubtle = Color(0xFF6B7280)
val LightOutline = Color(0xFFE0E2E6)

// ── Dark palette ────────────────────────────────────────────────────────────
val DarkBackground = Color(0xFF101114)
val DarkSurface = Color(0xFF1A1C20)
val DarkSurfaceVariant = Color(0xFF23262B)
val DarkOnBackground = Color(0xFFE4E6EA)
val DarkOnSurface = Color(0xFFE4E6EA)
val DarkSubtle = Color(0xFF9CA3AF)
val DarkOutline = Color(0xFF2E3238)

// ── Deprecated aliases for backward compat in files that still reference them ─
val SurfaceDark = DarkBackground
val SurfaceVariant = DarkSurfaceVariant
val OnSurface = DarkOnBackground
val SubtleGray = LightSubtle

private val LightColors = lightColorScheme(
    primary = MeshTeal,
    onPrimary = Color.White,
    secondary = WarnAmber,
    onSecondary = Color.Black,
    error = SosRed,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = MeshTeal,
    onPrimary = Color.Black,
    secondary = WarnAmber,
    onSecondary = Color.Black,
    error = SosRed,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

@Composable
fun MeshSosTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
