package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.interfaces.flash
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase

internal class PersistentScanningSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
    private val controller: MonitoringController,
) : ViewModel() {
    val persistentScanning = appPreferences.persistentScanning

    val suspendScanningInDoze = appPreferences.suspendScanningInDoze
    val suspendScanningOnWifi = appPreferences.suspendScanningOnWifi
    val wifiExceptionEnabled = appPreferences.wifiExceptionEnabled
    val exceptedWifiSsids = appPreferences.exceptedWifiSsids

    val activateOnCableCharging = appPreferences.activateOnCableCharging
    val activateOnWirelessCharging = appPreferences.activateOnWirelessCharging
    val activateOnAndroidAuto = appPreferences.activateOnAndroidAuto
    val justScan = appPreferences.justScan

    val wifiConnectionState = wifiConnectionUseCase.state

    fun requiredWifiPermissions(): List<String> = wifiConnectionUseCase.requiredPermissions()

    fun toggleExceptedSsid(ssid: String) {
        exceptedWifiSsids.value = exceptedWifiSsids.value.let {
            if (ssid in it) it - ssid else it + ssid
        }
    }

    /** Called once the monitoring permissions are granted, the toggle only turns on after that */
    fun enablePersistentScanning() {
        // Set first: the service reads it to ask for a sticky restart
        appPreferences.persistentScanning.value = true
        controller.start()
        appContext.flash("Activated persistent TPMS background scanning")
    }

    fun disablePersistentScanning() {
        appPreferences.persistentScanning.value = false
        controller.stop()
        appContext.flash("Disabled persistent TPMS background scanning")
    }
}
