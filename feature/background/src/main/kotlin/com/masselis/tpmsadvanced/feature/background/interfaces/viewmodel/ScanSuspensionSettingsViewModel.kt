package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase

internal class ScanSuspensionSettingsViewModel(
    appPreferences: AppPreferences,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
) : ViewModel() {
    val suspendScanningInDoze = appPreferences.suspendScanningInDoze
    val suspendScanningOnWifi = appPreferences.suspendScanningOnWifi
    val wifiExceptionEnabled = appPreferences.wifiExceptionEnabled
    val exceptedWifiSsids = appPreferences.exceptedWifiSsids

    val wifiConnectionState = wifiConnectionUseCase.state

    fun missingWifiPermission(): List<String> = wifiConnectionUseCase.missingPermission()

    fun toggleExceptedSsid(ssid: String) {
        exceptedWifiSsids.value = exceptedWifiSsids.value.let {
            if (ssid in it) it - ssid else it + ssid
        }
    }
}
