package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.LOW
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.LOW_SOON
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.NORMAL
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.parcelize.Parcelize

@Suppress("OPT_IN_TO_INHERITANCE", "LongParameterList", "MaxLineLength")
public class TyreStatsStateFlow internal constructor(
    atmosphereUseCase: TyreAtmosphereUseCase,
    rangeUseCase: VehicleRangesUseCase,
    calibrationUseCase: VehicleCalibrationUseCase,
    location: Location,
    unitPreferences: UnitPreferences,
    scope: CoroutineScope,
    stateFlow: StateFlow<State> = combine(
        atmosphereUseCase.listen(),
        rangeUseCase.highTemp,
        rangeUseCase.resolvedLowPressure(location),
        rangeUseCase.resolvedHighPressure(location),
        unitPreferences.pressure,
        unitPreferences.temperature,
        calibrationUseCase.isEnabled,
        rangeUseCase.lowBatteryVoltage,
    ) { values ->
        @Suppress("MagicNumber")
        (Data(
            values[0] as TyreAtmosphere,
            values[1] as Temperature,
            values[2] as Pressure,
            values[3] as Pressure,
            values[4] as PressureUnit,
            values[5] as TemperatureUnit,
            values[6] as Boolean,
            values[7] as Voltage,
        ))
    }
        .map { (atmosphere, highTemp, lowPressure, highPressure, pressureUnit, temperatureUnit, isCalibrated, lowBatteryVoltage) ->
            val isPressureAlert = atmosphere.pressure.hasPressure().not() ||
                atmosphere.pressure !in lowPressure..highPressure
            val isTemperatureAlert = atmosphere.temperature.celsius > highTemp.celsius
            val battery = atmosphere.batteryVoltage?.let { Battery.of(it, lowBatteryVoltage) }
            if (isPressureAlert || isTemperatureAlert || atmosphere.isSensorAlarm) State.Alerting(
                atmosphere.timestamp,
                atmosphere.sensorId,
                atmosphere.pressure,
                pressureUnit,
                atmosphere.temperature,
                temperatureUnit,
                isPressureAlert,
                isTemperatureAlert,
                isCalibrated,
                battery,
                atmosphere.isSensorAlarm,
                atmosphere.flags,
            ) else State.Normal(
                atmosphere.timestamp,
                atmosphere.sensorId,
                atmosphere.pressure,
                pressureUnit,
                atmosphere.temperature,
                temperatureUnit,
                isCalibrated,
                battery,
                atmosphere.flags,
            )
        }
        .catch { emit(State.NotDetected) }
        .stateIn(scope, WhileSubscribed(), State.NotDetected),
) : StateFlow<State> by stateFlow {

    private data class Data(
        val tyreAtmosphere: TyreAtmosphere,
        val highTemp: Temperature,
        val lowPressure: Pressure,
        val highPressure: Pressure,
        val pressureUnit: PressureUnit,
        val temperature: TemperatureUnit,
        val isCalibrated: Boolean,
        val lowBatteryVoltage: Voltage,
    )

    public sealed class State : Parcelable {
        // Shows "-:-" texts
        @Parcelize
        public data object NotDetected : State()

        // Shows the read values from the tyre
        @Parcelize
        public data class Normal(
            public val timestamp: Double,
            public val sensorId: Int,
            public val pressure: Pressure,
            public val pressureUnit: PressureUnit,
            public val temperature: Temperature,
            public val temperatureUnit: TemperatureUnit,
            // The pressure was corrected by the vehicle's calibration, marked by an asterisk
            public val isPressureCalibrated: Boolean = false,
            public val battery: Battery? = null,
            // The status byte of the last packet, see Tyre.flags
            public val flags: UByte? = null,
        ) : State()

        // Show the read values from the tyre, with the offending item(s) in red
        @Parcelize
        public data class Alerting(
            public val timestamp: Double,
            public val sensorId: Int,
            public val pressure: Pressure,
            public val pressureUnit: PressureUnit,
            public val temperature: Temperature,
            public val temperatureUnit: TemperatureUnit,
            public val isPressureAlert: Boolean,
            public val isTemperatureAlert: Boolean,
            public val isPressureCalibrated: Boolean = false,
            public val battery: Battery? = null,
            // Raised by the sensor itself (Sysgration), whatever the read values
            public val isSensorAlarm: Boolean = false,
            public val flags: UByte? = null,
        ) : State()

        /**
         * The sensor's battery, null when it doesn't report a voltage. A low battery never makes
         * the tyre alert: it isn't a safety issue, the readings stay accurate until the sensor goes
         * silent.
         */
        @Parcelize
        public data class Battery(public val voltage: Voltage, public val level: Level) : Parcelable {

            public enum class Level {
                NORMAL,

                /** Within [LOW_SOON_MARGIN] of the alarm, shown in orange */
                LOW_SOON,

                /** At or below the alarm, blinks red */
                LOW,
            }

            public companion object {
                public val LOW_SOON_MARGIN: Voltage = 0.1f.volts

                public fun of(voltage: Voltage, lowVoltage: Voltage): Battery = Battery(
                    voltage,
                    when {
                        voltage.isAtOrBelow(lowVoltage) -> LOW
                        voltage.isAtOrBelow(lowVoltage + LOW_SOON_MARGIN) -> LOW_SOON
                        else -> NORMAL
                    },
                )
            }
        }
    }
}
