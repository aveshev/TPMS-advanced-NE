package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehiclePressureSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehicleSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.VehicleTemperatureSettings
import com.masselis.tpmsadvanced.interfaces.composable.SettingsTag.vehicle

@Composable
internal fun Settings(
    openPressure: () -> Unit,
    openTemperature: () -> Unit,
    openBindingMethod: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehicleSettings(
        openPressure = openPressure,
        openTemperature = openTemperature,
        openBindingMethod = openBindingMethod,
        // backgroundSettings = { AutomaticBackgroundSettings(it) }
        modifier = Modifier.testTag(vehicle),
    )
}

@Composable
internal fun PressureSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehiclePressureSettings()
}

@Composable
internal fun TemperatureSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    VehicleTemperatureSettings()
}

internal object SettingsTag {
    const val vehicle = "SettingsTag_vehicle"
}
