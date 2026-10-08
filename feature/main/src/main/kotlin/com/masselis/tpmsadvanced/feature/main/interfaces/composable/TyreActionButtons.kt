package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

/** The sensor configuration's buttons for [location], shown in place of its readout */
@Composable
internal fun TyreActionButtons(
    location: Location,
    assign: () -> Unit,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val sensor by viewModel.boundSensor.collectAsState()
    TyreActionButtons(location, sensor, assign, viewModel::delete, modifier)
}

@Composable
private fun TyreActionButtons(
    location: Location,
    sensor: Sensor?,
    assign: () -> Unit,
    delete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(
        // Lined up against the tyre, like the readout
        horizontalAlignment = if (location.readoutSide == LEFT) Alignment.End else Alignment.Start,
        modifier = modifier.testTag(TyreActionButtonsTags.root(location)),
    ) {
        if (sensor == null)
            OutlinedButton(
                onClick = assign,
                modifier = Modifier.testTag(TyreActionButtonsTags.assign(location)),
            ) { Text("Assign") }
        else
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(TyreActionButtonsTags.delete(location)),
            ) { Text("Delete") }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = {
            Text(
                buildString {
                    append("Remove the sensor from the ")
                    appendLoc(location)
                    append("?\nThis cannot be undone.")
                }
            )
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { delete(); confirmDelete = false },
                modifier = Modifier.testTag(TyreActionButtonsTags.confirmDelete),
            ) { Text("Remove") }
        },
    )
}

@Preview(showBackground = true)
@Composable
internal fun TyreActionButtonsAssignPreview() {
    TyreActionButtons(Location.Wheel(FRONT_LEFT), null, {}, {})
}

@Preview(showBackground = true)
@Composable
internal fun TyreActionButtonsDeletePreview() {
    TyreActionButtons(Location.Wheel(FRONT_LEFT), Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), {}, {})
}

@Suppress("ConstPropertyName")
internal object TyreActionButtonsTags {
    fun root(location: Location) = "TyreActionButtonsTags_root_$location"
    fun assign(location: Location) = "TyreActionButtonsTags_assign_$location"
    fun delete(location: Location) = "TyreActionButtonsTags_delete_$location"
    const val confirmDelete = "TyreActionButtonsTags_confirmDelete"
}
