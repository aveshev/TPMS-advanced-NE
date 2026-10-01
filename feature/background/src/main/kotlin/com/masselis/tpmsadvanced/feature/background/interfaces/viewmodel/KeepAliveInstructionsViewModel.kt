package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase.Instructions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class KeepAliveInstructionsViewModel(
    private val useCase: KeepAliveInstructionsUseCase,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Loaded(val instructions: Instructions) : State

        /** The instructions can still be read on the website */
        data object Failed : State
    }

    private val logger = Logger.withTag("KeepAliveInstructionsViewModel")

    private val mutableStateFlow = MutableStateFlow<State>(State.Loading)
    val stateFlow: StateFlow<State> = mutableStateFlow.asStateFlow()

    val pageUrl: String = useCase.pageUrl

    init {
        load()
    }

    fun load() {
        mutableStateFlow.value = State.Loading
        viewModelScope.launch {
            mutableStateFlow.value = try {
                State.Loaded(useCase.instructions())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Offline, or the site changed: the website stays reachable from the failure state
                logger.w(e) { "Cannot load the instructions for ${useCase.vendor}" }
                State.Failed
            }
        }
    }
}
