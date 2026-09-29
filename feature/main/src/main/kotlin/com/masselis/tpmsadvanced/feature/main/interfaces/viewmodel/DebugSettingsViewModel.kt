package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class DebugSettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val showSensorId = appPreferences.showSensorId
    val showSensorFlags = appPreferences.showSensorFlags
}
