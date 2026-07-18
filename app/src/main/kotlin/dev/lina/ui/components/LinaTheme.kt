package dev.lina.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val LinaBlack = Color(0xFF000000)
private val LinaWhite = Color(0xFFFFFFFF)
private val LinaGold = Color(0xFFFFD700)

private val LinaColorScheme = darkColorScheme(
    primary = LinaGold,
    onPrimary = LinaBlack,
    background = LinaBlack,
    onBackground = LinaWhite,
    surface = LinaBlack,
    onSurface = LinaWhite,
    secondary = LinaGold,
    onSecondary = LinaBlack,
)

private val LinaTypography = Typography(
    bodyLarge = TextStyle(fontSize = 24.sp, color = LinaWhite),
    bodyMedium = TextStyle(fontSize = 24.sp, color = LinaWhite),
    titleLarge = TextStyle(fontSize = 32.sp, color = LinaGold),
    labelLarge = TextStyle(fontSize = 24.sp, color = LinaGold),
)

@Composable
fun LinaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinaColorScheme,
        typography = LinaTypography,
        content = content,
    )
}
