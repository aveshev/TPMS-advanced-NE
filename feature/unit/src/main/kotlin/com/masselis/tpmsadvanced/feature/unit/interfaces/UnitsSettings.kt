package com.masselis.tpmsadvanced.feature.unit.interfaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.EnumDropdown
import com.masselis.tpmsadvanced.core.ui.OutlinedTextFieldHorizontalPadding
import com.masselis.tpmsadvanced.core.ui.textWidth
import com.masselis.tpmsadvanced.feature.unit.ioc.Bindings.Companion.UnitsViewModel
import java.util.Locale

@Composable
public fun UnitsSettings(modifier: Modifier = Modifier): Unit =
    UnitsSettings(
        modifier,
        viewModel { UnitsViewModel() },
    )

@Suppress("DEPRECATION")
@Composable
internal fun UnitsSettings(
    modifier: Modifier = Modifier,
    viewModel: UnitsViewModel = viewModel { UnitsViewModel() },
) {
    val pressure by viewModel.pressure.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    // A populated dropdown shrinks its floating label to bodySmall, not the field's own text size.
    val widestLabelWidth = textWidth("Temperature in", MaterialTheme.typography.bodySmall) +
        OutlinedTextFieldHorizontalPadding
    BoxWithConstraints(modifier) {
        if (widestLabelWidth * 2 + 8.dp <= maxWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnumDropdown(
                    label = { Text("Pressure in") },
                    stringOf = { it.string().capitalize(Locale.ROOT) },
                    currentValue = pressure,
                    onValue = { viewModel.pressure.value = it },
                    modifier = Modifier.weight(1f),
                )
                EnumDropdown(
                    label = { Text(text = "Temperature in") },
                    stringOf = { it.string().capitalize(Locale.ROOT) },
                    currentValue = temperature,
                    onValue = { viewModel.temperature.value = it },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnumDropdown(
                    label = { Text("Pressure in") },
                    stringOf = { it.string().capitalize(Locale.ROOT) },
                    currentValue = pressure,
                    onValue = { viewModel.pressure.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = { Text(text = "Temperature in") },
                    stringOf = { it.string().capitalize(Locale.ROOT) },
                    currentValue = temperature,
                    onValue = { viewModel.temperature.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
