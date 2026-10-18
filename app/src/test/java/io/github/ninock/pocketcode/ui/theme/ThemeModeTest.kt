package io.github.ninock.pocketcode.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun toggleFromSystemPinsTheOppositeTheme() {
        assertEquals(AppThemeMode.LIGHT, toggledThemeMode(AppThemeMode.SYSTEM, systemDark = true))
        assertEquals(AppThemeMode.DARK, toggledThemeMode(AppThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun toggleBackToTheSystemThemeFollowsTheSystemAgain() {
        assertEquals(AppThemeMode.SYSTEM, toggledThemeMode(AppThemeMode.LIGHT, systemDark = true))
        assertEquals(AppThemeMode.SYSTEM, toggledThemeMode(AppThemeMode.DARK, systemDark = false))
    }

    @Test
    fun toggleAgainstTheSystemPinsTheOtherTheme() {
        assertEquals(AppThemeMode.LIGHT, toggledThemeMode(AppThemeMode.DARK, systemDark = true))
        assertEquals(AppThemeMode.DARK, toggledThemeMode(AppThemeMode.LIGHT, systemDark = false))
    }
}
