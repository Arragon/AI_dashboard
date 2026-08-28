package com.subscriptiontracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF35675D),
    onPrimary = Color.White,
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF16181C),
    surface = Color.White,
    onSurface = Color(0xFF16181C),
    surfaceVariant = Color(0xFFF0F2F4),
    onSurfaceVariant = Color(0xFF666C75),
    outline = Color(0xFFE1E4E8),
    error = Color(0xFFA44848),
)

@Composable
fun SubscriptionTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
