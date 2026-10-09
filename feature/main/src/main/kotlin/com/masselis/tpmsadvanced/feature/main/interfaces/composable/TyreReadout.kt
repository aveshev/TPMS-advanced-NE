package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

/** The readout of [location], or what to tap to assign it a sensor while it has none */
@Composable
internal fun TyreReadout(
    location: Location,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    when (val state = state) {
        is State.Unassigned -> TapToAssign(location, state.detected != null, modifier)
        State.Demo, is State.Assigned -> TyreStat(location = location, modifier = modifier)
    }
}

@Composable
private fun TapToAssign(
    location: Location,
    isDetected: Boolean,
    modifier: Modifier = Modifier,
) {
    // Lined up against the tyre, like the readout
    val isLeft = location.readoutSide == LEFT
    Column(
        horizontalAlignment = if (isLeft) Alignment.End else Alignment.Start,
        modifier = modifier.testTag(TyreReadoutTags.tapToAssign(location)),
    ) {
        Text(
            text = "Tap to\nassign",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (isLeft) TextAlign.End else TextAlign.Start,
        )
        if (isDetected) Text(
            text = "(detected)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TapToAssignPreview() {
    TapToAssign(Location.Wheel(FRONT_LEFT), isDetected = false)
}

@Preview(showBackground = true)
@Composable
internal fun TapToAssignDetectedPreview() {
    TapToAssign(Location.Wheel(FRONT_LEFT), isDetected = true)
}

internal object TyreReadoutTags {
    fun tapToAssign(location: Location) = "TyreReadoutTags_tapToAssign_$location"
}
