package com.openchamber.android.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The text sizes the shared UI uses.
 *
 * The desktop reads them from CSS variables. The phone keeps the same sizes
 * in scale-independent pixels, so a phone and a desktop show the same density
 * of information.
 */
object SemanticTypography {
    val markdown = 14.sp
    val code = 12.sp
    val uiHeader = 14.sp
    val uiLabel = 13.5.sp
    val meta = 12.5.sp
    val micro = 12.sp
    val settingsPageTitle = 17.sp
}

val OpenChamberTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = SemanticTypography.markdown,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = SemanticTypography.uiLabel,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = SemanticTypography.meta,
        lineHeight = 17.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = SemanticTypography.settingsPageTitle,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = SemanticTypography.uiHeader,
        lineHeight = 19.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = SemanticTypography.uiLabel,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = SemanticTypography.uiLabel,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = SemanticTypography.meta,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = SemanticTypography.micro,
        lineHeight = 15.sp,
    ),
)
