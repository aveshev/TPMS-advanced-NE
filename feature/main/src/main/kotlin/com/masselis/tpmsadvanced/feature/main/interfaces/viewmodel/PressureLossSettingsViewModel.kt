package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class PressureLossSettingsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val enabled = appPreferences.pressureLoss
    val hours = appPreferences.pressureLossHours
    val minDrop = appPreferences.pressureLossMinDrop
    val alwaysShow = appPreferences.alwaysShowPressureLoss
}
