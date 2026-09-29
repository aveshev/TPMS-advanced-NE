package com.masselis.tpmsadvanced.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A warning below [ColorScheme.error]: a suspended scan, a sensor battery getting low. Material 3
 * has no such role, it follows the scheme like the error does: deep on a light surface, pale on a
 * dark one. Read from the surface rather than compared to [DarkColors], so it also holds for a
 * scheme built elsewhere (previews, tests).
 */
public val ColorScheme.warning: Color
    get() = if (surface.luminance() < DARK_SURFACE_LUMINANCE) md_theme_dark_warning else md_theme_light_warning

private const val DARK_SURFACE_LUMINANCE = 0.5f
