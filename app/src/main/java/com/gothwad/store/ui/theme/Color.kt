package com.gothwad.store.ui.theme

import androidx.compose.ui.graphics.Color

// ── Google Play 2024 palette ────────────────────────────────────────────────
object PlayColors {
    // Brand
    val Green = Color(0xFF01875F)
    val GreenDark = Color(0xFF005B41)
    val GreenLight = Color(0xFFE6F4EA)
    val GreenContainer = Color(0xFFC4EED0)
    val GreenInk = Color(0xFF056B4E)
    val Blue = Color(0xFF1A73E8)
    val BlueLight = Color(0xFFE8F0FE)

    // Surfaces (light)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF8F9FA)
    val SearchBar = Color(0xFFF1F3F4)
    val ChipBorder = Color(0xFFDADCE0)
    val Divider = Color(0xFFE8EAED)
    val Scrim = Color(0x66000000)

    // Text
    val TextPrimary = Color(0xFF202124)
    val TextSecondary = Color(0xFF5F6368)
    val TextTertiary = Color(0xFF80868B)
    val OnGreen = Color(0xFFFFFFFF)

    // Accents
    val Star = Color(0xFFF4B400)
    val Error = Color(0xFFD93025)
    val ErrorContainer = Color(0xFFFCE8E6)
    val Warning = Color(0xFFE37400)
    val WarningContainer = Color(0xFFFEF7E0)
    val Success = Color(0xFF1E8E3E)
    val SuccessContainer = Color(0xFFE6F4EA)
    val Purple = Color(0xFFA142F4)
    val Teal = Color(0xFF24C1E0)
    val Orange = Color(0xFFFA903E)
    val Pink = Color(0xFFF538A0)
    val Yellow = Color(0xFFFBBC04)
    val Red = Color(0xFFEA4335)

    val BannerStart = Color(0xFF01875F)
    val BannerEnd = Color(0xFF00BCD4)

    // Per-app generated icon tint (mirrors the Worker's iconColor).
    val iconPalette = listOf(
        Color(0xFF01875F), Color(0xFF1A73E8), Color(0xFFA142F4), Color(0xFF24C1E0),
        Color(0xFFFA903E), Color(0xFFF538A0), Color(0xFF34A853), Color(0xFFEA4335),
        Color(0xFFFBBC04), Color(0xFF5F6368), Color(0xFF00838F), Color(0xFF6D4C41),
    )
}

// ── Dark palette (follows the system setting) ────────────────────────────────
object PlayColorsDark {
    val Green = Color(0xFF4ADE9B)
    val GreenDark = Color(0xFF00543C)
    val GreenLight = Color(0xFF10352A)
    val GreenContainer = Color(0xFF1E4C3B)
    val GreenInk = Color(0xFFB7F5D4)

    val Surface = Color(0xFF1F1F1F)
    val SurfaceMuted = Color(0xFF171717)
    val SearchBar = Color(0xFF2B2B2B)
    val ChipBorder = Color(0xFF484848)
    val Divider = Color(0xFF333333)

    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFFA6A9AD)
    val TextTertiary = Color(0xFF8A8D91)

    val Star = Color(0xFFFDD663)
    val Error = Color(0xFFF28B82)
    val ErrorContainer = Color(0xFF3A1F1D)
    val Warning = Color(0xFFFDD663)
    val WarningContainer = Color(0xFF3A2F14)
    val Success = Color(0xFF81C995)
    val SuccessContainer = Color(0xFF1B3324)

    val BannerStart = Color(0xFF00543C)
    val BannerEnd = Color(0xFF00566B)
}
