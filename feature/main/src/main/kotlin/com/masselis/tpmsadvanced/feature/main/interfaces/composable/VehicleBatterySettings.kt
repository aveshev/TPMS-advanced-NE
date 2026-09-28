package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.ui.Orange
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Companion.LOW_SOON_MARGIN
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** The page editing the voltage at which a sensor's battery alarms */
@Composable
public fun VehicleBatterySettings(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: VehicleSettingsViewModel =
        component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val lowVoltage by viewModel.lowBatteryVoltage.collectAsState()
    VehicleBatterySettings(
        lowVoltage = lowVoltage,
        onLowVoltage = { viewModel.lowBatteryVoltage.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "MaxLineLength")
@Composable
private fun VehicleBatterySettings(
    lowVoltage: Voltage,
    onLowVoltage: (Voltage) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        // Blinks like the alerting values of the main screen
        var isVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(300.milliseconds)
                isVisible = isVisible.not()
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                Triple(lowVoltage + LOW_SOON_MARGIN + LOW_SOON_MARGIN, MaterialTheme.colorScheme.onSurface, "Normal"),
                Triple(lowVoltage + LOW_SOON_MARGIN, Orange, "Getting low"),
                Triple(lowVoltage, MaterialTheme.colorScheme.error, "Low alarm"),
            ).forEach { (voltage, color, caption) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = voltage.string(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = color,
                        modifier = Modifier.alpha(if (voltage != lowVoltage || isVisible) 1f else 0f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Most sensors report the voltage of their battery. A fresh coin cell reads around 3 V and stays close to it for most of its life, then drops quickly near the end.\n\n" +
                "Once a sensor's voltage is within 0.1 V of the low voltage alarm, it is shown in orange below that tyre's readings, so you can plan a battery change. At or below the alarm, it blinks red and you get a notification.\n\n" +
                "Sysgration sensors battery monitoring currently not supported.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    SettingsSectionHeader("Alarm")
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsGroup {
        TextSettingsItem(
            headline = "Low voltage alarm",
            supporting = lowVoltage.string(),
            onClick = { editing = true },
            modifier = Modifier.testTag(VehicleBatterySettingsTags.lowVoltage),
        )
    }
    if (editing) NumberDialog(
        title = "Low voltage alarm",
        value = lowVoltage.volts,
        range = LowVoltageLimits,
        // The sensors report their voltage in steps of 0.1 V
        step = VOLTAGE_STEP,
        unit = "V",
        format = { it.volts.numberString() },
        onConfirm = { onLowVoltage(it.volts); editing = false },
        onDismissRequest = { editing = false },
    )
}

private const val VOLTAGE_STEP = 0.1f

// Below 2 V the sensors stop sending before reporting it, above 3 V a fresh battery would alarm
@Suppress("MagicNumber")
private val LowVoltageLimits = 2f..3f

@Suppress("ConstPropertyName")
internal object VehicleBatterySettingsTags {
    const val lowVoltage = "VehicleBatterySettingsTags_lowVoltage"
}

@Preview
@Composable
internal fun VehicleBatterySettingsPreview() {
    VehicleBatterySettings(lowVoltage = 2.6f.volts, onLowVoltage = {})
}
