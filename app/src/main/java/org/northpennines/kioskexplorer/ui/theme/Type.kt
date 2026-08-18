package org.northpennines.kioskexplorer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.northpennines.kioskexplorer.R

val Lexend = FontFamily(
    Font(R.font.lexend)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Lexend
    ),
    bodyMedium = TextStyle(
        fontFamily = Lexend
    ),
    bodySmall = TextStyle(
        fontFamily = Lexend
    ),
    titleLarge = TextStyle(
        fontFamily = Lexend
    ),
    titleMedium = TextStyle(
        fontFamily = Lexend
    ),
    titleSmall = TextStyle(
        fontFamily = Lexend
    ),
    headlineLarge = TextStyle(
        fontFamily = Lexend
    ),
    headlineMedium = TextStyle(
        fontFamily = Lexend
    ),
    headlineSmall = TextStyle(
        fontFamily = Lexend
    ),
    labelLarge = TextStyle(
        fontFamily = Lexend
    ),
    labelMedium = TextStyle(
        fontFamily = Lexend
    ),
    labelSmall = TextStyle(
        fontFamily = Lexend
    )

    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)