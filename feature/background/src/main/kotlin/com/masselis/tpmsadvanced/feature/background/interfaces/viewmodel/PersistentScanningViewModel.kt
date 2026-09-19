package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import kotlinx.coroutines.flow.Flow

internal class PersistentScanningViewModel(
    appPreferences: AppPreferences,
    scanPolicyUseCase: ScanPolicyUseCase,
    private val controller: MonitoringController,
) : ViewModel() {

    private val logger = Logger.withTag("PersistentScanningViewModel")

    val persistentScanning = appPreferences.persistentScanning

    val decision: Flow<ScanDecision> = scanPolicyUseCase.decision

    /** Persistent scanning is on, so the service is expected to run whenever the app is opened */
    fun ensureRunning() {
        if (controller.isRunning.value) return
        // Never take the app down for this: the next app opening will try again
        runCatching { controller.start() }
            .onFailure { logger.e(it) { "Cannot start the persistent scanning service" } }
    }
}
