package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

private val OUTSET = 6.dp
private val SHAPE = RoundedCornerShape(12.dp)

/**
 * Covers a location's tyre and readout. Tapping it assigns a sensor while there's none, and
 * manages the assigned one while [isManaging], when it's outlined to show it can be tapped.
 */
@Composable
internal fun TyreTapArea(
    location: Location,
    isManaging: Boolean,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val isTappable = when (state) {
        State.Demo -> false
        is State.Unassigned -> true
        is State.Assigned -> isManaging
    }
    Box(
        modifier
            .outset(OUTSET)
            .run { if (isManaging) border(2.dp, MaterialTheme.colorScheme.primary, SHAPE) else this }
            .clip(SHAPE)
            .run { if (isTappable) clickable { showDialog = true } else this }
            .testTag(TyreTapAreaTags.root(location))
    )
    if (showDialog) when (val state = state) {
        is State.Unassigned -> AssignSensorDialog(
            location = location,
            detected = state.detected,
            scanQrCode = { showDialog = false; scanQrCode() },
            scanBluetooth = { showDialog = false; scanBluetooth() },
            assignDetected = { viewModel.assign(it); showDialog = false },
            onDismissRequest = { showDialog = false },
        )

        is State.Assigned -> ManageSensorDialog(
            location = location,
            sensor = state.sensor,
            delete = { viewModel.delete(); showDialog = false },
            onDismissRequest = { showDialog = false },
        )

        // Never tappable
        State.Demo -> {}
    }
}

/** Grows by [outset] on every side, around what it's laid out on */
private fun Modifier.outset(outset: Dp) = layout { measurable, constraints ->
    val extra = outset.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
            minHeight = constraints.minHeight + extra,
            maxHeight = constraints.maxHeight + extra,
        )
    )
    layout(placeable.width - extra, placeable.height - extra) {
        placeable.place(-extra / 2, -extra / 2)
    }
}

/** What can be done with the [sensor] assigned to [location] */
@Composable
private fun ManageSensorDialog(
    location: Location,
    sensor: Sensor,
    delete: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
                onClick = delete,
                modifier = Modifier.testTag(TyreTapAreaTags.confirmDelete),
            ) { Text("Remove") }
        },
        modifier = modifier,
    )
    else AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(buildString { appendLoc(location, withType = false, capitalized = true) }) },
        text = {
            Column {
                Text("Sensor ${sensor.id.asSensorId()}", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TyreTapAreaTags.delete),
                ) { Text("Delete", modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        modifier = modifier.testTag(TyreTapAreaTags.manageDialog),
    )
}

@Preview
@Composable
internal fun ManageSensorDialogPreview() {
    ManageSensorDialog(Location.Wheel(FRONT_RIGHT), Sensor(0x0A0B0C, Location.Wheel(FRONT_RIGHT), PECHAM), {}, {})
}

@Suppress("ConstPropertyName")
internal object TyreTapAreaTags {
    fun root(location: Location) = "TyreTapAreaTags_root_$location"
    const val manageDialog = "TyreTapAreaTags_manageDialog"
    const val delete = "TyreTapAreaTags_delete"
    const val confirmDelete = "TyreTapAreaTags_confirmDelete"
}
