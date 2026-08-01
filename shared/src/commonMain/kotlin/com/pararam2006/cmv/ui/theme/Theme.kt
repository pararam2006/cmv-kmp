package com.pararam2006.cmv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.pararam2006.cmv.platform.*

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CustomMusicVolumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && isDynamicColorAvailable() -> {
            if (darkTheme) dynamicDarkColorScheme() else dynamicLightColorScheme()
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
//
//@Composable
//val disableServiceContainer: Color
//    @Composable
//    get() = MaterialTheme.colorScheme.errorContainer
//
//@Composable
//val enableServiceContainer: Color
//    @Composable
//    get() = MaterialTheme.colorScheme.tertiaryContainer