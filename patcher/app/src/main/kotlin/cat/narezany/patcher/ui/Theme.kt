package cat.narezany.patcher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cat.narezany.patcher.Strings
import cat.narezany.patcher.data.Prefs

val LocalWords = staticCompositionLocalOf { Strings.of("en") }

// The project's mint, for phones with no palette of their own to offer.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6B4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2D0), onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358), secondaryContainer = Color(0xFFCFE9DA), onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF3E6374), tertiaryContainer = Color(0xFFC2E8FC),
    background = Color(0xFFF5FBF5), surface = Color(0xFFF5FBF5),
    surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFE9), surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5B4), onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFA8F2D0),
    secondary = Color(0xFFB3CCBE), secondaryContainer = Color(0xFF354B41), onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA6CCE0), tertiaryContainer = Color(0xFF254B5B),
    background = Color(0xFF0F1512), surface = Color(0xFF0F1512),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0C), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

@Composable
fun PatcherTheme(prefs: Prefs.Now, content: @Composable () -> Unit) {
    val dark = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colours = when {
        prefs.dynamic && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colours, content = content)
}
