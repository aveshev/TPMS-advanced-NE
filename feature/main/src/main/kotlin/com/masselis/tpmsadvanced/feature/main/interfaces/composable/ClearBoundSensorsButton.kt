package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.ClearBoundSensorsButtonTags.root
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ClearBoundSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ClearBoundSensorsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.ClearBoundSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key

/** A [SettingsGroup] item unbinding the vehicle's sensors */
@Composable
internal fun ClearBoundSensorsButton(
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
    viewModel: ClearBoundSensorsViewModel = component.viewModel(component.key()) {
        it.ClearBoundSensorsViewModel(createSavedStateHandle())
    }
) {
    val state by viewModel.stateFlow.collectAsState()
    TextSettingsItem(
        headline = "Clear favourites",
        supporting = when (state) {
            State.ClearingPossible -> "Unbind the sensors bound to this vehicle"
            State.AlreadyCleared -> "No sensor is bound to this vehicle"
        },
        onClick = { viewModel.clear() },
        enabled = state is State.ClearingPossible,
        modifier = modifier.testTag(root),
    )
}

@Suppress("ConstPropertyName")
internal object ClearBoundSensorsButtonTags {
    const val root = "ClearBoundSensorsButtonTags_root"
}
