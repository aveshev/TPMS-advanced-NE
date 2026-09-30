package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences

internal class PressureLossSettingsViewModel(
    appPreferences: AppPreferences,
    unitPreferences: UnitPreferences,
) : ViewModel() {
    val enabled = appPreferences.pressureLoss
    val minDrop = appPreferences.pressureLossMinDrop
    val alwaysShow = appPreferences.alwaysShowPressureLoss
    val pressureUnit = unitPreferences.pressure
}
