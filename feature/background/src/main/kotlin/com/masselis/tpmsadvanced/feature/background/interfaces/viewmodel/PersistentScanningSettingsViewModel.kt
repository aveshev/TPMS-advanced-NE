package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import kotlinx.coroutines.flow.Flow

internal class PersistentScanningSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val scanPolicyUseCase: ScanPolicyUseCase,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
    private val controller: MonitoringController,
) : ViewModel() {
    val persistentScanning = appPreferences.persistentScanning

    val decision: Flow<ScanDecision> = scanPolicyUseCase.decision

    /** Known right away most of the time, so that the screen doesn't start with a wrong status */
    val currentDecision: ScanDecision? get() = scanPolicyUseCase.currentDecision

    val suspendConditions = appPreferences.suspendConditions
    val suspendScanningInDoze = appPreferences.suspendScanningInDoze
    val suspendScanningOnWifi = appPreferences.suspendScanningOnWifi
    val wifiExceptionEnabled = appPreferences.wifiExceptionEnabled
    val exceptedWifiSsids = appPreferences.exceptedWifiSsids

    val activateConditions = appPreferences.activateConditions
    val activateOnCableCharging = appPreferences.activateOnCableCharging
    val activateOnWirelessCharging = appPreferences.activateOnWirelessCharging
    val activateOnAndroidAuto = appPreferences.activateOnAndroidAuto
    val stayActive = appPreferences.stayActive
    val stayActiveMinutes = appPreferences.stayActiveMinutes

    val wifiConnectionState = wifiConnectionUseCase.state

    fun requiredWifiPermissions(): List<String> = wifiConnectionUseCase.requiredPermissions()

    fun toggleExceptedSsid(ssid: String) {
        exceptedWifiSsids.value = exceptedWifiSsids.value.let {
            if (ssid in it) it - ssid else it + ssid
        }
    }

    fun removeExceptedSsids(ssids: Set<String>) {
        exceptedWifiSsids.value -= ssids
    }

    fun disableActivateConditionsIfNoneSelected() {
        listOf(activateOnCableCharging, activateOnWirelessCharging, activateOnAndroidAuto)
            .none { it.value }
            .also { if (it) activateConditions.value = false }
    }

    fun disableSuspendConditionsIfNoneSelected() {
        listOf(suspendScanningInDoze, suspendScanningOnWifi)
            .none { it.value }
            .also { if (it) suspendConditions.value = false }
    }

    /** Called once the monitoring permissions are granted, the toggle only turns on after that */
    fun enablePersistentScanning() {
        // Set first: the service reads it to ask for a sticky restart
        appPreferences.persistentScanning.value = true
        controller.start()
    }

    fun disablePersistentScanning() {
        appPreferences.persistentScanning.value = false
        controller.stop()
    }
}
