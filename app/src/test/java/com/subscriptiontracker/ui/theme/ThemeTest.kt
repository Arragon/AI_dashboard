package com.subscriptiontracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeTest {
    @Test
    fun `theme selector returns dedicated light and dark palettes`() {
        assertEquals(LightAppColors, appColorScheme(darkTheme = false))
        assertEquals(DarkAppColors, appColorScheme(darkTheme = true))
        assertNotEquals(LightAppColors.background, DarkAppColors.background)
        assertNotEquals(LightAppColors.surface, DarkAppColors.surface)
    }

    @Test
    fun `core text pairs meet WCAG AA contrast`() {
        val pairs = listOf(
            LightAppColors.onBackground to LightAppColors.background,
            LightAppColors.onSurface to LightAppColors.surface,
            LightAppColors.onSurfaceVariant to LightAppColors.surfaceVariant,
            LightAppColors.onPrimary to LightAppColors.primary,
            DarkAppColors.onBackground to DarkAppColors.background,
            DarkAppColors.onSurface to DarkAppColors.surface,
            DarkAppColors.onSurfaceVariant to DarkAppColors.surfaceVariant,
            DarkAppColors.onPrimary to DarkAppColors.primary,
        )

        pairs.forEach { (foreground, background) ->
            assertTrue(
                contrastRatio(foreground, background) >= 4.5,
                "Expected AA contrast for $foreground on $background",
            )
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.04045) component / 12.92 else Math.pow((component + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
