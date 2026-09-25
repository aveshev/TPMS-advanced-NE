package com.masselis.tpmsadvanced.feature.unit.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.feature.unit.ioc.Bindings.Companion.UnitsViewModel

/** The app-wide units, as a [SettingsGroup] */
@Composable
public fun UnitsSettings(modifier: Modifier = Modifier): Unit =
    UnitsSettings(
        modifier,
        viewModel { UnitsViewModel() },
    )

@Composable
internal fun UnitsSettings(
    modifier: Modifier = Modifier,
    viewModel: UnitsViewModel = viewModel { UnitsViewModel() },
) {
    val pressure by viewModel.pressure.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    SettingsGroup(modifier) {
        SegmentedSettingsItem(
            headline = "Pressure unit",
            options = PressureUnit.entries,
            selected = pressure,
            onSelect = { viewModel.pressure.value = it },
            label = { it.symbol() },
        )
        SegmentedSettingsItem(
            headline = "Temperature unit",
            options = TemperatureUnit.entries,
            selected = temperature,
            onSelect = { viewModel.temperature.value = it },
            label = { it.symbol() },
        )
    }
}
