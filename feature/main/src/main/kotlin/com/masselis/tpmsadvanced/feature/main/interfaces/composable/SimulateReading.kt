package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.toTemperature
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SimulateReadingViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.SimulateReadingViewModel
import java.text.DateFormat
import java.util.Date

/** Whether the menu offers to simulate a reading: debug builds, with the debug options on */
@Composable
public fun isReadingSimulationAvailable(): Boolean =
    viewModel { SimulateReadingViewModel() }.isAvailable.collectAsState().value

/**
 * Sends a reading for one of the current vehicle's tyres as if its sensor did, to try the alerts
 * away from the sensors. Debug builds only, see [isReadingSimulationAvailable].
 */
@Composable
public fun SimulateReadingDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = SimulateReadingDialog(onDismissRequest, modifier, viewModel { SimulateReadingViewModel() })

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
internal fun SimulateReadingDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SimulateReadingViewModel,
) {
    val vehicle = LocalVehicleComponent.current.vehicle
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    var locationIndex by rememberSaveable(vehicle.uuid) { mutableStateOf(0) }
    var pressure by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("20") }
    var voltage by rememberSaveable { mutableStateOf("") }
    var isAlarm by rememberSaveable { mutableStateOf(false) }
    var sentAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val location = vehicle.kind.locations.elementAt(locationIndex)
    val pressureValue = pressure.number()
    val temperatureValue = temperature.number()
    // Empty is no voltage, as most sensors send
    val voltageValue = voltage.number()
    val isValid = pressureValue != null && temperatureValue != null && (voltage.isBlank() || voltageValue != null)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Simulate a reading") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Goes through the app as if the tyre's sensor sent it: shown, stored, alerted " +
                        "on. The pressure is the sensor's, before calibration. The same reading " +
                        "again within a minute is dropped, as a sensor's repeats are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vehicle.kind.locations.forEachIndexed { index, option ->
                        FilterChip(
                            selected = index == locationIndex,
                            onClick = { locationIndex = index },
                            label = { Text(buildString { appendLoc(option, withType = false, capitalized = true) }) },
                        )
                    }
                }
                OutlinedTextField(
                    value = pressure,
                    onValueChange = { pressure = it },
                    label = { Text("Pressure") },
                    suffix = { Text(pressureUnit.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag(SimulateReadingTags.pressure),
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("Temperature") },
                    suffix = { Text(temperatureUnit.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = voltage,
                    onValueChange = { voltage = it },
                    label = { Text("Battery voltage (optional)") },
                    suffix = { Text("V") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAlarm, onCheckedChange = { isAlarm = it })
                    Text("Sensor's own alarm")
                }
                sentAt?.let {
                    Text(
                        "Sent at ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        // Stays open: alerts are tried with a few readings in a row
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    viewModel.send(
                        vehicle,
                        location,
                        requireNotNull(pressureValue).toPressure(pressureUnit),
                        requireNotNull(temperatureValue).toTemperature(temperatureUnit),
                        voltageValue?.volts,
                        isAlarm,
                    )
                    sentAt = System.currentTimeMillis()
                },
                modifier = Modifier.testTag(SimulateReadingTags.send),
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Close") } },
        modifier = modifier,
    )
}

private fun String.number() = trim().replace(',', '.').toFloatOrNull()

private val PressureUnit.label
    get() = when (this) {
        PressureUnit.KILO_PASCAL -> "kPa"
        PressureUnit.BAR -> "bar"
        PressureUnit.PSI -> "psi"
    }

private val TemperatureUnit.label
    get() = when (this) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }

@Suppress("ConstPropertyName")
internal object SimulateReadingTags {
    const val pressure = "SimulateReadingTags_pressure"
    const val send = "SimulateReadingTags_send"
}
