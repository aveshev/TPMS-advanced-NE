package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getActivity
import android.app.PendingIntent.getBroadcast
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MAX
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Idle
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.LowBatteryAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.NoAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureLossAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.SensorAlarm
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Suspended
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.TemperatureAlert
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.explanation
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase.Alert
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
internal class ServiceNotifier(
    scope: CoroutineScope,
    unitPreferences: UnitPreferences,
    service: Service,
    vehicleListUseCase: VehicleListUseCase,
    scanPolicyUseCase: ScanPolicyUseCase,
) {
    private val logger = Logger.withTag("ServiceNotifier")
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameWhenOk, IMPORTANCE_LOW)
                .setName("Monitor service when tyres are OK")
                .build()
        )
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameForAlerts, IMPORTANCE_MAX)
                .setName("Monitor service when alerting")
                .build()
        )
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameForLowBattery, IMPORTANCE_HIGH)
                .setName("Sensor battery low")
                .build()
        )
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameForPressureLoss, IMPORTANCE_HIGH)
                .setName("Tyre losing pressure")
                .build()
        )

        scanPolicyUseCase
            .decision
            .flatMapLatest { decision ->
                when (decision) {
                    // Not scanning: skip the scan entirely rather than emit nothing, since the
                    // service must call startForeground() shortly after being started — a
                    // silent flow here would starve that call if the app launches already
                    // suspended (e.g. opened while already in Doze).
                    is ScanDecision.Suspended -> flowOf(Suspended(decision))
                    ScanDecision.Idle -> flowOf(Idle)
                    is ScanDecision.Active -> combine(
                        vehicleListUseCase
                            .vehicleListFlow
                            // Editing a vehicle (ranges, name...) re-emits the list; only a change
                            // in the set of vehicles may rebuild the collectors, otherwise the BLE
                            // scan restarts.
                            .distinctUntilChanged { old, new ->
                                old.map(Vehicle::uuid) == new.map(Vehicle::uuid)
                            }
                            .flatMapLatest { vehicles ->
                                combine(
                                    vehicles.map { vehicle ->
                                        VehicleAlertUseCase(VehicleComponent(vehicle))
                                            .alert
                                            .map { vehicle to it }
                                    }
                                ) { it.toList() }
                            },
                        vehicleListUseCase.vehicleListFlow,
                    ) { alerts, latestVehicles ->
                        // The collectors hold the snapshot they were built with, so names are
                        // refreshed here
                        val latestByUuid = latestVehicles.associateBy(Vehicle::uuid)
                        worst(
                            alerts.map { (vehicle, alert) ->
                                (latestByUuid[vehicle.uuid] ?: vehicle) to alert
                            }
                        )
                    }
                }
            }
            // The service must call startForeground() shortly after being started, before the
            // database had time to answer
            .onStart { emit(NoAlert) }
            .catch { logger.e("Failed to listen for atmospheres", it); emit(ScanFailure) }
            .distinctUntilChanged()
            .scan(Repeats()) { repeats, state -> repeats.next(state) }
            .mapNotNull { repeats -> repeats.state?.let { it to repeats.isRepeat } }
            .map { (state, isRepeat) ->
                NotificationCompat
                    .Builder(
                        appContext,
                        when (state) {
                            NoAlert, is Suspended, Idle -> channelNameWhenOk
                            is PressureAlert, is SensorAlarm, is TemperatureAlert, ScanFailure -> channelNameForAlerts
                            is LowBatteryAlert -> channelNameForLowBattery
                            is PressureLossAlert -> channelNameForPressureLoss
                        }
                    )
                    .setSmallIcon(
                        when (state) {
                            NoAlert, is Suspended, Idle, is LowBatteryAlert -> R.drawable.car_tire
                            is PressureAlert, is SensorAlarm, is TemperatureAlert, is PressureLossAlert, ScanFailure ->
                                R.drawable.car_tire_alert
                        }
                    )
                    .setPriority(
                        when (state) {
                            NoAlert, is Suspended, Idle -> PRIORITY_LOW
                            is PressureAlert, is SensorAlarm, is TemperatureAlert, ScanFailure -> PRIORITY_MAX
                            is LowBatteryAlert, is PressureLossAlert -> PRIORITY_HIGH
                        }
                    )
                    .setOnlyAlertOnce(isRepeat)
                    .setSubText(
                        when (state) {
                            NoAlert, is Suspended, Idle -> null
                            is PressureAlert -> state.vehicleName
                            is SensorAlarm -> state.vehicleName
                            is TemperatureAlert -> state.vehicleName
                            is LowBatteryAlert -> state.vehicleName
                            is PressureLossAlert -> state.vehicleName
                            ScanFailure -> null
                        }
                    )
                    .setContentText(
                        when (state) {
                            NoAlert -> "Your tyres are OK"
                            is PressureAlert -> "⚠️ A tyre reached the pressure of ${
                                state.atmosphere.pressure.string(unitPreferences.pressure.value)
                            } !!!"

                            is SensorAlarm -> "⚠️ A tyre may be leaking !!!"

                            is TemperatureAlert -> "⚠️ A tyre reached the temperature of ${
                                state.atmosphere.temperature.string(unitPreferences.temperature.value)
                            } !!!"

                            is LowBatteryAlert -> "🔋 A sensor's battery is low: ${
                                state.atmosphere.batteryVoltage?.string()
                            }"

                            is PressureLossAlert -> buildString {
                                val unit = unitPreferences.pressure.value
                                append("📉 ")
                                appendLoc(state.location, capitalized = true)
                                append(" is losing ${state.loss.perHour.string(unit)}/h, down to ")
                                append(state.loss.flatMark.string(unit))
                                state.loss
                                    .timeToFlat
                                    .inWholeMinutes
                                    .takeIf { it >= MINUTES_PER_HOUR }
                                    ?.let { append(" in about ${(it + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR} h") }
                                    ?: append(" in less than an hour")
                            }

                            ScanFailure -> "The Android system reported an issue during the" +
                                    " bluetooth scan, TPMS Advanced must be restarted"

                            is Suspended -> state.decision.explanation()
                            Idle -> ScanDecision.Idle.explanation()
                        }
                    )
                    .apply {
                        when (state) {
                            // Opens the app on its current vehicle
                            NoAlert, is Suspended, Idle -> appContext
                                .packageManager
                                .getLaunchIntentForPackage(appContext.packageName)
                                ?.let { getActivity(appContext, requestCode, it, FLAG_IMMUTABLE) }
                                ?.also(::setContentIntent)

                            is PressureAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))
                            is SensorAlarm -> setContentIntent(vehicleIntent(state.vehicleUuid))
                            is TemperatureAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))
                            is LowBatteryAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))
                            is PressureLossAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))

                            ScanFailure -> {
                                // Nothing to do, the intent does nothing when clicked
                            }
                        }

                    }
                    .addAction(
                        when (state) {
                            NoAlert, is PressureAlert, is SensorAlarm, is TemperatureAlert, is LowBatteryAlert,
                            is PressureLossAlert, is Suspended, Idle ->
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Stop",
                                    getBroadcast(
                                        appContext,
                                        requestCode,
                                        DisableMonitorBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()

                            ScanFailure ->
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Restart app",
                                    getBroadcast(
                                        appContext,
                                        requestCode,
                                        RestartAppBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()
                        }

                    )
                    .setAutoCancel(false)
                    .build()
            }
            .onEach {
                try {
                    ServiceCompat.startForeground(
                        service,
                        notificationId,
                        it,
                        // https://developer.android.com/about/versions/14/changes/fgs-types-required#connected-device
                        if (SDK_INT >= Q) FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                    )
                } catch (e: SecurityException) {
                    // The system refuses a connectedDevice foreground service when Bluetooth scan
                    // was revoked. That kills the process, and a sticky service is restarted right
                    // away: crashing here would loop. The next app opening walks the user through
                    // the missing permissions and starts the service again.
                    logger.w(e) { "The system refused the foreground service, stopping it" }
                    service.stopSelf()
                }
            }
            .launchIn(scope)

        callbackFlow<Nothing> {
            awaitClose {
                service.also { stopForeground(it, STOP_FOREGROUND_REMOVE) }
            }
        }.launchIn(scope)
    }

    private fun vehicleIntent(vehicleUuid: UUID) = Intent(
        Intent.ACTION_VIEW,
        "tpmsadvanced://vehicle/$vehicleUuid".toUri(),
    ).let {
        TaskStackBuilder
            .create(appContext)
            .addNextIntentWithParentStack(it)
            .getPendingIntent(requestCode, FLAG_IMMUTABLE)
    }

    sealed interface State {
        data object NoAlert : State

        data class PressureAlert(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val atmosphere: TyreAtmosphere,
        ) : State

        data class SensorAlarm(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val atmosphere: TyreAtmosphere,
        ) : State

        data class TemperatureAlert(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val atmosphere: TyreAtmosphere,
        ) : State

        data class LowBatteryAlert(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val atmosphere: TyreAtmosphere,
        ) : State

        data class PressureLossAlert(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val location: Location,
            val loss: PressureLoss,
        ) : State {
            val tyre: Pair<UUID, Location>
                get() = vehicleUuid to location
        }

        data object ScanFailure : State

        data class Suspended(val decision: ScanDecision.Suspended) : State

        data object Idle : State
    }

    /**
     * Whether [state] repeats an alert that already alerted, so its notification updates silently.
     * A sensor battery doesn't recover by itself and its voltage can hover around the alarm: each
     * sensor alerts once per service run. A loss is checked again at each reading and can come and
     * go around the rule's limit: once notified, a tyre's loss updates silently until it's pumped
     * up, the next loss alerting again.
     */
    private data class Repeats(
        val state: State? = null,
        val isRepeat: Boolean = false,
        val lowBatterySensors: Set<Int> = emptySet(),
        /** The latest reading notified for each tyre losing pressure */
        val losses: Map<Pair<UUID, Location>, Double> = emptyMap(),
    ) {
        fun next(state: State): Repeats = when (state) {
            is LowBatteryAlert -> copy(
                state = state,
                isRepeat = state.atmosphere.sensorId in lowBatterySensors,
                lowBatterySensors = lowBatterySensors + state.atmosphere.sensorId,
            )

            is PressureLossAlert -> copy(
                state = state,
                isRepeat = losses[state.tyre]?.let { (state.loss.refilledAt ?: 0.0) < it } ?: false,
                losses = losses + (state.tyre to state.loss.until),
            )

            else -> copy(state = state, isRepeat = false)
        }
    }

    @Suppress("ConstPropertyName")
    internal companion object {
        private const val channelNameWhenOk = "MONITOR_SERVICE_WHEN_OK"
        private const val channelNameForAlerts = "MONITOR_SERVICE_FOR_ALERT"
        private const val channelNameForLowBattery = "MONITOR_SERVICE_FOR_LOW_BATTERY"
        private const val channelNameForPressureLoss = "MONITOR_SERVICE_FOR_PRESSURE_LOSS"
        private const val MINUTES_PER_HOUR = 60L
        private const val notificationId = 1
        private const val requestCode = 0
    }
}

/**
 * Picks what the notification must show for all monitored vehicles at once, the most severe first:
 * a pressure alert, a sensor's own alarm, a temperature alert, a tyre losing pressure, then a low
 * battery. Ties are broken by the order of [alerts].
 */
internal fun worst(alerts: List<Pair<Vehicle, Alert>>): ServiceNotifier.State =
    alerts
        .firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.Pressure)?.let { PressureAlert(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: alerts.firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.SensorAlarm)?.let { SensorAlarm(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: alerts.firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.Temperature)?.let { TemperatureAlert(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: alerts.firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.PressureLoss)?.let { PressureLossAlert(vehicle.uuid, vehicle.name, it.location, it.loss) }
        }
        ?: alerts.firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.LowBattery)?.let { LowBatteryAlert(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: NoAlert
