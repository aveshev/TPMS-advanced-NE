package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanSuspensionUseCase(
    appPreferences: AppPreferences,
    deviceIdleModeUseCase: DeviceIdleModeUseCase,
    wifiConnectionUseCase: WifiConnectionUseCase,
    bluetoothDevicesUseCase: BluetoothDevicesUseCase,
) {
    enum class Reason { DOZE, WIFI, BLUETOOTH }

    val suspensionReasons: Flow<Set<Reason>> = appPreferences.suspendConditions.flatMapLatest { conditions ->
        // Turned off, no condition applies: none of the system listeners is needed
        if (conditions.not()) return@flatMapLatest flowOf(emptySet())
        combine(
            appPreferences.suspendScanningInDoze.flatMapLatest { enabled ->
                if (enabled) deviceIdleModeUseCase.isDeviceIdle else flowOf(false)
            },
            appPreferences.suspendScanningOnWifi.flatMapLatest { enabled ->
                if (enabled) {
                    combine(
                        appPreferences.wifiExceptionEnabled,
                        appPreferences.exceptedWifiSsids,
                        wifiConnectionUseCase.state,
                    ) { exceptionEnabled, exceptedSsids, connectionState ->
                        connectionState is WifiConnectionUseCase.State.Connected &&
                                (exceptionEnabled.not() || connectionState.ssid !in exceptedSsids)
                    }
                } else {
                    flowOf(false)
                }
            },
            appPreferences.suspendScanningOnBluetooth.flatMapLatest { enabled ->
                if (enabled) {
                    combine(
                        appPreferences.suspendBluetoothDevices,
                        bluetoothDevicesUseCase.connected,
                    ) { selected, connected -> connected.any { it.address in selected } }
                } else {
                    flowOf(false)
                }
            },
        ) { doze, wifi, bluetooth ->
            buildSet {
                if (doze) add(Reason.DOZE)
                if (wifi) add(Reason.WIFI)
                if (bluetooth) add(Reason.BLUETOOTH)
            }
        }
    }
}
