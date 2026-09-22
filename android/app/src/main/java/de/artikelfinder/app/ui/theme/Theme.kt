package de.artikelfinder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ein festes Farbschema statt der dynamischen Farben aus dem Hintergrundbild: die App hat
 * genau zwei Signalfarben — Grün für Standort und Aktionen, Rot für Werbepreise — und die
 * sollen auf jedem Gerät gleich aussehen und nebeneinander funktionieren.
 */

private val HellesSchema = lightColorScheme(
    primary = Color(0xFF1B6B3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0C6),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F12),
    tertiary = Color(0xFF3A646F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF6),
    onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8F5),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF6F8F5),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF424942),
    outline = Color(0xFF727971),
    outlineVariant = Color(0xFFC6CDC4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF2ED),
    surfaceContainerHigh = Color(0xFFE8ECE7),
    surfaceContainerHighest = Color(0xFFE2E6E1),
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFF8CD7A1),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFB7F0C6),
    secondary = Color(0xFFB6CCB8),
    onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFA2CEDA),
    onTertiary = Color(0xFF02363F),
    tertiaryContainer = Color(0xFF214C57),
    onTertiaryContainer = Color(0xFFBEEAF6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF424942),
    onSurfaceVariant = Color(0xFFC2C9C0),
    outline = Color(0xFF8C938A),
    outlineVariant = Color(0xFF424942),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF1A1E1B),
    surfaceContainer = Color(0xFF1C211D),
    surfaceContainerHigh = Color(0xFF272B27),
    surfaceContainerHighest = Color(0xFF313632),
)

/** Farben für Werbepreise. Bewusst getrennt vom Fehlerrot, auch wenn sie ähnlich aussehen. */
@Immutable
data class Aktionsfarben(
    val farbe: Color,
    val aufFarbe: Color,
    val container: Color,
    val aufContainer: Color,
)

private val HelleAktion = Aktionsfarben(
    farbe = Color(0xFFC62828),
    aufFarbe = Color(0xFFFFFFFF),
    container = Color(0xFFFFE3DE),
    aufContainer = Color(0xFF5C0A0A),
)

private val DunkleAktion = Aktionsfarben(
    farbe = Color(0xFFFF8A80),
    aufFarbe = Color(0xFF5C0A0A),
    container = Color(0xFF4A1916),
    aufContainer = Color(0xFFFFDAD4),
)

private val LokaleAktionsfarben = staticCompositionLocalOf { HelleAktion }

/** Zugriff wie auf die übrigen Theme-Farben: `MaterialTheme.aktion.farbe`. */
val MaterialTheme.aktion: Aktionsfarben
    @Composable @ReadOnlyComposable get() = LokaleAktionsfarben.current

private val Formen = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Schrift = Typography().let { basis ->
    basis.copy(
        headlineSmall = basis.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = basis.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = basis.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = basis.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelSmall = basis.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Einheitliche Abstände. Jeder Bildschirm hält 16 dp Rand und 12 dp zwischen Blöcken. */
object Abstand {
    val rand = 16.dp
    val block = 12.dp
    val eng = 8.dp
    val minimal = 4.dp
}

@Composable
fun ArtikelFinderTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val schema: ColorScheme = if (dunkel) DunklesSchema else HellesSchema

    CompositionLocalProvider(LokaleAktionsfarben provides if (dunkel) DunkleAktion else HelleAktion) {
        MaterialTheme(
            colorScheme = schema,
            shapes = Formen,
            typography = Schrift,
            content = content,
        )
    }
}
