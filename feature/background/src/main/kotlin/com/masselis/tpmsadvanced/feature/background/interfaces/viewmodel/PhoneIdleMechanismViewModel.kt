package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Companion.asPhoneIdleMechanism
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PhoneIdleMechanismViewModel(
    private val appPreferences: AppPreferences,
    private val activityRecognitionUseCase: ActivityRecognitionUseCase,
) : ViewModel() {

    /** The mechanism telling the phone is idle, a debug option */
    val mechanism: Flow<Mechanism> = appPreferences.phoneIdleMechanism.map { it.asPhoneIdleMechanism() }

    fun setMechanism(mechanism: Mechanism) {
        appPreferences.phoneIdleMechanism.value = mechanism.name
    }

    /** Needed by [Mechanism.STANDING_STILL] */
    fun requiredActivityPermissions(): List<String> = activityRecognitionUseCase.requiredPermissions()
}
