package com.lklock17.omnipos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Ambient Liquid Glass Background untuk Android
 * Sama persis dengan tampilan LiquidGlassBackground di iOS:
 * Memadukan deep sleek backdrop dengan 3 ambient glow orbs (Blue, Cyan, Purple).
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1424))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Orb 1: Blue Glow (Top-Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x403B82F6), Color.Transparent),
                    center = Offset(width * 0.1f, height * 0.1f),
                    radius = width * 0.65f
                ),
                radius = width * 0.65f,
                center = Offset(width * 0.1f, height * 0.1f)
            )

            // Orb 2: Cyan Glow (Middle-Right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3306B6D4), Color.Transparent),
                    center = Offset(width * 0.9f, height * 0.45f),
                    radius = width * 0.6f
                ),
                radius = width * 0.6f,
                center = Offset(width * 0.9f, height * 0.45f)
            )

            // Orb 3: Purple Glow (Bottom-Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E8B5CF6), Color.Transparent),
                    center = Offset(width * 0.2f, height * 0.85f),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = Offset(width * 0.2f, height * 0.85f)
            )
        }

        content()
    }
}

