package com.masselis.tpmsadvanced.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Approximates OutlinedTextField's default horizontal content padding (no label). */
public val OutlinedTextFieldHorizontalPadding: Dp = 32.dp

/** Exact rendered width of [text] at the current system font scale, so a layout can fit its content precisely. */
@Composable
public fun textWidth(text: String, style: TextStyle = MaterialTheme.typography.bodyLarge): Dp {
    val measurer = rememberTextMeasurer()
    return with(LocalDensity.current) { measurer.measure(text, style).size.width.toDp() }
}
