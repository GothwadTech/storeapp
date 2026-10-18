package com.gothwad.store.ui.theme

import androidx.compose.ui.graphics.Color

// ── Kiwi Store palette ───────────────────────────────────────────────────────
// The brand colour is sky blue #0494F4 (the same blue as the launcher icon).
// The property names used to say "Green" when the store was Google-Play green;
// they are now Brand* so nobody re-introduces the old colour by accident.
object PlayColors {
    // Brand — sky blue #0494F4
    val Brand = Color(0xFF0494F4)
    val BrandDark = Color(0xFF036FB8)
    val BrandLight = Color(0xFFE4F3FE)
    val BrandContainer = Color(0xFFC2E4FD)
    val BrandInk = Color(0xFF035A94)
    val OnBrand = Color(0xFFFFFFFF)

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

    // Hero banner gradient — sky blue to cyan.
    val BannerStart = Color(0xFF0494F4)
    val BannerEnd = Color(0xFF00C6FF)

    // Per-app generated icon tint (mirrors the Worker's iconColor).
    val iconPalette = listOf(
        Color(0xFF0494F4), Color(0xFF1A73E8), Color(0xFFA142F4), Color(0xFF24C1E0),
        Color(0xFFFA903E), Color(0xFFF538A0), Color(0xFF34A853), Color(0xFFEA4335),
        Color(0xFFFBBC04), Color(0xFF5F6368), Color(0xFF00838F), Color(0xFF6D4C41),
    )
}

// ── Dark palette (follows the system setting) ────────────────────────────────
object PlayColorsDark {
    val Brand = Color(0xFF6FC9FF)
    val BrandDark = Color(0xFF024E7A)
    val BrandLight = Color(0xFF0D2A3C)
    val BrandContainer = Color(0xFF123F5C)
    val BrandInk = Color(0xFFCDE9FF)

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

    // Keep the banner in the same sky-blue family in dark mode.
    val BannerStart = Color(0xFF025B94)
    val BannerEnd = Color(0xFF00566B)
}
