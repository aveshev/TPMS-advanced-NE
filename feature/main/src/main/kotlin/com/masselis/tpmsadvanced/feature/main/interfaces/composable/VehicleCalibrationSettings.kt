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
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CalibrationField.MULTIPLIER
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CalibrationField.OFFSET
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key

/** The page correcting the pressure read by the vehicle's sensors */
@Composable
public fun VehicleCalibrationSettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    // A calibration adjusting nothing only adds asterisks: leaving the page like this means not
    // wanting it at all
    OnLeaveEffect(viewModel::disableCalibrationIfNoAdjustment)
    val unit by viewModel.pressureUnit.collectAsState()
    val enabled by viewModel.pressureCalibration.collectAsState()
    val offset by viewModel.pressureOffset.collectAsState()
    val multiplier by viewModel.pressureMultiplier.collectAsState()
    val low by viewModel.lowPressure.collectAsState()
    val high by viewModel.highPressure.collectAsState()
    VehicleCalibrationSettings(
        unit = unit,
        enabled = enabled,
        calibration = PressureCalibration(offset, multiplier),
        // Somewhere the user expects their tyres to be, so the example speaks to them
        example = ((low.kpa + high.kpa) / 2).kpa,
        onEnabled = { viewModel.pressureCalibration.value = it },
        onOffset = { viewModel.pressureOffset.value = it },
        onMultiplier = { viewModel.pressureMultiplier.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength")
@Composable
private fun VehicleCalibrationSettings(
    unit: PressureUnit,
    enabled: Boolean,
    calibration: PressureCalibration,
    example: Pressure,
    onEnabled: (Boolean) -> Unit,
    onOffset: (Pressure) -> Unit,
    onMultiplier: (Float) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Some sensors read a bit too high or too low. Once calibrated, the pressure they send is multiplied by the multiplier, then the offset is added, before being displayed and checked against the alerts.\n\nA calibrated pressure is marked with an asterisk, like ${example.withSymbol(unit)}*."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Pressure calibration",
            checked = enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.testTag(VehicleCalibrationSettingsTags.enabled),
        )
    }
    SettingsSectionHeader("Correction")
    var editing by rememberSaveable { mutableStateOf<CalibrationField?>(null) }
    // Greyed out rather than hidden while the calibration is off, like Android's settings do
    SettingsGroup {
        TextSettingsItem(
            headline = "Offset",
            supporting = calibration.offset.signedWithSymbol(unit),
            onClick = { editing = OFFSET },
            enabled = enabled,
        )
        TextSettingsItem(
            headline = "Multiplier",
            supporting = calibration.multiplier.multiplierString(),
            onClick = { editing = MULTIPLIER },
            enabled = enabled,
        )
        TextSettingsItem(
            headline = "Example",
            supporting = "${example.withSymbol(unit)} read by a sensor is shown as ${calibration.applyTo(example).withSymbol(unit)}*",
            enabled = enabled,
        )
    }
    when (editing) {
        OFFSET -> NumberDialog(
            title = "Offset",
            value = calibration.offset.convert(unit),
            range = OffsetLimit.let { (-it).convert(unit)..it.convert(unit) },
            step = unit.offsetStep,
            unit = unit.symbol(),
            format = { it.toPressure(unit).numberString(unit) },
            onConfirm = { onOffset(it.toPressure(unit)); editing = null },
            onDismissRequest = { editing = null },
        )

        MULTIPLIER -> NumberDialog(
            title = "Multiplier",
            value = calibration.multiplier,
            range = MultiplierLimits,
            step = MULTIPLIER_STEP,
            unit = "×",
            format = { "%.2f".format(it) },
            onConfirm = { onMultiplier(it); editing = null },
            onDismissRequest = { editing = null },
        )

        null -> {}
    }
}

private enum class CalibrationField { OFFSET, MULTIPLIER }

/** Far more than "a few units" off, a sensor that far off is broken rather than miscalibrated */
private val OffsetLimit = 20f.psi

@Suppress("MagicNumber")
private val MultiplierLimits = 0.5f..1.5f

private const val MULTIPLIER_STEP = 0.01f

private operator fun Pressure.unaryMinus() = (-kpa).kpa

private fun Pressure.withSymbol(unit: PressureUnit) = "${numberString(unit)} ${unit.symbol()}"

/** Shown summarised in [VehicleSettings] too */
internal fun Pressure.signedWithSymbol(unit: PressureUnit) =
    "${if (kpa > 0f) "+" else ""}${withSymbol(unit)}"

internal fun Float.multiplierString() = "×%.2f".format(this)

/** Finer than the alert range's steps, a calibration corrects a few units at most */
@Suppress("MagicNumber")
private val PressureUnit.offsetStep: Float
    get() = when (this) {
        KILO_PASCAL -> 5f
        BAR -> 0.05f
        PSI -> 0.5f
    }

@Suppress("ConstPropertyName")
internal object VehicleCalibrationSettingsTags {
    const val enabled = "VehicleCalibrationSettingsTags_enabled"
}

@Preview
@Composable
internal fun VehicleCalibrationSettingsPreview() {
    VehicleCalibrationSettings(
        unit = BAR,
        enabled = true,
        calibration = PressureCalibration(0.1f.bar, 1.02f),
        example = 2.4f.bar,
        onEnabled = {},
        onOffset = {},
        onMultiplier = {},
    )
}

@Preview
@Composable
internal fun VehicleCalibrationSettingsOffPreview() {
    VehicleCalibrationSettings(
        unit = PSI,
        enabled = false,
        calibration = PressureCalibration(0f.kpa, 1f),
        example = 35f.psi,
        onEnabled = {},
        onOffset = {},
        onMultiplier = {},
    )
}
