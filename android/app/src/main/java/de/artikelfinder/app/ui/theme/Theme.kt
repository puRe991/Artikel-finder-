package de.artikelfinder.app.ui.theme

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

private val Gruen = Color(0xFF1B6B3A)
private val GruenHell = Color(0xFF7ED9A0)
private val Rot = Color(0xFFC62828)
private val RotHell = Color(0xFFFF8A80)

private val HellesSchema = lightColorScheme(
    primary = Gruen,
    secondary = Color(0xFF4A6357),
    /** Werbepreise werden in der Sekundärfarbe des Fehlerkanals hervorgehoben. */
    tertiary = Rot,
)

private val DunklesSchema = darkColorScheme(
    primary = GruenHell,
    secondary = Color(0xFFB1CCBC),
    tertiary = RotHell,
)

@Composable
fun ArtikelFinderTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    dynamischeFarben: Boolean = true,
    content: @Composable () -> Unit,
) {
    val schema = when {
        dynamischeFarben && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dunkel -> DunklesSchema
        else -> HellesSchema
    }

    MaterialTheme(colorScheme = schema, content = content)
}
