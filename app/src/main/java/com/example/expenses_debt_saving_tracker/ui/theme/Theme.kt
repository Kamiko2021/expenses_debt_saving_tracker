package com.example.expenses_debt_saving_tracker.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Navy80,
    secondary = Mint80,
    tertiary = Grey80,
    background = DeepNavy,
    surface = NavyBlue,
    onPrimary = DeepNavy,
    onSecondary = DeepNavy,
    onBackground = LightGrey,
    onSurface = LightGrey
)

private val LightColorScheme = lightColorScheme(
    primary = NavyBlue,
    secondary = Mint40,
    tertiary = Grey40,
    background = LightGrey,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DeepNavy,
    onSurface = DeepNavy
)

@Composable
fun Expenses_debt_saving_trackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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
