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
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MAX
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.NoAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.TemperatureAlert
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase.Alert
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
internal class ServiceNotifier(
    scope: CoroutineScope,
    unitPreferences: UnitPreferences,
    service: Service,
    vehicleListUseCase: VehicleListUseCase,
) {
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

        combine(
            vehicleListUseCase
                .vehicleListFlow
                // Editing a vehicle (ranges, name...) re-emits the list; only a change in the
                // set of vehicles may rebuild the collectors, otherwise the BLE scan restarts.
                .distinctUntilChanged { old, new -> old.map(Vehicle::uuid) == new.map(Vehicle::uuid) }
                .flatMapLatest { vehicles ->
                    combine(
                        vehicles.map { vehicle ->
                            VehicleAlertUseCase(VehicleComponent(vehicle)).alert.map { vehicle to it }
                        }
                    ) { it.toList() }
                },
            vehicleListUseCase.vehicleListFlow,
        ) { alerts, latestVehicles ->
            // The collectors hold the snapshot they were built with, so names are refreshed here
            val latestByUuid = latestVehicles.associateBy(Vehicle::uuid)
            worst(alerts.map { (vehicle, alert) -> (latestByUuid[vehicle.uuid] ?: vehicle) to alert })
        }
            // The service must call startForeground() shortly after being started, before the
            // database had time to answer
            .onStart { emit(NoAlert) }
            .catch { Firebase.crashlytics.recordException(it); emit(ScanFailure) }
            .distinctUntilChanged()
            .map { state ->
                NotificationCompat
                    .Builder(
                        appContext,
                        when (state) {
                            NoAlert -> channelNameWhenOk
                            is PressureAlert, is TemperatureAlert, ScanFailure -> channelNameForAlerts
                        }
                    )
                    .setSmallIcon(
                        when (state) {
                            NoAlert -> R.drawable.car_tire
                            is PressureAlert, is TemperatureAlert, ScanFailure -> R.drawable.car_tire_alert
                        }
                    )
                    .setPriority(
                        when (state) {
                            NoAlert -> PRIORITY_LOW
                            is PressureAlert, is TemperatureAlert, ScanFailure -> PRIORITY_MAX
                        }
                    )
                    .setSubText(
                        when (state) {
                            NoAlert -> null
                            is PressureAlert -> state.vehicleName
                            is TemperatureAlert -> state.vehicleName
                            ScanFailure -> null
                        }
                    )
                    .setContentText(
                        when (state) {
                            NoAlert -> "Your tyres are OK"
                            is PressureAlert -> "⚠️ A tyre reached the pressure of ${
                                state.atmosphere.pressure.string(unitPreferences.pressure.value)
                            } !!!"

                            is TemperatureAlert -> "⚠️ A tyre reached the temperature of ${
                                state.atmosphere.temperature.string(unitPreferences.temperature.value)
                            } !!!"

                            ScanFailure -> "The Android system reported an issue during the" +
                                    " bluetooth scan, TPMS Advanced must be restarted"
                        }
                    )
                    .apply {
                        when (state) {
                            // Opens the app on its current vehicle
                            NoAlert -> appContext
                                .packageManager
                                .getLaunchIntentForPackage(appContext.packageName)
                                ?.let { getActivity(appContext, requestCode, it, FLAG_IMMUTABLE) }
                                ?.also(::setContentIntent)

                            is PressureAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))
                            is TemperatureAlert -> setContentIntent(vehicleIntent(state.vehicleUuid))

                            ScanFailure -> {
                                // Nothing to do, the intent does nothing when clicked
                            }
                        }

                    }
                    .addAction(
                        when (state) {
                            NoAlert, is PressureAlert, is TemperatureAlert ->
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
                ServiceCompat.startForeground(
                    service,
                    notificationId,
                    it,
                    // https://developer.android.com/about/versions/14/changes/fgs-types-required#connected-device
                    if (SDK_INT >= Q) FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                )
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

        data class TemperatureAlert(
            val vehicleUuid: UUID,
            val vehicleName: String,
            val atmosphere: TyreAtmosphere,
        ) : State

        data object ScanFailure : State
    }

    @Suppress("ConstPropertyName")
    internal companion object {
        private const val channelNameWhenOk = "MONITOR_SERVICE_WHEN_OK"
        private const val channelNameForAlerts = "MONITOR_SERVICE_FOR_ALERT"
        private const val notificationId = 1
        private const val requestCode = 0
    }
}

/**
 * Picks what the notification must show for all monitored vehicles at once: a pressure alert wins
 * over a temperature alert, and ties are broken by the order of [alerts].
 */
internal fun worst(alerts: List<Pair<Vehicle, Alert>>): ServiceNotifier.State =
    alerts
        .firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.Pressure)?.let { PressureAlert(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: alerts.firstNotNullOfOrNull { (vehicle, alert) ->
            (alert as? Alert.Temperature)?.let { TemperatureAlert(vehicle.uuid, vehicle.name, it.atmosphere) }
        }
        ?: NoAlert
