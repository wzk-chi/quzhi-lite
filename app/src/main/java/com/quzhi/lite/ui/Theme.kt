package com.quzhi.lite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFFE31B23),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF410004),
    secondary = Color(0xFF775653),
    secondaryContainer = Color(0xFFFFDAD7),
    background = Color(0xFFFFF8F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5DDDB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3AE),
    onPrimary = Color(0xFF690008),
    primaryContainer = Color(0xFF93000F),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFE7BDB9),
    secondaryContainer = Color(0xFF5D3F3C),
    background = Color(0xFF151110),
    surface = Color(0xFF211A19),
    surfaceVariant = Color(0xFF302423),
)

private val QuzhiShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val QuzhiTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun QuzhiLiteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = QuzhiShapes,
        typography = QuzhiTypography,
        content = content,
    )
}
