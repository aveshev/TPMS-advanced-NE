package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.VehiclePressureLossUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** The page of the vehicle's early leak warning */
@Composable
public fun VehiclePressureLossSettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val unit by viewModel.pressureUnit.collectAsState()
    val enabled by viewModel.pressureLoss.collectAsState()
    val horizon by viewModel.pressureLossHorizon.collectAsState()
    val low by viewModel.lowPressure.collectAsState()
    val rearLow by viewModel.rearLowPressure.collectAsState()
    val separateRear by viewModel.separateRearPressure.collectAsState()
    VehiclePressureLossSettings(
        unit = unit,
        enabled = enabled,
        horizon = horizon,
        lowPressure = low,
        rearLowPressure = rearLow?.takeIf { separateRear },
        onEnabled = { viewModel.pressureLoss.value = it },
        onHorizon = { viewModel.pressureLossHorizon.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength")
@Composable
private fun VehiclePressureLossSettings(
    unit: PressureUnit,
    enabled: Boolean,
    horizon: Duration,
    lowPressure: Pressure,
    rearLowPressure: Pressure?,
    onEnabled: (Boolean) -> Unit,
    onHorizon: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    fun Pressure.flatMark() = PressureLoss.Rule(this, horizon).flatMark.numberString(unit)
    SettingsIntro(
        "Warns about a leak before the low pressure alert: when a tyre loses pressure fast enough to fall to two thirds of the low pressure alert within the chosen time.\n\nThe pressures are compared at the same temperature, so a tyre cooling down once parked doesn't count as a loss. Letting air out shows as a loss too."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Pressure loss warning",
            checked = enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.testTag(VehiclePressureLossSettingsTags.enabled),
        )
    }
    // Greyed out rather than hidden while the warning is off, like Android's settings do
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SegmentedSettingsItem(
            headline = "Warn if below it within",
            options = VehiclePressureLossUseCase.HORIZONS,
            selected = horizon,
            onSelect = onHorizon,
            label = { it.horizonLabel() },
        )
        TextSettingsItem(
            headline = "Two thirds of the low pressure alert",
            supporting = rearLowPressure
                ?.let { "Front ${lowPressure.flatMark()} · Rear ${it.flatMark()} ${unit.symbol()}" }
                ?: "${lowPressure.flatMark()} ${unit.symbol()}",
            enabled = enabled,
        )
    }
}

/** Shown summarised in [VehicleSettings] too */
internal fun Duration.horizonLabel() = "${inWholeHours} h"

@Suppress("ConstPropertyName")
internal object VehiclePressureLossSettingsTags {
    const val enabled = "VehiclePressureLossSettingsTags_enabled"
}

@Preview
@Composable
internal fun VehiclePressureLossSettingsPreview() {
    VehiclePressureLossSettings(
        unit = BAR,
        enabled = true,
        horizon = 10.hours,
        lowPressure = 2f.bar,
        rearLowPressure = null,
        onEnabled = {},
        onHorizon = {},
    )
}

@Preview
@Composable
internal fun VehiclePressureLossSettingsOffPreview() {
    VehiclePressureLossSettings(
        unit = PSI,
        enabled = false,
        horizon = 2.hours,
        lowPressure = 30f.psi,
        rearLowPressure = 36f.psi,
        onEnabled = {},
        onHorizon = {},
    )
}
