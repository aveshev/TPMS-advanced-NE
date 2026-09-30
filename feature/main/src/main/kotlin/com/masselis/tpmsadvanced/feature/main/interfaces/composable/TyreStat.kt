// One preview per state variant
@file:Suppress("TooManyFunctions")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.core.ui.warning
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.LOW
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.LOW_SOON
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.NORMAL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun TyreStat(
    location: Location,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreStatsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreStatsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showSensorFlags by viewModel.showSensorFlags.collectAsState()
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    val showBatteryVoltage by viewModel.showBatteryVoltage.collectAsState()
    val alwaysShowPressureLoss by viewModel.alwaysShowPressureLoss.collectAsState()
    TyreStat(
        location,
        state,
        showSensorId,
        showTimeSinceUpdate,
        showBatteryVoltage,
        showSensorFlags,
        modifier,
        alwaysShowPressureLoss = alwaysShowPressureLoss,
    )
}

@Suppress("NAME_SHADOWING", "LongMethod", "CyclomaticComplexMethod", "ComplexCondition")
@Composable
private fun TyreStat(
    location: Location,
    state: State,
    showSensorId: Boolean = false,
    showTimeSinceUpdate: Boolean = true,
    showBatteryVoltage: Boolean = false,
    showSensorFlags: Boolean = false,
    modifier: Modifier = Modifier,
    alwaysShowPressureLoss: Boolean = false,
    // Lets a preview show the pressure loss phase of the pressure line
    startWithPressureLoss: Boolean = false,
) {
    val (pressure, temperature) = when (val state = state) {
        State.NotDetected -> null to null
        is State.Normal -> Pair(
            Pair(state.pressure, state.pressureUnit),
            Pair(state.temperature, state.temperatureUnit)
        )

        is State.Alerting -> Pair(
            Pair(state.pressure, state.pressureUnit),
            Pair(state.temperature, state.temperatureUnit)
        )
    }
    val sensorId = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.sensorId
        is State.Alerting -> state.sensorId
    }
    val timestamp = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.timestamp
        is State.Alerting -> state.timestamp
    }
    val flags = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.flags
        is State.Alerting -> state.flags
    }
    val isPressureCalibrated = when (state) {
        State.NotDetected -> false
        is State.Normal -> state.isPressureCalibrated
        is State.Alerting -> state.isPressureCalibrated
    }
    val battery = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.battery
        is State.Alerting -> state.battery
    }
    val isBatteryAlert = battery?.level == LOW
    val pressureLoss = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.pressureLoss
        is State.Alerting -> state.pressureLoss
    }?.takeIf { it.isWarning || alwaysShowPressureLoss }
    val isPressureAlert = state is State.Alerting && state.isPressureAlert
    val isTemperatureAlert = state is State.Alerting && state.isTemperatureAlert
    val isSensorAlarm = state is State.Alerting && state.isSensorAlarm
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error
    val pressureColor = if (isPressureAlert) errorColor else onSurfaceColor
    val temperatureColor = if (isTemperatureAlert) errorColor else onSurfaceColor
    var isVisible by remember { mutableStateOf(true) }
    if (isPressureAlert || isTemperatureAlert || isSensorAlarm || isBatteryAlert) {
        LaunchedEffect(key1 = isVisible) {
            launch {
                repeat(Int.MAX_VALUE) {
                    delay(300.milliseconds)
                    isVisible = !isVisible
                }
            }
        }
    } else
        isVisible = true
    // A tyre losing pressure alternates its pressure line between the pressure and the loss rate
    var showsPressureLoss by remember { mutableStateOf(startWithPressureLoss) }
    if (pressureLoss != null) {
        LaunchedEffect(Unit) {
            repeat(Int.MAX_VALUE) {
                delay(PRESSURE_LOSS_PHASE)
                showsPressureLoss = showsPressureLoss.not()
            }
        }
    } else
        showsPressureLoss = false
    val alignment = remember {
        when (location) {
            is Location.Axle -> Alignment.Start
            is Location.Wheel -> when (location.location.side) {
                LEFT -> Alignment.End
                RIGHT -> Alignment.Start
            }

            is Location.Side -> when (location.side) {
                LEFT -> Alignment.End
                RIGHT -> Alignment.Start
            }
        }
    }
    val pressureText = pressure
        ?.let { (value, unit) -> value.string(unit) }
        // Explained on the vehicle's calibration page
        ?.let { if (isPressureCalibrated) "$it*" else it }
        ?: "-.--"
    val pressureLossText = pressureLoss
        ?.let { loss -> pressure?.let { (_, unit) -> loss.rateString(unit) } }
    Column(modifier = modifier) {
        Text(
            pressureLossText?.takeIf { showsPressureLoss } ?: pressureText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = when {
                pressureLossText == null || showsPressureLoss.not() -> pressureColor
                pressureLoss.isWarning -> MaterialTheme.colorScheme.warning
                // Only measured, shown from the developer options
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .align(alignment)
                // Alternating with the loss rate already draws the eye, the pressure doesn't blink
                .alpha(if (isPressureAlert.not() || pressureLossText != null || isVisible) 1f else 0f)
                .run {
                    pressureLossText
                        ?.let { loss -> clearAndSetSemantics { contentDescription = "$pressureText, losing $loss" } }
                        ?: this
                },
        )

        Text(
            temperature?.let { (value, unit) -> value.string(unit) } ?: "-.-",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            fontSize = 16.sp,
            color = temperatureColor,
            modifier = Modifier
                .align(alignment)
                .alpha(if (isTemperatureAlert.not() || isVisible) 1f else 0f),
        )

        if (showTimeSinceUpdate && timestamp != null) {
            Text(
                elapsedSinceUpdateLabel(timestamp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 16.sp,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }

        // The sensor's own alarm, its meaning isn't documented but a leak is the likely one. The
        // pressure and temperature above stay as the sensor read them.
        if (isSensorAlarm) {
            Text(
                "Leaking?",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 16.sp,
                color = errorColor,
                modifier = Modifier
                    .align(alignment)
                    .alpha(if (isVisible) 1f else 0f),
            )
        }

        // A low battery shows whatever the setting, the tyre itself doesn't alert for it
        if (battery != null && (showBatteryVoltage || battery.level != NORMAL)) {
            Text(
                battery.voltage.string(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 16.sp,
                color = when (battery.level) {
                    NORMAL -> onSurfaceColor
                    LOW_SOON -> MaterialTheme.colorScheme.warning
                    LOW -> errorColor
                },
                modifier = Modifier
                    .align(alignment)
                    .alpha(if (isBatteryAlert.not() || isVisible) 1f else 0f),
            )
        }

        if (sensorId != null && showSensorId) {
            val displaySensorId = if ((sensorId ushr 24) == 0) {
                "%02X%02X%02X".format(
                    sensorId and 0xFF,
                    (sensorId shr 8) and 0xFF,
                    (sensorId shr 16) and 0xFF,
                )
            } else {
                "0x%08X".format(sensorId)
            }

            Text(
                text = displaySensorId,
                fontSize = 9.sp,
                maxLines = 1,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }

        // Bit 0 first, the bits which are not set in the last packet greyed out
        if (flags != null && showSensorFlags) {
            Text(
                text = buildAnnotatedString {
                    repeat(Byte.SIZE_BITS) { bit ->
                        withStyle(
                            if ((flags.toInt() shr bit) and 1 == 1) SpanStyle(fontWeight = FontWeight.Bold)
                            else SpanStyle(color = onSurfaceColor.copy(alpha = UNSET_FLAG_ALPHA))
                        ) { append("$bit") }
                    }
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                color = onSurfaceColor,
                modifier = Modifier.align(alignment),
            )
        }
    }
}

private const val UNSET_FLAG_ALPHA = 0.3f

/**
 * "↓1.1 psi/h", or "↑0.2 psi/h" for a measured gain, capped at [MAX_SHOWN_PRESSURE_LOSS] to fit
 * the readout, see [Vehicle]
 */
private fun PressureLoss.rateString(unit: PressureUnit) = minOf(perHour.kpa.absoluteValue, MAX_SHOWN_PRESSURE_LOSS.kpa)
    .kpa
    .string(unit)
    .let { if (perHour.kpa < 0f) "↑$it/h" else "↓$it/h" }

/** Far faster than any leak the low pressure alert doesn't already cover */
private val MAX_SHOWN_PRESSURE_LOSS: Pressure = 999f.kpa

private val PRESSURE_LOSS_PHASE = 1.5.seconds

// Re-emits on every tier boundary crossed (each minute, then each hour, then each day) so the
// label stays live without waiting for a new sensor packet. A new `timestamp` (new packet)
// restarts this from scratch via the `key1` change, cancelling any pending delay.
@Composable
private fun elapsedSinceUpdateLabel(timestamp: Double): String {
    val label by produceState(initialValue = elapsedLabel(timestamp, now()), key1 = timestamp) {
        // produceState's underlying mutableStateOf survives across key1 changes, only the
        // producer coroutine restarts - so `value` must be set immediately here (not after the
        // first delay) or a new packet would leave the stale label showing until the next tick.
        while (true) {
            value = elapsedLabel(timestamp, now())
            delay(nextElapsedTick(timestamp, now()))
        }
    }
    return label
}

private fun elapsedLabel(timestamp: Double, now: Double): String {
    val totalMinutes = totalMinutesSince(timestamp, now)
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    return when {
        totalMinutes == 0L -> "<1 min"
        totalMinutes < HOUR_TIER_START_MINUTES -> "$totalMinutes min"
        totalHours < DAY_TIER_START_HOURS -> "$totalHours hours"
        totalHours / HOURS_PER_DAY > MAX_DAYS -> "$MAX_DAYS+ days"
        else -> "${totalHours / HOURS_PER_DAY} days"
    }
}

// Delay until the label's next tier boundary: every minute while under 2 hours, every hour
// while under 2 days, every day after that.
private fun nextElapsedTick(timestamp: Double, now: Double): Duration {
    val totalMinutes = totalMinutesSince(timestamp, now)
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    val nextBoundarySeconds = when {
        totalMinutes < HOUR_TIER_START_MINUTES ->
            (totalMinutes + 1) * SECONDS_PER_MINUTE

        totalHours < DAY_TIER_START_HOURS ->
            (totalHours + 1) * MINUTES_PER_HOUR * SECONDS_PER_MINUTE

        else ->
            (totalHours / HOURS_PER_DAY + 1) * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
    }
    return (timestamp + nextBoundarySeconds - now).seconds.coerceAtLeast(Duration.ZERO)
}

private fun totalMinutesSince(timestamp: Double, now: Double): Long =
    ((now - timestamp) / SECONDS_PER_MINUTE).toLong().coerceAtLeast(0L)

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val HOUR_TIER_START_MINUTES = 120L
private const val DAY_TIER_START_HOURS = 48L

// Keeps the label within the width `Vehicle` reserves for readouts
private const val MAX_DAYS = 99L


@Preview
@Composable
internal fun TyreStatNotDetectedPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.NotDetected,
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatNormalPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state =
            State.Normal(
                0.0,
                0,
                2f.bar,
                PressureUnit.BAR,
                30f.celsius,
                TemperatureUnit.CELSIUS
            ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatCalibratedPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state =
            State.Normal(
                0.0,
                0,
                2f.bar,
                PressureUnit.BAR,
                30f.celsius,
                TemperatureUnit.CELSIUS,
                isPressureCalibrated = true,
            ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Alerting(
            0.0,
            0,
            0.5f.bar,
            PressureUnit.BAR,
            150f.celsius,
            TemperatureUnit.CELSIUS,
            isPressureAlert = true,
            isTemperatureAlert = true,
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatPressureAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Alerting(
            0.0,
            0,
            0.5f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            isPressureAlert = true,
            isTemperatureAlert = false,
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatTemperatureAlertingPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Alerting(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            150f.celsius,
            TemperatureUnit.CELSIUS,
            isPressureAlert = false,
            isTemperatureAlert = true,
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatTimeSinceUpdateMinutesPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            now() - 5 * SECONDS_PER_MINUTE,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS
        ),
        showTimeSinceUpdate = true,
    )
}


@Preview
@Composable
internal fun TyreStatTimeSinceUpdateHoursPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            now() - 5 * MINUTES_PER_HOUR * SECONDS_PER_MINUTE,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS
        ),
        showTimeSinceUpdate = true,
    )
}


@Preview
@Composable
internal fun TyreStatTimeSinceUpdateDaysPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            now() - 5 * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS
        ),
        showTimeSinceUpdate = true,
    )
}


@Preview
@Composable
internal fun TyreStatBatteryNormalPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            battery = Battery(3f.volts, NORMAL),
        ),
        showTimeSinceUpdate = false,
        showBatteryVoltage = true,
    )
}


@Preview
@Composable
internal fun TyreStatBatteryLowSoonPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            battery = Battery(2.7f.volts, LOW_SOON),
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatBatteryLowPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            battery = Battery(2.6f.volts, LOW),
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatSensorAlarmPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Alerting(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            isPressureAlert = false,
            isTemperatureAlert = false,
            isSensorAlarm = true,
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatFlagsPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0x562D00,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            flags = 0x83u,
        ),
        showSensorId = true,
        showTimeSinceUpdate = false,
        showSensorFlags = true,
    )
}


@Preview
@Composable
internal fun TyreStatPressureLossPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            pressureLoss = PressureLoss(0.12f.bar, 0.0, 3600.0, 36_000.0, 1.3f.bar),
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatPressureLossRatePreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            pressureLoss = PressureLoss(0.12f.bar, 0.0, 3600.0, 36_000.0, 1.3f.bar),
        ),
        showTimeSinceUpdate = false,
        startWithPressureLoss = true,
    )
}


@Preview
@Composable
internal fun TyreStatPressureAlertingLossPreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Alerting(
            0.0,
            0,
            1.4f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            isPressureAlert = true,
            isTemperatureAlert = false,
            pressureLoss = PressureLoss(0.3f.bar, 0.0, 3600.0, 7200.0, 1.3f.bar),
        ),
        showTimeSinceUpdate = false,
    )
}


@Preview
@Composable
internal fun TyreStatMeasuredPressureLossRatePreview() {
    TyreStat(
        location = Location.Wheel(SensorLocation.REAR_RIGHT),
        state = State.Normal(
            0.0,
            0,
            2f.bar,
            PressureUnit.BAR,
            30f.celsius,
            TemperatureUnit.CELSIUS,
            pressureLoss = PressureLoss(0.01f.bar, 0.0, 3600.0, 252_000.0, 1.3f.bar, isWarning = false),
        ),
        showTimeSinceUpdate = false,
        alwaysShowPressureLoss = true,
        startWithPressureLoss = true,
    )
}
