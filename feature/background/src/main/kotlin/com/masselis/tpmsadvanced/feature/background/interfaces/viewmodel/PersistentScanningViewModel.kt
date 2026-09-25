package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class PersistentScanningViewModel(
    private val appPreferences: AppPreferences,
    private val scanPolicyUseCase: ScanPolicyUseCase,
    private val wifiConnectionUseCase: WifiConnectionUseCase,
    private val controller: MonitoringController,
) : ViewModel() {

    private val logger = Logger.withTag("PersistentScanningViewModel")

    val persistentScanning = appPreferences.persistentScanning

    val decision: Flow<ScanDecision> = scanPolicyUseCase.decision

    /** Known right away most of the time, so that the bell doesn't start with a wrong colour */
    val currentDecision: ScanDecision? get() = scanPolicyUseCase.currentDecision

    /** Whether the settings in place need to read the connected WiFi's name, so the location permission */
    val wifiExceptionActive: Flow<Boolean> = combine(
        appPreferences.persistentScanning,
        appPreferences.suspendConditions,
        appPreferences.suspendScanningOnWifi,
        appPreferences.wifiExceptionEnabled,
    ) { persistent, suspendConditions, suspendOnWifi, exception ->
        persistent && suspendConditions && suspendOnWifi && exception
    }

    fun requiredWifiPermissions(): List<String> = wifiConnectionUseCase.requiredPermissions()

    fun disableWifiException() {
        appPreferences.wifiExceptionEnabled.value = false
    }

    /** Persistent scanning is on, so the service is expected to run whenever the app is opened */
    fun ensureRunning() {
        if (controller.isRunning.value) return
        // Never take the app down for this: the next app opening will try again
        runCatching { controller.start() }
            .onFailure { logger.e(it) { "Cannot start the persistent scanning service" } }
    }
}
