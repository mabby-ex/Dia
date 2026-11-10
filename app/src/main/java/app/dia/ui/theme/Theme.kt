package app.dia.ui.theme

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
import app.dia.domain.RangeBand

private val Light = lightColorScheme(
    primary = Color(0xFF1E6B5C),
    secondary = Color(0xFF4A635D),
    tertiary = Color(0xFF3F6375),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF86D6C2),
    secondary = Color(0xFFB1CCC4),
    tertiary = Color(0xFFA7CCE1),
)

object DiaColors {
    val veryLow = Color(0xFFB3261E)
    val low = Color(0xFFE5534B)
    val inRange = Color(0xFF2E9E6A)
    val high = Color(0xFFF2A93B)
    val veryHigh = Color(0xFFD9822B)
    val insulin = Color(0xFF3B7DD8)
    val longInsulin = Color(0xFF7B5CD6)
    val food = Color(0xFFE08A1E)
    val activity = Color(0xFF2E9E6A)
    val other = Color(0xFF7A7A7A)

    fun band(b: RangeBand): Color = when (b) {
        RangeBand.VERY_LOW -> veryLow
        RangeBand.LOW -> low
        RangeBand.IN_RANGE -> inRange
        RangeBand.HIGH -> high
        RangeBand.VERY_HIGH -> veryHigh
    }
}

@Composable
fun DiaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
