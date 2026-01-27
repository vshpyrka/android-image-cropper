package com.vshpyrka.imagecropper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Internal tokens for [ImageCropper] containing default sizes and colors.
 */
internal object ImageCropperTokens {
    const val DefaultCropRectPercentage: Float = 0.8f

    // Sizes
    val BorderWidth = 2.dp
    val GridStrokeWidth = 1.dp
    val HandleRadius = 10.dp
    val MinTouchTargetSize = 48.dp
    val BaseMinCropSize = 48.dp
    val CenterMargin = 20.dp
    val HandleInteractionSize = 48.dp

    // Colors
    val OverlayColor: Color = Color.Black.copy(alpha = 0.5f)
    val BorderColor: Color = Color.White
    val HandleColor: Color = Color.White
    val GridColor: Color = Color.White.copy(alpha = 0.7f)
}
