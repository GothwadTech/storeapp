package com.gothwad.store.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GothwadShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Shared spacing scale so every screen lines up on the same grid. */
object Spacing {
    val screenH = 16.dp
    val sectionGap = 22.dp
    val cardGap = 12.dp
    val rowGap = 8.dp
    val bottomContentPadding = 28.dp
}
