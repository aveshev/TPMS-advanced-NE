package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier
import com.masselis.tpmsadvanced.feature.background.usecase.AlertSilenceUseCase
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The main screen's button silencing the alerts' speech, see docs/alerts.md */
internal class AlertSilenceViewModel(
    appPreferences: AppPreferences,
    alertNotifier: AlertNotifier,
    private val alertSilenceUseCase: AlertSilenceUseCase,
) : ViewModel() {

    sealed interface State {
        data object Hidden : State

        /** Silences the alerts, all of them once [isCritical] */
        data class Offer(val isCritical: Boolean) : State

        /** The alerts are silent until [until], in milliseconds since the epoch, all of them once [isCritical] */
        data class Silenced(val isCritical: Boolean, val until: Long) : State
    }

    val stateFlow: StateFlow<State> = combine(
        appPreferences.spokenAlerts,
        alertNotifier.highestLevel,
        alertSilenceUseCase.silence,
    ) { spokenAlerts, highest, silence ->
        when {
            spokenAlerts.not() -> State.Hidden
            // A critical alert gets through a silence of the red ones, which can be silenced too
            highest == CRIMSON && silence?.level != CRIMSON -> State.Offer(isCritical = true)
            silence != null -> State.Silenced(silence.level == CRIMSON, silence.until)
            highest == RED -> State.Offer(isCritical = false)
            else -> State.Hidden
        }
    }.stateIn(viewModelScope, WhileSubscribed(), State.Hidden)

    fun silence(isCritical: Boolean) = alertSilenceUseCase.silence(if (isCritical) CRIMSON else RED)

    fun unmute() = alertSilenceUseCase.unmute()
}
