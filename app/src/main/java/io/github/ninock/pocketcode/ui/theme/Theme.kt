package io.github.ninock.pocketcode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import io.github.ninock.pocketcode.R

// ---------------------------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------------------------

// Fallback palettes for phones without wallpaper colours (before Android 12). Generated with
// Material Color Utilities (SchemeFidelity) from the brand orange #F28C52, which stays the exact
// primaryContainer colour in both modes.
private val BrandLightColors = lightColorScheme(
    primary = Color(0xFF994711),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF28C52),
    onPrimaryContainer = Color(0xFF662A00),
    inversePrimary = Color(0xFFFFB690),
    secondary = Color(0xFF81543C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEC2A4),
    onSecondaryContainer = Color(0xFF794D36),
    tertiary = Color(0xFF00696C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF00B9BE),
    onTertiaryContainer = Color(0xFF004446),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231A15),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231A15),
    surfaceVariant = Color(0xFFF8DDD0),
    onSurfaceVariant = Color(0xFF55433A),
    surfaceTint = Color(0xFF994711),
    inverseSurface = Color(0xFF382E29),
    inverseOnSurface = Color(0xFFFFEDE6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF887368),
    outlineVariant = Color(0xFFDBC1B5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFFCEAE3),
    surfaceContainerHigh = Color(0xFFF6E5DD),
    surfaceContainerHighest = Color(0xFFF1DFD7),
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE8D7CF),
    primaryFixed = Color(0xFFFFDBCA),
    primaryFixedDim = Color(0xFFFFB690),
    onPrimaryFixed = Color(0xFF331100),
    onPrimaryFixedVariant = Color(0xFF783200),
    secondaryFixed = Color(0xFFFFDBCA),
    secondaryFixedDim = Color(0xFFF4BA9C),
    onSecondaryFixed = Color(0xFF311302),
    onSecondaryFixedVariant = Color(0xFF653D26),
    tertiaryFixed = Color(0xFF6CF6FB),
    tertiaryFixedDim = Color(0xFF49DADF),
    onTertiaryFixed = Color(0xFF002021),
    onTertiaryFixedVariant = Color(0xFF004F52),
)

private val BrandDarkColors = darkColorScheme(
    primary = Color(0xFFFFB690),
    onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFFF28C52),
    onPrimaryContainer = Color(0xFF662A00),
    inversePrimary = Color(0xFF994711),
    secondary = Color(0xFFF4BA9C),
    onSecondary = Color(0xFF4B2712),
    secondaryContainer = Color(0xFF683F28),
    onSecondaryContainer = Color(0xFFE5AC8F),
    tertiary = Color(0xFF49DADF),
    onTertiary = Color(0xFF003738),
    tertiaryContainer = Color(0xFF00B9BE),
    onTertiaryContainer = Color(0xFF004446),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF1DFD7),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF1DFD7),
    surfaceVariant = Color(0xFF55433A),
    onSurfaceVariant = Color(0xFFDBC1B5),
    surfaceTint = Color(0xFFFFB690),
    inverseSurface = Color(0xFFF1DFD7),
    inverseOnSurface = Color(0xFF382E29),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA38C81),
    outlineVariant = Color(0xFF55433A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423732),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D332E),
    surfaceContainerLow = Color(0xFF231A15),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceDim = Color(0xFF1A120D),
    primaryFixed = Color(0xFFFFDBCA),
    primaryFixedDim = Color(0xFFFFB690),
    onPrimaryFixed = Color(0xFF331100),
    onPrimaryFixedVariant = Color(0xFF783200),
    secondaryFixed = Color(0xFFFFDBCA),
    secondaryFixedDim = Color(0xFFF4BA9C),
    onSecondaryFixed = Color(0xFF311302),
    onSecondaryFixedVariant = Color(0xFF653D26),
    tertiaryFixed = Color(0xFF6CF6FB),
    tertiaryFixedDim = Color(0xFF49DADF),
    onTertiaryFixed = Color(0xFF002021),
    onTertiaryFixedVariant = Color(0xFF004F52),
)

/** Semantic colours Material's scheme has no role for. */
@Immutable
data class PocketExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightExtraColors = PocketExtraColors(
    success = Color(0xFF006C45),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF8BF8BD),
    onSuccessContainer = Color(0xFF002112),
)

private val DarkExtraColors = PocketExtraColors(
    success = Color(0xFF6EDBA3),
    onSuccess = Color(0xFF003822),
    successContainer = Color(0xFF005233),
    onSuccessContainer = Color(0xFF8BF8BD),
)

val LocalPocketExtraColors = staticCompositionLocalOf { DarkExtraColors }

/** Accent colour: follows the wallpaper on Android 12+, brand orange before that. */
val PocketOrange: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Secondary accent used for code and links. */
val PocketBlue: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

/** Success / "done" colour. */
val PocketGreen: Color
    @Composable @ReadOnlyComposable get() = LocalPocketExtraColors.current.success

// ---------------------------------------------------------------------------------------------
// Type: Google Sans Flex (bundled, SIL OFL; see assets/licenses). Display and headline styles use
// its rounded axis for the Expressive look.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: FontWeight, roundness: Float) = Font(
    R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", roundness),
    ),
)

private val FlexWeights = listOf(
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

val GoogleSansFlex = FontFamily(FlexWeights.map { googleSansFlex(it, roundness = 0f) })
val GoogleSansFlexRounded = FontFamily(FlexWeights.map { googleSansFlex(it, roundness = 100f) })

private val AppTypography: Typography = Typography(GoogleSansFlex).let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = GoogleSansFlexRounded),
        displayMedium = base.displayMedium.copy(fontFamily = GoogleSansFlexRounded),
        displaySmall = base.displaySmall.copy(fontFamily = GoogleSansFlexRounded),
        headlineLarge = base.headlineLarge.copy(fontFamily = GoogleSansFlexRounded),
        headlineMedium = base.headlineMedium.copy(fontFamily = GoogleSansFlexRounded),
        headlineSmall = base.headlineSmall.copy(fontFamily = GoogleSansFlexRounded),
    )
}

// ---------------------------------------------------------------------------------------------
// Theme mode
// ---------------------------------------------------------------------------------------------

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

/** Whether the app is currently drawn dark, after resolving [AppThemeMode.SYSTEM]. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/**
 * Theme after tapping the quick sun/moon toggle: the opposite of what is showing. Landing on the
 * system's own theme goes back to following the system instead of pinning that theme.
 */
fun toggledThemeMode(current: AppThemeMode, systemDark: Boolean): AppThemeMode {
    val currentlyDark = when (current) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val nextDark = !currentlyDark
    return when {
        nextDark == systemDark -> AppThemeMode.SYSTEM
        nextDark -> AppThemeMode.DARK
        else -> AppThemeMode.LIGHT
    }
}

@Composable
private fun appColorScheme(dark: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (dark) BrandDarkColors else BrandLightColors
}

/**
 * Material 3 Expressive theme: wallpaper colours on Android 12+ (brand orange before that), the
 * expressive spring motion scheme, and Google Sans Flex.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides isDark,
        LocalPocketExtraColors provides if (isDark) DarkExtraColors else LightExtraColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = appColorScheme(isDark),
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}
