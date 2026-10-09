package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp

private val SHAPE = RoundedCornerShape(12.dp)
private const val HIGHLIGHT_ALPHA = .12f
private const val DISABLED_ALPHA = .38f

/**
 * An action of a dialog, a list item acting when tapped. [color] tints its icon and title, the
 * error color for a destructive action.
 */
@Composable
internal fun DialogOption(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isEnabled: Boolean = true,
    color: Color = Color.Unspecified,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        colors = ListItemDefaults.colors(
            containerColor =
                if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA)
                else Color.Transparent,
            headlineColor = color.takeOrElse { MaterialTheme.colorScheme.onSurface },
            leadingIconColor = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
        ),
        modifier = modifier
            .clip(SHAPE)
            .clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA),
    )
}
