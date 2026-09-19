package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun ToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    Text(text, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
}
