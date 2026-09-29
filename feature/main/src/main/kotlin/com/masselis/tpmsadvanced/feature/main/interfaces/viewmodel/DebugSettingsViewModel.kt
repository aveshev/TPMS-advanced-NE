package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class DebugSettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val debugOptions = appPreferences.debugOptions
    val showSensorId = appPreferences.showSensorId
    val showSensorFlags = appPreferences.showSensorFlags

    // Owned by the background feature, which also puts its item on the debug page
    val showDetectedActivities = appPreferences.showDetectedActivities

    fun disableIfNoneSelected() {
        listOf(showSensorId, showSensorFlags, showDetectedActivities)
            .none { it.value }
            .also { if (it) debugOptions.value = false }
    }
}
