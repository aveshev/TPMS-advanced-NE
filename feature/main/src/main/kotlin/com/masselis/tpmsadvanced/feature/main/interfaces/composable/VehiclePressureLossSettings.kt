package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.VehiclePressureLossUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
    val amount by viewModel.pressureLossAmount.collectAsState()
    val window by viewModel.pressureLossWindow.collectAsState()
    VehiclePressureLossSettings(
        unit = unit,
        enabled = enabled,
        amount = amount,
        window = window,
        onEnabled = { viewModel.pressureLoss.value = it },
        onAmount = { viewModel.pressureLossAmount.value = it },
        onWindow = { viewModel.pressureLossWindow.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength")
@Composable
private fun VehiclePressureLossSettings(
    unit: PressureUnit,
    enabled: Boolean,
    amount: Pressure,
    window: Duration,
    onEnabled: (Boolean) -> Unit,
    onAmount: (Pressure) -> Unit,
    onWindow: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Warns about a leak before the low pressure alert, when a tyre loses pressure faster than it should. The pressures are compared at the same temperature, so a tyre cooling down once parked doesn't count as a loss.\n\nLetting air out, or removing a sensor to pump the tyre up, shows as a loss too."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Pressure loss warning",
            checked = enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.testTag(VehiclePressureLossSettingsTags.enabled),
        )
    }
    SettingsSectionHeader("Warn when a tyre loses")
    var editing by rememberSaveable { mutableStateOf(false) }
    // Greyed out rather than hidden while the warning is off, like Android's settings do
    SettingsGroup {
        TextSettingsItem(
            headline = "Pressure",
            supporting = "${amount.numberString(unit)} ${unit.symbol()}",
            onClick = { editing = true },
            enabled = enabled,
        )
        SegmentedSettingsItem(
            headline = "Within",
            options = VehiclePressureLossUseCase.WINDOWS,
            selected = window,
            onSelect = onWindow,
            label = { it.windowLabel() },
        )
    }
    if (editing) NumberDialog(
        title = "Pressure loss",
        value = amount.convert(unit),
        range = AmountLimits.let { it.start.convert(unit)..it.endInclusive.convert(unit) },
        step = unit.fineStep,
        unit = unit.symbol(),
        format = { it.toPressure(unit).numberString(unit) },
        onConfirm = { onAmount(it.toPressure(unit)); editing = false },
        onDismissRequest = { editing = false },
    )
}

/**
 * Well above the sensors' resolution, 3 kPa at worst, and the error left by the temperature
 * compensation. Above the upper limit, the low pressure alert comes first anyway.
 */
@Suppress("MagicNumber")
private val AmountLimits = 10f.kpa..100f.kpa

/** Shown summarised in [VehicleSettings] too */
internal fun Duration.windowLabel() = inWholeMinutes
    .takeIf { it < MINUTES_PER_HOUR }
    ?.let { "$it min" }
    ?: "${inWholeHours} h"

private const val MINUTES_PER_HOUR = 60

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
        amount = 0.2f.bar,
        window = 30.minutes,
        onEnabled = {},
        onAmount = {},
        onWindow = {},
    )
}

@Preview
@Composable
internal fun VehiclePressureLossSettingsOffPreview() {
    VehiclePressureLossSettings(
        unit = PSI,
        enabled = false,
        amount = 20f.kpa,
        window = 10.minutes,
        onEnabled = {},
        onAmount = {},
        onWindow = {},
    )
}
